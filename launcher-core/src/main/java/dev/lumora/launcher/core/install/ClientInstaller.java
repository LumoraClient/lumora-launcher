package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.java.JavaProbe;
import dev.lumora.launcher.core.java.JavaRuntimeInstaller;
import dev.lumora.launcher.core.mods.BundledMods;
import dev.lumora.launcher.core.mods.DependencyChecker;
import dev.lumora.launcher.core.mods.FabricModMetadata;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Makes a runtime profile ready to start. Idempotent: running it again only repairs what is missing
 * or corrupt. Order: metadata, client jar, libraries, assets, logging config, Java, mods, dependency check.
 */
public final class ClientInstaller {
    public record Prepared(ProfileCatalog.Profile profile, ResolvedVersion version, Path javaExecutable,
                           Path clientJar, Path gameDir, Path nativesDir, Path loggingConfig,
                           List<DependencyChecker.Problem> warnings, List<Path> unmanagedMods) {
    }

    public static final class DependencyException extends IOException {
        private static final long serialVersionUID = 1L;
        private final transient List<DependencyChecker.Problem> problems;

        public DependencyException(List<DependencyChecker.Problem> problems) {
            super("Inkompatible Mod-Kombination:\n - " + String.join("\n - ",
                    problems.stream().map(DependencyChecker.Problem::message).toList()));
            this.problems = List.copyOf(problems);
        }

        public List<DependencyChecker.Problem> problems() {
            return problems;
        }
    }

    /** Versions whose Fabric API does not switch the built-in Lumora theme on by itself: the launcher does it once. */
    static final java.util.Set<String> THEME_ONCE = java.util.Set.of("26.3");

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
     * @param flavor {@link Flavor#LUMORA}: the profile with its mods; {@link Flavor#FABRIC}: the same Fabric loader
     *               without Lumora mods and packs (own mods in the instance's mods folder stay); {@link Flavor#VANILLA}:
     *               plain Minecraft. Fabric and Vanilla use their own instance folder and Mojang's Java for the version.
     */
    public Prepared prepare(ProfileCatalog.Profile profile, Flavor flavor, LauncherConfig config, boolean repair,
                            ProgressListener progress) throws IOException {
        MetadataService meta = new MetadataService(downloader, paths.versions());
        boolean vanillaOnly = flavor == Flavor.VANILLA;
        progress.stage("Lade Versionsinformationen (Minecraft " + profile.minecraftVersion
                + (vanillaOnly ? ", Vanilla" : ", Fabric " + profile.fabricLoaderVersion) + ")");
        progress.progress(-1, "");
        VersionJson vanilla = meta.vanilla(profile.minecraftVersion);
        VersionJson fabric = vanillaOnly ? null
                : meta.fabricProfile(profile.minecraftVersion, profile.fabricLoaderVersion, "legacy-fabric".equals(profile.loader));
        ResolvedVersion version = ResolvedVersion.merge(fabric, vanilla, os);
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
            String sha1 = lib.sha1() != null ? lib.sha1() : fetchMavenSha1(lib.url());
            tasks.add(new Downloader.Task(URI.create(lib.url()), FileUtil.resolveInside(paths.libraries(), lib.path()),
                    Hashing.Expected.sha1(sha1), lib.size()));
        }
        downloader.downloadAll(tasks, progressAdapter(progress, "Bibliotheken"), verify);

        // native libraries (up to 1.18.2): classifier jars, extracted into the natives directory below
        List<Downloader.Task> nativeTasks = new ArrayList<>();
        for (ResolvedVersion.NativeLibrary n : version.natives()) {
            String sha1 = n.sha1() != null ? n.sha1() : fetchMavenSha1(n.url());
            nativeTasks.add(new Downloader.Task(URI.create(n.url()), FileUtil.resolveInside(paths.libraries(), n.path()),
                    Hashing.Expected.sha1(sha1), n.size()));
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
            return new Prepared(profile, version, java, clientJar, gameDir, natives, loggingConfig, List.of(), List.of());
        }

        // mods (pure Fabric: none from Lumora, the player's own jars in mods/ stay)
        progress.stage(flavor == Flavor.LUMORA ? "Installiere Client-Komponenten (" + profile.displayName + ")" : "Prüfe Fabric-Mods");
        List<ProfileCatalog.Artifact> mods = flavor == Flavor.LUMORA
                ? catalog.resolveMods(profile, config.enabledExtensions, config.disabledResourcePacks) : List.of();
        ModInstaller modInstaller = new ModInstaller(downloader, paths.modCache(), bundled.get());
        ModInstaller.Result modResult = modInstaller.install(mods, gameDir.resolve("mods"), progressAdapter(progress, "Mods"));

        // resource packs: same verified download into resourcepacks/, then switched on in options.txt
        if (flavor == Flavor.LUMORA) {
            List<ProfileCatalog.Artifact> packs = catalog.resolvePacks(profile, config.disabledResourcePacks);
            progress.stage("Installiere Ressourcenpakete (" + packs.size() + ")");
            modInstaller.install(packs, gameDir.resolve("resourcepacks"), progressAdapter(progress, "Ressourcenpakete"));
            ResourcePackOptions.apply(gameDir, packs, THEME_ONCE.contains(profile.minecraftVersion)
                    && catalog.profileArtifact(profile, "lumora-client").isPresent());
        }

        progress.stage("Prüfe Mod-Kompatibilität");
        List<FabricModMetadata> metadata = new ArrayList<>();
        List<Path> allJars = new ArrayList<>(modResult.modFiles());
        allJars.addAll(modResult.unmanagedFiles());
        for (Path jar : allJars) {
            metadata.add(FabricModMetadata.read(jar));
        }
        int javaMajor = flavor == Flavor.LUMORA && profile.javaComponent != null ? componentMajor(profile.javaComponent)
                : version.javaVersion() != null ? version.javaVersion().majorVersion : 25;
        Map<String, String> environment = new HashMap<>(Map.of(
                "minecraft", profile.minecraftVersion,
                "fabricloader", profile.fabricLoaderVersion,
                "fabric-loader", profile.fabricLoaderVersion,
                "java", Integer.toString(javaMajor)));
        // the loader's own jar-in-jar (MixinExtras) satisfies dependencies too, e.g. Lithium for 26.3
        for (ResolvedVersion.Library lib : version.libraries()) {
            if (lib.name().startsWith("net.fabricmc:fabric-loader:")) {
                FabricModMetadata loader = FabricModMetadata.read(FileUtil.resolveInside(paths.libraries(), lib.path()));
                DependencyChecker.bundledWith(loader).forEach(environment::putIfAbsent);
            }
        }
        List<DependencyChecker.Problem> problems = DependencyChecker.check(metadata, environment);
        List<DependencyChecker.Problem> errors = problems.stream().filter(p -> p.severity() == DependencyChecker.Severity.ERROR).toList();
        if (!errors.isEmpty()) {
            throw new DependencyException(errors);
        }
        if (!modResult.unmanagedFiles().isEmpty()) {
            Log.warn("Additional user mods in " + gameDir.resolve("mods") + ": " + modResult.unmanagedFiles());
        }

        FileUtil.writeAtomically(verifiedMarker, version.id().getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(natives);
        NativeExtractor.extract(version.natives(), paths.libraries(), natives);
        progress.progress(1, "");
        return new Prepared(profile, version, java, clientJar, gameDir, natives, loggingConfig, problems, modResult.unmanagedFiles());
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

    private String fetchMavenSha1(String artifactUrl) throws IOException {
        // Fabric's launch profile omits the hash for fabric-loader itself; Maven publishes <artifact>.sha1
        // next to every artifact on the same trusted host.
        String body = downloader.fetchString(URI.create(artifactUrl + ".sha1")).strip();
        String sha1 = body.split("\\s+")[0];
        Hashing.Expected.sha1(sha1);
        return sha1;
    }

    private static Downloader.Progress progressAdapter(ProgressListener l, String label) {
        return (bytesDone, bytesTotal, filesDone, filesTotal) -> {
            double f = bytesTotal > 0 ? (double) bytesDone / bytesTotal : (double) filesDone / Math.max(1, filesTotal);
            l.progress(Math.min(1, f), label + ": " + filesDone + "/" + filesTotal
                    + (bytesTotal > 0 ? String.format(" (%.1f / %.1f MB)", bytesDone / 1048576.0, bytesTotal / 1048576.0) : ""));
        };
    }
}
