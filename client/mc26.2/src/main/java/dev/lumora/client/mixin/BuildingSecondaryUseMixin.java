package dev.lumora.client.mixin;

import dev.lumora.client.api.McBuilding;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Building tools (Lumora API 1.10): during a right-click that an addon turned into a sneak click (Interaction Control)
 * the own player counts as sneaking, the same as the sneak input the server just received.
 */
@Mixin(Player.class)
public abstract class BuildingSecondaryUseMixin {
    @Inject(method = "isSecondaryUseActive", at = @At("RETURN"), cancellable = true)
    private void lumora$secondaryUse(CallbackInfoReturnable<Boolean> cir) {
        if (McBuilding.forceSecondary() && (Object) this instanceof LocalPlayer) {
            cir.setReturnValue(true);
        }
    }
}
