package com.babou.webstream.web;

import com.babou.webstream.core.WsException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse multipart/form-data en flux : les fichiers sont écrits sur disque au fil de l'eau (jamais chargés
 * en mémoire), avec une taille maximale par fichier et un nombre maximal de parties.
 */
final class Multipart implements AutoCloseable {
    record FilePart(String field, String filename, Path path) {}

    private static final Pattern BOUNDARY = Pattern.compile("boundary=(?:\"([^\"]+)\"|([^;\\s]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("[; ]name=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME = Pattern.compile("filename=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int MAX_FIELD_BYTES = 1 << 20;
    private static final int WINDOW = 1 << 16;

    final Map<String, String> fields = new LinkedHashMap<>();
    final List<FilePart> files = new ArrayList<>();
    private final Path tmpDir;

    private Multipart(Path tmpDir) {
        this.tmpDir = tmpDir;
    }

    static boolean isMultipart(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("multipart/form-data");
    }

    static Multipart parse(InputStream body, String contentType, Path tmpDir, long maxFileBytes, int maxParts) throws IOException {
        Matcher bm = contentType == null ? null : BOUNDARY.matcher(contentType);
        if (bm == null || !bm.find()) throw new WsException("MULTIPART_INVALID");
        String boundary = bm.group(1) != null ? bm.group(1) : bm.group(2);
        Multipart result = new Multipart(tmpDir);
        try {
            result.read(body, boundary, maxFileBytes, maxParts);
        } catch (IOException | RuntimeException e) {
            result.close();
            throw e;
        }
        return result;
    }

    private void read(InputStream body, String boundary, long maxFileBytes, int maxParts) throws IOException {
        PushbackInputStream in = new PushbackInputStream(new BufferedInputStream(body, 1 << 16), WINDOW + 1024);
        byte[] delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        // Le premier délimiteur n'est pas précédé de CRLF : on le simule pour traiter tous les cas pareil
        in.unread(new byte[]{'\r', '\n'});
        readUntil(in, delimiter, null, MAX_FIELD_BYTES); // préambule ignoré

        int parts = 0;
        while (true) {
            int a = in.read();
            int b = in.read();
            if (a < 0 || b < 0) throw new WsException("MULTIPART_INVALID");
            if (a == '-' && b == '-') return;
            if (a != '\r' || b != '\n') throw new WsException("MULTIPART_INVALID");
            if (++parts > maxParts) throw new WsException("TOO_MANY_PARTS");

            String headers = readHeaders(in);
            Matcher nm = NAME.matcher(" " + headers);
            String name = nm.find() ? nm.group(1) : "";
            Matcher fm = FILENAME.matcher(headers);
            String filename = fm.find() ? fm.group(1) : null;

            if (filename != null) {
                Path tmp = Files.createTempFile(tmpDir, "upload-", ".tmp");
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    readUntil(in, delimiter, out, maxFileBytes);
                } catch (IOException | RuntimeException e) {
                    Files.deleteIfExists(tmp);
                    throw e;
                }
                if (!filename.isEmpty()) files.add(new FilePart(name, filename, tmp));
                else Files.deleteIfExists(tmp);
            } else {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                readUntil(in, delimiter, out, MAX_FIELD_BYTES);
                fields.putIfAbsent(name, out.toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static String readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int state = 0; // progression dans \r\n\r\n
        while (state < 4) {
            int c = in.read();
            if (c < 0) throw new WsException("MULTIPART_INVALID");
            out.write(c);
            if (out.size() > MAX_HEADER_BYTES) throw new WsException("MULTIPART_INVALID");
            if ((state == 0 || state == 2) && c == '\r') state++;
            else if ((state == 1 || state == 3) && c == '\n') state++;
            else state = c == '\r' ? 1 : 0;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Copie le flux dans out (ou le jette si null) jusqu'au délimiteur, qui est consommé. */
    private static void readUntil(PushbackInputStream in, byte[] delimiter, OutputStream out, long max) throws IOException {
        byte[] window = new byte[WINDOW + delimiter.length];
        int len = 0;
        long written = 0;
        while (true) {
            int n = in.read(window, len, window.length - len);
            if (n < 0) throw new WsException("MULTIPART_INVALID");
            len += n;

            int idx = indexOf(window, len, delimiter);
            if (idx >= 0) {
                if (out != null) out.write(window, 0, idx);
                written += idx;
                if (written > max) throw new WsException("FILE_TOO_LARGE");
                int rest = len - (idx + delimiter.length);
                if (rest > 0) in.unread(window, idx + delimiter.length, rest);
                return;
            }

            int keep = delimiter.length - 1;
            int flush = len - keep;
            if (flush > 0) {
                if (out != null) out.write(window, 0, flush);
                written += flush;
                if (written > max) throw new WsException("FILE_TOO_LARGE");
                System.arraycopy(window, flush, window, 0, keep);
                len = keep;
            }
        }
    }

    private static int indexOf(byte[] data, int len, byte[] pattern) {
        outer:
        for (int i = 0; i <= len - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** Supprime les fichiers temporaires restants. */
    @Override
    public void close() {
        for (FilePart f : files) {
            try {
                Files.deleteIfExists(f.path());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
