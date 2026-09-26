package com.babou.webstream;

import com.babou.webstream.core.Workspace;
import com.babou.webstream.web.WebService;
import com.babou.webstream.web.WebSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WebServiceTest {
    @TempDir
    Path root;

    Workspace ws;
    WebService web;
    WebSettings settings;
    HttpClient client;
    int publicPort;

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @BeforeEach
    void setUp() throws Exception {
        ws = new Workspace(root, Workspace.MODE_PER_WORLD);
        settings = new WebSettings();
        settings.port = 0;
        settings.language = "fr";
        settings.adminPassword = "secret";
        settings.maxUploadMb = 1;
        try (ServerSocket s = new ServerSocket(0)) {
            publicPort = s.getLocalPort();
        }
        settings.publicPort = publicPort;
        settings.publicBindAddress = "127.0.0.1";
        web = new WebService(ws, settings, "test");
        web.start();
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @AfterEach
    void tearDown() {
        web.stop();
    }

    // ------------------------------------------------------------------ helpers

    String url(String path) {
        return "http://localhost:" + web.adminPort() + path;
    }

    String publicUrl(String path) {
        return "http://localhost:" + publicPort + path;
    }

    HttpResponse<String> get(String url, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> getBytes(String url, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    HttpResponse<String> postForm(String path, String body, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> postJson(String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    record Upload(String field, String filename, byte[] data) {}

    HttpResponse<String> multipart(String path, Map<String, String> fields, List<Upload> files, String... headers) throws Exception {
        String boundary = "----WebKitFormBoundary" + Long.toHexString(new Random(7).nextLong());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (var e : fields.entrySet()) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (Upload u : files) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + u.field() + "\"; filename=\"" + u.filename()
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(u.data());
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Requête HTTP brute pour maîtriser l'en-tête Host (HttpClient l'interdit). Retourne le code de statut. */
    int rawStatus(String method, String path, String host, String extraHeaders) throws IOException {
        try (Socket s = new Socket("127.0.0.1", web.adminPort())) {
            OutputStream os = s.getOutputStream();
            os.write((method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n"
                + (method.equals("POST") ? "Content-Length: 0\r\n" : "") + extraHeaders + "\r\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            InputStream in = s.getInputStream();
            String head = new String(in.readNBytes(64), StandardCharsets.UTF_8);
            return Integer.parseInt(head.split(" ")[1]);
        }
    }

    static String sha(byte[] data) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data));
    }

    // ------------------------------------------------------------------ interface et statiques

    @Test
    void pagesRenderAndStaticFilesAreServed() throws Exception {
        for (String p : List.of("/", "/library", "/screens", "/screens?mode=all", "/families", "/profiles", "/data", "/settings")) {
            HttpResponse<String> r = get(url(p));
            assertEquals(200, r.statusCode(), p);
            assertTrue(r.body().contains("WebStream Manager"), p);
        }
        HttpResponse<String> css = get(url("/static/styles.css"));
        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
        assertEquals(200, getBytes(url("/static/images/icon.png")).statusCode());
        for (String font : List.of("pixelify-400.woff2", "pixelify-600.woff2", "silkscreen-400.woff2", "vt323-400.woff2")) {
            HttpResponse<byte[]> f = getBytes(url("/static/fonts/" + font));
            assertEquals(200, f.statusCode(), font);
            assertEquals("font/woff2", f.headers().firstValue("Content-Type").orElse(""), font);
        }
        assertEquals(200, get(url("/static/fonts/LICENSES.txt")).statusCode(), "les licences des polices sont distribuées avec le mod");
        assertTrue(css.body().contains("/static/fonts/vt323-400.woff2"));
        assertEquals(404, get(url("/static/inexistant.css")).statusCode());
        assertEquals(404, get(url("/static/%2e%2e/workspace.json")).statusCode());
        assertEquals(404, get(url("/static/images/..%2f..%2fpreview.js")).statusCode());
        assertEquals(404, get(url("/nimporte/quoi")).statusCode());
    }

    @Test
    void screenBaseUrlIsInjectedAndHtmlIsEscaped() throws Exception {
        settings.publicUrl = "http://mon-serveur:8283/";
        ws.createFamily("<script>alert(1)</script>");
        String html = get(url("/screens")).body();
        assertTrue(html.contains("data-base-url=\"http://mon-serveur:8283\""));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(get(url("/families")).body().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    // ------------------------------------------------------------------ images des écrans

    @Test
    void screenImagesAreServedLiveWithCacheValidators() throws Exception {
        assertEquals(404, getBytes(url("/inconnu.png")).statusCode());
        ws.createScreen("ecran1", null, null, null);

        assertEquals(404, getBytes(url("/ecran1.png")).statusCode(), "sans image : 404 par défaut, WebStreamer réessaie toutes les 30 s");
        settings.placeholderImage = true;
        HttpResponse<byte[]> placeholder = getBytes(url("/ecran1.png"));
        assertEquals(200, placeholder.statusCode());
        assertEquals("1", placeholder.headers().firstValue("X-WebStream-Placeholder").orElse(""));
        assertEquals("image/png", placeholder.headers().firstValue("Content-Type").orElse(""));

        Files.write(ws.libraryDir().resolve("a.png"), PNG);
        ws.assignContent("ecran1", "a.png");
        HttpResponse<byte[]> img = getBytes(url("/ecran1.png"));
        assertArrayEquals(PNG, img.body());
        assertEquals("no-cache", img.headers().firstValue("Cache-Control").orElse(""));
        String etag = img.headers().firstValue("ETag").orElseThrow();
        assertEquals(304, getBytes(url("/ecran1.png"), "If-None-Match", etag).statusCode());

        byte[] other = {(byte) 0x89, 'P', 'N', 'G', 9, 9, 9, 9, 9, 9, 9};
        Files.write(ws.libraryDir().resolve("b.png"), other);
        ws.assignContent("ecran1", "b.png");
        HttpResponse<byte[]> changed = getBytes(url("/ecran1.png"), "If-None-Match", etag);
        assertEquals(200, changed.statusCode());
        assertArrayEquals(other, changed.body());
    }

    @Test
    void switchingProfileChangesWhatScreensShow() throws Exception {
        Files.write(ws.libraryDir().resolve("a.png"), PNG);
        ws.createScreen("s", null, null, null);
        ws.assignContent("s", "a.png");
        ws.createProfile("Autre", null);
        assertArrayEquals(PNG, getBytes(url("/s.png")).body());

        assertEquals(303, postForm("/profiles/autre/activate", "").statusCode());
        assertEquals(404, getBytes(url("/s.png")).statusCode());
        assertEquals(303, postForm("/profiles/default/activate", "").statusCode());
        assertArrayEquals(PNG, getBytes(url("/s.png")).body());
    }

    @Test
    void contentEndpointIsSandboxedAndTraversalSafe() throws Exception {
        Files.write(ws.libraryDir().resolve("x.png"), PNG);
        HttpResponse<byte[]> r = getBytes(url("/content/x.png"));
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Security-Policy").orElse("").contains("sandbox"));
        assertEquals(404, getBytes(url("/content/..%2fworkspace.json")).statusCode());
        assertEquals(404, getBytes(url("/content/%2e%2e%5cworkspace.json")).statusCode());
        assertEquals(404, getBytes(url("/content/absent.png")).statusCode());
    }

    @Test
    void placeholderSvgIsGenerated() throws Exception {
        HttpResponse<String> r = get(url("/screens/placeholder/280/130.svg"));
        assertEquals(200, r.statusCode());
        assertTrue(r.body().startsWith("<svg"));
        assertTrue(r.body().contains("NO SIGNAL"));
    }

    // ------------------------------------------------------------------ envois de fichiers

    @Test
    void uploadStoresValidImagesAndRejectsFakes() throws Exception {
        HttpResponse<String> r = multipart("/library/upload", Map.of(), List.of(
            new Upload("files", "Mon Écran (1).png", PNG),
            new Upload("files", "faux.png", "<html>pas une image</html>".getBytes(StandardCharsets.UTF_8)),
            new Upload("files", "script.exe", PNG)));
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("faux.png"));
        assertTrue(r.body().contains("script.exe"));
        assertEquals(List.of("Mon__cran__1_.png"), ws.listLibraryFiles());

        HttpResponse<String> ok = multipart("/library/upload", Map.of(), List.of(new Upload("files", "b.png", PNG)));
        assertEquals(303, ok.statusCode());
        assertEquals(2, ws.listLibraryFiles().size());
        assertTrue(get(url("/library")).body().contains("/thumb/b.png"));
        assertEquals(0, Files.list(root.resolve("tmp")).count(), "aucun fichier temporaire ne doit rester");
    }

    @Test
    void uploadKeepsBinaryContentIntactEvenWithBoundaryLikeBytes() throws Exception {
        byte[] data = new byte[900_000];
        new Random(42).nextBytes(data);
        System.arraycopy(PNG, 0, data, 0, 4);
        byte[] trap = "\r\n------WebKitFormBoundary\r\n--".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(trap, 0, data, 500_000, trap.length);
        System.arraycopy(trap, 0, data, 65_530, trap.length);   // à cheval sur la frontière d'une fenêtre de lecture

        assertEquals(303, multipart("/library/upload", Map.of("autre", "champ"), List.of(new Upload("files", "gros.png", data))).statusCode());
        assertEquals(sha(data), sha(Files.readAllBytes(ws.libraryDir().resolve("gros.png"))));
    }

    @Test
    void uploadRejectsOversizedFiles() throws Exception {
        byte[] data = new byte[2 * 1024 * 1024];
        System.arraycopy(PNG, 0, data, 0, 4);
        HttpResponse<String> r = multipart("/library/upload", Map.of(), List.of(new Upload("files", "enorme.png", data)));
        assertEquals(413, r.statusCode());
        assertTrue(ws.listLibraryFiles().isEmpty());
        assertEquals(0, Files.list(root.resolve("tmp")).count());
    }

    @Test
    void uploadRejectsGarbageBodies() throws Exception {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(url("/library/upload")))
            .header("Content-Type", "multipart/form-data; boundary=xyz")
            .POST(HttpRequest.BodyPublishers.ofString("ceci n'est pas du multipart")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, r.statusCode());
    }

    @Test
    void deleteLibraryFileOverJson() throws Exception {
        Files.write(ws.libraryDir().resolve("x.png"), PNG);
        ws.createScreen("s", null, null, null);
        ws.assignContent("s", "x.png");
        assertEquals(409, postJson("/library/delete", "{\"file\":\"x.png\"}").statusCode());
        ws.unassignContent("s");
        assertEquals(200, postJson("/library/delete", "{\"file\":\"x.png\"}").statusCode());
        assertEquals(404, postJson("/library/delete", "{\"file\":\"x.png\"}").statusCode());
        assertEquals(400, postJson("/library/delete", "{\"file\":\"../a.png\"}").statusCode());
    }

    // ------------------------------------------------------------------ écrans, familles, profils

    @Test
    void screenFormFlowsMatchTheFrontEndContract() throws Exception {
        assertEquals(303, postForm("/families/create", "name=MTR").statusCode());
        int fam = ws.listFamilies().get(0).id();
        assertEquals(303, postForm("/screens/create", "ref=" + "Écran 1".replace(" ", "+") + "&familyId=" + fam + "&width=2%2C8&height=1.3").statusCode());
        assertEquals(2.8, ws.getScreen("Ecran_1").width());
        assertEquals(409, postForm("/screens/create", "ref=Ecran_1").statusCode());

        Files.write(ws.libraryDir().resolve("a.png"), PNG);
        HttpResponse<String> assign = postJson("/screens/Ecran_1/assign", "{\"file\":\"a.png\"}");
        assertEquals(200, assign.statusCode());
        assertTrue(assign.body().contains("\"ok\":true"));

        HttpResponse<String> dims = client.send(HttpRequest.newBuilder(URI.create(url("/screens/Ecran_1/dimensions")))
            .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("width=4&height=2")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, dims.statusCode());
        assertTrue(dims.body().contains("\"width\":4.0"));

        HttpResponse<String> missing = client.send(HttpRequest.newBuilder(URI.create(url("/screens/nope/unassign")))
            .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missing.statusCode());
        assertTrue(missing.body().contains("SCREEN_NOT_FOUND"));

        assertEquals(409, postForm("/families/" + fam + "/delete", "").statusCode());
        assertEquals(303, postForm("/screens/Ecran_1/delete", "").statusCode());
        assertEquals(303, postForm("/families/" + fam + "/delete", "").statusCode());
    }

    @Test
    void profileManagementEndpoints() throws Exception {
        ws.createScreen("origine", null, null, null);
        assertEquals(303, postForm("/profiles/create", "name=Copie&cloneFrom=default").statusCode());
        assertEquals(2, ws.listProfiles().size());
        assertEquals(400, postForm("/profiles/create", "name=%20").statusCode());
        assertEquals(303, postForm("/profiles/create", "name=Copie").statusCode());   // nom déjà pris : suffixe automatique
        assertTrue(ws.listProfiles().stream().anyMatch(p -> p.name().equals("Copie 2")));
        assertEquals(303, postForm("/profiles/copie-2/delete", "").statusCode());
        assertEquals(303, postForm("/profiles/copie/rename", "name=Renomm%C3%A9").statusCode());
        assertEquals(303, postForm("/profiles/copie/activate", "").statusCode());
        assertEquals("copie", ws.activeProfileId());
        assertEquals(1, ws.listScreens().size());
        assertEquals(409, postForm("/profiles/copie/delete", "").statusCode());
        assertEquals(303, postForm("/profiles/default/delete", "").statusCode());
        assertEquals(1, ws.listProfiles().size());
        assertTrue(get(url("/profiles")).body().contains("Renommé"));
    }

    @Test
    void statusApiReportsProfileAndWorld() throws Exception {
        ws.attachWorld("Mon Monde");
        String body = get(url("/api/status")).body();
        assertTrue(body.contains("\"world\":\"Mon Monde\""));
        assertTrue(body.contains("\"id\":\"mon-monde\""));
    }

    // ------------------------------------------------------------------ export / import

    @Test
    void exportThenImportIntoNewProfile() throws Exception {
        ws.createFamily("F");
        ws.createScreen("e", ws.listFamilies().get(0).id(), 3.0, 2.0);
        HttpResponse<String> export = get(url("/data/export"));
        assertEquals(200, export.statusCode());
        assertTrue(export.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));

        HttpResponse<String> r = multipart("/data/import", Map.of("mode", "skip", "target", "new", "newName", "Importé"),
            List.of(new Upload("files", "export.json", export.body().getBytes(StandardCharsets.UTF_8))));
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Import terminé"));
        assertNotNull(ws.listProfiles().stream().filter(p -> p.id().equals("importe")).findFirst().orElse(null));

        HttpResponse<String> bad = multipart("/data/import", Map.of("mode", "skip"),
            List.of(new Upload("files", "x.json", "{\"pas\":\"bon\"}".getBytes(StandardCharsets.UTF_8))));
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains("Fichier invalide"));

        HttpResponse<String> notJson = multipart("/data/import", Map.of("mode", "skip"),
            List.of(new Upload("files", "base.db", new byte[]{1, 2, 3})));
        assertEquals(400, notJson.statusCode());
    }

    // ------------------------------------------------------------------ sécurité

    @Test
    void localHostIsTrustedButForeignHostNamesNeedPassword() throws Exception {
        assertEquals(200, rawStatus("GET", "/library", "localhost:" + web.adminPort(), ""));
        assertEquals(200, rawStatus("GET", "/library", "127.0.0.1", ""));
        assertEquals(200, rawStatus("GET", "/library", "[::1]:8282", ""));

        // tunnel ou DNS rebinding : la connexion vient de 127.0.0.1 mais sous un autre nom
        assertEquals(401, rawStatus("GET", "/library", "abc.playit.gg", ""));
        String good = "Authorization: Basic " + Base64.getEncoder().encodeToString("x:secret".getBytes()) + "\r\n";
        String bad = "Authorization: Basic " + Base64.getEncoder().encodeToString("x:faux".getBytes()) + "\r\n";
        assertEquals(200, rawStatus("GET", "/library", "abc.playit.gg", good));
        assertEquals(401, rawStatus("GET", "/library", "abc.playit.gg", bad));
        assertEquals(401, rawStatus("POST", "/families/create", "abc.playit.gg", ""));
        assertEquals(401, rawStatus("GET", "/pagequiexistepas", "abc.playit.gg", ""), "même un 404 exige l'authentification");
    }

    @Test
    void remoteAccessIsRefusedWhenNoPasswordIsSet() throws Exception {
        settings.adminPassword = "";
        assertEquals(403, rawStatus("GET", "/library", "abc.playit.gg", ""));
        assertEquals(200, rawStatus("GET", "/library", "localhost", ""));
    }

    @Test
    void trustLocalhostCanBeDisabled() throws Exception {
        settings.trustLocalhost = false;
        assertEquals(401, rawStatus("GET", "/library", "localhost", ""));
        String good = "Authorization: Basic " + Base64.getEncoder().encodeToString(":secret".getBytes()) + "\r\n";
        assertEquals(200, rawStatus("GET", "/library", "localhost", good));
    }

    @Test
    void crossSitePostsAreRefused() throws Exception {
        String host = "localhost:" + web.adminPort();
        assertEquals(403, postForm("/families/create", "name=Piege", "Origin", "http://evil.example").statusCode());
        assertEquals(403, postForm("/families/create", "name=Piege", "Sec-Fetch-Site", "cross-site").statusCode());
        assertEquals(403, postForm("/families/create", "name=Piege", "Origin", "null").statusCode());
        assertTrue(ws.listFamilies().isEmpty());
        assertEquals(303, postForm("/families/create", "name=Legit", "Origin", "http://" + host).statusCode());
        assertEquals(1, ws.listFamilies().size());
    }

    @Test
    void publicPortServesOnlyImages() throws Exception {
        Files.write(ws.libraryDir().resolve("a.png"), PNG);
        ws.createScreen("s", null, null, null);
        ws.assignContent("s", "a.png");

        assertArrayEquals(PNG, getBytes(publicUrl("/s.png")).body());
        assertEquals(200, getBytes(publicUrl("/content/a.png")).statusCode());
        for (String p : List.of("/", "/library", "/screens", "/profiles", "/data/export", "/api/status", "/static/styles.css")) {
            assertEquals(404, get(publicUrl(p)).statusCode(), p);
        }
        HttpResponse<String> post = client.send(HttpRequest.newBuilder(URI.create(publicUrl("/families/create")))
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("name=X")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(404, post.statusCode());
        assertTrue(ws.listFamilies().isEmpty());
    }

    @Test
    void customBrandingIsPickedUpWithoutRestart() throws Exception {
        assertEquals(404, getBytes(url("/branding/banner")).statusCode());
        assertFalse(get(url("/")).body().contains("/branding/banner"));
        Files.createDirectories(root.resolve("branding"));
        Files.write(root.resolve("branding").resolve("banner.png"), PNG);
        assertEquals(200, getBytes(url("/branding/banner")).statusCode());
        assertTrue(get(url("/")).body().contains("/branding/banner"));
        assertEquals(404, getBytes(url("/branding/..%2fworkspace")).statusCode());
    }

    @Test
    void headRequestsReturnHeadersOnly() throws Exception {
        Files.write(ws.libraryDir().resolve("a.png"), PNG);
        ws.createScreen("s", null, null, null);
        ws.assignContent("s", "a.png");
        HttpResponse<byte[]> r = client.send(HttpRequest.newBuilder(URI.create(publicUrl("/s.png")))
            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode());
        assertEquals(0, r.body().length);
        assertEquals(String.valueOf(PNG.length), r.headers().firstValue("Content-Length").orElse(""));
    }

    // ------------------------------------------------------------------ miniatures, ping, adresses

    /** Dégradé légèrement bruité : proche d'une capture d'écran (PNG lourd, JPEG réduit léger). */
    private static void writePng(Path file, int w, int h) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Random r = new Random(3);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int red = Math.min(255, x * 255 / w + r.nextInt(12));
                int green = Math.min(255, y * 255 / h + r.nextInt(12));
                int blue = Math.min(255, ((x + y) * 255 / (w + h)) + r.nextInt(12));
                img.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        javax.imageio.ImageIO.write(img, "png", file.toFile());
    }

    @Test
    void thumbnailsAreSmallCachedAndFallBackToTheOriginal() throws Exception {
        Path big = ws.libraryDir().resolve("big.png");
        writePng(big, 1600, 900);
        HttpResponse<byte[]> t = getBytes(url("/thumb/big.png"));
        assertEquals(200, t.statusCode());
        assertEquals("image/jpeg", t.headers().firstValue("Content-Type").orElse(""));
        assertTrue(t.body().length < Files.size(big) / 2, "la miniature doit être bien plus légère que l'original");
        assertEquals(480, javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(t.body())).getWidth());
        assertEquals(200, javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(getBytes(url("/thumb/big.png?w=200")).body())).getWidth());
        try (var files = Files.list(root.resolve("cache").resolve("thumbs"))) {
            assertEquals(2, files.filter(f -> f.toString().endsWith(".jpg")).count());
        }
        assertEquals(304, getBytes(url("/thumb/big.png"), "If-None-Match", t.headers().firstValue("ETag").orElseThrow()).statusCode());

        Path small = ws.libraryDir().resolve("small.png");
        writePng(small, 100, 60);
        HttpResponse<byte[]> s = getBytes(url("/thumb/small.png"));
        assertEquals("image/png", s.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(Files.readAllBytes(small), s.body());

        Files.writeString(ws.libraryDir().resolve("vector.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>");
        assertEquals("image/svg+xml", getBytes(url("/thumb/vector.svg")).headers().firstValue("Content-Type").orElse(""));

        assertEquals(404, getBytes(url("/thumb/..%2fworkspace.json")).statusCode());
        assertEquals(404, getBytes(url("/thumb/absent.png")).statusCode());
    }

    @Test
    void pingIsPublicOnBothPorts() throws Exception {
        assertTrue(get(url("/ping")).body().contains("\"webstream\""));
        assertTrue(get(publicUrl("/ping")).body().contains("\"webstream\""));
    }

    @Test
    void copiedUrlsCarryTheConfiguredQuery() throws Exception {
        ws.createScreen("s", null, null, null);
        assertTrue(get(url("/screens")).body().contains("/s.png?refresh=1\""), "refresh=1 par défaut");
        settings.urlQuery = "";
        String html = get(url("/screens")).body();
        assertTrue(html.contains("/s.png\"") && !html.contains("/s.png?"));
        settings.urlQuery = "nocache=7";
        assertTrue(get(url("/screens")).body().contains("/s.png?nocache=7\""));
    }

    @Test
    void screenWithQueryStringIsServedLikeTheBareUrl() throws Exception {
        Files.write(ws.libraryDir().resolve("a.png"), PNG);
        ws.createScreen("s", null, null, null);
        ws.assignContent("s", "a.png");
        assertArrayEquals(PNG, getBytes(url("/s.png?refresh=1")).body());
        assertArrayEquals(PNG, getBytes(publicUrl("/s.png?refresh==1")).body());
    }

    // ------------------------------------------------------------------ réglages

    /** Formulaire de réglages valide ; chaque « cle=valeur » (valeur non encodée) remplace la valeur par défaut, « !cle » retire la case. */
    private String settingsForm(String... overrides) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("port", String.valueOf(settings.port));
        m.put("bindAddress", "127.0.0.1");
        m.put("publicEnabled", "on");
        m.put("publicPort", String.valueOf(settings.publicPort));
        m.put("publicBindAddress", "127.0.0.1");
        m.put("trustLocalhost", "on");
        m.put("newWorldProfile", "perWorld");
        m.put("maxUploadMb", "1");
        m.put("urlQuery", "refresh=1");
        for (String o : overrides) {
            if (o.startsWith("!")) m.remove(o.substring(1));
            else m.put(o.substring(0, o.indexOf('=')), o.substring(o.indexOf('=') + 1));
        }
        return m.entrySet().stream()
            .map(e -> java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
    }

    @Test
    void settingsAreValidatedSavedAndPersisted() throws Exception {
        settings.port = web.adminPort();
        java.util.concurrent.atomic.AtomicInteger saved = new java.util.concurrent.atomic.AtomicInteger();
        web.setOnSettingsChanged(saved::incrementAndGet);

        HttpResponse<String> page = get(url("/settings"));
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Multijoueur") && page.body().contains("name=\"publicUrl\""));

        HttpResponse<String> bad = postForm("/settings", settingsForm("publicUrl=ftp://nope"));
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains("http://"));
        assertEquals("", settings.publicUrl);
        assertEquals(0, saved.get());

        HttpResponse<String> ok = postForm("/settings", settingsForm("publicUrl=http://mon-serveur.fr:8283"));
        assertEquals(200, ok.statusCode());
        assertEquals("http://mon-serveur.fr:8283", settings.publicUrl);
        assertEquals("refresh=1", settings.urlQuery);
        assertEquals("secret", settings.adminPassword, "mot de passe laissé vide = inchangé");
        assertEquals(1, saved.get());
        assertFalse(settings.placeholderImage);
        assertTrue(ok.body().contains("Réglages enregistrés"));

        HttpResponse<String> lockout = postForm("/settings", settingsForm("!trustLocalhost", "clearPassword=on"));
        assertEquals(400, lockout.statusCode());
        assertTrue(lockout.body().contains("bloquerait"));
        assertEquals("secret", settings.adminPassword);

        assertEquals(400, postForm("/settings", settingsForm("maxUploadMb=99999")).statusCode());
        assertEquals(400, postForm("/settings", settingsForm("publicPort=" + settings.port)).statusCode(), "port public = port de l'interface");
        assertEquals(400, postForm("/settings", settingsForm("urlQuery=a b?c")).statusCode());
        assertEquals(1, saved.get(), "aucun enregistrement supplémentaire pour des réglages refusés");
    }

    @Test
    void changingPortsRestartsWebServerAndRollsBackWhenBusy() throws Exception {
        settings.port = web.adminPort();
        int first = publicPort;
        int second;
        try (ServerSocket s = new ServerSocket(0)) {
            second = s.getLocalPort();
        }

        assertEquals(200, postForm("/settings", settingsForm("publicPort=" + second)).statusCode());
        awaitPing(second);
        assertEquals(second, settings.publicPort);
        assertThrows(java.io.IOException.class, () -> get(publicUrl("/ping")), "l'ancien port public doit être fermé");

        // nouveau port déjà occupé : retour aux réglages précédents
        try (ServerSocket busy = new ServerSocket(0)) {
            int taken = busy.getLocalPort();
            assertEquals(200, postForm("/settings", settingsForm("publicPort=" + taken)).statusCode());
            long deadline = System.currentTimeMillis() + 8000;
            while (settings.publicPort != second && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertEquals(second, settings.publicPort, "les anciens réglages doivent revenir");
        }
        awaitPing(second);
        assertTrue(get(url("/settings")).body().contains("rétablis"));
    }

    private void awaitPing(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (true) {
            try {
                if (get("http://localhost:" + port + "/ping").statusCode() == 200) return;
            } catch (java.io.IOException e) {
                if (System.currentTimeMillis() > deadline) throw e;
            }
            Thread.sleep(100);
        }
    }

    @Test
    void connectionTestReportsSuccessAndFailure() throws Exception {
        HttpResponse<String> ok = postForm("/settings/test", "url=" + java.net.URLEncoder.encode("http://localhost:" + publicPort, StandardCharsets.UTF_8));
        assertTrue(ok.body().contains("\"ok\":true"), ok.body());
        HttpResponse<String> down = postForm("/settings/test", "url=" + java.net.URLEncoder.encode("http://localhost:1", StandardCharsets.UTF_8));
        assertTrue(down.body().contains("\"ok\":false"), down.body());
        HttpResponse<String> scheme = postForm("/settings/test", "url=ftp%3A%2F%2Fx");
        assertTrue(scheme.body().contains("http://"));
        HttpResponse<String> other = postForm("/settings/test", "url=" + java.net.URLEncoder.encode("http://localhost:" + web.adminPort() + "/static", StandardCharsets.UTF_8));
        assertTrue(other.body().contains("\"ok\":false"));
    }

    // ------------------------------------------------------------------ langues

    @Test
    void interfaceFollowsTheChosenLanguage() throws Exception {
        settings.port = web.adminPort();
        assertTrue(get(url("/")).body().contains("<html lang=\"fr\"") && get(url("/")).body().contains("Tableau de bord"));

        assertEquals(200, postForm("/settings", settingsForm("language=en")).statusCode());
        assertEquals("en", settings.language);
        String en = get(url("/")).body();
        assertTrue(en.contains("<html lang=\"en\"") && en.contains("Dashboard") && en.contains("Getting started"), "interface en anglais");
        assertFalse(en.contains("Tableau de bord"));
        assertTrue(get(url("/library")).body().contains("Drag and drop your images here"));
        assertTrue(get(url("/settings")).body().contains("Interface language"));

        assertEquals(200, postForm("/settings", settingsForm("language=es")).statusCode());
        String es = get(url("/screens")).body();
        assertTrue(es.contains("<html lang=\"es\"") && es.contains("Pantallas") && es.contains("Nueva pantalla"), "interface en espagnol");
        assertTrue(get(url("/profiles")).body().contains("Nuevo perfil"));
    }

    @Test
    void errorMessagesAndScriptTextsAreTranslated() throws Exception {
        settings.language = "en";
        HttpResponse<String> missing = client.send(HttpRequest.newBuilder(URI.create(url("/screens/nope/unassign")))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missing.statusCode());
        assertEquals("Screen not found.", missing.body());
        assertTrue(get(url("/inconnu/page")).body().contains("404 — page not found"));

        String page = get(url("/screens")).body();
        assertTrue(page.contains("window.WS_I18N="));
        assertTrue(page.contains("\"js.copied\":\"Address copied\""), "les textes du JavaScript sont envoyés dans la langue choisie");
        assertTrue(page.contains("\"err.SCREEN_NOT_FOUND\":\"Screen not found.\""));

        settings.language = "es";
        assertEquals("Pantalla no encontrada.", client.send(HttpRequest.newBuilder(URI.create(url("/screens/nope/unassign")))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    @Test
    void unsupportedLanguageIsRejected() throws Exception {
        settings.port = web.adminPort();
        HttpResponse<String> bad = postForm("/settings", settingsForm("language=de"));
        assertEquals(400, bad.statusCode());
        assertEquals("fr", settings.language);
    }

    @Test
    void placeholderSvgSubtitleFollowsTheLanguage() throws Exception {
        settings.language = "en";
        assertTrue(get(url("/screens/placeholder/280/130.svg")).body().contains("NO CONTENT"));
        settings.language = "es";
        assertTrue(get(url("/screens/placeholder/280/130.svg")).body().contains("SIN CONTENIDO"));
    }
}
