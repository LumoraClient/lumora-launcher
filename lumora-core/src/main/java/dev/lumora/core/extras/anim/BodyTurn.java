package dev.lumora.core.extras.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;

/**
 * Body rotation of players ("Backwards walking", "Head rotation"), version independent. The version mixins call
 * {@link #tick} at the start of Minecraft's {@code LivingEntity.tickHeadTurn} (client side, players only) with the
 * target Minecraft computed and use the result instead of running the original.
 * <p>
 * Original code: 1.7.10 {@code EntityLivingBase.func_110146_f} and 1.8.9 {@code EntityLivingBase.updateDistance} are the
 * same: the body follows the walking direction (also backwards), the head may turn 75° from the body and from 50° on
 * the body is pulled along by a fifth. Today's Minecraft turns the target round when walking backwards (95°–265° from
 * the view) and limits the head to {@code getMaxHeadRotationRelativeToBody} (50° for players).
 */
public final class BodyTurn {
    private BodyTurn() {
    }

    /**
     * The new body rotation, or {@code NaN} to let Minecraft's own code run (both parts unchanged).
     *
     * @param bodyRot  current {@code yBodyRot}
     * @param yRot     the view (head) rotation
     * @param target   the body target Minecraft computed this tick
     * @param dx       movement on x since the last tick
     * @param dz       movement on z since the last tick
     * @param attacking Minecraft aims the body at the view while the arm swings (1.7, 1.8 and modern alike)
     * @param maxModern Minecraft's {@code getMaxHeadRotationRelativeToBody()} (used for the modern head limit)
     * @param modernTurnsRound whether this Minecraft version itself turns the target round when walking backwards
     *                         (since 1.12; false: the version behaves like 1.8 and "Modern" has to turn it round here)
     * @param modernLimit whether this version itself has the modern head limit (false: "Modern" has to apply it here)
     */
    public static float tick(float bodyRot, float yRot, float target, boolean attacking, double dx, double dz, float maxModern,
                             boolean modernTurnsRound, boolean modernLimit) {
        Style back = AnimationsModule.style(Feature.BACKWARDS_WALKING);
        Style head = AnimationsModule.style(Feature.HEAD_ROTATION);
        boolean legacyBack = back.legacy() || back == Style.DEFAULT && !modernTurnsRound;
        boolean legacyHead = head.legacy() || head == Style.DEFAULT && !modernLimit;
        boolean changeBack = legacyBack == modernTurnsRound; // what this version does differs from what is wanted
        boolean changeHead = legacyHead == modernLimit;
        if (!changeBack && !changeHead) {
            return Float.NaN;
        }
        float t = target;
        float side = (float) (dx * dx + dz * dz);
        if (side > 0.0025000002F && !attacking) {
            float walk = (float) Math.atan2(dz, dx) * (180.0F / (float) Math.PI) - 90.0F;
            float diff = Math.abs(wrap(yRot) - walk);
            boolean backwards = 95.0F < diff && diff < 265.0F;
            if (backwards) {
                // the target Minecraft gave is the walking direction (1.8 like) or turned round (modern)
                t = legacyBack ? walk : walk - 180.0F;
            }
        }
        float body = bodyRot + wrap(t - bodyRot) * 0.3F;
        float d = wrap(yRot - body);
        if (legacyHead) {
            if (d < -75.0F) {
                d = -75.0F;
            }
            if (d >= 75.0F) {
                d = 75.0F;
            }
            body = yRot - d;
            if (d * d > 2500.0F) {
                body += d * 0.2F;
            }
            return body;
        }
        if (Math.abs(d) > maxModern) {
            body += d - Math.signum(d) * maxModern;
        }
        return body;
    }

    /** Minecraft's {@code Mth.wrapDegrees(float)}: into [-180, 180). */
    static float wrap(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }
        if (f < -180.0F) {
            f += 360.0F;
        }
        return f;
    }
}
