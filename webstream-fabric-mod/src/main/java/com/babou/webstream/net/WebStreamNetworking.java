package com.babou.webstream.net;

import com.babou.webstream.WebStreamMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Le serveur communique aux joueurs équipés du mod l'adresse de l'interface d'administration (touche / bouton du menu). */
public final class WebStreamNetworking {
    public static final Identifier INFO = new Identifier(WebStreamMod.MOD_ID, "info");

    private WebStreamNetworking() {}

    public static void sendInfo(ServerPlayerEntity player) {
        String adminUrl = WebStreamMod.CONFIG.adminUrl;
        if (adminUrl == null || adminUrl.isBlank()) return;
        // Un joueur sans le mod n'a pas déclaré ce canal : on ne lui envoie rien
        if (!ServerPlayNetworking.canSend(player, INFO)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(adminUrl.trim());
        ServerPlayNetworking.send(player, INFO, buf);
    }
}
