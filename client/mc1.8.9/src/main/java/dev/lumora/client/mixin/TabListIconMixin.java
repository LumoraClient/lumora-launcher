package dev.lumora.client.mixin;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Addon icons after names in the tab list (Lumora API 1.1 NameTags, plain-text fallback on this version). */
@Mixin(PlayerListHud.class)
public abstract class TabListIconMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void lumora$icon(PlayerListEntry entry, CallbackInfoReturnable<String> cir) {
        String name = cir.getReturnValue();
        String decorated = NameTagIcons.decorate(name, entry.getProfile().getId(), NameTags.Place.TAB_LIST);
        if (!decorated.equals(name)) {
            cir.setReturnValue(decorated);
        }
    }
}
