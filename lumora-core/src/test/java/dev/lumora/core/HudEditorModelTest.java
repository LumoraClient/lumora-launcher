package dev.lumora.core;

import dev.lumora.api.event.hud.HudEditorClosedEvent;
import dev.lumora.api.event.hud.WidgetMovedEvent;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.core.hud.HudEditorModel;
import dev.lumora.core.hud.WidgetEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudEditorModelTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    LumoraCore core;
    TestContexts.Box a;
    TestContexts.Box b;
    HudEditorModel editor;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        core = new LumoraCore(platform);
        a = new TestContexts.Box("a");
        a.placement = WidgetPlacement.at(0f, 0f).enabledByDefault();
        b = new TestContexts.Box("b");
        b.placement = WidgetPlacement.at(0.5f, 0.5f).enabledByDefault();
        core.widgetRegistry().register("demo", a);
        core.widgetRegistry().register("demo", b);
        editor = core.openEditor();
        layout();
    }

    @AfterEach
    void tearDown() {
        core.stopping();
    }

    void layout() {
        editor.layout(TestContexts.ctx(platform, 400, 300, true));
    }

    WidgetEntry entry(String id) {
        return core.widgetRegistry().entry("demo:" + id).orElseThrow();
    }

    @Test
    void clickSelectsWithoutMoving() {
        assertTrue(editor.press(10, 5, 0, false));
        assertSame(entry("a"), editor.selected());
        editor.drag(11, 5, false); // below the drag threshold
        editor.release();
        assertEquals(0f, entry("a").x());
        assertTrue(entry("a").layer() > entry("b").layer(), "a click brings it to the front, it does not move");
        assertFalse(editor.press(390, 290, 0, false), "empty space");
        assertNull(editor.selected());
    }

    @Test
    void dragMovesSnapsAndIsOneUndoStep() {
        List<Object> moved = new ArrayList<>();
        core.events().subscribe(WidgetMovedEvent.class, moved::add);
        editor.press(10, 5, 0, false);
        editor.drag(60, 40, false);
        editor.drag(100, 80, false);
        editor.release();
        WidgetEntry e = entry("a");
        assertEquals(90, e.bounds().x);
        assertEquals(90f / 360f, e.x(), 1e-4);
        assertEquals(1, moved.size());
        assertTrue(editor.canUndo());

        // snap to the vertical center line: widget center 200 +- 4
        editor.press(100, 80, 0, false);
        editor.drag(100 + (200 - 20 - 90) + 3, 80, false);
        assertEquals(180, e.bounds().x, "snapped so that the center is on 200");
        assertEquals(List.of(200), editor.guidesX());
        editor.release();
        assertTrue(editor.guidesX().isEmpty());

        // shift = free placement without snapping
        editor.press(190, 80, 0, false);
        editor.drag(193, 80, true);
        assertEquals(183, e.bounds().x);
        editor.release();

        editor.undo();
        assertEquals(180, Math.round(entry("a").x() * 360));
        editor.undo();
        editor.undo();
        assertEquals(0f, entry("a").x());
        assertFalse(editor.canUndo());
        editor.redo();
        assertEquals(90f / 360f, entry("a").x(), 1e-4);
    }

    @Test
    void aDroppedWidgetHangsOnTheZoneUnderTheCursor() {
        editor.press(10, 5, 0, false);
        assertNull(editor.dragZone());
        editor.drag(350, 250, false);
        assertEquals("bottom_right", editor.dragZone(), "shown while moving");
        editor.release();
        assertNull(editor.dragZone());
        WidgetEntry e = entry("a");
        assertEquals("bottom_right", e.state().anchor);
        assertEquals(List.of(340, 245), List.of(e.bounds().x, e.bounds().y));
        assertEquals(List.of(20, 45), List.of(e.state().offsetX, e.state().offsetY), "distance to the right and bottom edge");
        editor.layout(TestContexts.ctx(platform, 800, 600, true));
        assertEquals(List.of(740, 545), List.of(e.bounds().x, e.bounds().y), "the same distance on a bigger screen");
        layout();

        // into the middle: hangs on the center
        editor.press(345, 250, 0, false);
        editor.drag(205, 155, false);
        editor.release();
        assertEquals("center", e.state().anchor);
        int x = e.bounds().x;
        editor.layout(TestContexts.ctx(platform, 600, 300, true));
        assertEquals(x + 100, e.bounds().x, "stays centered");
        layout();
        editor.undo();
        editor.undo();
        assertNull(e.state().anchor, "undo restores the old placement");
    }

    @Test
    void snappingHighlightsTheWidgetItSnappedTo() {
        editor.press(10, 5, 0, false);
        editor.drag(148, 5, false);
        assertEquals(140, entry("a").bounds().x, "right edge on the left edge of b");
        assertEquals(List.of(entry("b")), editor.magnets());
        editor.release();
        assertTrue(editor.magnets().isEmpty());
    }

    @Test
    void aClickBringsTheWidgetToTheFrontAsOneUndoStep() {
        editor.press(10, 5, 0, false);
        editor.release();
        assertTrue(entry("a").layer() > entry("b").layer());
        assertTrue(editor.canUndo());
        editor.undo();
        assertEquals(0, entry("a").layer());
        editor.press(10, 5, 0, false);
        editor.release();
        int layer = entry("a").layer();
        editor.press(10, 5, 0, false);
        editor.release();
        assertEquals(layer, entry("a").layer(), "already in front: unchanged");
    }

    @Test
    void gridAndEdgeSnapping() {
        editor.setGrid(true);
        editor.setSnap(false);
        editor.press(10, 5, 0, false);
        editor.drag(10 + 37, 5 + 21, false);
        editor.release();
        WidgetEntry e = entry("a");
        assertEquals(40, e.bounds().x, "rounded to the 8px grid");
        assertEquals(24, e.bounds().y);
        editor.setGrid(false);
        editor.setSnap(true);
        editor.press(50, 30, 0, false);
        editor.drag(50 + 400, 30, false);
        editor.release();
        assertEquals(1f, e.x(), "always kept inside the screen");
    }

    @Test
    void cornerHandleResizesAroundTheOppositeCorner() {
        editor.press(10, 5, 0, false);
        editor.release();
        assertEquals(3, editor.handleAt(40, 10));
        editor.press(40, 10, 0, false);
        assertEquals(HudEditorModel.Mode.RESIZING, editor.mode());
        editor.drag(80, 20, false);
        editor.release();
        WidgetEntry e = entry("a");
        assertEquals(2f, e.scale());
        assertEquals(0, e.bounds().x, "top left stays");
        assertEquals(80, e.bounds().width);
        editor.press(80, 20, 0, false);
        editor.drag(400, 400, false);
        editor.release();
        assertEquals(3f, e.scale(), "clamped to maxScale");
    }

    @Test
    void scrollScalesRightClickTogglesAltCycles() {
        WidgetEntry eb = entry("b");
        int cx = eb.bounds().x + 20;
        int cy = eb.bounds().y + 5;
        editor.scroll(cx, cy, 1);
        editor.scroll(cx, cy, 1);
        assertEquals(1.2f, eb.scale(), 1e-6);
        editor.undo();
        assertEquals(1f, eb.scale(), 1e-6, "a scroll series is one undo step");

        editor.press(cx, cy, 1, false);
        assertFalse(eb.isEnabled());
        editor.press(cx, cy, 1, false);
        assertTrue(eb.isEnabled());

        // put a below b at the same place: Alt-click cycles through both
        entry("a").state().x = eb.state().x;
        entry("a").state().y = eb.state().y;
        layout();
        editor.press(cx, cy, 0, false);
        editor.release();
        assertSame(eb, editor.selected(), "topmost first");
        editor.press(cx, cy, 0, true);
        editor.release();
        assertSame(entry("a"), editor.selected());
        editor.moveLayer(entry("a"), 2);
        assertSame(entry("a"), editor.hit(cx, cy), "brought to front");
    }

    @Test
    void discardRestoresEverythingIncludingProfiles() {
        List<Boolean> closed = new ArrayList<>();
        core.events().subscribe(HudEditorClosedEvent.class, e -> closed.add(e.saved()));
        editor.press(10, 5, 0, false);
        editor.drag(100, 100, false);
        editor.release();
        String created = editor.createProfile();
        assertEquals("custom-1", editor.profile());
        editor.close(false);
        assertEquals("default", core.widgetRegistry().activeProfile());
        assertFalse(core.widgetRegistry().layout().profiles.containsKey(created));
        assertEquals(0f, entry("a").x());
        assertEquals(List.of(false), closed);
    }

    @Test
    void closingEndsADrag() {
        editor.press(10, 5, 0, false);
        editor.drag(100, 100, false);
        editor.close(true);
        assertEquals(HudEditorModel.Mode.NONE, editor.mode());
        assertTrue(entry("a").x() > 0, "kept");
    }

    @Test
    void resetRestoresDefaults() {
        entry("a").state().x = 0.9f;
        entry("a").state().scale = 2f;
        editor.resetWidget(entry("a"));
        assertEquals(0f, entry("a").x());
        assertEquals(1f, entry("a").scale());
        editor.undo();
        assertEquals(0.9f, entry("a").x());
    }
}
