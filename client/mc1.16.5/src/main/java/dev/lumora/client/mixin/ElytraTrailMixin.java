package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.ElytraTrails;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Elytra-Streifen": after a player is drawn (pose at its position), its wing tip trails. */
@Mixin(LivingEntityRenderer.class)
public abstract class ElytraTrailMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void lumora$elytraTrails(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                                     int light, CallbackInfo ci) {
        if (entity instanceof Player p) {
            ElytraTrails.draw(ElytraTrails.mesh(p, partial), pose);
        }
    }
}
