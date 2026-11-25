package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.cosmetics.CosmeticCategory;
import dev.lumora.core.cosmetics.CosmeticDefinition;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The cosmetics menu: categories, search, rarity filter, favourites, the list of cosmetics as cards and the live
 * preview on the right. Clicking a card adds it to (or removes it from) the preview, so combinations can be tried
 * without wearing anything; "Equip" wears the selected one, "Apply" wears the whole tried combination.
 */
public final class CosmeticsView implements AddonView {
    private static final String K = "lumora.cosmetics.";
    private static final int CARD_H = 48;
    private static final int GAP = 6;

    private final Cosmetics cosmetics;
    private final UiHost host;
    private final Ui.Regions regions = new Ui.Regions();
    private final PreviewPanel preview = new PreviewPanel();
    private final Ui.TextBox search = new Ui.TextBox(32);
    private CosmeticCategory category;
    private boolean favoritesOnly;
    private boolean equippedOnly;
    private Rarity rarity;
    private CosmeticDefinition selected;
    private int scroll;
    private int maxScroll;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;

    public CosmeticsView(Cosmetics cosmetics, UiHost host) {
        this.cosmetics = cosmetics;
        this.host = host;
        cosmetics.setPreview(cosmetics.state().equipped());
        cosmetics.previewEmote(null);
        preview.reset(20);
    }

    private String tr(String key, Object... args) {
        return host.tr(K + key, args);
    }

    static String name(UiHost host, CosmeticDefinition d) {
        return d.translated() ? host.tr(d.name()) : d.name();
    }

    static String description(UiHost host, CosmeticDefinition d) {
        return d.translated() ? host.tr(d.description()) : d.description();
    }

    List<CosmeticDefinition> visible() {
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        Set<String> equipped = cosmetics.state().equipped();
        List<CosmeticDefinition> out = new ArrayList<>();
        for (CosmeticDefinition d : cosmetics.registry().cosmetics()) {
            if (category != null && d.category() != category) {
                continue;
            }
            if (rarity != null && d.rarity() != rarity) {
                continue;
            }
            if (favoritesOnly && !cosmetics.state().isFavorite(d.id())) {
                continue;
            }
            if (equippedOnly && !equipped.contains(d.id())) {
                continue;
            }
            if (!q.isEmpty() && !name(host, d).toLowerCase(Locale.ROOT).contains(q) && !d.id().toLowerCase(Locale.ROOT).contains(q)
                    && !host.tr(K + "category." + d.category().id()).toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(d);
        }
        return out;
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        regions.clear();
        g.fill(0, 0, width, height, Ui.BACKDROP);
        int m = width < 520 ? 6 : 12;
        int x0 = m;
        int y0 = m;
        int pw = width - 2 * m;
        int ph = height - 2 * m;
        g.card(x0, y0, pw, ph, 8, Ui.PANEL, Ui.BORDER);

        // header: title, tabs, settings, close
        g.text(tr("title"), x0 + 10, y0 + 9, Ui.TEXT, false);
        int tx = x0 + 16 + g.textWidth(tr("title"));
        int tabW = Math.max(60, g.textWidth(tr("tab.emotes")) + 16);
        Ui.chip(g, regions, tx, y0 + 5, tabW, 16, tr("tab.cosmetics"), true, mx, my, () -> { });
        Ui.chip(g, regions, tx + tabW + 4, y0 + 5, tabW, 16, tr("tab.emotes"), false, mx, my, () -> {
            host.close(this);
            host.show(new EmotesView(cosmetics, host));
        });
        Ui.button(g, regions, x0 + pw - 24, y0 + 5, 18, 16, "✕", false, true, mx, my, () -> host.close(this));
        Ui.button(g, regions, x0 + pw - 46, y0 + 5, 18, 16, "⚙", false, true, mx, my, host::openSettings);

        int top = y0 + 28;
        int bodyH = ph - 34;
        int sb = Math.max(78, Math.min(112, pw / 6));
        int pv = Math.max(118, Math.min(210, pw * 3 / 10));
        sidebar(g, x0 + 6, top, sb, bodyH, mx, my);

        // center: search + rarity + grid
        int cx = x0 + 6 + sb + 6;
        int cw = pw - sb - pv - 24;
        int rw = Math.min(90, cw / 3);
        search.draw(g, cx, top, cw - rw - 4, 16, tr("search"), true);
        String rLabel = rarity == null ? tr("rarity.all") : host.tr(K + "rarity." + rarity.id());
        Ui.button(g, regions, cx + cw - rw, top, rw, 16, rLabel, false, true, mx, my, this::cycleRarity);
        grid(g, cx, top + 22, cw, bodyH - 22, mx, my);

        // right: preview + info
        int px = x0 + pw - pv - 6;
        int infoH = Math.min(118, bodyH / 2);
        int optH = selected == null ? 0 : selected.variants().isEmpty() ? 22 : 40;
        int previewH = bodyH - infoH - 4 - (optH > 0 ? optH + 4 : 0);
        preview.draw(g, host, px, top, pv, previewH, mx, my);
        if (optH > 0) {
            options(g, px, top + previewH + 4, pv, optH, mx, my);
        }
        info(g, px, top + bodyH - infoH, pv, infoH, mx, my);
    }

    private void sidebar(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        int row = 16;
        Ui.chip(g, regions, x, y, w, row, tr("category.all"), category == null && !favoritesOnly && !equippedOnly, mx, my, () -> {
            category = null;
            favoritesOnly = false;
            equippedOnly = false;
            scroll = 0;
        });
        int yy = y + row + 2;
        for (CosmeticCategory c : CosmeticCategory.values()) {
            CosmeticCategory cat = c;
            Ui.chip(g, regions, x, yy, w, row, tr("category." + c.id()), category == c, mx, my, () -> {
                category = category == cat ? null : cat;
                scroll = 0;
            });
            yy += row + 2;
        }
        yy += 4;
        g.fill(x + 4, yy, x + w - 4, yy + 1, Ui.BORDER);
        yy += 6;
        Ui.chip(g, regions, x, yy, w, row, "★ " + tr("filter.favorites"), favoritesOnly, mx, my, () -> {
            favoritesOnly = !favoritesOnly;
            scroll = 0;
        });
        yy += row + 2;
        int count = cosmetics.state().equipped().size();
        Ui.chip(g, regions, x, yy, w, row, "✔ " + tr("filter.equipped", count), equippedOnly, mx, my, () -> {
            equippedOnly = !equippedOnly;
            scroll = 0;
        });
    }

    private void cycleRarity() {
        Rarity[] all = Rarity.values();
        rarity = rarity == null ? all[0] : rarity.ordinal() + 1 < all.length ? all[rarity.ordinal() + 1] : null;
        scroll = 0;
    }

    private void grid(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        gridX = x;
        gridY = y;
        gridW = w;
        gridH = h;
        List<CosmeticDefinition> list = visible();
        int cols = Math.max(1, (w + GAP) / (96 + GAP));
        int cardW = (w - (cols - 1) * GAP) / cols;
        int rows = (list.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - h);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (list.isEmpty()) {
            g.centeredText(tr("empty"), x + w / 2, y + 30, Ui.MUTED, false);
            return;
        }
        Set<String> inPreview = cosmetics.previewSet();
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + GAP);
            int cy = y + row * (CARD_H + GAP) - scroll;
            if (cy + CARD_H < y || cy > y + h) {
                continue;
            }
            card(g, list.get(i), cx, cy, cardW, inPreview.contains(list.get(i).id()), mx, my, my >= y && my < y + h);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, h * h / (h + maxScroll));
            int barY = y + (h - barH) * scroll / maxScroll;
            g.fill(x + w + 2, barY, x + w + 4, barY + barH, Ui.BORDER);
        }
    }

    private void card(HudGraphics g, CosmeticDefinition d, int x, int y, int w, boolean inPreview, int mx, int my, boolean inside) {
        boolean hot = inside && Ui.hover(mx, my, x, y, w, CARD_H);
        boolean sel = selected == d;
        g.card(x, y, w, CARD_H, 5, hot ? Ui.ROW_HOVER : Ui.CARD, sel ? Ui.ACCENT : inPreview ? Ui.ACCENT_DIM : Ui.BORDER);
        g.fill(x + 1, y + 4, x + 3, y + CARD_H - 4, d.rarity().color);
        g.text(Ui.fit(g, name(host, d), w - 22), x + 8, y + 6, Ui.TEXT, false);
        String kind = host.tr(K + "category." + d.category().id());
        if (d.animated()) {
            kind += " · " + tr("animated");
        }
        g.text(Ui.fit(g, kind, w - 12), x + 8, y + 18, Ui.MUTED, false);
        g.text(Ui.fit(g, host.tr(K + "rarity." + d.rarity().id()), w - 30), x + 8, y + 31, d.rarity().color, false);
        boolean fav = cosmetics.state().isFavorite(d.id());
        Ui.star(g, x + w - 12, y + 6, fav);
        if (cosmetics.state().isEquipped(d.id())) {
            Ui.check(g, x + w - 13, y + CARD_H - 12, Ui.GOOD);
        } else if (inPreview) {
            g.fill(x + w - 11, y + CARD_H - 10, x + w - 7, y + CARD_H - 6, Ui.ACCENT);
        }
        if (inside) {
            regions.add(x, y, w, CARD_H, () -> select(d), () -> cosmetics.state().toggleFavorite(d.id()));
            regions.add(x + w - 16, y + 2, 14, 12, () -> cosmetics.state().toggleFavorite(d.id()));
        }
    }

    private void select(CosmeticDefinition d) {
        selected = d;
        cosmetics.togglePreview(d);
        preview.face(d.previewYaw());
    }

    // --- variant and offset of the selected cosmetic (applied at once, also to what is worn and synced)

    private int dragAxis = -1;
    /** Slider rectangles per axis: x, y, w, h. */
    private final int[][] sliders = new int[3][4];

    private void options(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.card(x, y, w, h, 6, Ui.CARD, Ui.BORDER);
        CosmeticDefinition d = selected;
        dev.lumora.core.cosmetics.CosmeticsState.Options o = cosmetics.state().options(d.id());
        int ry = y + 4;
        int labelW = Math.max(g.textWidth(tr("option.variant")), g.textWidth(tr("option.offset"))) + 8;
        if (!d.variants().isEmpty()) {
            CosmeticDefinition.Variant v = d.variant(o.variant());
            int idx = d.variants().indexOf(v);
            g.text(tr("option.variant"), x + 6, ry + 4, Ui.MUTED, false);
            int bx = x + 6 + labelW;
            int bw = w - 12 - labelW;
            Ui.button(g, regions, bx, ry, 14, 14, "<", false, d.variants().size() > 1, mx, my,
                    () -> setVariant(d, d.variants().get((idx + d.variants().size() - 1) % d.variants().size())));
            Ui.button(g, regions, bx + bw - 14, ry, 14, 14, ">", false, d.variants().size() > 1, mx, my,
                    () -> setVariant(d, d.variants().get((idx + 1) % d.variants().size())));
            String name = d.translated() ? host.tr(v.name()) : v.name();
            g.centeredText(Ui.fit(g, name, bw - 32), bx + bw / 2, ry + 4, Ui.TEXT, false);
            ry += 18;
        }
        g.text(tr("option.offset"), x + 6, ry + 4, Ui.MUTED, false);
        int sx = x + 6 + labelW;
        int sw = (w - 12 - labelW - 8) / 3;
        float[] vals = {o.x(), o.y(), o.z()};
        String[] axes = {"X", "Y", "Z"};
        for (int a = 0; a < 3; a++) {
            int bx = sx + a * (sw + 4);
            sliders[a] = new int[] {bx, ry, sw, 14};
            boolean hot = Ui.hover(mx, my, bx, ry, sw, 14) || dragAxis == a;
            g.card(bx, ry, sw, 14, 3, Ui.FIELD, hot ? Ui.ACCENT : Ui.BORDER);
            float max = dev.lumora.core.cosmetics.CosmeticsState.MAX_OFFSET;
            int kx = bx + 2 + Math.round((vals[a] + max) / (2 * max) * (sw - 6));
            g.fill(kx, ry + 2, kx + 2, ry + 12, vals[a] == 0 ? Ui.DIM : Ui.ACCENT);
            String text = axes[a] + ": " + (vals[a] == Math.round(vals[a]) ? Integer.toString(Math.round(vals[a])) : Float.toString(vals[a]));
            g.centeredText(Ui.fit(g, text, sw - 4), bx + sw / 2, ry + 3, Ui.TEXT, false);
        }
    }

    private void setVariant(CosmeticDefinition d, CosmeticDefinition.Variant v) {
        dev.lumora.core.cosmetics.CosmeticsState.Options o = cosmetics.state().options(d.id());
        cosmetics.state().setOptions(d.id(), o.withVariant(v.id().equals(d.variants().get(0).id()) ? null : v.id()));
    }

    /** Left click on a slider sets and drags the value, right click resets it to 0. */
    private boolean sliderPress(double x, double y, int button) {
        if (selected == null) {
            return false;
        }
        for (int a = 0; a < 3; a++) {
            int[] s = sliders[a];
            if (s[2] > 0 && x >= s[0] && x < s[0] + s[2] && y >= s[1] && y < s[1] + s[3]) {
                if (button == 1) {
                    setAxis(a, 0);
                } else if (button == 0) {
                    dragAxis = a;
                    sliderSet(a, x);
                }
                return true;
            }
        }
        return false;
    }

    private void sliderSet(int axis, double x) {
        int[] s = sliders[axis];
        float max = dev.lumora.core.cosmetics.CosmeticsState.MAX_OFFSET;
        double t = Math.max(0, Math.min(1, (x - s[0] - 2) / Math.max(1, s[2] - 6)));
        setAxis(axis, (float) (t * 2 * max - max));
    }

    private void setAxis(int axis, float value) {
        if (selected == null) {
            return;
        }
        dev.lumora.core.cosmetics.CosmeticsState.Options o = cosmetics.state().options(selected.id());
        float[] v = {o.x(), o.y(), o.z()};
        v[axis] = value;
        if (dragAxis >= 0) {
            cosmetics.state().setOptionsQuiet(selected.id(), o.withOffset(v[0], v[1], v[2]));
        } else {
            cosmetics.state().setOptions(selected.id(), o.withOffset(v[0], v[1], v[2]));
        }
    }

    private void info(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.card(x, y, w, h, 6, Ui.CARD, Ui.BORDER);
        int ty = y + 6;
        // text only above the buttons (one row without a selection, two with one)
        int textBottom = y + h - (selected == null ? 1 : 2) * (16 + 4) - 6;
        int room = Math.max(1, (textBottom - ty) / (g.lineHeight() + 1));
        if (selected == null) {
            for (String line : Ui.wrap(g, tr("select_hint"), w - 12, Math.min(3, room))) {
                g.text(line, x + 6, ty, Ui.MUTED, false);
                ty += g.lineHeight() + 1;
            }
        } else {
            g.text(Ui.fit(g, name(host, selected), w - 12), x + 6, ty, selected.rarity().color, false);
            ty += g.lineHeight() + 2;
            for (String line : Ui.wrap(g, description(host, selected), w - 12, Math.max(0, Math.min(3, room - 1)))) {
                g.text(line, x + 6, ty, Ui.MUTED, false);
                ty += g.lineHeight() + 1;
            }
        }
        int bh = 16;
        int by = y + h - bh - 6;
        int bw = w - 12;
        Set<String> equipped = cosmetics.state().equipped();
        Set<String> tried = cosmetics.previewSet();
        boolean changed = !equipped.equals(tried);
        Ui.button(g, regions, x + 6, by, bw / 2 - 2, bh, tr("apply"), false, changed, mx, my, () -> cosmetics.setEquipped(tried));
        Ui.button(g, regions, x + 6 + bw / 2 + 2, by, bw - bw / 2 - 2, bh, tr("reset"), false, changed, mx, my,
                () -> cosmetics.setPreview(cosmetics.state().equipped()));
        by -= bh + 4;
        if (selected != null) {
            CosmeticDefinition d = selected;
            boolean worn = cosmetics.state().isEquipped(d.id());
            if (!d.unlocked()) {
                Ui.button(g, regions, x + 6, by, bw, bh, tr("locked"), false, false, mx, my, null);
            } else if (worn) {
                Ui.button(g, regions, x + 6, by, bw, bh, tr("unequip"), false, true, mx, my, () -> {
                    cosmetics.unequip(d.id());
                    Set<String> next = cosmetics.previewSet();
                    next.remove(d.id());
                    cosmetics.setPreview(next);
                });
            } else {
                Ui.button(g, regions, x + 6, by, bw, bh, tr("equip"), true, true, mx, my, () -> {
                    cosmetics.equip(d);
                    if (!cosmetics.previewSet().contains(d.id())) {
                        cosmetics.togglePreview(d);
                    }
                });
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (preview.press(x, y, button)) {
            return true;
        }
        if (sliderPress(x, y, button)) {
            return true;
        }
        return regions.click(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button) {
        if (dragAxis >= 0) {
            sliderSet(dragAxis, x);
            return true;
        }
        return preview.drag(x, y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (dragAxis >= 0) {
            dragAxis = -1;
            cosmetics.state().flush();
            return true;
        }
        return preview.release();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (preview.scroll(x, y, delta)) {
            return true;
        }
        if (x >= gridX && x < gridX + gridW && y >= gridY && y < gridY + gridH) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (delta * 24)));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key == Keys.ESCAPE) {
            return false;
        }
        if (search.key(key, ctrl, host.clipboard())) {
            scroll = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch) {
        int before = search.text.length();
        search.append(String.valueOf(ch));
        if (search.text.length() != before) {
            scroll = 0;
        }
        return true;
    }

    @Override
    public void removed() {
        cosmetics.previewEmote(null);
    }
}
