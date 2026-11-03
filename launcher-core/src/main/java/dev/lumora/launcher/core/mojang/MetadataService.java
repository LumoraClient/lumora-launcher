package dev.lumora.launcher.core.mojang;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fetches official metadata:
 * <ul>
 *   <li>Mojang version manifest v2 and the per-version JSON (verified against the manifest SHA-1)</li>
 * </ul>
 * Every document is cached below versions/ so an already installed profile can start offline.
 */
public final class MetadataService {
    public static final URI VERSION_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    static final class Manifest {
        List<Entry> versions;
    }

    static final class Entry {
        String id;
        String type;
        String url;
        String sha1;
    }

    private final Downloader downloader;
    private final Path versionsDir;

    public MetadataService(Downloader downloader, Path versionsDir) {
        this.downloader = downloader;
        this.versionsDir = versionsDir;
    }

    public Path vanillaJsonPath(String mcVersion) throws IOException {
        return FileUtil.resolveInside(versionsDir, mcVersion + "/" + mcVersion + ".json");
    }

    public VersionJson vanilla(String mcVersion) throws IOException {
        Path cached = vanillaJsonPath(mcVersion);
        Path hashFile = cached.resolveSibling(mcVersion + ".json.sha1");
        try {
            Manifest manifest = parse(downloader.fetchString(VERSION_MANIFEST), Manifest.class);
            Entry entry = manifest.versions == null ? null : manifest.versions.stream()
                    .filter(e -> mcVersion.equals(e.id)).findFirst().orElse(null);
            if (entry == null) {
                throw new IOException("Minecraft " + mcVersion + " is not listed in Mojang's version manifest");
            }
            Hashing.Expected expected = Hashing.Expected.sha1(entry.sha1);
            if (!Hashing.matches(cached, expected)) {
                String body = downloader.fetchVerified(URI.create(entry.url), expected);
                FileUtil.writeAtomically(cached, body.getBytes(StandardCharsets.UTF_8));
                FileUtil.writeAtomically(hashFile, entry.sha1.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            if (verifiedCacheAvailable(cached, hashFile)) {
                Log.warn("Using cached version metadata for " + mcVersion + " (" + e.getMessage() + ")");
            } else {
                throw new IOException("Could not obtain Minecraft " + mcVersion + " metadata: " + e.getMessage(), e);
            }
        }
        return Json.read(cached, VersionJson.class);
    }

    private static boolean verifiedCacheAvailable(Path cached, Path hashFile) throws IOException {
        if (!Files.isRegularFile(cached) || !Files.isRegularFile(hashFile)) {
            return false;
        }
        String sha1 = Files.readString(hashFile).strip();
        return Hashing.matches(cached, Hashing.Expected.sha1(sha1));
    }

    private static <T> T parse(String json, Class<T> type) throws IOException {
        try {
            T v = Json.GSON.fromJson(json, type);
            if (v == null) {
                throw new IOException("empty document");
            }
            return v;
        } catch (JsonParseException e) {
            throw new IOException("Malformed metadata: " + e.getMessage(), e);
        }
    }
}
