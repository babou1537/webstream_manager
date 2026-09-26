package com.babou.webstream.web;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traductions de l'interface et des messages du mod : fichiers plats /web/lang/&lt;langue&gt;.json.
 * Une clé absente d'une langue retombe sur l'anglais, puis sur le français, puis s'affiche telle quelle.
 * Les valeurs peuvent contenir du HTML et des paramètres {0}, {1}… (à échapper par l'appelant).
 */
public final class I18n {
    public static final List<String> LANGUAGES = List.of("fr", "en", "es");
    public static final String DEFAULT = "en";

    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> BUNDLES = new ConcurrentHashMap<>();

    private I18n() {}

    public static boolean supported(String lang) {
        return lang != null && LANGUAGES.contains(lang);
    }

    public static String normalize(String lang) {
        return supported(lang) ? lang : DEFAULT;
    }

    /** Langue du système si elle est gérée (fr, en, es), sinon l'anglais. */
    public static String detect() {
        String system = Locale.getDefault().getLanguage();
        return supported(system) ? system : DEFAULT;
    }

    /** Nom de la langue dans sa propre langue, pour le sélecteur. */
    public static String nativeName(String lang) {
        return switch (lang) {
            case "fr" -> "Français";
            case "es" -> "Español";
            default -> "English";
        };
    }

    public static Locale locale(String lang) {
        return Locale.forLanguageTag(normalize(lang));
    }

    public static boolean has(String lang, String key) {
        return bundle(normalize(lang)).containsKey(key);
    }

    public static String tr(String lang, String key, Object... args) {
        String text = bundle(normalize(lang)).get(key);
        if (text == null) text = bundle(DEFAULT).get(key);
        if (text == null) text = bundle("fr").get(key);
        if (text == null) return key;
        for (int i = 0; i < args.length; i++) text = text.replace("{" + i + "}", String.valueOf(args[i]));
        return text;
    }

    /** Toutes les clés commençant par l'un des préfixes, avec repli sur l'anglais : envoyées au JavaScript de l'interface. */
    public static Map<String, String> forScript(String lang, String... prefixes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : bundle("fr").keySet()) {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    out.put(key, tr(lang, key));
                    break;
                }
            }
        }
        return out;
    }

    public static String toJson(Map<String, String> map) {
        return GSON.toJson(map);
    }

    /** Toutes les clés d'une langue (pour les tests de cohérence). */
    public static Map<String, String> bundle(String lang) {
        return BUNDLES.computeIfAbsent(lang, l -> {
            try (InputStream in = I18n.class.getResourceAsStream("/web/lang/" + l + ".json")) {
                if (in == null) return Map.of();
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> map = GSON.fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
                    return map == null ? Map.of() : map;
                }
            } catch (IOException e) {
                return Map.of();
            }
        });
    }
}
