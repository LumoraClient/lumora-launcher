package dev.lumora.client.mixin;

import dev.lumora.core.extras.AnimationsModule;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Remove recipe book": the recipe book button is not added to the inventory, crafting table and
 * furnace screens; {@code AnimationsRecipeBookHiddenMixin} keeps the book itself hidden so the window stays centred.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AnimationsRecipeBookMixin {
    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void lumora$noButton(CallbackInfo ci) {
        if (AnimationsModule.removeRecipeBook()) {
            ci.cancel();
        }
    }
}
