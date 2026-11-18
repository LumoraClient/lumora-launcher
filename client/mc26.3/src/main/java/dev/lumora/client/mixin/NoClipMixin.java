package dev.lumora.client.mixin;

import dev.lumora.client.api.McBuilding;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoClip of the building tools (Lumora API 1.12): right after {@code Player#tick} set {@code noPhysics} (true only for
 * spectators) it is also true for the own flying creative player while an addon has NoClip on, and in the own world
 * for the same player on the built-in server, which then accepts the movement through blocks.
 */
@Mixin(Player.class)
public abstract class NoClipMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void lumora$noClip(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (McBuilding.noClip(self)) {
            self.noPhysics = true;
        }
    }
}
