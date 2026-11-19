package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.SneakCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lumora "Animations", "Sneaking": the own player in third person is drawn at the height 1.7 / 1.8 drew it while
 * sneaking (1.8: not lowered, 1.7: lowered with the camera). Other players keep Minecraft's offset, which 1.7 and
 * 1.8 had as well.
 */
@Mixin(AvatarRenderer.class)
public abstract class AnimationsSneakModelMixin {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void lumora$sneakOffset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) {
            return;
        }
        double y = SneakCamera.ownModelOffset(state.isCrouching, state.scale);
        if (!Double.isNaN(y)) {
            Vec3 v = cir.getReturnValue();
            double minecraftCrouch = state.isCrouching ? state.scale * -2.0F / 16.0 : 0.0;
            cir.setReturnValue(new Vec3(v.x, v.y - minecraftCrouch + y, v.z));
        }
    }
}
