package dev.lumora.launcher.core.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.Json;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** The parts of fabric.mod.json the dependency check needs, read from a jar including its jar-in-jar entries. */
public record FabricModMetadata(String id, String version, List<String> provides,
                                Map<String, List<String>> depends, Map<String, List<String>> breaks,
                                List<FabricModMetadata> nested, String sourceFile) {

    private static final int MAX_NESTING = 4;

    public static FabricModMetadata read(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar)) {
            FabricModMetadata m = read(in, jar.getFileName().toString(), 0);
            if (m == null) {
                throw new IOException(jar.getFileName() + " is not a Fabric mod (no fabric.mod.json)");
            }
            return m;
        }
    }

    private static FabricModMetadata read(InputStream jarStream, String name, int depth) throws IOException {
        Map<String, byte[]> nestedJars = new LinkedHashMap<>();
        byte[] modJson = null;
        try (ZipInputStream zip = new ZipInputStream(jarStream)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String n = e.getName();
                if (n.equals("fabric.mod.json")) {
                    modJson = zip.readAllBytes();
                } else if (depth < MAX_NESTING && n.startsWith("META-INF/jars/") && n.endsWith(".jar")) {
                    nestedJars.put(n, zip.readAllBytes());
                }
            }
        }
        if (modJson == null) {
            return null;
        }
        JsonObject o;
        try {
            o = Json.GSON.fromJson(new String(modJson, StandardCharsets.UTF_8), JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed fabric.mod.json in " + name + ": " + e.getMessage(), e);
        }
        if (o == null || !o.has("id")) {
            throw new IOException("fabric.mod.json without id in " + name);
        }
        List<FabricModMetadata> nested = new ArrayList<>();
        for (Map.Entry<String, byte[]> j : nestedJars.entrySet()) {
            FabricModMetadata child = read(new ByteArrayInputStream(j.getValue()), name + "!/" + j.getKey(), depth + 1);
            if (child != null) {
                nested.add(child);
            }
        }
        List<String> provides = new ArrayList<>();
        if (o.has("provides") && o.get("provides").isJsonArray()) {
            for (JsonElement p : o.getAsJsonArray("provides")) {
                provides.add(p.getAsString());
            }
        }
        return new FabricModMetadata(o.get("id").getAsString(),
                o.has("version") ? o.get("version").getAsString() : "0",
                List.copyOf(provides), relations(o, "depends"), relations(o, "breaks"), List.copyOf(nested), name);
    }

    /** Values are either a single predicate string or an array of alternatives (OR). */
    private static Map<String, List<String>> relations(JsonObject o, String key) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            List<String> alts = new ArrayList<>();
            if (e.getValue().isJsonArray()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                arr.forEach(x -> alts.add(x.getAsString()));
            } else {
                alts.add(e.getValue().getAsString());
            }
            out.put(e.getKey(), List.copyOf(alts));
        }
        return out;
    }
}
