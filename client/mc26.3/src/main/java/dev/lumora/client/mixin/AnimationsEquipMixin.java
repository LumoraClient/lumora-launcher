package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.HandEquip;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations": when the main hand dips in first person ("Item equip", "Block building"), see {@link HandEquip}.
 * Replaces {@code FirstPersonHandsAndItems.tick} only while a legacy style or a pending build dip needs it; the off
 * hand keeps Minecraft's own logic (1.7 and 1.8 have none).
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class AnimationsEquipMixin {
    @Shadow
    private ItemStack mainHandItem;
    @Shadow
    private ItemStack offHandItem;
    @Shadow
    private float mainHandHeight;
    @Shadow
    private float oMainHandHeight;
    @Shadow
    private float offHandHeight;
    @Shadow
    private float oOffHandHeight;
    /** The hotbar slot of the shown item (1.7 compares it). */
    @Unique
    private int lumora$slot = -1;

    @Shadow
    private boolean shouldInstantlyReplaceVisibleItem(ItemStack currentlyVisibleItem, ItemStack expectedItem, LocalPlayer player) {
        throw new AssertionError("mixin");
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void lumora$tick(LocalPlayer player, CallbackInfo ci) {
        HandEquip.Mode mode = HandEquip.mode();
        if (mode == HandEquip.Mode.VANILLA) {
            this.lumora$slot = player.getInventory().getSelectedSlot();
            return;
        }
        ci.cancel();
        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        ItemStack next = player.getMainHandItem();
        ItemStack nextOff = player.getOffhandItem();
        int slot = player.getInventory().getSelectedSlot();
        boolean reequip;
        if (mode == HandEquip.Mode.LEGACY_1_7) {
            // 1.7.10: same slot and same stack object; the same item with the same damage is swapped at once
            boolean same = slot == this.lumora$slot && next == this.mainHandItem || this.mainHandItem.isEmpty() && next.isEmpty();
            if (!next.isEmpty() && !this.mainHandItem.isEmpty() && next != this.mainHandItem
                    && next.getItem() == this.mainHandItem.getItem() && next.getDamageValue() == this.mainHandItem.getDamageValue()) {
                this.mainHandItem = next;
                same = true;
            }
            reequip = !same;
        } else if (mode == HandEquip.Mode.LEGACY_1_8) {
            // 1.8.9: the whole stack (item, count, damage, data)
            reequip = HandEquip.countReequips() ? !ItemStack.matches(this.mainHandItem, next)
                    : !ItemStack.isSameItemSameComponents(this.mainHandItem, next) && !(this.mainHandItem.isEmpty() && next.isEmpty());
            if (!reequip) {
                this.mainHandItem = next;
            }
        } else {
            if (this.shouldInstantlyReplaceVisibleItem(this.mainHandItem, next, player)) {
                this.mainHandItem = next;
            }
            reequip = this.mainHandItem != next;
        }
        reequip |= HandEquip.buildDip();
        if (this.shouldInstantlyReplaceVisibleItem(this.offHandItem, nextOff, player)) {
            this.offHandItem = nextOff;
        }
        if (player.isHandsBusy()) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float target;
            if (mode == HandEquip.Mode.MODERN_WITH_DIP) {
                float attack = player.getItemSwapScale(1.0F);
                target = reequip ? 0.0F : attack * attack * attack;
            } else {
                target = reequip ? 0.0F : 1.0F; // no attack cooldown before 1.9
            }
            float offTarget = this.offHandItem != nextOff ? 0.0F : 1.0F;
            this.mainHandHeight = this.mainHandHeight + Mth.clamp(target - this.mainHandHeight, -0.4F, 0.4F);
            this.offHandHeight = this.offHandHeight + Mth.clamp(offTarget - this.offHandHeight, -0.4F, 0.4F);
        }
        if (this.mainHandHeight < 0.1F) {
            this.mainHandItem = next;
            this.lumora$slot = slot;
            HandEquip.handDown();
        }
        if (this.offHandHeight < 0.1F) {
            this.offHandItem = nextOff;
        }
    }
}
