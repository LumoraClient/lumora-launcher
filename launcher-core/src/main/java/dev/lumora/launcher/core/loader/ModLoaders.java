package dev.lumora.launcher.core.loader;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.download.Downloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The mod loaders the launcher offers, in display order. Adding a loader = one {@link ModLoader} registered here. */
public final class ModLoaders {
    private final List<ModLoader> loaders;

    public ModLoaders(List<ModLoader> loaders) {
        List<String> ids = new ArrayList<>();
        for (ModLoader l : loaders) {
            if (!l.id().matches("[a-z][a-z0-9-]{1,31}") || ids.contains(l.id())) {
                throw new IllegalArgumentException("invalid or duplicate loader id: " + l.id());
            }
            ids.add(l.id());
        }
        this.loaders = List.copyOf(loaders);
    }

    public static ModLoaders standard(Downloader downloader, LauncherPaths paths) {
        return new ModLoaders(List.of(
                new FabricModLoader(downloader, paths.versions()),
                new QuiltModLoader(downloader, paths.versions()),
                new ForgeModLoader(downloader, paths.libraries(), paths.versions()),
                new NeoForgeModLoader(downloader, paths.libraries(), paths.versions())));
    }

    public List<ModLoader> all() {
        return loaders;
    }

    public Optional<ModLoader> byId(String id) {
        return loaders.stream().filter(l -> l.id().equals(id)).findFirst();
    }
}
