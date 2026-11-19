package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", first-person item posture: decides per frame whether 1.7 / 1.8 draw the main hand (see
 * {@link ItemPose}) and then prepares that item without Minecraft's first-person display transform
 * ({@link ItemDisplayContext#NONE}); {@code AnimationsHandDrawMixin} applies the legacy transform instead.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class AnimationsHandPrepareMixin {
    @org.spongepowered.asm.mixin.Shadow
    private ItemStack mainHandItem;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void lumora$decide(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        LegacyHands.decide(player, this.mainHandItem);
    }

    /** The main hand (first call) is prepared without the first-person display transform while legacy draws it. */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V"))
    private void lumora$prepareMain(ItemModelResolver resolver, ItemStackRenderState out, ItemStack stack, ItemDisplayContext context,
                                    Level level, ItemOwner owner, int seed) {
        resolver.updateForTopItem(out, stack, ItemPose.frameVersion() != null ? ItemDisplayContext.NONE : context, level, owner, seed);
    }
}
