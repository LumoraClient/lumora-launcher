package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.install.NativeExtractor;
import dev.lumora.launcher.core.mojang.ResolvedVersion;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pre-1.13 version format (minecraftArguments) and pre-1.19 native jars, with trimmed copies of the real
 * 1.8.9 vanilla JSON and the Legacy Fabric 0.19.5 profile (structure unchanged).
 */
class LegacyVersionTest {
    static final String VANILLA_189 = """
            {"id":"1.8.9","type":"release","mainClass":"net.minecraft.client.main.Main",
             "minecraftArguments":"--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type}",
             "libraries":[
              {"name":"org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209","downloads":{"artifact":{"path":"org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar","sha1":"%s","size":1,"url":"https://libraries.minecraft.net/a.jar"}}},
              {"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209",
               "downloads":{"artifact":{"path":"org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209.jar","sha1":"%s","size":22,"url":"https://libraries.minecraft.net/b.jar"},
                            "classifiers":{"natives-windows":{"path":"org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar","sha1":"%s","size":2,"url":"https://libraries.minecraft.net/c.jar"}}},
               "extract":{"exclude":["META-INF/"]},"natives":{"linux":"natives-linux","osx":"natives-osx","windows":"natives-windows"},
               "rules":[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]},
              {"name":"tv.twitch:twitch-platform:6.5",
               "downloads":{"classifiers":{"natives-windows-64":{"path":"tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar","sha1":"%s","size":3,"url":"https://libraries.minecraft.net/d.jar"},
                                           "natives-windows-32":{"path":"tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-32.jar","sha1":"%s","size":3,"url":"https://libraries.minecraft.net/e.jar"}}},
               "natives":{"windows":"natives-windows-${arch}"}}],
             "assetIndex":{"id":"1.8","sha1":"%s","size":1,"totalSize":1,"url":"https://launchermeta.mojang.com/x.json"},
             "downloads":{"client":{"sha1":"%s","size":1,"url":"https://launcher.mojang.com/client.jar"}},
             "javaVersion":{"component":"jre-legacy","majorVersion":8}}
            """.formatted("1".repeat(40), "2".repeat(40), "3".repeat(40), "4".repeat(40), "5".repeat(40), "6".repeat(40), "7".repeat(40));

    static final String LEGACY_FABRIC = """
            {"id":"fabric-loader-0.19.5-1.8.9","inheritsFrom":"1.8.9","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
             "libraries":[{"name":"net.fabricmc:fabric-loader:0.19.5","url":"https://maven.fabricmc.net/"},
                          {"name":"org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17","url":"https://maven.legacyfabric.net/"},
                          {"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17","url":"https://maven.legacyfabric.net/",
                           "extract":{"exclude":["META-INF/"]},"natives":{"linux":"natives-linux","osx":"natives-osx","windows":"natives-windows"}}]}
            """;

    static final OsInfo WIN = new OsInfo(OsInfo.Name.WINDOWS, OsInfo.Arch.X86_64, "10.0");

    static ResolvedVersion resolved() {
        return ResolvedVersion.merge(Json.GSON.fromJson(LEGACY_FABRIC, VersionJson.class), Json.GSON.fromJson(VANILLA_189, VersionJson.class), WIN);
    }

    @Test
    void nativesComeFromTheLoaderOverrideAndTheRightArchitecture() {
        ResolvedVersion v = resolved();
        List<ResolvedVersion.NativeLibrary> natives = v.natives();
        assertEquals(2, natives.size());
        ResolvedVersion.NativeLibrary lwjgl = natives.get(0);
        assertEquals("org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17:natives-windows", lwjgl.name());
        assertEquals("https://maven.legacyfabric.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/lwjgl-platform-2.9.4+legacyfabric.17-natives-windows.jar", lwjgl.url());
        assertNull(lwjgl.sha1(), "Legacy Fabric publishes the hash as a Maven .sha1 file");
        assertEquals(List.of("META-INF/"), lwjgl.excludes());
        ResolvedVersion.NativeLibrary twitch = natives.get(1);
        assertEquals("tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar", twitch.path(), "${arch} = 64");
        // the vanilla lwjgl and lwjgl-platform are replaced by Legacy Fabric's patched builds
        List<String> libs = v.libraries().stream().map(ResolvedVersion.Library::name).toList();
        assertTrue(libs.contains("org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17"));
        assertFalse(libs.contains("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209"));
        assertFalse(libs.stream().anyMatch(l -> l.startsWith("org.lwjgl.lwjgl:lwjgl-platform")));
    }

    @Test
    void lwjgl3ClassesAndNativesListedTwiceAreBothKept() {
        // 1.13 - 1.18.2 list org.lwjgl:lwjgl once as classes and once with natives (same coordinates)
        String json = """
                {"id":"1.18.2","type":"release","mainClass":"net.minecraft.client.main.Main","arguments":{"game":[],"jvm":[]},
                 "libraries":[
                  {"name":"org.lwjgl:lwjgl:3.2.2","downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar","sha1":"%s","size":1,"url":"https://libraries.minecraft.net/l.jar"}},
                   "rules":[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]},
                  {"name":"org.lwjgl:lwjgl:3.2.2","downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar","sha1":"%s","size":1,"url":"https://libraries.minecraft.net/l.jar"},
                   "classifiers":{"natives-windows":{"path":"org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar","sha1":"%s","size":2,"url":"https://libraries.minecraft.net/n.jar"}}},
                   "natives":{"linux":"natives-linux","windows":"natives-windows"},"rules":[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]}],
                 "assetIndex":{"id":"1.18","sha1":"%s","size":1,"totalSize":1,"url":"https://piston-meta.mojang.com/x.json"},
                 "downloads":{"client":{"sha1":"%s","size":1,"url":"https://piston-data.mojang.com/client.jar"}}}
                """.formatted("1".repeat(40), "1".repeat(40), "2".repeat(40), "3".repeat(40), "4".repeat(40));
        ResolvedVersion v = ResolvedVersion.merge(null, Json.GSON.fromJson(json, VersionJson.class), WIN);
        assertEquals(List.of("org.lwjgl:lwjgl:3.2.2"), v.libraries().stream().map(ResolvedVersion.Library::name).toList());
        assertEquals(1, v.natives().size(), "the natives entry must not be dropped as a duplicate");
        assertEquals("org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar", v.natives().get(0).path());
    }

    @Test
    void legacyArgumentsGetDefaultJvmArgsDemoAndUserProperties() {
        Path base = Path.of("C:/lumora");
        var demo = new LaunchCommandBuilder.Identity("Demo", "u", "0", "0", "msa", true);
        var r = new LaunchCommandBuilder.Request(resolved(), base.resolve("jre/bin/javaw.exe"), base.resolve("libraries"),
                base.resolve("versions/1.8.9/1.8.9.jar"), base.resolve("assets"), base.resolve("instances/1.8.9"),
                base.resolve("instances/1.8.9/.natives"), null, demo, 1024, 2048, List.of(), null, null, List.of(), null);
        List<String> cmd = LaunchCommandBuilder.build(r, WIN);
        assertTrue(cmd.contains("-Djava.library.path=" + base.resolve("instances/1.8.9/.natives").toAbsolutePath()));
        assertEquals("-cp", cmd.get(cmd.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient") - 2));
        assertEquals("{}", cmd.get(cmd.indexOf("--userProperties") + 1));
        assertEquals("1.8", cmd.get(cmd.indexOf("--assetIndex") + 1));
        assertEquals("--demo", cmd.get(cmd.size() - 1), "demo flag appended for the legacy format");
    }

    @Test
    void joiningAServerOnVersionsWithoutQuickPlayUsesServerAndPort() {
        Path base = Path.of("C:/lumora");
        var id = new LaunchCommandBuilder.Identity("P", "u", "t", "0", "msa", false);
        for (String[] c : new String[][]{{"mc.example.net:25570", "mc.example.net", "25570"}, {"play.example.net", "play.example.net", "25565"}}) {
            var r = new LaunchCommandBuilder.Request(resolved(), base.resolve("jre/bin/javaw.exe"), base.resolve("libraries"),
                    base.resolve("versions/1.8.9/1.8.9.jar"), base.resolve("assets"), base.resolve("instances/1.8.9"),
                    base.resolve("instances/1.8.9/.natives"), null, id, 1024, 2048, List.of(), null, null, List.of(),
                    new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, c[0]));
            List<String> cmd = LaunchCommandBuilder.build(r, WIN);
            assertEquals(c[1], cmd.get(cmd.indexOf("--server") + 1));
            assertEquals(c[2], cmd.get(cmd.indexOf("--port") + 1));
            assertFalse(cmd.contains("--quickPlayMultiplayer"));
        }
    }

    @Test
    void extractionSkipsExcludesAndRejectsPathsOutsideTheTarget(@TempDir Path tmp) throws IOException {
        Path libs = tmp.resolve("libraries");
        Path jar = libs.resolve("x/natives.jar");
        Files.createDirectories(jar.getParent());
        zip(jar, "META-INF/MANIFEST.MF", "lwjgl64.dll", "sub/OpenAL64.dll");
        Path target = tmp.resolve("natives");
        Files.createDirectories(target);
        Files.writeString(target.resolve("stale.dll"), "old");
        var n = new ResolvedVersion.NativeLibrary("x", "x/natives.jar", "https://x", null, 0, List.of("META-INF/"));
        NativeExtractor.extract(List.of(n), libs, target);
        assertTrue(Files.isRegularFile(target.resolve("lwjgl64.dll")));
        assertTrue(Files.isRegularFile(target.resolve("sub/OpenAL64.dll")));
        assertFalse(Files.exists(target.resolve("META-INF")));
        assertFalse(Files.exists(target.resolve("stale.dll")), "directory is cleared first");

        zip(jar, "../escape.dll");
        assertThrows(IOException.class, () -> NativeExtractor.extract(List.of(n), libs, target));
        assertFalse(Files.exists(tmp.resolve("escape.dll")));
    }

    private static void zip(Path file, String... entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream z = new ZipOutputStream(out)) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write(e.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }
}
