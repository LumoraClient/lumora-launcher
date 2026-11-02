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
    public final dev.lumora.launcher.core.loader.LoaderManager loaders;
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
        configLoad = configStore.load();
        config = configLoad.config();
        paths = new LauncherPaths(config.installDir != null ? Path.of(config.installDir) : defaultRoot);
        InstanceMigration.migrate(paths);
        downloader = new Downloader(config.parallelDownloads);
        Path bundledDir = BundledMods.locate();
        loaders = new dev.lumora.launcher.core.loader.LoaderManager(
                dev.lumora.launcher.core.loader.ModLoaders.standard(downloader, paths), paths.versions());
        installer = new ClientInstaller(paths, downloader, catalog, () -> {
            try {
                return new BundledMods(bundledDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, os, loaders);
        accounts = new AccountStore(bootstrap.authFile().getParent(), os);
        play = new PlayService(paths, installer, accounts, os);
        Log.info("Lumora launcher started; data=" + paths.root() + ", bundled=" + bundledDir + ", os=" + os);
    }

    public ProfileCatalog.Profile selectedProfile() {
        return catalog.profile(config.selectedProfile).orElseGet(() -> catalog.profile(catalog.defaultProfile()).orElseThrow());
    }

    public void saveConfig() throws IOException {
        configStore.save(config);
    }
}
