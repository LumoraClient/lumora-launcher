package dev.lumora.core.perf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionCullerTest {
    /** Opaque blocks of a fake world; every section is loaded. */
    static final class FakeWorld implements OcclusionCuller.World {
        final Set<Long> opaque = new HashSet<>();
        int reads;

        void set(int x, int y, int z, boolean on) {
            long k = ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF);
            if (on) {
                opaque.add(k);
            } else {
                opaque.remove(k);
            }
        }

        boolean get(int x, int y, int z) {
            return opaque.contains(((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF));
        }

        @Override
        public long[] section(int sx, int sy, int sz) {
            reads++;
            long[] bits = new long[64];
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (get(sx * 16 + x, sy * 16 + y, sz * 16 + z)) {
                            int i = y << 8 | z << 4 | x;
                            bits[i >>> 6] |= 1L << (i & 63);
                        }
                    }
                }
            }
            return bits;
        }
    }

    static final class Target implements Cullable {
        int state;

        @Override
        public int lumora$cull() {
            return state;
        }

        @Override
        public void lumora$cull(int state) {
            this.state = state;
        }
    }

    FakeWorld world;
    OcclusionCuller culler;
    final Target mob = new Target();
    static final double CX = 0.5;
    static final double CY = 65.6;
    static final double CZ = 0.5;

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        // wall at x = 10, high and wide
        for (int y = 50; y < 81; y++) {
            for (int z = -20; z <= 20; z++) {
                world.set(10, y, z, true);
            }
        }
        culler = new OcclusionCuller(world, msg -> {
        }, false);
        PerformanceEngine.declareSupported(EnumSet.of(Optimization.ENTITY_CULLING));
        PerformanceEngine.set(Optimization.ENTITY_CULLING, true);
    }

    @AfterEach
    void tearDown() {
        PerformanceEngine.allOff();
        PerformanceEngine.declareSupported(EnumSet.noneOf(Optimization.class));
    }

    /** One frame with the mob in it, the culling pass, and the answer the next frame gets. */
    boolean frameAndCheck(double camX, double camY, double camZ, double x0, double y0, double z0, double x1, double y1, double z1) {
        culler.frame(camX, camY, camZ);
        culler.hidden(mob, x0, y0, z0, x1, y1, z1);
        culler.frame(camX, camY, camZ);
        culler.processOnce();
        return culler.hidden(mob, x0, y0, z0, x1, y1, z1);
    }

    /** A zombie-sized box (vanilla culling box: hit box inflated by 0.5) behind the wall. */
    boolean mobBehindWall() {
        return frameAndCheck(CX, CY, CZ, 14.5, 63.5, -0.5, 16.1, 66.3, 1.1);
    }

    @Test
    void entityBehindAWallIsHidden() {
        assertTrue(mobBehindWall());
        assertEquals(1, culler.lastHidden());
    }

    @Test
    void neverCheckedEntitiesAreVisible() {
        culler.frame(CX, CY, CZ);
        assertFalse(culler.hidden(mob, 14.5, 63.5, -0.5, 16.1, 66.3, 1.1));
    }

    @Test
    void aOneBlockWindowMakesItVisible() {
        world.set(10, 66, 0, false); // a window above the line to the box centre: only the grid rays find it
        assertFalse(mobBehindWall());
    }

    @Test
    void invalidatedSectionsAreReadAgain() {
        assertTrue(mobBehindWall());
        for (int y = 50; y < 81; y++) {
            for (int z = -20; z <= 20; z++) {
                world.set(10, y, z, false);
            }
        }
        // without invalidation the cached wall still hides it (within the cache lifetime)
        assertTrue(mobBehindWall());
        for (int sy = 3; sy <= 5; sy++) {
            for (int sz = -2; sz <= 1; sz++) {
                culler.invalidate(0, sy, sz);
            }
        }
        assertFalse(mobBehindWall());
    }

    @Test
    void entitiesNearTheCameraAreNeverCulled() {
        // a wall block right between camera and a box 3 blocks away
        world.set(2, 65, 0, true);
        assertFalse(frameAndCheck(CX, CY, CZ, 3.0, 64.0, -0.5, 4.0, 66.0, 1.0));
        assertEquals(0, culler.lastChecked(), "near boxes are not even queued");
    }

    @Test
    void cameraInsideAnOpaqueBlockCullsNothing() {
        world.set(0, 65, 0, true);
        assertFalse(mobBehindWall());
    }

    @Test
    void staleResultsCountAsVisible() {
        assertTrue(mobBehindWall());
        // the mob leaves the view for a few passes (other entities are checked), then comes back
        Target other = new Target();
        for (int i = 0; i <= OcclusionCuller.STALE_PASSES; i++) {
            culler.frame(CX, CY, CZ);
            culler.hidden(other, 14.5, 63.5, 5.5, 16.1, 66.3, 7.1);
            culler.frame(CX, CY, CZ);
            culler.processOnce();
        }
        culler.frame(CX, CY, CZ);
        assertFalse(culler.hidden(mob, 14.5, 63.5, -0.5, 16.1, 66.3, 1.1));
    }

    @Test
    void switchingOffAndOnForgetsOldResults() {
        assertTrue(mobBehindWall());
        PerformanceEngine.set(Optimization.ENTITY_CULLING, false);
        PerformanceEngine.culler(null);
        PerformanceEngine.culler(culler);
        PerformanceEngine.set(Optimization.ENTITY_CULLING, true);
        // the engine tells the culler, old results are stale now
        culler.switched(true);
        culler.frame(CX, CY, CZ);
        assertFalse(culler.hidden(mob, 14.5, 63.5, -0.5, 16.1, 66.3, 1.1));
    }

    @Test
    void entityAboveTheWallIsVisible() {
        // high enough that the line from the camera passes over the wall (wall ends at y = 81)
        assertFalse(frameAndCheck(CX, CY, CZ, 14.5, 95.0, -0.5, 16.1, 97.8, 1.1));
    }

    @Test
    void sampleSpacingIsAtMostOneBlock() {
        assertEquals(2, OcclusionCuller.samples(0.5));
        assertEquals(3, OcclusionCuller.samples(1.6));
        assertEquals(4, OcclusionCuller.samples(2.8));
        assertEquals(8, OcclusionCuller.samples(30));
    }

    @Test
    void unsupportedCullingStaysOff() {
        PerformanceEngine.declareSupported(EnumSet.noneOf(Optimization.class));
        PerformanceEngine.set(Optimization.ENTITY_CULLING, true);
        assertFalse(PerformanceEngine.entityCulling);
    }
}
