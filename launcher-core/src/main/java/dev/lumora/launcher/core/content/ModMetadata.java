package dev.lumora.launcher.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a mod jar says about itself, for every loader: {@code fabric.mod.json}, {@code quilt.mod.json},
 * {@code META-INF/mods.toml} (Forge), {@code META-INF/neoforge.mods.toml} (NeoForge) and the old {@code mcmod.info}.
 * Read-only; nothing in the jar is executed. A jar may declare several loaders (multi-loader jars).
 *
 * @param ids      mod ids the jar provides (first = its main id; includes "provides" aliases)
 * @param loaders  loaders the jar has metadata for ("fabric", "quilt", "forge", "neoforge")
 * @param depends  required mod ids with their version range as written (dependency check)
 * @param breaks   mod ids the jar declares incompatible
 * @param icon     PNG bytes of the mod icon (at most 256 KB), or null
 */
public record ModMetadata(List<String> ids, String name, String version, String description, List<String> authors,
                          Set<String> loaders, Map<String, String> depends, Set<String> breaks, byte[] icon) {
    private static final int MAX_JSON = 512 * 1024;
    private static final int MAX_ICON = 256 * 1024;
    /** Dependency ids that are the game, the loader or Java, not mods. */
    public static final Set<String> PLATFORM_IDS = Set.of("minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader",
            "quilt_base", "forge", "neoforge", "fml", "javafml", "mixinextras", "mixin");

    public String mainId() {
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Reads a jar; returns null if it carries no known metadata (not a mod, or a library without metadata). */
    public static ModMetadata read(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Builder b = new Builder();
            String fabric = text(zip, "fabric.mod.json");
            if (fabric != null) {
                b.fabric(fabric);
            }
            String quilt = text(zip, "quilt.mod.json");
            if (quilt != null) {
                b.quilt(quilt);
            }
            String neo = text(zip, "META-INF/neoforge.mods.toml");
            if (neo != null) {
                b.toml(neo, "neoforge", implementationVersion(zip));
            }
            String forge = text(zip, "META-INF/mods.toml");
            if (forge != null) {
                b.toml(forge, "forge", implementationVersion(zip));
            }
            String mcmod = text(zip, "mcmod.info");
            if (mcmod != null && b.loaders.isEmpty()) {
                b.mcmod(mcmod);
            }
            if (b.loaders.isEmpty()) {
                return null;
            }
            nested(zip, b);
            byte[] icon = null;
            if (b.iconPath != null) {
                ZipEntry e = zip.getEntry(b.iconPath.startsWith("/") ? b.iconPath.substring(1) : b.iconPath);
                if (e != null && e.getSize() <= MAX_ICON) {
                    try (InputStream in = zip.getInputStream(e)) {
                        byte[] data = in.readNBytes(MAX_ICON + 1);
                        icon = data.length <= MAX_ICON ? data : null;
                    }
                }
            }
            return new ModMetadata(List.copyOf(b.ids), b.name, b.version, b.description, List.copyOf(b.authors), Set.copyOf(b.loaders),
                    Map.copyOf(b.depends), Set.copyOf(b.breaks), icon);
        } catch (java.util.zip.ZipException e) {
            throw new IOException("not a valid jar: " + jar.getFileName(), e);
        }
    }

    /**
     * Ids of jars bundled inside the mod (jar-in-jar): they count as provided by this jar, so a mod that depends on a
     * library another mod bundles is not reported as missing. One level deep, at most 64 jars of 16 MB each.
     */
    private static void nested(ZipFile zip, Builder b) {
        List<String> paths = new ArrayList<>(b.nestedJars);
        try {
            String jarjar = text(zip, "META-INF/jarjar/metadata.json");
            if (jarjar != null) {
                for (JsonElement j : JsonParser.parseString(jarjar).getAsJsonObject().getAsJsonArray("jars")) {
                    String p = j.isJsonObject() ? str(j.getAsJsonObject(), "path") : null;
                    if (p != null) {
                        paths.add(p);
                    }
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException | ClassCastException e) {
            // no bundled jars listed
        }
        int n = 0;
        for (String p : paths) {
            if (++n > 64) {
                break;
            }
            ZipEntry e = zip.getEntry(p.startsWith("/") ? p.substring(1) : p);
            if (e == null || e.getSize() > 16L * 1024 * 1024) {
                continue;
            }
            try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(zip.getInputStream(e))) {
                java.util.zip.ZipEntry inner;
                while ((inner = in.getNextEntry()) != null) {
                    String name = inner.getName();
                    if (!name.equals("fabric.mod.json") && !name.equals("quilt.mod.json") && !name.equals("META-INF/mods.toml")
                            && !name.equals("META-INF/neoforge.mods.toml")) {
                        continue;
                    }
                    byte[] data = in.readNBytes(MAX_JSON + 1);
                    if (data.length > MAX_JSON) {
                        continue;
                    }
                    Builder sub = new Builder();
                    String s = new String(data, StandardCharsets.UTF_8);
                    switch (name) {
                        case "fabric.mod.json" -> sub.fabric(s);
                        case "quilt.mod.json" -> sub.quilt(s);
                        default -> sub.toml(s, "forge", null);
                    }
                    sub.ids.forEach(b::id);
                }
            } catch (IOException | RuntimeException ex) {
                // an unreadable bundled jar is skipped
            }
        }
    }

    private static String text(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(e)) {
            byte[] data = in.readNBytes(MAX_JSON + 1);
            if (data.length > MAX_JSON) {
                throw new IOException(name + " too large");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static String implementationVersion(ZipFile zip) {
        try {
            ZipEntry e = zip.getEntry("META-INF/MANIFEST.MF");
            if (e == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(e)) {
                return new Manifest(in).getMainAttributes().getValue("Implementation-Version");
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static final class Builder {
        final List<String> ids = new ArrayList<>();
        final Set<String> loaders = new LinkedHashSet<>();
        final Map<String, String> depends = new LinkedHashMap<>();
        final Set<String> breaks = new LinkedHashSet<>();
        final List<String> authors = new ArrayList<>();
        /** Paths of bundled jars (Fabric "jars", Forge/NeoForge jarjar). */
        final List<String> nestedJars = new ArrayList<>();
        String name;
        String version;
        String description;
        String iconPath;

        void id(String id) {
            if (id != null && !id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }

        void fabric(String json) {
            try {
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                loaders.add("fabric");
                id(str(o, "id"));
                for (String p : strings(o.get("provides"))) {
                    id(p);
                }
                name = first(name, str(o, "name"));
                version = first(version, str(o, "version"));
                description = first(description, str(o, "description"));
                authors(o.get("authors"));
                iconPath = first(iconPath, icon(o.get("icon")));
                ranges(o.get("depends"), depends);
                if (o.has("breaks") && o.get("breaks").isJsonObject()) {
                    breaks.addAll(o.getAsJsonObject("breaks").keySet());
                }
                for (JsonElement j : array(o.get("jars"))) {
                    if (j.isJsonObject() && str(j.getAsJsonObject(), "file") != null) {
                        nestedJars.add(str(j.getAsJsonObject(), "file"));
                    }
                }
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
                // broken metadata: the jar is still listed, just without details
            }
        }

        void quilt(String json) {
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonObject o = root.getAsJsonObject("quilt_loader");
                if (o == null) {
                    return;
                }
                loaders.add("quilt");
                id(str(o, "id"));
                version = first(version, str(o, "version"));
                for (JsonElement p : array(o.get("provides"))) {
                    id(p.isJsonObject() ? str(p.getAsJsonObject(), "id") : p.getAsString());
                }
                JsonObject meta = o.getAsJsonObject("metadata");
                if (meta != null) {
                    name = first(name, str(meta, "name"));
                    description = first(description, str(meta, "description"));
                    if (meta.has("contributors") && meta.get("contributors").isJsonObject()) {
                        authors.addAll(meta.getAsJsonObject("contributors").keySet());
                    }
                    iconPath = first(iconPath, icon(meta.get("icon")));
                }
                for (JsonElement d : array(o.get("depends"))) {
                    if (d.isJsonPrimitive()) {
                        depends.putIfAbsent(d.getAsString(), "*");
                    } else if (d.isJsonObject() && !(d.getAsJsonObject().has("optional") && d.getAsJsonObject().get("optional").getAsBoolean())) {
                        String id = str(d.getAsJsonObject(), "id");
                        if (id != null) {
                            depends.putIfAbsent(id, String.valueOf(d.getAsJsonObject().get("versions")));
                        }
                    }
                }
                for (JsonElement d : array(o.get("breaks"))) {
                    String id = d.isJsonObject() ? str(d.getAsJsonObject(), "id") : d.getAsString();
                    if (id != null) {
                        breaks.add(id);
                    }
                }
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | ClassCastException e) {
                // broken metadata
            }
        }

        /**
         * The parts of a Forge/NeoForge mods.toml the launcher needs, without a TOML library: the first [[mods]] table
         * (modId, version, displayName, description, authors, logoFile) and [[dependencies.*]] tables.
         */
        void toml(String toml, String loader, String jarVersion) {
            loaders.add(loader);
            String section = "";
            Map<String, String> table = new LinkedHashMap<>();
            String depOwner = null;
            List<Map<String, String>> mods = new ArrayList<>();
            List<Map<String, String>> deps = new ArrayList<>();
            String[] lines = toml.replace("\r", "").split("\n");
            String logo = null;
            String authorsTop = null;
            for (int i = 0; i < lines.length; i++) {
                String line = stripComment(lines[i]).strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[")) {
                    table = new LinkedHashMap<>();
                    if (line.equals("[[mods]]")) {
                        section = "mods";
                        mods.add(table);
                    } else if (line.startsWith("[[dependencies.")) {
                        section = "dep";
                        depOwner = line.substring("[[dependencies.".length(), line.length() - 2).replace("\"", "");
                        table.put("@owner", depOwner);
                        deps.add(table);
                    } else {
                        section = "other";
                    }
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                if (value.startsWith("'''") || value.startsWith("\"\"\"")) {
                    String quote = value.substring(0, 3);
                    StringBuilder sb = new StringBuilder(value.substring(3));
                    while (sb.indexOf(quote) < 0 && i + 1 < lines.length) {
                        sb.append('\n').append(lines[++i]);
                    }
                    int end = sb.indexOf(quote);
                    value = end >= 0 ? sb.substring(0, end) : sb.toString();
                } else {
                    value = unquote(value);
                }
                switch (section) {
                    case "mods", "dep" -> table.put(key, value);
                    case "" -> {
                        if (key.equals("logoFile")) {
                            logo = value;
                        } else if (key.equals("authors")) {
                            authorsTop = value;
                        }
                    }
                    default -> {
                    }
                }
            }
            String mainId = null;
            for (Map<String, String> m : mods) {
                id(m.get("modId"));
                if (mainId == null) {
                    mainId = m.get("modId");
                    name = first(name, m.get("displayName"));
                    String v = m.get("version");
                    if (v != null && v.contains("${file.jarVersion}")) {
                        v = jarVersion;
                    }
                    version = first(version, v);
                    description = first(description, m.get("description") == null ? null : m.get("description").strip());
                    String a = m.getOrDefault("authors", authorsTop);
                    if (a != null && authors.isEmpty()) {
                        for (String part : a.split(",")) {
                            if (!part.isBlank()) {
                                authors.add(part.strip());
                            }
                        }
                    }
                    iconPath = first(iconPath, m.getOrDefault("logoFile", logo));
                }
            }
            for (Map<String, String> d : deps) {
                String id = d.get("modId");
                if (id == null || ids.contains(id)) {
                    continue;
                }
                String type = d.get("type");
                boolean required = type != null ? type.equalsIgnoreCase("required") : "true".equals(d.get("mandatory"));
                boolean incompatible = type != null && type.equalsIgnoreCase("incompatible");
                if (incompatible) {
                    breaks.add(id);
                } else if (required && !ids.contains(id)) {
                    depends.putIfAbsent(id, d.getOrDefault("versionRange", "*"));
                }
            }
        }

        void mcmod(String json) {
            try {
                JsonElement root = JsonParser.parseString(json);
                JsonArray list = root.isJsonArray() ? root.getAsJsonArray()
                        : root.getAsJsonObject().getAsJsonArray("modList");
                if (list == null || list.isEmpty()) {
                    return;
                }
                loaders.add("forge");
                JsonObject o = list.get(0).getAsJsonObject();
                id(str(o, "modid"));
                name = first(name, str(o, "name"));
                version = first(version, str(o, "version"));
                description = first(description, str(o, "description"));
                authors.addAll(strings(o.get("authorList")));
                iconPath = first(iconPath, str(o, "logoFile"));
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | ClassCastException e) {
                // broken metadata
            }
        }

        private void authors(JsonElement e) {
            for (JsonElement a : array(e)) {
                String n = a.isJsonObject() ? str(a.getAsJsonObject(), "name") : a.isJsonPrimitive() ? a.getAsString() : null;
                if (n != null && !authors.contains(n)) {
                    authors.add(n);
                }
            }
        }

        private static void ranges(JsonElement e, Map<String, String> into) {
            if (e == null || !e.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> d : e.getAsJsonObject().entrySet()) {
                into.putIfAbsent(d.getKey(), d.getValue().isJsonPrimitive() ? d.getValue().getAsString() : d.getValue().toString());
            }
        }

        private static String icon(JsonElement e) {
            if (e == null) {
                return null;
            }
            if (e.isJsonPrimitive()) {
                return e.getAsString();
            }
            if (e.isJsonObject()) {
                String best = null;
                int bestSize = -1;
                for (Map.Entry<String, JsonElement> s : e.getAsJsonObject().entrySet()) {
                    try {
                        int size = Integer.parseInt(s.getKey());
                        if (size <= 128 && size > bestSize) {
                            bestSize = size;
                            best = s.getValue().getAsString();
                        }
                    } catch (NumberFormatException ignored) {
                        // not a size key
                    }
                }
                return best;
            }
            return null;
        }
    }

    private static String stripComment(String line) {
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                if (c == quote) {
                    inString = false;
                }
            } else if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
            } else if (c == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String first(String current, String candidate) {
        return current != null && !current.isBlank() ? current : candidate;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static JsonArray array(JsonElement e) {
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        for (JsonElement x : array(e)) {
            if (x.isJsonPrimitive()) {
                out.add(x.getAsString());
            }
        }
        return out;
    }
}
