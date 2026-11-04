package dev.lumora.client.mixin;

import dev.lumora.core.extras.AttackIndicatorModule;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lumora attack indicator: while the module is on, Minecraft's crosshair (method_12164) and hotbar read the attack
 * indicator option as "off" (0), so only Lumora's indicator is drawn. The option itself is not changed.
 */
@Mixin(InGameHud.class)
public abstract class AttackIndicatorMixin {
    @Redirect(method = {"method_12164", "renderHotbar"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;field_13290:I", opcode = Opcodes.GETFIELD))
    private int lumora$attackIndicator(GameOptions options) {
        return AttackIndicatorModule.hidesVanilla() ? 0 : options.field_13290;
    }
}
