package com.babou.webstream.config;

import com.babou.webstream.web.WebSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/webstream.json. Les réglages réseau et d'accès (hérités de WebSettings) sont aussi modifiables depuis la
 * page Réglages de l'interface ; les trois options ci-dessous ne le sont pas.
 */
public class WebStreamConfig extends WebSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("webstream");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int CURRENT_VERSION = 4;

    /** 0 = fichier créé par une version antérieure (avant l'ajout de cette option). */
    public int configVersion = 0;

    /** Désactive complètement le mod. */
    public boolean enabled = true;

    /** Côté joueur : adresse de l'interface à ouvrir, si le serveur ne l'envoie pas. */
    public String remoteUrl = "";

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("webstream.json");
    }

    public static WebStreamConfig load() {
        Path path = configPath();
        WebStreamConfig config = null;

        if (Files.exists(path)) {
            try {
                config = GSON.fromJson(Files.readString(path), WebStreamConfig.class);
                LOGGER.info("[WebStream] Configuration loaded from {}", path);
            } catch (IOException | JsonParseException e) {
                LOGGER.error("[WebStream] Failed to read config ({}), using defaults without overwriting the file", e.getMessage());
                return defaults();
            }
        }

        if (config == null) {
            config = defaults();
            config.save();
            return config;
        }

        config.sanitize();
        if (config.configVersion < CURRENT_VERSION) {
            // Ancienne version : on réécrit le fichier pour y faire apparaître les nouvelles options
            config.configVersion = CURRENT_VERSION;
            config.save();
        }
        return config;
    }

    /** Un serveur dédié ouvre par défaut le port public (images seulement) ; un jeu solo reste fermé. */
    private static WebStreamConfig defaults() {
        WebStreamConfig config = new WebStreamConfig();
        config.configVersion = CURRENT_VERSION;
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) config.publicPort = 8283;
        return config;
    }

    @Override
    public void sanitize() {
        super.sanitize();
        if (remoteUrl == null) remoteUrl = "";
    }

    /** Adresse de l'interface d'administration à ouvrir depuis cette machine. */
    public String localAdminUrl() {
        return "http://localhost:" + port;
    }

    public synchronized void save() {
        try {
            Files.writeString(configPath(), GSON.toJson(this));
            LOGGER.info("[WebStream] Configuration saved to {}", configPath());
        } catch (IOException e) {
            LOGGER.error("[WebStream] Failed to save config", e);
        }
    }
}
