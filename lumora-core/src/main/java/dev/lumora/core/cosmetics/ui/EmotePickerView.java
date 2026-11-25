package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.CosmeticsState;
import dev.lumora.core.cosmetics.EmoteDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Chooses the emote for one wheel slot: a small wheel on the left marks the slot (with the page), a searchable list of
 * all emotes on the right; a click puts the emote into the slot and goes back to the wheel editor. "Empty" clears the
 * slot, "Cancel" (or Esc) goes back without a change.
 */
public final class EmotePickerView implements AddonView {
    private static final String K = "lumora.cosmetics.";
    private static final int ROW = 18;

    private final Cosmetics cosmetics;
    private final UiHost host;
    private final int page;
    private final int slot;
    private final Wheel wheel = new Wheel();
    private final Ui.Regions regions = new Ui.Regions();
    private final Ui.TextBox search = new Ui.TextBox(32);
    private int scroll;
    private int maxScroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public EmotePickerView(Cosmetics cosmetics, UiHost host, int page, int slot) {
        this.cosmetics = cosmetics;
        this.host = host;
        this.page = page;
        this.slot = slot;
    }

    private String tr(String key, Object... args) {
        return host.tr(K + key, args);
    }

    private int index() {
        return CosmeticsState.slotIndex(page, slot);
    }

    List<EmoteDefinition> visible() {
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        List<EmoteDefinition> out = new ArrayList<>();
        for (EmoteDefinition e : cosmetics.registry().emotes()) {
            if (e.unlocked() && (q.isEmpty() || EmotesView.name(host, e).toLowerCase(Locale.ROOT).contains(q))) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparing(e -> EmotesView.name(host, e).toLowerCase(Locale.ROOT)));
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
        int leftW = Math.max(120, Math.min(200, pw / 3));
        // left: what is being swapped, the small wheel, page, buttons
        EmoteDefinition current = cosmetics.registry().emote(cosmetics.state().slot(index()));
        String title = current == null ? tr("picker.title_empty", slot + 1) : tr("picker.title", EmotesView.name(host, current));
        int ty = y0 + 8;
        for (String line : Ui.wrap(g, title, leftW - 12, 2)) {
            g.centeredText(line, x0 + 6 + leftW / 2, ty, Ui.TEXT, false);
            ty += g.lineHeight() + 1;
        }
        int n = Math.max(4, Math.min(CosmeticsState.MAX_SLOTS, host.wheelSlots()));
        int r = Math.max(30, Math.min(leftW / 2 - 8, (ph - 90) / 2));
        wheel.layout(x0 + 6 + leftW / 2, ty + 6 + r, r, 0.3f, n);
        wheel.draw(g, s -> s == slot ? 0xFF0B0F15 : 0xFF232A38, 0xFF3A4458);
        g.centeredText(tr("picker.page", page + 1), wheel.cx(), wheel.cy() + r + 6, Ui.TEXT, false);
        int bw = leftW - 12;
        Ui.button(g, regions, x0 + 6, y0 + ph - 46, bw, 16, tr("picker.clear"), false, current != null, mx, my, () -> {
            cosmetics.state().setSlot(index(), null);
            back();
        });
        Ui.button(g, regions, x0 + 6, y0 + ph - 24, bw, 16, tr("picker.cancel"), false, true, mx, my, this::back);
        // right: search and the list
        int rx = x0 + leftW + 12;
        int rw = pw - leftW - 18;
        search.draw(g, rx, y0 + 8, Math.min(180, rw), 16, tr("search_emotes"), true);
        listX = rx;
        listY = y0 + 30;
        listW = rw - 6;
        listH = ph - 36;
        List<EmoteDefinition> list = visible();
        maxScroll = Math.max(0, list.size() * ROW - listH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (list.isEmpty()) {
            g.centeredText(tr("empty"), rx + rw / 2, listY + 20, Ui.MUTED, false);
        }
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < list.size(); i++) {
            int y = listY + i * ROW - scroll;
            if (y + ROW < listY || y > listY + listH) {
                continue;
            }
            EmoteDefinition e = list.get(i);
            boolean inside = my >= listY && my < listY + listH;
            boolean hot = inside && Ui.hover(mx, my, listX, y, listW, ROW - 2);
            g.card(listX, y, listW, ROW - 2, 3, hot ? Ui.ROW_HOVER : Ui.ROW, e == current ? Ui.ACCENT : Ui.BORDER);
            g.text(Ui.fit(g, EmotesView.name(host, e), listW - 90), listX + 6, y + 4, Ui.TEXT, false);
            String cat = tr("emote_category." + e.category().id());
            g.rightText(Ui.fit(g, cat, 80), listX + listW - 6, y + 4, Ui.DIM, false);
            if (inside) {
                regions.add(listX, y, listW, ROW - 2, () -> {
                    cosmetics.state().setSlot(index(), e.id());
                    back();
                });
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, listH * listH / (listH + maxScroll));
            int barY = listY + (listH - barH) * scroll / maxScroll;
            g.fill(listX + listW + 2, barY, listX + listW + 4, barY + barH, Ui.BORDER);
        }
    }

    private void back() {
        host.close(this);
        host.show(new EmoteWheelView(cosmetics, host, true, page));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return regions.click(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= listX && x < listX + listW && y >= listY && y < listY + listH) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (delta * 24)));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key == Keys.ESCAPE) {
            back();
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
}
