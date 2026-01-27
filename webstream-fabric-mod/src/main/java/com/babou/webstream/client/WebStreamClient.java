package com.babou.webstream.client;

import com.babou.webstream.WebStreamMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class WebStreamClient implements ClientModInitializer {
    private static KeyBinding openWebStreamKey;

    @Override
    public void onInitializeClient() {
        WebStreamMod.LOGGER.info("[WebStream] Initializing client...");

        openWebStreamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.webstream.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_W,
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
        if (WebStreamMod.getServer() != null && WebStreamMod.getServer().isRunning()) {
            WebStreamMod.getServer().openBrowser();
        } else {
            WebStreamMod.LOGGER.warn("[WebStream] Server is not running!");
        }
    }
}

