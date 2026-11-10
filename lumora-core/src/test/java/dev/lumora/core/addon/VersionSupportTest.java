package dev.lumora.core.addon;

import dev.lumora.api.Version;
import dev.lumora.api.addon.AddonDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionSupportTest {
    public static AddonDescriptor addon(String api, List<String> authors, String... minecraft) {
        return new AddonDescriptor("glint", "GlintColorizer", Version.parse("1.0.0"), "", authors, Version.parse(api),
                List.of(minecraft), "com.example.Glint", List.of(), Set.of(), false, "glint.jar");
    }

    @Test
    void versionsAreComparedPartByPart() {
        assertTrue(VersionSupport.compare("1.8.9", "1.12.2") < 0);
        assertTrue(VersionSupport.compare("1.21", "1.21.11") < 0);
        assertTrue(VersionSupport.compare("1.21.11", "26.1") < 0);
        assertEquals(0, VersionSupport.compare("1.21.x", "1.21.4"));
        assertEquals(0, VersionSupport.compare("1.21", "1.21.0"));
    }

    @Test
    void theReasonSaysWhetherTheVersionIsTooOldOrTooNew() {
        VersionSupport.Problem tooNew = VersionSupport.minecraft(List.of("1.21.11", "1.8.9", "1.21.4"), "26.2");
        assertEquals(VersionSupport.Kind.TOO_NEW, tooNew.kind());
        assertEquals(List.of("1.8.9", "1.21.4", "1.21.11"), tooNew.supported(), "sorted oldest first");
        assertEquals("1.21.11", tooNew.newest());
        assertTrue(tooNew.describe(true, "GlintColorizer").get(1).contains("zu neu"));
        assertTrue(tooNew.describe(false, "GlintColorizer").get(0)
                .equals("GlintColorizer is not (yet) compatible with Minecraft 26.2."));

        VersionSupport.Problem tooOld = VersionSupport.minecraft(List.of("1.20.x", "26.3"), "1.8.9");
        assertEquals(VersionSupport.Kind.TOO_OLD, tooOld.kind());
        assertTrue(tooOld.describe(true, "X").get(1).contains("zu alt") && tooOld.describe(true, "X").get(1).contains("1.20."));

        VersionSupport.Problem gap = VersionSupport.minecraft(List.of("1.8.9", "26.3"), "1.20.1");
        assertEquals(VersionSupport.Kind.NOT_LISTED, gap.kind());
        assertTrue(gap.describe(false, "X").get(1).contains("1.8.9, 26.3"));

        assertNull(VersionSupport.minecraft(List.of("*"), "1.8.9"));
        assertNull(VersionSupport.minecraft(List.of("1.21.x"), "1.21.4"));
    }

    @Test
    void anAddonForANewerLumoraSaysLumoraIsTooOld() {
        VersionSupport.Problem p = VersionSupport.check(addon("1.99.0", List.of(), "*"), "26.2");
        assertEquals(VersionSupport.Kind.LUMORA_TOO_OLD, p.kind());
        assertTrue(p.describe(true, "X").get(1).contains("1.99.0"));
        assertNull(VersionSupport.check(addon("1.0.0", List.of(), "26.2"), "26.2"));
        assertEquals(VersionSupport.Kind.TOO_NEW, VersionSupport.check(addon("1.0.0", List.of(), "1.21.11"), "26.2").kind());
    }

    @Test
    void addonsByOtherAuthorsGetANote() {
        AddonDescriptor user = addon("1.0.0", List.of("SomeUser"), "*");
        assertTrue(VersionSupport.thirdParty(user));
        assertEquals("This addon was made by SomeUser and not by Lumora. We have no control over this.",
                VersionSupport.thirdPartyNote(false, user));
        assertTrue(VersionSupport.thirdPartyNote(true, addon("1.0.0", List.of(), "*")).startsWith("Dieses Addon wurde von einem Nutzer"));
        assertFalse(VersionSupport.thirdParty(addon("1.0.0", List.of("Lumora"), "*")));
    }
}
