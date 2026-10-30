package dev.lumora.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingAnimationTest {
    private static Matrix4f transform(SwingAnimation.Mode mode, float swing, HumanoidArm arm) {
        PoseStack pose = new PoseStack();
        SwingAnimation.apply(mode, pose, swing, arm);
        return new Matrix4f(pose.last().pose());
    }

    @Test
    void everyModeProducesAFiniteNonIdentityTransform() {
        for (SwingAnimation.Mode mode : SwingAnimation.Mode.values()) {
            Matrix4f m = transform(mode, 0.5f, HumanoidArm.RIGHT);
            assertTrue(m.isFinite(), mode + " must be finite");
            if (mode == SwingAnimation.Mode.VANILLA) {
                assertTrue(m.equals(new Matrix4f(), 1e-6f), "vanilla leaves the pose untouched");
            } else {
                assertFalse(m.equals(new Matrix4f(), 1e-4f), mode + " must transform the item");
            }
        }
    }

    @Test
    void leftArmMirrorsTheRightArm() {
        Matrix4f right = transform(SwingAnimation.Mode.SMOOTH, 0.4f, HumanoidArm.RIGHT);
        Matrix4f left = transform(SwingAnimation.Mode.SMOOTH, 0.4f, HumanoidArm.LEFT);
        assertFalse(right.equals(left, 1e-4f));
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
