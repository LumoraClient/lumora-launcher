package dev.lumora.client.mixin;

import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Addon icons next to player name tags (Lumora API 1.1 NameTags, plain-text fallback on this version): remembered while
 * the player's label is drawn and drawn right of the label's box, without a background (by NameTagBesideMixin).
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagIconMixin {
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void lumora$icon(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        // the score line under the name goes through here as well; it does not contain the player's name
        if (entity instanceof PlayerEntity player && label != null && label.contains(player.getGameProfile().getName())) {
            NameTagIcons.begin(player.getUuid());
        } else {
            NameTagIcons.end();
        }
    }

    @Inject(method = "renderLabelIfPresent", at = @At("RETURN"))
    private void lumora$iconDone(Entity entity, String label, double x, double y, double z, int range, CallbackInfo ci) {
        NameTagIcons.end();
    }
}
