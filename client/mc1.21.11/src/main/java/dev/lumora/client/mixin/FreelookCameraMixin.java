package dev.lumora.client.mixin;

import dev.lumora.core.camera.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook (module "Freelook"): the camera of the own player uses the free camera's yaw and pitch; the third person
 * distance, the front view and the visible chunks follow from there.
 */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float lumora$freelookYaw(Entity entity, float partialTick) {
        return Freelook.active() && entity instanceof LocalPlayer ? Freelook.yaw() : entity.getViewYRot(partialTick);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float lumora$freelookPitch(Entity entity, float partialTick) {
        return Freelook.active() && entity instanceof LocalPlayer ? Freelook.pitch() : entity.getViewXRot(partialTick);
    }
}
