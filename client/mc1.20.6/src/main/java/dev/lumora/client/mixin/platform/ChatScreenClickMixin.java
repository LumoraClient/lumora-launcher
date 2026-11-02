package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clicks on HUD widgets while the chat is open (the widgets are clickable there). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenClickMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lumora$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!Hooks.fireChatClick((ChatScreen) (Object) this, x, y, button)) {
            cir.setReturnValue(true);
        }
    }
}
