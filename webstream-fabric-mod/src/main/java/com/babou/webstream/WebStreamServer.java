package com.babou.webstream;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WebStreamServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebStreamMod.MOD_ID);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final int NPM_INSTALL_TIMEOUT_SECONDS = 300;

    private Process nodeProcess;
    private Process npmProcess;
    private final Path webstreamDir;
    private boolean isRunning = false;
    private String worldName = "default";

    public WebStreamServer() {
        // Répertoire dédié pour l'application Node.js extraite du JAR
        // Utilise "webstream" (pas "webstream_manager") pour éviter les conflits avec le dossier de dev
        this.webstreamDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("webstream");
    }

    public void setWorldName(String worldName) {
        // Nettoyer le nom du monde pour éviter les caractères invalides dans les chemins
        this.worldName = worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
        LOGGER.info("[WebStream] World name set to: {}", this.worldName);
    }

    public synchronized void start() {
        if (isRunning()) {
            LOGGER.info("[WebStream] Server already running");
            return;
        }

        try {
            LOGGER.info("[WebStream] Starting WebStream server...");

            if (!isNodeInstalled()) {
                LOGGER.error("[WebStream] ========================================");
                LOGGER.error("[WebStream] Node.js NOT FOUND in PATH!");
                LOGGER.error("[WebStream] Please download and install Node.js LTS:");
                LOGGER.error("[WebStream] https://nodejs.org");
                LOGGER.error("[WebStream] ========================================");
                return;
            }

            String nodeVersion = getNodeVersion();
            LOGGER.info("[WebStream] Node.js detected: {}", nodeVersion);

            boolean appUpdated = extractNodeApp();
            installDependencies(appUpdated);
            startNodeProcess();

            LOGGER.info("[WebStream] Server started successfully on http://{}:{}",
                WebStreamMod.CONFIG.bindAddress, WebStreamMod.CONFIG.port);

        } catch (Exception e) {
            LOGGER.error("[WebStream] Error starting server", e);
        }
    }

    public synchronized void stop() {
        if (nodeProcess != null && nodeProcess.isAlive()) {
            LOGGER.info("[WebStream] Stopping Node.js server...");
            nodeProcess.destroy();

            try {
                boolean exited = nodeProcess.waitFor(5, TimeUnit.SECONDS);
                if (!exited) {
                    LOGGER.warn("[WebStream] Server didn't stop gracefully, forcing...");
                    nodeProcess.destroyForcibly();
                }
                LOGGER.info("[WebStream] Server stopped");
            } catch (InterruptedException e) {
                LOGGER.error("[WebStream] Error stopping server", e);
                Thread.currentThread().interrupt();
            }
        }
        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning && nodeProcess != null && nodeProcess.isAlive();
    }

    private boolean isNodeInstalled() {
        return commandSucceeds(command("node", "--version"));
    }

    private boolean isNpmInstalled() {
        return commandSucceeds(command("npm", "--version"));
    }

    // Sous Windows, npm est un npm.cmd : il faut passer par cmd pour le trouver
    private static List<String> command(String program, String... args) {
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS && program.equals("npm")) {
            cmd.add("cmd");
            cmd.add("/c");
        }
        cmd.add(program);
        cmd.addAll(List.of(args));
        return cmd;
    }

    private static boolean commandSucceeds(List<String> cmd) {
        try {
            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getNodeVersion() {
        try {
            Process process = new ProcessBuilder("node", "--version")
                .redirectErrorStream(true)
                .start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            process.waitFor(2, TimeUnit.SECONDS);

            return version != null ? version : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getModVersion() {
        return FabricLoader.getInstance()
            .getModContainer(WebStreamMod.MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    // Version + taille/date du JAR : un JAR recompilé sans changer de version est aussi ré-extrait
    private String getAppFingerprint() {
        StringBuilder fp = new StringBuilder(getModVersion());
        try {
            var container = FabricLoader.getInstance().getModContainer(WebStreamMod.MOD_ID);
            if (container.isPresent()) {
                for (Path p : container.get().getOrigin().getPaths()) {
                    if (Files.isRegularFile(p)) {
                        fp.append('+').append(Files.size(p)).append('-')
                            .append(Files.getLastModifiedTime(p).toMillis());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[WebStream] Could not fingerprint mod jar, using version only");
        }
        return fp.toString();
    }

    // Retourne true si l'application a été (ré)extraite. Les données (data/, storage/) ne sont jamais touchées :
    // elles ne font pas partie du JAR.
    private boolean extractNodeApp() throws IOException {
        Files.createDirectories(webstreamDir);

        String fingerprint = getAppFingerprint();
        Path versionFile = webstreamDir.resolve(".mod-version");
        String installedFingerprint = Files.exists(versionFile) ? Files.readString(versionFile).trim() : "";

        if (installedFingerprint.equals(fingerprint) && Files.exists(webstreamDir.resolve("package.json"))) {
            LOGGER.info("[WebStream] Application already up to date, skipping extraction");
            return false;
        }

        LOGGER.info("[WebStream] Extracting Node.js application (mod version {})...", getModVersion());
        copyResourceDirectory("webstream-node", webstreamDir);
        Files.writeString(versionFile, fingerprint);
        LOGGER.info("[WebStream] Application extracted to: {}", webstreamDir);
        return true;
    }

    private void copyResourceDirectory(String resourcePath, Path targetDir) throws IOException {
        var modContainer = FabricLoader.getInstance()
            .getModContainer(WebStreamMod.MOD_ID)
            .orElseThrow();

        var sourcePath = modContainer.findPath(resourcePath);

        if (sourcePath.isPresent() && Files.isDirectory(sourcePath.get())) {
            try (Stream<Path> paths = Files.walk(sourcePath.get())) {
                Path source = sourcePath.get();
                paths.forEach(path -> {
                    try {
                        Path destination = targetDir.resolve(source.relativize(path).toString());
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        LOGGER.error("[WebStream] Error copying file: " + path, e);
                    }
                });
            }
        } else {
            LOGGER.warn("[WebStream] Resource directory not found: {}", resourcePath);
        }
    }

    private void installDependencies(boolean appUpdated) throws IOException, InterruptedException {
        // Marqueur écrit seulement après un npm install réussi : un install interrompu sera refait
        Path marker = webstreamDir.resolve("node_modules").resolve(".webstream-deps");

        if (!appUpdated && Files.exists(marker)) {
            LOGGER.info("[WebStream] Dependencies already installed, skipping npm install...");
            return;
        }

        if (!isNpmInstalled()) {
            LOGGER.error("[WebStream] ========================================");
            LOGGER.error("[WebStream] npm NOT FOUND in PATH!");
            LOGGER.error("[WebStream] Dependencies are missing and npm cannot install them.");
            LOGGER.error("[WebStream] Please run 'npm install' manually in:");
            LOGGER.error("[WebStream] {}", webstreamDir.toAbsolutePath());
            LOGGER.error("[WebStream] ========================================");
            throw new IOException("npm not found and dependencies are missing");
        }

        LOGGER.info("[WebStream] Installing npm dependencies (this may take a few minutes the first time)...");

        ProcessBuilder pb = new ProcessBuilder(command("npm", "install", "--omit=dev", "--no-audit", "--no-fund"))
            .directory(webstreamDir.toFile())
            .redirectErrorStream(true);

        npmProcess = pb.start();

        Thread outputThread = new Thread(() -> logProcessOutput(npmProcess, "[npm]"));
        outputThread.setDaemon(true);
        outputThread.start();

        boolean finished = npmProcess.waitFor(NPM_INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            LOGGER.error("[WebStream] npm install timeout!");
            npmProcess.destroyForcibly();
            throw new IOException("npm install timeout");
        }

        if (npmProcess.exitValue() != 0) {
            throw new IOException("npm install failed with code " + npmProcess.exitValue());
        }

        Files.createDirectories(marker.getParent());
        Files.writeString(marker, getModVersion());
        LOGGER.info("[WebStream] Dependencies installed successfully");
    }

    private void startNodeProcess() throws IOException {
        LOGGER.info("[WebStream] Starting Node.js process...");

        ProcessBuilder pb = new ProcessBuilder("node", "src/server.js")
            .directory(webstreamDir.toFile())
            .redirectErrorStream(true);

        pb.environment().put("PORT", String.valueOf(WebStreamMod.CONFIG.port));
        pb.environment().put("HOST", WebStreamMod.CONFIG.bindAddress);
        pb.environment().put("NODE_ENV", "production");
        pb.environment().put("WORLD_NAME", this.worldName);
        pb.environment().put("DATA_DIR", webstreamDir.resolve("data").resolve(this.worldName).toString());
        if (!WebStreamMod.CONFIG.adminPassword.isEmpty()) {
            pb.environment().put("ADMIN_PASSWORD", WebStreamMod.CONFIG.adminPassword);
        }

        nodeProcess = pb.start();
        isRunning = true;

        Thread outputThread = new Thread(() -> logProcessOutput(nodeProcess, "[Node.js]"));
        outputThread.setDaemon(true);
        outputThread.start();

        try {
            Thread.sleep(2000);
            if (!nodeProcess.isAlive()) {
                throw new IOException("Node.js process died immediately");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (WebStreamMod.CONFIG.autoOpenBrowser) {
            openBrowser();
        }
    }

    private void logProcessOutput(Process process, String prefix) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("{} {}", prefix, line);
            }
        } catch (IOException e) {
            // Process terminated
        }
    }

    public void openBrowser() {
        openUrl(WebStreamMod.CONFIG.getWebUrl());
    }

    // rundll32 plutôt que "cmd /c start" : les '&' d'une URL ne sont pas interprétés par cmd
    public static void openUrl(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            LOGGER.info("[WebStream] Opening browser: {}", url);

            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            LOGGER.error("[WebStream] Failed to open browser", e);
        }
    }

    public int getPort() {
        return WebStreamMod.CONFIG.port;
    }
}
