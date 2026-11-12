package dev.lumora.core.hud;

import dev.lumora.api.event.hud.HudEditorClosedEvent;
import dev.lumora.api.event.hud.HudEditorOpenedEvent;
import dev.lumora.api.event.hud.WidgetMovedEvent;
import dev.lumora.api.event.hud.WidgetScaledEvent;
import dev.lumora.api.event.hud.WidgetSelectedEvent;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.core.event.SimpleEventBus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logic of the HUD editor, independent of Minecraft: selection, drag-and-drop with snapping (screen edges,
 * center lines, other widgets, optional grid), resizing with corner handles, layers, undo/redo, reset, revert
 * and profiles. The editor screen only draws and forwards input. Coordinates are GUI pixels.
 */
public final class HudEditorModel {
    public static final int SNAP = 4;
    public static final int DRAG_THRESHOLD = 2;
    /** Half size of the corner handles. */
    public static final int HANDLE = 3;
    static final int MAX_UNDO = 50;
    static final float SCALE_STEP = 0.05f;

    public enum Mode { NONE, PRESSED, MOVING, RESIZING }

    private final WidgetRegistryImpl registry;
    private final HudRenderer renderer;
    private final SimpleEventBus events;
    private final String openedProfile;
    /** Profile contents as they were when the editor opened (for "discard"), per touched profile. */
    private final Map<String, HudProfile> openedSnapshots = new LinkedHashMap<>();
    private final Deque<HudProfile> undo = new ArrayDeque<>();
    private final Deque<HudProfile> redo = new ArrayDeque<>();

    private WidgetEntry selected;
    private Mode mode = Mode.NONE;
    private int corner = -1;
    private double pressX;
    private double pressY;
    private double grabX;
    private double grabY;
    private int startX;
    private int startY;
    private int startW;
    private int startH;
    private HudProfile before;
    /** Consecutive scroll/nudge steps on one widget form one undo step. */
    private WidgetEntry coalescing;
    private int screenW = 1;
    private int screenH = 1;
    private final List<Integer> guidesX = new ArrayList<>();
    private final List<Integer> guidesY = new ArrayList<>();
    /** Widgets the dragged one snapped to in the last move (highlighted by the editor). */
    private final List<WidgetEntry> magnets = new ArrayList<>();
    /** Anchor zone under the cursor while moving. */
    private String dragZone;
    private boolean closed;

    public HudEditorModel(WidgetRegistryImpl registry, HudRenderer renderer, SimpleEventBus events) {
        this.registry = registry;
        this.renderer = renderer;
        this.events = events;
        this.openedProfile = registry.activeProfile();
        snapshotIfNew(openedProfile);
        events.post(new HudEditorOpenedEvent(openedProfile));
    }

    private void snapshotIfNew(String profile) {
        if (!openedSnapshots.containsKey(profile)) {
            HudProfile p = registry.layout().profiles.get(profile);
            openedSnapshots.put(profile, p != null ? p.copy() : null);
        }
    }

    // --- layout pass

    /** Computes all widget rectangles (also of disabled widgets, which the editor shows as placeholders). */
    public void layout(RenderContext ctx) {
        screenW = Math.max(1, ctx.screenWidth());
        screenH = Math.max(1, ctx.screenHeight());
        for (WidgetEntry e : registry.entries()) {
            if (e.failure != null) {
                // a failed widget cannot report its size; keep a placeholder of its last size
                HudRenderer.place(e.bounds, e.state, Math.max(40, e.bounds.rawWidth), Math.max(12, e.bounds.rawHeight), screenW, screenH);
            } else {
                renderer.layout(e, ctx);
            }
        }
    }

    public List<WidgetEntry> entries() {
        return registry.entries();
    }

    public WidgetEntry selected() {
        return selected;
    }

    public Mode mode() {
        return mode;
    }

    public List<Integer> guidesX() {
        return guidesX;
    }

    public List<Integer> guidesY() {
        return guidesY;
    }

    /** Anchor zone (see {@link Anchor#ZONES}) under the cursor while a widget is moved, else null. */
    public String dragZone() {
        return mode == Mode.MOVING ? dragZone : null;
    }

    /** Widgets the moved one snapped to (edges or centers). */
    public List<WidgetEntry> magnets() {
        return magnets;
    }

    public HudLayout settings() {
        return registry.layout();
    }

    public void select(WidgetEntry e) {
        if (selected != e) {
            selected = e;
            coalescing = null;
            events.post(new WidgetSelectedEvent(e));
        }
    }

    /** Topmost widget under the point. */
    public WidgetEntry hit(double mx, double my) {
        List<WidgetEntry> list = registry.entries();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).bounds.contains(mx, my)) {
                return list.get(i);
            }
        }
        return null;
    }

    /** Next widget under the point below the selected one (Alt-click cycles through overlapping widgets). */
    WidgetEntry nextUnder(double mx, double my) {
        List<WidgetEntry> list = registry.entries();
        boolean passed = selected == null || !selected.bounds.contains(mx, my);
        for (int round = 0; round < 2; round++) {
            for (int i = list.size() - 1; i >= 0; i--) {
                WidgetEntry e = list.get(i);
                if (!e.bounds.contains(mx, my)) {
                    continue;
                }
                if (passed) {
                    return e;
                }
                if (e == selected) {
                    passed = true;
                }
            }
            passed = true;
        }
        return null;
    }

    /** Corner handle of the selected widget under the point: 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
    public int handleAt(double mx, double my) {
        if (selected == null || !selected.bounds.valid) {
            return -1;
        }
        Bounds b = selected.bounds;
        int[][] corners = {{b.x, b.y}, {b.right(), b.y}, {b.x, b.bottom()}, {b.right(), b.bottom()}};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(mx - corners[i][0]) <= HANDLE + 1 && Math.abs(my - corners[i][1]) <= HANDLE + 1) {
                return i;
            }
        }
        return -1;
    }

    // --- mouse

    /**
     * Mouse button pressed. Left: handle = resize, widget = select (+ drag after a few pixels), empty = deselect.
     * Right: switch the widget on/off. {@code cycle} (Alt) selects the next widget below the selected one.
     */
    public boolean press(double mx, double my, int button, boolean cycle) {
        if (button == 1) {
            WidgetEntry e = hit(mx, my);
            if (e == null) {
                return false;
            }
            select(e);
            HudProfile b = snapshot();
            e.setEnabled(!e.state.enabled);
            commit(b);
            return true;
        }
        if (button != 0) {
            return false;
        }
        int c = handleAt(mx, my);
        if (c >= 0) {
            begin(Mode.RESIZING, mx, my);
            corner = c;
            return true;
        }
        WidgetEntry e = cycle ? nextUnder(mx, my) : hit(mx, my);
        select(e);
        if (e == null) {
            return false;
        }
        begin(Mode.PRESSED, mx, my);
        grabX = mx - e.bounds.x;
        grabY = my - e.bounds.y;
        raise(e);
        return true;
    }

    /** A clicked widget comes to the front (part of the undo step of the click). */
    private void raise(WidgetEntry e) {
        int max = Integer.MIN_VALUE;
        for (WidgetEntry o : registry.entries()) {
            if (o != e) {
                max = Math.max(max, o.state.layer);
            }
        }
        if (max != Integer.MIN_VALUE && e.state.layer <= max && max < 1000) {
            e.state.layer = max + 1;
            registry.rebuildOrder();
        }
    }

    private void begin(Mode m, double mx, double my) {
        mode = m;
        pressX = mx;
        pressY = my;
        before = snapshot();
        coalescing = null;
        Bounds b = selected.bounds;
        startX = b.x;
        startY = b.y;
        startW = b.width;
        startH = b.height;
    }

    /** Mouse moved with the button held. {@code free} (Shift) disables snapping. */
    public boolean drag(double mx, double my, boolean free) {
        if (selected == null || mode == Mode.NONE) {
            return false;
        }
        if (mode == Mode.PRESSED) {
            if (Math.abs(mx - pressX) < DRAG_THRESHOLD && Math.abs(my - pressY) < DRAG_THRESHOLD) {
                return true;
            }
            mode = Mode.MOVING;
        }
        if (mode == Mode.MOVING) {
            move(mx - grabX, my - grabY, free, mx, my);
        } else if (mode == Mode.RESIZING) {
            resize(mx, my);
        }
        return true;
    }

    /** Moves the selection; the cursor (cx, cy) decides the anchor zone the widget hangs on afterwards. */
    private void move(double x, double y, boolean free, double cx, double cy) {
        Bounds b = selected.bounds;
        int w = b.width;
        int h = b.height;
        guidesX.clear();
        guidesY.clear();
        magnets.clear();
        dragZone = Anchor.zone(cx, cy, screenW, screenH);
        selected.state.anchor = dragZone;
        HudLayout l = registry.layout();
        double sx = x;
        double sy = y;
        boolean snappedX = false;
        boolean snappedY = false;
        if (l.snap && !free) {
            double[] rx = snapAxis(x, w, screenW, true);
            double[] ry = snapAxis(y, h, screenH, false);
            snappedX = rx[1] >= 0;
            snappedY = ry[1] >= 0;
            sx = rx[0];
            sy = ry[0];
            if (snappedX) {
                guidesX.add((int) rx[1]);
                magnet((int) rx[2]);
            }
            if (snappedY) {
                guidesY.add((int) ry[1]);
                magnet((int) ry[2]);
            }
        }
        if (l.grid && !free) {
            int g = Math.max(2, l.gridSize);
            if (!snappedX) {
                sx = Math.round(sx / g) * g;
            }
            if (!snappedY) {
                sy = Math.round(sy / g) * g;
            }
        }
        setPosition(selected, sx, sy, w, h);
    }

    private void magnet(int index) {
        if (index >= 0 && index < registry.entries().size()) {
            WidgetEntry e = registry.entries().get(index);
            if (!magnets.contains(e)) {
                magnets.add(e);
            }
        }
    }

    /**
     * Returns {new position, guide line or -1, index of the widget snapped to or -1}. Snaps to screen edges, center and
     * other widgets' edges/centers.
     */
    private double[] snapAxis(double pos, int size, int screen, boolean horizontal) {
        double best = SNAP;
        double result = pos;
        double guide = -1;
        double target = -1;
        // own edges (start, center, end) against screen edges/center and the edges/centers of other widgets
        double[] own = {0, size / 2.0, size};
        List<double[]> lines = new ArrayList<>();
        lines.add(new double[]{0, -1});
        lines.add(new double[]{screen / 2.0, -1});
        lines.add(new double[]{screen, -1});
        List<WidgetEntry> all = registry.entries();
        for (int i = 0; i < all.size(); i++) {
            WidgetEntry o = all.get(i);
            if (o == selected || !o.bounds.valid || !o.state.enabled) {
                continue;
            }
            Bounds ob = o.bounds;
            double start = horizontal ? ob.x : ob.y;
            double length = horizontal ? ob.width : ob.height;
            lines.add(new double[]{start, i});
            lines.add(new double[]{start + length / 2.0, i});
            lines.add(new double[]{start + length, i});
        }
        for (double[] line : lines) {
            for (double edge : own) {
                double d = Math.abs(pos + edge - line[0]);
                if (d < best) {
                    best = d;
                    result = line[0] - edge;
                    guide = line[0];
                    target = line[1];
                }
            }
        }
        return new double[]{result, guide, target};
    }

    private void resize(double mx, double my) {
        Bounds b = selected.bounds;
        int rawW = Math.max(1, b.rawWidth);
        int rawH = Math.max(1, b.rawHeight);
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner == 2 || corner == 3;
        double w = right ? mx - startX : startX + startW - mx;
        double h = bottom ? my - startY : startY + startH - my;
        // the dragged size includes the size of all widgets ("Widget-Design"); the widget keeps its own part
        float all = WidgetDesign.scale();
        float scale = (float) Math.max(w / rawW, h / rawH) / all;
        scale = Math.round(scale / SCALE_STEP) * SCALE_STEP;
        scale = WidgetRegistryImpl.clampScale(selected.widget, scale);
        selected.state.scale = scale;
        int nw = Math.max(1, Math.round(rawW * scale * all));
        int nh = Math.max(1, Math.round(rawH * scale * all));
        double nx = right ? startX : startX + startW - nw;
        double ny = bottom ? startY : startY + startH - nh;
        setPosition(selected, nx, ny, nw, nh);
    }

    /**
     * Stores a pixel position (always inside the screen): as fractions, and for an anchored widget also as offsets to
     * its anchor (which then decide where it is drawn).
     */
    void setPosition(WidgetEntry e, double px, double py, int w, int h) {
        int freeW = screenW - w;
        int freeH = screenH - h;
        e.state.x = freeW > 0 ? (float) Math.max(0, Math.min(1, px / freeW)) : 0f;
        e.state.y = freeH > 0 ? (float) Math.max(0, Math.min(1, py / freeH)) : 0f;
        if (e.state.anchor != null) {
            double cx = Math.max(0, Math.min(Math.max(0, freeW), px));
            double cy = Math.max(0, Math.min(Math.max(0, freeH), py));
            int[] o = Anchor.offsets(e.state.anchor, cx, cy, w, h, screenW, screenH);
            e.state.offsetX = o[0];
            e.state.offsetY = o[1];
        }
        HudRenderer.place(e.bounds, e.state, e.bounds.rawWidth, e.bounds.rawHeight, screenW, screenH);
    }

    /** Mouse button released: ends a move/resize as one undo step. */
    public boolean release() {
        Mode m = mode;
        mode = Mode.NONE;
        corner = -1;
        guidesX.clear();
        guidesY.clear();
        magnets.clear();
        dragZone = null;
        if (m == Mode.NONE || selected == null) {
            return false;
        }
        if (m == Mode.PRESSED) {
            commit(before); // brought to the front by the click
        }
        if (m == Mode.MOVING || m == Mode.RESIZING) {
            commit(before);
            if (m == Mode.MOVING) {
                events.post(new WidgetMovedEvent(selected, selected.state.x, selected.state.y));
            } else {
                events.post(new WidgetScaledEvent(selected, selected.state.scale));
            }
        }
        before = null;
        return true;
    }

    /** Mouse wheel over a widget (or the selection): scale in steps of 0.1 around its center. */
    public boolean scroll(double mx, double my, double amount) {
        WidgetEntry e = hit(mx, my);
        if (e == null) {
            e = selected;
        }
        if (e == null || amount == 0) {
            return false;
        }
        select(e);
        HudProfile b = coalesce(e);
        float scale = Math.round((e.state.scale + (amount > 0 ? 0.1f : -0.1f)) * 10f) / 10f;
        scale = WidgetRegistryImpl.clampScale(e.widget, scale);
        if (scale != e.state.scale) {
            double cx = e.bounds.x + e.bounds.width / 2.0;
            double cy = e.bounds.y + e.bounds.height / 2.0;
            e.state.scale = scale;
            int nw = Math.max(1, Math.round(e.bounds.rawWidth * scale * WidgetDesign.scale()));
            int nh = Math.max(1, Math.round(e.bounds.rawHeight * scale * WidgetDesign.scale()));
            setPosition(e, cx - nw / 2.0, cy - nh / 2.0, nw, nh);
            commitCoalesced(b);
            events.post(new WidgetScaledEvent(e, scale));
        }
        return true;
    }

    /** Arrow keys: move the selection by pixels. */
    public boolean nudge(int dx, int dy) {
        if (selected == null || !selected.bounds.valid) {
            return false;
        }
        HudProfile b = coalesce(selected);
        Bounds bo = selected.bounds;
        setPosition(selected, bo.x + dx, bo.y + dy, bo.width, bo.height);
        commitCoalesced(b);
        return true;
    }

    private HudProfile coalesce(WidgetEntry e) {
        if (coalescing == e && !undo.isEmpty()) {
            return null; // already recorded the state before this series
        }
        coalescing = e;
        return snapshot();
    }

    private void commitCoalesced(HudProfile b) {
        if (b != null) {
            commit(b);
        } else {
            redo.clear();
            registry.changed();
        }
    }

    // --- commands

    public void toggleEnabled(WidgetEntry e) {
        HudProfile b = snapshot();
        e.setEnabled(!e.state.enabled);
        commit(b);
    }

    public void toggleShown(WidgetEntry e) {
        HudProfile b = snapshot();
        e.setShown(!e.state.shown);
        commit(b);
    }

    /** +1 = forward, -1 = backward, +2 = to front, -2 = to back. */
    public void moveLayer(WidgetEntry e, int how) {
        HudProfile b = snapshot();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (WidgetEntry o : registry.entries()) {
            if (o != e) {
                min = Math.min(min, o.state.layer);
                max = Math.max(max, o.state.layer);
            }
        }
        if (min == Integer.MAX_VALUE) {
            min = max = e.state.layer;
        }
        int layer = switch (how) {
            case 2 -> max + 1;
            case -2 -> min - 1;
            default -> e.state.layer + Integer.signum(how);
        };
        e.state.layer = Math.max(-1000, Math.min(1000, layer));
        registry.rebuildOrder();
        commit(b);
    }

    public void resetWidget(WidgetEntry e) {
        HudProfile b = snapshot();
        registry.resetToDefaults(e);
        commit(b);
    }

    /** All widgets of the active profile back to their defaults. */
    public void resetAll() {
        HudProfile b = snapshot();
        for (WidgetEntry e : registry.entries()) {
            registry.resetToDefaults(e);
        }
        commit(b);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        step(undo, redo);
    }

    public void redo() {
        step(redo, undo);
    }

    private void step(Deque<HudProfile> from, Deque<HudProfile> to) {
        if (from.isEmpty() || mode != Mode.NONE) {
            return;
        }
        to.push(snapshot());
        registry.layout().active().restore(from.pop());
        registry.reloadStates();
        registry.changed();
        coalescing = null;
    }

    public void setGrid(boolean grid) {
        registry.layout().grid = grid;
        registry.changed();
    }

    public void setSnap(boolean snap) {
        registry.layout().snap = snap;
        registry.changed();
    }

    // --- profiles

    public String profile() {
        return registry.activeProfile();
    }

    public List<String> profiles() {
        return registry.layout().profileIds();
    }

    /** Switches profile; undo history is per profile, so it is cleared. */
    public void switchProfile(String profile) {
        if (mode != Mode.NONE) {
            return;
        }
        snapshotIfNew(profile);
        if (registry.activateProfile(profile)) {
            undo.clear();
            redo.clear();
            coalescing = null;
        }
    }

    /** Creates {@code custom-N} as a copy of the current profile and activates it. */
    public String createProfile() {
        int n = 1;
        while (registry.layout().profiles.containsKey("custom-" + n)) {
            n++;
        }
        String id = "custom-" + n;
        switchProfile(id);
        return id;
    }

    // --- closing

    private HudProfile snapshot() {
        return registry.layout().active().copy();
    }

    private void commit(HudProfile b) {
        if (b == null || b.sameAs(registry.layout().active())) {
            return;
        }
        undo.push(b);
        while (undo.size() > MAX_UNDO) {
            undo.removeLast();
        }
        redo.clear();
        registry.changed();
    }

    /** Writes the layout now (asynchronously). */
    public void save() {
        registry.saveSoon();
    }

    /** Throws away everything changed since the editor opened (all touched profiles). */
    public void revert() {
        release();
        HudLayout l = registry.layout();
        for (Map.Entry<String, HudProfile> e : openedSnapshots.entrySet()) {
            if (e.getValue() == null) {
                l.profiles.remove(e.getKey());
            } else {
                l.profiles.computeIfAbsent(e.getKey(), k -> new HudProfile()).restore(e.getValue());
            }
        }
        l.activeProfile = l.profiles.containsKey(openedProfile) ? openedProfile : HudLayout.DEFAULT;
        registry.reloadStates();
        registry.changed();
        undo.clear();
        redo.clear();
    }

    /** Closes the editor; ends a running drag. {@code keep = false} discards all changes. */
    public void close(boolean keep) {
        if (closed) {
            return;
        }
        closed = true;
        release();
        if (!keep) {
            revert();
        }
        registry.saveSoon();
        events.post(new HudEditorClosedEvent(keep));
    }

    public boolean isClosed() {
        return closed;
    }
}
