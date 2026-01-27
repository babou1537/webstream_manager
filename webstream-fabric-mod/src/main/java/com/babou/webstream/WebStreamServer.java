package com.babou.webstream;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WebStreamServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebStreamMod.MOD_ID);
    private Process nodeProcess;
    private Process npmProcess;
    private Path webstreamDir;
    private boolean isRunning = false;

    public WebStreamServer() {
        this.webstreamDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("webstream");
    }

    public void start() {
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

            extractNodeApp();
            installDependencies();
            startNodeProcess();

            LOGGER.info("[WebStream] Server started successfully on http://localhost:{}",
                WebStreamMod.CONFIG.port);

        } catch (Exception e) {
            LOGGER.error("[WebStream] Error starting server", e);
        }
    }

    public void stop() {
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
        try {
            Process process = new ProcessBuilder("node", "--version")
                .redirectErrorStream(true)
                .start();
            process.waitFor(2, TimeUnit.SECONDS);
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

    private void extractNodeApp() throws IOException {
        LOGGER.info("[WebStream] Extracting Node.js application...");

        if (!Files.exists(webstreamDir)) {
            Files.createDirectories(webstreamDir);
        }

        Path packageJson = webstreamDir.resolve("package.json");
        if (Files.exists(packageJson)) {
            LOGGER.info("[WebStream] Application already extracted, skipping...");
            return;
        }

        copyResourceDirectory("webstream-node", webstreamDir);

        LOGGER.info("[WebStream] Application extracted to: {}", webstreamDir);
    }

    private void copyResourceDirectory(String resourcePath, Path targetDir) throws IOException {
        Path modJar = FabricLoader.getInstance()
            .getModContainer(WebStreamMod.MOD_ID)
            .orElseThrow()
            .getRootPath();

        Path sourcePath = modJar.resolve(resourcePath);

        if (Files.isDirectory(sourcePath)) {
            try (Stream<Path> paths = Files.walk(sourcePath)) {
                paths.forEach(source -> {
                    try {
                        Path destination = targetDir.resolve(sourcePath.relativize(source).toString());
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        LOGGER.error("[WebStream] Error copying file: " + source, e);
                    }
                });
            }
        }
    }

    private void installDependencies() throws IOException, InterruptedException {
        Path nodeModules = webstreamDir.resolve("node_modules");

        if (Files.exists(nodeModules)) {
            LOGGER.info("[WebStream] Dependencies already installed, skipping npm install...");
            return;
        }

        LOGGER.info("[WebStream] Installing npm dependencies (this may take a moment)...");

        ProcessBuilder pb = new ProcessBuilder("npm", "install", "--production")
            .directory(webstreamDir.toFile())
            .redirectErrorStream(true);

        npmProcess = pb.start();

        Thread outputThread = new Thread(() -> logProcessOutput(npmProcess, "[npm]"));
        outputThread.start();

        boolean finished = npmProcess.waitFor(120, TimeUnit.SECONDS);

        if (!finished) {
            LOGGER.error("[WebStream] npm install timeout!");
            npmProcess.destroyForcibly();
            throw new IOException("npm install timeout");
        }

        if (npmProcess.exitValue() != 0) {
            throw new IOException("npm install failed with code " + npmProcess.exitValue());
        }

        LOGGER.info("[WebStream] Dependencies installed successfully");
    }

    private void startNodeProcess() throws IOException {
        LOGGER.info("[WebStream] Starting Node.js process...");

        ProcessBuilder pb = new ProcessBuilder("node", "src/server.js")
            .directory(webstreamDir.toFile())
            .redirectErrorStream(true);

        pb.environment().put("PORT", String.valueOf(WebStreamMod.CONFIG.port));
        pb.environment().put("NODE_ENV", "production");

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
        try {
            String url = "http://localhost:" + WebStreamMod.CONFIG.port;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + url);
            } else if (os.contains("nix") || os.contains("nux")) {
                Runtime.getRuntime().exec("xdg-open " + url);
            }

            LOGGER.info("[WebStream] Opening browser: {}", url);
        } catch (IOException e) {
            LOGGER.error("[WebStream] Failed to open browser", e);
        }
    }

    public int getPort() {
        return WebStreamMod.CONFIG.port;
    }
}

