package dev.lumora.client.mixin;

import dev.lumora.core.extras.ToggleSprintModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Toggle Sprint", fly speed: the horizontal flying speed the own player's travel uses
 * ({@code Abilities#getFlyingSpeed} in {@code Player#travel}) times {@link ToggleSprintModule#flySpeed}. Other players
 * and the integrated server are not touched; the stored ability value stays as it is.
 */
@Mixin(Player.class)
public abstract class FlySpeedMixin {
    @Redirect(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F"))
    private float lumora$flySpeed(Abilities abilities) {
        float speed = abilities.getFlyingSpeed();
        return (Object) this instanceof LocalPlayer ? speed * ToggleSprintModule.flySpeed() : speed;
    }
}
