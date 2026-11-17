package dev.lumora.core.extras.light;

import dev.lumora.api.config.SettingsGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightOverlayModuleTest {
    /**
     * A flat test world: three spawn positions in every chunk at y 64 (dark, sky-lit, bright); in light level mode
     * (every standable block) a fourth position, a bright block nothing spawns on.
     */
    final class FakeProbe implements LightProbe {
        Object world = new Object();
        int[] player = {8, 64, 8, 2};
        final List<long[]> scans = new ArrayList<>();
        int skyOfSecond = 15;

        @Override
        public Object world() {
            return world;
        }

        @Override
        public boolean player(int[] out) {
            if (player == null) {
                return false;
            }
            System.arraycopy(player, 0, out, 0, 4);
            return true;
        }

        @Override
        public boolean scan(int chunkX, int chunkZ, int minY, int maxY, boolean surfaces, Sink sink) {
            scans.add(new long[]{chunkX, chunkZ, minY, maxY, surfaces ? 1 : 0});
            sink.mark(1, 64, 2, 0, 0);
            sink.mark(3, 64, 3, 0, skyOfSecond);
            sink.mark(5, 64, 5, 12, 15);
            if (surfaces) {
                sink.mark(7, 64, 7, 15, 4);
            }
            return true;
        }
    }

    FakeProbe probe;
    long now;
    long step;
    LightOverlayModule module;

    @BeforeEach
    void setUp() {
        probe = new FakeProbe();
        module = new LightOverlayModule(() -> probe, "26.3", () -> now += step);
        module.settings(new SettingsGroup("test."));
        module.horizontal.set(16); // one chunk around the player: 3 x 3 chunks
        module.mode.decode("spawn"); // the spawn tests; light level mode below
        module.enable();
    }

    @AfterEach
    void tearDown() {
        module.disable();
    }

    int lines() {
        int n = 0;
        for (LightMesh m : module.meshes()) {
            n += m.lineCount;
        }
        return n;
    }

    int quads() {
        int n = 0;
        for (LightMesh m : module.meshes()) {
            n += m.quadCount;
        }
        return n;
    }

    @Test
    void redWhereMobsSpawnNowYellowAtNightNothingWhereBright() {
        assertEquals(LightOverlayModule.NOW, LightOverlayModule.kind(0, 0, 0, false));
        assertEquals(LightOverlayModule.NOW, LightOverlayModule.kind(0, 7, 0, false), "sky light up to 7: also by day");
        assertEquals(LightOverlayModule.NIGHT, LightOverlayModule.kind(0, 8, 0, false));
        assertEquals(-1, LightOverlayModule.kind(1, 0, 0, false), "since 1.18 any block light keeps mobs away");
        assertEquals(LightOverlayModule.NOW, LightOverlayModule.kind(7, 0, 7, false), "before 1.18: up to 7");
        assertEquals(LightOverlayModule.SAFE, LightOverlayModule.kind(12, 15, 0, true), "numbers may show safe blocks");
        assertEquals(0, module.lightLevel.get(), "default 0 on 1.18 and newer");
        LightOverlayModule old = new LightOverlayModule(() -> probe, "1.16.5");
        old.settings(new SettingsGroup("old."));
        assertEquals(7, old.lightLevel.get(), "default 7 before 1.18");
    }

    @Test
    void crossesForEverySpawnPositionNearestChunksFirst() {
        module.tick();
        assertEquals(9, probe.scans.size(), "3 x 3 chunks in one tick (the fake world is fast)");
        assertEquals(0, probe.scans.get(0)[0], "the own chunk first");
        assertEquals(0, probe.scans.get(0)[1]);
        assertEquals(64 - 16 - LightOverlayModule.Y_MARGIN, probe.scans.get(0)[2], "vertical reach plus margin");
        assertEquals(9 * 2 * 2, lines(), "two spawn positions per chunk, two lines per cross");
        assertSame(module, LightOverlayModule.active());

        module.tick();
        assertEquals(9, probe.scans.size(), "nothing is read again right away");
        for (int i = 0; i < LightOverlayModule.interval(1); i++) {
            module.tick();
        }
        assertTrue(probe.scans.size() > 9, "nearby chunks are read again after half a second");
    }

    @Test
    void theTimeBudgetSpreadsTheWorkOverTicks() {
        step = 2_000_000L; // every clock read is two milliseconds later
        module.tick();
        assertEquals(1, probe.scans.size(), "after the budget the rest waits for the next tick");
        module.tick();
        assertEquals(2, probe.scans.size());
        assertTrue(module.stats().contains("chunks=2"), module.stats());
    }

    @Test
    void stylesBuildTheirOwnGeometry() {
        module.style.decode("tile");
        module.tick();
        assertEquals(9 * 2, quads(), "one translucent tile per spawn position");
        assertEquals(9 * 2 * 4, lines(), "and its outline");

        module.style.decode("marker");
        module.tick();
        assertEquals(9 * 2 * 5, quads(), "pillar: four sides and the top");
        assertEquals(0, lines());

        module.style.decode("number");
        module.tick();
        // light 0 = seven-segment "0" (6 segments) on the two dark blocks, "12" (2 + 5 segments) on the bright one;
        // numbers only within 16 blocks of the player (8, 8): the marks of the chunks at -16 are farther away
        assertEquals(4 * (6 + 6 + 7), lines(), "numbers also on the safe block, only nearby");
        module.safeNumbers.set(false);
        module.tick();
        assertEquals(4 * (6 + 6), lines());
    }

    @Test
    void gradientBlendsByTheSkyLight() {
        module.colorNow.set(0xFF0000);
        module.colorNight.set(0x00FF00);
        assertEquals(0xFFFF0000, module.color(LightOverlayModule.NOW, 0));
        assertEquals(0xFF00FF00, module.color(LightOverlayModule.NIGHT, 15));
        module.gradient.set(true);
        int mid = module.color(LightOverlayModule.NIGHT, 8);
        assertTrue(((mid >> 16) & 0xFF) > 0 && ((mid >> 8) & 0xFF) > 0, Integer.toHexString(mid));
    }

    @Test
    void positionsOutsideTheVerticalReachAreLeftOut() {
        module.tick();
        int all = lines();
        probe.player = new int[]{8, 100, 8, 2}; // 36 blocks above the marks, reach 16
        module.tick();
        assertEquals(0, lines(), "too far below: not drawn");
        assertTrue(all > 0);
    }

    @Test
    void hiddenOrWithoutWorldNothingIsReadOrDrawn() {
        module.toggleShown();
        assertNull(LightOverlayModule.active());
        module.tick();
        assertEquals(0, probe.scans.size(), "hidden: the world is not read");
        module.toggleShown();
        module.tick();
        assertEquals(9, probe.scans.size());

        probe.world = new Object(); // other world or dimension
        module.tick();
        assertEquals(18, probe.scans.size(), "everything is read again");

        probe.player = null;
        module.tick();
        assertEquals(0, module.meshes().length, "no player: nothing to draw");
    }

    @Test
    void numbersStandUprightForThePlayer() {
        LightMesh north = new LightMesh(0, 0);
        LightOverlayModule.number(north, 0, 64f, 0, 1, 0xFFFFFFFF, 2);
        // "1" = segments b and c: a line along the view direction (north = -z) at the right of the digit
        assertEquals(2, north.lineCount);
        float dz = north.lines[5] - north.lines[2];
        float dx = north.lines[3] - north.lines[0];
        assertEquals(0f, dx, 1e-5f, "upright: the stroke of the 1 runs along the view direction");
        assertTrue(Math.abs(dz) > 0.1f);
    }

    @Test
    void lightLevelIsTheDefaultAndShowsEveryStandableBlock() {
        LightOverlayModule fresh = new LightOverlayModule(() -> probe, "26.3");
        fresh.settings(new SettingsGroup("fresh."));
        assertEquals("light", fresh.mode.get(), "light levels by default, spawn positions as the second mode");
        assertEquals("number", fresh.lightStyle.get());
        assertEquals("block", fresh.lightType.get());

        module.mode.decode("light");
        module.lightStyle.decode("tile");
        module.tick();
        assertEquals(1, probe.scans.get(0)[4], "light level mode reads every standable block");
        assertEquals(9 * 4, quads(), "a tile on every block, also the bright ones nothing spawns on");

        module.mode.decode("spawn");
        module.tick();
        assertEquals(0, probe.scans.get(probe.scans.size() - 1)[4], "switching the mode reads the world again");
        assertEquals(9 * 2 * 2, lines(), "spawn mode: crosses on the two spawn positions as before");
    }

    @Test
    void lightLevelNumbersAndColoursGoFromDarkToBright() {
        assertEquals(12, LightOverlayModule.lightValue("block", 12, 3));
        assertEquals(3, LightOverlayModule.lightValue("sky", 12, 3));
        assertEquals(12, LightOverlayModule.lightValue("combined", 12, 3));
        assertEquals(15, LightOverlayModule.lightValue("combined", 2, 15));

        module.colorDark.set(0xFF0000);
        module.colorMedium.set(0xFFFF00);
        module.colorBright.set(0x00FF00);
        assertEquals(0xFFFF0000, module.lightColor(0));
        assertEquals(0xFFFFFF00, module.lightColor(7));
        assertEquals(0xFF00FF00, module.lightColor(15));

        module.mode.decode("light");
        module.lightStyle.decode("number");
        module.tick();
        // block light: "0", "0", "12", "15" on the four blocks of the 4 chunks within 16 blocks of the player
        assertEquals(4 * (6 + 6 + 7 + 7), lines());
        module.lightType.decode("sky");
        module.tick();
        // sky light: "0", "15", "15", "4"
        assertEquals(4 * (6 + 7 + 7 + 4), lines());
    }

    @Test
    void spiralStartsInTheMiddle() {
        int[] o = LightOverlayModule.spiral(2);
        assertEquals(25 * 2, o.length);
        assertEquals(0, o[0]);
        assertEquals(0, o[1]);
        int last = o[o.length - 2] * o[o.length - 2] + o[o.length - 1] * o[o.length - 1];
        assertEquals(8, last, "the corners last");
    }
}
