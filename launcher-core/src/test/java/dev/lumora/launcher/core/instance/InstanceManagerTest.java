package dev.lumora.launcher.core.instance;

import dev.lumora.launcher.core.InstanceLayout;
import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceManagerTest {
    @TempDir
    Path tmp;

    InstanceManager manager() throws IOException {
        return new InstanceManager(new LauncherPaths(tmp.resolve("data")), ProfileCatalog.loadBundled());
    }

    static Instance spec(String name, InstanceKind kind, String mc) {
        Instance i = new Instance();
        i.name = name;
        i.kind = kind.id();
        i.minecraftVersion = mc;
        return i;
    }

    @Test
    void lumoraVersionsAreBuiltInInstances() throws IOException {
        InstanceManager m = manager();
        LauncherConfig c = new LauncherConfig();
        c.selectedProfile = "26.3";
        List<Instance> builtIns = m.builtIns(c);
        assertTrue(builtIns.stream().allMatch(i -> i.builtIn && i.kind() == InstanceKind.LUMORA), "no Vanilla instance before it was used");
        Instance lumora = builtIns.stream().filter(i -> i.id.equals("26.3")).findFirst().orElseThrow();
        assertEquals("26.3", lumora.key());
        assertEquals(lumora.key(), m.selected(c).key(), "the selected version is the selected instance");

        c.selectedFlavor = "vanilla";
        assertTrue(m.builtIns(c).stream().anyMatch(i -> i.id.equals("26.3-vanilla")));
        assertEquals("26.3-vanilla", m.selected(c).key());

        InstanceManager.select(c, lumora);
        assertEquals("26.3", c.selectedInstance);
        assertEquals("lumora", c.selectedFlavor, "selecting a built-in instance keeps the version menu in sync");
        assertThrows(IOException.class, () -> m.delete(lumora), "built-in instances cannot be deleted");
    }

    @Test
    void ownInstancesAreCreatedRenamedCopiedAndDeleted() throws IOException {
        InstanceManager m = manager();
        LauncherConfig c = new LauncherConfig();
        Instance a = m.create(spec("Fabric 1.21.4 – Test", InstanceKind.FABRIC, "1.21.4"));
        assertEquals("fabric-1-21-4-test", a.id);
        assertTrue(Files.isDirectory(a.gameDir()));
        assertEquals("custom:fabric-1-21-4-test", a.key());
        Instance b = m.create(spec("Fabric 1.21.4 – Test", InstanceKind.FABRIC, "1.21.4"));
        assertEquals("fabric-1-21-4-test-2", b.id, "same name, own folder");

        Files.createDirectories(a.gameDir().resolve("mods"));
        Files.writeString(a.gameDir().resolve("mods/a.jar"), "x");
        Files.createDirectories(a.gameDir().resolve("logs"));
        Files.writeString(a.gameDir().resolve("logs/latest.log"), "x");
        Instance copy = m.duplicate(c, a, "Kopie");
        assertTrue(Files.isRegularFile(copy.gameDir().resolve("mods/a.jar")));
        assertFalse(Files.exists(copy.gameDir().resolve("logs")), "logs stay with the original");
        Files.writeString(copy.gameDir().resolve("mods/b.jar"), "y");
        assertFalse(Files.exists(a.gameDir().resolve("mods/b.jar")), "a copy is independent");

        Instance renamed = m.rename(a, "Neu");
        assertEquals("Neu", m.find(c, renamed.key()).orElseThrow().name);
        assertEquals(a.id, renamed.id, "renaming keeps the folder");

        InstanceLayout layout = m.layout(c, copy);
        assertEquals(copy.gameDir(), layout.gameDir());
        assertNotEquals(layout.gameDir(), layout.lumoraDir(), "Lumora settings of an own instance live next to the game folder");

        m.delete(b);
        assertFalse(Files.exists(b.dir));
        assertEquals(2, m.own().size());
    }

    @Test
    void invalidInputIsRefusedWithAReason() throws IOException {
        InstanceManager m = manager();
        assertThrows(IOException.class, () -> m.create(spec(" ", InstanceKind.FABRIC, "1.21.4")));
        assertThrows(IOException.class, () -> m.create(spec("x".repeat(70), InstanceKind.FABRIC, "1.21.4")));
        assertThrows(IOException.class, () -> m.create(spec("a", InstanceKind.FABRIC, "../1.21")));
        assertThrows(IOException.class, () -> m.create(spec("a", InstanceKind.LUMORA, "1.19.2")), "Lumora only for its versions");
        Instance bad = spec("a", InstanceKind.FABRIC, "1.21.4");
        bad.maxMemoryMb = 10;
        assertThrows(IOException.class, () -> m.create(bad));
        assertTrue(m.find(new LauncherConfig(), "custom:../x").isEmpty());
        assertEquals("instance", m.freeId("!!!"));
        assertEquals("aeoeue-ss", m.freeId("ÄÖÜ ß"));
    }
}
