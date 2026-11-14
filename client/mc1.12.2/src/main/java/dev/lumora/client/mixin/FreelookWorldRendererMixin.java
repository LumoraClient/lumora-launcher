package dev.lumora.client.mixin;

import dev.lumora.core.camera.Freelook;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook (module "Freelook"): the visible chunks follow the free camera - the terrain update notices its turns and
 * the search starts in its direction (newer versions use the camera there anyway).
 */
@Mixin(WorldRenderer.class)
public abstract class FreelookWorldRendererMixin {

    @Redirect(method = {"setupTerrain", "getFacing"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookYaw(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.yaw() : entity.yaw;
    }

    @Redirect(method = {"setupTerrain", "getFacing"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevYaw:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookPrevYaw(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.yaw() : entity.prevYaw;
    }

    @Redirect(method = {"setupTerrain", "getFacing"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookPitch(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.pitch() : entity.pitch;
    }

    @Redirect(method = {"setupTerrain", "getFacing"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevPitch:F", opcode = Opcodes.GETFIELD))
    private float lumora$freelookPrevPitch(Entity entity) {
        return Freelook.active() && entity instanceof ClientPlayerEntity ? Freelook.pitch() : entity.prevPitch;
    }
}
