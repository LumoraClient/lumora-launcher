package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.CosmeticsState;
import dev.lumora.core.cosmetics.EmoteDefinition;

/**
 * The emote wheel: a ring of 4 to 10 slots per page around the screen centre, up to 8 pages.
 * <ul>
 * <li><b>Play</b> (in the game): hold the wheel key, point at an emote, release the key to play it; a short tap
 * keeps the wheel open, then a click plays (number keys 1-9 and 0 too). The centre stops a running emote; right click
 * or Esc closes. It only stays open until a choice is made, so it never blocks the controls for long.</li>
 * <li><b>Edit</b> (from the emote menu): "choose a slot to edit" opens the {@link EmotePickerView} for that slot;
 * right click empties a slot.</li>
 * </ul>
 * Pages: buttons below the wheel, the mouse wheel, the arrow keys or Page Up/Down.
 */
public final class EmoteWheelView implements AddonView {
    private static final String K = "lumora.cosmetics.";
    private static final int FILL = 0xB01A1F2B;
    private static final int FILL_EMPTY = 0x80151922;
    private static final int HOVER = 0xE03D7BF0;
    private static final int LINE = 0x70D8E2F5;
    /** A key press shorter than this keeps the wheel open (tap) instead of choosing on release. */
    private static final long TAP_MS = 250;

    private final Cosmetics cosmetics;
    private final UiHost host;
    private final boolean edit;
    private final Wheel wheel = new Wheel();
    private final Ui.Regions regions = new Ui.Regions();
    private final long openedAt = System.currentTimeMillis();
    private boolean holding;
    private int page;
    private int hovered = -1;
    private int mouseX;
    private int mouseY;

    public EmoteWheelView(Cosmetics cosmetics, UiHost host) {
        this(cosmetics, host, false, 0);
    }

    /** {@code edit}: the slot editor of the emote menu, starting on {@code page}. */
    public EmoteWheelView(Cosmetics cosmetics, UiHost host, boolean edit, int page) {
        this.cosmetics = cosmetics;
        this.host = host;
        this.edit = edit;
        this.page = page;
        this.holding = !edit && host.wheelKeyDown();
    }

    private String tr(String key, Object... args) {
        return host.tr(K + key, args);
    }

    private int pages() {
        return Math.max(1, Math.min(CosmeticsState.MAX_PAGES, host.wheelPages()));
    }

    private int slots() {
        return Math.max(4, Math.min(CosmeticsState.MAX_SLOTS, host.wheelSlots()));
    }

    private EmoteDefinition emoteAt(int slot) {
        return cosmetics.registry().emote(cosmetics.state().slot(CosmeticsState.slotIndex(page, slot)));
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        regions.clear();
        mouseX = mx;
        mouseY = my;
        page = Math.min(page, pages() - 1);
        if (edit) {
            g.fill(0, 0, width, height, Ui.BACKDROP);
        }
        int n = slots();
        int r = Math.max(48, Math.min(edit ? 120 : 112, Math.min(width, height) / 2 - 34));
        wheel.layout(width / 2, height / 2 - (pages() > 1 ? 8 : 0), r, 0.38f, n);
        // holding the key: the slot the mouse points to (from the centre, any distance) is chosen on release
        hovered = holding ? wheel.slotToward(mx, my, wheel.inner()) : wheel.slotAt(mx, my);
        if (holding && !host.wheelKeyDown()) {
            holding = false;
            if (hovered >= 0) {
                choose(hovered);
                return;
            }
            if (System.currentTimeMillis() - openedAt > TAP_MS) {
                host.close(this);
                return;
            }
        }
        EmoteDefinition[] emotes = new EmoteDefinition[n];
        for (int i = 0; i < n; i++) {
            emotes[i] = emoteAt(i);
        }
        int hov = hovered;
        wheel.draw(g, slot -> slot == hov ? HOVER : emotes[slot] == null ? FILL_EMPTY : FILL, LINE);
        int lw = wheel.labelWidth();
        for (int i = 0; i < n; i++) {
            int[] p = wheel.labelPos(i);
            g.centeredText(Integer.toString((i + 1) % 10), p[0], p[1] - 11, i == hovered ? 0xFFFFFFFF : Ui.DIM, false);
            String label = emotes[i] == null ? tr("wheel.empty") : EmotesView.name(host, emotes[i]);
            g.centeredText(Ui.fit(g, label, lw), p[0], p[1] - 1, emotes[i] == null ? Ui.DIM : Ui.TEXT, true);
        }
        centre(g, emotes, mx, my);
        int below = wheel.cy() + wheel.outer() + 8;
        if (edit) {
            g.centeredText(tr("wheel.edit_title"), width / 2, wheel.cy() - wheel.outer() - 14, Ui.TEXT, true);
        }
        if (pages() > 1) {
            g.centeredText(tr("wheel.page", page + 1, pages()), width / 2, below, Ui.TEXT, true);
            int bw = Math.max(70, g.textWidth(tr("wheel.next")) + 14);
            Ui.button(g, regions, width / 2 - bw - 3, below + 12, bw, 16, tr("wheel.previous"), false, page > 0, mx, my, () -> page--);
            Ui.button(g, regions, width / 2 + 3, below + 12, bw, 16, tr("wheel.next"), false, page < pages() - 1, mx, my, () -> page++);
            below += 32;
        }
        if (edit) {
            Ui.button(g, regions, width / 2 - 40, below + 2, 80, 16, tr("wheel.done"), true, true, mx, my, this::backToMenu);
        } else {
            g.centeredText(tr(holding ? "wheel.help_hold" : "wheel.help"), width / 2, below + 2, Ui.MUTED, true);
        }
    }

    private void centre(HudGraphics g, EmoteDefinition[] emotes, int mx, int my) {
        EmoteDefinition playing = cosmetics.currentEmote();
        String center;
        int color = Ui.TEXT;
        if (hovered >= 0) {
            center = emotes[hovered] == null ? tr(edit ? "wheel.assign" : "wheel.empty") : EmotesView.name(host, emotes[hovered]);
        } else if (edit) {
            center = tr("wheel.edit_center");
            color = Ui.MUTED;
        } else if (playing != null && wheel.inCenter(mx, my)) {
            center = tr("wheel.stop");
            color = 0xFFFF8A8A;
        } else if (playing != null) {
            center = EmotesView.name(host, playing);
            color = Ui.GOOD;
        } else {
            center = tr("wheel.center");
            color = Ui.MUTED;
        }
        g.centeredText(Ui.fit(g, center, wheel.inner() * 2 - 8), wheel.cx(), wheel.cy() - 4, color, true);
    }

    private void choose(int slot) {
        if (slot < 0 || slot >= slots()) {
            return;
        }
        int index = CosmeticsState.slotIndex(page, slot);
        if (edit) {
            host.close(this);
            host.show(new EmotePickerView(cosmetics, host, page, slot));
            return;
        }
        EmoteDefinition e = cosmetics.registry().emote(cosmetics.state().slot(index));
        host.close(this);
        if (e == null) {
            host.show(new EmoteWheelView(cosmetics, host, true, page));
        } else {
            cosmetics.playEmote(e);
        }
    }

    private void backToMenu() {
        host.close(this);
        host.show(new EmotesView(cosmetics, host));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (regions.click(x, y, button)) {
            return true;
        }
        int slot = wheel.slotAt(x, y);
        if (button == 1) {
            if (edit && slot >= 0) {
                cosmetics.state().setSlot(CosmeticsState.slotIndex(page, slot), null);
            } else if (!edit) {
                host.close(this);
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        holding = false;
        if (slot >= 0) {
            choose(slot);
            return true;
        }
        if (!edit) {
            if (wheel.inCenter(x, y) && cosmetics.currentEmote() != null) {
                cosmetics.stopEmote();
            }
            host.close(this);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (delta < 0 && page < pages() - 1) {
            page++;
        } else if (delta > 0 && page > 0) {
            page--;
        }
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
        if ((key == Keys.RIGHT || key == Keys.PAGE_DOWN) && page < pages() - 1) {
            page++;
            return true;
        }
        if ((key == Keys.LEFT || key == Keys.PAGE_UP) && page > 0) {
            page--;
            return true;
        }
        if (key == Keys.ESCAPE && edit) {
            backToMenu();
            return true;
        }
        return false;
    }

    /** Mouse position of the last frame (tests). */
    int[] mouse() {
        return new int[] {mouseX, mouseY};
    }
}
