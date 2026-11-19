package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyCreative;
import dev.lumora.client.anim.LegacyTabItems;
import dev.lumora.core.extras.anim.CreativeTabs18;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lumora "Animations", "Creative layout" Legacy (1.8): Minecraft's tab slots show the 1.8 tabs (name, icon, contents,
 * see {@link CreativeTabs18}); the hotbar and operator tabs, which 1.8 did not have, are hidden. Only what the
 * creative screen shows changes; the items themselves and what is sent to the server stay the same.
 */
@Mixin(CreativeModeTab.class)
public abstract class AnimationsCreativeTabMixin implements LegacyTabItems {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    public abstract CreativeModeTab.Type getType();

    @Override
    public Collection<ItemStack> lumora$originalItems() {
        return this.displayItems;
    }

    @Unique
    private CreativeTabs18.Tab lumora$legacy() {
        return CreativeTabs18.active() ? LegacyCreative.slot((CreativeModeTab) (Object) this) : null;
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void lumora$name(CallbackInfoReturnable<Component> cir) {
        CreativeTabs18.Tab t = this.lumora$legacy();
        if (t != null) {
            cir.setReturnValue(LegacyCreative.name(t));
        }
    }

    @Inject(method = "getIconItem", at = @At("HEAD"), cancellable = true)
    private void lumora$icon(CallbackInfoReturnable<ItemStack> cir) {
        CreativeTabs18.Tab t = this.lumora$legacy();
        if (t != null) {
            cir.setReturnValue(LegacyCreative.icon(t));
        }
    }

    @Inject(method = "shouldDisplay", at = @At("HEAD"), cancellable = true)
    private void lumora$display(CallbackInfoReturnable<Boolean> cir) {
        if (!CreativeTabs18.active()) {
            return;
        }
        CreativeModeTab self = (CreativeModeTab) (Object) this;
        if (LegacyCreative.hidden(self)) {
            cir.setReturnValue(false);
        } else if (LegacyCreative.slot(self) != null) {
            cir.setReturnValue(!this.lumora$contents(LegacyCreative.slot(self)).isEmpty());
        }
    }

    @Inject(method = "getDisplayItems", at = @At("HEAD"), cancellable = true)
    private void lumora$items(CallbackInfoReturnable<Collection<ItemStack>> cir) {
        CreativeTabs18.Tab t = this.lumora$legacy();
        if (t != null) {
            cir.setReturnValue(this.lumora$contents(t));
        }
    }

    @Inject(method = "contains", at = @At("HEAD"), cancellable = true)
    private void lumora$contains(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        CreativeTabs18.Tab t = this.lumora$legacy();
        if (t != null) {
            for (ItemStack s : this.lumora$contents(t)) {
                if (ItemStack.isSameItemSameTags(s, stack)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void lumora$rebuilt(CallbackInfo ci) {
        LegacyCreative.invalidate();
    }

    /** The 1.8 contents, made from what Minecraft put into all its category tabs. */
    @Unique
    private List<ItemStack> lumora$contents(CreativeTabs18.Tab t) {
        Map<String, Collection<ItemStack>> original = new LinkedHashMap<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && tab.getType() == CreativeModeTab.Type.CATEGORY) {
                original.put(id.getPath(), ((LegacyTabItems) tab).lumora$originalItems());
            }
        }
        return LegacyCreative.contents(t, original);
    }
}
