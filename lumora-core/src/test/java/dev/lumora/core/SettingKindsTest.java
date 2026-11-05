package dev.lumora.core;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ButtonSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.EnumSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingType;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.config.SettingsBinder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The setting kinds of Lumora API 1.3 (like LabyMod's settings): types, storage and visibility. */
class SettingKindsTest {
    enum Mode { HOLD, TOGGLE, DOUBLE_TAP }

    @Test
    void everyKindHasItsTypeAndFluentSettersKeepTheConcreteType() {
        SettingsGroup g = new SettingsGroup("test.");
        BooleanSetting sw = g.toggle("switch", true).name("test.switch");
        BooleanSetting box = g.checkbox("box", false).name("test.box").section("test.section.a");
        ChoiceSetting arrows = g.choice("arrows", "a", "a", "b").name("test.arrows");
        ChoiceSetting drop = g.dropdown("drop", "b", "a", "b", "c").requires(sw);
        ChoiceSetting row = g.selection("row", "a", "a", "b");
        EnumSetting<Mode> mode = g.dropdown("mode", Mode.HOLD).name("test.mode");
        EnumSetting<Mode> modeRow = g.selection("mode_row", Mode.TOGGLE);
        ColorSetting chroma = g.chroma("color", 0xFF112233, true).name("test.color");
        ButtonSetting button = g.button("reset", () -> { }).name("test.reset");
        assertEquals(SettingType.BOOLEAN, sw.type());
        assertEquals(SettingType.CHECKBOX, box.type());
        assertEquals(SettingType.CHOICE, arrows.type());
        assertEquals(SettingType.DROPDOWN, drop.type());
        assertEquals(SettingType.SELECTION, row.type());
        assertEquals(SettingType.DROPDOWN, mode.type());
        assertEquals(SettingType.SELECTION, modeRow.type());
        assertEquals(SettingType.CHOICE, mode.arrows().type());
        assertEquals(SettingType.COLOR, chroma.type());
        assertEquals(SettingType.BUTTON, button.type());
        assertEquals("test.section.a", box.sectionKey());
        assertEquals("test.reset.button", button.labelKey());
        assertEquals("custom", g.button("other", () -> { }).label("custom").labelKey());
    }

    @Test
    void visibilityFollowsTheParentSwitchAndAFailingConditionShows() {
        SettingsGroup g = new SettingsGroup("test.");
        BooleanSetting parent = g.toggle("parent", false);
        IntegerSetting child = g.slider("child", 5, 0, 10).requires(parent);
        assertFalse(child.isVisible());
        parent.set(true);
        assertTrue(child.isVisible());
        IntegerSetting broken = g.slider("broken", 1, 0, 2).visibleWhen(() -> {
            throw new IllegalStateException("addon bug");
        });
        assertTrue(broken.isVisible(), "a failing condition does not hide the setting");
    }

    @Test
    void enumOptionsAreLowerCaseIdsAndStoredByName() {
        SettingsGroup g = new SettingsGroup("test.");
        EnumSetting<Mode> mode = g.dropdown("mode", Mode.HOLD);
        assertEquals(List.of("hold", "toggle", "double_tap"), mode.optionIds());
        assertEquals("test.mode.double_tap", mode.optionKey("double_tap"));
        assertTrue(mode.select("double_tap"));
        assertSame(Mode.DOUBLE_TAP, mode.get());
        assertEquals("double_tap", mode.encode());
        assertFalse(mode.select("nope"));
        mode.cycle(1);
        assertSame(Mode.HOLD, mode.get(), "wraps around");
        assertTrue(mode.decode("TOGGLE"));
        assertSame(Mode.TOGGLE, mode.get());
        assertFalse(mode.decode("removed_constant"));
        assertSame(Mode.TOGGLE, mode.get());
    }

    @Test
    void integerStepsAndUnits() {
        SettingsGroup g = new SettingsGroup("test.");
        IntegerSetting size = g.slider("size", 100, 50, 200).step(5).unit("%");
        size.set(103);
        assertEquals(105, size.get());
        size.set(201);
        assertEquals(200, size.get());
        assertEquals("%", size.unit());
        IntegerSetting odd = g.slider("odd", 0, 0, 7).step(5);
        odd.set(7);
        assertEquals(5, odd.get(), "never above the maximum");
    }

    @Test
    void chromaIsStoredWithTheColorAndAppliedBeforeChromaIsOffered() {
        Map<String, String> store = new HashMap<>();
        store.put("color", "#ff112233 chroma 8");
        SettingsGroup g = new SettingsGroup("test.");
        AtomicInteger changes = new AtomicInteger();
        SettingsBinder binder = new SettingsBinder(g, () -> store, s -> changes.incrementAndGet());
        // the stored value is applied when the setting is added, chroma() comes after that
        ColorSetting c = g.color("color", 0xFFFFFFFF, true).chroma();
        assertTrue(c.isChroma());
        assertEquals(8, c.chromaSpeed());
        assertEquals(0xFF112233, c.get());
        assertTrue(c.argbAt(0) != c.argbAt(700), "the rainbow moves");
        assertEquals(0xFF000000, c.argbAt(123) & 0xFF000000, "alpha of the base color");
        c.setChroma(false);
        assertEquals("#FF112233", store.get("color"), "switching the rainbow off is stored");
        assertEquals(1, changes.get());
        c.setChromaSpeed(3);
        c.setChroma(true);
        assertEquals("#FF112233 chroma 3", store.get("color"));
        c.reset();
        assertFalse(c.isChroma(), "reset clears the rainbow");
        assertEquals(0xFFFFFFFF, c.argb());
        ColorSetting plain = g.color("plain", 0xFF000000, false);
        plain.setChroma(true);
        assertFalse(plain.isChroma(), "only where chroma() offers it");
        binder.close();
    }

    @Test
    void buttonsRunTheirActionAndAreNeverStored() {
        Map<String, String> store = new HashMap<>();
        store.put("reset", "true");
        SettingsGroup g = new SettingsGroup("test.");
        new SettingsBinder(g, () -> store, s -> { });
        AtomicInteger pressed = new AtomicInteger();
        ButtonSetting b = g.button("reset", pressed::incrementAndGet);
        assertFalse(b.isPersistent());
        b.press();
        b.press();
        assertEquals(2, pressed.get());
        b.set(true);
        assertEquals(Boolean.FALSE, b.get());
        assertEquals("true", store.get("reset"), "the binder leaves buttons alone");
        assertNull(store.get("other"));
    }
}
