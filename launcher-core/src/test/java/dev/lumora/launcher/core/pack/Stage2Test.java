package dev.lumora.launcher.core.pack;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.content.ContentManifest;
import dev.lumora.launcher.core.content.ContentTransfer;
import dev.lumora.launcher.core.content.ContentUpdates;
import dev.lumora.launcher.core.content.InstalledContent;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.instance.BackupManager;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.platform.ModRepository;
import dev.lumora.launcher.core.platform.ModrinthProvider;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.testutil.LocalServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Updates, backups, export, launcher import and transfer. */
class Stage2Test {
    @TempDir
    Path tmp;

    InstanceManager instances() throws IOException {
        return new InstanceManager(new LauncherPaths(tmp.resolve("data")), ProfileCatalog.loadBundled());
    }

    static Instance spec(String name, InstanceKind kind, String mc) {
        Instance i = new Instance();
        i.name = name;
        i.kind = kind.id();
        i.minecraftVersion = mc;
        return i;
    }

    static byte[] jar(String fabricJson) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("fabric.mod.json"));
            z.write(fabricJson.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    static String sha1(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(b));
    }

    static String sha512(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(b));
    }

    @Test
    void updatesAreFoundByHashAndPinnedFilesAreLeftAlone() throws Exception {
        Path game = tmp.resolve("game");
        Files.createDirectories(game.resolve("mods"));
        byte[] old = jar("{\"id\":\"sodium\",\"name\":\"Sodium\",\"version\":\"0.5\"}");
        byte[] other = jar("{\"id\":\"lithium\",\"name\":\"Lithium\",\"version\":\"0.1\"}");
        Files.write(game.resolve("mods/sodium-0.5.jar"), old);
        Files.write(game.resolve("mods/lithium-0.1.jar"), other);
        String oldSha = sha1(old);
        String otherSha = sha1(other);
        byte[] newer = "new sodium".getBytes(StandardCharsets.UTF_8);
        String newSha1 = sha1(newer);
        String newSha512 = sha512(newer);
        String otherSha512 = sha512(other);
        try (LocalServer s = new LocalServer()) {
            s.on("/v2/version_files/update", (ex, n) -> LocalServer.Response.json(200, """
                    {"%s":{"id":"v6","project_id":"AANobbMI","name":"Sodium 0.6","version_number":"0.6","version_type":"release",
                      "loaders":["fabric"],"game_versions":["1.21.4"],"files":[{"primary":true,"filename":"sodium-0.6.jar","url":"https://cdn.modrinth.com/x",
                      "size":10,"hashes":{"sha1":"%s","sha512":"%s"}}],"dependencies":[]},
                     "%s":{"id":"l1","project_id":"gvQqBUqZ","name":"Lithium","version_number":"0.1","version_type":"release",
                      "loaders":["fabric"],"game_versions":["1.21.4"],"files":[{"primary":true,"filename":"lithium-0.1.jar","url":"https://cdn.modrinth.com/y",
                      "size":10,"hashes":{"sha1":"%s","sha512":"%s"}}],"dependencies":[]}}""".formatted(oldSha, newSha1, newSha512,
                    otherSha, otherSha, otherSha512)));
            ModRepository repo = new ModRepository(List.of(new ModrinthProvider(
                    new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 2), s.uri("/v2").toString())));
            List<ContentUpdates.Update> updates = ContentUpdates.check(repo, InstanceKind.FABRIC, "1.21.4", game);
            assertEquals(1, updates.size(), "Lithium is up to date");
            assertEquals("0.6", updates.getFirst().latest().number());
            assertEquals("0.5", updates.getFirst().current());

            InstalledContent.Item sodium = InstalledContent.scan(game, ProjectType.MOD, ContentManifest.load(game)).stream()
                    .filter(i -> i.fileName().startsWith("sodium")).findFirst().orElseThrow();
            ContentUpdates.setPinned(game, sodium, true);
            assertTrue(ContentUpdates.check(repo, InstanceKind.FABRIC, "1.21.4", game).isEmpty(), "a pinned file gets no update offer");
        }
    }

    @Test
    void backupsRestoreTheInstanceAndRefuseTheSharedFolder() throws IOException {
        InstanceManager im = instances();
        LauncherConfig config = new LauncherConfig();
        Instance i = im.create(spec("Backup Test", InstanceKind.FABRIC, "1.21.4"));
        Files.createDirectories(i.gameDir().resolve("mods"));
        Files.writeString(i.gameDir().resolve("mods/a.jar"), "a");
        Files.createDirectories(i.gameDir().resolve("logs"));
        Files.writeString(i.gameDir().resolve("logs/latest.log"), "log");
        BackupManager bm = new BackupManager(new LauncherPaths(tmp.resolve("data")));
        BackupManager.Backup b = bm.create(i, im.layout(config, i), null);
        try (ZipFile z = new ZipFile(b.file().toFile())) {
            assertTrue(z.getEntry("minecraft/mods/a.jar") != null);
            assertTrue(z.getEntry("instance.json") != null);
            assertEquals(null, z.getEntry("minecraft/logs/latest.log"), "logs are not backed up");
        }
        Files.delete(i.gameDir().resolve("mods/a.jar"));
        Files.writeString(i.gameDir().resolve("mods/b.jar"), "b");
        bm.restore(i, im.layout(config, i), b);
        assertTrue(Files.exists(i.gameDir().resolve("mods/a.jar")));
        assertFalse(Files.exists(i.gameDir().resolve("mods/b.jar")));
        assertEquals(2, bm.list(i).size(), "the state before the restore was backed up first");

        Instance builtIn = im.find(config, "26.3").orElseThrow();
        assertThrows(IOException.class, () -> bm.create(builtIn, im.layout(config, builtIn), null), "the shared standard folder is not backed up");
    }

    @Test
    void exportMrpackAndArchiveRoundTrip() throws Exception {
        InstanceManager im = instances();
        Instance i = im.create(spec("Export Test", InstanceKind.FABRIC, "1.21.4"));
        Path game = i.gameDir();
        Files.createDirectories(game.resolve("mods"));
        byte[] fromModrinth = "mr".getBytes(StandardCharsets.UTF_8);
        Files.write(game.resolve("mods/mr.jar"), fromModrinth);
        Files.writeString(game.resolve("mods/own.jar"), "own");
        Files.createDirectories(game.resolve("config"));
        Files.writeString(game.resolve("config/x.toml"), "x");
        Files.createDirectories(game.resolve("saves/World"));
        Files.writeString(game.resolve("saves/World/level.dat"), "w");
        ContentManifest m = ContentManifest.load(game);
        ContentManifest.Entry e = new ContentManifest.Entry();
        e.platform = "modrinth";
        e.projectId = "AAAAAAAA";
        e.url = "https://cdn.modrinth.com/data/AAAAAAAA/versions/BBBBBBBB/mr.jar";
        e.sha1 = sha1(fromModrinth);
        e.sha512 = sha512(fromModrinth);
        m.put("mods/mr.jar", e);
        m.save();

        Path out = tmp.resolve("export.mrpack");
        InstanceExporter.Result r = InstanceExporter.exportMrpack(i, game, "0.16.9", "My Pack", "1.0",
                new InstanceExporter.MrpackOptions(true, false, false), null, out);
        assertEquals(1, r.downloads());
        MrpackImporter.Preview p = MrpackImporter.analyze(out);
        assertTrue(p.importable(), p.problems().toString());
        assertEquals(InstanceKind.FABRIC, p.kind());
        assertEquals("0.16.9", p.loaderVersion());
        assertEquals(1, p.count("mods"));
        try (ZipFile z = new ZipFile(out.toFile())) {
            assertTrue(z.getEntry("overrides/mods/own.jar") != null, "files not on Modrinth go into the overrides");
            assertTrue(z.getEntry("overrides/config/x.toml") != null);
            assertEquals(null, z.getEntry("overrides/saves/World/level.dat"), "worlds are not part of a modpack");
        }
        Instance lumora = im.create(spec("L", InstanceKind.LUMORA, "26.3"));
        assertThrows(IOException.class, () -> InstanceExporter.exportMrpack(lumora, lumora.gameDir(), null, "x", "1",
                new InstanceExporter.MrpackOptions(false, false, false), null, tmp.resolve("l.mrpack")));

        Path archive = tmp.resolve("export.zip");
        InstanceExporter.exportArchive(i, game, archive);
        assertTrue(LauncherImport.isLumoraArchive(archive));
        assertEquals(FileClassifier.Kind.INSTANCE_ARCHIVE, FileClassifier.classify(archive).kind());
        Instance copy = LauncherImport.importArchive(archive, "Imported", im);
        assertEquals(InstanceKind.FABRIC, copy.kind());
        assertTrue(Files.exists(copy.gameDir().resolve("saves/World/level.dat")), "an archive keeps worlds");
        assertTrue(Files.exists(copy.gameDir().resolve(".lumora/content.json")), "and the origin of the files");
    }

    @Test
    void otherLaunchersAreFoundAndImported() throws IOException {
        Path prism = tmp.resolve("prism/instances/MyPack");
        Files.createDirectories(prism.resolve(".minecraft/mods"));
        Files.writeString(prism.resolve("instance.cfg"), "InstanceType=OneSix\nname=My Prism Pack\n");
        Files.writeString(prism.resolve("mmc-pack.json"), """
                {"components":[{"uid":"net.minecraft","version":"1.20.1"},{"uid":"net.minecraftforge","version":"47.2.0"}],"formatVersion":1}""");
        Files.writeString(prism.resolve(".minecraft/mods/a.jar"), "a");
        Files.createDirectories(prism.resolve(".minecraft/logs"));
        Files.writeString(prism.resolve(".minecraft/logs/latest.log"), "l");
        Files.createDirectories(prism.resolve(".minecraft/saves/W"));
        Files.writeString(prism.resolve(".minecraft/saves/W/level.dat"), "w");

        Path cf = tmp.resolve("cf/Instances/CF One");
        Files.createDirectories(cf.resolve("mods"));
        Files.writeString(cf.resolve("minecraftinstance.json"), "{\"name\":\"CF One\",\"gameVersion\":\"1.21.1\",\"baseModLoader\":{\"name\":\"neoforge-21.1.80\"}}");

        Path official = tmp.resolve("official");
        Path ownDir = tmp.resolve("official-game");
        Files.createDirectories(ownDir);
        Files.createDirectories(official);
        Files.writeString(official.resolve("launcher_profiles.json"), """
                {"profiles":{"a":{"name":"Fabric","gameDir":"%s","lastVersionId":"fabric-loader-0.16.9-1.21.4"},
                 "b":{"name":"Latest","lastVersionId":"latest-release"}}}""".formatted(ownDir.toString().replace("\\", "\\\\")));

        List<LauncherImport.Found> fromPrism = LauncherImport.scan(LauncherImport.Source.PRISM, tmp.resolve("prism/instances"));
        assertEquals(1, fromPrism.size());
        LauncherImport.Found f = fromPrism.getFirst();
        assertEquals("My Prism Pack", f.name());
        assertEquals(InstanceKind.FORGE, f.kind());
        assertEquals("1.20.1-47.2.0", f.loaderVersion());
        LauncherImport.Found c = LauncherImport.scan(LauncherImport.Source.CURSEFORGE, tmp.resolve("cf/Instances")).getFirst();
        assertEquals(InstanceKind.NEOFORGE, c.kind());
        assertEquals("21.1.80", c.loaderVersion());
        assertEquals("0.18.4", CurseForgePackImporter.loader("fabric-0.18.4-1.20.1", "1.20.1")[1], "the CurseForge app appends the Minecraft version");
        assertEquals("1.20.1-47.2.0", CurseForgePackImporter.loader("forge-47.2.0", "1.20.1")[1]);
        List<LauncherImport.Found> off = LauncherImport.scan(LauncherImport.Source.OFFICIAL, official.resolve("launcher_profiles.json"));
        assertEquals(1, off.size(), "only profiles with an own game folder");
        assertEquals(InstanceKind.FABRIC, off.getFirst().kind());
        assertEquals("1.21.4", off.getFirst().minecraftVersion());
        assertEquals(3, LauncherImport.scan(LauncherImport.Source.FOLDER, tmp).size(), "a folder scan finds all of them");

        InstanceManager im = instances();
        Instance imported = LauncherImport.importFound(f, f.name(), false, im, null);
        assertTrue(Files.exists(imported.gameDir().resolve("mods/a.jar")));
        assertFalse(Files.exists(imported.gameDir().resolve("logs")), "logs stay behind");
        assertFalse(Files.exists(imported.gameDir().resolve("saves")), "worlds only when asked");
        assertTrue(Files.exists(prism.resolve(".minecraft/mods/a.jar")), "the other launcher is only read");
    }

    @Test
    void transferCopiesCompatibleFilesWithTheirOrigin() throws Exception {
        Path from = tmp.resolve("from");
        Path to = tmp.resolve("to");
        Files.createDirectories(from.resolve("mods"));
        Files.write(from.resolve("mods/fab.jar"), jar("{\"id\":\"fab\",\"name\":\"Fab\"}"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            z.write("[[mods]]\nmodId=\"forgy\"\n".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        Files.write(from.resolve("mods/forgy.jar"), bos.toByteArray());
        ContentManifest m = ContentManifest.load(from);
        ContentManifest.Entry e = new ContentManifest.Entry();
        e.platform = "modrinth";
        e.projectId = "FABFABFA";
        m.put("mods/fab.jar", e);
        m.save();
        List<InstalledContent.Item> items = InstalledContent.scan(from, ProjectType.MOD, ContentManifest.load(from));
        ContentTransfer.Result r = ContentTransfer.copy(items, from, InstanceKind.QUILT, "1.21.4", to);
        assertEquals(List.of("Fab"), r.copied(), "Quilt takes the Fabric mod");
        assertEquals(1, r.skipped().size(), "the Forge mod is skipped with a reason");
        assertEquals("FABFABFA", ContentManifest.load(to).get("mods/fab.jar").orElseThrow().projectId);
        ContentTransfer.Result again = ContentTransfer.copy(items, from, InstanceKind.QUILT, "1.21.4", to);
        assertTrue(again.copied().isEmpty(), "nothing is overwritten");
    }
}
