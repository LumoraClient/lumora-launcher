package dev.lumora.core.extras.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;

/**
 * First-person item transforms of Minecraft 1.7.10 and 1.8.9 ("Item posture", "Sword / blocking", "Bow",
 * "Eating / drinking", "Fishing rod"), version independent. The version mixins let Minecraft prepare the main-hand
 * item without its own first-person display transform ({@code ItemDisplayContext.NONE}) and, instead of Minecraft's
 * arm transform, call {@link #apply} right before the item is drawn. Minecraft then only adds its usual
 * {@code translate(-0.5, -0.5, -0.5)} before the model, as 1.7 and 1.8 did.
 * <p>
 * The numbers are the ones of the original code:
 * <ul>
 *     <li>1.8.9 {@code ItemRenderer.renderItemInFirstPerson}, {@code transformFirstPersonItem},
 *     {@code doItemUsedTransformations}, {@code performDrinking}, {@code doBlockTransformations},
 *     {@code doBowTransformations}; then {@code ItemCameraTransforms.applyTransform(FIRST_PERSON)} (translation,
 *     rotation in the order Y, X, Z, scale) with the "firstperson" values of the 1.8 item models and the
 *     {@code scale(0.5)} of {@code RenderItem.renderItem}.</li>
 *     <li>1.7.10 {@code ItemRenderer.renderItemInFirstPerson}: its own placement (0.7·0.8, −0.65·0.8, −0.9·0.8), the swing
 *     rotation also while using an item (block hitting), {@code shouldRotateAroundWhenRendering} for rods and for flat
 *     items the placement of {@code renderItemIn2D} (sprite drawn mirrored, here a turn by 180° so no face is culled).</li>
 * </ul>
 * Main hand only (1.7 and 1.8 have no off hand). Items that did not exist then and have their own first-person
 * animation (crossbow, shield, trident, spyglass, maps …) are left to Minecraft by the version mixins.
 */
public final class ItemPose {
    /** What the hand does with the item. */
    public enum Use {
        NONE, EAT_DRINK, BLOCK, BOW, OTHER
    }

    /**
     * How the 1.8 models show the item ("firstperson" and "thirdperson" display values of the 1.8.9 item models,
     * resolved through their parents) and how 1.7 draws it (flat sprite, "full 3D" item, bow or block).
     */
    public enum Kind {
        /** Flat item ({@code builtin/generated}). */
        FLAT(Display.FP_FLAT, new Display(-90, 0, 0, 0, 1, -3, 0.55F), Draw17.FLAT),
        /** Flat items whose 1.8 model sits a little further out in third person (helmets, ghast tear, rabbit foot). */
        FLAT_2_25(Display.FP_FLAT, new Display(-90, 0, 0, 0, 1, -2.25F, 0.55F), Draw17.FLAT),
        /** Boots, bowl, stews, seeds, nether wart and some dyes. */
        FLAT_2_5(Display.FP_FLAT, new Display(-90, 0, 0, 0, 1, -2.5F, 0.55F), Draw17.FLAT),
        /** Gold nugget. */
        FLAT_2(Display.FP_FLAT, new Display(-90, 0, 0, 0, 1, -2, 0.55F), Draw17.FLAT),
        /** Tools, swords, stick, bone (1.7: {@code isFull3D}). */
        HANDHELD(Display.FP_FLAT, new Display(0, 90, -35, 0, 1.25F, -3.5F, 0.85F), Draw17.FULL_3D),
        BLAZE_ROD(Display.FP_FLAT, new Display(0, 90, -35, 0, 0.75F, -3.5F, 0.85F), Draw17.FULL_3D),
        BOW(Display.FP_FLAT, new Display(5, 80, -45, 0.75F, 0, 0.25F, 1), Draw17.BOW),
        /** Fishing rod and carrot on a stick (1.7: full 3D and turned round). */
        ROD(new Display(0, 45, 25, 0, 4, 2, 1.7F), new Display(180, 90, -35, 0, 0, -3.5F, 0.85F), Draw17.FULL_3D),
        /** 3D block: no first-person display values in 1.8. */
        BLOCK(Display.NONE, new Display(10, -45, 170, 0, 1.5F, -2.75F, 0.375F), Draw17.BLOCK),
        CARPET(new Display(0, 0, 0, 0, 5.25F, 0, 1), new Display(10, -45, 170, 0, 0.25F, -2.75F, 0.375F), Draw17.BLOCK),
        /** Trapdoors and the daylight detector. */
        TRAPDOOR(new Display(0, 0, 0, 0, 5, 0, 1), new Display(10, -45, 170, 0, 0.25F, -2.75F, 0.375F), Draw17.BLOCK),
        FENCE_GATE(new Display(0, 90, 0, 0, 0, 0, 1), new Display(0, -90, 170, 0, 1.5F, -2.75F, 0.375F), Draw17.BLOCK),
        FENCE(Display.NONE, new Display(0, 0, 180, 0, 1.5F, -2.75F, 0.375F), Draw17.BLOCK),
        BUTTON(Display.NONE, new Display(10, -45, 170, 0, 1, -1.75F, 0.375F), Draw17.BLOCK),
        ANVIL(Display.NONE, new Display(10, -45, 170, 0.25F, 1.5F, -2.5F, 0.375F), Draw17.BLOCK),
        BANNER(new Display(0, 225, 0, 0, 0, 0, 1), new Display(0, 90, -90, 0, 0, -4, 0.5F), Draw17.BLOCK),
        SKULL(new Display(0, -180, 0, 0, 0, 0, 0.55F), new Display(180, -45, 0, 0, 1, -2.5F, 0.25F), Draw17.BLOCK);

        final Display first;
        final Display third;
        final Draw17 draw17;

        Kind(Display first, Display third, Draw17 draw17) {
            this.first = first;
            this.third = third;
            this.draw17 = draw17;
        }

        /** Whether 1.7 draws it as a flat sprite ({@code renderItemIn2D}) rather than a block. */
        boolean flat17() {
            return draw17 != Draw17.BLOCK;
        }
    }

    /** How 1.7.10 draws an item ({@code RenderPlayer.renderEquippedItems} branches). */
    enum Draw17 {
        FLAT, FULL_3D, BOW, BLOCK
    }

    /** One display transform of a 1.8 model: rotation (degrees), translation (pixels), scale. */
    record Display(float rx, float ry, float rz, float tx, float ty, float tz, float scale) {
        static final Display NONE = new Display(0, 0, 0, 0, 0, 0, 1);
        static final Display FP_FLAT = new Display(0, -135, 25, 0, 4, 2, 1.7F);

        /** {@code ItemCameraTransforms.applyTransform}: translation, rotation Y, X, Z, scale. */
        void apply(Matrix p) {
            p.translate(tx / 16.0F, ty / 16.0F, tz / 16.0F);
            p.rotateY(ry);
            p.rotateX(rx);
            p.rotateZ(rz);
            p.scale(scale, scale, scale);
        }
    }

    /** The matrix operations of the running version's PoseStack (degrees, like {@code GlStateManager.rotate}). */
    public interface Matrix {
        void translate(float x, float y, float z);

        void rotateX(float degrees);

        void rotateY(float degrees);

        void rotateZ(float degrees);

        void scale(float x, float y, float z);
    }

    private static final float PI = (float) Math.PI;
    /** Decided when Minecraft prepares the hands (render thread), used when it draws them right after. */
    private static Style frameVersion;
    private static Use frameUse = Use.NONE;

    private ItemPose() {
    }

    /** Remembers the decision for the main hand of this frame ({@code null}: Minecraft draws it). */
    public static void decide(Style version, Use use) {
        frameVersion = version;
        frameUse = use;
    }

    /** The legacy version that draws the main hand this frame, {@code null} for Minecraft. */
    public static Style frameVersion() {
        return frameVersion;
    }

    /** What the main hand does this frame (BLOCK also for the shield case). */
    public static Use frameUse() {
        return frameUse;
    }

    /**
     * The legacy version that draws the main hand now, or {@code null} for Minecraft's own drawing.
     *
     * @param use        what the main hand does (NONE when not using it)
     * @param sword      the item is a sword
     * @param rod        fishing rod or carrot on a stick
     * @param shieldBlocking the player blocks with a shield in the off hand while holding a sword (1.9+ servers block
     *                   like that; 1.7 and 1.8 showed the sword in block pose), or holds the use key with a sword while
     *                   "Old block animation" is on
     */
    public static Style select(Use use, boolean sword, boolean rod, boolean shieldBlocking) {
        Feature f;
        if (use == Use.BLOCK || use == Use.NONE && sword && shieldBlocking) {
            f = sword ? Feature.SWORD : Feature.POSTURE;
        } else if (use == Use.BOW) {
            f = Feature.BOW;
        } else if (use == Use.EAT_DRINK) {
            f = Feature.FOOD;
        } else if (use == Use.OTHER) {
            return null; // an item use 1.7 / 1.8 did not have
        } else if (rod) {
            f = Feature.FISHING_ROD;
        } else {
            f = Feature.POSTURE;
        }
        Style s = AnimationsModule.style(f);
        if (f == Feature.SWORD && !s.legacy() && AnimationsModule.oldBlocking()) {
            s = Feature.SWORD.legacy; // "Old block animation": the 1.7 block pose without the rest of the legacy look
        }
        return s == Style.LEGACY_1_7 || s == Style.LEGACY_1_8 ? s : null;
    }

    /**
     * Applies the whole legacy first-person transform for the main hand, ending right before the model (Minecraft then
     * adds its {@code translate(-0.5, -0.5, -0.5)}).
     *
     * @param version          {@link Style#LEGACY_1_7} or {@link Style#LEGACY_1_8}
     * @param right            the main arm is the right one (left: mirrored like Minecraft mirrors the left hand)
     * @param inverseArmHeight 1 − equip progress (0 = fully up)
     * @param swing            swing progress 0..1
     * @param use              what the hand does
     * @param useCount         remaining use ticks − partial tick + 1 (1.8's {@code getItemInUseCount() - f + 1})
     * @param useDuration      the item's maximum use duration
     * @param kind             how the item is shown
     */
    public static void apply(Style version, Matrix m, boolean right, float inverseArmHeight, float swing, Use use,
                             float useCount, int useDuration, Kind kind) {
        Matrix p = right ? m : new Mirrored(m);
        if (version == Style.LEGACY_1_7) {
            apply17(p, inverseArmHeight, swing, use, useCount, useDuration, kind);
        } else {
            apply18(p, inverseArmHeight, swing, use, useCount, useDuration, kind);
        }
    }

    /**
     * "Item posture (third person)": the legacy style for a player's main-hand item, or {@code null} for Minecraft.
     */
    public static Style selectThird() {
        Style s = AnimationsModule.style(Feature.POSTURE_THIRD);
        return s == Style.LEGACY_1_7 || s == Style.LEGACY_1_8 ? s : null;
    }

    /**
     * The legacy third-person transform of the main-hand item, applied right after Minecraft moved to the hand
     * ({@code translateToHand}, the same as 1.7 / 1.8's {@code postRenderArm(0.0625)}) instead of Minecraft's own
     * steps; ends right before the model.
     * <p>
     * 1.8.9 {@code LayerHeldItem.doRenderLayer}: {@code translate(-1/16, 7/16, 1/16)}, then the "thirdperson" display
     * values and {@code scale(0.5)}. (Its extra 0.203125 while sneaking makes up for the body offset 1.8 applied only
     * to the model; Minecraft's crouching arm already contains it.) 1.7.10 {@code RenderPlayer.renderEquippedItems}:
     * the same first step, then one placement for blocks, the bow, "full 3D" items (rods turned round, an extra turn
     * while blocking) and flat items, followed by the flat sprite placement of {@code ItemRenderer.renderItem}.
     *
     * @param right the item is in the right hand (left: mirrored like Minecraft mirrors the left hand)
     */
    public static void applyThird(Style version, Matrix m, boolean right, Use use, Kind kind) {
        Matrix p = right ? m : new Mirrored(m);
        p.translate(-0.0625F, 0.4375F, 0.0625F);
        if (version == Style.LEGACY_1_8) {
            kind.third.apply(p);
            p.scale(0.5F, 0.5F, 0.5F);
            return;
        }
        switch (kind.draw17) {
            case BLOCK -> {
                p.translate(0.0F, 0.1875F, -0.3125F);
                p.rotateX(20.0F);
                p.rotateY(45.0F);
                p.scale(-0.375F, -0.375F, 0.375F);
                return; // RenderBlocks.renderBlockAsItem: the cube around the origin, like Minecraft's model
            }
            case BOW -> {
                p.translate(0.0F, 0.125F, 0.3125F);
                p.rotateY(-20.0F);
                p.scale(0.625F, -0.625F, 0.625F);
                p.rotateX(-100.0F);
                p.rotateY(45.0F);
            }
            case FULL_3D -> {
                if (kind == Kind.ROD) {
                    p.rotateZ(180.0F);
                    p.translate(0.0F, -0.125F, 0.0F);
                }
                if (use == Use.BLOCK) {
                    p.translate(0.05F, 0.0F, -0.1F);
                    p.rotateY(-50.0F);
                    p.rotateX(-10.0F);
                    p.rotateZ(-60.0F);
                }
                p.translate(0.0F, 0.1875F, 0.0F);
                p.scale(0.625F, -0.625F, 0.625F);
                p.rotateX(-100.0F);
                p.rotateY(45.0F);
            }
            default -> {
                p.translate(0.25F, 0.1875F, -0.1875F);
                p.scale(0.375F, 0.375F, 0.375F);
                p.rotateZ(60.0F);
                p.rotateX(-90.0F);
                p.rotateZ(20.0F);
            }
        }
        flatSprite17(p);
    }

    /**
     * 1.7.10 {@code ItemRenderer.renderItem} for a flat item, then {@code renderItemIn2D} (sprite from 0 to 1, 1/16
     * deep, mirrored); Minecraft's model spans −0.5..0.5 around the origin after its own {@code translate(-0.5)}: it is
     * moved to 0..1, half a pixel back, and turned so the mirrored side faces where the 1.7 sprite did.
     */
    static void flatSprite17(Matrix p) {
        p.translate(-0.0F, -0.3F, 0.0F);
        p.scale(1.5F, 1.5F, 1.5F);
        p.rotateY(50.0F);
        p.rotateZ(335.0F);
        p.translate(-0.9375F, -0.0625F, 0.0F);
        p.translate(0.5F, 0.5F, -0.03125F);
        p.rotateY(180.0F);
    }

    /**
     * 1.7 / 1.8 blocking arm in third person instead of Minecraft's (which follows the head): x like Minecraft without
     * the head, turned by 30° in 1.8, not turned in 1.7.
     *
     * @return {xRot, yRot}
     */
    public static float[] blockingArm(Style version, float xRot, boolean right) {
        float x = xRot * 0.5F - 0.9424779F;
        float y = version == Style.LEGACY_1_8 ? (right ? -0.5235988F : 0.5235988F) : 0.0F;
        return new float[]{x, y};
    }

    // --- 1.8.9

    static void apply18(Matrix p, float f, float swing, Use use, float useCount, int duration, Kind kind) {
        switch (use) {
            case EAT_DRINK -> {
                drinking18(p, useCount, duration);
                transformFirstPersonItem(p, f, 0.0F);
            }
            case BLOCK -> {
                transformFirstPersonItem(p, f, 0.0F);
                blockTransformations(p);
            }
            case BOW -> {
                transformFirstPersonItem(p, f, 0.0F);
                bow18(p, useCount, duration);
            }
            default -> {
                // 1.8 swings an item it is not using; while blocking with a shield the sword stays still
                itemUsedTransformations(p, swing);
                transformFirstPersonItem(p, f, swing);
            }
        }
        // ItemCameraTransforms.applyTransform(FIRST_PERSON): translation, rotation Y, X, Z, scale
        kind.first.apply(p);
        // RenderItem.renderItem
        p.scale(0.5F, 0.5F, 0.5F);
    }

    static void transformFirstPersonItem(Matrix p, float f, float swing) {
        p.translate(0.56F, -0.52F, -0.71999997F);
        p.translate(0.0F, f * -0.6F, 0.0F);
        p.rotateY(45.0F);
        float a = sin(swing * swing * PI);
        float b = sin(sqrt(swing) * PI);
        p.rotateY(a * -20.0F);
        p.rotateZ(b * -20.0F);
        p.rotateX(b * -80.0F);
        p.scale(0.4F, 0.4F, 0.4F);
    }

    static void itemUsedTransformations(Matrix p, float swing) {
        float x = -0.4F * sin(sqrt(swing) * PI);
        float y = 0.2F * sin(sqrt(swing) * PI * 2.0F);
        float z = -0.2F * sin(swing * PI);
        p.translate(x, y, z);
    }

    static void drinking18(Matrix p, float useCount, int duration) {
        float progress = useCount / (float) duration;
        float jiggle = Math.abs((float) Math.cos(useCount / 4.0F * PI) * 0.1F);
        if (progress >= 0.8F) {
            jiggle = 0.0F;
        }
        p.translate(0.0F, jiggle, 0.0F);
        float e = 1.0F - (float) Math.pow(progress, 27.0);
        p.translate(e * 0.6F, e * -0.5F, e * 0.0F);
        p.rotateY(e * 90.0F);
        p.rotateX(e * 10.0F);
        p.rotateZ(e * 30.0F);
    }

    static void blockTransformations(Matrix p) {
        p.translate(-0.5F, 0.2F, 0.0F);
        p.rotateY(30.0F);
        p.rotateX(-80.0F);
        p.rotateY(60.0F);
    }

    static void bow18(Matrix p, float useCount, int duration) {
        p.rotateZ(-18.0F);
        p.rotateY(-12.0F);
        p.rotateX(-8.0F);
        p.translate(-0.9F, 0.2F, 0.0F);
        float held = (float) duration - useCount;
        float power = bowPower(held);
        if (power > 0.1F) {
            float shake = sin((held - 0.1F) * 1.3F) * (power - 0.1F);
            p.translate(shake * 0.0F, shake * 0.01F, shake * 0.0F);
        }
        p.translate(power * 0.0F, power * 0.0F, power * 0.1F);
        p.scale(1.0F, 1.0F, 1.0F + power * 0.2F);
    }

    static float bowPower(float held) {
        float power = held / 20.0F;
        power = (power * power + power * 2.0F) / 3.0F;
        return Math.min(power, 1.0F);
    }

    // --- 1.7.10

    static void apply17(Matrix p, float f, float swing, Use use, float useCount, int duration, Kind kind) {
        float scale = 0.8F;
        if (use == Use.EAT_DRINK) {
            float progress = 1.0F - useCount / (float) duration;
            float rest = 1.0F - progress;
            rest = rest * rest * rest;
            rest = rest * rest * rest;
            rest = rest * rest * rest;
            float e = 1.0F - rest;
            p.translate(0.0F, Math.abs((float) Math.cos(useCount / 4.0F * PI) * 0.1F) * (progress > 0.2F ? 1 : 0), 0.0F);
            p.translate(e * 0.6F, -e * 0.5F, 0.0F);
            p.rotateY(e * 90.0F);
            p.rotateX(e * 10.0F);
            p.rotateZ(e * 30.0F);
        } else if (use == Use.NONE) {
            float a = sin(swing * PI);
            float b = sin(sqrt(swing) * PI);
            p.translate(-b * 0.4F, sin(sqrt(swing) * PI * 2.0F) * 0.2F, -a * 0.2F);
        }
        p.translate(0.7F * scale, -0.65F * scale - f * 0.6F, -0.9F * scale);
        p.rotateY(45.0F);
        // the swing rotation runs while using as well: block hitting
        float a = sin(swing * swing * PI);
        float b = sin(sqrt(swing) * PI);
        p.rotateY(-a * 20.0F);
        p.rotateZ(-b * 20.0F);
        p.rotateX(-b * 80.0F);
        p.scale(0.4F, 0.4F, 0.4F);
        if (use == Use.BLOCK) {
            blockTransformations(p);
        } else if (use == Use.BOW) {
            p.rotateZ(-18.0F);
            p.rotateY(-12.0F);
            p.rotateX(-8.0F);
            p.translate(-0.9F, 0.2F, 0.0F);
            float held = (float) duration - useCount;
            float power = bowPower(held);
            if (power > 0.1F) {
                p.translate(0.0F, sin((held - 0.1F) * 1.3F) * 0.01F * (power - 0.1F), 0.0F);
            }
            p.translate(0.0F, 0.0F, power * 0.1F);
            p.rotateZ(-335.0F);
            p.rotateY(-50.0F);
            p.translate(0.0F, 0.5F, 0.0F);
            p.scale(1.0F, 1.0F, 1.0F + power * 0.2F);
            p.translate(0.0F, -0.5F, 0.0F);
            p.rotateY(50.0F);
            p.rotateZ(335.0F);
        }
        if (kind == Kind.ROD) {
            p.rotateY(180.0F); // Item.shouldRotateAroundWhenRendering
        }
        if (kind.flat17()) {
            flatSprite17(p);
        }
        // blocks: RenderBlocks.renderBlockAsItem draws the cube around the origin, as Minecraft's model does
    }

    private static float sin(float v) {
        return (float) Math.sin(v);
    }

    private static float sqrt(float v) {
        return (float) Math.sqrt(v);
    }

    /** The left hand: mirrored on the x axis like Minecraft's {@code invert} (x moves and Y/Z turns change sign). */
    static final class Mirrored implements Matrix {
        private final Matrix m;

        Mirrored(Matrix m) {
            this.m = m;
        }

        @Override
        public void translate(float x, float y, float z) {
            m.translate(-x, y, z);
        }

        @Override
        public void rotateX(float degrees) {
            m.rotateX(degrees);
        }

        @Override
        public void rotateY(float degrees) {
            m.rotateY(-degrees);
        }

        @Override
        public void rotateZ(float degrees) {
            m.rotateZ(-degrees);
        }

        @Override
        public void scale(float x, float y, float z) {
            m.scale(x, y, z);
        }
    }
}
