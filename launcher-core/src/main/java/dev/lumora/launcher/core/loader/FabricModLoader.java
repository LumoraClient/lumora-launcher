package dev.lumora.launcher.core.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.mojang.MetadataService;
import dev.lumora.launcher.core.mojang.VersionJson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabric Loader from meta.fabricmc.net; versions before 1.14 (1.8.9, 1.12.2) use Legacy Fabric's meta with the same
 * loader and its own intermediary mappings. The launch profile lists Maven libraries that the client installer
 * downloads and verifies like every other library.
 */
public final class FabricModLoader implements ModLoader {
    private final Downloader downloader;
    private final Path versionsDir;

    public FabricModLoader(Downloader downloader, Path versionsDir) {
        this.downloader = downloader;
        this.versionsDir = versionsDir;
    }

    @Override
    public String id() {
        return "fabric";
    }

    @Override
    public String displayName() {
        return "Fabric";
    }

    @Override
    public String description() {
        return "Leichter, schneller Loader mit großer Mod-Auswahl (Sodium, Iris, Lithium …). Vor 1.14 über Legacy Fabric.";
    }

    @Override
    public URI supportUrl() {
        return URI.create("https://fabricmc.net/");
    }

    @Override
    public boolean fabricMods() {
        return true;
    }

    /** Minecraft versions before 1.14 are served by Legacy Fabric. */
    public static boolean legacy(String mc) {
        if (!mc.startsWith("1.")) {
            return false;
        }
        String[] p = mc.split("\\.");
        return p.length >= 2 && p[1].matches("\\d+") && Integer.parseInt(p[1]) < 14;
    }

    @Override
    public List<LoaderVersion> versions(String mc) throws IOException {
        String base = legacy(mc) ? MetadataService.LEGACY_FABRIC_META : MetadataService.FABRIC_META;
        String body = downloader.fetchString(URI.create(base + "/versions/loader/" + enc(mc)));
        return parse(body, "loader");
    }

    /** Fabric and Quilt meta: an array of {"loader": {"version": ..., "stable": ...}}; the newest stable is recommended. */
    static List<LoaderVersion> parse(String body, String key) throws IOException {
        List<LoaderVersion> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) {
                throw new IOException("unexpected loader list");
            }
            boolean recommendedTaken = false;
            for (JsonElement e : root.getAsJsonArray()) {
                if (!e.isJsonObject() || !e.getAsJsonObject().has(key)) {
                    continue;
                }
                JsonObject l = e.getAsJsonObject().getAsJsonObject(key);
                String v = l.get("version").getAsString();
                boolean stable = l.has("stable") ? l.get("stable").getAsBoolean() : !MavenMetadata.isPreRelease(v);
                if (!v.matches("[A-Za-z0-9._+-]{1,80}")) {
                    continue;
                }
                boolean recommended = stable && !recommendedTaken;
                recommendedTaken |= recommended;
                out.add(new LoaderVersion(v, v, stable, recommended));
            }
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            throw new IOException("Malformed loader list: " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public String profileId(String mc, String loaderVersion) {
        return "fabric-loader-" + loaderVersion + "-" + mc;
    }

    @Override
    public VersionJson install(Install r, ProgressListener progress) throws IOException {
        progress.stage("Lade Fabric " + r.loaderVersion() + " für Minecraft " + r.minecraftVersion());
        return new MetadataService(downloader, versionsDir).fabricProfile(r.minecraftVersion(), r.loaderVersion(), legacy(r.minecraftVersion()));
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
