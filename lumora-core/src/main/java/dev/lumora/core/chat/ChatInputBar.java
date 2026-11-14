package dev.lumora.core.chat;

import dev.lumora.api.chat.ChatInputButton;
import dev.lumora.api.chat.ChatPanel;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * The buttons of addons at the chat input (Lumora API 1.9, like LabyMod's chat input buttons): a row right above
 * Minecraft's input line on the right, a tooltip above the button under the mouse, and the open {@link ChatPanel}
 * above the row, right-aligned. One panel at a time; a second click on its button closes it.
 */
final class ChatInputBar {
    static final int BUTTON = 14;
    static final int GAP = 1;
    /** Minecraft's chat input line: 12 px high, 2 px above the bottom edge. */
    static final int INPUT_TOP = 14;
    static final int BORDER = 2;
    static final int BUTTON_BG = 0xC0101018;
    static final int PANEL_BG = 0xE8101418;
    static final int TOOLTIP_BG = 0xF0121A20;

    /** A button where it was drawn in the last frame. */
    record Placed(ChatApi.OwnedButton owned, int x, int y) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + BUTTON && my >= y && my < y + BUTTON;
        }
    }

    private final ChatApi api;
    private List<Placed> placed = List.of();
    private ChatApi.OwnedButton open;
    private ChatPanel panel;
    /** The open panel's rectangle in the last frame (content inside the border). */
    private int px;
    private int py;
    private int pw;
    private int ph;

    ChatInputBar(ChatApi api) {
        this.api = api;
    }

    /** The buttons that are shown, in order (hidden and failing ones left out). */
    List<ChatApi.OwnedButton> visible() {
        List<ChatApi.OwnedButton> out = new ArrayList<>();
        for (ChatApi.OwnedButton o : api.buttons()) {
            try {
                if (o.button().visible()) {
                    out.add(o);
                }
            } catch (RuntimeException | LinkageError e) {
                api.buttons().remove(o);
                api.log().warn("Chat input button " + o.key() + " failed and is removed", e);
            }
        }
        return out;
    }

    /** Where the buttons go: right-aligned, right above the input line. */
    static List<int[]> layout(int count, int screenWidth, int screenHeight) {
        List<int[]> out = new ArrayList<>();
        int y = screenHeight - INPUT_TOP - BUTTON - 1;
        for (int i = 0; i < count; i++) {
            int x = screenWidth - 2 - (count - i) * (BUTTON + GAP) + GAP;
            out.add(new int[]{x, y});
        }
        return out;
    }

    void render(HudGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY, long now) {
        List<ChatApi.OwnedButton> shown = visible();
        if (open != null && !shown.contains(open)) {
            closePanel();
        }
        List<int[]> spots = layout(shown.size(), screenWidth, screenHeight);
        List<Placed> now2 = new ArrayList<>();
        Placed hovered = null;
        for (int i = 0; i < shown.size(); i++) {
            Placed p = new Placed(shown.get(i), spots.get(i)[0], spots.get(i)[1]);
            now2.add(p);
            boolean over = p.contains(mouseX, mouseY);
            if (over) {
                hovered = p;
            }
            drawButton(g, p, over, p.owned() == open);
        }
        placed = now2;
        if (panel != null && !now2.isEmpty()) {
            drawPanel(g, screenWidth, now2.get(0).y(), mouseX, mouseY);
        }
        if (hovered != null && hovered.owned() != open) {
            drawTooltip(g, hovered, screenWidth);
        }
    }

    private static void drawButton(HudGraphics g, Placed p, boolean over, boolean active) {
        int accent = LumoraTheme.accent();
        int bg = active ? LumoraTheme.alpha(accent, 0xE0) : over ? LumoraTheme.alpha(accent, 0x90) : BUTTON_BG;
        g.roundedRect(p.x(), p.y(), BUTTON, BUTTON, 2, bg);
        ChatInputButton b = p.owned().button();
        if (b.icon() != null) {
            ChatInputButton.Icon i = b.icon();
            g.texture(i.texture(), p.x() + 2, p.y() + 2, BUTTON - 4, BUTTON - 4, i.u(), i.v(), i.regionWidth(), i.regionHeight(),
                    i.textureWidth(), i.textureHeight());
        } else {
            String label = b.label();
            g.text(label, p.x() + (BUTTON - g.textWidth(label)) / 2 + 1, p.y() + (BUTTON - g.lineHeight()) / 2 + 1, 0xFFFFFFFF, true);
        }
    }

    private static void drawTooltip(HudGraphics g, Placed p, int screenWidth) {
        String text = p.owned().button().tooltip();
        int w = g.textWidth(text) + 8;
        int x = Math.max(2, Math.min(screenWidth - 2 - w, p.x() + BUTTON - w));
        int y = p.y() - 14;
        g.roundedRect(x, y, w, 12, 2, TOOLTIP_BG);
        g.text(text, x + 4, y + 2, 0xFFFFFFFF, false);
    }

    private void drawPanel(HudGraphics g, int screenWidth, int buttonsTop, int mouseX, int mouseY) {
        int w;
        int h;
        try {
            w = Math.max(10, Math.min(panel.width(), screenWidth - 4 - BORDER * 2));
            h = Math.max(10, Math.min(panel.height(), buttonsTop - 4 - BORDER * 2 - 2));
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return;
        }
        try {
            panel.resized(w, h);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return;
        }
        px = screenWidth - 2 - BORDER - w;
        py = buttonsTop - 2 - BORDER - h;
        pw = w;
        ph = h;
        g.roundedRect(px - BORDER, py - BORDER, w + BORDER * 2, h + BORDER * 2, 3, PANEL_BG);
        g.outline(px - BORDER, py - BORDER, w + BORDER * 2, h + BORDER * 2, LumoraTheme.alpha(LumoraTheme.accent(), 0x70));
        g.enableScissor(px, py, px + w, py + h);
        g.push();
        g.translate(px, py);
        try {
            panel.render(g, mouseX - px, mouseY - py);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        } finally {
            g.pop();
            g.disableScissor();
        }
    }

    private boolean inPanel(int mx, int my) {
        return panel != null && mx >= px - BORDER && mx < px + pw + BORDER && my >= py - BORDER && my < py + ph + BORDER;
    }

    boolean click(int mx, int my, int button) {
        for (Placed p : placed) {
            if (p.contains(mx, my)) {
                press(p.owned());
                return true;
            }
        }
        if (inPanel(mx, my)) {
            try {
                panel.mouseClicked(mx - px, my - py, button);
            } catch (RuntimeException | LinkageError e) {
                fail(e);
            }
            return true; // a click on the panel never reaches the chat behind it
        }
        return false;
    }

    boolean scroll(int mx, int my, double amount) {
        if (!inPanel(mx, my)) {
            return false;
        }
        try {
            panel.mouseScrolled(mx - px, my - py, amount);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
        return true;
    }

    boolean key(int key) {
        return false;
    }

    /** A click on a button: toggles its panel or runs its action. */
    void press(ChatApi.OwnedButton o) {
        ChatInputButton b = o.button();
        try {
            if (!b.hasPanel()) {
                closePanel();
                b.press();
                return;
            }
            if (open == o) {
                closePanel();
                return;
            }
            closePanel();
            ChatPanel p = b.createPanel();
            if (p != null) {
                open = o;
                panel = p;
                p.opened();
            }
        } catch (RuntimeException | LinkageError e) {
            api.log().warn("Chat input button " + o.key() + " failed", e);
            open = null;
            panel = null;
        }
    }

    void closePanel() {
        ChatPanel p = panel;
        panel = null;
        open = null;
        pw = 0;
        ph = 0;
        if (p != null) {
            try {
                p.closed();
            } catch (RuntimeException | LinkageError e) {
                api.log().warn("Chat panel failed while closing", e);
            }
        }
    }

    /** The button was removed (addon off): its panel closes. */
    void removed(ChatApi.OwnedButton o) {
        if (open == o) {
            closePanel();
        }
    }

    /** The open panel (tests), or null. */
    ChatPanel openPanel() {
        return panel;
    }

    private void fail(Throwable e) {
        String key = open == null ? "?" : open.key();
        api.log().warn("Chat panel of " + key + " failed and is closed", e);
        panel = null;
        open = null;
    }
}
