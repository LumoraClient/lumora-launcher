package dev.lumora.client.hud;

import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.input.Keys;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.api.McHudGraphics;
import dev.lumora.client.api.McRenderContext;
import dev.lumora.client.gui.LumoraButton;
import dev.lumora.client.gui.LumoraShaders;
import dev.lumora.client.gui.LumoraUi;
import dev.lumora.client.gui.SettingsScreen;
import dev.lumora.core.hud.Bounds;
import dev.lumora.core.hud.HudEditorModel;
import dev.lumora.core.hud.HudLayout;
import dev.lumora.core.hud.WidgetEntry;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.compat.Screen;
import dev.lumora.client.compat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor. Every registered widget (built-in and from addons) is listed automatically and drawn exactly as in
 * game. Click = select, drag = move (snaps to edges, center, other widgets and optionally a grid; Shift = free),
 * corner handles = resize, wheel = size, right click = on/off, Alt+click = next widget below, double click =
 * settings. Keys: arrows nudge (Shift = 10 px), Del = off, H = hide, G = grid, Ctrl+Z / Ctrl+Y = undo / redo.
 * The logic lives in {@link HudEditorModel}; this screen draws and forwards input.
 */
public final class HudEditorScreen extends Screen {
    private static final int LIST_W = 138;
    private static final int ROW = 17;
    private static boolean listOpen = true;
    private static int page;
    private final net.minecraft.client.gui.screen.Screen parent;
    private final HudEditorModel model;
    /** Keep the editor session when switching to a settings screen. */
    private boolean keepSession;
    private final List<int[]> headers = new ArrayList<>();
    private final List<String> headerNames = new ArrayList<>();
    private WidgetEntry lastSelected;
    private long lastClick;

    public HudEditorScreen(net.minecraft.client.gui.screen.Screen parent) {
        this(parent, LumoraRuntime.core().openEditor());
    }

    private HudEditorScreen(net.minecraft.client.gui.screen.Screen parent, HudEditorModel model) {
        super(Component.translatable("lumora.hud.editor"));
        this.parent = parent;
        this.model = model;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String name(WidgetEntry e) {
        return Component.translatable(e.widget().nameKey()).getString();
    }

    private int listLeft() {
        return width - LIST_W - 8;
    }

    private boolean inList(double mx) {
        return listOpen && mx >= listLeft() - 4;
    }

    @Override
    public void init() {
        headers.clear();
        headerNames.clear();
        lastSelected = model.selected();
        if (listOpen) {
            tools();
            widgetList();
        } else {
            button(width - 82, 4, 76, 16, tr("lumora.hud.list.show"), "☰", () -> listOpen = true);
            addRenderableWidget(new LumoraButton(width / 2 - 50, height - 24, 100, 20, tr("gui.done"), LumoraButton.Style.PRIMARY, null, this::onClose));
        }
        WidgetEntry sel = model.selected();
        if (sel != null) {
            selectionPanel(sel);
        }
    }

    /** Profile, grid, snapping and undo/redo at the top of the side panel. */
    private void tools() {
        int x = listLeft();
        int half = (LIST_W - 4) / 2;
        button(x + LIST_W - 16, 4, 16, 14, Component.literal("×"), null, () -> listOpen = false);
        button(x, 20, LIST_W - 20, 16, tr("lumora.hud.profile", profileName(model.profile())), null, () -> {
            List<String> ids = model.profiles();
            model.switchProfile(ids.get((ids.indexOf(model.profile()) + 1) % ids.size()));
        });
        button(x + LIST_W - 16, 20, 16, 16, Component.literal("+"), null, model::createProfile);
        HudLayout l = model.settings();
        button(x, 39, half, 16, tr(l.grid ? "lumora.hud.grid.on" : "lumora.hud.grid.off"), null, () -> model.setGrid(!l.grid));
        button(x + half + 4, 39, half, 16, tr(l.snap ? "lumora.hud.snap.on" : "lumora.hud.snap.off"), null, () -> model.setSnap(!l.snap));
        button(x, 58, half, 16, tr("lumora.hud.undo"), null, model::undo).active = model.canUndo();
        button(x + half + 4, 58, half, 16, tr("lumora.hud.redo"), null, model::redo).active = model.canRedo();
    }

    private LumoraButton button(int x, int y, int w, int h, Component label, String glyph, Runnable action) {
        LumoraButton b = new LumoraButton(x, y, w, h, label, LumoraButton.Style.SECONDARY, glyph, () -> {
            action.run();
            rebuildWidgets();
        });
        addRenderableWidget(b);
        return b;
    }

    private LumoraButton button(int x, int y, int w, Component label, String glyph, Runnable action) {
        return button(x, y, w, 16, label, glyph, action);
    }

    @Override
    public void tick() {
        // selection changed from outside the screen (keyboard, API): show the matching action buttons
        if (model.selected() != lastSelected && model.mode() == HudEditorModel.Mode.NONE) {
            rebuildWidgets();
        }
    }

    private static String profileName(String id) {
        String key = "lumora.hud.profile." + id;
        Component c = Component.translatable(key);
        return c.getString().equals(key) ? id : c.getString();
    }

    private void widgetList() {
        int x = listLeft();
        int top = 80;
        int bottom = height - 74;
        int capacity = Math.max(3, (bottom - top) / ROW);
        // rows: category headers + widgets, paged
        List<Object> rows = new ArrayList<>();
        String category = null;
        List<WidgetEntry> sorted = new ArrayList<>(model.entries());
        sorted.sort((a, b) -> {
            int c = a.category().compareTo(b.category());
            return c != 0 ? c : name(a).compareToIgnoreCase(name(b));
        });
        for (WidgetEntry e : sorted) {
            if (!e.category().equals(category)) {
                category = e.category();
                rows.add(category);
            }
            rows.add(e);
        }
        int pages = Math.max(1, (rows.size() + capacity - 1) / capacity);
        page = Math.min(page, pages - 1);
        int y = top;
        for (int i = page * capacity; i < Math.min(rows.size(), (page + 1) * capacity); i++) {
            Object row = rows.get(i);
            if (row instanceof String cat) {
                headers.add(new int[]{x, y + 5});
                headerNames.add(categoryName(cat));
            } else {
                WidgetEntry e = (WidgetEntry) row;
                String label = (e.isEnabled() ? "● " : "○ ") + name(e) + (e.failure().isPresent() ? " ⚠" : !e.isShown() ? " ◌" : "");
                LumoraButton b = new LumoraButton(x, y, LIST_W, ROW - 1, Component.literal(label),
                        e == model.selected() ? LumoraButton.Style.PRIMARY : e.isEnabled() ? LumoraButton.Style.SECONDARY : LumoraButton.Style.NAV,
                        null, () -> {
                            if (model.selected() == e) {
                                model.toggleEnabled(e);
                            } else {
                                model.select(e);
                            }
                            rebuildWidgets();
                        });
                addRenderableWidget(b);
            }
            y += ROW;
        }
        if (pages > 1) {
            LumoraButton prev = new LumoraButton(x, bottom + 2, 20, 14, Component.literal("‹"), LumoraButton.Style.SECONDARY, null, () -> {
                page--;
                rebuildWidgets();
            });
            prev.active = page > 0;
            addRenderableWidget(prev);
            LumoraButton next = new LumoraButton(x + LIST_W - 20, bottom + 2, 20, 14, Component.literal("›"), LumoraButton.Style.SECONDARY, null, () -> {
                page++;
                rebuildWidgets();
            });
            next.active = page < pages - 1;
            addRenderableWidget(next);
        }
        int half = (LIST_W - 4) / 2;
        addRenderableWidget(new LumoraButton(x, height - 50, half, 18, tr("lumora.hud.reset"), LumoraButton.Style.SECONDARY, null, () -> {
            model.resetAll();
            rebuildWidgets();
        }));
        addRenderableWidget(new LumoraButton(x + half + 4, height - 50, half, 18, tr("lumora.hud.discard"), LumoraButton.Style.SECONDARY, null, () -> {
            model.close(false);
            minecraft.setScreen(parent);
        }));
        addRenderableWidget(new LumoraButton(x, height - 28, LIST_W, 20, tr("gui.done"), LumoraButton.Style.PRIMARY, null, this::onClose));
    }

    private static String categoryName(String cat) {
        String key = WidgetCategory.nameKey(cat);
        String s = Component.translatable(key).getString();
        return (s.equals(key) ? cat : s).toUpperCase(java.util.Locale.ROOT);
    }

    /** Width left of the side panel (or the whole screen). */
    private int freeWidth() {
        return listOpen ? listLeft() - 14 : width - 16;
    }

    private int selectionPanelWidth() {
        return Math.min(240, freeWidth());
    }

    private int selectionPanelY() {
        return height - 18 - 60;
    }

    /** Card with the actions for the selected widget (bottom left): two rows of three buttons. */
    private void selectionPanel(WidgetEntry e) {
        int x = 8 + 6;
        int y = selectionPanelY() + 20;
        int bw = (selectionPanelWidth() - 12 - 6) / 3;
        LumoraButton settings = new LumoraButton(x, y, bw, 16, tr("lumora.hud.sel.settings"), LumoraButton.Style.PRIMARY, null, () -> openSettings(e));
        settings.active = !e.widget().settings().isEmpty();
        addRenderableWidget(settings);
        button(x + bw + 3, y, bw, tr(e.isShown() ? "lumora.hud.sel.hide" : "lumora.hud.sel.show"), null, () -> model.toggleShown(e));
        button(x + 2 * (bw + 3), y, bw, tr(e.isEnabled() ? "lumora.hud.sel.off" : "lumora.hud.sel.on"), null, () -> model.toggleEnabled(e));
        y += 19;
        button(x, y, bw, tr("lumora.hud.sel.front"), null, () -> model.moveLayer(e, 1));
        button(x + bw + 3, y, bw, tr("lumora.hud.sel.back"), null, () -> model.moveLayer(e, -1));
        button(x + 2 * (bw + 3), y, bw, tr("lumora.hud.sel.reset"), null, () -> model.resetWidget(e));
    }

    private void openSettings(WidgetEntry e) {
        keepSession = true;
        minecraft.setScreen(SettingsScreen.create(new HudEditorScreen(parent, model), Component.literal(name(e)),
                e.widget().settings(), List.of(), null));
    }

    // --- drawing

    /** Minecraft 1.12.2: Screen#render does not draw the background (1.20.2+ does), so the screen draws it first. */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
    }

    /** Background of this screen; Minecraft 1.12.2 has no Screen#renderBackground(g, x, y, delta) yet, see render. */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.world == null) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
            LumoraShaders.sky(g, width, height, 0.6f);
        }
        g.fill(0, 0, width, height, LumoraUi.alpha(LumoraUi.BG, minecraft.world == null ? 0x60 : 0x40));
        HudLayout l = model.settings();
        if (l.grid) {
            int step = Math.max(2, l.gridSize);
            int c = LumoraUi.alpha(0xFFFFFFFF, 0x12);
            for (int x = 0; x < width; x += step) {
                g.fill(x, 0, x + 1, height, c);
            }
            for (int y = 0; y < height; y += step) {
                g.fill(0, y, width, y + 1, c);
            }
        }
        g.fill(width / 2, 0, width / 2 + 1, height, LumoraUi.alpha(LumoraUi.ACCENT, 0x28));
        g.fill(0, height / 2, width, height / 2 + 1, LumoraUi.alpha(LumoraUi.ACCENT, 0x28));

        McRenderContext ctx = LumoraRuntime.editorContext(width, height, delta);
        McHudGraphics hg = LumoraRuntime.graphics(g);
        model.layout(ctx);
        WidgetEntry hover = inList(mouseX) ? null : model.hit(mouseX, mouseY);
        for (WidgetEntry e : model.entries()) {
            Bounds b = e.bounds();
            if (!b.valid) {
                continue;
            }
            boolean live = e.isEnabled() && e.isShown() && e.failure().isEmpty() && visible(e, ctx);
            if (live) {
                LumoraRuntime.core().renderer().draw(hg, ctx, e);
            } else {
                g.fill(b.x, b.y, b.right(), b.bottom(), LumoraUi.alpha(LumoraUi.BG, 0x70));
                String state = e.failure().isPresent() ? tr("lumora.hud.state.failed").getString()
                        : !e.isEnabled() ? tr("lumora.hud.state.off").getString()
                        : !e.isShown() ? tr("lumora.hud.state.hidden").getString() : tr("lumora.hud.state.condition").getString();
                g.drawString(font, name(e), b.x + 3, b.y + 3, LumoraUi.MUTED, true);
                if (b.height >= 22) {
                    g.drawString(font, state, b.x + 3, b.y + 13, e.failure().isPresent() ? 0xFFF2555A : LumoraUi.MUTED, true);
                }
            }
            int c = e == model.selected() ? LumoraUi.ACCENT_HOVER : e == hover ? LumoraUi.alpha(LumoraUi.ACCENT, 0xC0) : LumoraUi.alpha(0xFFFFFFFF, 0x30);
            hg.outline(b.x - 1, b.y - 1, b.width + 2, b.height + 2, c);
        }
        for (int gx : model.guidesX()) {
            g.fill(gx, 0, gx + 1, height, LumoraUi.alpha(LumoraUi.ACCENT, 0xD0));
        }
        for (int gy : model.guidesY()) {
            g.fill(0, gy, width, gy + 1, LumoraUi.alpha(LumoraUi.ACCENT, 0xD0));
        }
        WidgetEntry sel = model.selected();
        if (sel != null && sel.bounds().valid) {
            Bounds b = sel.bounds();
            int h = HudEditorModel.HANDLE;
            int[][] corners = {{b.x, b.y}, {b.right(), b.y}, {b.x, b.bottom()}, {b.right(), b.bottom()}};
            for (int[] p : corners) {
                g.fill(p[0] - h, p[1] - h, p[0] + h, p[1] + h, LumoraUi.ACCENT_HOVER);
                g.fill(p[0] - h + 1, p[1] - h + 1, p[0] + h - 1, p[1] + h - 1, 0xFF0E0A16);
            }
        }
        if (hover != null && model.mode() == HudEditorModel.Mode.NONE) {
            String info = name(hover) + "  " + Math.round(hover.scale() * 100) + " %";
            g.drawString(font, info, hover.bounds().x, Math.max(22, hover.bounds().y - 10), LumoraUi.ACCENT_HOVER, true);
        }
        if (listOpen) {
            g.fill(listLeft() - 6, 0, width, height, LumoraUi.alpha(LumoraUi.BG, 0xD8));
            g.drawString(font, tr("lumora.hud.editor.widgets", model.entries().size()), listLeft(), 7, LumoraUi.TEXT, true);
            for (int i = 0; i < headers.size(); i++) {
                int[] p = headers.get(i);
                g.drawString(font, headerNames.get(i), p[0] + 2, p[1], LumoraUi.MUTED, false);
            }
        }
        if (sel != null) {
            int x = 8;
            int y = selectionPanelY();
            int w = selectionPanelWidth();
            LumoraUi.card(g, x, y, w, 60, LumoraUi.alpha(LumoraUi.SURFACE, 0xF0), LumoraUi.BORDER);
            String layer = tr("lumora.hud.sel.info", Math.round(sel.scale() * 100), sel.layer()).getString();
            g.drawString(font, fit(name(sel), w - 20 - font.width(layer)), x + 7, y + 7, LumoraUi.TEXT, false);
            g.drawString(font, layer, x + w - 7 - font.width(layer), y + 7, LumoraUi.MUTED, false);
            sel.failure().ifPresent(f -> g.drawString(font, fit(f, w), x + 1, y - 11, 0xFFF2555A, true));
        }
        g.drawString(font, fit(tr("lumora.hud.editor.hint").getString(), freeWidth()), 8, height - 14, LumoraUi.MUTED, true);
    }

    private String fit(String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && font.width(t + "…") > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static boolean visible(WidgetEntry e, McRenderContext ctx) {
        try {
            return e.widget().isVisible(ctx);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // --- version-neutral input (the overrides below only translate the events)

    boolean press(double mx, double my, int button) {
        if (inList(mx)) {
            return false;
        }
        boolean alt = LumoraRuntime.keyDown(Keys.LEFT_ALT) || LumoraRuntime.keyDown(Keys.RIGHT_ALT);
        WidgetEntry before = model.selected();
        boolean handled = model.press(mx, my, button, alt);
        long now = System.currentTimeMillis();
        if (button == 0 && handled && model.selected() != null && model.selected() == before && now - lastClick < 300
                && model.handleAt(mx, my) < 0 && !model.selected().widget().settings().isEmpty()) {
            model.release();
            openSettings(model.selected());
            return true;
        }
        lastClick = now;
        if (model.selected() != lastSelected || button == 1) {
            rebuildWidgets();
        }
        return handled;
    }

    boolean drag(double mx, double my) {
        return model.drag(mx, my, LumoraRuntime.keyDown(Keys.LEFT_SHIFT) || LumoraRuntime.keyDown(Keys.RIGHT_SHIFT));
    }

    boolean release() {
        boolean r = model.release();
        if (r) {
            rebuildWidgets();
        }
        return r;
    }

    boolean key(int key, boolean ctrl, boolean shift) {
        if (ctrl && key == Keys.Z) {
            if (shift) {
                model.redo();
            } else {
                model.undo();
            }
        } else if (ctrl && key == Keys.Y) {
            model.redo();
        } else if (key == Keys.G && !ctrl) {
            model.setGrid(!model.settings().grid);
        } else if (model.selected() != null && (key == Keys.LEFT || key == Keys.RIGHT || key == Keys.UP || key == Keys.DOWN)) {
            int step = shift ? 10 : 1;
            model.nudge(key == Keys.LEFT ? -step : key == Keys.RIGHT ? step : 0, key == Keys.UP ? -step : key == Keys.DOWN ? step : 0);
            return true;
        } else if (model.selected() != null && key == Keys.DELETE) {
            model.toggleEnabled(model.selected());
        } else if (model.selected() != null && key == Keys.H) {
            model.toggleShown(model.selected());
        } else {
            return false;
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sy) {
        if (inList(mx)) {
            page = Math.max(0, page - (int) Math.signum(sy));
            rebuildWidgets();
            return true;
        }
        return model.scroll(mx, my, sy) || super.mouseScrolled(mx, my, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button) || press(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return drag(mx, my) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return release() || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode, hasControlDown(), hasShiftDown()) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        model.close(true);
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        // any other way out (another screen opened, game closed) keeps the changes and ends a running drag
        if (!keepSession && !model.isClosed()) {
            model.close(true);
        }
        keepSession = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For the smoke test: the editor logic of this screen. */
    public HudEditorModel model() {
        return model;
    }
}
