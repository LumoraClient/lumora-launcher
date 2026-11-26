package dev.lumora.launcher.core.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumoraAddonTest {
    @TempDir
    Path tmp;

    @Test
    void fabricAndQuiltGetTheJarAsPropertyNothingIsCopied() throws Exception {
        Path game = tmp.resolve("game");
        Path jar = Files.writeString(tmp.resolve("lumora-client-mc1.21.11-fabric.jar"), "fabric");
        List<String> fabric = LumoraAddon.apply(game, "fabric", jar);
        assertEquals(List.of("-Dfabric.addMods=" + jar.toAbsolutePath()), fabric);
        List<String> quilt = LumoraAddon.apply(game, "quilt", jar);
        assertTrue(quilt.contains("-Dloader.addMods=" + jar.toAbsolutePath()), quilt.toString());
        assertFalse(Files.exists(game.resolve("mods").resolve(jar.getFileName())), "no copy in mods/");
        assertEquals(List.of(), LumoraAddon.apply(game, "fabric", null), "switched off: nothing added");
    }

    @Test
    void forgeGetsAManagedCopyThatIsReplacedAndRemoved() throws Exception {
        Path game = tmp.resolve("game");
        Path mods = Files.createDirectories(game.resolve("mods"));
        Files.writeString(mods.resolve("jei-1.21.11-forge.jar"), "player's mod");
        Files.writeString(mods.resolve("lumora-client-mc1.21.8-forge.jar"), "old version");
        Path jar = Files.writeString(tmp.resolve("lumora-client-mc1.21.11-forge.jar"), "v1");

        assertEquals(List.of(), LumoraAddon.apply(game, "neoforge", jar));
        assertEquals("v1", Files.readString(mods.resolve("lumora-client-mc1.21.11-forge.jar")));
        assertFalse(Files.exists(mods.resolve("lumora-client-mc1.21.8-forge.jar")), "an old managed copy goes");

        Files.writeString(jar, "v2 (new launcher)");
        LumoraAddon.apply(game, "forge", jar);
        assertEquals("v2 (new launcher)", Files.readString(mods.resolve("lumora-client-mc1.21.11-forge.jar")));

        LumoraAddon.apply(game, "forge", null);
        assertFalse(Files.exists(mods.resolve("lumora-client-mc1.21.11-forge.jar")), "switched off: the copy goes");
        assertEquals("player's mod", Files.readString(mods.resolve("jei-1.21.11-forge.jar")), "other mods stay");
        assertEquals("forge", ClientInstaller.lumoraVariant("neoforge"));
        assertEquals("fabric", ClientInstaller.lumoraVariant("quilt"));
        assertEquals(null, ClientInstaller.lumoraVariant("lumora"));
        assertEquals("lumora-client-mc26.3-forge.jar", ClientInstaller.variantJarName("26.3", "forge"));
    }
}
