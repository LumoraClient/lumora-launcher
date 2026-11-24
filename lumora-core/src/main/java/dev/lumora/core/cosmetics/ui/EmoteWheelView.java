package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.EmoteDefinition;

import java.util.Arrays;

/**
 * The emote wheel: a ring of 4 to 10 slots around the screen centre. Pointing at a slot highlights it, a click plays
 * the emote and closes the wheel (number keys 1-9 and 0 too); the centre stops a running emote. Right click or Esc
 * closes it without playing. It only stays open until a choice is made, so it never blocks the controls for long.
 * <p>
 * The ring is drawn from horizontal runs of one slot each (HudGraphics has no polygons); the runs are computed once
 * per size and slot count.
 */
public final class EmoteWheelView implements AddonView {
    private static final String K = "lumora.cosmetics.";
    private static final int FILL = 0xB01A1F2B;
    private static final int FILL_EMPTY = 0x80151922;
    private static final int HOVER = 0xE03D7BF0;
    private static final int LINE = 0x70D8E2F5;

    private final Cosmetics cosmetics;
    private final UiHost host;
    private int cx;
    private int cy;
    private int outer;
    private int inner;
    private int slots;
    /** Runs: y, x1, x2, slot (-1 = separator line), four ints each. */
    private int[] runs = new int[0];
    private int runCount;
    private int hovered = -1;

    public EmoteWheelView(Cosmetics cosmetics, UiHost host) {
        this.cosmetics = cosmetics;
        this.host = host;
    }

    private String tr(String key, Object... args) {
        return host.tr(K + key, args);
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        int n = Math.max(4, Math.min(10, host.wheelSlots()));
        int r = Math.max(48, Math.min(112, Math.min(width, height) / 2 - 24));
        int ccx = width / 2;
        int ccy = height / 2;
        if (r != outer || n != slots || ccx != cx || ccy != cy) {
            cx = ccx;
            cy = ccy;
            outer = r;
            inner = Math.round(r * 0.38f);
            slots = n;
            buildRuns();
        }
        hovered = slotAt(mx, my);
        EmoteDefinition[] emotes = new EmoteDefinition[slots];
        for (int i = 0; i < slots; i++) {
            emotes[i] = cosmetics.registry().emote(cosmetics.state().slot(i));
        }
        for (int i = 0; i < runCount; i++) {
            int o = i * 4;
            int slot = runs[o + 3];
            int color = slot < 0 ? LINE : slot == hovered ? HOVER : emotes[slot] == null ? FILL_EMPTY : FILL;
            g.fill(runs[o + 1], runs[o], runs[o + 2] + 1, runs[o] + 1, color);
        }
        // labels
        double step = 2 * Math.PI / slots;
        int labelR = (inner + outer) / 2;
        int labelW = (int) Math.min(78, 2 * labelR * Math.sin(step / 2) - 6);
        for (int i = 0; i < slots; i++) {
            double a = -Math.PI / 2 + i * step;
            int lx = cx + (int) Math.round(Math.cos(a) * labelR);
            int ly = cy + (int) Math.round(Math.sin(a) * labelR);
            String num = Integer.toString((i + 1) % 10);
            g.centeredText(num, lx, ly - 11, i == hovered ? 0xFFFFFFFF : Ui.DIM, false);
            String label = emotes[i] == null ? tr("wheel.empty") : EmotesView.name(host, emotes[i]);
            g.centeredText(Ui.fit(g, label, Math.max(20, labelW)), lx, ly - 1, emotes[i] == null ? Ui.DIM : Ui.TEXT, true);
        }
        // centre
        EmoteDefinition playing = cosmetics.currentEmote();
        String center;
        int centerColor = Ui.TEXT;
        if (hovered >= 0) {
            center = emotes[hovered] == null ? tr("wheel.assign") : EmotesView.name(host, emotes[hovered]);
        } else if (playing != null && centerHover(mx, my)) {
            center = tr("wheel.stop");
            centerColor = 0xFFFF8A8A;
        } else if (playing != null) {
            center = EmotesView.name(host, playing);
            centerColor = Ui.GOOD;
        } else {
            center = tr("wheel.center");
            centerColor = Ui.MUTED;
        }
        g.centeredText(Ui.fit(g, center, inner * 2 - 8), cx, cy - 4, centerColor, true);
        g.centeredText(tr("wheel.help"), cx, cy + outer + 10, Ui.MUTED, true);
    }

    private boolean centerHover(int mx, int my) {
        double dx = mx + 0.5 - cx;
        double dy = my + 0.5 - cy;
        return dx * dx + dy * dy < inner * inner;
    }

    /** Slot under the point, -1 if none. */
    int slotAt(double mx, double my) {
        double dx = mx + 0.5 - cx;
        double dy = my + 0.5 - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < inner || d > outer + 6) {
            return -1;
        }
        return slotOfAngle(Math.atan2(dy, dx));
    }

    private int slotOfAngle(double angle) {
        double step = 2 * Math.PI / slots;
        double rel = angle + Math.PI / 2 + step / 2;
        rel = ((rel % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return Math.min(slots - 1, (int) (rel / step));
    }

    private void buildRuns() {
        int[] out = new int[1024];
        int count = 0;
        double step = 2 * Math.PI / slots;
        for (int y = -outer; y <= outer; y++) {
            int runStart = Integer.MIN_VALUE;
            int runSlot = -2;
            for (int x = -outer; x <= outer + 1; x++) {
                int slot = -2;
                if (x <= outer) {
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double d = Math.sqrt(px * px + py * py);
                    if (d >= inner && d <= outer) {
                        double angle = Math.atan2(py, px);
                        slot = slotOfAngle(angle);
                        // distance to the nearest slot border in pixels
                        double rel = angle + Math.PI / 2 + step / 2;
                        rel = ((rel % step) + step) % step;
                        double edge = Math.min(rel, step - rel) * d;
                        if (edge < 0.7 || d - inner < 0.8 || outer - d < 0.8) {
                            slot = -1;
                        }
                    }
                }
                if (slot != runSlot) {
                    if (runSlot != -2) {
                        if (count + 4 > out.length) {
                            out = Arrays.copyOf(out, out.length * 2);
                        }
                        out[count++] = cy + y;
                        out[count++] = cx + runStart;
                        out[count++] = cx + x - 1;
                        out[count++] = runSlot;
                    }
                    runStart = x;
                    runSlot = slot;
                }
            }
        }
        runs = out;
        runCount = count / 4;
    }

    private void choose(int slot) {
        if (slot < 0 || slot >= slots) {
            return;
        }
        EmoteDefinition e = cosmetics.registry().emote(cosmetics.state().slot(slot));
        host.close(this);
        if (e == null) {
            host.show(new EmotesView(cosmetics, host));
        } else {
            cosmetics.playEmote(e);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) {
            host.close(this);
            return true;
        }
        if (button != 0) {
            return false;
        }
        int slot = slotAt(x, y);
        if (slot >= 0) {
            choose(slot);
            return true;
        }
        if (centerHover((int) x, (int) y) && cosmetics.currentEmote() != null) {
            cosmetics.stopEmote();
        }
        host.close(this);
        return true;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key >= Keys.NUM_0 + 1 && key <= Keys.NUM_9) {
            choose(key - Keys.NUM_0 - 1);
            return true;
        }
        if (key == Keys.NUM_0) {
            choose(9);
            return true;
        }
        if (key >= Keys.KP_0 + 1 && key <= Keys.KP_9) {
            choose(key - Keys.KP_0 - 1);
            return true;
        }
        return false;
    }
}
