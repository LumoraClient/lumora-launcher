package dev.lumora.client.mixin;

import dev.lumora.core.extras.ToggleSprintModule;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Toggle Sprint", fly speed: the horizontal flying speed the own player's travel uses
 * ({@code PlayerAbilities#getFlySpeed} in {@code PlayerEntity#method_2657 (travel)}) times {@link ToggleSprintModule#flySpeed}.
 * Other players and the integrated server are not touched; the stored ability value stays as it is.
 */
@Mixin(PlayerEntity.class)
public abstract class FlySpeedMixin {
    @Redirect(method = "method_2657", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerAbilities;getFlySpeed()F"))
    private float lumora$flySpeed(PlayerAbilities abilities) {
        float speed = abilities.getFlySpeed();
        return (Object) this instanceof ClientPlayerEntity ? speed * ToggleSprintModule.flySpeed() : speed;
    }
}
