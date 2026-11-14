package dev.lumora.core.chat.emoji;

import dev.lumora.api.chat.ChatPanel;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The emoji picker of the module "Emoji Chat" (a {@link ChatPanel} at the chat input): a row of category tabs (the
 * recently used ones first), a scrolling grid of the category's emojis and a line with the name of the emoji under
 * the mouse. A click inserts its {@code :name:} into the chat input; the panel stays open for more.
 */
public final class EmojiPanel implements ChatPanel {
    static final String RECENT = "recent";
    static final int TILE = 18;
    static final int ICON = 14;
    static final int TABS_H = 16;
    static final int FOOTER_H = 12;
    static final int PAD = 2;
    static final int SCROLLBAR = 3;
    static final int TEXT = 0xFFE8EEFA;
    static final int MUTED = 0xFF8D9BB8;

    private final EmojiRegistry registry;
    private final Supplier<List<EmojiRegistry.Emoji>> recent;
    private final Consumer<EmojiRegistry.Emoji> pick;
    private final Function<String, String> tr;
    private final int columns;
    private String category;
    /** First visible row (whole rows: no clipping needed, which old versions cannot nest). */
    private int row;
    private int maxRow;
    private EmojiRegistry.Emoji hovered;
    private String hoveredTab;
    /** The size the bar gave in the last frame (0: not drawn yet, use the wanted size). */
    private int shownW;
    private int shownH;

    /**
     * @param recent  recently used emojis, newest first
     * @param pick    inserts the emoji into the chat and remembers it as recently used
     * @param tr      translation of a key below {@code lumora.emojichat.}
     * @param columns emojis per row
     */
    public EmojiPanel(EmojiRegistry registry, Supplier<List<EmojiRegistry.Emoji>> recent, Consumer<EmojiRegistry.Emoji> pick,
                      Function<String, String> tr, int columns) {
        this.registry = registry;
        this.recent = recent;
        this.pick = pick;
        this.tr = tr;
        this.columns = Math.max(5, Math.min(14, columns));
        this.category = recent.get().isEmpty() ? EmojiRegistry.CATEGORIES.get(0) : RECENT;
    }

    /** The tabs: "recent" while there are recently used emojis, then the categories. */
    List<String> tabs() {
        List<String> out = new ArrayList<>();
        if (!recent.get().isEmpty()) {
            out.add(RECENT);
        }
        out.addAll(EmojiRegistry.CATEGORIES);
        return out;
    }

    String category() {
        return category;
    }

    void select(String tab) {
        if (!tab.equals(category)) {
            category = tab;
            row = 0;
        }
    }

    List<EmojiRegistry.Emoji> shown() {
        return RECENT.equals(category) ? recent.get() : registry.category(category);
    }

    @Override
    public int width() {
        return PAD * 2 + columns * TILE + SCROLLBAR + 1;
    }

    @Override
    public int height() {
        return TABS_H + 7 * TILE + FOOTER_H + PAD * 2;
    }

    private int gridTop() {
        return TABS_H + PAD;
    }

    private int gridHeight(int height) {
        return Math.max(TILE, height - gridTop() - FOOTER_H - PAD);
    }

    @Override
    public void resized(int width, int height) {
        shownW = width;
        shownH = height;
    }

    private int w() {
        return shownW > 0 ? shownW : width();
    }

    private int h() {
        return shownH > 0 ? shownH : height();
    }

    @Override
    public void render(HudGraphics g, int mouseX, int mouseY) {
        // the bar may give less room than asked for: draw into what is left
        renderInto(g, w(), h(), mouseX, mouseY);
    }

    void renderInto(HudGraphics g, int w, int h, int mouseX, int mouseY) {
        hovered = null;
        hoveredTab = null;
        List<String> tabs = tabs();
        if (!tabs.contains(category)) {
            category = tabs.get(0);
        }
        int tabW = Math.max(12, (w - PAD * 2) / tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            String t = tabs.get(i);
            int x = PAD + i * tabW;
            boolean over = mouseX >= x && mouseX < x + tabW && mouseY >= 0 && mouseY < TABS_H;
            if (t.equals(category)) {
                g.roundedRect(x, 1, tabW - 1, TABS_H - 2, 2, LumoraTheme.alpha(LumoraTheme.accent(), 0xC0));
            } else if (over) {
                g.roundedRect(x, 1, tabW - 1, TABS_H - 2, 2, 0x40FFFFFF);
            }
            if (over) {
                hoveredTab = t;
            }
            EmojiRegistry.Emoji icon = tabIcon(t);
            if (icon != null) {
                int s = Math.min(12, tabW - 2);
                draw(g, icon, x + (tabW - 1 - s) / 2, 2 + (TABS_H - 4 - s) / 2, s);
            }
        }
        List<EmojiRegistry.Emoji> list = shown();
        int top = gridTop();
        int gh = gridHeight(h);
        int rows = (list.size() + columns - 1) / columns;
        int visibleRows = Math.max(1, gh / TILE);
        maxRow = Math.max(0, rows - visibleRows);
        row = Math.max(0, Math.min(maxRow, row));
        int firstRow = row;
        int lastRow = Math.min(rows - 1, row + visibleRows - 1);
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = 0; c < columns; c++) {
                int i = r * columns + c;
                if (i >= list.size()) {
                    break;
                }
                int x = PAD + c * TILE;
                int y = top + (r - row) * TILE;
                boolean over = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
                if (over) {
                    hovered = list.get(i);
                    g.roundedRect(x, y, TILE, TILE, 2, 0x50FFFFFF);
                }
                draw(g, list.get(i), x + (TILE - ICON) / 2, y + (TILE - ICON) / 2, ICON);
            }
        }
        if (maxRow > 0) {
            int barH = Math.max(10, gh * visibleRows / rows);
            int barY = top + Math.round((gh - barH) * ((float) row / maxRow));
            g.fill(w - SCROLLBAR - 1, top, w - 1, top + gh, 0x30FFFFFF);
            g.fill(w - SCROLLBAR - 1, barY, w - 1, barY + barH, LumoraTheme.alpha(LumoraTheme.accent(), 0xE0));
        }
        if (list.isEmpty()) {
            g.text(tr.apply("panel.empty"), PAD + 2, top + 4, MUTED, false);
        }
        int fy = h - FOOTER_H + 1;
        g.fill(PAD, fy - 2, w - PAD, fy - 1, 0x30FFFFFF);
        String footer = hovered != null ? hovered.shortcode()
                : hoveredTab != null ? tr.apply("category." + hoveredTab) : tr.apply("category." + category);
        g.text(fit(g, footer, w - PAD * 2 - 2), PAD + 1, fy + 1, hovered != null ? TEXT : MUTED, false);
    }

    private EmojiRegistry.Emoji tabIcon(String tab) {
        if (RECENT.equals(tab)) {
            return registry.byName("clock3");
        }
        List<EmojiRegistry.Emoji> l = registry.category(tab);
        return l.isEmpty() ? null : l.get(0);
    }

    private void draw(HudGraphics g, EmojiRegistry.Emoji e, int x, int y, int size) {
        int cell = registry.cell();
        int sheet = registry.sheetSize();
        g.texture(registry.sheet(e), x, y, size, size, registry.u(e), registry.v(e), cell, cell, sheet, sheet);
    }

    private static String fit(HudGraphics g, String text, int width) {
        if (g.textWidth(text) <= width) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && g.textWidth(s + "…") > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (mouseY >= 0 && mouseY < TABS_H) {
            List<String> tabs = tabs();
            int tabW = Math.max(12, (w() - PAD * 2) / tabs.size());
            int i = (mouseX - PAD) / tabW;
            if (mouseX >= PAD && i >= 0 && i < tabs.size()) {
                select(tabs.get(i));
                return true;
            }
            return false;
        }
        EmojiRegistry.Emoji e = at(mouseX, mouseY);
        if (e != null) {
            pick.accept(e);
            return true;
        }
        return false;
    }

    /** The emoji under (x, y) with the current scroll position, or null. */
    EmojiRegistry.Emoji at(int x, int y) {
        int top = gridTop();
        int gh = gridHeight(h());
        if (x < PAD || x >= PAD + columns * TILE || y < top || y >= top + gh) {
            return null;
        }
        int c = (x - PAD) / TILE;
        if (y >= top + Math.max(1, gh / TILE) * TILE) {
            return null;
        }
        int r = (y - top) / TILE + row;
        int i = r * columns + c;
        List<EmojiRegistry.Emoji> list = shown();
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        int step = amount > 0 ? -2 : amount < 0 ? 2 : 0;
        row = Math.max(0, Math.min(maxRow, row + step * Math.max(1, (int) Math.round(Math.abs(amount)))));
        return true;
    }

    /** Scroll position in pixels (tests). */
    float scroll() {
        return row * TILE;
    }
}
