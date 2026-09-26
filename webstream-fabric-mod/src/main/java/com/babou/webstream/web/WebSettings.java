package com.babou.webstream.web;

import com.babou.webstream.core.Workspace;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Réglages du serveur web, indépendants de Minecraft. Ils sont sérialisés tels quels dans config/webstream.json
 * (WebStreamConfig en hérite) et modifiables depuis la page Réglages.
 */
public class WebSettings {
    private static final Pattern HOST = Pattern.compile("^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|[0-9a-fA-F:]*:[0-9a-fA-F:.]*)$");

    /** Langue de l'interface web et des messages du serveur : fr, en ou es (par défaut celle du système). */
    public String language = I18n.detect();
    /** Port de l'interface d'administration (et des images, si publicPort = 0). */
    public int port = 8282;
    /** 127.0.0.1 = cette machine seulement ; 0.0.0.0 = ouvert au réseau (mot de passe requis à distance). */
    public String bindAddress = "127.0.0.1";
    /** Second port qui ne sert QUE les images (0 = désactivé) : celui à ouvrir ou tunneler pour les joueurs. */
    public int publicPort = 0;
    public String publicBindAddress = "0.0.0.0";
    /** Mot de passe (utilisateur libre) demandé aux accès non locaux à l'interface. Vide = accès distant refusé. */
    public String adminPassword = "";
    /** Si faux, même les accès locaux doivent fournir le mot de passe (à utiliser si le port principal est tunnelisé). */
    public boolean trustLocalhost = true;
    /** Adresse que les joueurs doivent utiliser dans WebStreamer (ex. http://mon-serveur:8283). */
    public String publicUrl = "";
    /** Adresse de l'interface d'administration communiquée aux joueurs (touche / bouton du menu pause). */
    public String adminUrl = "";
    /** perWorld : un profil par monde ; shared : un seul profil pour tous les mondes. */
    public String newWorldProfile = Workspace.MODE_PER_WORLD;
    public int maxUploadMb = 64;
    /**
     * Écran sans image : false = erreur 404 (WebStreamer réessaie toutes les 30 s et affiche l'image dès qu'elle est
     * assignée) ; true = image « NO SIGNAL » (WebStreamer la garde en cache tant que l'URL ne change pas).
     */
    public boolean placeholderImage = false;
    /** Paramètre ajouté aux adresses d'écrans copiées depuis l'interface (sans le « ? »), ex. refresh=1. Vide = aucun. */
    public String urlQuery = "refresh=1";

    /** Traduit une clé dans la langue des réglages. */
    public String tr(String key, Object... args) {
        return I18n.tr(language, key, args);
    }

    /** Remet des valeurs cohérentes après lecture d'un fichier ancien ou édité à la main. */
    public void sanitize() {
        language = I18n.supported(language) ? language : I18n.detect();
        if (bindAddress == null || bindAddress.isBlank()) bindAddress = "127.0.0.1";
        if (publicBindAddress == null || publicBindAddress.isBlank()) publicBindAddress = "0.0.0.0";
        if (adminPassword == null) adminPassword = "";
        if (publicUrl == null) publicUrl = "";
        if (adminUrl == null) adminUrl = "";
        if (!Workspace.MODE_SHARED.equals(newWorldProfile)) newWorldProfile = Workspace.MODE_PER_WORLD;
        if (port < 0 || port > 65535) port = 8282;
        if (publicPort < 0 || publicPort > 65535) publicPort = 0;
        if (maxUploadMb < 1) maxUploadMb = 64;
        urlQuery = urlQuery == null ? "" : urlQuery.trim().replaceFirst("^\\?+", "");
    }

    /** Vérification stricte avant d'appliquer des réglages saisis dans l'interface : liste des erreurs (vide si OK). */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (!I18n.supported(language)) errors.add(tr("val.language"));
        if (port < 1 || port > 65535) errors.add(tr("val.port"));
        if (publicPort < 0 || publicPort > 65535) errors.add(tr("val.publicPort"));
        if (publicPort > 0 && publicPort == port) errors.add(tr("val.publicPortSame"));
        if (!HOST.matcher(bindAddress == null ? "" : bindAddress.trim()).matches()) errors.add(tr("val.bind"));
        if (!HOST.matcher(publicBindAddress == null ? "" : publicBindAddress.trim()).matches()) errors.add(tr("val.publicBind"));
        if (!validUrl(publicUrl)) errors.add(tr("val.publicUrl"));
        if (!validUrl(adminUrl)) errors.add(tr("val.adminUrl"));
        if (!Workspace.MODE_PER_WORLD.equals(newWorldProfile) && !Workspace.MODE_SHARED.equals(newWorldProfile)) errors.add(tr("val.profileMode"));
        if (maxUploadMb < 1 || maxUploadMb > 2048) errors.add(tr("val.maxUpload"));
        if (urlQuery != null && !urlQuery.matches("[A-Za-z0-9_.~%=&-]*")) errors.add(tr("val.urlQuery"));
        return errors;
    }

    private static boolean validUrl(String url) {
        if (url == null || url.isBlank()) return true;
        String u = url.trim();
        try {
            URI uri = URI.create(u);
            return (u.startsWith("http://") || u.startsWith("https://")) && uri.getHost() != null && !u.contains(" ");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isLoopbackBind() {
        String b = bindAddress == null ? "" : bindAddress.trim();
        return b.equals("127.0.0.1") || b.equals("localhost") || b.equals("::1");
    }

    public void copyFrom(WebSettings o) {
        language = o.language;
        port = o.port;
        bindAddress = o.bindAddress;
        publicPort = o.publicPort;
        publicBindAddress = o.publicBindAddress;
        adminPassword = o.adminPassword;
        trustLocalhost = o.trustLocalhost;
        publicUrl = o.publicUrl;
        adminUrl = o.adminUrl;
        newWorldProfile = o.newWorldProfile;
        maxUploadMb = o.maxUploadMb;
        placeholderImage = o.placeholderImage;
        urlQuery = o.urlQuery;
    }

    /** Copie indépendante (pour préparer des changements sans toucher aux réglages actifs). */
    public WebSettings copy() {
        WebSettings c = new WebSettings();
        c.copyFrom(this);
        return c;
    }

    /** Vrai si les écoutes réseau doivent être redémarrées pour appliquer ces réglages. */
    public boolean networkDiffersFrom(WebSettings o) {
        return port != o.port || publicPort != o.publicPort
            || !bindAddress.equals(o.bindAddress) || !publicBindAddress.equals(o.publicBindAddress);
    }
}
