package dev.lumora.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lumora API 1.9: the mouse wheel and keys of the open chat go to Lumora first (panels of chat input buttons, emoji
 * suggestions: Tab, arrow keys); Minecraft only sees them when Lumora did not use them.
 */
@Mixin(net.minecraft.client.gui.screens.ChatScreen.class)
public abstract class ChatInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lumora$key(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (dev.lumora.core.chat.ChatApi.screenKey(dev.lumora.client.api.SdlKeys.toGlfw(event.key()))) {
            cir.setReturnValue(true);
        }
    }

    /** Lumora chat windows: closing the chat goes back to the newest messages. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void lumora$closed(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.lumora.core.extras.ChatModule.chatScrollReset();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void lumora$scroll(double mouseX, double mouseY, double horizontal, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (dev.lumora.core.chat.ChatApi.screenScroll(mouseX, mouseY, amount)) {
            cir.setReturnValue(true);
        }
    }
}
