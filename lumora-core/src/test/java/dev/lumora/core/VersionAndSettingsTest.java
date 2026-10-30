package dev.lumora.core;

import dev.lumora.api.Version;
import dev.lumora.api.VersionRange;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.KeybindSetting;
import dev.lumora.api.config.SettingType;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.config.TextSetting;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.Colors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionAndSettingsTest {
    @Test
    void versionsCompareSemantically() {
        assertTrue(Version.parse("1.10.0").compareTo(Version.parse("1.9.9")) > 0);
        assertEquals(Version.parse("1.2"), Version.parse("1.2.0"));
        assertTrue(Version.parse("1.0.0-beta").compareTo(Version.parse("1.0.0")) < 0);
        assertEquals(Version.parse("1.0.0+build5"), Version.parse("1.0.0"));
        assertNull(Version.tryParse("1.x"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3.4"));
    }

    @Test
    void rangesMatch() {
        assertTrue(VersionRange.parse("*").matches(Version.parse("9.9.9")));
        assertTrue(VersionRange.parse(">=1.2 <2").matches(Version.parse("1.5.0")));
        assertFalse(VersionRange.parse(">=1.2 <2").matches(Version.parse("2.0.0")));
        assertTrue(VersionRange.parse("1.2.x").matches(Version.parse("1.2.7")));
        assertFalse(VersionRange.parse("1.2.x").matches(Version.parse("1.3.0")));
        assertTrue(VersionRange.parse("^1.2.0").matches(Version.parse("1.9.0")));
        assertFalse(VersionRange.parse("^1.2.0").matches(Version.parse("2.0.0")));
        assertFalse(VersionRange.parse("~1.2.0").matches(Version.parse("1.3.0")));
        assertTrue(VersionRange.parse("1.0.0").matches(Version.parse("1.0")));
        assertThrows(IllegalArgumentException.class, () -> VersionRange.parse("latest"));
    }

    @Test
    void minecraftPatterns() {
        AddonDescriptor d = new AddonDescriptor("x", "x", Version.parse("1"), "", List.of(), Version.parse("1"),
                List.of("26.1.2", "1.21.x"), "a.B", List.of(), Set.of(), false, "t");
        assertTrue(d.supportsMinecraft("26.1.2"));
        assertTrue(d.supportsMinecraft("1.21.8"));
        assertTrue(d.supportsMinecraft("1.21.11"));
        assertFalse(d.supportsMinecraft("1.20.4"));
        assertFalse(d.supportsMinecraft("1.211"));
    }

    @Test
    void numbersAreClampedAndInvalidValuesRejected() {
        IntegerSetting i = new IntegerSetting("count", 5, 1, 10, false);
        assertTrue(i.set(50));
        assertEquals(10, i.get());
        assertFalse(i.decode("abc"));
        assertEquals(10, i.get());
        DecimalSetting d = new DecimalSetting("opacity", 0.5, 0, 1, 0.05, true);
        assertEquals(SettingType.SLIDER, d.type());
        d.set(0.33);
        assertEquals(0.35, d.get(), 1e-9);
        assertFalse(d.set(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new IntegerSetting("bad", 20, 1, 10, false));
    }

    @Test
    void textChoiceColorKeybind() {
        TextSetting t = new TextSetting("label", "Hi", 5);
        t.set("ab\ncdefgh");
        assertEquals("abcde", t.get());
        ChoiceSetting c = new ChoiceSetting("mode", "a", List.of("a", "b", "c"));
        assertFalse(c.set("z"));
        c.cycle(-1);
        assertEquals("c", c.get());
        assertFalse(c.decode("nope"));
        ColorSetting col = new ColorSetting("color", 0x18B4CC, false);
        assertEquals(0xFF18B4CC, col.get());
        assertTrue(col.decode("#80FF0000"));
        assertEquals(0xFFFF0000, col.get(), "no alpha: always opaque");
        assertEquals("#FFFF0000", col.encode());
        assertEquals(0x8011AA22, Colors.parseHex("8011AA22"));
        KeybindSetting k = new KeybindSetting("key", Keys.C);
        assertFalse(k.set(9999));
        assertTrue(k.set(Keys.UNBOUND));
    }

    @Test
    void groupRejectsDuplicatesAndNamesKeys() {
        SettingsGroup g = new SettingsGroup("addon.widget.w.");
        BooleanSetting b = g.toggle("shadow", true);
        assertEquals("addon.widget.w.shadow", b.nameKey());
        assertEquals("addon.widget.w.shadow.description", b.descriptionKey());
        assertThrows(IllegalArgumentException.class, () -> g.toggle("shadow", false));
        assertThrows(IllegalArgumentException.class, () -> g.toggle("Bad Key", false));
        List<Boolean> seen = new ArrayList<>();
        b.onChange(seen::add);
        b.toggle();
        b.set(false); // unchanged -> no event
        assertEquals(List.of(false), seen);
    }

    @Test
    void boundSettingUsesOwnerField() {
        float[] field = {0.85f};
        DecimalSetting s = new DecimalSetting("opacity", 0.85, 0.1, 1, 0.05, true);
        s.bind(() -> (double) field[0], v -> field[0] = (float) (double) v);
        assertTrue(s.isBound());
        s.set(0.5);
        assertEquals(0.5f, field[0]);
        field[0] = 0.3f;
        assertEquals(0.3, s.get(), 1e-6);
    }
}
