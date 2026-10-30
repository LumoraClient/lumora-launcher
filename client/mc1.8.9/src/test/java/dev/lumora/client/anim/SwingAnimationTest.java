package dev.lumora.client.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Minecraft 1.8.9 applies the swing to the fixed-function GL matrix, which needs a GL context: only the mode handling is
 * tested here (the transforms are the same formulas as in the newer modules, where they are tested on a PoseStack).
 */
class SwingAnimationTest {
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
