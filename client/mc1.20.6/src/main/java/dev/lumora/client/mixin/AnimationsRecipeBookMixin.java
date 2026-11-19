package dev.lumora.client.mixin;

import dev.lumora.core.extras.AnimationsModule;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Remove recipe book": the recipe book button of the inventory, crafting table and furnace
 * screens (their only image button) is hidden and inactive; {@code AnimationsRecipeBookHiddenMixin} keeps the book
 * itself closed in this client, so the window stays centred.
 */
@Mixin({InventoryScreen.class, CraftingScreen.class, AbstractFurnaceScreen.class})
public abstract class AnimationsRecipeBookMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void lumora$noButton(CallbackInfo ci) {
        if (!AnimationsModule.removeRecipeBook()) {
            return;
        }
        for (GuiEventListener child : ((Screen) (Object) this).children()) {
            if (child instanceof ImageButton) {
                ((AbstractWidget) child).visible = false;
                ((AbstractWidget) child).active = false;
            }
        }
    }
}
