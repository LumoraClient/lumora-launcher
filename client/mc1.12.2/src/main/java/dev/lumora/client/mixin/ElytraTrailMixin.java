package dev.lumora.client.mixin;

import dev.lumora.client.render.ElytraTrails;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Elytra-Streifen": after a player is drawn, its wing tip trails. */
@Mixin(LivingEntityRenderer.class)
public abstract class ElytraTrailMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;DDDFF)V", at = @At("TAIL"))
    private void lumora$elytraTrails(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta,
                                     CallbackInfo ci) {
        if (entity instanceof PlayerEntity) {
            ElytraTrails.draw(entity, x, y, z, tickDelta);
        }
    }
}
