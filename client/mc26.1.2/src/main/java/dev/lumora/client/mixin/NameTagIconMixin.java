package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.NameTagIcons;
import dev.lumora.client.render.PlayerIdHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Addon icons next to player name tags (drawn beside the box by NameTagBesideMixin) (Lumora API 1.1 NameTags); the line under the name is {@link AvatarSubtitleMixin}. */
@Mixin(EntityRenderer.class)
public abstract class NameTagIconMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void lumora$playerId(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
        ((PlayerIdHolder) state).lumora$playerId(entity instanceof Player ? entity.getUUID() : null);
    }

    // The final name after player/team formatting; the first call draws the separate score line.
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/level/CameraRenderState;)V", ordinal = 1))
    private void lumora$icon(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
        var id = ((PlayerIdHolder) state).lumora$playerId();
        if (id != null) {
            NameTagIcons.begin(id);
        }
    }

    /** The name is submitted: no icon pending for later name tags (other entities, the line under the name). */
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/level/CameraRenderState;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void lumora$iconDone(CallbackInfo ci) {
        NameTagIcons.end();
    }
}
