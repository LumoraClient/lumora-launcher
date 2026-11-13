package dev.lumora.client.mixin;

import dev.lumora.core.render.ItemTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Tooltip lines of addons (Lumora API 1.7 ItemTooltipEvent): appended below the game's lines whenever a tooltip is built. */
@Mixin(ItemStack.class)
public abstract class ItemTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void lumora$tooltip(CallbackInfoReturnable<List<Component>> cir) {
        // the search index builds tooltips on other threads; addons only expect the client thread
        if (!ItemTooltips.active() || !Minecraft.getInstance().isSameThread()) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        List<Component> lines = cir.getReturnValue();
        if (lines == null || self.isEmpty()) {
            return;
        }
        List<String> extra = ItemTooltips.extra(String.valueOf(net.minecraft.core.Registry.ITEM.getKey(self.getItem())), self.getCount());
        try {
            for (String line : extra) {
                lines.add(new net.minecraft.network.chat.TextComponent(line));
            }
        } catch (UnsupportedOperationException fixed) {
            // a hidden tooltip is an immutable empty list: nothing to add to
        }
    }
}
