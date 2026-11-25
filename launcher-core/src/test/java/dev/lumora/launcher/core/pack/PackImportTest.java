package dev.lumora.launcher.core.pack;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.content.ContentManifest;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.testutil.LocalServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackImportTest {
    @TempDir
    Path tmp;
    LocalServer server;
    final byte[] modBytes = "a mod".getBytes(StandardCharsets.UTF_8);
    volatile boolean corrupt;

    @BeforeEach
    void start() throws IOException {
        server = new LocalServer();
        server.on("/data/", (ex, n) -> LocalServer.Response.ok(corrupt ? "evil".getBytes() : modBytes));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    static String sha512(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-512").digest(b));
    }

    static Path zip(Path file, Map<String, String> entries) throws IOException {
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream z = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return file;
    }

    String index(String path, String deps) throws Exception {
        String url = server.uri("/data/AANobbMI/versions/abc/sodium.jar").toString();
        return """
                {"formatVersion":1,"game":"minecraft","versionId":"1.2","name":"Test Pack","summary":"A pack",
                 "files":[{"path":"%s","hashes":{"sha1":"00","sha512":"%s"},"downloads":["%s"],"fileSize":%d},
                          {"path":"mods/server.jar","hashes":{"sha512":"%s"},"env":{"client":"unsupported","server":"required"},"downloads":["%s"],"fileSize":1}],
                 "dependencies":{%s}}""".formatted(path, sha512(modBytes), url, modBytes.length, sha512(modBytes), url, deps);
    }

    InstanceManager instances() throws IOException {
        return new InstanceManager(new LauncherPaths(tmp.resolve("data")), ProfileCatalog.loadBundled());
    }

    MrpackImporter importer() {
        return new MrpackImporter(new Downloader(HttpClient.newHttpClient(), Set.of(), true, 1, Duration.ofMillis(1), 2));
    }

    @Test
    void safeZipRefusesPathsOutsideTheTarget() throws IOException {
        assertThrows(IOException.class, () -> SafeZip.checkedName("../evil.txt"));
        assertThrows(IOException.class, () -> SafeZip.checkedName("a/../../evil.txt"));
        assertThrows(IOException.class, () -> SafeZip.checkedName("/etc/passwd"));
        assertThrows(IOException.class, () -> SafeZip.checkedName("\\windows\\x"));
        assertThrows(IOException.class, () -> SafeZip.checkedName("C:/x"));
        assertEquals("config/a.toml", SafeZip.checkedName("config\\a.toml"));

        Path evil = zip(tmp.resolve("evil.zip"), Map.of("overrides/../../evil.txt", "x"));
        try (ZipFile z = new ZipFile(evil.toFile())) {
            assertThrows(IOException.class, () -> SafeZip.extract(z, "overrides/", tmp.resolve("out"), r -> true));
        }
        assertFalse(Files.exists(tmp.resolve("evil.txt")));

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("overrides/config/a.toml", "new");
        entries.put("overrides/options.txt", "new");
        Path ok = zip(tmp.resolve("ok.zip"), entries);
        Files.createDirectories(tmp.resolve("out"));
        Files.writeString(tmp.resolve("out/options.txt"), "mine");
        try (ZipFile z = new ZipFile(ok.toFile())) {
            List<SafeZip.Extracted> done = SafeZip.extract(z, "overrides/", tmp.resolve("out"), rel -> !rel.equals("options.txt"));
            assertEquals(List.of("config/a.toml"), done.stream().map(SafeZip.Extracted::path).toList());
        }
        assertEquals("mine", Files.readString(tmp.resolve("out/options.txt")), "existing files stay unless allowed");
    }

    @Test
    void analyzeShowsWhatThePackContainsAndImportBuildsAnInstance() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("modrinth.index.json", index("mods/sodium.jar", "\"minecraft\":\"1.21.4\",\"fabric-loader\":\"0.16.9\""));
        entries.put("overrides/config/sodium.json", "{}");
        entries.put("client-overrides/options.txt", "gamma:1.0");
        Path pack = zip(tmp.resolve("pack.mrpack"), entries);
        assertTrue(MrpackImporter.isMrpack(pack));
        MrpackImporter.Preview p = MrpackImporter.analyze(pack, Set.of(), true);
        assertTrue(p.importable(), p.problems().toString());
        assertEquals("Test Pack", p.name());
        assertEquals("1.21.4", p.minecraftVersion());
        assertEquals(InstanceKind.FABRIC, p.kind());
        assertEquals("0.16.9", p.loaderVersion());
        assertEquals(1, p.count("mods"), "the server-only file is skipped");
        assertEquals(1, p.skippedServerOnly());
        assertEquals(2, p.overrideFiles());

        InstanceManager im = instances();
        Instance inst = importer().importPack(p, p.name(), Set.of(), im, "mrpack", null, null, null);
        assertEquals(InstanceKind.FABRIC, inst.kind());
        assertTrue(inst.loaderPinned);
        Path game = inst.gameDir();
        assertTrue(Files.isRegularFile(game.resolve("mods/sodium.jar")));
        assertEquals("{}", Files.readString(game.resolve("config/sodium.json")));
        assertEquals("gamma:1.0", Files.readString(game.resolve("options.txt")));
        assertEquals(sha512(modBytes), ContentManifest.load(game).get("mods/sodium.jar").orElseThrow().sha512, "pack files are recorded");
        assertEquals(List.of("AANobbMI", "Xy12Ab34"), List.of(MrpackImporter.modrinthIds("https://cdn.modrinth.com/data/AANobbMI/versions/Xy12Ab34/sodium.jar")),
                "files from Modrinth's CDN keep their project for later updates");
        assertEquals(null, MrpackImporter.modrinthIds("https://evil.example/data/AANobbMI/versions/Xy12Ab34/sodium.jar"));
        assertEquals(1, im.own().size());
    }

    @Test
    void brokenPacksAreExplainedAndNothingStaysBehind() throws Exception {
        Path traversal = zip(tmp.resolve("t.mrpack"), Map.of("modrinth.index.json", index("../../evil.jar", "\"minecraft\":\"1.21.4\"")));
        MrpackImporter.Preview t = MrpackImporter.analyze(traversal, Set.of(), true);
        assertFalse(t.importable());

        Path unknownLoader = zip(tmp.resolve("u.mrpack"), Map.of("modrinth.index.json", index("mods/a.jar", "\"minecraft\":\"1.21.4\",\"rift\":\"1\"")));
        assertFalse(MrpackImporter.analyze(unknownLoader, Set.of(), true).importable());

        Path foreignHost = zip(tmp.resolve("h.mrpack"), Map.of("modrinth.index.json", index("mods/a.jar", "\"minecraft\":\"1.21.4\"")));
        assertFalse(MrpackImporter.analyze(foreignHost).importable(), "http://127.0.0.1 is no allowed download host");

        Path good = zip(tmp.resolve("g.mrpack"), Map.of("modrinth.index.json", index("mods/a.jar", "\"minecraft\":\"1.21.4\",\"fabric-loader\":\"0.16.9\"")));
        MrpackImporter.Preview p = MrpackImporter.analyze(good, Set.of(), true);
        corrupt = true;
        InstanceManager im = instances();
        assertThrows(IOException.class, () -> importer().importPack(p, "X", Set.of(), im, "mrpack", null, null, null));
        assertEquals(0, im.own().size(), "the half-made instance is removed again");

        assertThrows(IOException.class, () -> MrpackImporter.analyze(zip(tmp.resolve("n.zip"), Map.of("a.txt", "x"))));
    }

    @Test
    void filesAreRecognisedByTheirContent() throws Exception {
        assertEquals(FileClassifier.Kind.RESOURCE_PACK, FileClassifier.classify(zip(tmp.resolve("r.zip"),
                Map.of("pack.mcmeta", "{}", "assets/minecraft/x.png", "x"))).kind());
        assertEquals(FileClassifier.Kind.DATAPACK, FileClassifier.classify(zip(tmp.resolve("d.zip"),
                Map.of("pack.mcmeta", "{}", "data/x/y.json", "x"))).kind());
        assertEquals(FileClassifier.Kind.SHADER, FileClassifier.classify(zip(tmp.resolve("s.zip"), Map.of("shaders/final.fsh", "x"))).kind());
        assertEquals(FileClassifier.Kind.WORLD, FileClassifier.classify(zip(tmp.resolve("w.zip"), Map.of("MyWorld/level.dat", "x"))).kind());
        assertEquals(FileClassifier.Kind.MOD, FileClassifier.classify(zip(tmp.resolve("m.jar"),
                Map.of("fabric.mod.json", "{\"id\":\"m\",\"name\":\"M\"}"))).kind());
        assertEquals(FileClassifier.Kind.UNKNOWN, FileClassifier.classify(zip(tmp.resolve("x.jar"), Map.of("a.class", "x"))).kind());
        assertEquals(FileClassifier.Kind.MODPACK, FileClassifier.classify(zip(tmp.resolve("p.zip"), Map.of("modrinth.index.json", "{}"))).kind());
    }
}
