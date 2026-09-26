package com.babou.webstream;

import com.babou.webstream.command.WebStreamCommand;
import com.babou.webstream.config.WebStreamConfig;
import com.babou.webstream.core.Workspace;
import com.babou.webstream.net.WebStreamNetworking;
import com.babou.webstream.web.WebService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebStreamMod implements ModInitializer {
    public static final String MOD_ID = "webstream";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static WebStreamConfig CONFIG;

    private static Workspace workspace;
    private static WebService service;

    public static Workspace workspace() {
        return workspace;
    }

    public static WebService service() {
        return service;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[WebStream] Initializing WebStream Manager...");
        CONFIG = WebStreamConfig.load();

        if (!CONFIG.enabled) {
            LOGGER.info("[WebStream] WebStream is disabled in config");
            return;
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WebStreamCommand.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> WebStreamNetworking.sendInfo(handler.player));

        // Le monde n'est connu qu'au démarrage du serveur Minecraft (solo, LAN ou dédié) : c'est là que son profil est retrouvé
        ServerLifecycleEvents.SERVER_STARTING.register(server -> start(server.getSaveProperties().getLevelName()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop());
        Runtime.getRuntime().addShutdownHook(new Thread(WebStreamMod::stop, "webstream-shutdown"));
    }

    private static synchronized void start(String worldName) {
        stop();
        try {
            Path root = FabricLoader.getInstance().getConfigDir().resolve("webstream");
            workspace = new Workspace(root, CONFIG.newWorldProfile);
            workspace.attachWorld(worldName);
            LOGGER.info("[WebStream] Monde « {} » -> profil « {} »", worldName, workspace.activeProfileName());
            warnAboutLegacyInstall(root);

            String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
            service = new WebService(workspace, CONFIG, version);
            // Les réglages modifiés depuis l'interface sont enregistrés dans config/webstream.json
            service.setOnSettingsChanged(() -> {
                Workspace ws = workspace;
                if (ws != null) ws.setNewWorldMode(CONFIG.newWorldProfile);
                CONFIG.save();
            });
            service.start();
        } catch (BindException e) {
            LOGGER.error("[WebStream] Port déjà utilisé ({}). Changez « port » ou « publicPort » dans config/webstream.json.", e.getMessage());
            stop();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[WebStream] Impossible de démarrer le serveur web", e);
            stop();
        }
    }

    private static synchronized void stop() {
        if (service != null) {
            service.stop();
            LOGGER.info("[WebStream] Serveur web arrêté");
        }
        if (workspace != null) workspace.detachWorld();
        service = null;
        workspace = null;
    }

    /** Recharge les profils depuis le disque et ré-associe le monde ouvert (commande /webstream reload). */
    public static synchronized void reload() {
        if (workspace == null) return;
        String world = workspace.currentWorld();
        workspace.reload();
        if (world != null) workspace.attachWorld(world);
    }

    private static void warnAboutLegacyInstall(Path root) {
        if (Files.exists(root.resolve("node_modules")) || Files.exists(root.resolve("src").resolve("server.js"))) {
            LOGGER.info("[WebStream] Ancienne installation Node.js détectée dans {} : elle n'est plus utilisée et peut être supprimée "
                + "(node_modules, src, package.json, data, storage). Votre bibliothèque d'images a été reprise ; "
                + "pour vos écrans, importez un export .json depuis la page Données.", root);
        }
    }
}
