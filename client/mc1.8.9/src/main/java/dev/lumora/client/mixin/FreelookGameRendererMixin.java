package dev.lumora.client.mixin;

import dev.lumora.core.camera.Freelook;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook (module "Freelook"): mouse movement turns the free camera instead of the player, and the camera transform
 * uses the free camera's yaw and pitch for the own player.
 */
@Mixin(GameRenderer.class)
public abstract class FreelookGameRendererMixin {
    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ClientPlayerEntity;increaseTransforms(FF)V"))
    private void lumora$freelookTurn(ClientPlayerEntity player, float yaw, float pitch) {
        // Entity#increaseTransforms subtracts the pitch, Freelook#turn adds it like the newer Entity#turn
        if (!Freelook.turn(yaw, -pitch)) {
            player.increaseTransforms(yaw, pitch);
        }
    }

    @Redirect(method = "transformCamera(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookYaw(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.yaw() : entity.yaw;
    }

    @Redirect(method = "transformCamera(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevYaw:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookPrevYaw(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.yaw() : entity.prevYaw;
    }

    @Redirect(method = "transformCamera(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookPitch(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.pitch() : entity.pitch;
    }

    @Redirect(method = "transformCamera(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevPitch:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookPrevPitch(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.pitch() : entity.prevPitch;
    }
}
