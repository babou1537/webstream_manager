package com.babou.webstream.mixin;

import com.babou.webstream.client.WebStreamClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addWebStreamButton(CallbackInfo ci) {
        int buttonWidth = 204;
        int buttonHeight = 20;
        int centerX = this.width / 2 - buttonWidth / 2;
        // Positionner le bouton en bas, au-dessus du bouton Disconnect
        int buttonY = this.height / 4 + 120 + 24;

        this.addDrawableChild(
            ButtonWidget.builder(
                Text.translatable("text.webstream.button"),
                button -> WebStreamClient.openWebStream()
            )
            .dimensions(centerX, buttonY, buttonWidth, buttonHeight)
            .build()
        );
    }
}

