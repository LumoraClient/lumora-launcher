package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtrasTest {
    @TempDir
    Path dir;
    LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    private Map<String, AddonState> states() {
        return core.addons().stream().collect(Collectors.toMap(AddonInfo::id, AddonInfo::state));
    }

    @Test
    void modulesLoadAndThreeStartSwitchedOff() {
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        Map<String, AddonState> s = states();
        assertEquals(AddonState.ENABLED, s.get("lumora-info"));
        assertEquals(AddonState.ENABLED, s.get("lumora-chat"));
        assertEquals(AddonState.ENABLED, s.get("lumora-tab"));
        for (String off : Extras.OFF_BY_DEFAULT) {
            assertTrue(s.get(off) != AddonState.ENABLED, off + " starts off");
        }
        assertTrue(core.widgetRegistry().entry("lumora-info:potions").isPresent());
        assertTrue(core.widgetRegistry().entry("lumora-info:compass").isPresent());
        assertTrue(ChatModule.active() != null && TabModule.active() != null);
        assertEquals(null, CrosshairModule.active());

        // the player switches the crosshair on: it stays on after a restart
        assertTrue(core.addonManager().setWanted("lumora-crosshair", true).isEmpty());
        assertTrue(CrosshairModule.active() != null);
        core.stopping();
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        assertEquals(AddonState.ENABLED, states().get("lumora-crosshair"), "the player's choice wins over the default");
        assertTrue(states().get("lumora-fullbright") != AddonState.ENABLED);
    }

    @Test
    void crosshairShapes() {
        List<int[]> gap = CrosshairModule.shape("gap", 5, 2, 1, false);
        assertEquals(4, gap.size());
        // the centre pixel stays free with a gap, and the four arms are symmetric
        for (int[] r : gap) {
            assertFalse(r[0] <= 0 && r[2] > 0 && r[1] <= 0 && r[3] > 0, "centre must be open");
        }
        assertEquals(List.of(-7, 0, -2, 1), List.of(gap.get(0)[0], gap.get(0)[1], gap.get(0)[2], gap.get(0)[3]));
        assertEquals(List.of(3, 0, 8, 1), List.of(gap.get(1)[0], gap.get(1)[1], gap.get(1)[2], gap.get(1)[3]));
        assertEquals(5, CrosshairModule.shape("gap", 5, 2, 1, true).size(), "centre dot");
        int[] thick = CrosshairModule.shape("cross", 4, 0, 3, false).getFirst();
        assertEquals(3, thick[3] - thick[1], "thickness 3");
        assertEquals(1, CrosshairModule.shape("dot", 1, 0, 1, false).size());
        Set<String> pts = new HashSet<>();
        for (int[] r : CrosshairModule.shape("circle", 5, 0, 1, false)) {
            pts.add(r[0] + "," + r[1]);
        }
        assertTrue(pts.contains("5,0") && pts.contains("0,5") && pts.contains("-5,0") && pts.contains("0,-5"));
        assertFalse(pts.contains("0,0"));
    }

    @Test
    void chatStacksAndStamps() {
        ChatModule.Stacker s = new ChatModule.Stacker();
        assertEquals(1, s.add("Hallo"));
        assertEquals(2, s.add("Hallo"));
        assertEquals(3, s.add(" Hallo "));
        assertEquals(1, s.add("Tschüss"));
        assertEquals(1, s.add(""));
        assertEquals(1, s.add(""), "blank lines never stack");
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        ChatModule chat = ChatModule.active();
        ChatModule fixed = new ChatModule(() -> LocalTime.of(9, 5, 7));
        fixed.format = chat.format;
        assertEquals("[09:05]", fixed.timestamp());
        chat.format.set("24h_seconds");
        assertEquals("[09:05:07]", fixed.timestamp());
        chat.format.set("12h");
        assertEquals("[9:05 AM]", fixed.timestamp());
        ChatModule.Line first = chat.onMessage("GG");
        ChatModule.Line second = chat.onMessage("GG");
        assertFalse(first.replacePrevious());
        assertTrue(second.replacePrevious());
        assertEquals(2, second.count());
    }

    @Test
    void comboCounter() {
        InfoWidgetsModule.ComboTracker c = new InfoWidgetsModule.ComboTracker();
        c.tick(7, 0, 0);
        c.tick(7, 10, 0); // hit
        c.tick(7, 9, 0);
        c.tick(7, 0, 0);
        c.tick(7, 10, 0); // second hit
        assertEquals(2, c.combo());
        c.tick(7, 9, 10); // hit by the opponent
        assertEquals(0, c.combo());
        c.tick(7, 10, 9);
        assertEquals(1, c.combo());
        for (int i = 0; i < InfoWidgetsModule.ComboTracker.TIMEOUT_TICKS + 1; i++) {
            c.tick(-1, -1, 0);
        }
        assertEquals(0, c.combo(), "three seconds without a hit");
    }

    @Test
    void blockOverlayFadesInAndHidesInThirdPerson() {
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        assertTrue(core.addonManager().setWanted("lumora-blockoverlay", true).isEmpty());
        BlockOverlayModule live = BlockOverlayModule.active();
        assertTrue(live != null);
        long[] now = {0};
        BlockOverlayModule m = new BlockOverlayModule(new FakePlatform(dir), () -> now[0]);
        m.color = live.color;
        m.rainbow = live.rainbow;
        m.rainbowSpeed = live.rainbowSpeed;
        m.fade = live.fade;
        m.fadeMillis = live.fadeMillis;
        m.firstPersonOnly = live.firstPersonOnly;
        m.thickness = live.thickness;
        m.color.set(0xFF102030);
        assertEquals(0, m.outlineColor(true) >>> 24, "new target: starts transparent");
        now[0] = 90_000_000L; // half of 180 ms
        int half = m.outlineColor(true) >>> 24;
        assertTrue(half > 100 && half < 255);
        now[0] = 500_000_000L;
        assertEquals(0xFF102030, m.outlineColor(true), "fully faded in, colour unchanged");
        m.firstPersonOnly.set(true);
        assertEquals(0, m.outlineColor(false));
        m.rainbow.set(true);
        assertTrue((m.outlineColor(true) & 0xFFFFFF) != 0x102030);
        assertEquals(0xFFFF0000, BlockOverlayModule.hsbToRgb(0f, 1f, 1f));
        assertEquals(0xFF00FF00, BlockOverlayModule.hsbToRgb(1f / 3, 1f, 1f));

        // box, slide and side (FDPClient / BThack behaviour)
        m.mode = live.mode;
        m.fillColor = live.fillColor;
        m.sideOnly = live.sideOnly;
        m.slide = live.slide;
        m.slideEasing = live.slideEasing;
        m.slideMillis = live.slideMillis;
        m.rainbow.set(false);
        m.firstPersonOnly.set(false);
        m.fillColor.set(0x80112233);
        now[0] = 1_000_000_000L;
        BlockOverlayModule.Frame f = m.frame(0, 0, 0, 1, 1, 1, 1, true);
        assertEquals(-BlockOverlayModule.GROW, f.minX(), 1e-9, "grown a little around the block");
        assertEquals(1 + BlockOverlayModule.GROW, f.maxY(), 1e-9);
        assertEquals(0, f.fill() >>> 24, "fades in on the first block");
        now[0] += 100_000_000L;
        f = m.frame(1, 0, 0, 2, 0.5, 1, 1, true); // next block (a slab): slides from the first
        assertTrue(f.minX() < 0.01, "slide starts where the box was");
        now[0] += 75_000_000L; // half of 150 ms, quad in-out = 0.5
        f = m.frame(1, 0, 0, 2, 0.5, 1, 1, true);
        assertEquals(0.5, f.minX() + BlockOverlayModule.GROW, 0.02);
        for (int i = 0; i < 5; i++) { // frames every 50 ms
            now[0] += 50_000_000L;
            f = m.frame(1, 0, 0, 2, 0.5, 1, 1, true);
        }
        assertEquals(1 - BlockOverlayModule.GROW, f.minX(), 1e-9, "arrived");
        assertEquals(0.5 + BlockOverlayModule.GROW, f.maxY(), 1e-9, "real shape of the slab");
        assertEquals(0x80112233, f.fill(), "fill colour after the fade");
        assertEquals(0xFF102030, f.outline());
        m.mode.set("outline");
        assertEquals(0, m.frame(1, 0, 0, 2, 0.5, 1, 1, true).fill());
        m.mode.set("fill");
        assertEquals(0, m.frame(1, 0, 0, 2, 0.5, 1, 1, true).outline());
        m.sideOnly.set(true);
        now[0] += 1_000_000_000L; // after a pause: no slide
        f = m.frame(1, 0, 0, 2, 0.5, 1, 1, true);
        assertEquals(f.minY(), f.maxY(), 1e-9, "only the top face");
        assertEquals(0.5 + BlockOverlayModule.GROW, f.minY(), 1e-9);
        assertEquals(0.5, BlockOverlayModule.ease("expo", 0.5f), 1e-9);
        assertEquals(0.25, BlockOverlayModule.ease("linear", 0.25f), 1e-9);
        m.firstPersonOnly.set(true);
        assertNull(m.frame(0, 0, 0, 1, 1, 1, 1, false));
    }

    @Test
    void togglesAndFormatting() {
        ToggleSprintModule.Toggle t = new ToggleSprintModule.Toggle();
        assertTrue(t.update(true));
        assertTrue(t.update(true), "holding the key does not flip again");
        assertTrue(t.update(false));
        assertFalse(t.update(true));
        assertEquals("II", InfoWidgetsModule.roman(2));
        assertEquals("3:05", InfoWidgetsModule.duration(185 * 20));
        assertEquals("∞", InfoWidgetsModule.duration(-1));
        assertEquals(TabModule.pingColor(30), dev.lumora.api.render.Colors.OK);
        assertEquals(TabModule.pingColor(500), dev.lumora.api.render.Colors.ERROR);
    }
}
