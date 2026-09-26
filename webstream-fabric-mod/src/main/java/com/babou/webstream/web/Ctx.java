package com.babou.webstream.web;

import com.babou.webstream.core.WsException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Requête + réponse d'un échange HTTP, avec les helpers dont les handlers ont besoin. */
final class Ctx {
    private static final Gson GSON = new Gson();
    private static final int MAX_SMALL_BODY = 1 << 20;

    final HttpExchange ex;
    final String method;
    /** Chemin brut (encore encodé), sans la query. */
    final String rawPath;
    final Map<String, String> query;
    Map<String, String> params = Map.of();
    /** Vrai si la requête a passé le contrôle d'accès administrateur. */
    boolean admin;

    private Map<String, String> form;
    private JsonObject json;
    private boolean bodyRead;

    Ctx(HttpExchange ex) {
        this.ex = ex;
        this.method = ex.getRequestMethod().toUpperCase();
        this.rawPath = ex.getRequestURI().getRawPath();
        this.query = parseUrlEncoded(ex.getRequestURI().getRawQuery());
    }

    String header(String name) {
        return ex.getRequestHeaders().getFirst(name);
    }

    String param(String name) {
        return params.get(name);
    }

    boolean wantsJson() {
        String accept = header("Accept");
        return accept != null && accept.contains("application/json");
    }

    // ------------------------------------------------------------------ corps de requête

    static Map<String, String> parseUrlEncoded(String s) {
        Map<String, String> out = new HashMap<>();
        if (s == null || s.isEmpty()) return out;
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                out.putIfAbsent(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // paramètre mal encodé : ignoré
            }
        }
        return out;
    }

    static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new WsException("PAYLOAD_TOO_LARGE");
        }
        return out.toByteArray();
    }

    private byte[] smallBody() throws IOException {
        if (bodyRead) return new byte[0];
        bodyRead = true;
        return readLimited(ex.getRequestBody(), MAX_SMALL_BODY);
    }

    /** Champs d'un formulaire application/x-www-form-urlencoded (vide pour les autres types). */
    Map<String, String> form() throws IOException {
        if (form == null) {
            String ct = header("Content-Type");
            form = ct != null && ct.toLowerCase().startsWith("application/x-www-form-urlencoded")
                ? parseUrlEncoded(new String(smallBody(), StandardCharsets.UTF_8))
                : new HashMap<>();
        }
        return form;
    }

    /** Corps JSON (objet vide s'il est absent ou invalide). */
    JsonObject json() throws IOException {
        if (json == null) {
            json = new JsonObject();
            String ct = header("Content-Type");
            if (ct != null && ct.toLowerCase().contains("json")) {
                try {
                    JsonElement e = JsonParser.parseString(new String(smallBody(), StandardCharsets.UTF_8));
                    if (e.isJsonObject()) json = e.getAsJsonObject();
                } catch (JsonParseException ignored) {
                    // corps invalide : traité comme vide
                }
            }
        }
        return json;
    }

    /** Valeur d'un champ, qu'il vienne d'un formulaire, du JSON ou de la query. */
    String field(String name) throws IOException {
        String v = form().get(name);
        if (v == null && json().has(name) && !json().get(name).isJsonNull()) v = json().get(name).getAsString();
        return v != null ? v : query.get(name);
    }

    // ------------------------------------------------------------------ réponses

    void send(int status, String contentType, byte[] body) throws IOException {
        if (contentType != null) ex.getResponseHeaders().set("Content-Type", contentType);
        boolean head = method.equals("HEAD");
        ex.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
        if (head || body.length == 0) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    void html(int status, String html) throws IOException {
        send(status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    void text(int status, String text) throws IOException {
        send(status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    void json(int status, Object body) throws IOException {
        send(status, "application/json; charset=utf-8", GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    void redirect(String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        send(303, "text/plain; charset=utf-8", new byte[0]);
    }

    /** Envoie un fichier avec Content-Length connu (le corps est copié en flux). */
    void sendFile(int status, String contentType, Path file, long size) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Content-Length", String.valueOf(size));
        if (method.equals("HEAD") || size == 0) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, size);
        try (OutputStream os = ex.getResponseBody(); InputStream in = Files.newInputStream(file)) {
            in.transferTo(os);
        }
    }
}
