package dev.lumora.launcher.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.mods.ModVersion;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Looks for newer releases of the locked Modrinth artifacts for the same Minecraft version via the
 * official Modrinth API. Updates are never applied silently: the user confirms, the new entry (URL, size,
 * SHA-512 from Modrinth) is written to profiles.override.json and the next start installs and verifies it.
 * The dependency check still runs, so an update that breaks the combination is rejected before launch.
 */
public final class UpdateChecker {
    public record Update(ProfileCatalog.Artifact current, ProfileCatalog.Artifact candidate) {
    }

    private final Downloader downloader;

    public UpdateChecker(Downloader downloader) {
        this.downloader = downloader;
    }

    /** Checks the Modrinth artifacts of one profile against that profile's Minecraft version. */
    public List<Update> check(ProfileCatalog catalog, ProfileCatalog.Profile profile) {
        String minecraftVersion = profile.minecraftVersion;
        List<Update> out = new ArrayList<>();
        List<ProfileCatalog.Artifact> all = new ArrayList<>(catalog.profileArtifacts(profile));
        profile.resourcePacks.forEach(id -> all.add(catalog.artifact(id)));
        for (ProfileCatalog.Artifact a : all) {
            if (a.source != ProfileCatalog.Source.MODRINTH || a.modrinthProjectId == null) {
                continue;
            }
            try {
                ProfileCatalog.Artifact latest = latest(a, minecraftVersion);
                if (latest != null && isNewer(latest.version, a.version)) {
                    out.add(new Update(a, latest));
                }
            } catch (IOException e) {
                Log.warn("Update check for " + a.id + " failed: " + e.getMessage());
            }
        }
        return out;
    }

    ProfileCatalog.Artifact latest(ProfileCatalog.Artifact a, String mc) throws IOException {
        String query = "?loaders=" + enc(a.isResourcePack() ? "[\"minecraft\"]" : "[\"fabric\"]") + "&game_versions=" + enc("[\"" + mc + "\"]");
        String body = downloader.fetchString(URI.create("https://api.modrinth.com/v2/project/" + a.modrinthProjectId + "/version" + query));
        return parseLatest(body, a);
    }

    static ProfileCatalog.Artifact parseLatest(String body, ProfileCatalog.Artifact template) throws IOException {
        try {
            JsonArray versions = Json.GSON.fromJson(body, JsonArray.class);
            for (JsonElement el : versions) {
                JsonObject v = el.getAsJsonObject();
                if (!"release".equals(v.get("version_type").getAsString())) {
                    continue;
                }
                for (JsonElement fe : v.getAsJsonArray("files")) {
                    JsonObject f = fe.getAsJsonObject();
                    if (!f.get("primary").getAsBoolean()) {
                        continue;
                    }
                    ProfileCatalog.Artifact c = new ProfileCatalog.Artifact();
                    c.id = template.id;
                    c.key = template.key;
                    c.name = template.name;
                    c.license = template.license;
                    c.source = ProfileCatalog.Source.MODRINTH;
                    c.modrinthProjectId = template.modrinthProjectId;
                    c.optional = template.optional;
                    c.version = v.get("version_number").getAsString();
                    c.fileName = f.get("filename").getAsString();
                    c.type = template.type;
                    c.requires = template.requires;
                    c.dependencyOnly = template.dependencyOnly;
                    c.replaces = template.replaces;
                    if (template.isResourcePack()) {
                        // pack names on Modrinth often contain spaces/brackets; keep the lockfile's naming scheme
                        c.fileName = (template.key() + "-" + c.version).replaceAll("[^A-Za-z0-9_.+-]", "-") + ".zip";
                    }
                    c.url = f.get("url").getAsString();
                    c.size = f.get("size").getAsLong();
                    c.sha512 = f.getAsJsonObject("hashes").get("sha512").getAsString();
                    return c;
                }
            }
            return null;
        } catch (JsonParseException | IllegalStateException | NullPointerException | ClassCastException e) {
            throw new IOException("Unexpected Modrinth response: " + e.getMessage(), e);
        }
    }

    static boolean isNewer(String candidate, String current) {
        // Modrinth version numbers mix styles ("mc26.1.2-0.9.2-fabric", "0.6.8+26.1.2"); compare the
        // first semantic-looking token in each.
        ModVersion a = ModVersion.parse(semanticPart(candidate));
        ModVersion b = ModVersion.parse(semanticPart(current));
        if (a.isSemantic() && b.isSemantic()) {
            return a.compareTo(b) > 0;
        }
        return !candidate.equals(current);
    }

    static String semanticPart(String v) {
        String s = v.startsWith("mc") && v.contains("-") ? v.substring(v.indexOf('-') + 1) : v;
        for (String suffix : Set.of("-fabric", "-quilt")) {
            if (s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
            }
        }
        return s;
    }

    /**
     * Persists accepted updates into the override lockfile (validated on load). Entries of other profiles (other Minecraft versions)
     * already in the file are kept; an unreadable file is replaced.
     */
    public static void writeOverride(Path overrideFile, List<ProfileCatalog.Artifact> accepted) throws IOException {
        java.util.Map<String, JsonElement> byId = new java.util.LinkedHashMap<>();
        if (java.nio.file.Files.isRegularFile(overrideFile)) {
            try {
                JsonObject old = Json.GSON.fromJson(java.nio.file.Files.readString(overrideFile), JsonObject.class);
                if (old != null && old.has("artifacts") && old.get("artifacts").isJsonArray()) {
                    for (JsonElement e : old.getAsJsonArray("artifacts")) {
                        if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                            byId.put(e.getAsJsonObject().get("id").getAsString(), e);
                        }
                    }
                }
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
                Log.warn("Replacing unreadable lockfile override " + overrideFile + ": " + e.getMessage());
            }
        }
        for (ProfileCatalog.Artifact a : accepted) {
            byId.put(a.id, Json.GSON.toJsonTree(a));
        }
        JsonArray artifacts = new JsonArray();
        byId.values().forEach(artifacts::add);
        JsonObject doc = new JsonObject();
        doc.addProperty("schemaVersion", 1);
        doc.add("artifacts", artifacts);
        Json.write(overrideFile, doc);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
