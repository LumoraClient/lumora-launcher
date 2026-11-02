package dev.lumora.launcher.core.mojang;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.lumora.launcher.core.util.OsInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A version JSON merged with its parent (inheritsFrom), filtered for the current OS.
 * Child libraries win over parent libraries with the same group:artifact[:classifier].
 * <p>
 * Two older formats are supported as well:
 * <ul>
 *   <li>Native jars (up to 1.18.2): libraries with a {@code natives} map. Their classifier jar for this OS is
 *       downloaded and extracted into the natives directory before start ({@link #natives()}).</li>
 *   <li>{@code minecraftArguments} (up to 1.12.2): a plain game argument string; the JVM arguments are the defaults
 *       the official launcher used for those versions ({@link #LEGACY_JVM_ARGS}).</li>
 * </ul>
 */
public final class ResolvedVersion {
    /** {@code path} is relative to the libraries directory; {@code url} is null for files a loader installer created. */
    public record Library(String name, String path, String url, String sha1, long size) {
    }

    /** A native jar to extract; {@code excludes} are path prefixes inside the jar (e.g. "META-INF/"). */
    public record NativeLibrary(String name, String path, String url, String sha1, long size, List<String> excludes) {
    }

    /** JVM arguments the official launcher passed to versions without an {@code arguments} block. */
    static final List<String> LEGACY_JVM_ARGS = List.of(
            "-Djava.library.path=${natives_directory}",
            "-Dminecraft.launcher.brand=${launcher_name}",
            "-Dminecraft.launcher.version=${launcher_version}",
            "-cp", "${classpath}");

    private final String id;
    private final String vanillaId;
    private final String type;
    private final String mainClass;
    private final List<JsonElement> jvmArgs;
    private final List<JsonElement> gameArgs;
    private final List<Library> libraries;
    private final List<NativeLibrary> natives;
    private final VersionJson.AssetIndex assetIndex;
    private final VersionJson.Download clientJar;
    private final VersionJson.JavaVersion javaVersion;
    private final VersionJson.LoggingEntry logging;

    private ResolvedVersion(String id, String vanillaId, String type, String mainClass, List<JsonElement> jvmArgs,
                            List<JsonElement> gameArgs, List<Library> libraries, List<NativeLibrary> natives,
                            VersionJson.AssetIndex assetIndex, VersionJson.Download clientJar,
                            VersionJson.JavaVersion javaVersion, VersionJson.LoggingEntry logging) {
        this.id = id;
        this.vanillaId = vanillaId;
        this.type = type;
        this.mainClass = mainClass;
        this.jvmArgs = jvmArgs;
        this.gameArgs = gameArgs;
        this.libraries = libraries;
        this.natives = natives;
        this.assetIndex = assetIndex;
        this.clientJar = clientJar;
        this.javaVersion = javaVersion;
        this.logging = logging;
    }

    /**
     * @param child  the loader profile (e.g. Fabric) or null for plain vanilla
     * @param parent the vanilla version JSON
     */
    public static ResolvedVersion merge(VersionJson child, VersionJson parent, OsInfo os) {
        if (parent == null) {
            throw new IllegalArgumentException("vanilla version JSON required");
        }
        if (child != null && child.inheritsFrom != null && !child.inheritsFrom.equals(parent.id)) {
            throw new IllegalArgumentException("Profile " + child.id + " inherits from " + child.inheritsFrom
                    + " but parent is " + parent.id);
        }
        if (parent.arguments == null && (parent.minecraftArguments == null || parent.minecraftArguments.isBlank())) {
            throw new IllegalArgumentException("Version " + parent.id + " has neither 'arguments' nor 'minecraftArguments'");
        }
        Map<String, Library> libs = new LinkedHashMap<>();
        Map<String, NativeLibrary> natives = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        if (child != null) {
            addLibraries(libs, natives, taken, child.libraries, os);
        }
        addLibraries(libs, natives, taken, parent.libraries, os);

        List<JsonElement> jvm = new ArrayList<>();
        List<JsonElement> game = new ArrayList<>();
        if (parent.arguments != null) {
            jvm.addAll(parent.arguments.jvm);
            game.addAll(parent.arguments.game);
        } else {
            for (String a : LEGACY_JVM_ARGS) {
                jvm.add(new JsonPrimitive(a));
            }
            // a loader profile with its own legacy string replaces the vanilla one (same as the official launcher)
            String legacy = child != null && child.minecraftArguments != null ? child.minecraftArguments : parent.minecraftArguments;
            for (String a : legacy.trim().split("\\s+")) {
                game.add(new JsonPrimitive(a));
            }
        }
        if (child != null && child.arguments != null) {
            jvm.addAll(child.arguments.jvm);
            game.addAll(child.arguments.game);
        }
        VersionJson.Download client = parent.downloads == null ? null : parent.downloads.get("client");
        if (client == null || client.sha1 == null) {
            throw new IllegalArgumentException("Version " + parent.id + " has no client download");
        }
        return new ResolvedVersion(
                child != null ? child.id : parent.id,
                parent.id,
                parent.type,
                child != null && child.mainClass != null ? child.mainClass : parent.mainClass,
                List.copyOf(jvm), List.copyOf(game), List.copyOf(libs.values()), List.copyOf(natives.values()),
                parent.assetIndex, client, parent.javaVersion,
                parent.logging != null ? parent.logging.client : null);
    }

    private static void addLibraries(Map<String, Library> out, Map<String, NativeLibrary> nativesOut, Set<String> taken,
                                     List<VersionJson.Library> in, OsInfo os) {
        if (in == null) {
            return;
        }
        for (VersionJson.Library l : in) {
            if (!Rules.allowed(l.rules, os, Map.of())) {
                continue;
            }
            MavenCoordinate c = MavenCoordinate.parse(l.name);
            String key = c.dedupeKey();
            if (l.natives != null && !l.natives.isEmpty()) {
                // own namespace: Mojang lists e.g. org.lwjgl:lwjgl twice (classes and natives) from 1.13 to 1.18
                String nativeKey = "natives:" + key;
                if (taken.contains(nativeKey)) {
                    continue;
                }
                taken.add(nativeKey);
                addNative(nativesOut, l, c, os);
                // Mojang-style entries can also carry a regular (often empty) artifact for the classpath
                if (l.downloads != null && l.downloads.artifact != null && l.downloads.artifact.url != null && !taken.contains(key)) {
                    VersionJson.Download a = l.downloads.artifact;
                    taken.add(key);
                    out.put(key, new Library(l.name, a.path != null && !a.path.isEmpty() ? a.path : c.path(), a.url, a.sha1, a.size));
                }
                continue;
            }
            if (taken.contains(key)) {
                continue;
            }
            String path = c.path();
            String url;
            String sha1;
            long size;
            if (l.downloads != null && l.downloads.artifact != null) {
                VersionJson.Download a = l.downloads.artifact;
                url = a.url;
                sha1 = a.sha1;
                size = a.size;
                if (a.path != null && !a.path.isEmpty()) {
                    path = a.path;
                }
            } else {
                String base = l.url != null ? l.url : "https://libraries.minecraft.net/";
                url = (base.endsWith("/") ? base : base + "/") + c.path();
                sha1 = l.sha1 == null || l.sha1.isEmpty() ? null : l.sha1;
                size = l.size != null ? l.size : 0;
            }
            if (url == null || url.isEmpty()) {
                // no download: the loader installer put the file there (Forge jar of 1.12.2, patched client)
                url = null;
            }
            taken.add(key);
            out.put(key, new Library(l.name, path, url, sha1, size));
        }
    }

    private static void addNative(Map<String, NativeLibrary> out, VersionJson.Library l, MavenCoordinate c, OsInfo os) {
        String template = l.natives.get(os.name().mojangName);
        if (template == null) {
            return; // no native for this OS
        }
        String classifier = template.replace("${arch}", os.arch().is64Bit() ? "64" : "32");
        List<String> excludes = l.extract != null && l.extract.exclude != null ? List.copyOf(l.extract.exclude) : List.of();
        VersionJson.Download d = l.downloads != null && l.downloads.classifiers != null ? l.downloads.classifiers.get(classifier) : null;
        MavenCoordinate nc = c.withClassifier(classifier);
        String key = c.dedupeKey() + ":" + classifier;
        if (d != null && d.url != null) {
            out.putIfAbsent(key, new NativeLibrary(nc.coordinates(), d.path != null && !d.path.isEmpty() ? d.path : nc.path(), d.url, d.sha1, d.size, excludes));
        } else if (l.url != null) {
            String base = l.url.endsWith("/") ? l.url : l.url + "/";
            out.putIfAbsent(key, new NativeLibrary(nc.coordinates(), nc.path(), base + nc.path(), null, 0, excludes));
        }
    }

    public String id() {
        return id;
    }

    public String vanillaId() {
        return vanillaId;
    }

    public String type() {
        return type;
    }

    public String mainClass() {
        return mainClass;
    }

    public List<JsonElement> jvmArgs() {
        return jvmArgs;
    }

    public List<JsonElement> gameArgs() {
        return gameArgs;
    }

    public List<Library> libraries() {
        return libraries;
    }

    /** Native jars to extract before start (empty from 1.19 on). */
    public List<NativeLibrary> natives() {
        return natives;
    }

    public VersionJson.AssetIndex assetIndex() {
        return assetIndex;
    }

    public VersionJson.Download clientJar() {
        return clientJar;
    }

    public VersionJson.JavaVersion javaVersion() {
        return javaVersion;
    }

    public VersionJson.LoggingEntry logging() {
        return logging;
    }
}
