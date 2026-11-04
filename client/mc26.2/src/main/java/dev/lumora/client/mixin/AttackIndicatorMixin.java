package dev.lumora.client.mixin;

import dev.lumora.core.extras.AttackIndicatorModule;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lumora attack indicator: while the module is on, Minecraft's crosshair and hotbar read the attack indicator option
 * as OFF, so only Lumora's indicator is drawn. Other options read in these methods pass through; the option itself is
 * not changed (and not saved).
 */
@Mixin(Hud.class)
public abstract class AttackIndicatorMixin {
    @Redirect(method = {"extractCrosshair", "extractItemHotbar"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object lumora$attackIndicator(OptionInstance<?> option) {
        if (AttackIndicatorModule.hidesVanilla() && option == Minecraft.getInstance().options.attackIndicator()) {
            return AttackIndicatorStatus.OFF;
        }
        return option.get();
    }
}
