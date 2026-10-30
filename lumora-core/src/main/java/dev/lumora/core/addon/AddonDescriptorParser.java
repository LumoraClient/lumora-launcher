package dev.lumora.core.addon;

import dev.lumora.core.io.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lumora.api.Version;
import dev.lumora.api.VersionRange;
import dev.lumora.api.addon.AddonDependency;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.AddonPermission;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads and validates {@code lumora-addon.json}. Every problem becomes an {@link InvalidDescriptorException}. */
public final class AddonDescriptorParser {
    public static final int SCHEMA = 1;
    private static final String ID = "[a-z][a-z0-9_-]{1,63}";
    private static final String CLASS = "([A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*";

    public static final class InvalidDescriptorException extends Exception {
        private static final long serialVersionUID = 1L;

        public InvalidDescriptorException(String message) {
            super(message);
        }
    }

    private AddonDescriptorParser() {
    }

    public static AddonDescriptor parse(String json, String source) throws InvalidDescriptorException {
        JsonElement root;
        try {
            root = Json.parse(json);
        } catch (RuntimeException e) {
            throw new InvalidDescriptorException("lumora-addon.json ist kein gültiges JSON: " + e.getMessage());
        }
        if (!(root instanceof JsonObject o)) {
            throw new InvalidDescriptorException("lumora-addon.json muss ein JSON-Objekt sein");
        }
        int schema = o.has("schema") ? integer(o, "schema") : SCHEMA;
        if (schema != SCHEMA) {
            throw new InvalidDescriptorException("Unbekannte Schema-Version " + schema + " (unterstützt: " + SCHEMA + ")");
        }
        String id = required(o, "id");
        if (!id.matches(ID)) {
            throw new InvalidDescriptorException("Ungültige Addon-ID \"" + id + "\" (erlaubt: a-z, 0-9, _ und -, 2-64 Zeichen, Beginn mit Buchstabe)");
        }
        String name = optional(o, "name", id);
        Version version = version(required(o, "version"), "version");
        String description = optional(o, "description", "");
        List<String> authors = strings(o, "authors");
        Version api = version(required(o, "api"), "api");
        List<String> minecraft = o.has("minecraft") ? strings(o, "minecraft") : List.of("*");
        if (minecraft.isEmpty()) {
            throw new InvalidDescriptorException("\"minecraft\" darf nicht leer sein (\"*\" = alle Versionen)");
        }
        String entrypoint = required(o, "entrypoint");
        if (!entrypoint.matches(CLASS)) {
            throw new InvalidDescriptorException("Ungültiger Einstiegspunkt \"" + entrypoint + "\"");
        }
        List<AddonDependency> deps = new ArrayList<>();
        dependencies(o, "depends", false, deps, id);
        dependencies(o, "optional", true, deps, id);
        Set<AddonPermission> permissions = EnumSet.noneOf(AddonPermission.class);
        for (String p : strings(o, "permissions")) {
            AddonPermission perm = AddonPermission.byId(p);
            if (perm == null) {
                throw new InvalidDescriptorException("Unbekannte Berechtigung \"" + p + "\"");
            }
            permissions.add(perm);
        }
        return new AddonDescriptor(id, name, version, description, authors, api, minecraft, entrypoint, deps, permissions, false, source);
    }

    private static void dependencies(JsonObject o, String key, boolean optional, List<AddonDependency> out, String self)
            throws InvalidDescriptorException {
        JsonElement e = o.get(key);
        if (e == null) {
            return;
        }
        if (!(e instanceof JsonObject map)) {
            throw new InvalidDescriptorException("\"" + key + "\" muss ein Objekt {\"addon-id\": \"Version\"} sein");
        }
        for (Map.Entry<String, JsonElement> d : map.entrySet()) {
            String depId = d.getKey();
            if (!depId.matches(ID)) {
                throw new InvalidDescriptorException("Ungültige Abhängigkeit \"" + depId + "\"");
            }
            if (depId.equals(self)) {
                throw new InvalidDescriptorException("Ein Addon kann nicht von sich selbst abhängen");
            }
            for (AddonDependency existing : out) {
                if (existing.id().equals(depId)) {
                    throw new InvalidDescriptorException("Abhängigkeit \"" + depId + "\" ist doppelt angegeben");
                }
            }
            if (!(d.getValue() instanceof JsonPrimitive p) || !p.isString()) {
                throw new InvalidDescriptorException("Version für \"" + depId + "\" muss ein Text sein");
            }
            try {
                out.add(new AddonDependency(depId, VersionRange.parse(p.getAsString()), optional));
            } catch (IllegalArgumentException ex) {
                throw new InvalidDescriptorException("Ungültige Versionsangabe für \"" + depId + "\": " + p.getAsString());
            }
        }
    }

    private static Version version(String v, String field) throws InvalidDescriptorException {
        try {
            return Version.parse(v);
        } catch (IllegalArgumentException e) {
            throw new InvalidDescriptorException("Ungültige Version in \"" + field + "\": " + v);
        }
    }

    private static String required(JsonObject o, String key) throws InvalidDescriptorException {
        JsonElement e = o.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isString() || p.getAsString().isBlank()) {
            throw new InvalidDescriptorException("Pflichtfeld \"" + key + "\" fehlt");
        }
        return p.getAsString().trim();
    }

    private static String optional(JsonObject o, String key, String fallback) throws InvalidDescriptorException {
        JsonElement e = o.get(key);
        if (e == null) {
            return fallback;
        }
        if (!(e instanceof JsonPrimitive p) || !p.isString()) {
            throw new InvalidDescriptorException("\"" + key + "\" muss ein Text sein");
        }
        return p.getAsString().trim();
    }

    private static int integer(JsonObject o, String key) throws InvalidDescriptorException {
        JsonElement e = o.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            throw new InvalidDescriptorException("\"" + key + "\" muss eine Zahl sein");
        }
        return p.getAsInt();
    }

    private static List<String> strings(JsonObject o, String key) throws InvalidDescriptorException {
        JsonElement e = o.get(key);
        if (e == null) {
            return List.of();
        }
        if (e instanceof JsonPrimitive p && p.isString()) {
            return List.of(p.getAsString());
        }
        if (!(e instanceof JsonArray a)) {
            throw new InvalidDescriptorException("\"" + key + "\" muss eine Liste sein");
        }
        List<String> out = new ArrayList<>();
        for (JsonElement x : a) {
            if (!(x instanceof JsonPrimitive p) || !p.isString()) {
                throw new InvalidDescriptorException("\"" + key + "\" darf nur Texte enthalten");
            }
            out.add(p.getAsString().trim());
        }
        return out;
    }
}
