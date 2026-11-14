package dev.lumora.core.chat;

import dev.lumora.api.chat.ChatIcons;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.event.SimpleEventBus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How {@code :name:} codes are found in chat text (Lumora API 1.9 inline icons) and where the input buttons go. */
class ChatSplitTest {
    static final Map<String, ChatIcons.Icon> ICONS = Map.of("smile", new ChatIcons.Icon("t:e", "a"), "+1", new ChatIcons.Icon("t:e", "b"));

    static final CoreLogger QUIET = new CoreLogger() {
        public void info(String message) { }
        public void warn(String message) { }
        public void warn(String message, Throwable error) { }
        public void error(String message, Throwable error) { }
    };

    private static List<ChatApi.Segment> split(String text) {
        ChatApi api = new ChatApi(new SimpleEventBus(QUIET), QUIET);
        return api.split(text, ICONS::get);
    }

    private static String shape(List<ChatApi.Segment> parts) {
        if (parts == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (ChatApi.Segment s : parts) {
            b.append(s.isIcon() ? "[" + s.icon().glyph() + "]" : s.text());
        }
        return b.toString();
    }

    @Test
    void findsKnownCodesOnly() {
        assertEquals("hi [a]!", shape(split("hi :smile:!")));
        assertEquals("[a][b]", shape(split(":smile::+1:")), "codes right after each other");
        assertEquals("12:30[a]", shape(split("12:30:smile:")), "a colon that starts nothing may end a code");
        assertEquals("[a]", shape(split(":SMILE:")), "case does not matter");
        assertNull(split("no codes: here :nope: or :"));
        assertNull(split(":sm ile:"), "spaces are not part of a name");
        assertEquals("a: [a] :b", shape(split("a: :smile: :b")));
    }

    @Test
    void validNames() {
        assertTrue(ChatApi.validName("+1"));
        assertTrue(ChatApi.validName("flag-de"));
        assertFalse(ChatApi.validName(""));
        assertFalse(ChatApi.validName("a".repeat(41)));
        assertFalse(ChatApi.validName("ä"));
    }

    @Test
    void buttonsSitInsideTheInputLineOnTheRight() {
        List<int[]> spots = ChatInputBar.layout(3, 427, 240);
        assertEquals(3, spots.size());
        int[] last = spots.get(2);
        assertEquals(427 - 2 - ChatInputBar.BUTTON, last[0], "the last one at the right edge");
        assertEquals(240 - 14, last[1], "inside the input line");
        assertEquals(240 - 2, last[1] + ChatInputBar.BUTTON, "as high as the input line");
        assertEquals(ChatInputBar.BUTTON + ChatInputBar.GAP, spots.get(1)[0] - spots.get(0)[0]);
    }

    @Test
    void textFieldEndsBeforeTheButtons() {
        assertEquals(427 - 4, ChatInputBar.inputWidth(0, 427), "no buttons: Minecraft's width");
        int first = ChatInputBar.layout(2, 427, 240).get(0)[0];
        int end = 4 + ChatInputBar.inputWidth(2, 427);
        assertEquals(ChatInputBar.TEXT_GAP, first - end, "a small gap between the text and the first button");
    }
}
