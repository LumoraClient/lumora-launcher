package dev.lumora.launcher.core.java;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Installs the Java runtime that Mojang publishes for each Minecraft version (the same source the
 * official launcher uses): all.json -> component manifest (SHA-1) -> individual files (SHA-1 each).
 */
public final class JavaRuntimeInstaller {
    public static final URI ALL_JSON = URI.create(
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json");

    static final class ComponentEntry {
        Ref manifest;
        Version version;
    }

    static final class Ref {
        String sha1;
        long size;
        String url;
    }

    static final class Version {
        String name;
    }

    static final class RuntimeManifest {
        Map<String, FileEntry> files;
    }

    static final class FileEntry {
        String type;
        boolean executable;
        String target;
        Map<String, Ref> downloads;
    }

    public record Runtime(Path home, Path javaExecutable, String version) {
    }

    private final Downloader downloader;
    private final Path runtimesDir;
    private final OsInfo os;

    public JavaRuntimeInstaller(Downloader downloader, Path runtimesDir, OsInfo os) {
        this.downloader = downloader;
        this.runtimesDir = runtimesDir;
        this.os = os;
    }

    public Path homeFor(String component) throws IOException {
        return FileUtil.resolveInside(runtimesDir, component + "/" + os.javaRuntimePlatform());
    }

    public Runtime install(String component, Downloader.Progress progress, Downloader.Verify verify) throws IOException {
        Path home = homeFor(component);
        Path marker = home.resolve(".lumora-manifest.sha1");
        ComponentEntry entry;
        try {
            entry = selectComponent(downloader.fetchString(ALL_JSON), component);
        } catch (IOException e) {
            Runtime existing = existing(component);
            if (existing != null) {
                Log.warn("Java runtime metadata unavailable, using installed " + component + ": " + e.getMessage());
                return existing;
            }
            throw e;
        }
        Hashing.Expected manifestHash = Hashing.Expected.sha1(entry.manifest.sha1);
        String manifestJson = downloader.fetchVerified(URI.create(entry.manifest.url), manifestHash);
        RuntimeManifest manifest;
        try {
            manifest = Json.GSON.fromJson(manifestJson, RuntimeManifest.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed Java runtime manifest: " + e.getMessage(), e);
        }
        if (manifest == null || manifest.files == null) {
            throw new IOException("Empty Java runtime manifest for " + component);
        }

        List<Downloader.Task> tasks = new ArrayList<>();
        List<Path> executables = new ArrayList<>();
        List<Map.Entry<Path, String>> links = new ArrayList<>();
        for (Map.Entry<String, FileEntry> e : manifest.files.entrySet()) {
            Path target = FileUtil.resolveInside(home, e.getKey());
            FileEntry f = e.getValue();
            switch (f.type == null ? "" : f.type) {
                case "directory" -> Files.createDirectories(target);
                case "file" -> {
                    Ref raw = f.downloads == null ? null : f.downloads.get("raw");
                    if (raw == null) {
                        throw new IOException("Runtime file without raw download: " + e.getKey());
                    }
                    tasks.add(new Downloader.Task(URI.create(raw.url), target, Hashing.Expected.sha1(raw.sha1), raw.size));
                    if (f.executable) {
                        executables.add(target);
                    }
                }
                case "link" -> links.add(Map.entry(target, f.target));
                default -> throw new IOException("Unknown runtime entry type '" + f.type + "' for " + e.getKey());
            }
        }
        // A runtime whose manifest hash changed since the last install is always fully re-verified.
        boolean sameManifest = Files.isRegularFile(marker)
                && Files.readString(marker).strip().equals(entry.manifest.sha1);
        downloader.downloadAll(tasks, progress, sameManifest ? verify : Downloader.Verify.FULL);
        for (Path exe : executables) {
            if (!exe.toFile().setExecutable(true) && os.name() != OsInfo.Name.WINDOWS) {
                Log.warn("Could not mark executable: " + exe);
            }
        }
        if (os.name() != OsInfo.Name.WINDOWS) {
            for (Map.Entry<Path, String> link : links) {
                Path linkPath = link.getKey();
                // Resolve relative to the link's directory and make sure the target stays inside the runtime.
                FileUtil.resolveInside(home, home.relativize(linkPath.getParent().resolve(link.getValue()).normalize()).toString());
                Files.deleteIfExists(linkPath);
                Files.createDirectories(linkPath.getParent());
                Files.createSymbolicLink(linkPath, Path.of(link.getValue()));
            }
        }
        FileUtil.writeAtomically(marker, entry.manifest.sha1.getBytes(StandardCharsets.UTF_8));
        Runtime rt = new Runtime(home, javaExecutable(home), entry.version != null ? entry.version.name : "?");
        if (!Files.isRegularFile(rt.javaExecutable())) {
            throw new IOException("Java runtime installed but executable missing: " + rt.javaExecutable());
        }
        return rt;
    }

    /** Returns an already installed runtime without network access, or null. */
    public Runtime existing(String component) throws IOException {
        Path home = homeFor(component);
        Path exe = javaExecutable(home);
        if (Files.isRegularFile(exe) && Files.isRegularFile(home.resolve(".lumora-manifest.sha1"))) {
            return new Runtime(home, exe, "installed");
        }
        return null;
    }

    ComponentEntry selectComponent(String allJson, String component) throws IOException {
        try {
            com.google.gson.JsonObject root = Json.GSON.fromJson(allJson, com.google.gson.JsonObject.class);
            com.google.gson.JsonObject platform = root.getAsJsonObject(os.javaRuntimePlatform());
            if (platform == null || !platform.has(component) || platform.getAsJsonArray(component).isEmpty()) {
                throw new IOException("Mojang provides no Java runtime '" + component + "' for platform "
                        + os.javaRuntimePlatform());
            }
            ComponentEntry entry = Json.GSON.fromJson(platform.getAsJsonArray(component).get(0), ComponentEntry.class);
            if (entry.manifest == null || entry.manifest.url == null || entry.manifest.sha1 == null) {
                throw new IOException("Runtime entry without manifest: " + component);
            }
            return entry;
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IOException("Malformed java-runtime all.json: " + e.getMessage(), e);
        }
    }

    private Path javaExecutable(Path home) {
        if (os.name() == OsInfo.Name.OSX) {
            return home.resolve("jre.bundle/Contents/Home/bin/java");
        }
        return home.resolve("bin").resolve(os.javaExecutableName(false));
    }
}
