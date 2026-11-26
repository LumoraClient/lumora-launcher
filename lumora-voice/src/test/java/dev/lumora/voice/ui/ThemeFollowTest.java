package dev.lumora.voice.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeFollowTest {
    /** A client look with a purple accent and Lumora's navy surfaces. */
    static final class ClientTheme implements dev.lumora.api.ui.Theme {
        @Override public String style() { return "lumora"; }
        @Override public String accentId() { return "purple"; }
        @Override public int accent() { return 0xFF8B5CF6; }
        @Override public int accentHover() { return 0xFFA78BFA; }
        @Override public int accentDark() { return 0xFF6D28D9; }
        @Override public List<Accent> accents() { return List.of(); }
        @Override public dev.lumora.api.Registration addAccent(String id, String name, int color) { throw new UnsupportedOperationException(); }
    }

    @AfterEach
    void reset() {
        Theme.follow(null);
        Theme.reload(null);
    }

    @Test
    void voiceWindowsTakeTheLookOfTheClient() {
        int cyan = Theme.color("accent");
        Theme.follow(new ClientTheme());
        assertEquals(0xFF8B5CF6, Theme.color("accent"), "the hub's accent");
        assertEquals(0xFF8B5CF6, Theme.color("toggle_on"));
        assertEquals(0xFFE8EEFA, Theme.color("text"), "the client's text colour");
        // translucent voice surfaces keep their alpha, take the client's colour
        assertEquals(0xF00D1424, Theme.color("surface"));
        assertEquals(0xFF22314F, Theme.color("border"));
        Theme.follow(null);
        assertEquals(cyan, Theme.color("accent"), "outside the client: the voice defaults");
    }

    @Test
    void theJarsOwnThemeFileDoesNotHideTheClient() {
        Theme.reload(() -> new java.io.ByteArrayInputStream("{\"colors\":{\"accent\":\"#18B4CC\"}}".getBytes()));
        Theme.follow(new ClientTheme());
        assertEquals(0xFF8B5CF6, Theme.color("accent"));
    }
}
