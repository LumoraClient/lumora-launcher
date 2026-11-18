package dev.lumora.client.mixin;

import dev.lumora.core.extras.ToggleSprintModule;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Toggle Sprint", fly speed: flying up and down ({@code PlayerAbilities#getFlySpeed} in
 * {@code ClientPlayerEntity#tickMovement}, jump and sneak while flying) times {@link ToggleSprintModule#flySpeed}.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class FlySpeedVerticalMixin {
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerAbilities;getFlySpeed()F"))
    private float lumora$flyVertical(PlayerAbilities abilities) {
        return abilities.getFlySpeed() * ToggleSprintModule.flySpeed();
    }
}
