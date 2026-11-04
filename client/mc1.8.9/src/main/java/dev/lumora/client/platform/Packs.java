package dev.lumora.client.platform;

import dev.lumora.loader.api.LoadedJar;
import dev.lumora.loader.api.LumoraLoader;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ZipResourcePack;

import java.util.List;
import java.util.Optional;

/** Lumora's own assets on Minecraft 1.8.9 / 1.12.2: the client jar as one of the game's default resource packs. */
public final class Packs {
    private Packs() {
    }

    public static void addTo(List<ResourcePack> defaults) {
        Optional<LoadedJar> jar = LumoraLoader.component(LoaderEnv.CLIENT_ID);
        jar.ifPresent(j -> defaults.add(new ZipResourcePack(j.file().toFile())));
        java.nio.file.Path addonDir = addonAssets();
        if (addonDir != null) {
            defaults.add(new net.minecraft.resource.DirectoryResourcePack(addonDir.toFile()));
        }
    }

    /**
     * The assets of the addon jars in {@code addons/} (textures, fonts, sounds) as one folder for the always-on pack
     * "Lumora-Addons" ({@link dev.lumora.core.addon.AddonAssets}), or null when no addon brings any.
     */
    private static java.nio.file.Path addonAssets() {
        byte[] meta = null;
        java.util.Optional<java.nio.file.Path> root = LumoraLoader.root(LoaderEnv.CLIENT_ID);
        if (root.isPresent()) {
            try {
                meta = java.nio.file.Files.readAllBytes(root.get().resolve("pack.mcmeta"));
            } catch (java.io.IOException | RuntimeException e) {
                meta = null;
            }
        }
        java.nio.file.Path game = LumoraLoader.gameDir();
        return dev.lumora.core.addon.AddonAssets.prepare(game.resolve("addons"), game.resolve(".lumora").resolve("addon-assets"),
                meta, w -> dev.lumora.client.LumoraClient.LOG.warn(w));
    }
}
