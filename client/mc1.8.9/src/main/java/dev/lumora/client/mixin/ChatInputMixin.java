package dev.lumora.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora API 1.9: the mouse wheel and keys of the open chat go to Lumora first (panels of chat input buttons, emoji
 * suggestions: Tab, arrow keys); Minecraft only sees them when Lumora did not use them.
 */
@Mixin(net.minecraft.client.gui.screen.ChatScreen.class)
public abstract class ChatInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lumora$key(char character, int code, CallbackInfo ci) {
        if (code != 0 && dev.lumora.core.chat.ChatApi.screenKey(dev.lumora.client.compat.Keys.toGlfw(code))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMouse", at = @At("HEAD"), cancellable = true)
    private void lumora$scroll(CallbackInfo ci) {
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.gui.screen.Screen self = (net.minecraft.client.gui.screen.Screen) (Object) this;
        if (wheel == 0 || mc.width <= 0 || mc.height <= 0) {
            return;
        }
        int mx = org.lwjgl.input.Mouse.getEventX() * self.width / mc.width;
        int my = self.height - org.lwjgl.input.Mouse.getEventY() * self.height / mc.height - 1;
        if (dev.lumora.core.chat.ChatApi.screenScroll(mx, my, wheel > 0 ? 1 : -1)) {
            ci.cancel();
        }
    }
}
