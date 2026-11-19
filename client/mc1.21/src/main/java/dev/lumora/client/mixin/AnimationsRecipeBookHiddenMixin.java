package dev.lumora.client.mixin;

import dev.lumora.core.extras.AnimationsModule;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lumora "Animations", "Remove recipe book": the recipe book counts as closed in this client, so it is neither drawn
 * nor takes input and Minecraft centres the window as with a closed book. The open/closed setting Minecraft keeps for
 * the player (and sends to the server) is not touched.
 */
@Mixin(RecipeBookComponent.class)
public abstract class AnimationsRecipeBookHiddenMixin {
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void lumora$hidden(CallbackInfoReturnable<Boolean> cir) {
        if (AnimationsModule.removeRecipeBook()) {
            cir.setReturnValue(false);
        }
    }
}
