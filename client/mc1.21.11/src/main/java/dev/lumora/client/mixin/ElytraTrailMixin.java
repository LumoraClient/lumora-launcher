package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.ElytraTrails;
import dev.lumora.client.render.TrailHolder;
import dev.lumora.core.extras.ElytraTrailsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Elytra-Streifen": the trails are built while the player's render state is extracted (the entity is only
 * available there) and drawn after the player (pose at its position).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ElytraTrailMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void lumora$elytraMesh(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        ((TrailHolder) state).lumora$trail(entity instanceof Player p ? ElytraTrails.mesh(p, partial) : null);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void lumora$elytraTrails(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                     CameraRenderState camera, CallbackInfo ci) {
        ElytraTrailsModule.Mesh mesh = ((TrailHolder) state).lumora$trail();
        if (mesh != null) {
            ElytraTrails.draw(mesh, pose, collector);
        }
    }
}
