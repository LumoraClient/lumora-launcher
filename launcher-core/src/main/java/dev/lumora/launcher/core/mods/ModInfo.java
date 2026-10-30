package dev.lumora.launcher.core.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Display information of a Fabric mod jar for the launcher's mod list: name, description, authors, license,
 * links, number of bundled (jar-in-jar) modules and the mod icon. Read-only; nothing in the jar is executed.
 */
public record ModInfo(String id, String name, String version, String description, List<String> authors,
                      String license, String homepage, String sources, int bundledModules, byte[] icon,
                      String fileName, long size) {

    /** Icons larger than this are ignored (a mod list never needs more than a small PNG). */
    static final int MAX_ICON_BYTES = 512 * 1024;
    private static final int MAX_JSON_BYTES = 1024 * 1024;

    public static ModInfo read(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry json = zip.getEntry("fabric.mod.json");
            if (json == null) {
                throw new IOException(jar.getFileName() + " is not a Fabric mod (no fabric.mod.json)");
            }
            JsonObject o;
            try (InputStream in = zip.getInputStream(json)) {
                o = Json.GSON.fromJson(new String(in.readNBytes(MAX_JSON_BYTES), StandardCharsets.UTF_8), JsonObject.class);
            } catch (JsonParseException | IllegalStateException e) {
                throw new IOException(jar.getFileName() + ": invalid fabric.mod.json: " + e.getMessage(), e);
            }
            if (o == null) {
                throw new IOException(jar.getFileName() + ": empty fabric.mod.json");
            }
            String id = str(o, "id");
            String name = str(o, "name");
            JsonObject contact = o.has("contact") && o.get("contact").isJsonObject() ? o.getAsJsonObject("contact") : new JsonObject();
            int nested = o.has("jars") && o.get("jars").isJsonArray() ? o.getAsJsonArray("jars").size() : 0;
            return new ModInfo(id, name != null ? name : id, str(o, "version"), str(o, "description"),
                    authors(o.get("authors")), license(o.get("license")), str(contact, "homepage"), str(contact, "sources"),
                    nested, icon(zip, o.get("icon")), jar.getFileName().toString(), Files.size(jar));
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static List<String> authors(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement a : e.getAsJsonArray()) {
                if (a.isJsonPrimitive()) {
                    out.add(a.getAsString());
                } else if (a.isJsonObject() && a.getAsJsonObject().has("name")) {
                    out.add(a.getAsJsonObject().get("name").getAsString());
                }
            }
        }
        return List.copyOf(out);
    }

    static String license(JsonElement e) {
        if (e == null) {
            return null;
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        if (e.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement p : (JsonArray) e) {
                if (p.isJsonPrimitive()) {
                    parts.add(p.getAsString());
                }
            }
            return parts.isEmpty() ? null : String.join(", ", parts);
        }
        return null;
    }

    /** "icon" is either a path or a map size -> path; the largest size up to 128 px is preferred. */
    private static byte[] icon(ZipFile zip, JsonElement e) throws IOException {
        String path = null;
        if (e != null && e.isJsonPrimitive()) {
            path = e.getAsString();
        } else if (e != null && e.isJsonObject()) {
            TreeMap<Integer, String> bySize = new TreeMap<>();
            for (Map.Entry<String, JsonElement> s : e.getAsJsonObject().entrySet()) {
                try {
                    bySize.put(Integer.parseInt(s.getKey()), s.getValue().getAsString());
                } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException ignored) {
                    // not a size key
                }
            }
            Map.Entry<Integer, String> best = bySize.floorEntry(128);
            path = best != null ? best.getValue() : bySize.isEmpty() ? null : bySize.firstEntry().getValue();
        }
        if (path == null || path.contains("..")) {
            return null;
        }
        ZipEntry entry = zip.getEntry(path.startsWith("/") ? path.substring(1) : path);
        if (entry == null || entry.getSize() > MAX_ICON_BYTES) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] data = in.readNBytes(MAX_ICON_BYTES + 1);
            return data.length > MAX_ICON_BYTES ? null : data;
        }
    }
}
