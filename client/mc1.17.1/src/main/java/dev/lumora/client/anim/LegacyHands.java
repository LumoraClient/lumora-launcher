package dev.lumora.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * Lumora "Animations": the version part of the 1.7 / 1.8 first-person item posture ({@link ItemPose}): which items
 * take part, what the hand does, how the item is shown and the PoseStack of this version.
 */
public final class LegacyHands {
    /** 1.8 item models with "thirdperson" translation z -2.25 and -2.5 instead of -3 (1.8 names turned into today's ids). */
    private static final java.util.Set<String> FLAT_2_25 = java.util.Set.of("chainmail_helmet", "diamond_helmet", "golden_helmet",
            "iron_helmet", "leather_helmet", "ghast_tear", "rabbit_foot");
    private static final java.util.Set<String> FLAT_2_5 = java.util.Set.of("bowl", "chainmail_boots", "diamond_boots", "golden_boots",
            "iron_boots", "leather_boots", "gray_dye", "light_blue_dye", "magenta_dye", "pink_dye", "purple_dye", "melon_seeds",
            "pumpkin_seeds", "wheat_seeds", "mushroom_stew", "rabbit_stew", "nether_wart");

    private LegacyHands() {
    }

    /** Decides for this frame whether 1.7 / 1.8 draw the main hand ({@link ItemPose#decide}); {@code main}: the item shown in it. */
    public static void decide(Player player, ItemStack main) {
        if (AnimationsModule.active() == null) {
            ItemPose.decide(null, ItemPose.Use.NONE);
            return;
        }
        boolean using = player.isUsingItem() && player.getUseItemRemainingTicks() > 0;
        ItemPose.Use use = ItemPose.Use.NONE;
        if (using && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            use = use(main.getUseAnimation());
        }
        if (!legacyItem(main)) {
            ItemPose.decide(null, use);
            return;
        }
        boolean sword = main.getItem() instanceof net.minecraft.world.item.SwordItem;
        boolean shieldBlocking = using && player.getUsedItemHand() == InteractionHand.OFF_HAND && player.getOffhandItem().is(Items.SHIELD);
        ItemPose.decide(ItemPose.select(use, sword, rod(main), shieldBlocking),
                use == ItemPose.Use.NONE && sword && shieldBlocking ? ItemPose.Use.BLOCK : use);
    }

    /** "Item posture (third person)": the legacy style for this entity's main-hand item, {@code null} for Minecraft (players only). */
    public static AnimationsModule.Style third(net.minecraft.world.entity.LivingEntity e, ItemStack main) {
        if (!(e instanceof Player) || AnimationsModule.active() == null || !legacyItem(main)) {
            return null;
        }
        return ItemPose.selectThird();
    }

    /** What the main hand of a player does, for the third person (BLOCK also when blocking with a shield while holding a sword). */
    public static ItemPose.Use thirdUse(net.minecraft.world.entity.LivingEntity e, ItemStack main) {
        if (!e.isUsingItem()) {
            return ItemPose.Use.NONE;
        }
        if (e.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return use(main.getUseAnimation());
        }
        return shieldBlocking(e, main) ? ItemPose.Use.BLOCK : ItemPose.Use.NONE;
    }

    /** The player blocks with a shield in the off hand while holding a sword (1.7 / 1.8 showed the sword blocking). */
    public static boolean shieldBlocking(net.minecraft.world.entity.LivingEntity e, ItemStack main) {
        return e.isUsingItem() && e.getUsedItemHand() == InteractionHand.OFF_HAND && e.getOffhandItem().is(Items.SHIELD) && main.getItem() instanceof net.minecraft.world.item.SwordItem;
    }

    /** Items that existed in 1.7 / 1.8 or behave like them; everything with an own modern animation stays Minecraft's. */
    static boolean legacyItem(ItemStack s) {
        if (s.isEmpty() || s.is(Items.FILLED_MAP) || s.is(Items.SHIELD) || s.is(Items.CROSSBOW)) {
            return false;
        }
        UseAnim a = s.getUseAnimation();
        return a == UseAnim.NONE || a == UseAnim.EAT || a == UseAnim.DRINK
                || a == UseAnim.BLOCK || a == UseAnim.BOW;
    }

    static ItemPose.Use use(UseAnim a) {
        if (a == UseAnim.EAT || a == UseAnim.DRINK) {
            return ItemPose.Use.EAT_DRINK;
        }
        if (a == UseAnim.BLOCK) {
            return ItemPose.Use.BLOCK;
        }
        if (a == UseAnim.BOW) {
            return ItemPose.Use.BOW;
        }
        return a == UseAnim.NONE ? ItemPose.Use.NONE : ItemPose.Use.OTHER;
    }

    static boolean rod(ItemStack s) {
        return s.is(Items.FISHING_ROD) || s.is(Items.CARROT_ON_A_STICK) || s.is(Items.WARPED_FUNGUS_ON_A_STICK);
    }

    /** How the item is shown (1.8 display values, 1.7 drawing); {@code blockLight}: the baked model is lit like a block. */
    public static ItemPose.Kind kind(ItemStack s, boolean blockLight) {
        if (rod(s)) {
            return ItemPose.Kind.ROD;
        }
        if (s.is(Items.BOW)) {
            return ItemPose.Kind.BOW;
        }
        if (s.is(Items.BLAZE_ROD)) {
            return ItemPose.Kind.BLAZE_ROD;
        }
        if (s.getItem() instanceof net.minecraft.world.item.SwordItem || s.getItem() instanceof net.minecraft.world.item.DiggerItem
                || s.is(Items.STICK) || s.is(Items.BONE)) {
            return ItemPose.Kind.HANDHELD;
        }
        if (s.is(ItemTags.CARPETS)) {
            return ItemPose.Kind.CARPET;
        }
        if (s.getItem() instanceof BlockItem fg && fg.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
            return ItemPose.Kind.FENCE_GATE;
        }
        if (s.getItem() instanceof BlockItem fb && fb.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
            return ItemPose.Kind.FENCE;
        }
        if (s.getItem() instanceof BlockItem bb && bb.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock) {
            return ItemPose.Kind.BUTTON;
        }
        if (s.is(ItemTags.ANVIL)) {
            return ItemPose.Kind.ANVIL;
        }
        if (s.is(ItemTags.BANNERS)) {
            return ItemPose.Kind.BANNER;
        }
        if (s.getItem() instanceof BlockItem sk && sk.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock) {
            return ItemPose.Kind.SKULL;
        }
        if (s.getItem() instanceof BlockItem b && (b.getBlock() instanceof TrapDoorBlock || b.getBlock() instanceof DaylightDetectorBlock)) {
            return ItemPose.Kind.TRAPDOOR;
        }
        if (blockLight) {
            return ItemPose.Kind.BLOCK;
        }
        // flat items whose 1.8 model sits a little differently in third person (by id: names of dyes changed over the versions)
        String id = net.minecraft.core.Registry.ITEM.getKey(s.getItem()).getPath();
        if (FLAT_2_25.contains(id)) {
            return ItemPose.Kind.FLAT_2_25;
        }
        if (FLAT_2_5.contains(id)) {
            return ItemPose.Kind.FLAT_2_5;
        }
        return id.equals("gold_nugget") ? ItemPose.Kind.FLAT_2 : ItemPose.Kind.FLAT;
    }

    /** {@link ItemPose.Matrix} on this version's PoseStack. */
    public static final class Pose implements ItemPose.Matrix {
        private final PoseStack p;

        public Pose(PoseStack p) {
            this.p = p;
        }

        @Override
        public void translate(float x, float y, float z) {
            p.translate(x, y, z);
        }

        @Override
        public void rotateX(float degrees) {
            p.mulPose(Vector3f.XP.rotationDegrees(degrees));
        }

        @Override
        public void rotateY(float degrees) {
            p.mulPose(Vector3f.YP.rotationDegrees(degrees));
        }

        @Override
        public void rotateZ(float degrees) {
            p.mulPose(Vector3f.ZP.rotationDegrees(degrees));
        }

        @Override
        public void scale(float x, float y, float z) {
            p.scale(x, y, z);
        }
    }
}
