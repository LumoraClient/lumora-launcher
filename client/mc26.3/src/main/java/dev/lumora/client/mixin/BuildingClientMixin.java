package dev.lumora.client.mixin;

import dev.lumora.client.api.McBuilding;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Building tools (Lumora API 1.10): addons may take over pick-block on a block and shorten the right-click delay in
 * creative mode.
 */
@Mixin(Minecraft.class)
public abstract class BuildingClientMixin {
    @Shadow
    private int rightClickDelay;

    @Shadow
    public HitResult hitResult;

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void lumora$pick(CallbackInfo ci) {
        if (hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK && McBuilding.pick(b)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void lumora$useDelay(CallbackInfo ci) {
        rightClickDelay = McBuilding.useDelay(rightClickDelay);
    }
}
