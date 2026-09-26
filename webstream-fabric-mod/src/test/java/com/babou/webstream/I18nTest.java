package com.babou.webstream;

import com.babou.webstream.web.I18n;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    private static final Pattern PARAM = Pattern.compile("\\{(\\d+)}");

    private static Set<String> params(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = PARAM.matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyLanguageHasExactlyTheSameKeysAndParameters() {
        Map<String, String> fr = I18n.bundle("fr");
        assertFalse(fr.isEmpty());
        for (String lang : List.of("en", "es")) {
            Map<String, String> other = I18n.bundle(lang);
            assertEquals(fr.keySet(), other.keySet(), "clés différentes entre fr et " + lang);
            for (String key : fr.keySet()) {
                assertFalse(other.get(key).isBlank(), lang + " : traduction vide pour " + key);
                assertEquals(params(fr.get(key)), params(other.get(key)), lang + " : paramètres différents pour " + key);
            }
        }
    }

    @Test
    void everyKeyUsedInTheSourceExists() throws IOException {
        Pattern call = Pattern.compile("\\b(?:t|tr|settings\\.tr|CONFIG\\.tr)\\(\\s*\"([a-z][A-Za-z0-9._-]*)\"");
        Set<String> used = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                Matcher m = call.matcher(Files.readString(f));
                while (m.find()) used.add(m.group(1));
            }
        }
        Path js = Path.of("src/main/resources/web/static/app.js");
        Matcher m = Pattern.compile("\\bt\\('([a-zA-Z0-9._-]+)'").matcher(Files.readString(js));
        while (m.find()) used.add(m.group(1));

        assertTrue(used.size() > 150, "la détection des clés utilisées ne fonctionne plus : " + used.size());
        Set<String> known = I18n.bundle("fr").keySet();
        Set<String> missing = new HashSet<>(used);
        missing.removeIf(k -> k.endsWith(".")); // préfixes construits dynamiquement (ex. err. + code)
        missing.removeAll(known);
        assertTrue(missing.isEmpty(), "clés utilisées mais absentes des traductions : " + missing);
    }

    @Test
    void fallbackAndParameters() {
        assertEquals("Screen not found.", I18n.tr("en", "err.SCREEN_NOT_FOUND"));
        assertEquals("Écran introuvable.", I18n.tr("fr", "err.SCREEN_NOT_FOUND"));
        assertEquals("Delete the family “Metro”?", I18n.tr("en", "fam.confirmDelete", "Metro"));
        assertEquals("cle.inconnue", I18n.tr("es", "cle.inconnue"));
        assertEquals("en", I18n.normalize("de"));
        assertEquals("es", I18n.normalize("es"));
        assertTrue(I18n.supported("fr") && !I18n.supported("xx") && !I18n.supported(null));
    }

    @Test
    void scriptTextsAreExposedWithTheirPrefixes() {
        Map<String, String> js = I18n.forScript("en", "js.", "err.");
        assertEquals("Address copied", js.get("js.copied"));
        assertEquals("Screen not found.", js.get("err.SCREEN_NOT_FOUND"));
        assertFalse(js.containsKey("nav.home"));
    }
}
