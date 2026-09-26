package com.babou.webstream.web;

import com.babou.webstream.core.ExportData.ImportResult;
import com.babou.webstream.core.Workspace;
import com.babou.webstream.core.Workspace.FamilyInfo;
import com.babou.webstream.core.Workspace.LibraryFile;
import com.babou.webstream.core.Workspace.ProfileInfo;
import com.babou.webstream.core.Workspace.ScreenGroup;
import com.babou.webstream.core.Workspace.ScreenInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.babou.webstream.web.Html.esc;
import static com.babou.webstream.web.Html.num;
import static com.babou.webstream.web.Html.urlEnc;

/** Génération des pages HTML. Le style est dans styles.css, les comportements dans app.js. */
final class Pages {
    private final Workspace ws;
    private final WebSettings settings;
    private final String version;
    private final Branding branding;

    interface Branding {
        boolean hasBanner();

        boolean hasIcon();
    }

    Pages(Workspace ws, WebSettings settings, String version, Branding branding) {
        this.ws = ws;
        this.settings = settings;
        this.version = version;
        this.branding = branding;
    }

    // ------------------------------------------------------------------ adresses

    /** Adresse à donner à WebStreamer : publicUrl si configurée, sinon l'hôte utilisé pour ouvrir l'interface. */
    String baseUrl(Ctx c) {
        String pub = settings.publicUrl == null ? "" : settings.publicUrl.trim();
        if (!pub.isEmpty()) return pub.endsWith("/") ? pub.substring(0, pub.length() - 1) : pub;
        String host = c == null ? null : c.header("Host");
        return "http://" + (host == null || host.isBlank() ? "localhost:" + settings.port : host);
    }

    /** Adresse complète d'un écran, avec le paramètre configuré (ex. ?refresh=1). */
    String screenUrl(Ctx c, String ref) {
        String q = settings.urlQuery == null ? "" : settings.urlQuery.trim();
        return baseUrl(c) + "/" + urlEnc(ref) + ".png" + (q.isEmpty() ? "" : "?" + q);
    }

    // ------------------------------------------------------------------ petits éléments

    private static final Map<String, String> ICONS = Map.ofEntries(
        Map.entry("home", "<path d=\"M3 11l9-8 9 8\"/><path d=\"M5 10v10h14V10\"/>"),
        Map.entry("image", "<rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"2\"/><circle cx=\"9\" cy=\"10\" r=\"1.6\"/><path d=\"M21 16l-5-5-9 9\"/>"),
        Map.entry("monitor", "<rect x=\"2\" y=\"4\" width=\"20\" height=\"13\" rx=\"2\"/><path d=\"M8 21h8M12 17v4\"/>"),
        Map.entry("folder", "<path d=\"M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"/>"),
        Map.entry("layers", "<path d=\"M12 3l9 5-9 5-9-5z\"/><path d=\"M3 13l9 5 9-5\"/>"),
        Map.entry("database", "<ellipse cx=\"12\" cy=\"5\" rx=\"8\" ry=\"3\"/><path d=\"M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5\"/><path d=\"M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6\"/>"),
        Map.entry("settings", "<path d=\"M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0\"/><circle cx=\"16\" cy=\"6\" r=\"2\"/><circle cx=\"10\" cy=\"12\" r=\"2\"/><circle cx=\"18\" cy=\"18\" r=\"2\"/>"),
        Map.entry("plus", "<path d=\"M12 5v14M5 12h14\"/>"),
        Map.entry("copy", "<rect x=\"9\" y=\"9\" width=\"11\" height=\"11\" rx=\"2\"/><path d=\"M5 15V6a2 2 0 0 1 2-2h9\"/>"),
        Map.entry("upload", "<path d=\"M12 16V4M7 9l5-5 5 5\"/><path d=\"M4 20h16\"/>"),
        Map.entry("download", "<path d=\"M12 4v12M7 11l5 5 5-5\"/><path d=\"M4 20h16\"/>"),
        Map.entry("search", "<circle cx=\"11\" cy=\"11\" r=\"7\"/><path d=\"M21 21l-4.3-4.3\"/>"),
        Map.entry("x", "<path d=\"M6 6l12 12M18 6L6 18\"/>"),
        Map.entry("globe", "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18\"/>"),
        Map.entry("check", "<path d=\"M5 12l5 5 9-10\"/>"),
        Map.entry("menu", "<path d=\"M4 7h16M4 12h16M4 17h16\"/>"),
        Map.entry("trash", "<path d=\"M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3\"/>"),
        Map.entry("dots", "<circle cx=\"5\" cy=\"12\" r=\"1.3\"/><circle cx=\"12\" cy=\"12\" r=\"1.3\"/><circle cx=\"19\" cy=\"12\" r=\"1.3\"/>"));

    static String icon(String name) {
        return "<svg class=\"i\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">" + ICONS.getOrDefault(name, "") + "</svg>";
    }

    static String flash(String kind, String html) {
        return "<div class=\"alert alert-" + kind + "\">" + html + "</div>\n";
    }

    /** 1536 -> « 1,5 Ko » ; 3 145 728 -> « 3,0 Mo ». */
    static String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024 * 1024) return String.format(Locale.FRANCE, "%.0f Ko", bytes / 1024.0);
        return String.format(Locale.FRANCE, "%.1f Mo", bytes / 1048576.0);
    }

    private static String pageHead(String title, String sub, String actionsHtml) {
        return "<div class=\"page-head\"><div><h1>" + esc(title) + "</h1>"
            + (sub == null ? "" : "<p class=\"sub\">" + sub + "</p>") + "</div>"
            + (actionsHtml == null ? "" : "<div class=\"actions\">" + actionsHtml + "</div>") + "</div>\n";
    }

    // ------------------------------------------------------------------ mise en page

    String layout(Ctx c, String title, String page, String body) {
        StringBuilder sb = new StringBuilder(body.length() + 6000);
        sb.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <meta name=\"color-scheme\" content=\"dark\">\n")
            .append("  <link rel=\"icon\" type=\"image/png\" href=\"").append(branding.hasIcon() ? "/branding/icon" : "/static/images/icon.png").append("\">\n")
            .append("  <title>").append(esc(title)).append(" - WebStream Manager</title>\n")
            .append("  <link rel=\"stylesheet\" href=\"/static/styles.css\">\n")
            .append("</head>\n<body data-page=\"").append(esc(page)).append("\" data-base-url=\"").append(esc(baseUrl(c)))
            .append("\" data-url-query=\"").append(esc(settings.urlQuery)).append("\">\n<div class=\"app\">\n");

        // barre latérale
        sb.append("  <aside class=\"sidebar\" id=\"sidebar\">\n");
        if (branding.hasBanner()) {
            sb.append("    <a href=\"/\"><img src=\"/branding/banner\" alt=\"WebStream Manager\" class=\"banner-img\" /></a>\n");
        } else {
            sb.append("    <a href=\"/\" class=\"brand\"><img src=\"/static/images/icon.png\" alt=\"\" class=\"brand-icon\" /><span>WEB<b>STREAM</b></span></a>\n");
        }
        profileSwitch(sb);
        sb.append("    <nav class=\"nav\" aria-label=\"Navigation\">\n");
        nav(sb, "/", "home", "Tableau de bord", "home", page);
        nav(sb, "/screens", "monitor", "Écrans", "screens", page);
        nav(sb, "/library", "image", "Bibliothèque", "library", page);
        nav(sb, "/families", "folder", "Familles", "families", page);
        nav(sb, "/profiles", "layers", "Profils", "profiles", page);
        nav(sb, "/data", "database", "Données", "data", page);
        nav(sb, "/settings", "settings", "Réglages", "settings", page);
        sb.append("    </nav>\n    <div class=\"sidebar-foot\">\n")
            .append("      <span><span class=\"dot ok\"></span>Interface · port ").append(settings.port).append("</span>\n");
        if (settings.publicPort > 0) {
            sb.append("      <span><span class=\"dot ok\"></span>Images publiques · port ").append(settings.publicPort).append("</span>\n");
        } else {
            sb.append("      <a href=\"/settings\" title=\"Partager les images avec les autres joueurs\"><span class=\"dot warn\"></span>Images non partagées</a>\n");
        }
        sb.append("      <span class=\"clock\" id=\"clock\" aria-label=\"Horloge\"></span>\n")
            .append("    </div>\n  </aside>\n  <div class=\"backdrop\" id=\"backdrop\"></div>\n");

        // contenu
        sb.append("  <div class=\"main\">\n    <header class=\"topbar\">\n")
            .append("      <button class=\"nav-toggle\" id=\"navToggle\" aria-label=\"Menu\" aria-controls=\"sidebar\" aria-expanded=\"false\">").append(icon("menu")).append("</button>\n")
            .append("      <strong>").append(esc(ws.activeProfileName())).append("</strong>\n    </header>\n")
            .append("    <main class=\"content\">\n").append(body).append("\n    </main>\n")
            .append("    <footer class=\"site-foot\">WebStream Manager ").append(esc(version)).append(" // signal ok</footer>\n  </div>\n</div>\n")
            .append("<div class=\"toasts\" id=\"toasts\" aria-live=\"polite\"></div>\n")
            .append("<script src=\"/static/app.js\"></script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static void nav(StringBuilder sb, String href, String icon, String label, String key, String current) {
        sb.append("      <a href=\"").append(href).append("\"").append(key.equals(current) ? " class=\"active\" aria-current=\"page\"" : "").append(">")
            .append(icon(icon)).append("<span>").append(label).append("</span></a>\n");
    }

    private void profileSwitch(StringBuilder sb) {
        String world = ws.currentWorld();
        int screens = ws.listScreens().size();
        sb.append("    <div class=\"profile-switch\" id=\"profileSwitch\">\n")
            .append("      <button type=\"button\" class=\"ps-current\" aria-haspopup=\"true\" aria-expanded=\"false\">\n")
            .append("        <span class=\"ps-label\">En diffusion</span>\n")
            .append("        <span class=\"ps-name\">").append(esc(ws.activeProfileName())).append("</span>\n")
            .append("        <span class=\"ps-sub\">").append(world == null ? "" : "Monde " + esc(world) + " · ").append(screens).append(" écran(s)</span>\n")
            .append("        <span class=\"ps-caret\">▾</span>\n      </button>\n")
            .append("      <div class=\"ps-menu\" hidden>\n");
        int channel = 0;
        for (ProfileInfo p : ws.listProfiles()) {
            sb.append("        <button type=\"button\" class=\"ps-item").append(p.active() ? " active" : "").append("\" data-profile-id=\"").append(esc(p.id()))
                .append("\"><span>").append(esc(p.name())).append("</span><small>").append(channelLabel(++channel)).append("</small></button>\n");
        }
        sb.append("        <a class=\"ps-manage\" href=\"/profiles\">Gérer les profils…</a>\n      </div>\n    </div>\n");
    }

    /** « CH 01 » : chaque profil est une chaîne que l'on peut diffuser sur les écrans. */
    static String channelLabel(int n) {
        return String.format("CH %02d", n);
    }

    String notFound(Ctx c) {
        return layout(c, "Page introuvable", "", "<div class=\"empty\"><strong>404 — page introuvable</strong>"
            + "<a class=\"btn btn-primary\" href=\"/\">Retour au tableau de bord</a></div>");
    }

    // ------------------------------------------------------------------ tableau de bord

    String home(Ctx c) {
        List<LibraryFile> lib = ws.listLibraryFilesInfo();
        long bytes = lib.stream().mapToLong(LibraryFile::size).sum();
        int screens = ws.listScreens().size();
        long assigned = ws.listScreens().stream().filter(s -> s.content() != null).count();
        String example = screens > 0 ? screenUrl(c, ws.listScreens().get(0).ref()) : baseUrl(c) + "/mon-ecran.png"
            + (settings.urlQuery.isBlank() ? "" : "?" + settings.urlQuery);

        StringBuilder b = new StringBuilder();
        b.append(pageHead("Tableau de bord", "Profil <strong>" + esc(ws.activeProfileName()) + "</strong>"
            + (ws.currentWorld() == null ? "" : " · monde <strong>" + esc(ws.currentWorld()) + "</strong>"), null));

        b.append("<div class=\"stats\">\n");
        stat(b, "/screens", "monitor", String.valueOf(screens), "écran(s) · " + assigned + " avec image");
        stat(b, "/library", "image", String.valueOf(lib.size()), "image(s) · " + fmtSize(bytes));
        stat(b, "/families", "folder", String.valueOf(ws.listFamilies().size()), "famille(s)");
        stat(b, "/profiles", "layers", String.valueOf(ws.listProfiles().size()), "profil(s)");
        b.append("</div>\n<div class=\"card-grid\">\n");

        b.append("<section class=\"card\"><h2>Adresse d'un écran</h2>\n")
            .append("<p class=\"sub\">À coller dans le bloc d'affichage WebStreamer, en jeu.</p>\n")
            .append("<div class=\"copybox\"><code>").append(esc(example)).append("</code>")
            .append("<button type=\"button\" class=\"btn btn-sm\" data-copy=\"").append(esc(example)).append("\">").append(icon("copy")).append("Copier</button></div>\n")
            .append("<p class=\"hint\" style=\"margin-top:.7rem\">Chaque écran a une adresse fixe : changer son image ici met à jour l'affichage en jeu.</p></section>\n");

        b.append("<section class=\"card\"><h2>Multijoueur</h2>\n");
        if (settings.publicPort > 0) {
            boolean hasUrl = settings.publicUrl != null && !settings.publicUrl.isBlank();
            b.append("<p><span class=\"chip chip-ok\">").append(icon("globe")).append("Port public ouvert : ").append(settings.publicPort).append("</span></p>\n")
                .append(hasUrl ? "<p class=\"muted\">Adresse publique : <code>" + esc(settings.publicUrl) + "</code></p>\n"
                    : "<p class=\"muted\">Renseignez l'<strong>adresse publique</strong> pour que les adresses copiées fonctionnent chez les autres joueurs.</p>\n");
        } else {
            b.append("<p><span class=\"chip chip-warn\">Images non partagées</span></p>\n")
                .append("<p class=\"muted\">Seule cette machine voit les écrans. Pour jouer à plusieurs, ouvrez un port public (images seulement).</p>\n");
        }
        b.append("<div class=\"actions\"><a class=\"btn btn-primary\" href=\"/settings\">").append(icon("settings")).append("Réglages multijoueur</a>")
            .append("<button type=\"button\" class=\"btn\" id=\"quickTest\">Tester</button><span id=\"quickTestResult\" class=\"muted\"></span></div></section>\n</div>\n");

        b.append("<section class=\"card\"><h2>Pour commencer</h2><ol class=\"steps\">\n")
            .append("<li><span><a href=\"/library\">Bibliothèque</a> : envoyez vos images.</span></li>\n")
            .append("<li><span><a href=\"/screens\">Écrans</a> : créez un écran et assignez-lui une image.</span></li>\n")
            .append("<li><span>Copiez son adresse (bouton sur la carte) et collez-la dans le bloc WebStreamer.</span></li>\n")
            .append("<li><span><a href=\"/profiles\">Profils</a> : une configuration par monde, ou changez à tout moment depuis la barre latérale.</span></li>\n")
            .append("</ol></section>\n");
        return b.toString();
    }

    private static void stat(StringBuilder b, String href, String icon, String number, String label) {
        b.append("<a class=\"stat\" href=\"").append(href).append("\"><span class=\"ico\">").append(icon(icon)).append("</span><span><span class=\"num\">")
            .append(esc(number)).append("</span><br><span class=\"lbl\">").append(esc(label)).append("</span></span></a>\n");
    }

    // ------------------------------------------------------------------ bibliothèque

    String library(Ctx c, List<LibraryFile> files, Map<String, List<String>> usage, String errorHtml) {
        long bytes = files.stream().mapToLong(LibraryFile::size).sum();
        StringBuilder b = new StringBuilder();
        b.append(pageHead("Bibliothèque", files.size() + " image(s) · " + fmtSize(bytes) + " — communes à tous les profils",
            "<button type=\"button\" class=\"btn btn-primary\" id=\"uploadBtn\">" + icon("upload") + "Envoyer des images</button>"));
        if (errorHtml != null) b.append(flash("error", errorHtml));

        b.append("<form id=\"uploadForm\" action=\"/library/upload\" method=\"post\" enctype=\"multipart/form-data\" hidden>")
            .append("<input type=\"file\" id=\"fileInput\" name=\"files\" accept=\"image/*\" multiple /></form>\n")
            .append("<div class=\"dropzone\" id=\"dropzone\" tabindex=\"0\" role=\"button\"><strong>Glissez-déposez vos images ici</strong>")
            .append("<span>ou cliquez pour choisir des fichiers (PNG, JPG, GIF, WebP, BMP, TIFF, SVG)</span>")
            .append("<div class=\"progress\" id=\"uploadProgress\" hidden style=\"width:min(420px,90%)\"><i></i></div></div>\n");

        b.append("<div class=\"toolbar\"><div class=\"searchbox grow\">").append(icon("search"))
            .append("<input type=\"search\" id=\"searchInput\" placeholder=\"Rechercher une image…\" autocomplete=\"off\" aria-label=\"Rechercher\" /></div>\n")
            .append("<div class=\"seg\" id=\"sizeSeg\" role=\"group\" aria-label=\"Taille des vignettes\">")
            .append("<button type=\"button\" class=\"active\" data-size=\"small\">Petites</button>")
            .append("<button type=\"button\" data-size=\"medium\">Moyennes</button>")
            .append("<button type=\"button\" data-size=\"large\">Grandes</button></div></div>\n");

        b.append("<div id=\"library-grid\" class=\"grid size-small\">\n");
        if (files.isEmpty()) {
            b.append("<div class=\"empty no-files\"><strong>Aucune image pour le moment</strong>Déposez vos premières images ci-dessus.</div>\n");
        }
        for (LibraryFile f : files) {
            List<String> used = usage.getOrDefault(f.name(), List.of());
            JsonArray refs = new JsonArray();
            used.forEach(refs::add);
            b.append("<div class=\"library-item\" tabindex=\"0\" data-file=\"").append(esc(f.name())).append("\" data-filename=\"").append(esc(f.name().toLowerCase(Locale.ROOT)))
                .append("\" data-size=\"").append(esc(fmtSize(f.size()))).append("\" data-usage=\"").append(esc(refs.toString())).append("\">\n")
                .append("  <img src=\"/thumb/").append(urlEnc(f.name())).append("\" alt=\"").append(esc(f.name())).append("\" loading=\"lazy\" decoding=\"async\" />\n")
                .append("  <div class=\"meta\"><span class=\"name\" title=\"").append(esc(f.name())).append("\">").append(esc(f.name())).append("</span>")
                .append("<span><span class=\"chip chip-muted\">").append(esc(fmtSize(f.size()))).append("</span> ")
                .append(used.isEmpty() ? "" : "<span class=\"chip\">" + used.size() + " écran(s)</span>").append("</span></div>\n</div>\n");
        }
        b.append("</div>\n");
        appendModalShell(b, "libraryModal");
        return b.toString();
    }

    private static void appendModalShell(StringBuilder b, String id) {
        b.append("<div class=\"modal\" id=\"").append(id).append("\" role=\"dialog\" aria-modal=\"true\">\n<div class=\"modal-backdrop\" data-close></div>\n")
            .append("<div class=\"modal-dialog\"><div class=\"modal-head\"><div class=\"modal-title\"></div>")
            .append("<button type=\"button\" class=\"btn btn-ghost btn-icon\" data-close aria-label=\"Fermer\">").append(icon("x")).append("</button></div>\n")
            .append("<div class=\"modal-body\"></div></div></div>\n");
    }

    // ------------------------------------------------------------------ écrans

    private String tile(Ctx c, ScreenInfo s, boolean showFamily) {
        double wd = s.width() == null ? 16 : s.width();
        double hd = s.height() == null ? 9 : s.height();
        String src = s.content() != null
            ? "/thumb/" + urlEnc(s.content())
            : "/screens/placeholder/" + Math.round(wd * 100) + "/" + Math.round(hd * 100) + ".svg";
        String url = screenUrl(c, s.ref());
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"screen-card\" data-search=\"").append(esc((s.ref() + " " + (s.family() == null ? "" : s.family())).toLowerCase(Locale.ROOT))).append("\">\n")
            .append("  <button type=\"button\" class=\"screen-tile\" data-ref=\"").append(esc(s.ref())).append("\" data-content=\"").append(esc(s.content()))
            .append("\" data-width=\"").append(num(s.width())).append("\" data-height=\"").append(num(s.height())).append("\" data-family-id=\"")
            .append(s.familyId() == null ? "" : s.familyId()).append("\">\n")
            .append("    <div class=\"sc-frame\" style=\"--w: ").append(num(wd)).append("; --h: ").append(num(hd)).append(";\">")
            .append("<img src=\"").append(src).append("\" alt=\"").append(esc(s.ref())).append("\" loading=\"lazy\" decoding=\"async\" /></div>\n")
            .append("    <div class=\"sc-meta\"><div class=\"sc-title\"><code>").append(esc(s.ref())).append("</code></div>\n")
            .append("      <div class=\"sc-sub\">").append(s.width() == null ? "—" : num(s.width())).append(" × ").append(s.height() == null ? "—" : num(s.height())).append("</div>\n");
        if (showFamily) b.append("      <div class=\"sc-family\">").append(s.family() == null ? "Sans famille" : esc(s.family())).append("</div>\n");
        b.append("    </div>\n  </button>\n")
            .append("  <span class=\"tile-status chip ").append(s.content() == null ? "chip-warn\">sans image" : "chip-ok\">image").append("</span>\n")
            .append("  <button type=\"button\" class=\"btn btn-sm btn-icon tile-copy\" data-copy=\"").append(esc(url)).append("\" title=\"Copier l'adresse\" aria-label=\"Copier l'adresse\">")
            .append(icon("copy")).append("</button>\n</div>\n");
        return b.toString();
    }

    String screens(Ctx c, String mode) {
        boolean all = mode.equals("all");
        StringBuilder b = new StringBuilder();
        b.append(pageHead("Écrans", "Profil <strong>" + esc(ws.activeProfileName()) + "</strong> — cliquez sur une carte pour la modifier",
            "<button type=\"button\" class=\"btn btn-primary\" id=\"addScreenBtn\">" + icon("plus") + "Nouvel écran</button>"));
        b.append("<div class=\"toolbar\"><div class=\"seg\">")
            .append("<a class=\"").append(all ? "" : "active").append("\" href=\"/screens?mode=family\">Par famille</a>")
            .append("<a class=\"").append(all ? "active" : "").append("\" href=\"/screens?mode=all\">Tous</a></div>\n")
            .append("<div class=\"searchbox grow\">").append(icon("search")).append("<input type=\"search\" id=\"screenFilter\" placeholder=\"Filtrer les écrans…\" autocomplete=\"off\" aria-label=\"Filtrer\" /></div>\n")
            .append("<a class=\"btn\" href=\"/families\">").append(icon("folder")).append("Gérer les familles</a></div>\n");

        if (all) {
            List<ScreenInfo> list = ws.listScreens();
            if (list.isEmpty()) b.append(emptyScreens());
            b.append("<div class=\"screen-grid\">\n");
            for (ScreenInfo s : list) b.append(tile(c, s, true));
            b.append("</div>\n");
        } else {
            List<ScreenGroup> groups = ws.listScreensByFamily();
            if (groups.isEmpty() || groups.stream().allMatch(g -> g.screens().isEmpty())) b.append(emptyScreens());
            for (ScreenGroup g : groups) {
                b.append("<details class=\"family-group\" open>\n<summary><strong>").append(esc(g.family())).append("</strong> <span class=\"chip chip-muted\">")
                    .append(g.screens().size()).append("</span></summary>\n<div class=\"screen-grid\">\n");
                for (ScreenInfo s : g.screens()) b.append(tile(c, s, false));
                b.append("</div>\n</details>\n");
            }
        }
        appendModalShell(b, "screenModal");

        JsonArray fams = new JsonArray();
        for (FamilyInfo f : ws.listFamilies()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("name", f.name());
            fams.add(o);
        }
        JsonArray lib = new JsonArray();
        ws.listLibraryFiles().forEach(lib::add);
        b.append("<div id=\"screens-data\" data-families=\"").append(esc(fams.toString())).append("\" data-library=\"").append(esc(lib.toString())).append("\" hidden></div>\n");
        return b.toString();
    }

    private static String emptyScreens() {
        return "<div class=\"empty\" style=\"margin-bottom:1rem\"><strong>Aucun écran pour le moment</strong>Créez votre premier écran avec « Nouvel écran ».</div>\n";
    }

    // ------------------------------------------------------------------ familles

    String families(Ctx c, String errorHtml) {
        List<FamilyInfo> list = ws.listFamilies();
        StringBuilder b = new StringBuilder();
        b.append(pageHead("Familles", "Rangez vos écrans par famille (Métro, Parc, Musée…) — profil <strong>" + esc(ws.activeProfileName()) + "</strong>",
            "<a class=\"btn\" href=\"/screens\">Retour aux écrans</a>"));
        if (errorHtml != null) b.append(flash("error", errorHtml));
        b.append("<form class=\"card\" method=\"post\" action=\"/families/create\"><h2>Nouvelle famille</h2>")
            .append("<div class=\"form-row\"><input name=\"name\" placeholder=\"MTR, Parc, Musée…\" required maxlength=\"60\" aria-label=\"Nom de la famille\" />")
            .append("<button class=\"btn btn-primary\" type=\"submit\">").append(icon("plus")).append("Créer</button></div></form>\n");
        b.append("<div class=\"card\"><h2>Vos familles</h2>\n");
        if (list.isEmpty()) {
            b.append("<div class=\"empty\"><strong>Aucune famille</strong>Les écrans sans famille apparaissent dans « Sans famille ».</div>\n");
        } else {
            b.append("<table class=\"table\"><thead><tr><th>Nom</th><th>Écrans</th><th></th></tr></thead><tbody>\n");
            for (FamilyInfo f : list) {
                b.append("<tr><td><strong>").append(esc(f.name())).append("</strong></td><td>").append(f.screenCount()).append("</td><td style=\"text-align:right\">")
                    .append("<form method=\"post\" action=\"/families/").append(f.id()).append("/delete\" data-confirm=\"Supprimer la famille « ").append(esc(f.name())).append(" » ?\">")
                    .append("<button type=\"submit\" class=\"btn btn-sm btn-danger\"").append(f.screenCount() > 0 ? " disabled title=\"Des écrans utilisent cette famille\"" : "")
                    .append(">").append(icon("trash")).append("Supprimer</button></form></td></tr>\n");
            }
            b.append("</tbody></table>\n");
        }
        b.append("</div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ profils

    String profiles(Ctx c, String flashHtml, String flashKind) {
        List<ProfileInfo> list = ws.listProfiles();
        String world = ws.currentWorld();
        StringBuilder b = new StringBuilder();
        b.append(pageHead("Profils", "Un profil = une configuration complète (familles + écrans). Les images sont communes. "
            + "Changer de profil change instantanément ce que montrent vos écrans en jeu.", null));
        if (flashHtml != null) b.append(flash(flashKind, flashHtml));
        if (world != null) {
            b.append(flash("ok", "<p>Monde ouvert : <strong>" + esc(world) + "</strong>. Activer un profil ici le retient pour ce monde.</p>"));
        }

        b.append("<div class=\"profile-grid\">\n");
        int channel = 0;
        for (ProfileInfo p : list) {
            String id = esc(p.id());
            b.append("<article class=\"profile-card").append(p.active() ? " active" : "").append("\">\n<div class=\"top\"><div><div class=\"channel\">")
                .append(channelLabel(++channel)).append("</div><h3>").append(esc(p.name())).append("</h3>")
                .append("<div class=\"profile-meta\" style=\"margin-top:.4rem\"><span class=\"chip chip-muted\">").append(p.screenCount()).append(" écran(s)</span>")
                .append("<span class=\"chip chip-muted\">").append(p.familyCount()).append(" famille(s)</span>");
            for (String w : p.worlds()) b.append("<span class=\"chip\">Monde ").append(esc(w)).append("</span>");
            b.append("</div></div>").append(p.active() ? "<span class=\"chip chip-live\">en diffusion</span>" : "").append("</div>\n");

            if (p.previews().isEmpty()) {
                b.append("<div class=\"mosaic empty-m\">Aucune image</div>\n");
            } else {
                b.append("<div class=\"mosaic\">");
                for (String f : p.previews()) b.append("<img src=\"/thumb/").append(urlEnc(f)).append("\" alt=\"\" loading=\"lazy\" decoding=\"async\" />");
                for (int i = p.previews().size(); i < 4; i++) b.append("<span></span>");
                b.append("</div>\n");
            }

            b.append("<div class=\"profile-actions\">\n");
            if (!p.active()) {
                b.append("<form method=\"post\" action=\"/profiles/").append(id).append("/activate\">")
                    .append(world != null ? "<input type=\"hidden\" name=\"bind\" value=\"1\" />" : "")
                    .append("<button type=\"submit\" class=\"btn btn-primary\">Activer</button></form>\n");
            }
            b.append("<details class=\"more\"><summary class=\"btn btn-icon\" aria-label=\"Autres actions\">").append(icon("dots")).append("</summary><div class=\"more-menu\">\n")
                .append("<form method=\"post\" action=\"/profiles/").append(id).append("/duplicate\"><button type=\"submit\" class=\"btn btn-sm\">").append(icon("copy")).append("Dupliquer</button></form>\n")
                .append("<form method=\"post\" action=\"/profiles/").append(id).append("/rename\"><input name=\"name\" value=\"").append(esc(p.name()))
                .append("\" maxlength=\"60\" aria-label=\"Nouveau nom\" /><button type=\"submit\" class=\"btn btn-sm\">OK</button></form>\n")
                .append("<a class=\"btn btn-sm\" href=\"/data/export?profile=").append(id).append("\">").append(icon("download")).append("Exporter (.json)</a>\n")
                .append("<form method=\"post\" action=\"/profiles/").append(id).append("/delete\" data-confirm=\"Supprimer le profil « ").append(esc(p.name())).append(" » et tous ses écrans ?\">")
                .append("<button type=\"submit\" class=\"btn btn-sm btn-danger\"").append(p.active() ? " disabled title=\"Le profil actif ne peut pas être supprimé\"" : "").append(">")
                .append(icon("trash")).append("Supprimer</button></form>\n</div></details>\n</div>\n</article>\n");
        }

        b.append("<form class=\"profile-card new\" method=\"post\" action=\"/profiles/create\">\n<h3>").append(icon("plus")).append(" Nouveau profil</h3>\n")
            .append("<label class=\"field\">Nom<input name=\"name\" placeholder=\"Monde créatif, Événement…\" required maxlength=\"60\" /></label>\n")
            .append("<label class=\"field\">Copier depuis<select name=\"cloneFrom\"><option value=\"\">— profil vide —</option>");
        for (ProfileInfo p : list) b.append("<option value=\"").append(esc(p.id())).append("\">").append(esc(p.name())).append("</option>");
        b.append("</select></label>\n<button type=\"submit\" class=\"btn btn-primary\">Créer le profil</button>\n</form>\n</div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ données (export / import)

    String data(Ctx c, ImportResult result, String errorHtml) {
        List<ProfileInfo> profiles = ws.listProfiles();
        StringBuilder b = new StringBuilder();
        b.append(pageHead("Données", "Exportez ou importez vos familles et écrans (fichier JSON). Les images ne sont pas incluses.", null));
        if (errorHtml != null) b.append(flash("error", "<p>" + errorHtml + "</p>"));
        if (result != null) {
            StringBuilder r = new StringBuilder("<p><strong>Import terminé</strong> dans le profil « ").append(esc(result.profileName)).append(" »</p><ul>")
                .append("<li>").append(result.screensAdded).append(" écran(s) ajouté(s)</li>")
                .append("<li>").append(result.screensUpdated).append(" écran(s) mis à jour</li>")
                .append("<li>").append(result.screensSkipped).append(" écran(s) ignoré(s) (déjà présents)</li>")
                .append("<li>").append(result.familiesAdded).append(" famille(s) ajoutée(s)</li>");
            if (result.screensInvalid > 0) r.append("<li>").append(result.screensInvalid).append(" entrée(s) invalide(s) ignorée(s)</li>");
            r.append("</ul>");
            if (!result.missingFiles.isEmpty()) {
                r.append("<p style=\"margin-top:.6rem\"><strong>").append(result.missingFiles.size())
                    .append(" image(s) référencée(s) absente(s) de la bibliothèque</strong> — envoyez-les depuis la <a href=\"/library\">Bibliothèque</a> :</p><ul>");
                for (String f : result.missingFiles) r.append("<li><code>").append(esc(f)).append("</code></li>");
                r.append("</ul>");
            }
            b.append(flash(result.missingFiles.isEmpty() ? "ok" : "warn", r.toString()));
        }

        b.append("<div class=\"card-grid\">\n<section class=\"card\"><h2>Exporter</h2>\n")
            .append("<p class=\"muted\">Profil « <strong>").append(esc(ws.activeProfileName())).append("</strong> » — importable dans un autre profil ou un autre monde.</p>\n")
            .append("<a class=\"btn btn-primary\" href=\"/data/export\">").append(icon("download")).append("Télécharger l'export (.json)</a></section>\n");

        b.append("<form class=\"card\" method=\"post\" action=\"/data/import\" enctype=\"multipart/form-data\"><h2>Importer</h2>\n")
            .append("<p class=\"muted\">Un export <code>.json</code>, y compris ceux des versions précédentes du mod.</p>\n")
            .append("<label class=\"field\">Fichier<input type=\"file\" name=\"files\" accept=\".json\" required /></label>\n")
            .append("<div class=\"opt\"><strong>Importer dans</strong>\n")
            .append("<label><input type=\"radio\" name=\"target\" value=\"active\" checked /> Le profil actif (« ").append(esc(ws.activeProfileName())).append(" »)</label>\n")
            .append("<label><input type=\"radio\" name=\"target\" value=\"new\" /> Un nouveau profil : <input name=\"newName\" placeholder=\"Nom du profil\" maxlength=\"60\" style=\"width:auto;display:inline-block\" /></label>\n");
        if (profiles.size() > 1) {
            b.append("<label><input type=\"radio\" name=\"target\" value=\"other\" /> Un autre profil : <select name=\"otherId\" style=\"width:auto;display:inline-block\">");
            for (ProfileInfo p : profiles) if (!p.active()) b.append("<option value=\"").append(esc(p.id())).append("\">").append(esc(p.name())).append("</option>");
            b.append("</select></label>\n");
        }
        b.append("</div>\n<div class=\"opt\"><strong>Si un écran existe déjà</strong>\n")
            .append("<label><input type=\"radio\" name=\"mode\" value=\"skip\" checked /> Le garder tel quel (ajouter seulement les nouveaux)</label>\n")
            .append("<label><input type=\"radio\" name=\"mode\" value=\"overwrite\" /> Le mettre à jour avec le fichier importé</label>\n")
            .append("<label><input type=\"radio\" name=\"mode\" value=\"replace\" /> Tout remplacer (supprime les écrans et familles du profil cible)</label></div>\n")
            .append("<button type=\"submit\" class=\"btn btn-primary\" data-confirm-if=\"mode=replace\" data-confirm-text=\"Supprimer tous les écrans et familles du profil cible avant l'import ?\">")
            .append(icon("upload")).append("Importer</button></form>\n</div>\n");
        return b.toString();
    }

    // ------------------------------------------------------------------ réglages

    private static String sw(String name, boolean checked, String label, String hint) {
        return "<label class=\"switch\"><input type=\"checkbox\" name=\"" + name + "\"" + (checked ? " checked" : "") + " /><span class=\"track\"></span>"
            + "<span class=\"label\">" + label + (hint == null ? "" : "<small>" + hint + "</small>") + "</span></label>";
    }

    String settings(Ctx c, WebSettings s, List<String> errors, String flashKind, String notice, List<String> lan, boolean passwordSet) {
        StringBuilder b = new StringBuilder();
        b.append(pageHead("Réglages", "Multijoueur, accès et options. Les changements s'appliquent sans redémarrer Minecraft.", null));

        if (!errors.isEmpty()) {
            StringBuilder ul = new StringBuilder("<p><strong>Réglages non enregistrés :</strong></p><ul>");
            for (String e : errors) ul.append("<li>").append(esc(e)).append("</li>");
            b.append(flash("error", ul.append("</ul>").toString()));
        }
        if (notice != null) b.append(flash("warn", "<p>" + esc(notice) + "</p>"));
        if ("saved".equals(flashKind)) b.append(flash("ok", "<p>Réglages enregistrés.</p>"));
        if ("restart".equals(flashKind)) {
            b.append("<div class=\"alert alert-ok\" id=\"restartNotice\" data-port=\"").append(s.port).append("\"><p>Réglages enregistrés. Le serveur web redémarre sur le nouveau port… vous allez être redirigé.</p></div>\n");
        }

        boolean publicOn = s.publicPort > 0;
        int suggestPort = publicOn ? s.publicPort : 8283;
        b.append("<form method=\"post\" action=\"/settings\" class=\"settings-layout\" id=\"settingsForm\">\n");

        // multijoueur
        b.append("<section class=\"card\"><h2>Multijoueur — partager les images</h2>\n")
            .append("<p class=\"sub\">WebStreamer télécharge les images depuis le PC de <strong>chaque joueur</strong> : ils doivent pouvoir joindre ce serveur.</p>\n")
            .append("<div class=\"opt\">").append(sw("publicEnabled", publicOn, "Ouvrir un port public (images seulement)",
                "L'interface d'administration reste privée : ce port ne sert que les images des écrans.")).append("\n")
            .append("<div class=\"only-when\" data-when=\"publicEnabled\"").append(publicOn ? "" : " hidden").append(">")
            .append("<label class=\"field\">Port public<input type=\"number\" name=\"publicPort\" min=\"1\" max=\"65535\" value=\"").append(suggestPort).append("\" id=\"publicPort\" />")
            .append("<span class=\"hint\">À ouvrir / rediriger sur votre box, votre pare-feu ou votre tunnel (playit.gg…).</span></label></div></div>\n")
            .append("<div class=\"opt\"><label class=\"field\">Adresse publique<input name=\"publicUrl\" id=\"publicUrl\" value=\"").append(esc(s.publicUrl))
            .append("\" placeholder=\"http://mon-serveur.fr:8283\" /><span class=\"hint\">L'adresse que les joueurs utilisent pour joindre ce port. Elle sert à générer les adresses copiées.</span></label>\n");
        if (!lan.isEmpty()) {
            b.append("<div><span class=\"hint\">Adresses de cette machine sur votre réseau local (pour jouer en LAN) :</span><div class=\"suggest\" style=\"margin-top:.4rem\">");
            for (String ip : lan) b.append("<button type=\"button\" class=\"btn btn-sm\" data-suggest-ip=\"").append(esc(ip)).append("\"><span>http://").append(esc(ip)).append(":<span data-port-mirror>").append(suggestPort).append("</span></span></button>");
            b.append("</div></div>\n");
        }
        b.append("<div><button type=\"button\" class=\"btn\" id=\"testBtn\">Tester l'adresse</button>\n")
            .append("<div class=\"test-result\" id=\"testResult\" aria-live=\"polite\"></div>\n")
            .append("<p class=\"hint\">Le test part de ce serveur : il vérifie que l'adresse répond, pas que le port est ouvert depuis Internet (box, pare-feu). "
                + "Testez depuis un autre réseau pour en être sûr.</p></div></div></section>\n");

        // accès
        b.append("<section class=\"card\"><h2>Accès à l'interface</h2>\n<div class=\"opt\">")
            .append("<label class=\"field\">Qui peut ouvrir l'interface ?<select name=\"bindAddress\">")
            .append(opt("127.0.0.1", "Cette machine seulement (recommandé)", s.bindAddress))
            .append(opt("0.0.0.0", "Toutes les machines du réseau (mot de passe requis)", s.bindAddress));
        if (!s.bindAddress.equals("127.0.0.1") && !s.bindAddress.equals("0.0.0.0")) b.append(opt(s.bindAddress, s.bindAddress, s.bindAddress));
        b.append("</select></label>\n");
        if (!s.isLoopbackBind() && !passwordSet) {
            b.append(flash("warn", "<p style=\"margin:0\">Sans mot de passe, les accès venant d'autres machines sont refusés.</p>"));
        }
        b.append("</div>\n<div class=\"opt\"><label class=\"field\">Mot de passe d'accès distant<input type=\"password\" name=\"adminPassword\" autocomplete=\"new-password\" placeholder=\"")
            .append(passwordSet ? "•••••••• (inchangé)" : "Aucun mot de passe").append("\" /><span class=\"hint\">Nom d'utilisateur libre. ")
            .append("Transmis en clair en HTTP : sur Internet, placez un proxy HTTPS devant.</span></label>\n");
        if (passwordSet) b.append("<label><input type=\"checkbox\" name=\"clearPassword\" /> Supprimer le mot de passe</label>\n");
        b.append("</div>\n<div class=\"opt\">").append(sw("trustLocalhost", s.trustLocalhost, "Faire confiance à cette machine",
            "Désactivez si vous tunnelisez le port de l'interface : même l'accès local demandera le mot de passe.")).append("</div>\n")
            .append("<div class=\"opt\"><label class=\"field\">Adresse de l'interface communiquée aux joueurs<input name=\"adminUrl\" value=\"").append(esc(s.adminUrl))
            .append("\" placeholder=\"http://mon-serveur.fr:8282\" /><span class=\"hint\">Optionnel : ouverte par la touche H / le bouton du menu pause chez les opérateurs.</span></label></div></section>\n");

        // profils
        b.append("<section class=\"card\"><h2>Profils</h2>\n<div class=\"opt\"><label class=\"field\">À la première ouverture d'un monde<select name=\"newWorldProfile\">")
            .append(opt(Workspace.MODE_PER_WORLD, "Créer un profil pour ce monde", s.newWorldProfile))
            .append(opt(Workspace.MODE_SHARED, "Utiliser le profil « Par défaut » (identique pour tous les mondes)", s.newWorldProfile))
            .append("</select><span class=\"hint\">Les mondes déjà connus gardent leur profil.</span></label></div></section>\n");

        // adresses et images
        b.append("<section class=\"card\"><h2>Adresses et images</h2>\n<div class=\"opt\"><label class=\"field\">Paramètre ajouté aux adresses copiées<input name=\"urlQuery\" value=\"")
            .append(esc(s.urlQuery)).append("\" placeholder=\"refresh=1\" /><span class=\"hint\">Sans le « ? ». WebStreamer garde une image en cache par adresse exacte ; ")
            .append("un paramètre permet de distinguer deux adresses. Exemple : <code>refresh=1</code>. Laissez vide pour aucune.</span></label></div>\n")
            .append("<div class=\"opt\">").append(sw("placeholderImage", s.placeholderImage, "Image « Aucun contenu » pour les écrans sans image",
                "Désactivé : l'écran répond « introuvable » et WebStreamer réessaie toutes les 30 s, puis affiche l'image dès qu'elle est assignée. "
                    + "Activé : l'image d'attente est mise en cache par WebStreamer tant que l'adresse ne change pas.")).append("</div>\n")
            .append("<div class=\"opt\"><label class=\"field\">Taille maximale d'une image envoyée (Mo)<input type=\"number\" name=\"maxUploadMb\" min=\"1\" max=\"2048\" value=\"")
            .append(s.maxUploadMb).append("\" /></label></div></section>\n");

        // avancé
        b.append("<section class=\"card\"><h2>Avancé</h2>\n<div class=\"form-grid\">")
            .append("<label class=\"field\">Port de l'interface<input type=\"number\" name=\"port\" min=\"1\" max=\"65535\" value=\"").append(s.port).append("\" /></label>")
            .append("<label class=\"field\">Écoute du port public<input name=\"publicBindAddress\" value=\"").append(esc(s.publicBindAddress)).append("\" /></label></div>\n")
            .append("<p class=\"hint\" style=\"margin-top:.7rem\">Changer un port redémarre le serveur web ; si le nouveau port est occupé, les anciens réglages sont rétablis.</p></section>\n");

        b.append("<div class=\"sticky-save\"><button type=\"submit\" class=\"btn btn-primary\">Enregistrer les réglages</button>")
            .append("<span class=\"muted\">Fichier : config/webstream.json</span></div>\n</form>\n");
        return b.toString();
    }

    private static String opt(String value, String label, String current) {
        return "<option value=\"" + esc(value) + "\"" + (value.equals(current) ? " selected" : "") + ">" + esc(label) + "</option>";
    }
}
