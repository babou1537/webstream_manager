package com.babou.webstream.web;

/** Réglages du serveur web, indépendants de Minecraft (remplis depuis webstream.json). */
public final class WebSettings {
    /** Port de l'interface d'administration et des images. */
    public int port = 8282;
    /** 127.0.0.1 = cette machine seulement ; 0.0.0.0 = ouvert au réseau (mot de passe requis à distance). */
    public String bindAddress = "127.0.0.1";
    /** Second port qui ne sert QUE les images (0 = désactivé) : c'est celui à ouvrir ou tunneler pour les joueurs. */
    public int publicPort = 0;
    public String publicBindAddress = "0.0.0.0";
    /** Mot de passe (utilisateur libre) demandé aux accès non locaux à l'interface. Vide = accès distant refusé. */
    public String adminPassword = "";
    /** Si faux, même les accès locaux doivent fournir le mot de passe. */
    public boolean trustLocalhost = true;
    /** Adresse que les joueurs doivent utiliser dans WebStreamer (ex. http://mon-serveur:8283). */
    public String publicUrl = "";
    public int maxUploadMb = 64;
}
