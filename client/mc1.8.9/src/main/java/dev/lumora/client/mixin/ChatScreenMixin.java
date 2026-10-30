package dev.lumora.client.mixin;

import dev.lumora.client.api.LumoraRuntime;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clicks on HUD widgets while the chat is open (the newer versions use Fabric's ScreenMouseEvents). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lumora$hudClick(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (LumoraRuntime.chatClick(mouseX, mouseY, button)) {
            ci.cancel();
        }
    }
}
