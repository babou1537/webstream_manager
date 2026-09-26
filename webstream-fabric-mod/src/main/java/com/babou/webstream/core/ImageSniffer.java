package com.babou.webstream.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Vérifie que le contenu d'un fichier correspond bien à son extension d'image (pas du HTML renommé en .png). */
public final class ImageSniffer {
    private ImageSniffer() {}

    public static boolean matchesExtension(Path file, String filename) throws IOException {
        byte[] head = new byte[512];
        int n;
        try (InputStream in = Files.newInputStream(file)) {
            n = in.readNBytes(head, 0, head.length);
        }
        if (n < 4) return false;
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> starts(head, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> starts(head, 0xFF, 0xD8, 0xFF);
            case "gif" -> starts(head, 'G', 'I', 'F', '8');
            case "bmp" -> starts(head, 'B', 'M');
            case "tif", "tiff" -> starts(head, 'I', 'I', 42, 0) || starts(head, 'M', 'M', 0, 42);
            case "webp" -> starts(head, 'R', 'I', 'F', 'F') && n >= 12 && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            case "svg" -> new String(head, 0, n, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("<svg");
            default -> false;
        };
    }

    private static boolean starts(byte[] data, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
