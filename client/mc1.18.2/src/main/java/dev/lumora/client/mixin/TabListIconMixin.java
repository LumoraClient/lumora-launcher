package dev.lumora.client.mixin;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Addon icons after names in the tab list (Lumora API 1.1 NameTags). */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListIconMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void lumora$icon(PlayerInfo player, CallbackInfoReturnable<Component> cir) {
        Component name = cir.getReturnValue();
        Component decorated = NameTagIcons.decorate(name, player.getProfile().getId(), NameTags.Place.TAB_LIST);
        if (decorated != name) {
            cir.setReturnValue(decorated);
        }
    }
}
