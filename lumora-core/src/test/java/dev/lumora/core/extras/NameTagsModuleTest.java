package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameTagsModuleTest {
    private final NameTagsModule module = new NameTagsModule();

    @AfterEach
    void off() {
        module.setActiveForTest(false);
    }

    @Test
    void offChangesNothing() {
        module.settings(new SettingsGroup("test."));
        assertFalse(NameTagsModule.showOwn());
        assertFalse(NameTagsModule.shadow(false, false), "vanilla: no shadow");
        assertEquals(0x40000000, NameTagsModule.background(0x40000000));
        assertEquals(0x12345678, NameTagsModule.backgroundArgb(0x12345678));
    }

    @Test
    void shadowOnlyOnTheNormalPart() {
        module.settings(new SettingsGroup("test."));
        module.setActiveForTest(true);
        assertTrue(NameTagsModule.showOwn(), "default on");
        assertTrue(NameTagsModule.shadow(false, false));
        assertFalse(NameTagsModule.shadow(false, true), "never through walls");
        module.textShadow.set(false);
        assertFalse(NameTagsModule.shadow(false, false));
        module.own.set(false);
        assertFalse(NameTagsModule.showOwn());
    }

    @Test
    void backgroundColorOrNoneButPartsWithoutBoxStayWithout() {
        module.settings(new SettingsGroup("test."));
        module.setActiveForTest(true);
        assertEquals(0x40000000, NameTagsModule.background(0x3F000000), "default: vanilla black 25 %");
        assertEquals(0, NameTagsModule.background(0), "the normal part has no box");
        module.backgroundColor.set(0x80FF0000);
        assertEquals(0x80FF0000, NameTagsModule.background(0x40000000));
        assertEquals(0x80FF0000, NameTagsModule.backgroundArgb(0x40000000));
        module.background.set(false);
        assertEquals(0, NameTagsModule.background(0x40000000), "no box");
        assertEquals(0, NameTagsModule.backgroundArgb(0x40000000));
    }
}
