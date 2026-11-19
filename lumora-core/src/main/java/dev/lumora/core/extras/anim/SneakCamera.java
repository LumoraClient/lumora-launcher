package dev.lumora.core.extras.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;

/**
 * "Sneaking": the camera height while sneaking and the matching offset of the own player in third person. Version
 * independent; the version mixins call {@link #eye} after Minecraft's {@code Camera.tick} and
 * {@link #ownModelOffset} for the own player's render offset.
 * <p>
 * Original code:
 * <ul>
 *     <li>1.7.10: sneaking sets {@code ySize} to 0.2, {@code moveEntity} multiplies it by 0.4 each tick and lowers the
 *     player's position by it: 0.08 lower while sneaking (reached within one tick), when getting up 0.032, 0.0128 …
 *     The model of the own player moves down with the position.</li>
 *     <li>1.8.9: {@code getEyeHeight()} is 1.62 − 0.08 while sneaking, read every frame: no transition. The own player's
 *     model is not lowered.</li>
 *     <li>modern: the eye height glides to the crouching pose (1.27) with half the rest per tick; all crouching players
 *     are drawn 2/16 lower.</li>
 * </ul>
 * Only in the standing and crouching pose; swimming, gliding, sleeping and a forced crouch under a low ceiling stay
 * modern (1.7 and 1.8 have no such poses, and a legacy camera would be inside the blocks above).
 */
public final class SneakCamera {
    /** 0.08 of 1.62: the 1.7 / 1.8 sneak drop relative to the standing eye height. */
    static final float DROP = 0.08F / 1.62F;
    /** The 1.7 {@code ySize} offset of the camera player (in blocks, scaled), render thread only. */
    private static float offset17;
    private static boolean snap;

    private SneakCamera() {
    }

    /**
     * The camera eye height after this tick, or {@code NaN} to keep Minecraft's.
     *
     * @param sneaking    crouching pose and the player could stand up there
     * @param standing    standing pose
     * @param standingEye eye height of the standing pose
     */
    public static float eye(boolean sneaking, boolean standing, float standingEye) {
        Style s = AnimationsModule.style(Feature.SNEAK);
        if (!s.legacy() || !sneaking && !standing) {
            offset17 = 0.0F;
            snap = false;
            return Float.NaN;
        }
        float drop = standingEye * DROP;
        if (s == Style.LEGACY_1_8) {
            offset17 = 0.0F;
            snap = true;
            return sneaking ? standingEye - drop : standingEye;
        }
        snap = false;
        offset17 = sneaking ? drop : offset17 * 0.4F;
        if (offset17 < 1.0E-4F) {
            offset17 = 0.0F;
        }
        return standingEye - offset17;
    }

    /** 1.8: the eye height is read every frame, so also the old value of the camera's interpolation is set. */
    public static boolean snap() {
        return snap;
    }

    /**
     * The vertical render offset of the own player model in third person instead of Minecraft's crouch offset, or
     * {@code NaN} to keep it.
     *
     * @param crouching the render state is crouching
     * @param scale     the player's scale
     */
    public static double ownModelOffset(boolean crouching, float scale) {
        Style s = AnimationsModule.style(Feature.SNEAK);
        if (s == Style.LEGACY_1_8) {
            return 0.0;
        }
        if (s == Style.LEGACY_1_7) {
            return -offset17;
        }
        return Double.NaN;
    }
}
