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
 *   <li>the layers from {@code resourcepacks/<name>} in the jar (Lumora Theme, the Lumora fonts): built into the client,
 *       not in the resource pack list or options.txt. {@link #withLayers} lays the active ones over the selected packs
 *       when the resources load ({@code PackRepositoryMixin}); the hub switches them (theme Lumora/Vanilla, font).</li>
 * </ul>
 */
public final class Packs {
    private record Builtin(String name, Component title) {
    }

    private static final List<Builtin> OPTIONAL = new CopyOnWriteArrayList<>();

    private Packs() {
    }

    /** A layer from {@code resourcepacks/<name>} of the Lumora jar (theme or font), not offered as a resource pack. */
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
                LAYERS.put(p.name(), pack);
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

    /** The layers by folder name ("lumora_theme", "lumora_font_outfit" ...), refreshed with every pack scan. */
    private static final java.util.Map<String, Pack> LAYERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether theme or font chosen in the hub differ from the loaded resources (a resource reload shows them). */
    public static boolean changed() {
        dev.lumora.client.config.LumoraConfig c = dev.lumora.client.LumoraClient.config();
        return c == null ? dev.lumora.core.theme.ThemeLayers.changed(null, false, null)
                : dev.lumora.core.theme.ThemeLayers.changed(c.theme, c.lumoraFont, c.font);
    }

    /** The active layers, lowest first (dev.lumora.core.theme.ThemeLayers), remembered as loaded. */
    private static List<Pack> activeLayers() {
        dev.lumora.client.config.LumoraConfig c = dev.lumora.client.LumoraClient.config();
        List<String> names = c == null ? dev.lumora.core.theme.ThemeLayers.load(null, false, null)
                : dev.lumora.core.theme.ThemeLayers.load(c.theme, c.lumoraFont, c.font);
        List<Pack> out = new java.util.ArrayList<>();
        for (String n : names) {
            Pack p = LAYERS.get(n);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * The packs of a resource load with the active layers (theme, font) added: above the player's packs, below packs
     * fixed on top such as server resource packs, so their fonts and textures still win. Other repositories (data
     * packs) stay as they are.
     */
    public static List<net.minecraft.server.packs.PackResources> withLayers(net.minecraft.server.packs.repository.PackRepository repo,
                                                                           List<net.minecraft.server.packs.PackResources> selected) {
        if (!repo.isAvailable(LoaderEnv.CLIENT_ID)) {
            return selected;
        }
        List<Pack> layers = activeLayers();
        if (layers.isEmpty()) {
            return selected;
        }
        // a pack can open several pack resources here: find the first one of a pack fixed on top by its id
        java.util.Set<String> fixedTop = new java.util.HashSet<>();
        for (Pack p : repo.getSelectedPacks()) {
            if (p.isFixedPosition() && p.getDefaultPosition() == Pack.Position.TOP) {
                fixedTop.add(p.getId());
            }
        }
        int at = selected.size();
        for (int i = 0; i < selected.size(); i++) {
            if (fixedTop.contains(selected.get(i).packId())) {
                at = i;
                break;
            }
        }
        List<net.minecraft.server.packs.PackResources> out = new java.util.ArrayList<>(selected);
        for (Pack l : layers) {
            List<net.minecraft.server.packs.PackResources> res = l.open().toList();
            out.addAll(at, res);
            at += res.size();
        }
        return out;
    }
}
