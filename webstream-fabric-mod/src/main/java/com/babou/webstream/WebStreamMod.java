package com.babou.webstream;

import com.babou.webstream.config.WebStreamConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

            server = new WebStreamServer();

            // Le nom du monde n'est connu qu'au démarrage du serveur Minecraft : c'est là que Node démarre,
            // pour que les données soient bien rangées par monde. Le démarrage se fait hors du thread
            // principal car le premier npm install peut durer plusieurs minutes.
            ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> {
                String worldName = minecraftServer.getSaveProperties().getLevelName();
                LOGGER.info("[WebStream] Detected world: {}", worldName);
                server.setWorldName(worldName);

                Thread starter = new Thread(server::start, "webstream-start");
                starter.setDaemon(true);
                starter.start();
            });

            ServerLifecycleEvents.SERVER_STOPPED.register(minecraftServer -> server.stop());

            // Hook d'arrêt propre (filet de sécurité si Minecraft est fermé brutalement)
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

