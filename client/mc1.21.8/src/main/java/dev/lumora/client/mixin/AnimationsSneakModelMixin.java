package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.SneakCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lumora "Animations", "Sneaking": the own player in third person is drawn at the height 1.7 drew it while sneaking
 * (lowered with the camera). Other players keep Minecraft's offset, which 1.7 had as well.
 */
@Mixin(PlayerRenderer.class)
public abstract class AnimationsSneakModelMixin {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void lumora$sneakOffset(PlayerRenderState state, CallbackInfoReturnable<Vec3> cir) {
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
