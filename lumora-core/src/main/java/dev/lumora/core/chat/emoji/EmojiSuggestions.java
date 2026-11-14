package dev.lumora.core.chat.emoji;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.chat.ChatApi;
import dev.lumora.core.extras.GameExtras;
import dev.lumora.core.theme.LumoraTheme;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Emoji suggestions of the module "Emoji Chat" (like LabyMod's emoji suggestions): typing {@code :jo} in the chat
 * shows matching emojis above the input; Tab or a click completes to {@code :joy: }, the arrow keys and the mouse
 * wheel choose.
 */
public final class EmojiSuggestions implements ChatApi.ScreenLayer {
    static final int ROWS = 6;
    static final int ROW_H = 12;
    static final int MIN_QUERY = 2;
    private static final int BG = 0xF0101418;

    /** What is being typed: the text after the colon and where the colon is. */
    record Token(String query, int colon, int cursor) {
    }

    private final EmojiRegistry registry;
    private final Supplier<GameExtras.ChatInput> input;
    private final BiConsumer<Integer, String> replace;
    private final Consumer<EmojiRegistry.Emoji> used;
    private final BooleanSupplier enabled;
    private List<EmojiRegistry.Emoji> matches = List.of();
    private Token token;
    private int selected;
    /** The popup in the last frame. */
    private int bx;
    private int by;
    private int bw;
    private int bh;

    /**
     * @param replace replaces that many characters before the cursor with the text
     * @param used    remembers a completed emoji as recently used
     */
    public EmojiSuggestions(EmojiRegistry registry, Supplier<GameExtras.ChatInput> input, BiConsumer<Integer, String> replace,
                            Consumer<EmojiRegistry.Emoji> used, BooleanSupplier enabled) {
        this.registry = registry;
        this.input = input;
        this.replace = replace;
        this.used = used;
        this.enabled = enabled;
    }

    /**
     * The shortcode being typed right before the cursor: a colon at the start or after a space, then at least
     * {@link #MIN_QUERY} name characters up to the cursor; null if there is none.
     */
    static Token token(String text, int cursor) {
        if (text == null || cursor <= 0 || cursor > text.length()) {
            return null;
        }
        int i = cursor - 1;
        while (i >= 0 && nameChar(text.charAt(i))) {
            i--;
        }
        if (i < 0 || text.charAt(i) != ':' || cursor - i - 1 < MIN_QUERY) {
            return null;
        }
        if (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            return null; // "12:30" or "http://" or the end of a finished ":smile:"
        }
        return new Token(text.substring(i + 1, cursor).toLowerCase(java.util.Locale.ROOT), i, cursor);
    }

    static boolean nameChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '+' || c == '-';
    }

    /** Looks at the input again (each frame). */
    void update() {
        GameExtras.ChatInput in = enabled.getAsBoolean() ? input.get() : null;
        Token t = in == null ? null : token(in.text(), in.cursor());
        if (t == null) {
            token = null;
            matches = List.of();
            selected = 0;
            return;
        }
        if (token == null || !token.query().equals(t.query())) {
            matches = registry.search(t.query(), ROWS);
            selected = 0;
        }
        token = t;
    }

    List<EmojiRegistry.Emoji> matches() {
        return matches;
    }

    int selected() {
        return selected;
    }

    @Override
    public void render(HudGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        update();
        if (matches.isEmpty()) {
            bw = 0;
            return;
        }
        int w = 0;
        for (EmojiRegistry.Emoji e : matches) {
            w = Math.max(w, g.textWidth(e.shortcode()));
        }
        bw = w + 18;
        bh = matches.size() * ROW_H + 2;
        bx = 2;
        by = screenHeight - 14 - bh - 1;
        g.roundedRect(bx, by, bw, bh, 2, BG);
        for (int i = 0; i < matches.size(); i++) {
            EmojiRegistry.Emoji e = matches.get(i);
            int y = by + 1 + i * ROW_H;
            boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + ROW_H;
            if (i == selected || over) {
                g.roundedRect(bx + 1, y, bw - 2, ROW_H, 2, i == selected ? LumoraTheme.alpha(LumoraTheme.accent(), 0xC0) : 0x40FFFFFF);
            }
            int cell = registry.cell();
            int sheet = registry.sheetSize();
            g.texture(registry.sheet(e), bx + 3, y + 1, 10, 10, registry.u(e), registry.v(e), cell, cell, sheet, sheet);
            g.text(":" + EmojiRegistry.completion(e, token.query()) + ":", bx + 15, y + 2, 0xFFE8EEFA, false);
        }
    }

    /** Completes the typed code to the chosen emoji. */
    void accept(int index) {
        if (token == null || index < 0 || index >= matches.size()) {
            return;
        }
        EmojiRegistry.Emoji e = matches.get(index);
        replace.accept(token.cursor() - token.colon(), ":" + EmojiRegistry.completion(e, token.query()) + ": ");
        used.accept(e);
        token = null;
        matches = List.of();
        selected = 0;
    }

    @Override
    public boolean click(int mouseX, int mouseY, int button) {
        if (bw == 0 || matches.isEmpty() || mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + bh) {
            return false;
        }
        accept(Math.max(0, Math.min(matches.size() - 1, (mouseY - by - 1) / ROW_H)));
        return true;
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double amount) {
        if (bw == 0 || matches.isEmpty() || mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + bh) {
            return false;
        }
        move(amount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean key(int key) {
        update();
        if (matches.isEmpty()) {
            return false;
        }
        if (key == Keys.TAB) {
            accept(selected);
            return true;
        }
        if (key == Keys.UP) {
            move(-1);
            return true;
        }
        if (key == Keys.DOWN) {
            move(1);
            return true;
        }
        return false;
    }

    private void move(int by) {
        selected = Math.floorMod(selected + by, matches.size());
    }

    @Override
    public void chatClosed() {
        token = null;
        matches = List.of();
        selected = 0;
    }
}
