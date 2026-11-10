package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.addon.VersionSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraTrailsModuleTest {
    private static ElytraTrailsModule module() {
        ElytraTrailsModule m = new ElytraTrailsModule();
        m.configure(new SettingsGroup("lumora-elytratrails.setting."));
        return m;
    }

    /** Flies south (+z) at 1.5 blocks per tick for {@code frames} frames of 50 ms; returns the last mesh. */
    private static ElytraTrailsModule.Mesh fly(ElytraTrailsModule m, int frames, boolean gliding) {
        ElytraTrailsModule.Mesh mesh = ElytraTrailsModule.Mesh.EMPTY;
        for (int i = 0; i < frames; i++) {
            double z = i * 1.5;
            mesh = m.mesh(7, false, gliding, 0, 100, z, 0, 10, 0, -0.1, 1.5, 5, 103, z - 6, 1000 + i * 50L);
        }
        return mesh;
    }

    @Test
    void theWingTipsSitLeftAndRightOfTheGlidingBody() {
        ElytraTrailsModule.Basis b = ElytraTrailsModule.basis(0, 0);
        assertEquals(1, b.fz(), 1e-9, "yaw 0 looks south");
        double[] left = ElytraTrailsModule.tip(b, 0, 0, 0, -1);
        double[] right = ElytraTrailsModule.tip(b, 0, 0, 0, 1);
        assertEquals(0.8, left[0], 1e-9, "facing south the left wing is east (+x)");
        assertEquals(-0.8, right[0], 1e-9);
        assertEquals(0.45, left[2], 1e-9, "along the body, which lies forward while gliding");
        // looking straight down: still a sideways axis
        ElytraTrailsModule.Basis down = ElytraTrailsModule.basis(90, 90);
        assertEquals(1, Math.abs(down.rx()) + Math.abs(down.rz()), 1e-6);
    }

    @Test
    void aGlidingPlayerLeavesTwoTrailsBehindTheWings() {
        ElytraTrailsModule m = module();
        ElytraTrailsModule.Mesh mesh = fly(m, 30, true);
        assertFalse(mesh.isEmpty());
        assertEquals(0, mesh.vertices() % 8, "quads with a back side");
        // relative to the player: the trails run behind (negative z) and to both sides
        double minZ = 0;
        boolean east = false;
        boolean west = false;
        for (int v = 0; v < mesh.vertices(); v++) {
            minZ = Math.min(minZ, mesh.xyz()[v * 3 + 2]);
            east |= mesh.xyz()[v * 3] > 0.5;
            west |= mesh.xyz()[v * 3] < -0.5;
        }
        assertTrue(minZ < -20, "trail reaches back, was " + minZ);
        assertTrue(east && west, "one trail per wing");
        assertTrue((mesh.colors()[0] >>> 24) < (mesh.colors()[mesh.vertices() - 8] >>> 24) || (mesh.colors()[0] >>> 24) <= 0xC8,
                "older parts are fainter");
    }

    @Test
    void trailsFadeAfterTheChosenLengthAndNeedSpeed() {
        ElytraTrailsModule m = module();
        m.minSpeed.set(40);
        assertTrue(fly(m, 10, true).isEmpty(), "30 blocks per second is below 40");
        assertEquals(0, m.activeTrails());
        m.minSpeed.set(12);
        fly(m, 10, true);
        m.length.set(0.5);
        ElytraTrailsModule.Mesh later = m.mesh(7, false, false, 0, 100, 20, 0, 10, 0, 0, 0, 5, 103, 10, 1000 + 9 * 50L + 600);
        assertTrue(later.isEmpty(), "landed: the trail is gone after 0.5 s");
        assertTrue(module().mesh(1, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).isEmpty(), "not gliding: nothing");
    }

    @Test
    void ownAndOtherPlayersCanBeSwitchedSeparately() {
        ElytraTrailsModule m = module();
        m.own.set(false);
        for (int i = 0; i < 10; i++) {
            assertTrue(m.mesh(1, true, true, 0, 100, i * 1.5, 0, 0, 0, 0, 1.5, 5, 100, 0, i * 50L).isEmpty());
        }
        assertFalse(fly(m, 10, true).isEmpty(), "others still have trails");
    }

    @Test
    void vortexTrailsSpiralAroundTheVaporLine() {
        ElytraTrailsModule vapor = module();
        ElytraTrailsModule vortex = module();
        vortex.style.set("vortex");
        ElytraTrailsModule.Mesh a = fly(vapor, 30, true);
        ElytraTrailsModule.Mesh b = fly(vortex, 30, true);
        double spreadA = 0;
        double spreadB = 0;
        for (int v = 0; v < a.vertices(); v++) {
            spreadA = Math.max(spreadA, Math.abs(a.xyz()[v * 3 + 1]));
        }
        for (int v = 0; v < b.vertices(); v++) {
            spreadB = Math.max(spreadB, Math.abs(b.xyz()[v * 3 + 1]));
        }
        assertTrue(spreadB > spreadA + 0.1, "the vortex leaves the straight line: " + spreadA + " / " + spreadB);
    }

    @Test
    void beforeMinecraft19TheModuleIsLockedAsTooOld() {
        assertEquals(List.of("1.9.x"), VersionSupport.atLeast(ElytraTrailsModule.MIN_MINECRAFT, "1.8.9"));
        assertEquals(List.of("*"), VersionSupport.atLeast(ElytraTrailsModule.MIN_MINECRAFT, "1.12.2"));
        assertEquals(List.of("*"), VersionSupport.atLeast(ElytraTrailsModule.MIN_MINECRAFT, "26.3"));
        var d = LumoraCore.builtInDescriptor("lumora-elytratrails", "Elytra-Streifen", "", ElytraTrailsModule.class,
                VersionSupport.atLeast(ElytraTrailsModule.MIN_MINECRAFT, "1.8.9"));
        VersionSupport.Problem p = VersionSupport.check(d, "1.8.9");
        assertEquals(VersionSupport.Kind.TOO_OLD, p.kind());
        assertEquals(List.of("Elytra-Streifen ist mit Minecraft 1.8.9 nicht kompatibel.",
                "Diese Version ist zu alt: nötig ist mindestens 1.9."), p.describe(true, "Elytra-Streifen"));
    }
}
