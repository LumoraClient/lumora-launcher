package dev.lumora.core.theme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeLayersTest {
    @AfterEach
    void reset() {
        ThemeLayers.reset();
    }

    @Test
    void themeAndFontFollowTheHub() {
        assertEquals(List.of("lumora_theme"), ThemeLayers.load("lumora", false, "inter"), "font off: only the theme");
        assertTrue(ThemeLayers.themeActive());
        assertEquals(List.of("lumora_theme", "lumora_font_outfit"), ThemeLayers.load("lumora", true, "outfit"));
        assertEquals(List.of("lumora_font"), ThemeLayers.load("vanilla", true, "inter"), "Inter lives in lumora_font");
        assertFalse(ThemeLayers.themeActive());
        assertEquals(List.of(), ThemeLayers.load("vanilla", true, "vanilla"));
        assertEquals(List.of("lumora_theme", "lumora_font"), ThemeLayers.load(null, true, "unknown"), "unknown font: Inter");
    }

    @Test
    void aReloadIsDueOnlyWhenTheLookChanges() {
        assertTrue(ThemeLayers.changed("lumora", false, "inter"), "nothing loaded yet");
        ThemeLayers.load("lumora", false, "inter");
        assertFalse(ThemeLayers.changed("lumora", false, "inter"));
        assertFalse(ThemeLayers.changed("lumora", false, "outfit"), "another font while the font is off changes nothing");
        assertTrue(ThemeLayers.changed("vanilla", false, "inter"));
        assertTrue(ThemeLayers.changed("lumora", true, "inter"));
    }

    @Test
    void theSmokeTestCanShowAnotherFont() {
        ThemeLayers.overrideFont("vanilla");
        assertEquals(List.of("lumora_theme"), ThemeLayers.load("lumora", true, "outfit"));
        ThemeLayers.overrideFont(null);
        assertTrue(ThemeLayers.changed("lumora", true, "outfit"), "back to the configured font");
        assertEquals(List.of("lumora_theme", "lumora_font_outfit"), ThemeLayers.load("lumora", true, "outfit"));
    }
}
