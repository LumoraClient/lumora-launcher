package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.SneakCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Sneaking": the camera height while sneaking as in 1.7 / 1.8 (see {@link SneakCamera}). Only
 * where the camera is drawn: the hit box, the eye position for the server and block reach stay Minecraft's.
 */
@Mixin(Camera.class)
public abstract class AnimationsCameraMixin {
    @Shadow
    private Level level;
    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Inject(method = "tick", at = @At("TAIL"))
    private void lumora$sneakCamera(CallbackInfo ci) {
        if (this.level == null || !(this.entity instanceof Player player)) {
            return;
        }
        Pose pose = player.getPose();
        // a legacy camera only where the player could stand: under a low ceiling it would be inside the blocks
        boolean sneaking = pose == Pose.CROUCHING
                && this.level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7));
        float eye = SneakCamera.eye(sneaking, pose == Pose.STANDING, player.getEyeHeight(Pose.STANDING));
        if (!Float.isNaN(eye)) {
            if (SneakCamera.snap()) {
                this.eyeHeightOld = eye;
            }
            this.eyeHeight = eye;
        }
    }
}
