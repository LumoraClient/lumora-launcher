package dev.lumora.launcher.core.integration;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.mods.DependencyChecker;
import dev.lumora.launcher.core.mods.FabricModMetadata;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Hashing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Network integration test against the REAL artifacts pinned in profiles.json: downloads them from the
 * Modrinth CDN, verifies SHA-512, reads their fabric.mod.json (including jar-in-jar) and checks for every
 * Minecraft version profile that
 * <ul>
 *   <li>the required mods (Fabric API + Sodium + ViaFabricPlus + Lumora) resolve without errors,</li>
 *   <li>every optional extension combines with them,</li>
 *   <li>the companion mod build of another version is rejected (wrong "minecraft" dependency).</li>
 * </ul>
 * Fabric Loader itself is downloaded too (maven.fabricmc.net, SHA-1): its jar-in-jar MixinExtras satisfies mods such as
 * Lithium, as in the real installer.
 * Run with: gradlew :launcher-core:integrationTest
 */
@Tag("integration")
class RealModsIntegrationTest {
    static final Map<String, FabricModMetadata> META = new HashMap<>();
    static ProfileCatalog catalog;

    /** Mods shipped inside the real Fabric Loader jar of each loader version (MixinExtras), like ClientInstaller. */
    static final Map<String, Map<String, String>> LOADER_BUNDLED = new HashMap<>();

    static Map<String, String> env(ProfileCatalog.Profile p) {
        String java = p.minecraftVersion.startsWith("1.") ? "21" : "25";
        Map<String, String> env = new HashMap<>(Map.of("minecraft", p.minecraftVersion, "fabricloader", p.fabricLoaderVersion,
                "fabric-loader", p.fabricLoaderVersion, "java", java));
        LOADER_BUNDLED.getOrDefault(p.fabricLoaderVersion, Map.of()).forEach(env::putIfAbsent);
        return env;
    }

    @BeforeAll
    static void download() throws IOException {
        catalog = ProfileCatalog.loadBundled();
        Path cache = Path.of(System.getProperty("lumora.itCache", "build/it-cache"));
        Downloader d = new Downloader();
        List<Downloader.Task> tasks = new ArrayList<>();
        for (ProfileCatalog.Artifact a : catalog.artifacts()) {
            if (a.source == ProfileCatalog.Source.MODRINTH) {
                tasks.add(new Downloader.Task(URI.create(a.url), cache.resolve(a.id).resolve(a.fileName), a.expectedHash(), a.size));
            }
        }
        d.downloadAll(tasks, null);
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            String v = p.fabricLoaderVersion;
            if (LOADER_BUNDLED.containsKey(v)) {
                continue;
            }
            String url = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/" + v + "/fabric-loader-" + v + ".jar";
            String sha1 = d.fetchString(URI.create(url + ".sha1")).strip().split("\\s+")[0];
            Path loaderJar = cache.resolve("fabric-loader").resolve("fabric-loader-" + v + ".jar");
            d.download(new Downloader.Task(URI.create(url), loaderJar, Hashing.Expected.sha1(sha1), -1), null);
            LOADER_BUNDLED.put(v, DependencyChecker.bundledWith(FabricModMetadata.read(loaderJar)));
        }
        for (ProfileCatalog.Artifact a : catalog.artifacts()) {
            if (a.source == ProfileCatalog.Source.MODRINTH && !a.isResourcePack()) {
                META.put(a.id, FabricModMetadata.read(cache.resolve(a.id).resolve(a.fileName)));
            }
        }
        // own companion mod builds, matched to their artifact by file name
        for (String jar : System.getProperty("lumora.modJars", "").split(File.pathSeparator)) {
            if (jar.isBlank() || !Files.isRegularFile(Path.of(jar))) {
                continue;
            }
            String name = Path.of(jar).getFileName().toString();
            for (ProfileCatalog.Artifact a : catalog.artifacts()) {
                if (a.source == ProfileCatalog.Source.BUNDLED && a.fileName.equals(name)) {
                    META.put(a.id, FabricModMetadata.read(Path.of(jar)));
                }
            }
        }
    }

    static List<FabricModMetadata> mods(List<String> ids) {
        List<FabricModMetadata> out = new ArrayList<>();
        for (String id : ids) {
            if (META.containsKey(id)) {
                out.add(META.get(id));
            }
        }
        return out;
    }

    @Test
    void everyCompanionModBuildWasProvided() {
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            String id = catalog.profileArtifact(p, "lumora-client").orElseThrow().id;
            assertTrue(META.containsKey(id), "set -Dlumora.modJars (done by the Gradle task): " + id);
            assertEquals("lumora", META.get(id).id());
        }
    }

    @Test
    void everyProfileResolves() {
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            var problems = DependencyChecker.check(mods(p.mods), env(p));
            assertTrue(problems.isEmpty(), p.id + ": " + problems);
        }
    }

    @Test
    void resourcePacksAndTheirModsCombineWithEveryProfile() {
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            var ids = catalog.resolveMods(p, List.of(), List.of()).stream().map(a -> a.id).toList();
            var problems = DependencyChecker.check(mods(ids), env(p));
            assertTrue(problems.isEmpty(), p.id + ": " + problems);
        }
    }

    @Test
    void allExtensionsCombineWithEveryProfile() {
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            List<String> ids = new ArrayList<>(p.mods);
            ids.addAll(p.extensions);
            var problems = DependencyChecker.check(mods(ids), env(p));
            assertTrue(problems.isEmpty(), p.id + ": " + problems);
        }
    }

    @Test
    void companionModOfAnotherVersionIsRejected() {
        ProfileCatalog.Profile newest = catalog.profile("26.1.2").orElseThrow();
        ProfileCatalog.Profile old = catalog.profile("1.21.8").orElseThrow();
        String oldLumora = catalog.profileArtifact(old, "lumora-client").orElseThrow().id;
        var problems = DependencyChecker.check(mods(List.of("fabric-api", oldLumora)), env(newest));
        assertTrue(problems.stream().anyMatch(pr -> pr.modId().equals("lumora")), problems.toString());
    }

    @Test
    void viaFabricPlusRequiresThisMinecraftRange() {
        var problems = DependencyChecker.check(mods(List.of("fabric-api", "viafabricplus")),
                Map.of("minecraft", "26.3", "fabricloader", "0.19.5", "java", "25"));
        assertTrue(problems.stream().anyMatch(pr -> pr.modId().equals("viafabricplus")), "VFP 4.5.6 only supports 26.1-26.1.2");
    }
}
