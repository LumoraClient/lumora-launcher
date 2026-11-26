package dev.lumora.voice.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Single-line text input without a frame (the view draws the card): cursor, paste, optional password dots. */
public final class TextField {
    private final int maxLength;
    private final boolean password;
    private final Supplier<String> clipboard;
    private String value = "";
    private int cursor;
    private Consumer<String> responder = ignored -> {};
    int x, y, width, height = 12;
    boolean focused;

    public TextField(int maxLength, boolean password, Supplier<String> clipboard) {
        this.maxLength = maxLength; this.password = password; this.clipboard = clipboard;
    }

    public TextField bounds(int x, int y, int width) { this.x = x; this.y = y; this.width = width; return this; }

    public TextField onChange(Consumer<String> responder) { this.responder = responder; return this; }

    public String value() { return value; }

    public boolean focused() { return focused; }

    public void focus(boolean value) { focused = value; }

    /** Replaces the text; calls the responder like typing would. */
    public void setValue(String text) {
        String clean = filter(text);
        if (clean.length() > maxLength) clean = clean.substring(0, maxLength);
        boolean changed = !clean.equals(value);
        value = clean; cursor = value.length();
        if (changed) responder.accept(value);
    }

    boolean contains(double mx, double my) { return mx >= x - 6 && my >= y - 6 && mx < x + width + 6 && my < y + height + 4; }

    void render(HudGraphics g, int color, long timeMillis) {
        String shown = password ? "•".repeat(value.length()) : value;
        int cut = 0;
        // keep the cursor visible: drop characters on the left until the text up to the cursor fits
        while (cut < cursor && g.textWidth(shown.substring(cut, cursor)) > width - 4) cut++;
        String visible = shown.substring(cut);
        while (!visible.isEmpty() && g.textWidth(visible) > width) visible = visible.substring(0, visible.length() - 1);
        Ui.label(g, visible, x, y, color);
        if (focused && (timeMillis / 500) % 2 == 0) {
            int cx = x + g.textWidth(shown.substring(cut, Math.max(cut, cursor)));
            g.fill(cx, y - 1, cx + 1, y + 9, color);
        }
    }

    boolean keyPressed(int key, boolean ctrl) {
        if (!focused) return false;
        switch (key) {
            case Keys.BACKSPACE -> { if (cursor > 0) { value = value.substring(0, cursor - 1) + value.substring(cursor); cursor--; responder.accept(value); } }
            case Keys.DELETE -> { if (cursor < value.length()) { value = value.substring(0, cursor) + value.substring(cursor + 1); responder.accept(value); } }
            case Keys.LEFT -> cursor = Math.max(0, cursor - 1);
            case Keys.RIGHT -> cursor = Math.min(value.length(), cursor + 1);
            case Keys.HOME -> cursor = 0;
            case Keys.END -> cursor = value.length();
            default -> {
                if (ctrl && key == Keys.V) { insert(clipboard.get()); return true; }
                return key != Keys.ESCAPE && key != Keys.TAB && key != Keys.ENTER && key != Keys.UP && key != Keys.DOWN && key != Keys.PAGE_UP && key != Keys.PAGE_DOWN;
            }
        }
        return true;
    }

    boolean charTyped(char ch) {
        if (!focused || !allowed(ch)) return false;
        insert(String.valueOf(ch));
        return true;
    }

    private void insert(String text) {
        String clean = filter(text == null ? "" : text);
        int room = maxLength - value.length();
        if (room <= 0 || clean.isEmpty()) return;
        if (clean.length() > room) clean = clean.substring(0, room);
        value = value.substring(0, cursor) + clean + value.substring(cursor);
        cursor += clean.length();
        responder.accept(value);
    }

    private static boolean allowed(char ch) { return ch >= ' ' && ch != 127 && ch != '§'; }

    private static String filter(String text) {
        var out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) if (allowed(ch)) out.append(ch);
        return out.toString();
    }
}
