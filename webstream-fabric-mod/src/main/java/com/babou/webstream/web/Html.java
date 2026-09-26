package com.babou.webstream.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Petits utilitaires d'échappement pour la génération HTML. */
final class Html {
    private Html() {}

    /** Échappe pour le texte et les attributs (guillemets doubles ou simples). */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** encodeURIComponent : un espace devient %20 (pas +). */
    static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Nombre sans « .0 » inutile (2.0 -> 2, 2.8 -> 2.8). */
    static String num(Double d) {
        if (d == null) return "";
        if (d == Math.rint(d)) return String.valueOf(d.longValue());
        return String.valueOf(d);
    }
}
