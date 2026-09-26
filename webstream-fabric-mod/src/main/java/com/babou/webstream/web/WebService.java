package com.babou.webstream.web;

import com.babou.webstream.core.ExportData;
import com.babou.webstream.core.ExportData.ImportResult;
import com.babou.webstream.core.ImageSniffer;
import com.babou.webstream.core.Workspace;
import com.babou.webstream.core.Workspace.ScreenInfo;
import com.babou.webstream.core.WsException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static com.babou.webstream.web.Html.esc;

/**
 * Serveur web embarqué (JDK uniquement) : interface d'administration + images servies aux écrans.
 *
 * Deux écoutes possibles : le port principal (administration + images) et, si publicPort > 0, un port
 * public qui ne sert que les images — c'est celui qu'on ouvre ou tunnelise pour les joueurs.
 */
public final class WebService {
    private static final Logger LOGGER = LoggerFactory.getLogger("webstream");
    private static final Pattern STATIC_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String[] BRANDING_EXTENSIONS = {"webp", "png", "jpg", "jpeg", "svg", "gif"};

    private final Workspace ws;
    private final WebSettings settings;
    private final String version;
    private final Router router = new Router();
    private final Pages pages;
    private final Path tmpDir;
    private HttpServer adminServer;
    private HttpServer publicServer;
    private ExecutorService executor;
    private byte[] placeholderPng;
    private final Thumbnails thumbnails;
    private Runnable onSettingsChanged;
    private volatile String settingsNotice;

    public WebService(Workspace ws, WebSettings settings, String version) {
        this.ws = ws;
        this.settings = settings;
        this.version = version;
        this.tmpDir = ws.rootDir().resolve("tmp");
        this.thumbnails = new Thumbnails(ws.rootDir().resolve("cache").resolve("thumbs"));
        this.pages = new Pages(ws, settings, version, new Pages.Branding() {
            @Override
            public boolean hasBanner() {
                return brandingFile("banner") != null;
            }

            @Override
            public boolean hasIcon() {
                return brandingFile("icon") != null;
            }
        });
        registerRoutes();
    }

    // ------------------------------------------------------------------ cycle de vie

    public synchronized void start() throws IOException {
        if (adminServer != null) return;
        Files.createDirectories(tmpDir);
        try (var old = Files.list(tmpDir)) {
            old.forEach(f -> f.toFile().delete());
        }
        AtomicInteger n = new AtomicInteger();
        executor = Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "webstream-http-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        adminServer = create(settings.bindAddress, settings.port, false);
        LOGGER.info("[WebStream] Interface d'administration sur http://{}:{}", displayHost(settings.bindAddress), adminPort());
        if (settings.publicPort > 0) {
            try {
                publicServer = create(settings.publicBindAddress, settings.publicPort, true);
                LOGGER.info("[WebStream] Images publiques sur le port {}", publicPort());
            } catch (IOException e) {
                stop();
                throw e;
            }
        }
    }

    private HttpServer create(String bind, int port, boolean publicOnly) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 0);
        server.createContext("/", ex -> dispatch(ex, publicOnly));
        server.setExecutor(executor);
        server.start();
        return server;
    }

    public synchronized void stop() {
        if (adminServer != null) adminServer.stop(0);
        if (publicServer != null) publicServer.stop(0);
        adminServer = null;
        publicServer = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    /** Appelé après chaque modification des réglages depuis l'interface (pour les enregistrer dans le fichier de config). */
    public void setOnSettingsChanged(Runnable callback) {
        this.onSettingsChanged = callback;
    }

    public boolean isRunning() {
        return adminServer != null;
    }

    public int adminPort() {
        return adminServer == null ? -1 : adminServer.getAddress().getPort();
    }

    public int publicPort() {
        return publicServer == null ? -1 : publicServer.getAddress().getPort();
    }

    /** Adresse que les joueurs doivent utiliser dans WebStreamer (publicUrl, sinon adresse locale). */
    public String baseUrl() {
        return pages.baseUrl(null);
    }

    /** Adresse complète d'un écran, telle qu'à coller dans WebStreamer (avec le paramètre configuré, ex. ?refresh=1). */
    public String screenUrl(String ref) {
        return pages.screenUrl(null, ref);
    }

    private static String displayHost(String bind) {
        return bind.equals("0.0.0.0") ? "localhost" : bind;
    }

    // ------------------------------------------------------------------ contrôle d'accès

    private void dispatch(HttpExchange ex, boolean publicOnly) {
        try {
            Ctx c = new Ctx(ex);
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

            String lookupMethod = c.method.equals("HEAD") ? "GET" : c.method;
            if (!lookupMethod.equals("GET") && !lookupMethod.equals("POST")) {
                ex.getResponseHeaders().set("Allow", "GET, HEAD, POST");
                c.text(405, "Méthode non autorisée");
                return;
            }
            Router.Match match = router.find(lookupMethod, c.rawPath);
            if (match == null || (publicOnly && !match.route().publicAsset)) {
                if (publicOnly || !lookupMethod.equals("GET")) {
                    c.text(404, "Introuvable");
                } else if (authorize(c, false)) {
                    c.html(404, pages.notFound(c));
                }
                return;
            }
            c.params = match.params();

            boolean assetRead = match.route().publicAsset && lookupMethod.equals("GET");
            if (!publicOnly && !assetRead && !authorize(c, true)) return;

            ex.getResponseHeaders().set("X-Frame-Options", "DENY");
            match.route().handler.handle(c);
        } catch (WsException e) {
            respondError(ex, e);
        } catch (Exception e) {
            LOGGER.error("[WebStream] Erreur sur {} {}", ex.getRequestMethod(), ex.getRequestURI().getRawPath(), e);
            try {
                new Ctx(ex).text(500, "Erreur interne du serveur");
            } catch (Exception ignored) {
                // réponse déjà commencée
            }
        } finally {
            ex.close();
        }
    }

    /**
     * Vrai si la requête est administrateur. Sinon a déjà répondu 401/403 (et le renvoie faux).
     * Local de confiance = adresse de bouclage ET nom d'hôte local : un tunnel (playit.gg...) ou une attaque
     * « DNS rebinding » arrive bien depuis 127.0.0.1 mais avec un autre nom d'hôte, et doit donc s'authentifier.
     */
    private boolean authorize(Ctx c, boolean checkOrigin) throws IOException {
        boolean trusted = settings.trustLocalhost
            && c.ex.getRemoteAddress().getAddress().isLoopbackAddress()
            && isLocalHostHeader(c.header("Host"));
        if (!trusted) {
            String password = settings.adminPassword == null ? "" : settings.adminPassword;
            if (password.isEmpty()) {
                c.text(403, "Accès distant désactivé : définissez adminPassword dans config/webstream.json.");
                return false;
            }
            if (!passwordMatches(c.header("Authorization"), password)) {
                c.ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"WebStream Manager\", charset=\"UTF-8\"");
                c.text(401, "Authentification requise");
                return false;
            }
        }
        if (checkOrigin && !c.method.equals("GET") && !c.method.equals("HEAD") && !sameOrigin(c)) {
            c.text(403, "Requête inter-sites refusée.");
            return false;
        }
        c.admin = true;
        return true;
    }

    private static boolean sameOrigin(Ctx c) {
        if ("cross-site".equalsIgnoreCase(c.header("Sec-Fetch-Site"))) return false;
        String origin = c.header("Origin");
        if (origin == null || origin.equals("null")) return origin == null;
        try {
            String authority = URI.create(origin).getAuthority();
            String host = c.header("Host");
            return authority != null && host != null && authority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isLocalHostHeader(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            h = end > 0 ? h.substring(1, end) : h;
        } else {
            int colon = h.lastIndexOf(':');
            if (colon >= 0 && h.indexOf(':') == colon) h = h.substring(0, colon);
        }
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1");
    }

    private static boolean passwordMatches(String header, String password) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) return false;
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String given = decoded.substring(decoded.indexOf(':') + 1);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return MessageDigest.isEqual(sha.digest(given.getBytes(StandardCharsets.UTF_8)), sha.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ erreurs

    private static int statusFor(String code) {
        if (code.endsWith("_NOT_FOUND")) return 404;
        if (code.endsWith("_EXISTS") || code.endsWith("_IN_USE") || code.equals("PROFILE_ACTIVE")) return 409;
        if (code.equals("PAYLOAD_TOO_LARGE") || code.equals("FILE_TOO_LARGE")) return 413;
        return 400;
    }

    static String messageFor(String code) {
        return switch (code) {
            case "SCREEN_EXISTS" -> "Cet écran existe déjà.";
            case "SCREEN_NOT_FOUND" -> "Écran introuvable.";
            case "REF_REQUIRED" -> "Le nom de l'écran est requis (lettres, chiffres, _ - . uniquement).";
            case "FAMILY_EXISTS" -> "Cette famille existe déjà.";
            case "FAMILY_NAME_REQUIRED" -> "Le nom de la famille est requis.";
            case "FAMILY_IN_USE" -> "Cette famille est utilisée par des écrans.";
            case "FAMILY_NOT_FOUND" -> "Famille introuvable.";
            case "CONTENT_NOT_FOUND" -> "Image introuvable dans la bibliothèque.";
            case "FILE_IN_USE" -> "Ce fichier est assigné à un écran d'un profil.";
            case "FILE_NOT_FOUND" -> "Fichier introuvable.";
            case "BAD_PATH" -> "Nom de fichier invalide.";
            case "BAD_FILE_TYPE" -> "Type de fichier non autorisé (images uniquement).";
            case "BAD_DIMENSIONS" -> "Dimensions invalides (nombre entre 0 et 1000).";
            case "PROFILE_EXISTS" -> "Un profil porte déjà ce nom.";
            case "PROFILE_NOT_FOUND" -> "Profil introuvable.";
            case "PROFILE_ACTIVE" -> "Le profil actif ne peut pas être supprimé : activez-en un autre d'abord.";
            case "PROFILE_NAME_REQUIRED" -> "Le nom du profil est requis.";
            case "IMPORT_NO_FILE" -> "Aucun fichier valide reçu (.json attendu).";
            case "IMPORT_INVALID" -> "Fichier invalide : liste d'écrans introuvable.";
            case "IMPORT_TOO_LARGE" -> "Trop d'écrans dans ce fichier.";
            case "IMPORT_BAD_MODE" -> "Mode d'import inconnu.";
            case "IMPORT_BAD_JSON" -> "Ce fichier JSON est illisible.";
            case "FILE_TOO_LARGE" -> "Fichier trop volumineux.";
            case "PAYLOAD_TOO_LARGE" -> "Requête trop volumineuse.";
            case "MULTIPART_INVALID", "TOO_MANY_PARTS" -> "Envoi de fichier invalide.";
            default -> "Erreur : " + code;
        };
    }

    private void respondError(HttpExchange ex, WsException e) {
        try {
            Ctx c = new Ctx(ex);
            int status = statusFor(e.code());
            if (c.wantsJson()) c.json(status, Map.of("error", e.code()));
            else c.text(status, messageFor(e.code()));
        } catch (Exception ignored) {
            // réponse déjà commencée
        }
    }

    // ------------------------------------------------------------------ routes

    private static void ok(Ctx c, String redirectTo, Object jsonBody) throws IOException {
        if (c.wantsJson()) c.json(200, jsonBody);
        else c.redirect(redirectTo);
    }

    private static Double parseNum(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            double d = Double.parseDouble(v.trim().replace(',', '.'));
            return Double.isNaN(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void registerRoutes() {
        Router r = router;

        r.add("GET", "/", c -> c.html(200, pages.layout(c, "Accueil", "home", pages.home(c))));

        r.add("GET", "/api/status", c -> c.json(200, Map.of(
            "version", version,
            "profile", Map.of("id", ws.activeProfileId(), "name", ws.activeProfileName()),
            "world", ws.currentWorld() == null ? "" : ws.currentWorld(),
            "screens", ws.listScreens().size(),
            "baseUrl", pages.baseUrl(c))));

        r.addPublic("GET", "/ping", c -> c.json(200, Map.of("ok", true, "name", "webstream", "version", version)));

        // ---- bibliothèque
        r.add("GET", "/library", c -> libraryPage(c, 200, null));
        r.add("GET", "/thumb/{file}", c -> {
            Path src = ws.resolveLibraryFile(c.param("file"));
            if (src == null) {
                c.text(404, "Fichier introuvable");
                return;
            }
            Integer asked = parseInt(c.query.get("w"));
            int width = Math.max(96, Math.min(1024, asked == null ? 480 : asked));
            Path thumb = thumbnails.get(src, width);
            c.ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
            serveFile(c, thumb != null ? thumb : src, "no-cache");
        });
        r.add("POST", "/library/upload", this::uploadLibrary);
        r.add("POST", "/library/delete", c -> {
            String file = c.field("file");
            if (file == null || file.isEmpty()) {
                c.json(400, Map.of("error", "FILE_REQUIRED"));
                return;
            }
            ws.deleteLibraryFile(file);
            c.json(200, Map.of("ok", true));
        });

        // ---- écrans
        r.add("GET", "/screens", c -> {
            String mode = "all".equals(c.query.get("mode")) ? "all" : "family";
            c.html(200, pages.layout(c, "Écrans", "screens", pages.screens(c, mode)));
        });
        r.add("GET", "/screens/placeholder/{w}/{h}.svg", c -> {
            int w = clamp(parseInt(c.param("w")), 640);
            int h = clamp(parseInt(c.param("h")), 400);
            c.ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            c.send(200, "image/svg+xml", placeholderSvg(w, h).getBytes(StandardCharsets.UTF_8));
        });
        r.add("POST", "/screens/create", c -> {
            String ref = c.field("ref");
            if (ref == null || ref.isBlank()) throw new WsException("REF_REQUIRED");
            ws.createScreen(ref, parseInt(c.field("familyId")), parseNum(c.field("width")), parseNum(c.field("height")));
            c.redirect("/screens");
        });
        r.add("POST", "/screens/{ref}/family", c -> {
            Integer familyId = parseInt(c.field("familyId"));
            ws.setScreenFamily(c.param("ref"), familyId);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("ok", true);
            body.put("familyId", familyId);
            ok(c, "/screens", body);
        });
        r.add("POST", "/screens/{ref}/assign", c -> {
            String file = c.field("file");
            ws.assignContent(c.param("ref"), file);
            ok(c, "/screens", Map.of("ok", true, "file", file));
        });
        r.add("POST", "/screens/{ref}/unassign", c -> {
            ws.unassignContent(c.param("ref"));
            ok(c, "/screens", Map.of("ok", true));
        });
        r.add("POST", "/screens/{ref}/delete", c -> {
            ws.deleteScreen(c.param("ref"));
            ok(c, "/screens", Map.of("ok", true));
        });
        r.add("POST", "/screens/{ref}/dimensions", c -> {
            Double w = parseNum(c.field("width"));
            Double h = parseNum(c.field("height"));
            ws.setDimensions(c.param("ref"), w, h);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("ok", true);
            body.put("width", w);
            body.put("height", h);
            ok(c, "/screens", body);
        });

        // ---- familles
        r.add("GET", "/families", c -> c.html(200, pages.layout(c, "Familles", "families", pages.families(c, null))));
        r.add("POST", "/families/create", c -> {
            try {
                ws.createFamily(c.field("name"));
                c.redirect("/families");
            } catch (WsException e) {
                c.html(statusFor(e.code()), pages.layout(c, "Familles", "families", pages.families(c, esc(messageFor(e.code())))));
            }
        });
        r.add("POST", "/families/{id}/delete", c -> {
            try {
                Integer id = parseInt(c.param("id"));
                if (id == null) throw new WsException("FAMILY_NOT_FOUND");
                ws.deleteFamily(id);
                c.redirect("/families");
            } catch (WsException e) {
                c.html(statusFor(e.code()), pages.layout(c, "Familles", "families", pages.families(c, esc(messageFor(e.code())))));
            }
        });

        // ---- profils
        r.add("GET", "/profiles", c -> profilesPage(c, 200, null, null));
        r.add("POST", "/profiles/create", c -> profileAction(c, () -> ws.createProfile(c.field("name"), c.field("cloneFrom"))));
        r.add("POST", "/profiles/{id}/activate", c -> profileAction(c, () -> ws.activateProfile(c.param("id"), "1".equals(c.field("bind")))));
        r.add("POST", "/profiles/{id}/duplicate", c -> profileAction(c, () -> ws.duplicateProfile(c.param("id"))));
        r.add("POST", "/profiles/{id}/rename", c -> profileAction(c, () -> ws.renameProfile(c.param("id"), c.field("name"))));
        r.add("POST", "/profiles/{id}/delete", c -> profileAction(c, () -> ws.deleteProfile(c.param("id"))));

        // ---- réglages
        r.add("GET", "/settings", c -> settingsPage(c, 200, settings, List.of(), null));
        r.add("POST", "/settings", this::saveSettings);
        r.add("POST", "/settings/test", this::testPublicUrl);

        // ---- export / import
        r.add("GET", "/data", c -> dataPage(c, 200, null, null));
        r.add("GET", "/data/export", c -> {
            String profile = c.query.get("profile");
            ExportData data = ws.exportProfile(profile == null || profile.isBlank() ? null : profile);
            String name = "webstream-" + Workspace.normalizeRef(data.profile).toLowerCase(Locale.ROOT) + "-" + LocalDate.now() + ".json";
            c.ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
            c.send(200, "application/json; charset=utf-8", Workspace.toJson(data).getBytes(StandardCharsets.UTF_8));
        });
        r.add("POST", "/data/import", this::importData);

        // ---- images des écrans et contenu (publics)
        r.addPublic("GET", "/{ref}.png", this::serveScreen);
        r.addPublic("GET", "/content/{file}", c -> {
            Path file = ws.resolveLibraryFile(c.param("file"));
            if (file == null) {
                c.text(404, "Fichier introuvable");
                return;
            }
            c.ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
            serveFile(c, file, "no-cache");
        });
        r.addPublic("GET", "/branding/{name}", c -> {
            Path file = brandingFile(c.param("name"));
            if (file == null) {
                c.text(404, "Introuvable");
                return;
            }
            serveFile(c, file, "public, max-age=300");
        });

        // ---- fichiers statiques de l'interface
        r.add("GET", "/static/{name}", c -> serveStatic(c, c.param("name")));
        r.add("GET", "/static/{dir}/{name}", c -> serveStatic(c, c.param("dir") + "/" + c.param("name")));
    }

    private static int clamp(Integer v, int dflt) {
        return Math.max(100, Math.min(2000, v == null ? dflt : v));
    }

    private interface ProfileOp {
        void run() throws IOException;
    }

    private void profileAction(Ctx c, ProfileOp op) throws IOException {
        try {
            op.run();
            if (c.wantsJson()) c.json(200, Map.of("ok", true));
            else c.redirect("/profiles");
        } catch (WsException e) {
            if (c.wantsJson()) c.json(statusFor(e.code()), Map.of("error", e.code()));
            else profilesPage(c, statusFor(e.code()), esc(messageFor(e.code())), "error");
        }
    }

    private void profilesPage(Ctx c, int status, String flashHtml, String kind) throws IOException {
        c.html(status, pages.layout(c, "Profils", "profiles", pages.profiles(c, flashHtml, kind)));
    }

    private void libraryPage(Ctx c, int status, String errorHtml) throws IOException {
        c.html(status, pages.layout(c, "Bibliothèque", "library", pages.library(c, ws.listLibraryFilesInfo(), ws.usageByFile(), errorHtml)));
    }

    private void dataPage(Ctx c, int status, ImportResult result, String errorHtml) throws IOException {
        c.html(status, pages.layout(c, "Données", "data", pages.data(c, result, errorHtml)));
    }

    // ------------------------------------------------------------------ envois de fichiers

    private long maxFileBytes() {
        return Math.max(1, settings.maxUploadMb) * 1024L * 1024L;
    }

    /** Lit un envoi multipart ; en cas de refus, vide le reste de la requête pour que le client reçoive bien l'erreur. */
    private Multipart receiveMultipart(Ctx c, long maxFileBytes, int maxParts) throws IOException {
        String ct = c.header("Content-Type");
        if (!Multipart.isMultipart(ct)) throw new WsException("MULTIPART_INVALID");
        InputStream body = c.ex.getRequestBody();
        try {
            return Multipart.parse(body, ct, tmpDir, maxFileBytes, maxParts);
        } catch (WsException e) {
            drain(body);
            throw e;
        }
    }

    /** Sans cela, fermer la connexion pendant que le navigateur envoie encore provoque une « connexion réinitialisée ». */
    private static void drain(InputStream in) {
        byte[] buf = new byte[65536];
        long total = 0;
        try {
            int n;
            while (total < (1L << 30) && (n = in.read(buf)) >= 0) total += n;
        } catch (IOException ignored) {
            // le client a interrompu son envoi : rien de plus à faire
        }
    }

    private void uploadLibrary(Ctx c) throws IOException {
        try (Multipart mp = receiveMultipart(c, maxFileBytes(), 200)) {
            List<String> rejected = new ArrayList<>();
            int stored = 0;
            for (Multipart.FilePart f : mp.files) {
                if (!f.field().equals("files")) continue;
                try {
                    String safe = f.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (!Workspace.isAllowedImageName(safe) || !ImageSniffer.matchesExtension(f.path(), safe)) {
                        rejected.add(f.filename());
                        continue;
                    }
                    ws.storeLibraryFile(f.filename(), f.path());
                    stored++;
                } catch (WsException e) {
                    rejected.add(f.filename());
                }
            }
            if (c.wantsJson()) {
                c.json(rejected.isEmpty() ? 200 : 400, Map.of("stored", stored, "rejected", rejected));
            } else if (!rejected.isEmpty()) {
                StringBuilder sb = new StringBuilder("<p style=\"margin-top:0\">❌ ").append(rejected.size())
                    .append(" fichier(s) refusé(s) (image PNG, JPG, GIF, WebP, BMP, TIFF ou SVG valide attendue) :</p><ul style=\"margin:0\">");
                for (String name : rejected) sb.append("<li>").append(esc(name)).append("</li>");
                sb.append("</ul>");
                libraryPage(c, 400, sb.toString());
            } else {
                c.redirect("/library");
            }
        }
    }

    private void importData(Ctx c) throws IOException {
        try (Multipart mp = receiveMultipart(c, 20L * 1024 * 1024, 20)) {
            Multipart.FilePart file = mp.files.stream()
                .filter(f -> f.filename().toLowerCase(Locale.ROOT).endsWith(".json")).findFirst().orElse(null);
            if (file == null) throw new WsException("IMPORT_NO_FILE");
            ExportData data = Workspace.parseExport(Files.readString(file.path(), StandardCharsets.UTF_8));

            String target = mp.fields.getOrDefault("target", "active");
            String newName = "new".equals(target) ? mp.fields.get("newName") : null;
            if ("new".equals(target) && (newName == null || newName.isBlank())) newName = data.profile;
            String targetId = "other".equals(target) ? mp.fields.get("otherId") : null;

            ImportResult result = ws.importData(data, mp.fields.getOrDefault("mode", "skip"), targetId, newName);
            dataPage(c, 200, result, null);
        } catch (WsException e) {
            dataPage(c, statusFor(e.code()), null, esc(messageFor(e.code())));
        }
    }

    // ------------------------------------------------------------------ service des fichiers

    private void serveScreen(Ctx c) throws IOException {
        ScreenInfo screen = ws.getScreen(c.param("ref"));
        if (screen == null) {
            c.text(404, "Écran introuvable");
            return;
        }
        Path file = screen.content() == null ? null : ws.resolveLibraryFile(screen.content());
        if (file == null) {
            c.ex.getResponseHeaders().set("Cache-Control", "no-cache");
            if (!settings.placeholderImage) {
                c.text(404, "Aucune image assignée à cet écran");
                return;
            }
            c.ex.getResponseHeaders().set("X-WebStream-Placeholder", "1");
            c.send(200, "image/png", placeholder());
            return;
        }
        serveFile(c, file, "no-cache");
    }

    private synchronized byte[] placeholder() throws IOException {
        if (placeholderPng == null) {
            try (InputStream in = WebService.class.getResourceAsStream("/web/placeholder.png")) {
                placeholderPng = in == null ? new byte[0] : in.readAllBytes();
            }
        }
        return placeholderPng;
    }

    private void serveFile(Ctx c, Path file, String cacheControl) throws IOException {
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        String etag = "\"" + Long.toHexString(size) + "-" + Long.toHexString(modified) + "\"";
        c.ex.getResponseHeaders().set("ETag", etag);
        c.ex.getResponseHeaders().set("Cache-Control", cacheControl);
        String inm = c.header("If-None-Match");
        if (inm != null && inm.contains(etag)) {
            c.ex.sendResponseHeaders(304, -1);
            return;
        }
        c.sendFile(200, contentType(file.getFileName().toString()), file, size);
    }

    static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".tif") || n.endsWith(".tiff")) return "image/tiff";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private void serveStatic(Ctx c, String name) throws IOException {
        for (String part : name.split("/")) {
            if (!STATIC_NAME.matcher(part).matches() || part.equals("..") || part.equals(".")) {
                c.text(404, "Introuvable");
                return;
            }
        }
        try (InputStream in = WebService.class.getResourceAsStream("/web/static/" + name)) {
            if (in == null) {
                c.text(404, "Introuvable");
                return;
            }
            c.ex.getResponseHeaders().set("Cache-Control", "public, max-age=300");
            c.send(200, contentType(name), in.readAllBytes());
        }
    }

    /** Fichier de personnalisation (config/webstream/branding/banner.* ou icon.*), ou null. */
    private Path brandingFile(String name) {
        if (!name.equals("banner") && !name.equals("icon")) return null;
        Path dir = ws.rootDir().resolve("branding");
        for (String ext : BRANDING_EXTENSIONS) {
            Path p = dir.resolve(name + "." + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ réglages

    private void settingsPage(Ctx c, int status, WebSettings shown, List<String> errors, String flashKind) throws IOException {
        c.html(status, pages.layout(c, "Réglages", "settings",
            pages.settings(c, shown, errors, flashKind, settingsNotice, localAddresses(), !settings.adminPassword.isEmpty())));
    }

    private static int num(String v, int invalid) {
        Integer n = parseInt(v);
        return n == null ? invalid : n;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private void persist() {
        if (onSettingsChanged != null) onSettingsChanged.run();
    }

    private void saveSettings(Ctx c) throws IOException {
        Map<String, String> f = c.form();
        WebSettings next = settings.copy();

        next.port = num(f.get("port"), -1);
        boolean publicOn = f.containsKey("publicEnabled");
        next.publicPort = !publicOn ? 0 : (trim(f.get("publicPort")).isEmpty() ? 8283 : num(f.get("publicPort"), -1));
        next.bindAddress = trim(f.get("bindAddress"));
        next.publicBindAddress = trim(f.get("publicBindAddress"));
        if (f.containsKey("clearPassword")) next.adminPassword = "";
        else if (!trim(f.get("adminPassword")).isEmpty()) next.adminPassword = f.get("adminPassword");
        next.trustLocalhost = f.containsKey("trustLocalhost");
        next.publicUrl = trim(f.get("publicUrl"));
        next.adminUrl = trim(f.get("adminUrl"));
        next.newWorldProfile = trim(f.get("newWorldProfile"));
        next.maxUploadMb = num(f.get("maxUploadMb"), -1);
        next.placeholderImage = f.containsKey("placeholderImage");
        next.urlQuery = trim(f.get("urlQuery")).replaceFirst("^\\?+", "");

        List<String> errors = new ArrayList<>(next.validate());
        if (!next.trustLocalhost && next.adminPassword.isEmpty()) {
            errors.add("Sans mot de passe, désactiver la confiance locale vous bloquerait : définissez d'abord un mot de passe.");
        }
        if (!errors.isEmpty()) {
            next.adminPassword = settings.adminPassword;
            settingsPage(c, 400, next, errors, null);
            return;
        }

        boolean network = next.networkDiffersFrom(settings);
        WebSettings previous = settings.copy();
        settings.copyFrom(next);
        persist();
        settingsNotice = null;
        if (network) restartAsync(previous);
        settingsPage(c, 200, settings, List.of(), network ? "restart" : "saved");
    }

    /** Redémarre les écoutes réseau (après avoir répondu) ; si les nouveaux ports sont refusés, les anciens réglages reviennent. */
    private void restartAsync(WebSettings previous) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (this) {
                stop();
                try {
                    start();
                    LOGGER.info("[WebStream] Serveur web redémarré avec les nouveaux réglages");
                } catch (IOException e) {
                    LOGGER.error("[WebStream] Nouveaux ports inutilisables ({}) : retour aux réglages précédents", e.getMessage());
                    settings.copyFrom(previous);
                    persist();
                    settingsNotice = "Les nouveaux ports n'ont pas pu être utilisés (" + e.getMessage() + ") : les réglages précédents ont été rétablis.";
                    try {
                        start();
                    } catch (IOException e2) {
                        LOGGER.error("[WebStream] Impossible de relancer le serveur web", e2);
                    }
                }
            }
        }, "webstream-restart");
        t.setDaemon(true);
        t.start();
    }

    /** Vérifie que l'adresse publique répond, depuis ce serveur (ne prouve pas que le port est ouvert vers Internet). */
    private void testPublicUrl(Ctx c) throws IOException {
        String typed = trim(c.field("url"));
        String configured = typed.isEmpty() ? trim(settings.publicUrl) : typed;
        if (!configured.isEmpty() && !configured.startsWith("http://") && !configured.startsWith("https://")) {
            c.json(200, Map.of("ok", false, "url", configured, "message", "L'adresse doit commencer par http:// ou https://."));
            return;
        }
        String base = configured.isEmpty()
            ? "http://127.0.0.1:" + (settings.publicPort > 0 ? publicPort() : adminPort())
            : (configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured);
        String url = base + "/ping";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<InputStream> r = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream in = r.body()) {
                body = new String(in.readNBytes(2048), StandardCharsets.UTF_8);
            }
            boolean ok = r.statusCode() == 200 && body.contains("\"webstream\"");
            result.put("ok", ok);
            result.put("status", r.statusCode());
            result.put("message", ok ? "Le serveur répond bien à cette adresse."
                : "Une réponse a été reçue, mais ce n'est pas WebStream (code " + r.statusCode() + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.put("ok", false);
            result.put("message", "Test interrompu.");
        } catch (IOException | IllegalArgumentException e) {
            result.put("ok", false);
            result.put("message", "Aucune réponse à cette adresse (" + e.getClass().getSimpleName() + ").");
        }
        c.json(200, result);
    }

    /** Adresses IPv4 de cette machine sur le réseau local, pour suggérer une publicUrl. */
    static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                nic.getInterfaceAddresses().forEach(a -> {
                    if (a.getAddress() instanceof Inet4Address v4 && !v4.isLinkLocalAddress()) out.add(v4.getHostAddress());
                });
            }
        } catch (IOException ignored) {
            // pas d'interface lisible : aucune suggestion
        }
        return out;
    }

    private static String placeholderSvg(int width, int height) {
        String[] bars = {"#c8c8c8", "#c8c800", "#00c8c8", "#00c800", "#c800c8", "#c80000", "#0000c8"};
        String[] low = {"#0000c8", "#111111", "#c800c8", "#111111", "#00c8c8", "#111111", "#c8c8c8"};
        double bw = width / 7.0;
        double top = height * 0.72;
        double mid = height * 0.86;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" preserveAspectRatio=\"none\">", width, height, width, height));
        sb.append(String.format(Locale.ROOT, "<rect width=\"%d\" height=\"%d\" fill=\"#0a0a0a\"/>", width, height));
        for (int i = 0; i < 7; i++) {
            sb.append(String.format(Locale.ROOT, "<rect x=\"%.2f\" y=\"0\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>", i * bw, bw + 0.5, top, bars[i]));
            sb.append(String.format(Locale.ROOT, "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>", i * bw, top, bw + 0.5, mid - top, low[i]));
        }
        double stroke = Math.max(2, Math.min(width, height) / 90.0);
        sb.append(String.format(Locale.ROOT, "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"#05080d\" stroke=\"#3df5ff\" stroke-width=\"%.2f\"/>",
            width * 0.2, height * 0.26, width * 0.6, height * 0.36, stroke));
        double big = Math.min(width * 0.09, height * 0.16);
        double small = Math.min(width * 0.035, height * 0.06);
        sb.append(String.format(Locale.ROOT, "<text x=\"50%%\" y=\"%.2f\" fill=\"#3df5ff\" font-family=\"Consolas,'Courier New',monospace\" font-weight=\"700\" font-size=\"%.2f\" text-anchor=\"middle\">NO SIGNAL</text>", height * 0.44, big));
        sb.append(String.format(Locale.ROOT, "<text x=\"50%%\" y=\"%.2f\" fill=\"#ff3df2\" font-family=\"Consolas,'Courier New',monospace\" font-size=\"%.2f\" text-anchor=\"middle\">AUCUN CONTENU</text>", height * 0.54, small));
        return sb.append("</svg>").toString();
    }
}
