package dev.lumora.loader.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@code lumora.loader.json} at the root of a jar the loader starts (the Lumora client of one Minecraft version):
 * <pre>{"schema": 1, "id": "lumora", "name": "Lumora Client", "version": "0.2.2", "minecraft": "26.3",
 *  "entrypoints": ["dev.lumora.client.LumoraClient"], "mixins": ["lumora.mixins.json"]}</pre>
 */
record JarDescriptor(String id, String name, String version, String minecraft, List<String> entrypoints, List<String> mixins,
                     Path file) {
    static final String FILE = "lumora.loader.json";
    private static final String ID = "[a-z][a-z0-9_-]{1,63}";
    private static final String CLASS = "([A-Za-z_$][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*";
    private static final String CONFIG = "[A-Za-z0-9_./-]{1,128}\\.json";
    private static final int MAX_BYTES = 64 * 1024;

    static JarDescriptor read(Path jar) throws IOException {
        String json;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(FILE);
            if (e == null) {
                throw new IOException(jar.getFileName() + " has no " + FILE);
            }
            try (InputStream in = zip.getInputStream(e)) {
                byte[] b = in.readNBytes(MAX_BYTES + 1);
                if (b.length > MAX_BYTES) {
                    throw new IOException(FILE + " in " + jar.getFileName() + " is too large");
                }
                json = new String(b, StandardCharsets.UTF_8);
            }
        }
        return parse(json, jar);
    }

    @SuppressWarnings("deprecation") // instance parser: works with Gson 2.2.4 as well
    static JarDescriptor parse(String json, Path jar) throws IOException {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            throw new IOException(FILE + " is not valid JSON: " + e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new IOException(FILE + " must be an object");
        }
        JsonObject o = root.getAsJsonObject();
        int schema = o.has("schema") ? o.get("schema").getAsInt() : 1;
        if (schema != 1) {
            throw new IOException(FILE + ": unknown schema " + schema);
        }
        String id = string(o, "id", null);
        if (id == null || !id.matches(ID)) {
            throw new IOException(FILE + ": invalid id \"" + id + "\"");
        }
        List<String> entrypoints = strings(o, "entrypoints", CLASS);
        List<String> mixins = strings(o, "mixins", CONFIG);
        return new JarDescriptor(id, string(o, "name", id), string(o, "version", "0"), string(o, "minecraft", ""),
                entrypoints, mixins, jar);
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static List<String> strings(JsonObject o, String key, String pattern) throws IOException {
        JsonElement e = o.get(key);
        if (e == null) {
            return List.of();
        }
        if (!e.isJsonArray()) {
            throw new IOException(FILE + ": \"" + key + "\" must be a list");
        }
        List<String> out = new ArrayList<>();
        for (JsonElement v : (JsonArray) e) {
            String s = v.getAsString();
            if (!s.matches(pattern)) {
                throw new IOException(FILE + ": invalid entry \"" + s + "\" in \"" + key + "\"");
            }
            out.add(s);
        }
        return List.copyOf(out);
    }
}
