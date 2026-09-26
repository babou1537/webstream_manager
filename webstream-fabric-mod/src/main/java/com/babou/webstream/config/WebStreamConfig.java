package com.babou.webstream.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WebStreamConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("webstream");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public int port = 8282;
    public boolean autoOpenBrowser = false;
    public String keybind = "W";
    public boolean useCtrlModifier = true;
    // 127.0.0.1 = cette machine seulement. Mettre 0.0.0.0 pour ouvrir au réseau (définir alors adminPassword)
    public String bindAddress = "127.0.0.1";
    // Mot de passe demandé aux accès distants à l'interface (utilisateur quelconque). Vide = accès distant refusé
    public String adminPassword = "";
    // URL de l'interface hébergée ailleurs (ex: http://mon-serveur:8282). Vide = serveur local
    public String remoteUrl = "";

    public String getWebUrl() {
        String url = remoteUrl == null ? "" : remoteUrl.trim();
        return url.isEmpty() ? "http://localhost:" + port : url;
    }

    public static WebStreamConfig load() {
        Path configPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("webstream.json");

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                WebStreamConfig config = GSON.fromJson(json, WebStreamConfig.class);
                if (config == null) {
                    return new WebStreamConfig();
                }
                // Une valeur "null" explicite dans le JSON écrase le défaut
                if (config.bindAddress == null || config.bindAddress.isBlank()) config.bindAddress = "127.0.0.1";
                if (config.adminPassword == null) config.adminPassword = "";
                LOGGER.info("[WebStream] Configuration loaded from {}", configPath);
                return config;
            } catch (IOException e) {
                LOGGER.error("[WebStream] Failed to load config, using defaults", e);
            }
        } else {
            LOGGER.info("[WebStream] No config file found, creating default...");
            WebStreamConfig config = new WebStreamConfig();
            config.save();
            return config;
        }

        return new WebStreamConfig();
    }

    public void save() {
        Path configPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("webstream.json");

        try {
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);
            LOGGER.info("[WebStream] Configuration saved to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("[WebStream] Failed to save config", e);
        }
    }
}

