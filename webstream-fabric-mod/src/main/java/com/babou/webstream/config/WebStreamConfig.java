package com.babou.webstream.config;

import com.babou.webstream.core.Workspace;
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

/** config/webstream.json. Les commentaires ci-dessous décrivent chaque option pour la documentation. */
public class WebStreamConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("webstream");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int CURRENT_VERSION = 2;

    /** 0 = fichier créé par une version antérieure (avant l'ajout de cette option). */
    public int configVersion = 0;

    /** Désactive complètement le mod. */
    public boolean enabled = true;

    /** Port de l'interface d'administration (et des images, si publicPort = 0). */
    public int port = 8282;

    /** 127.0.0.1 = interface accessible depuis cette machine seulement ; 0.0.0.0 = ouverte au réseau. */
    public String bindAddress = "127.0.0.1";

    /** Mot de passe demandé aux accès non locaux à l'interface (nom d'utilisateur libre). Vide = accès distant refusé. */
    public String adminPassword = "";

    /** Si false, même l'accès local demande le mot de passe (à activer si vous tunnelisez le port principal). */
    public boolean trustLocalhost = true;

    /**
     * Port qui ne sert QUE les images des écrans (0 = désactivé). C'est celui à ouvrir sur la box ou à tunneler
     * (playit.gg...) pour que les autres joueurs voient les écrans, sans exposer l'interface d'administration.
     */
    public int publicPort = 0;

    /** Adresse d'écoute du port public. */
    public String publicBindAddress = "0.0.0.0";

    /** Adresse publique à coller dans WebStreamer, ex. http://mon-serveur.fr:8283. Vide = déduite de l'adresse d'accès. */
    public String publicUrl = "";

    /** Adresse de l'interface d'administration, envoyée aux joueurs pour la touche/bouton (ex. https://admin.mon-serveur.fr). */
    public String adminUrl = "";

    /** Côté joueur : adresse de l'interface à ouvrir, si le serveur ne l'envoie pas. */
    public String remoteUrl = "";

    /** perWorld : un profil par monde, créé à la première ouverture. shared : un seul profil « default » pour tous les mondes. */
    public String newWorldProfile = Workspace.MODE_PER_WORLD;

    /** Taille maximale d'une image envoyée, en Mo. */
    public int maxUploadMb = 64;

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

    private void sanitize() {
        if (bindAddress == null || bindAddress.isBlank()) bindAddress = "127.0.0.1";
        if (publicBindAddress == null || publicBindAddress.isBlank()) publicBindAddress = "0.0.0.0";
        if (adminPassword == null) adminPassword = "";
        if (publicUrl == null) publicUrl = "";
        if (adminUrl == null) adminUrl = "";
        if (remoteUrl == null) remoteUrl = "";
        if (!Workspace.MODE_SHARED.equals(newWorldProfile)) newWorldProfile = Workspace.MODE_PER_WORLD;
        if (port < 0 || port > 65535) port = 8282;
        if (publicPort < 0 || publicPort > 65535) publicPort = 0;
        if (maxUploadMb < 1) maxUploadMb = 64;
    }

    public WebSettings toSettings() {
        WebSettings s = new WebSettings();
        s.port = port;
        s.bindAddress = bindAddress;
        s.publicPort = publicPort;
        s.publicBindAddress = publicBindAddress;
        s.adminPassword = adminPassword;
        s.trustLocalhost = trustLocalhost;
        s.publicUrl = publicUrl;
        s.maxUploadMb = maxUploadMb;
        return s;
    }

    /** Adresse de l'interface d'administration à ouvrir depuis cette machine. */
    public String localAdminUrl() {
        return "http://localhost:" + port;
    }

    public void save() {
        try {
            Files.writeString(configPath(), GSON.toJson(this));
            LOGGER.info("[WebStream] Configuration saved to {}", configPath());
        } catch (IOException e) {
            LOGGER.error("[WebStream] Failed to save config", e);
        }
    }
}
