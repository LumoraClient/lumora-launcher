package dev.lumora.client.platform;

import dev.lumora.loader.api.LumoraLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lumora's resource packs, added to the game's built-in packs ({@code BuiltInPackSourceMixin}):
 * <ul>
 *   <li>"lumora" - the client's own {@code assets/} (texts, textures, shaders); always on, cannot be switched off,</li>
 *   <li>optional packs from {@code resourcepacks/<name>} in the jar (Lumora Theme, Lumora Schrift), switchable under
 *       Options > Resource Packs. Their ids ("lumora:lumora_theme") are the same as before, so saved choices stay.</li>
 * </ul>
 */
public final class Packs {
    private record Builtin(String name, Component title) {
    }

    private static final List<Builtin> OPTIONAL = new CopyOnWriteArrayList<>();

    private Packs() {
    }

    /** An optional built-in pack from {@code resourcepacks/<name>} of the Lumora jar. */
    public static void builtin(String name, Component title) {
        OPTIONAL.add(new Builtin(name, title));
    }

    public static void addTo(Consumer<Pack> out) {
        Optional<Path> root = LumoraLoader.root(LoaderEnv.CLIENT_ID);
        if (root.isEmpty()) {
            return;
        }
        Pack own = Pack.readMetaAndCreate(new PackLocationInfo(LoaderEnv.CLIENT_ID, Component.literal("Lumora Client"),
                        PackSource.BUILT_IN, Optional.empty()), new PathPackResources.PathResourcesSupplier(root.get()),
                PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
        if (own != null) {
            out.accept(own);
        }

        java.nio.file.Path addonDir = addonAssets();
        if (addonDir != null) {
            Pack addons = Pack.readMetaAndCreate(new PackLocationInfo(LoaderEnv.CLIENT_ID + ":addons", Component.literal("Lumora-Addons"),
                            PackSource.BUILT_IN, Optional.empty()), new PathPackResources.PathResourcesSupplier(addonDir),
                    PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
            if (addons != null) {
                out.accept(addons);
            }
        }
        for (Builtin p : OPTIONAL) {
            Path dir = root.get().resolve("resourcepacks").resolve(p.name());
            if (!Files.isDirectory(dir)) {
                continue;
            }
            Pack pack = Pack.readMetaAndCreate(new PackLocationInfo(LoaderEnv.CLIENT_ID + ":" + p.name(), p.title(),
                            PackSource.BUILT_IN, Optional.empty()), new PathPackResources.PathResourcesSupplier(dir),
                    PackType.CLIENT_RESOURCES, new PackSelectionConfig(false, Pack.Position.TOP, false));
            if (pack != null) {
                out.accept(pack);
            }
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
