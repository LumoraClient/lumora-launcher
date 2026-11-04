package dev.lumora.client.mixin;

import dev.lumora.core.extras.AttackIndicatorModule;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lumora attack indicator: while the module is on, Minecraft's crosshair and hotbar read the attack indicator option
 * as OFF, so only Lumora's indicator is drawn. The option itself is not changed (and not saved).
 */
@Mixin(Gui.class)
public abstract class AttackIndicatorMixin {
    @Redirect(method = {"renderCrosshair", "renderHotbar"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;attackIndicator:Lnet/minecraft/client/AttackIndicatorStatus;", opcode = Opcodes.GETFIELD))
    private AttackIndicatorStatus lumora$attackIndicator(Options options) {
        return AttackIndicatorModule.hidesVanilla() ? AttackIndicatorStatus.OFF : options.attackIndicator;
    }
}
