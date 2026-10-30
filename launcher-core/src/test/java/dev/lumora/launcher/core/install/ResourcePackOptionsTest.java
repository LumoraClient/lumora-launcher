package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.profile.ProfileCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackOptionsTest {
    @TempDir
    Path tmp;

    static ProfileCatalog.Artifact pack(String file, String replaces) {
        ProfileCatalog.Artifact a = new ProfileCatalog.Artifact();
        a.id = file;
        a.fileName = file;
        a.type = "resourcepack";
        a.replaces = replaces;
        return a;
    }

    String packsLine() throws IOException {
        return Files.readAllLines(tmp.resolve("options.txt"), StandardCharsets.UTF_8).stream()
                .filter(l -> l.startsWith("resourcePacks:")).findFirst().orElseThrow();
    }

    @Test
    void activatesNewPacksAboveVanillaAndReplacesOldManualCopies() throws IOException {
        Files.writeString(tmp.resolve("options.txt"), "fov:0.0\nresourcePacks:[\"vanilla\",\"fabric\",\"file/Better-Leaves-9.5.zip\",\"file/MyOwn.zip\"]\nlang:de_de\n");
        ResourcePackOptions.apply(tmp, List.of(pack("better-leaves-9.6.zip", "Better-Leaves-"), pack("icons-1.14.zip", "Icons_v")));
        assertEquals("resourcePacks:[\"vanilla\",\"fabric\",\"file/better-leaves-9.6.zip\",\"file/icons-1.14.zip\",\"file/MyOwn.zip\"]", packsLine());
        assertTrue(Files.readString(tmp.resolve("options.txt")).contains("lang:de_de"), "other options stay");
    }

    @Test
    void respectsPacksSwitchedOffInTheGameAndRemovesDroppedOnes() throws IOException {
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null), pack("b-1.zip", null)));
        assertEquals("resourcePacks:[\"vanilla\",\"file/a-1.zip\",\"file/b-1.zip\"]", packsLine());
        // the player turns "a" off in the game
        Files.writeString(tmp.resolve("options.txt"), "resourcePacks:[\"vanilla\",\"file/b-1.zip\"]\n");
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null), pack("b-1.zip", null)));
        assertEquals("resourcePacks:[\"vanilla\",\"file/b-1.zip\"]", packsLine(), "not forced back on");
        // "b" is updated to a new file and "a" switched off in the launcher
        ResourcePackOptions.apply(tmp, List.of(pack("b-2.zip", "b-")));
        assertEquals("resourcePacks:[\"vanilla\",\"file/b-2.zip\"]", packsLine());
    }

    @Test
    void themeIsSwitchedOnOnceWhereFabricDoesNotDoIt() throws IOException {
        Files.writeString(tmp.resolve("options.txt"), "resourcePacks:[\"vanilla\"]\n");
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null)), true);
        assertEquals("resourcePacks:[\"vanilla\",\"file/a-1.zip\",\"lumora:lumora_theme\"]", packsLine(), "theme on top");
        // the player switches the theme off in the game: it stays off
        Files.writeString(tmp.resolve("options.txt"), "resourcePacks:[\"vanilla\",\"file/a-1.zip\"]\n");
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null)), true);
        assertEquals("resourcePacks:[\"vanilla\",\"file/a-1.zip\"]", packsLine());
        // versions where Fabric enables it by itself: the launcher leaves the theme alone
        Path other = tmp.resolve("other");
        Files.createDirectories(other);
        Files.writeString(other.resolve("options.txt"), "resourcePacks:[\"vanilla\"]\n");
        ResourcePackOptions.apply(other, List.of(), false);
        assertEquals("resourcePacks:[\"vanilla\"]", Files.readAllLines(other.resolve("options.txt")).getFirst());
    }

    @Test
    void packsComeBackAfterMinecraftClearedTheListOnALoadError() throws IOException {
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null)));
        Files.writeString(tmp.resolve("options.txt"), "resourcePacks:[]\n");
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null)));
        assertEquals("resourcePacks:[\"file/a-1.zip\"]", packsLine());
        // but a player who only kept the Lumora theme switched our packs off on purpose
        Files.writeString(tmp.resolve("options.txt"), "resourcePacks:[\"lumora:lumora_theme\"]\n");
        ResourcePackOptions.apply(tmp, List.of(pack("a-1.zip", null)));
        assertEquals("resourcePacks:[\"lumora:lumora_theme\"]", packsLine());
    }

    @Test
    void catalogResolvesPacksAndTheirRequiredMods() throws IOException {
        ProfileCatalog c = ProfileCatalog.loadBundled();
        ProfileCatalog.Profile p = c.profile("1.21.8").orElseThrow();
        assertEquals(7, c.resolvePacks(p, List.of()).size());
        assertEquals(6, c.resolvePacks(c.profile("1.21.11").orElseThrow(), List.of()).size(), "no Fresh Food on 1.21.11");
        List<String> mods = c.resolveMods(p, List.of(), List.of()).stream().map(a -> a.key()).toList();
        assertTrue(mods.containsAll(List.of("emf", "etf", "nuit")), "Fresh Animations needs EMF+ETF, Dramatic Skys needs Nuit: " + mods);
        List<String> without = c.resolveMods(p, List.of(), List.of("fresh-animations", "dramatic-skys")).stream().map(a -> a.key()).toList();
        assertTrue(without.stream().noneMatch(k -> k.equals("emf") || k.equals("etf") || k.equals("nuit")), without.toString());
        assertTrue(c.allResourcePackKeys().contains("enhanced-boss-bars"));
        assertEquals(8, c.resolvePacks(c.profile("26.1.2").orElseThrow(), List.of()).size(), "Enhanced Boss Bars exists only for 26.1.2");
    }
}
