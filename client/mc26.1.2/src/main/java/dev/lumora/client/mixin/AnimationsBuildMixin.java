package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.HandEquip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lumora "Animations", "Block building": whether the main hand dips after placing a block (see {@link HandEquip}).
 * Only the dip after {@code useItemOn} (the first {@code itemUsed} in {@code startUseItem}); using an item in the air
 * keeps Minecraft's dip. What is placed and sent to the server does not change.
 */
@Mixin(Minecraft.class)
public abstract class AnimationsBuildMixin {
    @Redirect(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;itemUsed(Lnet/minecraft/world/InteractionHand;)V", ordinal = 0))
    private void lumora$blockPlaced(ItemInHandRenderer renderer, InteractionHand hand) {
        Minecraft mc = (Minecraft) (Object) this;
        if (hand != InteractionHand.MAIN_HAND || mc.player == null || HandEquip.blockPlaced(mc.player.hasInfiniteMaterials())) {
            renderer.itemUsed(hand);
        }
    }
}
