package dev.lumora.client.platform;

import dev.lumora.loader.api.LoadedJar;
import dev.lumora.loader.api.LumoraLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.FolderPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Lumora's resource packs on Minecraft up to 1.18.2, added to the client's built-in packs
 * ({@code ClientPackSourceMixin}):
 * <ul>
 *   <li>"lumora" - the client jar itself (its {@code assets/}), always on,</li>
 *   <li>the layers from {@code resourcepacks/<name>} in the jar (Lumora Theme, the Lumora fonts), copied once to
 *       {@code .lumora/packs/<name>} (these versions read packs only from files or folders): built into the client,
 *       not in the resource pack list or options.txt. {@link #withLayers} lays the active ones over the selected
 *       packs when the resources load ({@code PackRepositoryMixin}); the hub switches them.</li>
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

    public static void addTo(Consumer<Pack> out, Pack.PackConstructor constructor) {
        Optional<LoadedJar> jar = LumoraLoader.component(LoaderEnv.CLIENT_ID);
        if (jar.isEmpty()) {
            return;
        }
        Pack own = Pack.create(LoaderEnv.CLIENT_ID, true, () -> new FilePackResources(jar.get().file().toFile()), constructor,
                Pack.Position.BOTTOM, PackSource.BUILT_IN);
        if (own != null) {
            out.accept(own);
        }

        java.nio.file.Path addonDir = addonAssets();
        if (addonDir != null) {
            Pack addons = Pack.create(LoaderEnv.CLIENT_ID + ":addons", true, () -> new FolderPackResources(addonDir.toFile()), constructor,
                    Pack.Position.BOTTOM, PackSource.BUILT_IN);
            if (addons != null) {
                out.accept(addons);
            }
        }
        for (Builtin p : OPTIONAL) {
            Path dir = extracted(p.name());
            if (dir == null) {
                continue;
            }
            Pack pack = Pack.create(LoaderEnv.CLIENT_ID + ":" + p.name(), false, () -> new FolderPackResources(dir.toFile()),
                    constructor, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) {
                LAYERS.put(p.name(), pack);
            }
        }
    }

    /** {@code resourcepacks/<name>} of the jar as a folder under {@code .lumora/packs}, refreshed when the jar changes. */
    private static Path extracted(String name) {
        Optional<Path> root = LumoraLoader.root(LoaderEnv.CLIENT_ID);
        Optional<LoadedJar> jar = LumoraLoader.component(LoaderEnv.CLIENT_ID);
        if (root.isEmpty() || jar.isEmpty()) {
            return null;
        }
        Path src = root.get().resolve("resourcepacks").resolve(name);
        if (!Files.isDirectory(src)) {
            return null;
        }
        Path dst = LumoraLoader.instanceDir().resolve(".lumora").resolve("packs").resolve(name);
        try {
            String stamp = jar.get().version() + " " + Files.size(jar.get().file()) + " " + Files.getLastModifiedTime(jar.get().file()).toMillis();
            Path marker = dst.resolve(".lumora-extracted");
            if (Files.isRegularFile(marker) && Files.readString(marker).equals(stamp)) {
                return dst;
            }
            try (Stream<Path> s = Files.walk(src)) {
                for (Path f : s.toList()) {
                    Path rel = dst.resolve(src.relativize(f).toString());
                    if (Files.isDirectory(f)) {
                        Files.createDirectories(rel);
                    } else {
                        Files.createDirectories(rel.getParent());
                        try (InputStream in = Files.newInputStream(f)) {
                            Files.copy(in, rel, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            Files.writeString(marker, stamp);
            return dst;
        } catch (IOException e) {
            return Files.isDirectory(dst) ? dst : null;
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
        return dev.lumora.core.addon.AddonAssets.prepare(LumoraLoader.lumoraDir().resolve("addons"),
                LumoraLoader.instanceDir().resolve(".lumora").resolve("addon-assets"),
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
        // openAllSelected opens one pack resource per selected pack, in the same order
        int at = 0;
        for (Pack p : repo.getSelectedPacks()) {
            if (p.isFixedPosition() && p.getDefaultPosition() == Pack.Position.TOP) {
                break;
            }
            at++;
        }
        List<net.minecraft.server.packs.PackResources> out = new java.util.ArrayList<>(selected);
        at = Math.min(at, out.size());
        for (Pack l : layers) {
            out.add(at++, l.open());
        }
        return out;
    }
}
