/*
 * Swing animation formulas ported from LiquidBounce
 * (src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/animations/SwingAnimations.kt,
 * https://github.com/CCBlueX/LiquidBounce, branch nextgen, retrieved 2026-10-18).
 * Copyright (c) 2015 - 2026 CCBlueX. Licensed under the GNU General Public License v3.0 or later,
 * the same license as the Lumora client. Changes: translated from Kotlin to Java, adapted to the
 * Minecraft 26.1.2 PoseStack API (mulPose), added the VANILLA mode, kept only the Smooth, Spin and Slap modes.
 *
 * Purely cosmetic: only the first-person item transform of the local player changes. Nothing is sent to the
 * server and hit timing/reach are untouched.
 */
package dev.lumora.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Locale;

public final class SwingAnimation {
    public enum Mode {
        VANILLA, SMOOTH, SPIN, SLAP;

        public String displayName() {
            String n = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static Mode parse(String s) {
            // unknown or removed modes fall back to the default
            try {
                return s == null ? SMOOTH : Mode.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return SMOOTH;
            }
        }
    }

    private static final float PI = (float) Math.PI;

    private SwingAnimation() {
    }

    private static float sin(float v) {
        return Mth.sin(v);
    }

    /** Applies the swing transform of {@code mode} (not VANILLA) for the given swing progress 0..1. */
    public static void apply(Mode mode, PoseStack pose, float swing, HumanoidArm arm) {
        float i = arm == HumanoidArm.RIGHT ? 1f : -1f;
        float g = sin(Mth.sqrt(swing) * PI);
        float sin1 = sin(swing * swing * PI);

        switch (mode) {
            case VANILLA -> {
            }
            case SMOOTH -> {
                pose.mulPose(Vector3f.YP.rotationDegrees(i * (45.0f + sin1 * -20.0f)));
                pose.mulPose(Vector3f.ZP.rotationDegrees(i * g * -20.0f));
                pose.mulPose(Vector3f.XP.rotationDegrees(g * -80.0f));
                pose.mulPose(Vector3f.YP.rotationDegrees(i * -45.0f));
                pose.translate(0.0, -0.1, 0.0);
            }
            case SPIN -> pose.mulPose(Vector3f.XP.rotationDegrees(swing * -360f));
            case SLAP -> {
                pose.mulPose(Vector3f.XP.rotationDegrees(-sin(swing * 3f) * 60f));
                pose.mulPose(Vector3f.ZP.rotationDegrees(i * -60f * g));
            }
        }
    }
}
