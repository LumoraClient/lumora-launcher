package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.mojang.ResolvedVersion;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.OsInfo;
import dev.lumora.launcher.core.util.Redactor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses trimmed copies of the real 26.1.2 vanilla JSON and Fabric 0.19.5 profile (structure unchanged). */
class LaunchCommandBuilderTest {
    static final String VANILLA = """
            {"id":"26.1.2","type":"release","mainClass":"net.minecraft.client.main.Main",
             "arguments":{
              "jvm":[{"rules":[{"action":"allow","os":{"name":"osx"}}],"value":["-XstartOnFirstThread"]},
                     {"rules":[{"action":"allow","os":{"arch":"x86"}}],"value":"-Xss1M"},
                     "-Djava.library.path=${natives_directory}","-Dminecraft.launcher.brand=${launcher_name}","-cp","${classpath}"],
              "game":["--username","${auth_player_name}","--version","${version_name}","--gameDir","${game_directory}",
                      "--assetsDir","${assets_root}","--assetIndex","${assets_index_name}","--uuid","${auth_uuid}",
                      "--accessToken","${auth_access_token}","--xuid","${auth_xuid}","--versionType","${version_type}",
                      {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"},
                      {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width","${resolution_width}","--height","${resolution_height}"]},
                      {"rules":[{"action":"allow","features":{"has_quick_plays_support":true}}],"value":["--quickPlayPath","${quickPlayPath}"]},
                      {"rules":[{"action":"allow","features":{"is_quick_play_singleplayer":true}}],"value":["--quickPlaySingleplayer","${quickPlaySingleplayer}"]},
                      {"rules":[{"action":"allow","features":{"is_quick_play_multiplayer":true}}],"value":["--quickPlayMultiplayer","${quickPlayMultiplayer}"]}]},
             "libraries":[
              {"name":"org.ow2.asm:asm:9.6","downloads":{"artifact":{"path":"org/ow2/asm/asm/9.6/asm-9.6.jar","sha1":"%s","size":1,"url":"https://libraries.minecraft.net/org/ow2/asm/asm/9.6/asm-9.6.jar"}}},
              {"name":"com.mojang:jtracy:1.0.37:natives-windows","rules":[{"action":"allow","os":{"name":"windows"}}],"downloads":{"artifact":{"path":"com/mojang/jtracy/1.0.37/jtracy-1.0.37-natives-windows.jar","sha1":"%s","size":1,"url":"https://libraries.minecraft.net/x.jar"}}},
              {"name":"com.mojang:jtracy:1.0.37:natives-linux","rules":[{"action":"allow","os":{"name":"linux"}}],"downloads":{"artifact":{"path":"com/mojang/jtracy/1.0.37/jtracy-1.0.37-natives-linux.jar","sha1":"%s","size":1,"url":"https://libraries.minecraft.net/y.jar"}}}],
             "assetIndex":{"id":"30","sha1":"%s","size":1,"totalSize":1,"url":"https://piston-meta.mojang.com/x.json"},
             "downloads":{"client":{"sha1":"%s","size":1,"url":"https://piston-data.mojang.com/client.jar"}},
             "javaVersion":{"component":"java-runtime-epsilon","majorVersion":25}}
            """.formatted("1".repeat(40), "2".repeat(40), "3".repeat(40), "4".repeat(40), "5".repeat(40));

    static final String FABRIC = """
            {"id":"fabric-loader-0.19.5-26.1.2","inheritsFrom":"26.1.2","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
             "arguments":{"game":[],"jvm":["-DFabricMcEmu= net.minecraft.client.main.Main "]},
             "libraries":[{"name":"org.ow2.asm:asm:9.10.1","url":"https://maven.fabricmc.net/","sha1":"ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236"},
                          {"name":"net.fabricmc:fabric-loader:0.19.5","url":"https://maven.fabricmc.net/","sha1":""}]}
            """;

    static final OsInfo WIN = new OsInfo(OsInfo.Name.WINDOWS, OsInfo.Arch.X86_64, "10.0");

    static ResolvedVersion resolved(OsInfo os) {
        return ResolvedVersion.merge(Json.GSON.fromJson(FABRIC, VersionJson.class), Json.GSON.fromJson(VANILLA, VersionJson.class), os);
    }

    static LaunchCommandBuilder.Request request(LaunchCommandBuilder.Identity id, Integer w, Integer h) {
        Path base = Path.of("C:/lumora");
        return new LaunchCommandBuilder.Request(resolved(WIN), base.resolve("runtime/bin/javaw.exe"), base.resolve("libraries"),
                base.resolve("versions/26.1.2/26.1.2.jar"), base.resolve("assets"), base.resolve("instances/26.1.2"),
                base.resolve("instances/26.1.2/.natives"), null, id, 1024, 4096, List.of("-XX:+UseZGC"), w, h,
                List.of("-Dlumora.profile=26.1.2"), null);
    }

    static final LaunchCommandBuilder.Identity USER = new LaunchCommandBuilder.Identity("Steve", "0123abcd", "secret-token-123456", "9876", "msa", false);

    @Test
    void quickPlayStartsAWorldOrServerDirectly() {
        LaunchCommandBuilder.Request base = request(USER, null, null);
        List<String> none = LaunchCommandBuilder.build(base, WIN);
        assertFalse(none.contains("--quickPlaySingleplayer") || none.contains("--quickPlayMultiplayer"));
        var world = new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.SINGLEPLAYER, "Meine Welt");
        List<String> w = LaunchCommandBuilder.build(withQuickPlay(base, world), WIN);
        assertEquals("Meine Welt", w.get(w.indexOf("--quickPlaySingleplayer") + 1));
        assertFalse(w.contains("--quickPlayMultiplayer"));
        var server = new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, "play.example.net:25570");
        List<String> s = LaunchCommandBuilder.build(withQuickPlay(base, server), WIN);
        assertEquals("play.example.net:25570", s.get(s.indexOf("--quickPlayMultiplayer") + 1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, "--demo"));
    }

    static LaunchCommandBuilder.Request withQuickPlay(LaunchCommandBuilder.Request r, LaunchCommandBuilder.QuickPlay q) {
        return new LaunchCommandBuilder.Request(r.version(), r.javaExecutable(), r.librariesDir(), r.clientJar(), r.assetsDir(),
                r.gameDir(), r.nativesDir(), r.loggingConfig(), r.identity(), r.minMemoryMb(), r.maxMemoryMb(), r.extraJvmArgs(),
                r.width(), r.height(), r.extraSystemProperties(), q);
    }

    @Test
    void mergesFabricProfileAndDeduplicatesLibraries() {
        ResolvedVersion v = resolved(WIN);
        assertEquals("fabric-loader-0.19.5-26.1.2", v.id());
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", v.mainClass());
        List<String> paths = v.libraries().stream().map(ResolvedVersion.Library::path).toList();
        assertTrue(paths.contains("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar"), "Fabric's newer ASM wins");
        assertFalse(paths.contains("org/ow2/asm/asm/9.6/asm-9.6.jar"));
        assertTrue(paths.contains("com/mojang/jtracy/1.0.37/jtracy-1.0.37-natives-windows.jar"));
        assertFalse(paths.contains("com/mojang/jtracy/1.0.37/jtracy-1.0.37-natives-linux.jar"), "OS rules are applied");
        assertTrue(v.libraries().stream().anyMatch(l -> l.path().endsWith("fabric-loader-0.19.5.jar") && l.sha1() == null),
                "empty sha1 is treated as missing (fetched from Maven .sha1 later)");
    }

    @Test
    void buildsCommandWithPlaceholdersAndRules() {
        List<String> cmd = LaunchCommandBuilder.build(request(USER, null, null), WIN);
        assertTrue(cmd.getFirst().endsWith("javaw.exe"));
        assertTrue(cmd.contains("-Xmx4096M") && cmd.contains("-Xms1024M"));
        assertTrue(cmd.contains("-XX:+UseZGC"));
        assertTrue(cmd.contains("-Dlumora.profile=26.1.2"));
        assertTrue(cmd.contains("-DFabricMcEmu= net.minecraft.client.main.Main "));
        assertFalse(cmd.contains("-XstartOnFirstThread"), "macOS-only argument");
        assertFalse(cmd.contains("-Xss1M"), "32-bit only argument");
        assertFalse(cmd.contains("--demo"));
        assertFalse(cmd.contains("--width"));
        assertFalse(cmd.contains("--quickPlayPath"), "unsupported feature stays off");
        assertEquals("Steve", cmd.get(cmd.indexOf("--username") + 1));
        assertEquals("secret-token-123456", cmd.get(cmd.indexOf("--accessToken") + 1));
        assertEquals("fabric-loader-0.19.5-26.1.2", cmd.get(cmd.indexOf("--version") + 1));
        assertEquals("30", cmd.get(cmd.indexOf("--assetIndex") + 1));
        int main = cmd.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient");
        assertTrue(main > cmd.indexOf("-cp") && main < cmd.indexOf("--username"), "JVM args, main class, game args order");
        String cp = cmd.get(cmd.indexOf("-cp") + 1);
        assertTrue(cp.endsWith("26.1.2.jar"), "client jar is last on the classpath");
        assertTrue(cp.split(File.pathSeparator.equals(";") ? ";" : ":").length >= 3);
    }

    @Test
    void demoAndResolutionFeatures() {
        var demo = new LaunchCommandBuilder.Identity("Demo", "u", "0", "0", "msa", true);
        List<String> cmd = LaunchCommandBuilder.build(request(demo, 1280, 720), WIN);
        assertTrue(cmd.contains("--demo"));
        assertEquals("1280", cmd.get(cmd.indexOf("--width") + 1));
        assertEquals("720", cmd.get(cmd.indexOf("--height") + 1));
    }

    @Test
    void accessTokenIsRedactedForLogging() {
        List<String> cmd = LaunchCommandBuilder.build(request(USER, null, null), WIN);
        List<String> safe = Redactor.redactCommand(cmd);
        assertFalse(String.join(" ", safe).contains("secret-token-123456"));
        assertEquals(Redactor.MASK, safe.get(safe.indexOf("--accessToken") + 1));
    }

    @Test
    void parentMismatchAndIncompleteVersionsAreRejected() {
        VersionJson vanilla = Json.GSON.fromJson(VANILLA, VersionJson.class);
        VersionJson fabric = Json.GSON.fromJson(FABRIC.replace("\"inheritsFrom\":\"26.1.2\"", "\"inheritsFrom\":\"1.21.11\""), VersionJson.class);
        assertThrows(IllegalArgumentException.class, () -> ResolvedVersion.merge(fabric, vanilla, WIN));
        // legacy format is supported (see LegacyVersionTest), but a version without client download is not
        VersionJson legacy = Json.GSON.fromJson("{\"id\":\"1.12.2\",\"minecraftArguments\":\"--username x\",\"libraries\":[]}", VersionJson.class);
        assertThrows(IllegalArgumentException.class, () -> ResolvedVersion.merge(null, legacy, WIN));
    }
}
