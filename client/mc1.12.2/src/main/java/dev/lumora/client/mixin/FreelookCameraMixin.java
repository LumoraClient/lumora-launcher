package dev.lumora.client.mixin;

import dev.lumora.core.camera.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook (module "Freelook"): particles face the free camera, not the player. */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {
    // Camera#update is static, so are the redirects
    @Redirect(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;yaw:F", opcode = Opcodes.GETFIELD))
    private static float lumora$freelookYaw(PlayerEntity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.yaw() : entity.yaw;
    }

    @Redirect(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;pitch:F", opcode = Opcodes.GETFIELD))
    private static float lumora$freelookPitch(PlayerEntity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.pitch() : entity.pitch;
    }
}
