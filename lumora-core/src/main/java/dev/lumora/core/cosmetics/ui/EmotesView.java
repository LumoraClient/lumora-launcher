package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.EmoteCategory;
import dev.lumora.core.cosmetics.EmoteDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The emote menu: favourites and categories, search, all emotes as cards, a looping preview of the selected emote on
 * the own player (local only), play and stop, and the slots of the emote wheel (choose an emote, then click a slot;
 * right click empties a slot).
 */
public final class EmotesView implements AddonView {
    private static final String K = "lumora.cosmetics.";
    private static final int CARD_H = 36;
    private static final int GAP = 6;
    /** Pause before the preview emote starts again (seconds). */
    private static final long REPEAT_PAUSE_MS = 700;

    private final Cosmetics cosmetics;
    private final UiHost host;
    private final Ui.Regions regions = new Ui.Regions();
    private final PreviewPanel preview = new PreviewPanel();
    private final Ui.TextBox search = new Ui.TextBox(32);
    private EmoteCategory category;
    private boolean favoritesOnly;
    private EmoteDefinition selected;
    private long previewEnded = -1;
    private int scroll;
    private int maxScroll;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;

    public EmotesView(Cosmetics cosmetics, UiHost host) {
        this.cosmetics = cosmetics;
        this.host = host;
        cosmetics.setPreview(cosmetics.state().equipped());
        cosmetics.previewEmote(null);
        preview.reset(25);
    }

    private String tr(String key, Object... args) {
        return host.tr(K + key, args);
    }

    static String name(UiHost host, EmoteDefinition e) {
        return e.translated() ? host.tr(e.name()) : e.name();
    }

    static String description(UiHost host, EmoteDefinition e) {
        return e.translated() ? host.tr(e.description()) : e.description();
    }

    List<EmoteDefinition> visible() {
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        List<EmoteDefinition> out = new ArrayList<>();
        for (EmoteDefinition e : cosmetics.registry().emotes()) {
            if (category != null && e.category() != category) {
                continue;
            }
            if (favoritesOnly && !cosmetics.state().isEmoteFavorite(e.id())) {
                continue;
            }
            if (!q.isEmpty() && !name(host, e).toLowerCase(Locale.ROOT).contains(q) && !e.id().contains(q)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        regions.clear();
        loopPreview();
        g.fill(0, 0, width, height, Ui.BACKDROP);
        int m = width < 520 ? 6 : 12;
        int x0 = m;
        int y0 = m;
        int pw = width - 2 * m;
        int ph = height - 2 * m;
        g.card(x0, y0, pw, ph, 8, Ui.PANEL, Ui.BORDER);

        g.text(tr("title"), x0 + 10, y0 + 9, Ui.TEXT, false);
        int tx = x0 + 16 + g.textWidth(tr("title"));
        int tabW = Math.max(60, g.textWidth(tr("tab.emotes")) + 16);
        Ui.chip(g, regions, tx, y0 + 5, tabW, 16, tr("tab.cosmetics"), false, mx, my, () -> {
            host.close(this);
            host.show(new CosmeticsView(cosmetics, host));
        });
        Ui.chip(g, regions, tx + tabW + 4, y0 + 5, tabW, 16, tr("tab.emotes"), true, mx, my, () -> { });
        Ui.button(g, regions, x0 + pw - 24, y0 + 5, 18, 16, "✕", false, true, mx, my, () -> host.close(this));
        Ui.button(g, regions, x0 + pw - 46, y0 + 5, 18, 16, "⚙", false, true, mx, my, host::openSettings);

        int top = y0 + 28;
        int wheelH = 40;
        int bodyH = ph - 34 - wheelH - 4;
        int sb = Math.max(78, Math.min(112, pw / 6));
        int pv = Math.max(118, Math.min(210, pw * 3 / 10));
        sidebar(g, x0 + 6, top, sb, bodyH, mx, my);

        int cx = x0 + 6 + sb + 6;
        int cw = pw - sb - pv - 24;
        search.draw(g, cx, top, cw, 16, tr("search_emotes"), true);
        grid(g, cx, top + 22, cw, bodyH - 22, mx, my);

        int px = x0 + pw - pv - 6;
        int infoH = Math.min(96, bodyH / 2);
        preview.draw(g, host, px, top, pv, bodyH - infoH - 4, mx, my);
        info(g, px, top + bodyH - infoH, pv, infoH, mx, my);

        wheelSlots(g, x0 + 6, top + bodyH + 4, pw - 12, wheelH, mx, my);
    }

    /** Plays the selected emote again in the preview after it ended (looping preview). */
    private void loopPreview() {
        if (selected == null) {
            return;
        }
        if (cosmetics.previewEmote() == null || !previewRunning()) {
            long now = System.currentTimeMillis();
            if (previewEnded < 0) {
                previewEnded = now;
            } else if (now - previewEnded > REPEAT_PAUSE_MS) {
                cosmetics.previewEmote(selected);
                previewEnded = -1;
            }
        }
    }

    private boolean previewRunning() {
        return cosmetics.previewEmoteRunning();
    }

    private void sidebar(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        int row = 15;
        Ui.chip(g, regions, x, y, w, row, "★ " + tr("filter.favorites"), favoritesOnly, mx, my, () -> {
            favoritesOnly = !favoritesOnly;
            category = null;
            scroll = 0;
        });
        int yy = y + row + 2;
        Ui.chip(g, regions, x, yy, w, row, tr("category.all"), category == null && !favoritesOnly, mx, my, () -> {
            category = null;
            favoritesOnly = false;
            scroll = 0;
        });
        yy += row + 2;
        for (EmoteCategory c : EmoteCategory.values()) {
            EmoteCategory cat = c;
            if (yy + row > y + h) {
                break;
            }
            Ui.chip(g, regions, x, yy, w, row, tr("emote_category." + c.id()), category == c, mx, my, () -> {
                category = category == cat ? null : cat;
                favoritesOnly = false;
                scroll = 0;
            });
            yy += row + 2;
        }
    }

    private void grid(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        gridX = x;
        gridY = y;
        gridW = w;
        gridH = h;
        List<EmoteDefinition> list = visible();
        int cols = Math.max(1, (w + GAP) / (90 + GAP));
        int cardW = (w - (cols - 1) * GAP) / cols;
        int rows = (list.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - h);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (list.isEmpty()) {
            g.centeredText(tr(favoritesOnly ? "empty_favorites" : "empty"), x + w / 2, y + 30, Ui.MUTED, false);
            return;
        }
        EmoteDefinition playing = cosmetics.currentEmote();
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < list.size(); i++) {
            int cx = x + (i % cols) * (cardW + GAP);
            int cy = y + (i / cols) * (CARD_H + GAP) - scroll;
            if (cy + CARD_H < y || cy > y + h) {
                continue;
            }
            EmoteDefinition e = list.get(i);
            boolean inside = my >= y && my < y + h;
            boolean hot = inside && Ui.hover(mx, my, cx, cy, cardW, CARD_H);
            g.card(cx, cy, cardW, CARD_H, 5, hot ? Ui.ROW_HOVER : Ui.CARD, selected == e ? Ui.ACCENT : playing == e ? Ui.GOOD : Ui.BORDER);
            g.fill(cx + 1, cy + 4, cx + 3, cy + CARD_H - 4, e.rarity().color);
            g.text(Ui.fit(g, name(host, e), cardW - 22), cx + 8, cy + 6, Ui.TEXT, false);
            String sub = tr("emote_category." + e.category().id());
            if (playing == e) {
                sub = tr("playing");
            }
            g.text(Ui.fit(g, sub, cardW - 12), cx + 8, cy + 19, playing == e ? Ui.GOOD : Ui.MUTED, false);
            Ui.star(g, cx + cardW - 12, cy + 6, cosmetics.state().isEmoteFavorite(e.id()));
            if (inside) {
                regions.add(cx, cy, cardW, CARD_H, () -> select(e), () -> cosmetics.state().toggleEmoteFavorite(e.id()));
                regions.add(cx + cardW - 16, cy + 2, 14, 12, () -> cosmetics.state().toggleEmoteFavorite(e.id()));
            }
        }
        g.disableScissor();
    }

    private void select(EmoteDefinition e) {
        selected = e;
        previewEnded = -1;
        cosmetics.previewEmote(e);
    }

    private void info(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.card(x, y, w, h, 6, Ui.CARD, Ui.BORDER);
        int ty = y + 6;
        // text only above the two buttons (16 px + gaps each)
        int textBottom = y + h - 2 * (16 + 4) - 6;
        int room = Math.max(1, (textBottom - ty) / (g.lineHeight() + 1));
        if (selected == null) {
            for (String line : Ui.wrap(g, tr("select_emote_hint"), w - 12, Math.min(3, room))) {
                g.text(line, x + 6, ty, Ui.MUTED, false);
                ty += g.lineHeight() + 1;
            }
        } else {
            g.text(Ui.fit(g, name(host, selected), w - 12), x + 6, ty, selected.rarity().color, false);
            ty += g.lineHeight() + 2;
            for (String line : Ui.wrap(g, description(host, selected), w - 12, Math.max(0, Math.min(2, room - 1)))) {
                g.text(line, x + 6, ty, Ui.MUTED, false);
                ty += g.lineHeight() + 1;
            }
        }
        int bh = 16;
        int bw = w - 12;
        int by = y + h - bh - 6;
        boolean inWorld = host.inWorld();
        EmoteDefinition playing = cosmetics.currentEmote();
        Ui.button(g, regions, x + 6, by, bw, bh, tr("stop"), false, playing != null, mx, my, cosmetics::stopEmote);
        by -= bh + 4;
        EmoteDefinition sel = selected;
        Ui.button(g, regions, x + 6, by, bw, bh, inWorld ? tr("play") : tr("play_no_world"), true, sel != null && inWorld && sel.unlocked(),
                mx, my, () -> {
                    cosmetics.playEmote(sel);
                    host.close(this);
                });
    }

    private void wheelSlots(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.card(x, y, w, h, 6, Ui.CARD, Ui.BORDER);
        g.text(tr("wheel.title"), x + 6, y + 5, Ui.TEXT, false);
        String hint = selected == null ? tr("wheel.hint_select") : tr("wheel.hint_assign", name(host, selected));
        g.text(Ui.fit(g, hint, w - 18 - g.textWidth(tr("wheel.title"))), x + 12 + g.textWidth(tr("wheel.title")), y + 5, Ui.MUTED, false);
        int n = host.wheelSlots();
        int sx = x + 6;
        int sy = y + 17;
        int sw = (w - 12 - (n - 1) * 4) / n;
        for (int i = 0; i < n; i++) {
            int slot = i;
            int bx = sx + i * (sw + 4);
            EmoteDefinition e = cosmetics.registry().emote(cosmetics.state().slot(i));
            boolean hot = Ui.hover(mx, my, bx, sy, sw, 18);
            g.card(bx, sy, sw, 18, 4, hot ? Ui.ROW_HOVER : Ui.ROW, Ui.BORDER);
            String label = (i + 1) + " " + (e == null ? "–" : name(host, e));
            g.text(Ui.fit(g, label, sw - 8), bx + 4, sy + 5, e == null ? Ui.DIM : Ui.TEXT, false);
            regions.add(bx, sy, sw, 18, () -> {
                if (selected != null) {
                    cosmetics.state().setSlot(slot, selected.id());
                } else if (e != null) {
                    select(e);
                }
            }, () -> cosmetics.state().setSlot(slot, null));
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (preview.press(x, y, button)) {
            return true;
        }
        return regions.click(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button) {
        return preview.drag(x, y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
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
        if (key == Keys.ENTER && selected != null && host.inWorld()) {
            cosmetics.playEmote(selected);
            host.close(this);
            return true;
        }
        if (search.key(key, ctrl, host.clipboard())) {
            scroll = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch) {
        search.append(String.valueOf(ch));
        scroll = 0;
        return true;
    }

    @Override
    public void removed() {
        cosmetics.previewEmote(null);
    }
}
