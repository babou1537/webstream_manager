package com.babou.webstream;

import com.babou.webstream.config.WebStreamConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebStreamMod implements ModInitializer {
    public static final String MOD_ID = "webstream";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static WebStreamConfig CONFIG;
    private static WebStreamServer server;

    @Override
    public void onInitialize() {
        LOGGER.info("[WebStream] Initializing WebStream Manager...");

        // Charger la configuration
        CONFIG = WebStreamConfig.load();

        if (CONFIG.enabled) {
            LOGGER.info("[WebStream] WebStream is enabled, starting server...");

            // Démarrer le serveur Node.js
            server = new WebStreamServer();

            try {
                server.start();
            } catch (Exception e) {
                LOGGER.error("[WebStream] Failed to start server", e);
            }

            // Hook d'arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("[WebStream] Shutting down...");
                if (server != null) {
                    server.stop();
                }
            }));
        } else {
            LOGGER.info("[WebStream] WebStream is disabled in config");
        }
    }

    public static WebStreamServer getServer() {
        return server;
    }
}

