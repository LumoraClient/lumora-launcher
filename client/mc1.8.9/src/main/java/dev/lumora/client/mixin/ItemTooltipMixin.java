package dev.lumora.client.mixin;

import dev.lumora.core.render.ItemTooltips;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Tooltip lines of addons (Lumora API 1.7 ItemTooltipEvent): appended below the game's lines whenever a tooltip is built. */
@Mixin(ItemStack.class)
public abstract class ItemTooltipMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void lumora$tooltip(CallbackInfoReturnable<List<String>> cir) {
        if (!ItemTooltips.active() || !MinecraftClient.getInstance().isOnThread()) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        List<String> lines = cir.getReturnValue();
        if (lines == null || self.getItem() == null) {
            return;
        }
        Object id = Item.REGISTRY.getIdentifier(self.getItem());
        if (id == null) {
            return;
        }
        try {
            lines.addAll(ItemTooltips.extra(id.toString(), self.count));
        } catch (UnsupportedOperationException fixed) {
            // an immutable list: nothing to add to
        }
    }
}
