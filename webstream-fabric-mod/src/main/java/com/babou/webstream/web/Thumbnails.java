package com.babou.webstream.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;

/**
 * Miniatures JPEG mises en cache sur disque : l'interface affiche des dizaines d'images de plusieurs Mo, il serait
 * absurde de toutes les télécharger en pleine taille pour de simples vignettes.
 * Si le format n'est pas lisible par ImageIO (SVG, WebP...) ou si java.desktop est absent, get() retourne null
 * et l'appelant sert l'original.
 */
final class Thumbnails {
    private static final Logger LOGGER = LoggerFactory.getLogger("webstream");
    private static final Color BACKGROUND = new Color(0x1e293b);

    private final Path cacheDir;
    private final Semaphore decodes = new Semaphore(2);
    private volatile boolean unavailable;

    Thumbnails(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /** Miniature de largeur maximale `width`, ou null si l'original doit être servi tel quel. */
    Path get(Path source, int width) throws IOException {
        if (unavailable) return null;
        String key = key(source, width);
        Path out = cacheDir.resolve(key + ".jpg");
        if (Files.isRegularFile(out)) return out;

        decodes.acquireUninterruptibly();
        try {
            if (Files.isRegularFile(out)) return out;
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null || image.getWidth() <= width) return null;

            BufferedImage scaled = scale(image, width);
            Files.createDirectories(cacheDir);
            Path tmp = cacheDir.resolve(key + ".tmp");
            if (!ImageIO.write(scaled, "jpg", tmp.toFile())) {
                Files.deleteIfExists(tmp);
                return null;
            }
            try {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            }
            return out;
        } catch (LinkageError e) {
            LOGGER.warn("[WebStream] Miniatures indisponibles (java.desktop absent) : les images seront servies en taille réelle");
            unavailable = true;
            return null;
        } catch (RuntimeException e) {
            return null;
        } finally {
            decodes.release();
        }
    }

    /** Réduction progressive (moitié par moitié) : bien plus fidèle qu'un seul grand saut. */
    private static BufferedImage scale(BufferedImage src, int targetWidth) {
        int w = src.getWidth();
        int h = src.getHeight();
        int targetHeight = Math.max(1, Math.round((float) h * targetWidth / w));

        BufferedImage current = toRgb(src);
        while (w / 2 >= targetWidth) {
            w /= 2;
            h = Math.max(1, h / 2);
            current = draw(current, w, h);
        }
        return draw(current, targetWidth, targetHeight);
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static String key(Path source, int width) throws IOException {
        String raw = source.getFileName() + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis() + "|" + width;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
