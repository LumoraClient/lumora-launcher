package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyArmed;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Item posture (third person)": decides for each armed player whether 1.7 / 1.8 draw the
 * main-hand item (see {@link ItemPose#applyThird}), remembers it on the render state and prepares that item without
 * Minecraft's third-person display transform ({@link ItemDisplayContext#NONE}).
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class AnimationsArmedStateMixin implements LegacyArmed {
    private static final String UPDATE = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForLiving(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/LivingEntity;)V";

    @Unique
    private AnimationsModule.Style lumora$style;
    @Unique
    private ItemPose.Use lumora$use = ItemPose.Use.NONE;
    @Unique
    private boolean lumora$shield;
    @Unique
    private ItemStack lumora$stack = ItemStack.EMPTY;

    @Override
    public AnimationsModule.Style lumora$thirdStyle() {
        return this.lumora$style;
    }

    @Override
    public ItemPose.Use lumora$thirdUse() {
        return this.lumora$use;
    }

    @Override
    public boolean lumora$shieldBlocking() {
        return this.lumora$shield;
    }

    @Override
    public ItemStack lumora$thirdStack() {
        return this.lumora$stack;
    }

    @Override
    public void lumora$setThird(AnimationsModule.Style style, ItemPose.Use use, boolean shieldBlocking, ItemStack stack) {
        this.lumora$stack = stack;
        this.lumora$style = style;
        this.lumora$use = use;
        this.lumora$shield = shieldBlocking;
    }

    @Inject(method = "extractArmedEntityRenderState", at = @At("HEAD"))
    private static void lumora$decide(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver, CallbackInfo ci) {
        ItemStack main = entity.getMainHandItem();
        AnimationsModule.Style style = LegacyHands.third(entity, main);
        ((LegacyArmed) state).lumora$setThird(style, style == null ? ItemPose.Use.NONE : LegacyHands.thirdUse(entity, main),
                style != null && LegacyHands.shieldBlocking(entity, main), main);
    }

    @Redirect(method = "extractArmedEntityRenderState", at = @At(value = "INVOKE", target = UPDATE, ordinal = 0))
    private static void lumora$prepareRight(ItemModelResolver resolver, ItemStackRenderState out, ItemStack stack, ItemDisplayContext context,
                                            LivingEntity entity) {
        resolver.updateForLiving(out, stack, lumora$context(entity, HumanoidArm.RIGHT, context), entity);
    }

    @Redirect(method = "extractArmedEntityRenderState", at = @At(value = "INVOKE", target = UPDATE, ordinal = 1))
    private static void lumora$prepareLeft(ItemModelResolver resolver, ItemStackRenderState out, ItemStack stack, ItemDisplayContext context,
                                           LivingEntity entity) {
        resolver.updateForLiving(out, stack, lumora$context(entity, HumanoidArm.LEFT, context), entity);
    }

    /** No display transform for the main-hand item while legacy draws it. */
    @Unique
    private static ItemDisplayContext lumora$context(LivingEntity entity, HumanoidArm arm, ItemDisplayContext context) {
        return entity.getMainArm() == arm && LegacyHands.third(entity, entity.getMainHandItem()) != null ? ItemDisplayContext.NONE : context;
    }
}
