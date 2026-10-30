package dev.lumora.client.mixin;

import dev.lumora.client.render.PlayerIdHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the player's UUID into the render state, for addon name tag icons (Lumora API 1.1 NameTags). */
@Mixin(EntityRenderer.class)
public abstract class NameTagIconMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lumora$playerId(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
        ((PlayerIdHolder) state).lumora$playerId(entity instanceof Player ? entity.getUUID() : null);
    }
}
