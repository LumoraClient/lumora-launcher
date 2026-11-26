package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.i18n.Lang;
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
import java.nio.file.StandardCopyOption;
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
                           Path clientJar, dev.lumora.launcher.core.InstanceLayout layout, Path loggingConfig,
                           dev.lumora.launcher.core.launch.LaunchCommandBuilder.Loader loader) {
        public Path gameDir() {
            return layout.gameDir();
        }

        public Path nativesDir() {
            return layout.nativesDir();
        }
    }

    /** The Lumora Loader in the launcher's bundled folder (started instead of a mod loader, profile loader "lumora"). */
    public static final String LOADER_JAR = "lumora-loader.jar";

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final ProfileCatalog catalog;
    private final Supplier<BundledMods> bundled;
    private final OsInfo os;
    private final dev.lumora.launcher.core.loader.LoaderManager loaders;

    public ClientInstaller(LauncherPaths paths, Downloader downloader, ProfileCatalog catalog,
                           Supplier<BundledMods> bundled, OsInfo os) {
        this(paths, downloader, catalog, bundled, os, new dev.lumora.launcher.core.loader.LoaderManager(
                dev.lumora.launcher.core.loader.ModLoaders.standard(downloader, paths), paths.versions()));
    }

    public ClientInstaller(LauncherPaths paths, Downloader downloader, ProfileCatalog catalog,
                           Supplier<BundledMods> bundled, OsInfo os, dev.lumora.launcher.core.loader.LoaderManager loaders) {
        this.paths = paths;
        this.downloader = downloader;
        this.catalog = catalog;
        this.bundled = bundled;
        this.os = os;
        this.loaders = loaders;
    }

    /** Mod loaders: versions, installed state. */
    public dev.lumora.launcher.core.loader.LoaderManager loaders() {
        return loaders;
    }

    public Prepared prepare(ProfileCatalog.Profile profile, LauncherConfig config, boolean repair, ProgressListener progress) throws IOException {
        return prepare(profile, Flavor.LUMORA, config, repair, progress);
    }

    /**
     * @param flavor {@link Flavor#LUMORA}: Minecraft with the Lumora client, started by the Lumora Loader;
     *               {@link Flavor#VANILLA}: plain Minecraft with Mojang's Java (game folder: {@link dev.lumora.launcher.core.InstanceLayout}).
     */
    public Prepared prepare(ProfileCatalog.Profile profile, Flavor flavor, LauncherConfig config, boolean repair,
                            ProgressListener progress) throws IOException {
        return prepare(profile, flavor, dev.lumora.launcher.core.InstanceLayout.of(paths, config, flavor.instanceId(profile.id)), null,
                config, repair, progress);
    }

    /**
     * Like above with the folders given (own instances) and an optional own Java executable (null = launcher setting,
     * else the official runtime).
     */
    public Prepared prepare(ProfileCatalog.Profile profile, Flavor flavor, dev.lumora.launcher.core.InstanceLayout layout,
                            String javaOverride, LauncherConfig config, boolean repair, ProgressListener progress) throws IOException {
        MetadataService meta = new MetadataService(downloader, paths.versions());
        boolean vanillaOnly = flavor == Flavor.VANILLA;
        progress.stage(Lang.t("install.stage.version_info", profile.minecraftVersion
                , vanillaOnly ? "Vanilla" : "Lumora Loader"));
        progress.progress(-1, "");
        // only Mojang's version JSON: Lumora needs no mod loader profile
        VersionJson vanilla = meta.vanilla(profile.minecraftVersion);
        ResolvedVersion version = ResolvedVersion.merge(null, vanilla, os);

        Path verifiedMarker = FileUtil.resolveInside(paths.versions(), version.id() + "/.lumora-verified");
        Downloader.Verify verify = !repair && Files.isRegularFile(verifiedMarker) ? Downloader.Verify.QUICK : Downloader.Verify.FULL;
        if (repair) {
            Files.deleteIfExists(verifiedMarker);
        }
        Log.info("Preparing profile " + profile.id + " (" + version.id() + "), verification " + verify);

        // client jar + libraries
        progress.stage(Lang.t("install.stage.check_files"));
        List<Downloader.Task> tasks = new ArrayList<>();
        Path clientJar = FileUtil.resolveInside(paths.versions(), vanilla.id + "/" + vanilla.id + ".jar");
        tasks.add(new Downloader.Task(URI.create(version.clientJar().url), clientJar,
                Hashing.Expected.sha1(version.clientJar().sha1), version.clientJar().size));
        for (ResolvedVersion.Library lib : version.libraries()) {
            if (lib.url() == null) {
                continue; // only loader installers make libraries without a source; vanilla has none
            }
            tasks.add(new Downloader.Task(URI.create(lib.url()), FileUtil.resolveInside(paths.libraries(), lib.path()),
                    Hashing.Expected.sha1(lib.sha1()), lib.size()));
        }
        downloader.downloadAll(tasks, progressAdapter(progress, Lang.t("storage.kind.libraries")), verify);

        // native libraries (up to 1.18.2): classifier jars, extracted into the natives directory below
        List<Downloader.Task> nativeTasks = new ArrayList<>();
        for (ResolvedVersion.NativeLibrary n : version.natives()) {
            nativeTasks.add(new Downloader.Task(URI.create(n.url()), FileUtil.resolveInside(paths.libraries(), n.path()),
                    Hashing.Expected.sha1(n.sha1()), n.size()));
        }
        downloader.downloadAll(nativeTasks, progressAdapter(progress, Lang.t("install.native_libraries")), verify);

        // assets
        progress.stage(Lang.t("install.stage.assets"));
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
        Path java = resolveJava(version, flavor == Flavor.LUMORA ? profile.javaComponent : null, javaOverride, config, verify, progress);

        Path gameDir = layout.gameDir();
        Path natives = layout.nativesDir();
        if (vanillaOnly) {
            // plain Minecraft ignores the mods folder; nothing of Lumora is installed
            Files.createDirectories(gameDir);
            FileUtil.writeAtomically(verifiedMarker, version.id().getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(natives);
            NativeExtractor.extract(version.natives(), paths.libraries(), natives);
            progress.progress(1, "");
            return new Prepared(profile, version, java, clientJar, layout, loggingConfig, null);
        }
        // the addon library next to the instances: addon jars there are offered in the hub (Addons) for installing
        Files.createDirectories(paths.addonLibrary());

        // the Lumora jar(s) of the profile (verified); they live in .lumora/client, a mods/ folder is left alone
        progress.stage(Lang.t("install.stage.client", profile.displayName));
        List<ProfileCatalog.Artifact> mods = catalog.resolveMods(profile, List.of(), null);
        ModInstaller modInstaller = new ModInstaller(downloader, paths.modCache(), bundled.get());
        ModInstaller.Result modResult = modInstaller.install(mods, layout.clientDir(),
                progressAdapter(progress, "Lumora"));

        // resource packs: same verified download into resourcepacks/, then switched on in options.txt; packs that
        // would need a mod (none without a mod loader) are skipped. Only in an own instance: the standard folder is
        // shared with Vanilla and the other versions, its resourcepacks/ and options.txt stay the player's own
        if (layout.own()) {
            List<ProfileCatalog.Artifact> packs = catalog.resolvePacks(profile, config.disabledResourcePacks).stream()
                    .filter(a -> a.requires == null || a.requires.isEmpty()).toList();
            progress.stage(Lang.t("install.stage.packs", packs.size()));
            modInstaller.install(packs, gameDir.resolve("resourcepacks"), progressAdapter(progress, Lang.t("library.packs")));
            ResourcePackOptions.apply(gameDir, packs);
        }

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
        return new Prepared(profile, version, java, clientJar, layout, loggingConfig,
                new dev.lumora.launcher.core.launch.LaunchCommandBuilder.Loader(loaderJar, modResult.modFiles(), mappings));
    }

    /**
     * Prepares plain Minecraft ({@code loaderId} null) or Minecraft with a mod loader for any Minecraft version, in the
     * folders of {@code layout}. The loader's launch profile inherits from Mojang's version JSON; every library comes with
     * the hash its publisher states (Maven {@code .sha1} where the profile has none). Mods in {@code mods/} are the
     * player's own and stay untouched.
     *
     * @param loaderVersion the loader version to install; null = the loader's recommended one
     * @return the prepared start; {@link Prepared#profile()} is null
     */
    public Prepared prepareGame(String minecraftVersion, String loaderId, String loaderVersion,
                                dev.lumora.launcher.core.InstanceLayout layout, String javaOverride, LauncherConfig config,
                                boolean repair, ProgressListener progress) throws IOException {
        return prepareGame(minecraftVersion, loaderId, loaderVersion, layout, javaOverride, config, repair, progress, null);
    }

    /**
     * @param javaComponent official Java runtime to use instead of the version's (the Lumora Client on Fabric needs the
     *                      Java of its Lumora version, e.g. 21 for 1.16.5), null = the version's own
     */
    public Prepared prepareGame(String minecraftVersion, String loaderId, String loaderVersion,
                                dev.lumora.launcher.core.InstanceLayout layout, String javaOverride, LauncherConfig config,
                                boolean repair, ProgressListener progress, String javaComponent) throws IOException {
        MetadataService meta = new MetadataService(downloader, paths.versions());
        dev.lumora.launcher.core.loader.ModLoader loader = loaderId == null ? null
                : loaders.loaders().byId(loaderId).orElseThrow(() -> new IOException(Lang.t("loader.error.unknown", loaderId)));
        if (loader != null && loaderVersion == null) {
            loaderVersion = loaders.recommended(loaderId, minecraftVersion).map(dev.lumora.launcher.core.loader.LoaderVersion::version)
                    .orElseThrow(() -> new IOException(Lang.t("loader.error.not_available", loader.displayName(), minecraftVersion)));
        }
        progress.stage(Lang.t("install.stage.version_info", minecraftVersion,
                loader == null ? "Vanilla" : loader.displayName() + " " + loaderVersion));
        progress.progress(-1, "");
        Downloader.Verify quick = repair ? Downloader.Verify.FULL : Downloader.Verify.QUICK;
        VersionJson vanilla = meta.vanilla(minecraftVersion);
        Path clientJar = FileUtil.resolveInside(paths.versions(), vanilla.id + "/" + vanilla.id + ".jar");
        Path java = null;
        VersionJson child = null;
        if (loader != null) {
            if (loader.needsOwnClientJar()) {
                // Forge / NeoForge patch the vanilla jar with their own tools: jar and Java first
                ResolvedVersion base = ResolvedVersion.merge(null, vanilla, os);
                downloader.download(new Downloader.Task(URI.create(base.clientJar().url), clientJar,
                        Hashing.Expected.sha1(base.clientJar().sha1), base.clientJar().size), null, quick);
                java = resolveJava(base, null, javaOverride, config, quick, progress);
            }
            child = loader.install(new dev.lumora.launcher.core.loader.ModLoader.Install(minecraftVersion, loaderVersion,
                    vanilla, clientJar, java, repair), progress);
            loaders.recordInstalled(loaderId, minecraftVersion, loaderVersion);
        }
        ResolvedVersion version = ResolvedVersion.merge(child, vanilla, os);

        Path verifiedMarker = FileUtil.resolveInside(paths.versions(), version.id() + "/.lumora-verified");
        Downloader.Verify verify = !repair && Files.isRegularFile(verifiedMarker) ? Downloader.Verify.QUICK : Downloader.Verify.FULL;
        if (repair) {
            Files.deleteIfExists(verifiedMarker);
        }
        Log.info("Preparing " + (loader == null ? "Vanilla" : loader.id() + " " + loaderVersion) + " " + minecraftVersion
                + " for " + layout.instanceId() + ", verification " + verify);

        progress.stage(Lang.t("install.stage.check_files"));
        List<Downloader.Task> tasks = new ArrayList<>();
        tasks.add(new Downloader.Task(URI.create(version.clientJar().url), clientJar,
                Hashing.Expected.sha1(version.clientJar().sha1), version.clientJar().size));
        for (ResolvedVersion.Library lib : version.libraries()) {
            Path target = FileUtil.resolveInside(paths.libraries(), lib.path());
            if (lib.url() == null) {
                // created by the loader installer (no download source)
                if (!Files.isRegularFile(target)) {
                    throw new IOException(Lang.t("loader.error.library_missing", lib.name(), loader == null ? "?" : loader.displayName()));
                }
                continue;
            }
            String sha1 = lib.sha1() != null ? lib.sha1() : fetchMavenSha1(lib.url());
            tasks.add(new Downloader.Task(URI.create(lib.url()), target, Hashing.Expected.sha1(sha1), lib.size()));
        }
        downloader.downloadAll(tasks, progressAdapter(progress, Lang.t("storage.kind.libraries")), verify);
        if (loader != null && loader.needsOwnClientJar()) {
            // like the official launcher: the vanilla jar under the loader's id, which Forge leaves out of its module layer
            Path own = FileUtil.resolveInside(paths.versions(), version.id() + "/" + version.id() + ".jar");
            Hashing.Expected vanillaHash = Hashing.Expected.sha1(version.clientJar().sha1);
            if (!Hashing.matches(own, vanillaHash)) {
                Path tmp = own.resolveSibling(own.getFileName() + ".part");
                Files.createDirectories(own.getParent());
                Files.copy(clientJar, tmp, StandardCopyOption.REPLACE_EXISTING);
                FileUtil.moveReplacing(tmp, own);
            }
            clientJar = own;
        }

        List<Downloader.Task> nativeTasks = new ArrayList<>();
        for (ResolvedVersion.NativeLibrary n : version.natives()) {
            String sha1 = n.sha1() != null ? n.sha1() : fetchMavenSha1(n.url());
            nativeTasks.add(new Downloader.Task(URI.create(n.url()), FileUtil.resolveInside(paths.libraries(), n.path()),
                    Hashing.Expected.sha1(sha1), n.size()));
        }
        downloader.downloadAll(nativeTasks, progressAdapter(progress, Lang.t("install.native_libraries")), verify);

        progress.stage(Lang.t("install.stage.assets"));
        VersionJson.AssetIndex idx = version.assetIndex();
        Path indexFile = FileUtil.resolveInside(paths.assets(), "indexes/" + idx.id + ".json");
        downloader.download(new Downloader.Task(URI.create(idx.url), indexFile, Hashing.Expected.sha1(idx.sha1), idx.size), null);
        AssetIndex assets = Json.read(indexFile, AssetIndex.class);
        downloader.downloadAll(assets.tasks(paths.assets()), progressAdapter(progress, "Assets"), verify);

        Path loggingConfig = null;
        if (version.logging() != null && version.logging().file != null) {
            VersionJson.Download f = version.logging().file;
            loggingConfig = FileUtil.resolveInside(paths.assets(), "log_configs/" + f.id);
            downloader.download(new Downloader.Task(URI.create(f.url), loggingConfig, Hashing.Expected.sha1(f.sha1), f.size), null, verify);
        }

        if (java == null) {
            java = resolveJava(version, javaComponent, javaOverride, config, verify, progress);
        }
        Files.createDirectories(layout.gameDir());
        if (loader != null) {
            Files.createDirectories(layout.gameDir().resolve("mods"));
        }
        FileUtil.writeAtomically(verifiedMarker, version.id().getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(layout.nativesDir());
        NativeExtractor.extract(version.natives(), paths.libraries(), layout.nativesDir());
        progress.progress(1, "");
        return new Prepared(null, version, java, clientJar, layout, loggingConfig, null);
    }

    private String fetchMavenSha1(String artifactUrl) throws IOException {
        // loader launch profiles omit the hash for some artifacts (e.g. fabric-loader itself); Maven publishes
        // <artifact>.sha1 next to every artifact on the same trusted host
        String body = downloader.fetchString(URI.create(artifactUrl + ".sha1")).strip();
        String sha1 = body.split("\\s+")[0];
        Hashing.Expected.sha1(sha1);
        return sha1;
    }

    /**
     * Variant of the Lumora Client for a mod loader: "fabric" (Fabric, Quilt: fabric.mod.json, Fabric's names) or "forge"
     * (Forge, NeoForge: mods.toml, Mojang's names, which both use at run time from 1.20.6), null for other loaders.
     */
    public static String lumoraVariant(String loaderId) {
        if (loaderId == null) {
            return null;
        }
        return switch (loaderId) {
            case "fabric", "quilt" -> "fabric";
            case "forge", "neoforge" -> "forge";
            default -> null;
        };
    }

    /** File name of a loader variant of the Lumora Client (shipped in the bundled folder), e.g. lumora-client-mc1.21.11-fabric.jar. */
    public static String variantJarName(String minecraftVersion, String variant) {
        return "lumora-client-mc" + minecraftVersion + "-" + variant + ".jar";
    }

    /** Whether the launcher ships the Lumora Client for this mod loader and Minecraft version. */
    public boolean hasLumoraFor(String loaderId, String minecraftVersion) {
        String variant = lumoraVariant(loaderId);
        try {
            return variant != null && bundled.get().contains(variantJarName(minecraftVersion, variant));
        } catch (RuntimeException e) {
            return false; // incomplete installation: no bundled index
        }
    }

    /** The loader variant of the Lumora Client, checked against its SHA-512 from the bundled index. */
    public Path lumoraFor(String loaderId, String minecraftVersion) throws IOException {
        String variant = lumoraVariant(loaderId);
        if (variant == null) {
            throw new IOException(Lang.t("lumora_mod.error.loader", loaderId));
        }
        return bundled.get().verified(variantJarName(minecraftVersion, variant));
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
    private Path resolveJava(ResolvedVersion version, String profileComponent, String javaOverride, LauncherConfig config,
                             Downloader.Verify verify, ProgressListener progress) throws IOException {
        String javaPath = javaOverride != null && !javaOverride.isBlank() ? javaOverride : config.javaPath;
        int required = version.javaVersion() != null ? version.javaVersion().majorVersion : 25;
        if (profileComponent != null && javaPath == null) {
            // newer official runtime than the version JSON asks for (the profile needs it, e.g. for the Lumora mod)
            int major = componentMajor(profileComponent);
            if (major < required) {
                throw new IOException(Lang.t("install.java_too_old", profileComponent, required));
            }
            progress.stage(Lang.t("install.stage.java", profileComponent, major));
            JavaRuntimeInstaller jri = new JavaRuntimeInstaller(downloader, paths.runtimes(), os);
            return jri.install(profileComponent, progressAdapter(progress, "Java"), verify).javaExecutable();
        }
        if (javaPath != null) {
            Path custom = Path.of(javaPath);
            int major = JavaProbe.majorVersion(custom);
            if (major < required) {
                throw new IOException(Lang.t("install.custom_java_old", major,
                        version.vanillaId(), required));
            }
            return custom;
        }
        String component = version.javaVersion() != null ? version.javaVersion().component : "java-runtime-epsilon";
        progress.stage(Lang.t("install.stage.java", component, required));
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
