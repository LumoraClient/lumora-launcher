package dev.lumora.client.mixin;

import dev.lumora.api.building.UseHandler;
import dev.lumora.client.api.McBuilding;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Building tools (Lumora API 1.10): addons decide about a right-click on a block before Minecraft handles it (normal,
 * as if sneaking, or dropped), and may shorten the break delay in creative mode.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class BuildingUseMixin {
    @Shadow
    private int destroyDelay;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void lumora$useStart(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                 CallbackInfoReturnable<InteractionResult> cir) {
        UseHandler.Decision d = McBuilding.decide(player, hand, hit);
        if (d == UseHandler.Decision.CANCEL) {
            cir.setReturnValue(InteractionResult.FAIL);
        } else if (d == UseHandler.Decision.AS_SNEAKING) {
            McBuilding.beginSneakClick(player);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void lumora$useEnd(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                               CallbackInfoReturnable<InteractionResult> cir) {
        McBuilding.endSneakClick(player);
    }

    @Inject(method = "startDestroyBlock", at = @At("RETURN"))
    private void lumora$breakDelayStart(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        destroyDelay = McBuilding.breakDelay(destroyDelay);
    }

    @Inject(method = "continueDestroyBlock", at = @At("RETURN"))
    private void lumora$breakDelayContinue(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        destroyDelay = McBuilding.breakDelay(destroyDelay);
    }
}
