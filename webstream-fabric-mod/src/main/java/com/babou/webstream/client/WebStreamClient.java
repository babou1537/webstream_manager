package com.babou.webstream.client;

import com.babou.webstream.WebStreamMod;
import com.babou.webstream.WebStreamServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WebStreamClient implements ClientModInitializer {
    private static KeyBinding openWebStreamKey;

    @Override
    public void onInitializeClient() {
        WebStreamMod.LOGGER.info("[WebStream] Initializing client...");

        openWebStreamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.webstream.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.webstream"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openWebStreamKey.wasPressed()) {
                boolean ctrlPressed = InputUtil.isKeyPressed(
                    client.getWindow().getHandle(),
                    GLFW.GLFW_KEY_LEFT_CONTROL
                ) || InputUtil.isKeyPressed(
                    client.getWindow().getHandle(),
                    GLFW.GLFW_KEY_RIGHT_CONTROL
                );

                if (!WebStreamMod.CONFIG.useCtrlModifier || ctrlPressed) {
                    // Récupérer le nom du joueur
                    if (client.player != null) {
                        String username = client.player.getName().getString();
                        openWebStream(username);
                    } else {
                        openWebStream("guest");
                    }
                }
            }
        });
    }

    public static void openWebStream(String username) {
        try {
            // Construire l'URL avec le nom du joueur en paramètre
            String base = WebStreamMod.CONFIG.getWebUrl();
            String url = base + (base.contains("?") ? "&" : "?")
                + "user=" + URLEncoder.encode(username, StandardCharsets.UTF_8);

            WebStreamMod.LOGGER.info("[WebStream] Opening browser for user '{}'", username);
            WebStreamServer.openUrl(url);
        } catch (RuntimeException e) {
            WebStreamMod.LOGGER.error("[WebStream] Failed to open browser", e);
        }
    }
}

