package dev.lumora.launcher.core.mojang;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
    public static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    /** Legacy Fabric (1.8.9, 1.12.2 and other versions before 1.14): same loader, own intermediary mappings and LWJGL fixes. */
    public static final String LEGACY_FABRIC_META = "https://meta.legacyfabric.net/v2";

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

    /** One entry of Mojang's version list. */
    public record GameVersion(String id, String type) {
        public boolean release() {
            return "release".equals(type);
        }
    }

    /**
     * Minecraft versions from Mojang's manifest, newest first: releases, and snapshots too if asked. The list is kept in
     * {@code versions/version_manifest_v2.json} so instance creation also works offline.
     */
    public List<GameVersion> gameVersions(boolean snapshots) throws IOException {
        Path cached = versionsDir.resolve("version_manifest_v2.json");
        String body;
        try {
            body = downloader.fetchString(VERSION_MANIFEST);
            FileUtil.writeAtomically(cached, body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (!Files.isRegularFile(cached)) {
                throw e;
            }
            body = Files.readString(cached, StandardCharsets.UTF_8);
        }
        Manifest m = parse(body, Manifest.class);
        List<GameVersion> out = new java.util.ArrayList<>();
        if (m.versions != null) {
            for (Entry e : m.versions) {
                if (e.id != null && e.id.matches("[0-9A-Za-z._ -]{1,32}") && ("release".equals(e.type) || snapshots && "snapshot".equals(e.type))) {
                    out.add(new GameVersion(e.id, e.type));
                }
            }
        }
        return out;
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

    /**
     * Fabric Loader launch profile from meta.fabricmc.net (HTTPS; Fabric publishes no hash for the profile itself, its
     * libraries are verified like all others). Cached next to the profile so an installed loader starts offline.
     *
     * @param legacy load the profile from Legacy Fabric (meta.legacyfabric.net) instead of meta.fabricmc.net
     */
    public VersionJson fabricProfile(String mcVersion, String loaderVersion, boolean legacy) throws IOException {
        String id = "fabric-loader-" + loaderVersion + "-" + mcVersion;
        Path cached = FileUtil.resolveInside(versionsDir, id + "/" + id + ".json");
        try {
            URI uri = URI.create((legacy ? LEGACY_FABRIC_META : FABRIC_META) + "/versions/loader/" + enc(mcVersion) + "/" + enc(loaderVersion) + "/profile/json");
            String body = downloader.fetchString(uri);
            VersionJson parsed = parse(body, VersionJson.class);
            if (parsed.mainClass == null || !mcVersion.equals(parsed.inheritsFrom)) {
                throw new IOException("Unexpected Fabric profile for " + mcVersion + "/" + loaderVersion);
            }
            FileUtil.writeAtomically(cached, body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (Files.isRegularFile(cached)) {
                Log.warn("Using cached Fabric profile (" + e.getMessage() + ")");
            } else {
                throw new IOException(dev.lumora.launcher.core.i18n.Lang.t("loader.error.profile", (legacy ? "Legacy " : "") + "Fabric Loader " + loaderVersion,
                        mcVersion, e.getMessage()), e);
            }
        }
        return Json.read(cached, VersionJson.class);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
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
