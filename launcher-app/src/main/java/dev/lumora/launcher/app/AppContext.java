package dev.lumora.launcher.app;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.PlayService;
import dev.lumora.launcher.core.auth.AccountStore;
import dev.lumora.launcher.core.config.ConfigStore;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.install.ClientInstaller;
import dev.lumora.launcher.core.install.InstanceMigration;
import dev.lumora.launcher.core.mods.BundledMods;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Wires the launcher services. The settings file always lives in the platform default location
 * (so it can be found again), while game data lives in {@link LauncherConfig#installDir} if set.
 */
public final class AppContext {
    public final OsInfo os;
    public final Path defaultRoot;
    public final ConfigStore configStore;
    public final ConfigStore.LoadResult configLoad;
    public final LauncherConfig config;
    public final LauncherPaths paths;
    public final ProfileCatalog catalog;
    public final Downloader downloader;
    public final ClientInstaller installer;
    public final AccountStore accounts;
    public final PlayService play;

    public AppContext() throws IOException {
        os = OsInfo.current();
        defaultRoot = LauncherPaths.defaultRoot(os);
        Log.init(defaultRoot.resolve("logs"));
        LauncherPaths bootstrap = new LauncherPaths(defaultRoot);
        catalog = ProfileCatalog.load(bootstrap.lockfileOverride());
        configStore = new ConfigStore(bootstrap.configFile(),
                catalog.profiles().stream().map(p -> p.id).toList(), catalog.allExtensionIds(), catalog.defaultProfile());
        boolean fresh = !java.nio.file.Files.exists(bootstrap.configFile()) && !java.nio.file.Files.exists(defaultRoot.resolve("instances"))
                && !java.nio.file.Files.exists(defaultRoot.resolve("libraries"));
        configLoad = configStore.load();
        config = configLoad.config();
        if (fresh && config.installDir == null) {
            // new installation: all data next to Minecraft's own (.minecraft/LumoraClient), like other clients do;
            // the installer's choice below wins. Existing installations keep their folder.
            config.installDir = dev.lumora.launcher.core.InstanceLayout.minecraftDir(os).resolve(LauncherPaths.APP_DIR_NAME).toString();
            saveQuietly();
        }
        // choices from the Windows installer (data directory, versions, design), once
        if (dev.lumora.launcher.core.config.SetupHandoff.apply(bootstrap.configFile(), config,
                catalog.profiles().stream().map(p -> p.id).toList())) {
            saveQuietly();
        }
        paths =new LauncherPaths(config.installDir != null ? Path.of(config.installDir) : defaultRoot);
        InstanceMigration.migrate(paths);
        // versions with an instance folder from before the standard folder (.minecraft) keep it, once
        if (dev.lumora.launcher.core.InstanceLayout.adoptExisting(paths, config)) {
            saveQuietly();
        }
        downloader = new Downloader(config.parallelDownloads);
        Path bundledDir = BundledMods.locate();
        installer = new ClientInstaller(paths, downloader, catalog, () -> {
            try {
                return new BundledMods(bundledDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, os);
        accounts = new AccountStore(bootstrap.authFile().getParent(), os);
        play = new PlayService(paths, installer, accounts, os);
        play.history().setEnabled(config.connectionHistory);
        Log.info("Lumora launcher started; data=" + paths.root() + ", bundled=" + bundledDir + ", os=" + os);
    }

    public ProfileCatalog.Profile selectedProfile() {
        return catalog.profile(config.selectedProfile).orElseGet(() -> catalog.profile(catalog.defaultProfile()).orElseThrow());
    }

    /** Versions shown in the launcher (installer / settings choice; all when none is chosen), always with the selected one. */
    public java.util.List<ProfileCatalog.Profile> visibleProfiles() {
        java.util.List<ProfileCatalog.Profile> all = catalog.profiles();
        if (config.visibleProfiles == null || config.visibleProfiles.isEmpty()) {
            return all;
        }
        return all.stream().filter(p -> config.visibleProfiles.contains(p.id) || p.id.equals(config.selectedProfile)).toList();
    }

    public void saveConfig() throws IOException {
        configStore.save(config);
    }

    private void saveQuietly() {
        try {
            configStore.save(config);
        } catch (IOException e) {
            Log.warn("Could not save the launcher settings", e);
        }
    }

    /** Folders of an instance (standard folder or own, see InstanceLayout). */
    public dev.lumora.launcher.core.InstanceLayout layout(String instanceId) {
        return dev.lumora.launcher.core.InstanceLayout.of(paths, config, instanceId);
    }

    /** Game folder of an instance ({@code --gameDir}). */
    public Path gameDir(String instanceId) {
        return layout(instanceId).gameDir();
    }
}
