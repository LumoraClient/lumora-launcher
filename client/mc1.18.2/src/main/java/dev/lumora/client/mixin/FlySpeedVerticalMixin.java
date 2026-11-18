package dev.lumora.client.mixin;

import dev.lumora.core.extras.ToggleSprintModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Toggle Sprint", fly speed: flying up and down ({@code Abilities#getFlyingSpeed} in {@code LocalPlayer#aiStep},
 * jump and sneak while flying) times {@link ToggleSprintModule#flySpeed}.
 */
@Mixin(LocalPlayer.class)
public abstract class FlySpeedVerticalMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F"))
    private float lumora$flyVertical(Abilities abilities) {
        return abilities.getFlyingSpeed() * ToggleSprintModule.flySpeed();
    }
}
