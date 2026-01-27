package com.babou.webstream.client;

import com.babou.webstream.WebStreamMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

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
                    openWebStream();
                }
            }
        });
    }

    public static void openWebStream() {
        try {
            String url = "http://localhost:" + WebStreamMod.CONFIG.port;
            String os = System.getProperty("os.name").toLowerCase();

            WebStreamMod.LOGGER.info("[WebStream] Opening browser: {}", url);

            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            WebStreamMod.LOGGER.error("[WebStream] Failed to open browser", e);
        }
    }
}

