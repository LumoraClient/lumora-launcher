package dev.lumora.core.addon;

import dev.lumora.api.addon.AddonDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The addon library: a folder {@code addons} in the Lumora folder (next to {@code instances}) holding Lumora addon
 * jars that are not installed yet. The hub lists them; "install" copies a jar into the game's {@code addons/} folder,
 * where {@link JarAddonLoader} loads it at the next start. Only the descriptor of a library jar is read, none of its
 * code runs before it is installed.
 * <p>
 * Replacing an older installed copy: on Windows a loaded jar stays locked, so its name goes into
 * {@code addons/.lumora-remove} and {@link #removePending} deletes it at the next start, before the addons load.
 */
public final class AddonLibrary {
    /** Name of the library folder inside the Lumora folder. */
    public static final String FOLDER = "addons";
    /** Jars to delete at the next start (one file name per line). */
    public static final String REMOVE_FILE = ".lumora-remove";

    public enum Status { AVAILABLE, INSTALLED, UPDATE, RESTART, INCOMPATIBLE, BROKEN }

    /**
     * One jar of the library.
     *
     * @param descriptor null if the jar is no readable Lumora addon ({@code error} says why)
     * @param installed  version of the installed copy, null if not installed
     */
    public record Entry(Path jar, AddonDescriptor descriptor, String error, Status status, String installed) {
        public String id() {
            return descriptor == null ? jar.getFileName().toString() : descriptor.id();
        }

        public String name() {
            return descriptor == null ? jar.getFileName().toString() : descriptor.name();
        }
    }

    private final Path library;
    private final Path addons;
    private final String minecraft;
    /** Ids installed in this session (they load after a restart). */
    private final Set<String> pending = new LinkedHashSet<>();

    /**
     * @param library   the library folder, null if there is none (no Lumora folder found)
     * @param addons    the game's addon folder
     * @param minecraft the running Minecraft version (for the compatibility check)
     */
    public AddonLibrary(Path library, Path addons, String minecraft) {
        this.library = library;
        this.addons = addons;
        this.minecraft = minecraft;
    }

    /**
     * The library folder for a game whose addon folder is {@code <Lumora folder>/instances/<id>/addons}, or null when
     * the game does not live in a Lumora folder. The system property {@code lumora.home} overrides the Lumora folder.
     */
    public static Path locate(Path addonsDir) {
        String home = System.getProperty("lumora.home");
        if (home != null && !home.isBlank()) {
            return Path.of(home).resolve(FOLDER);
        }
        if (addonsDir == null) {
            return null;
        }
        Path game = addonsDir.toAbsolutePath().getParent();
        Path instances = game == null ? null : game.getParent();
        if (instances == null || instances.getFileName() == null || instances.getParent() == null
                || !instances.getFileName().toString().equals("instances")) {
            return null;
        }
        return instances.getParent().resolve(FOLDER);
    }

    public Path folder() {
        return library;
    }

    /** All jars of the library with their state, by name; creates the folder if it is missing. */
    public List<Entry> scan() {
        if (library == null) {
            return List.of();
        }
        List<Path> jars = jars(library, true);
        Map<String, String> installed = new HashMap<>();
        for (Path jar : jars(addons, false)) {
            AddonDescriptor d = descriptorOrNull(jar);
            if (d != null && !removalPending(jar)) {
                installed.put(d.id(), d.version().toString());
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Path jar : jars) {
            AddonDescriptor d;
            try {
                d = descriptor(jar);
            } catch (IOException | AddonDescriptorParser.InvalidDescriptorException e) {
                out.add(new Entry(jar, null, e.getMessage(), Status.BROKEN, null));
                continue;
            }
            String have = installed.get(d.id());
            Status s;
            if (pending.contains(d.id())) {
                s = Status.RESTART;
            } else if (have != null) {
                s = d.version().compareTo(dev.lumora.api.Version.parse(have)) > 0 ? Status.UPDATE : Status.INSTALLED;
            } else if (minecraft != null && !d.supportsMinecraft(minecraft)) {
                s = Status.INCOMPATIBLE;
            } else {
                s = Status.AVAILABLE;
            }
            out.add(new Entry(jar, d, null, s, have));
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    /**
     * Copies the jar into the addon folder (an older copy of the same addon is removed now or at the next start).
     *
     * @return null on success, else the reason
     */
    public String install(Entry e) {
        if (e.descriptor() == null) {
            return e.error();
        }
        try {
            Files.createDirectories(addons);
            Path target = addons.resolve(e.jar().getFileName().toString());
            for (Path jar : jars(addons, false)) {
                AddonDescriptor d = descriptorOrNull(jar);
                if (d != null && d.id().equals(e.id()) && !jar.getFileName().equals(target.getFileName())) {
                    try {
                        Files.delete(jar);
                    } catch (IOException locked) {
                        markForRemoval(jar);
                    }
                }
            }
            Path tmp = addons.resolve(target.getFileName() + ".part");
            Files.copy(e.jar(), tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException locked) {
                // the same file name is loaded right now: keep the new copy under a new name, drop the old at restart
                Path other = addons.resolve(stem(target) + "-" + e.descriptor().version() + ".jar");
                Files.move(tmp, other, StandardCopyOption.REPLACE_EXISTING);
                markForRemoval(target);
            }
            pending.add(e.id());
            return null;
        } catch (IOException ex) {
            return ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
    }

    /** Deletes the jars listed in {@code addons/.lumora-remove} (at start, before the addons load). */
    public static void removePending(Path addonsDir) {
        if (addonsDir == null) {
            return;
        }
        Path list = addonsDir.resolve(REMOVE_FILE);
        if (!Files.isRegularFile(list)) {
            return;
        }
        try {
            for (String name : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                String n = name.trim();
                if (!n.isEmpty() && !n.contains("/") && !n.contains("\\") && n.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    Files.deleteIfExists(addonsDir.resolve(n));
                }
            }
            Files.delete(list);
        } catch (IOException ignored) {
            // tried again at the next start
        }
    }

    private boolean removalPending(Path jar) {
        Path list = addons.resolve(REMOVE_FILE);
        try {
            return Files.isRegularFile(list) && Files.readAllLines(list, StandardCharsets.UTF_8).stream()
                    .anyMatch(n -> n.trim().equals(jar.getFileName().toString()));
        } catch (IOException e) {
            return false;
        }
    }

    private void markForRemoval(Path jar) throws IOException {
        Path list = addons.resolve(REMOVE_FILE);
        List<String> names = Files.isRegularFile(list) ? new ArrayList<>(Files.readAllLines(list, StandardCharsets.UTF_8)) : new ArrayList<>();
        String n = jar.getFileName().toString();
        if (!names.contains(n)) {
            names.add(n);
        }
        Files.write(list, names, StandardCharsets.UTF_8);
    }

    private static String stem(Path jar) {
        String n = jar.getFileName().toString();
        return n.substring(0, n.length() - 4);
    }

    private static List<Path> jars(Path dir, boolean create) {
        if (dir == null) {
            return List.of();
        }
        try {
            if (create) {
                Files.createDirectories(dir);
            } else if (!Files.isDirectory(dir)) {
                return List.of();
            }
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted().toList();
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static AddonDescriptor descriptorOrNull(Path jar) {
        try {
            return descriptor(jar);
        } catch (IOException | AddonDescriptorParser.InvalidDescriptorException e) {
            return null;
        }
    }

    /** Reads only {@code lumora-addon.json} of a jar (size-limited, like {@link JarAddonLoader}). */
    static AddonDescriptor descriptor(Path jar) throws IOException, AddonDescriptorParser.InvalidDescriptorException {
        if (Files.size(jar) > JarAddonLoader.MAX_JAR_BYTES) {
            throw new IOException("Datei ist größer als " + (JarAddonLoader.MAX_JAR_BYTES >> 20) + " MB");
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(JarAddonLoader.DESCRIPTOR);
            if (entry == null || entry.isDirectory()) {
                throw new IOException(zip.getEntry("fabric.mod.json") != null
                        ? "Fabric-Mod ohne " + JarAddonLoader.DESCRIPTOR + " (gehört in den Ordner mods)"
                        : "Enthält keine " + JarAddonLoader.DESCRIPTOR + " (kein Lumora-Addon)");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return AddonDescriptorParser.parse(JarAddonLoader.readLimited(in, JarAddonLoader.MAX_DESCRIPTOR_BYTES),
                        FOLDER + "/" + jar.getFileName());
            }
        }
    }
}
