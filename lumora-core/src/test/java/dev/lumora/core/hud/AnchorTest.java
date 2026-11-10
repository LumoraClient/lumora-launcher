package dev.lumora.core.hud;

import dev.lumora.core.CoreLogger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnchorTest {
    @Test
    void zonesAreTheThirdsOfTheScreen() {
        assertEquals("top_left", Anchor.zone(10, 10, 300, 300));
        assertEquals("top_center", Anchor.zone(150, 10, 300, 300));
        assertEquals("center_right", Anchor.zone(250, 150, 300, 300));
        assertEquals("center", Anchor.zone(150, 150, 300, 300));
        assertEquals("bottom_right", Anchor.zone(299, 299, 300, 300));
        assertEquals(9, Anchor.ZONES.size());
        assertArrayEquals(new int[] {300, 300}, Anchor.point("bottom_right", 300, 300));
        assertArrayEquals(new int[] {150, 150}, Anchor.point("center", 300, 300));
        assertArrayEquals(new int[] {0, 150}, Anchor.point("center_left", 300, 300));
    }

    @Test
    void offsetsAndPlaceAreInverseForEveryAnchor() {
        for (String a : Anchor.ZONES) {
            int[] o = Anchor.offsets(a, 37, 52, 40, 10, 400, 300);
            assertArrayEquals(new int[] {37, 52}, Anchor.place(a, o[0], o[1], 40, 10, 400, 300), a);
        }
    }

    @Test
    void anAnchoredWidgetKeepsItsDistanceWhenTheScreenGrows() {
        int[] o = Anchor.offsets("bottom_right", 340, 270, 40, 10, 400, 300);
        assertArrayEquals(new int[] {20, 20}, o, "20 px from the right and bottom edge");
        assertArrayEquals(new int[] {740, 570}, Anchor.place("bottom_right", o[0], o[1], 40, 10, 800, 600));
        int[] c = Anchor.offsets("center", 180, 145, 40, 10, 400, 300);
        assertArrayEquals(new int[] {380, 295}, Anchor.place("center", c[0], c[1], 40, 10, 800, 600), "still centered");
        assertArrayEquals(new int[] {0, 0}, Anchor.place("top_left", -50, -50, 40, 10, 800, 600), "never off screen");
    }

    @Test
    void theAnchorIsStoredAndOldLayoutsStayFractions() {
        HudLayout l = new HudLayout();
        WidgetState s = new WidgetState();
        s.anchor = "bottom_center";
        s.offsetX = -12;
        s.offsetY = 30;
        l.active().widgets.put("demo:a", s);
        l.active().widgets.put("demo:b", new WidgetState());
        CoreLogger log = new CoreLogger() {
            @Override public void info(String message) { }
            @Override public void warn(String message) { }
            @Override public void warn(String message, Throwable t) { }
            @Override public void error(String message, Throwable t) { }
        };
        HudLayout back = LayoutStore.parse(LayoutStore.toJson(l), log);
        WidgetState a = back.active().widgets.get("demo:a");
        assertEquals("bottom_center", a.anchor);
        assertEquals(List.of(-12, 30), List.of(a.offsetX, a.offsetY));
        assertNull(back.active().widgets.get("demo:b").anchor);
        assertNull(LayoutStore.parse("{\"schema\":2,\"profiles\":{\"default\":{\"widgets\":{\"demo:a\":{\"anchor\":\"nowhere\"}}}}}", log)
                .active().widgets.get("demo:a").anchor, "unknown anchors are ignored");
    }
}
