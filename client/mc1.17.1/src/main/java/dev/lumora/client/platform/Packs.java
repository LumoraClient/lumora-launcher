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
 *   <li>optional packs from {@code resourcepacks/<name>} in the jar (Lumora Theme, Lumora Schrift), copied once to
 *       {@code .lumora/packs/<name>} (these versions read packs only from files or folders); ids as before
 *       ("lumora:lumora_theme"), so saved choices stay.</li>
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
        for (Builtin p : OPTIONAL) {
            Path dir = extracted(p.name());
            if (dir == null) {
                continue;
            }
            Pack pack = Pack.create(LoaderEnv.CLIENT_ID + ":" + p.name(), false, () -> new FolderPackResources(dir.toFile()),
                    constructor, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) {
                out.accept(pack);
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
        Path dst = LumoraLoader.gameDir().resolve(".lumora").resolve("packs").resolve(name);
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
}
