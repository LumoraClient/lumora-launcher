package dev.lumora.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 1.18.2 has no JOML: the pose matrix is com.mojang.math.Matrix4f, compared here as its 16 values. */
class SwingAnimationTest {
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private static float[] transform(SwingAnimation.Mode mode, float swing, HumanoidArm arm) {
        PoseStack pose = new PoseStack();
        SwingAnimation.apply(mode, pose, swing, arm);
        FloatBuffer buffer = FloatBuffer.allocate(16);
        pose.last().pose().store(buffer);
        return buffer.array();
    }

    private static boolean same(float[] a, float[] b, float epsilon) {
        for (int i = 0; i < 16; i++) {
            if (Math.abs(a[i] - b[i]) > epsilon) {
                return false;
            }
        }
        return true;
    }

    private static boolean finite(float[] m) {
        for (float v : m) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void everyModeProducesAFiniteNonIdentityTransform() {
        for (SwingAnimation.Mode mode : SwingAnimation.Mode.values()) {
            float[] m = transform(mode, 0.5f, HumanoidArm.RIGHT);
            assertTrue(finite(m), mode + " must be finite");
            if (mode == SwingAnimation.Mode.VANILLA) {
                assertTrue(same(m, IDENTITY, 1e-6f), "vanilla leaves the pose untouched");
            } else {
                assertFalse(same(m, IDENTITY, 1e-4f), mode + " must transform the item");
            }
        }
    }

    @Test
    void leftArmMirrorsTheRightArm() {
        float[] right = transform(SwingAnimation.Mode.SMOOTH, 0.4f, HumanoidArm.RIGHT);
        float[] left = transform(SwingAnimation.Mode.SMOOTH, 0.4f, HumanoidArm.LEFT);
        assertFalse(same(right, left, 1e-4f));
    }

    @Test
    void parsingAndCycling() {
        assertEquals(SwingAnimation.Mode.SMOOTH, SwingAnimation.Mode.parse("smooth"));
        assertEquals(SwingAnimation.Mode.SLAP, SwingAnimation.Mode.parse("SLAP"));
        // removed (e.g. "feast") or unknown modes fall back to the default
        assertEquals(SwingAnimation.Mode.SMOOTH, SwingAnimation.Mode.parse("feast"));
        assertEquals(SwingAnimation.Mode.SMOOTH, SwingAnimation.Mode.parse(null));
        assertEquals(4, SwingAnimation.Mode.values().length);
        assertEquals(SwingAnimation.Mode.VANILLA, SwingAnimation.Mode.SLAP.next());
        assertEquals("Smooth", SwingAnimation.Mode.SMOOTH.displayName());
    }
}
