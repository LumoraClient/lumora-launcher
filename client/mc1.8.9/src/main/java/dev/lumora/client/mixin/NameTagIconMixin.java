package dev.lumora.client.mixin;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.render.NameTagIcons;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Addon icons after player name tags (Lumora API 1.1 NameTags, plain-text fallback on this version). */
@Mixin(EntityRenderer.class)
public abstract class NameTagIconMixin {
    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private String lumora$icon(String label, Entity entity) {
        // the score line under the name goes through here as well; it does not contain the player's name
        if (entity instanceof PlayerEntity player && label != null && label.contains(player.getGameProfile().getName())) {
            return NameTagIcons.decorate(label, player.getUuid(), NameTags.Place.WORLD);
        }
        return label;
    }
}
