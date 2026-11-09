package dev.lumora.core.addon;

import dev.lumora.api.addon.AddonDependency;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.core.CoreLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Lumora's own addon loader: Lumora addons as plain jars in the game's {@code addons/} folder, without being a mod of
 * a mod loader. An addon jar only needs a {@code lumora-addon.json} at its root (same format as before) and classes
 * built against the Lumora API.
 * <p>
 * Each jar gets its own {@link AddonClassLoader}. Nothing of a jar runs during discovery: only the descriptor is read
 * (size-limited); the entry point class is loaded when the {@link AddonManager} initializes the addon, i.e. after the
 * API version, Minecraft version and dependencies were checked. Problems (no descriptor, broken jar, a Fabric mod put
 * into the wrong folder) become failed entries with a reason, shown in the addon list like any other failure.
 * <p>
 * Addons packaged as Fabric mods with a {@code lumora-addon.json} in {@code mods/} keep working next to this
 * (the version adapter's Fabric discovery); an id may only exist once.
 */
public final class JarAddonLoader implements AutoCloseable {
    public static final String DESCRIPTOR = "lumora-addon.json";
    /** Larger files are not opened (an addon is code plus a few assets). */
    static final long MAX_JAR_BYTES = 128L * 1024 * 1024;
    static final int MAX_DESCRIPTOR_BYTES = 64 * 1024;

    private final Path dir;
    private final ClassLoader parent;
    private final CoreLogger log;
    private final Map<String, AddonClassLoader> byId = new ConcurrentHashMap<>();
    private final List<AddonClassLoader> loaders = new CopyOnWriteArrayList<>();

    /**
     * @param dir    the addon folder, null = no jar addons (tests, adapters without a game folder)
     * @param parent the game's class loader (sees the Lumora API, Minecraft and the mods)
     */
    public JarAddonLoader(Path dir, ClassLoader parent, CoreLogger log) {
        this.dir = dir;
        this.parent = parent;
        this.log = log;
    }

    /** The addon folder, or null. */
    public Path directory() {
        return dir;
    }

    /** Class loader of an addon jar from the folder (e.g. for the smoke test), empty for other addons. */
    public java.util.Optional<ClassLoader> classLoader(String addonId) {
        return java.util.Optional.ofNullable(byId.get(addonId));
    }

    /** Reads every {@code *.jar} in the folder (created if missing), sorted by file name. */
    public List<AddonCandidate> discover() {
        if (dir == null) {
            return List.of();
        }
        List<Path> jars;
        try {
            Files.createDirectories(dir);
            AddonLibrary.removePending(dir); // older copies replaced from the addon library while they were loaded
            try (Stream<Path> s = Files.list(dir)) {
                jars = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Addon folder " + dir + " could not be read: " + e);
            return List.of();
        }
        List<AddonCandidate> out = new ArrayList<>();
        for (Path jar : jars) {
            out.add(read(jar));
        }
        if (!out.isEmpty()) {
            log.info("Addon folder " + dir + ": " + out.size() + " jar(s)");
        }
        return out;
    }

    AddonCandidate read(Path jar) {
        String source = "addons/" + jar.getFileName();
        AddonDescriptor d;
        try {
            if (Files.size(jar) > MAX_JAR_BYTES) {
                return AddonCandidate.broken(source, "Datei ist größer als " + (MAX_JAR_BYTES >> 20) + " MB");
            }
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(DESCRIPTOR);
                if (entry == null || entry.isDirectory()) {
                    return AddonCandidate.broken(source, zip.getEntry("fabric.mod.json") != null
                            ? "Das ist eine Fabric-Mod ohne " + DESCRIPTOR + ", sie gehört in den Ordner mods"
                            : "Enthält keine " + DESCRIPTOR + " (kein Lumora-Addon)");
                }
                String json;
                try (InputStream in = zip.getInputStream(entry)) {
                    json = readLimited(in, MAX_DESCRIPTOR_BYTES);
                }
                d = AddonDescriptorParser.parse(json, source);
            }
        } catch (ZipException e) {
            return AddonCandidate.broken(source, "Keine gültige Jar-Datei (" + e.getMessage() + ")");
        } catch (IOException e) {
            return AddonCandidate.broken(source, DESCRIPTOR + " konnte nicht gelesen werden: " + e.getMessage());
        } catch (AddonDescriptorParser.InvalidDescriptorException e) {
            return AddonCandidate.broken(source, e.getMessage());
        }
        if (AddonClassLoader.isProtected(d.entrypoint())) {
            return AddonCandidate.broken(source, "Einstiegspunkt " + d.entrypoint() + " liegt in einem geschützten Paket");
        }
        List<String> peers = new ArrayList<>();
        for (AddonDependency dep : d.dependencies()) {
            if (!dep.id().equals(AddonManager.API_ID)) {
                peers.add(dep.id());
            }
        }
        AddonClassLoader loader;
        try {
            loader = new AddonClassLoader(d.id(), jar.toUri().toURL(), parent, peers, byId::get);
        } catch (MalformedURLException e) {
            return AddonCandidate.broken(source, "Ungültiger Pfad: " + e.getMessage());
        }
        loaders.add(loader);
        // a duplicate id keeps the first jar's loader for lookups; the manager marks the duplicate as failed anyway
        byId.putIfAbsent(d.id(), loader);
        String entrypoint = d.entrypoint();
        return AddonCandidate.fromJar(d, () -> instantiate(loader, entrypoint), jar);
    }

    static LumoraAddon instantiate(AddonClassLoader loader, String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, true, loader);
        if (type.getClassLoader() != loader) {
            throw new ClassNotFoundException(className + " liegt nicht in der Addon-Datei (" + type.getClassLoader() + ")");
        }
        if (!LumoraAddon.class.isAssignableFrom(type)) {
            throw new ClassCastException(className + " implementiert nicht " + LumoraAddon.class.getName());
        }
        return (LumoraAddon) type.getDeclaredConstructor().newInstance();
    }

    static String readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) {
                throw new IOException(DESCRIPTOR + " ist größer als " + (max >> 10) + " KB");
            }
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Number of jar class loaders (diagnostics/tests). */
    int loaderCount() {
        return loaders.size();
    }

    /** Releases the jar files (on Windows they stay locked while open). Called when the client stops. */
    @Override
    public void close() {
        for (AddonClassLoader l : loaders) {
            try {
                l.close();
            } catch (IOException e) {
                log.warn("Addon " + l.addonId() + ": jar not closed: " + e);
            }
        }
        loaders.clear();
        byId.clear();
    }
}
