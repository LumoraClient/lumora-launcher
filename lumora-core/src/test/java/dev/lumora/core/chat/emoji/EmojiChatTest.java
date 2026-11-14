package dev.lumora.core.chat.emoji;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lumora.api.input.Keys;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.extras.GameExtras;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Module "Emoji Chat": the emoji assets, the picker and the suggestions while typing. */
class EmojiChatTest {
    static final EmojiRegistry REGISTRY = load();

    private static EmojiRegistry load() {
        try {
            return EmojiRegistry.load();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everyEmojiHasAFontCharacterAndASheetCell() throws Exception {
        assertEquals(1870, REGISTRY.all().size());
        assertEquals(24, REGISTRY.cell());
        // the font maps U+E000 + index to the same cell the picker draws
        JsonObject font;
        try (InputStream in = getClass().getResourceAsStream("/assets/lumora/font/emoji.json")) {
            assertNotNull(in);
            font = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
        JsonArray providers = font.getAsJsonArray("providers");
        assertEquals(8, providers.size());
        Set<Character> chars = new HashSet<>();
        for (int s = 0; s < providers.size(); s++) {
            JsonObject p = providers.get(s).getAsJsonObject();
            assertEquals("lumora:font/emoji_" + s + ".png", p.get("file").getAsString());
            assertEquals(8, p.get("height").getAsInt());
            JsonArray rows = p.getAsJsonArray("chars");
            assertEquals(16, rows.size());
            for (int y = 0; y < 16; y++) {
                String row = rows.get(y).getAsString();
                assertEquals(16, row.length(), "same length in every row");
                for (int x = 0; x < 16; x++) {
                    char c = row.charAt(x);
                    if (c != 0) {
                        int index = c - 0xE000;
                        EmojiRegistry.Emoji e = REGISTRY.all().get(index);
                        assertEquals("lumora/textures/font/emoji_" + s + ".png", REGISTRY.sheet(e).namespace() + "/" + REGISTRY.sheet(e).path());
                        assertEquals(x * 24, REGISTRY.u(e));
                        assertEquals(y * 24, REGISTRY.v(e));
                        assertTrue(chars.add(c));
                    }
                }
            }
        }
        assertEquals(1870, chars.size(), "one character per emoji");
        for (int s = 0; s < 8; s++) {
            assertNotNull(getClass().getResource("/assets/lumora/textures/font/emoji_" + s + ".png"));
        }
        assertNotNull(getClass().getResource("/assets/lumora/emoji/NOTICE.txt"), "attribution of Twemoji and gemoji");
    }

    @Test
    void namesAliasesAndSearch() {
        assertEquals("", REGISTRY.byName("smile").glyph());
        assertEquals(REGISTRY.byName("+1"), REGISTRY.byName("thumbsup"), "aliases");
        assertEquals(REGISTRY.byName("joy"), REGISTRY.byName("JOY"));
        assertNull(REGISTRY.byName("not_an_emoji"));
        List<EmojiRegistry.Emoji> jo = REGISTRY.search("jo", 6);
        assertEquals("joy", EmojiRegistry.completion(jo.get(0), "jo"), "shortest name that starts with it first");
        assertTrue(jo.size() <= 6);
        assertTrue(REGISTRY.search("happy", 10).stream().anyMatch(e -> e.name().equals("smiley")), "search words");
        assertEquals(9, EmojiRegistry.CATEGORIES.size());
        for (String c : EmojiRegistry.CATEGORIES) {
            assertFalse(REGISTRY.category(c).isEmpty(), c);
        }
    }

    @Test
    void thePickerShowsCategoriesAndInsertsTheClickedEmoji() {
        List<EmojiRegistry.Emoji> recent = new ArrayList<>();
        List<String> inserted = new ArrayList<>();
        EmojiPanel panel = new EmojiPanel(REGISTRY, () -> recent, e -> {
            inserted.add(e.shortcode());
            recent.add(0, e);
        }, k -> k, 9);
        assertEquals("smileys", panel.category(), "no recently used yet");
        assertFalse(panel.tabs().contains(EmojiPanel.RECENT));
        FakeGraphics g = new FakeGraphics();
        panel.render(g, -1, -1);
        assertTrue(g.textures.stream().anyMatch(t -> t.contains("emoji_0")), g.textures.toString());

        // first emoji of the grid
        int x = EmojiPanel.PAD + 4;
        int y = EmojiPanel.TABS_H + EmojiPanel.PAD + 4;
        assertTrue(panel.mouseClicked(x, y, 0));
        assertEquals(List.of(":grinning:"), inserted);
        assertTrue(panel.tabs().contains(EmojiPanel.RECENT), "recently used tab once something was used");

        // a tab click changes the category, the wheel scrolls and stops at the end
        panel.select("food");
        g = new FakeGraphics();
        panel.render(g, -1, -1);
        assertTrue(panel.mouseScrolled(x, y, -100));
        assertTrue(panel.scroll() > 0);
        float end = panel.scroll();
        panel.mouseScrolled(x, y, -100);
        assertEquals(end, panel.scroll(), "no scrolling past the end");
        panel.resized(panel.width(), 60);
        g = new FakeGraphics();
        panel.render(g, -1, -1);
        panel.mouseScrolled(x, y, -100);
        assertTrue(panel.scroll() > end, "less room: further to scroll");
    }

    @Test
    void suggestionsAppearAfterAColonAndTabCompletes() {
        assertEquals("jo", EmojiSuggestions.token("hi :jo", 6).query());
        assertNull(EmojiSuggestions.token("hi :j", 5), "two letters at least");
        assertNull(EmojiSuggestions.token("at 12:30", 8), "times are not codes");
        assertNull(EmojiSuggestions.token("https://x", 9));
        assertNull(EmojiSuggestions.token("a:jo", 4), "a colon inside a word");
        assertEquals("jo", EmojiSuggestions.token(":JO", 3).query());

        AtomicReference<GameExtras.ChatInput> input = new AtomicReference<>(new GameExtras.ChatInput("gg :jo", 6));
        List<String> used = new ArrayList<>();
        EmojiSuggestions s = new EmojiSuggestions(REGISTRY, input::get, (count, text) -> {
            String t = input.get().text();
            int cursor = input.get().cursor();
            String next = t.substring(0, cursor - count) + text + t.substring(cursor);
            input.set(new GameExtras.ChatInput(next, cursor - count + text.length()));
        }, e -> used.add(e.name()), () -> true);
        FakeGraphics g = new FakeGraphics();
        s.render(g, 427, 240, 0, 0);
        assertFalse(s.matches().isEmpty());
        assertTrue(g.texts.contains(":joy:"), g.texts.toString());
        assertTrue(s.key(Keys.DOWN));
        assertEquals(1, s.selected());
        assertTrue(s.key(Keys.UP));
        assertTrue(s.key(Keys.TAB));
        assertEquals("gg :joy: ", input.get().text());
        assertEquals(9, input.get().cursor());
        assertEquals(List.of("joy"), used);
        assertFalse(s.key(Keys.TAB), "nothing to complete: Tab goes to Minecraft");

        EmojiSuggestions off = new EmojiSuggestions(REGISTRY, () -> new GameExtras.ChatInput(":jo", 3), (c, t) -> { }, e -> { }, () -> false);
        off.render(new FakeGraphics(), 427, 240, 0, 0);
        assertTrue(off.matches().isEmpty(), "switched off in the settings");
    }

    @Test
    void recentlyUsedKeepsTheNewestFirstWithoutDuplicates() {
        EmojiChatModule m = new EmojiChatModule(REGISTRY);
        m.recent = new dev.lumora.api.config.TextSetting("recent", "", 600);
        m.remember(REGISTRY.byName("joy"));
        m.remember(REGISTRY.byName("smile"));
        m.remember(REGISTRY.byName("joy"));
        assertEquals(List.of("joy", "smile"), m.recentEmojis().stream().map(EmojiRegistry.Emoji::name).toList());
        for (EmojiRegistry.Emoji e : REGISTRY.all().subList(0, 40)) {
            m.remember(e);
        }
        assertEquals(EmojiChatModule.MAX_RECENT, m.recentEmojis().size());
    }
}
