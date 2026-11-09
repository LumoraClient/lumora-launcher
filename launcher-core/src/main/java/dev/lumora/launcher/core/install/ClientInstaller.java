package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.java.JavaProbe;
import dev.lumora.launcher.core.java.JavaRuntimeInstaller;
import dev.lumora.launcher.core.mods.BundledMods;
import dev.lumora.launcher.core.mods.ModInstaller;
import dev.lumora.launcher.core.mojang.AssetIndex;
import dev.lumora.launcher.core.mojang.MetadataService;
import dev.lumora.launcher.core.mojang.ResolvedVersion;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.profile.Flavor;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Makes a runtime profile ready to start. Idempotent: running it again only repairs what is missing
 * or corrupt. Order: metadata, client jar, libraries, assets, logging config, Java, Lumora jar, resource packs,
 * loader. Lumora profiles start through the Lumora Loader (no mod loader), Vanilla is plain Minecraft.
 */
public final class ClientInstaller {
    public record Prepared(ProfileCatalog.Profile profile, ResolvedVersion version, Path javaExecutable,
                           Path clientJar, Path gameDir, Path nativesDir, Path loggingConfig,
                           dev.lumora.launcher.core.launch.LaunchCommandBuilder.Loader loader) {
    }

    /** The Lumora Loader in the launcher's bundled folder (started instead of a mod loader, profile loader "lumora"). */
    public static final String LOADER_JAR = "lumora-loader.jar";

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final ProfileCatalog catalog;
    private final Supplier<BundledMods> bundled;
    private final OsInfo os;

    public ClientInstaller(LauncherPaths paths, Downloader downloader, ProfileCatalog catalog,
                           Supplier<BundledMods> bundled, OsInfo os) {
        this.paths = paths;
        this.downloader = downloader;
        this.catalog = catalog;
        this.bundled = bundled;
        this.os = os;
    }

    public Prepared prepare(ProfileCatalog.Profile profile, LauncherConfig config, boolean repair, ProgressListener progress) throws IOException {
        return prepare(profile, Flavor.LUMORA, config, repair, progress);
    }

    /**
     * @param flavor {@link Flavor#LUMORA}: Minecraft with the Lumora client, started by the Lumora Loader;
     *               {@link Flavor#VANILLA}: plain Minecraft in its own instance folder with Mojang's Java.
     */
    public Prepared prepare(ProfileCatalog.Profile profile, Flavor flavor, LauncherConfig config, boolean repair,
                            ProgressListener progress) throws IOException {
        MetadataService meta = new MetadataService(downloader, paths.versions());
        boolean vanillaOnly = flavor == Flavor.VANILLA;
        progress.stage("Lade Versionsinformationen (Minecraft " + profile.minecraftVersion
                + (vanillaOnly ? ", Vanilla" : ", Lumora Loader") + ")");
        progress.progress(-1, "");
        // only Mojang's version JSON: Lumora needs no mod loader profile
        VersionJson vanilla = meta.vanilla(profile.minecraftVersion);
        ResolvedVersion version = ResolvedVersion.merge(null, vanilla, os);
        String instanceId = flavor.instanceId(profile.id);

        Path verifiedMarker = FileUtil.resolveInside(paths.versions(), version.id() + "/.lumora-verified");
        Downloader.Verify verify = !repair && Files.isRegularFile(verifiedMarker) ? Downloader.Verify.QUICK : Downloader.Verify.FULL;
        if (repair) {
            Files.deleteIfExists(verifiedMarker);
        }
        Log.info("Preparing profile " + profile.id + " (" + version.id() + "), verification " + verify);

        // client jar + libraries
        progress.stage("Prüfe Spieldateien und Bibliotheken");
        List<Downloader.Task> tasks = new ArrayList<>();
        Path clientJar = FileUtil.resolveInside(paths.versions(), vanilla.id + "/" + vanilla.id + ".jar");
        tasks.add(new Downloader.Task(URI.create(version.clientJar().url), clientJar,
                Hashing.Expected.sha1(version.clientJar().sha1), version.clientJar().size));
        for (ResolvedVersion.Library lib : version.libraries()) {
            tasks.add(new Downloader.Task(URI.create(lib.url()), FileUtil.resolveInside(paths.libraries(), lib.path()),
                    Hashing.Expected.sha1(lib.sha1()), lib.size()));
        }
        downloader.downloadAll(tasks, progressAdapter(progress, "Bibliotheken"), verify);

        // native libraries (up to 1.18.2): classifier jars, extracted into the natives directory below
        List<Downloader.Task> nativeTasks = new ArrayList<>();
        for (ResolvedVersion.NativeLibrary n : version.natives()) {
            nativeTasks.add(new Downloader.Task(URI.create(n.url()), FileUtil.resolveInside(paths.libraries(), n.path()),
                    Hashing.Expected.sha1(n.sha1()), n.size()));
        }
        downloader.downloadAll(nativeTasks, progressAdapter(progress, "Native Bibliotheken"), verify);

        // assets
        progress.stage("Prüfe Assets (Texturen, Sounds, Sprachen)");
        VersionJson.AssetIndex idx = version.assetIndex();
        Path indexFile = FileUtil.resolveInside(paths.assets(), "indexes/" + idx.id + ".json");
        downloader.download(new Downloader.Task(URI.create(idx.url), indexFile, Hashing.Expected.sha1(idx.sha1), idx.size), null);
        AssetIndex assets = Json.read(indexFile, AssetIndex.class);
        downloader.downloadAll(assets.tasks(paths.assets()), progressAdapter(progress, "Assets"), verify);

        // logging config
        Path loggingConfig = null;
        if (version.logging() != null && version.logging().file != null) {
            VersionJson.Download f = version.logging().file;
            loggingConfig = FileUtil.resolveInside(paths.assets(), "log_configs/" + f.id);
            downloader.download(new Downloader.Task(URI.create(f.url), loggingConfig, Hashing.Expected.sha1(f.sha1), f.size), null, verify);
        }

        // Java
        Path java = resolveJava(version, flavor == Flavor.LUMORA ? profile.javaComponent : null, config, verify, progress);

        Path gameDir = paths.instance(instanceId);
        Path natives = paths.natives(instanceId);
        if (vanillaOnly) {
            // plain Minecraft ignores the mods folder; nothing of Lumora is installed
            Files.createDirectories(gameDir);
            FileUtil.writeAtomically(verifiedMarker, version.id().getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(natives);
            NativeExtractor.extract(version.natives(), paths.libraries(), natives);
            progress.progress(1, "");
            return new Prepared(profile, version, java, clientJar, gameDir, natives, loggingConfig, null);
        }
        // the addon library next to the instances: addon jars there are offered in the hub (Addons) for installing
        Files.createDirectories(paths.addonLibrary());

        // the Lumora jar(s) of the profile (verified); they live in .lumora/client, a mods/ folder is left alone
        progress.stage("Installiere Client-Komponenten (" + profile.displayName + ")");
        List<ProfileCatalog.Artifact> mods = catalog.resolveMods(profile, List.of(), null);
        ModInstaller modInstaller = new ModInstaller(downloader, paths.modCache(), bundled.get());
        ModInstaller.Result modResult = modInstaller.install(mods, gameDir.resolve(".lumora").resolve("client"),
                progressAdapter(progress, "Lumora"));

        // resource packs: same verified download into resourcepacks/, then switched on in options.txt; packs that
        // would need a mod (none without a mod loader) are skipped
        List<ProfileCatalog.Artifact> packs = catalog.resolvePacks(profile, config.disabledResourcePacks).stream()
                .filter(a -> a.requires == null || a.requires.isEmpty()).toList();
        progress.stage("Installiere Ressourcenpakete (" + packs.size() + ")");
        modInstaller.install(packs, gameDir.resolve("resourcepacks"), progressAdapter(progress, "Ressourcenpakete"));
        ResourcePackOptions.apply(gameDir, packs);

        // the Lumora Loader starts exactly these jars
        Path loaderJar = bundled.get().verified(LOADER_JAR);
        // obfuscated versions (1.16.5 - 1.21.11): Mojang's official client mappings from the version JSON (SHA-1
        // checked); 1.8.9 / 1.12.2 bring their mappings in the Lumora jar, 26.1+ is not obfuscated
        Path mappings = null;
        VersionJson.Download cm = vanilla.downloads == null ? null : vanilla.downloads.get("client_mappings");
        if (cm != null && cm.url != null) {
            mappings = FileUtil.resolveInside(paths.versions(), vanilla.id + "/" + vanilla.id + "-client-mappings.txt");
            downloader.download(new Downloader.Task(URI.create(cm.url), mappings, Hashing.Expected.sha1(cm.sha1), cm.size), null, verify);
        }
        FileUtil.writeAtomically(verifiedMarker, version.id().getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(natives);
        NativeExtractor.extract(version.natives(), paths.libraries(), natives);
        progress.progress(1, "");
        return new Prepared(profile, version, java, clientJar, gameDir, natives, loggingConfig,
                new dev.lumora.launcher.core.launch.LaunchCommandBuilder.Loader(loaderJar, modResult.modFiles(), mappings));
    }

    /** Java major version of Mojang's runtime components (all.json). */
    static int componentMajor(String component) {
        return switch (component) {
            case "jre-legacy" -> 8;
            case "java-runtime-alpha" -> 16;
            case "java-runtime-beta", "java-runtime-gamma", "java-runtime-gamma-snapshot" -> 17;
            case "java-runtime-delta" -> 21;
            case "java-runtime-epsilon" -> 25;
            default -> -1;
        };
    }

    /** @param profileComponent the profile's own Java component (only for the Lumora flavor), null = the version's */
    private Path resolveJava(ResolvedVersion version, String profileComponent, LauncherConfig config, Downloader.Verify verify,
                             ProgressListener progress) throws IOException {
        int required = version.javaVersion() != null ? version.javaVersion().majorVersion : 25;
        if (profileComponent != null && config.javaPath == null) {
            // newer official runtime than the version JSON asks for (the profile needs it, e.g. for the Lumora mod)
            int major = componentMajor(profileComponent);
            if (major < required) {
                throw new IOException(profileComponent + " ist älter als Java " + required);
            }
            progress.stage("Prüfe Java-Laufzeit (" + profileComponent + ", Java " + major + ")");
            JavaRuntimeInstaller jri = new JavaRuntimeInstaller(downloader, paths.runtimes(), os);
            return jri.install(profileComponent, progressAdapter(progress, "Java"), verify).javaExecutable();
        }
        if (config.javaPath != null) {
            Path custom = Path.of(config.javaPath);
            int major = JavaProbe.majorVersion(custom);
            if (major < required) {
                throw new IOException("Die eingestellte Java-Installation ist Version " + major + ", Minecraft "
                        + version.vanillaId() + " benötigt Java " + required + ". Entferne den Java-Pfad in den "
                        + "Einstellungen, dann verwendet der Launcher die offizielle Mojang-Laufzeit.");
            }
            return custom;
        }
        String component = version.javaVersion() != null ? version.javaVersion().component : "java-runtime-epsilon";
        progress.stage("Prüfe Java-Laufzeit (" + component + ", Java " + required + ")");
        JavaRuntimeInstaller jri = new JavaRuntimeInstaller(downloader, paths.runtimes(), os);
        return jri.install(component, progressAdapter(progress, "Java"), verify).javaExecutable();
    }

    private static Downloader.Progress progressAdapter(ProgressListener l, String label) {
        return (bytesDone, bytesTotal, filesDone, filesTotal) -> {
            double f = bytesTotal > 0 ? (double) bytesDone / bytesTotal : (double) filesDone / Math.max(1, filesTotal);
            l.progress(Math.min(1, f), label + ": " + filesDone + "/" + filesTotal
                    + (bytesTotal > 0 ? String.format(" (%.1f / %.1f MB)", bytesDone / 1048576.0, bytesTotal / 1048576.0) : ""));
        };
    }
}
