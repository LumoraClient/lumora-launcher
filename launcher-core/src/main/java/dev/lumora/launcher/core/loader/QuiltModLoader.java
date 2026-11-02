package dev.lumora.launcher.core.loader;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Quilt Loader from meta.quiltmc.org (v3). Like Fabric, the launch profile only lists Maven libraries (Quilt's own
 * maven and Fabric's intermediary); Quilt also loads most Fabric mods.
 */
public final class QuiltModLoader implements ModLoader {
    static final String META = "https://meta.quiltmc.org/v3";

    private final Downloader downloader;
    private final Path versionsDir;

    public QuiltModLoader(Downloader downloader, Path versionsDir) {
        this.downloader = downloader;
        this.versionsDir = versionsDir;
    }

    @Override
    public String id() {
        return "quilt";
    }

    @Override
    public String displayName() {
        return "Quilt";
    }

    @Override
    public String description() {
        return "Community-Fork von Fabric mit eigener API; lädt auch die meisten Fabric-Mods. Ab Minecraft 1.14.";
    }

    @Override
    public URI supportUrl() {
        return URI.create("https://quiltmc.org/");
    }

    @Override
    public boolean supports(String mc) {
        return !FabricModLoader.legacy(mc);
    }

    @Override
    public List<LoaderVersion> versions(String mc) throws IOException {
        if (FabricModLoader.legacy(mc)) {
            return List.of();
        }
        try {
            return FabricModLoader.parse(downloader.fetchString(URI.create(META + "/versions/loader/" + FabricModLoader.enc(mc))), "loader");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("HTTP 404")) {
                return List.of(); // Minecraft version unknown to Quilt
            }
            throw e;
        }
    }

    @Override
    public String profileId(String mc, String loaderVersion) {
        return "quilt-loader-" + loaderVersion + "-" + mc;
    }

    @Override
    public VersionJson install(Install r, ProgressListener progress) throws IOException {
        String mc = r.minecraftVersion();
        progress.stage("Lade Quilt " + r.loaderVersion() + " für Minecraft " + mc);
        String id = profileId(mc, r.loaderVersion());
        Path cached = FileUtil.resolveInside(versionsDir, id + "/" + id + ".json");
        try {
            URI uri = URI.create(META + "/versions/loader/" + FabricModLoader.enc(mc) + "/" + FabricModLoader.enc(r.loaderVersion()) + "/profile/json");
            String body = downloader.fetchString(uri);
            VersionJson parsed = Json.GSON.fromJson(body, VersionJson.class);
            if (parsed == null || parsed.mainClass == null || !mc.equals(parsed.inheritsFrom)) {
                throw new IOException("Unexpected Quilt profile for " + mc + "/" + r.loaderVersion());
            }
            FileUtil.writeAtomically(cached, body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JsonParseException e) {
            if (Files.isRegularFile(cached)) {
                Log.warn("Using cached Quilt profile (" + e.getMessage() + ")");
            } else {
                throw new IOException("Quilt Loader " + r.loaderVersion() + " für " + mc + " konnte nicht geladen werden: " + e.getMessage(), e);
            }
        }
        return Json.read(cached, VersionJson.class);
    }
}
