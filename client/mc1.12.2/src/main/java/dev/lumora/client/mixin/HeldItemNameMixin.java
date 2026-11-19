package dev.lumora.client.mixin;

import dev.lumora.core.extras.InfoWidgetsModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Held Item Tooltip": Minecraft's name of the held item above the hotbar is skipped while the widget shows it. */
@Mixin(InGameHud.class)
public abstract class HeldItemNameMixin {
    @Inject(method = "renderHeldItemName", at = @At("HEAD"), cancellable = true)
    private void lumora$heldItemName(CallbackInfo ci) {
        if (InfoWidgetsModule.heldItemTooltipTakesOver()) {
            ci.cancel();
        }
    }
}
