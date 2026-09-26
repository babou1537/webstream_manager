package com.babou.webstream.client;

import com.babou.webstream.WebStreamMod;
import com.babou.webstream.net.WebStreamNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

public class WebStreamClient implements ClientModInitializer {
    private static KeyBinding openWebStreamKey;
    /** Adresse de l'interface envoyée par le serveur auquel on est connecté (vide si aucune). */
    private static volatile String serverAdminUrl = "";

    @Override
    public void onInitializeClient() {
        WebStreamMod.LOGGER.info("[WebStream] Initializing client...");

        // Touche modifiable dans Options > Contrôles
        openWebStreamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.webstream.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.webstream"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openWebStreamKey.wasPressed()) {
                if (client.world != null) openWebStream();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(WebStreamNetworking.INFO, (client, handler, buf, responseSender) -> {
            String url = buf.readString(2048);
            client.execute(() -> serverAdminUrl = url);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverAdminUrl = "");
    }

    /**
     * Ouvre l'interface : l'adresse « remoteUrl » du fichier de config si elle existe, sinon celle envoyée par le
     * serveur, sinon l'interface locale quand on joue en solo ou en hôte LAN.
     */
    public static void openWebStream() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String url = resolveUrl(mc);
        if (url == null) {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[WebStream] Ce serveur n'a pas publié l'adresse de l'interface. "
                    + "Demandez-la à un opérateur (/webstream admin), ou renseignez « remoteUrl » dans config/webstream.json."), false);
            }
            return;
        }
        try {
            WebStreamMod.LOGGER.info("[WebStream] Opening {}", url);
            Util.getOperatingSystem().open(URI.create(url));
        } catch (IllegalArgumentException e) {
            WebStreamMod.LOGGER.error("[WebStream] Adresse invalide : {}", url);
        }
    }

    private static String resolveUrl(MinecraftClient mc) {
        String remote = WebStreamMod.CONFIG.remoteUrl;
        if (remote != null && !remote.isBlank()) return remote.trim();
        if (!serverAdminUrl.isBlank()) return serverAdminUrl;
        if (mc.getServer() != null) return WebStreamMod.CONFIG.localAdminUrl();
        return null;
    }
}
