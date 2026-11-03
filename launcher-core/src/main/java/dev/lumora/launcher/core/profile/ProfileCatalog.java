package dev.lumora.launcher.core.profile;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** The version-locked list of runtime profiles and their artifacts (resource lumora/profiles.json). */
public final class ProfileCatalog {
    public static final String RESOURCE = "/lumora/profiles.json";
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_.-]{0,63}$");
    private static final Pattern FILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.+-]{0,127}\\.(jar|zip)$");

    public enum Source { MODRINTH, BUNDLED }

    public static final class Profile {
        public String id;
        public String displayName;
        public String description;
        public String minecraftVersion;
        /** Always "lumora": the Lumora Loader starts plain Minecraft plus the Lumora client (no mod loader). */
        public String loader = "lumora";

        /** Started by the Lumora Loader (every Lumora profile). */
        public boolean lumoraLoader() {
            return "lumora".equals(loader);
        }
        /**
         * Optional Mojang Java runtime component to use instead of the one in the version JSON, e.g.
         * "java-runtime-delta" (Java 21) so the Lumora mod and API (Java 21) can load on an older Minecraft version.
         */
        public String javaComponent;
        public List<String> mods = new ArrayList<>();
        public List<String> extensions = new ArrayList<>();
        /** Resource packs of this version (artifact ids, type "resourcepack"); on unless switched off. */
        public List<String> resourcePacks = new ArrayList<>();

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final class Artifact {
        public String id;
        /**
         * Logical mod shared by the per-version builds (e.g. "modmenu" for modmenu-1.21.11). Extension toggles
         * are stored by key, so one switch covers every Minecraft version. Defaults to the id.
         */
        public String key;
        public String name;
        public String version;
        public String license;
        public Source source;
        public String modrinthProjectId;
        public String fileName;
        public String url;
        public long size;
        public String sha512;
        public boolean optional;
        /** "mod" (default) or "resourcepack" (installed into resourcepacks/ and activated in options.txt). */
        public String type;
        /** Keys of mods this artifact needs (e.g. Fresh Animations needs "emf" and "etf"); installed with it. */
        public List<String> requires = new ArrayList<>();
        /** Mod that is only installed because something requires it (not a switch of its own). */
        public boolean dependencyOnly;
        /** Prefix of older, manually installed files of the same pack; their options.txt entries are replaced. */
        public String replaces;

        public boolean isResourcePack() {
            return "resourcepack".equals(type);
        }

        public Hashing.Expected expectedHash() {
            return Hashing.Expected.sha512(sha512);
        }

        public String key() {
            return key != null ? key : id;
        }
    }

    static final class Document {
        int schemaVersion;
        String defaultProfile;
        List<Profile> profiles;
        List<Artifact> artifacts;
    }

    private final String defaultProfile;
    private final Map<String, Profile> profiles;
    private final Map<String, Artifact> artifacts;

    private ProfileCatalog(Document doc) {
        this.defaultProfile = doc.defaultProfile;
        this.profiles = new LinkedHashMap<>();
        doc.profiles.forEach(p -> profiles.put(p.id, p));
        this.artifacts = new LinkedHashMap<>();
        doc.artifacts.forEach(a -> artifacts.put(a.id, a));
    }

    public static ProfileCatalog loadBundled() throws IOException {
        try (InputStream in = ProfileCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing resource " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Loads the bundled catalog and applies a user-level override (written by the updater) if one exists
     * and is valid. An invalid override is ignored, never partially applied.
     */
    public static ProfileCatalog load(Path override) throws IOException {
        ProfileCatalog bundled = loadBundled();
        if (override == null || !Files.isRegularFile(override)) {
            return bundled;
        }
        try {
            Document doc = Json.GSON.fromJson(Files.readString(override), Document.class);
            Document merged = bundled.toDocument();
            Map<String, Artifact> byId = new LinkedHashMap<>();
            merged.artifacts.forEach(a -> byId.put(a.id, a));
            if (doc != null && doc.artifacts != null) {
                for (Artifact a : doc.artifacts) {
                    if (a != null && byId.containsKey(a.id) && a.source == Source.MODRINTH) {
                        // grouping and optional flag always come from the bundled lockfile
                        Artifact base = byId.get(a.id);
                        a.key = base.key;
                        a.optional = base.optional;
                        a.type = base.type;
                        a.requires = base.requires;
                        a.dependencyOnly = base.dependencyOnly;
                        a.replaces = base.replaces;
                        byId.put(a.id, a);
                    }
                }
            }
            merged.artifacts = new ArrayList<>(byId.values());
            validate(merged);
            Log.info("Applied lockfile override " + override);
            return new ProfileCatalog(merged);
        } catch (IOException | RuntimeException e) {
            Log.warn("Ignoring invalid lockfile override " + override + ": " + e.getMessage());
            return bundled;
        }
    }

    public static ProfileCatalog parse(String json) throws IOException {
        Document doc;
        try {
            doc = Json.GSON.fromJson(json, Document.class);
        } catch (JsonParseException e) {
            throw new IOException("profiles.json is not valid JSON: " + e.getMessage(), e);
        }
        validate(doc);
        return new ProfileCatalog(doc);
    }

    static void validate(Document doc) throws IOException {
        List<String> errors = new ArrayList<>();
        if (doc == null || doc.profiles == null || doc.artifacts == null) {
            throw new IOException("profiles.json: profiles and artifacts are required");
        }
        if (doc.schemaVersion != 1) {
            errors.add("unsupported schemaVersion " + doc.schemaVersion);
        }
        Map<String, Artifact> arts = new LinkedHashMap<>();
        for (Artifact a : doc.artifacts) {
            if (a == null || a.id == null || !ID.matcher(a.id).matches()) {
                errors.add("artifact with invalid id");
                continue;
            }
            if (arts.put(a.id, a) != null) {
                errors.add("duplicate artifact " + a.id);
            }
            if (a.key != null && !ID.matcher(a.key).matches()) {
                errors.add(a.id + ": invalid key " + a.key);
            }
            if (a.source == null) {
                errors.add(a.id + ": source missing");
                continue;
            }
            if (a.fileName == null || !FILE_NAME.matcher(a.fileName).matches()) {
                errors.add(a.id + ": invalid fileName " + a.fileName);
            }
            if (a.source == Source.MODRINTH) {
                if (a.url == null || !a.url.startsWith("https://cdn.modrinth.com/")) {
                    errors.add(a.id + ": download URL must be on https://cdn.modrinth.com/");
                }
                try {
                    a.expectedHash();
                } catch (IllegalArgumentException e) {
                    errors.add(a.id + ": " + e.getMessage());
                }
                if (a.size <= 0) {
                    errors.add(a.id + ": size must be positive");
                }
            }
        }
        java.util.Set<String> profileIds = new java.util.HashSet<>();
        for (Profile p : doc.profiles) {
            if (p == null || p.id == null || !ID.matcher(p.id).matches()) {
                errors.add("profile with invalid id");
                continue;
            }
            if (!profileIds.add(p.id)) {
                errors.add("duplicate profile " + p.id);
            }
            if (p.minecraftVersion == null) {
                errors.add(p.id + ": minecraftVersion is required");
            }
            if (!"lumora".equals(p.loader)) {
                errors.add(p.id + ": loader must be lumora (mod loaders are not supported)");
            }
            if (p.javaComponent != null && !p.javaComponent.matches("(java-runtime-[a-z]+|jre-legacy)")) {
                errors.add(p.id + ": invalid javaComponent " + p.javaComponent);
            }
            for (String m : p.mods) {
                Artifact a = arts.get(m);
                if (a == null) {
                    errors.add(p.id + ": unknown mod " + m);
                } else if (a.optional) {
                    errors.add(p.id + ": optional artifact " + m + " listed as required mod");
                }
            }
            for (String e : p.extensions) {
                Artifact a = arts.get(e);
                if (a == null) {
                    errors.add(p.id + ": unknown extension " + e);
                } else if (!a.optional) {
                    errors.add(p.id + ": extension " + e + " must be marked optional");
                }
            }
            for (String r : p.resourcePacks) {
                Artifact a = arts.get(r);
                if (a == null) {
                    errors.add(p.id + ": unknown resource pack " + r);
                } else if (!a.isResourcePack() || !a.optional || !a.fileName.endsWith(".zip")) {
                    errors.add(p.id + ": " + r + " must be an optional resourcepack .zip");
                }
            }
            // what packs and mods require must be part of the profile
            java.util.Set<String> available = new java.util.HashSet<>();
            concat(p.mods, p.extensions).forEach(id -> { if (arts.containsKey(id)) available.add(arts.get(id).key()); });
            for (String id : concat(concat(p.mods, p.extensions), p.resourcePacks)) {
                Artifact a = arts.get(id);
                if (a != null && a.requires != null) {
                    for (String req : a.requires) {
                        if (!available.contains(req)) {
                            errors.add(p.id + ": " + id + " requires " + req + ", which is not in the profile");
                        }
                    }
                }
            }
            // one build per mod and profile, otherwise two jars of the same mod would end up in mods/
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (String id : concat(concat(p.mods, p.extensions), p.resourcePacks)) {
                Artifact a = arts.get(id);
                if (a != null && !keys.add(a.key())) {
                    errors.add(p.id + ": two artifacts with key " + a.key());
                }
            }
        }
        if (doc.defaultProfile == null || !profileIds.contains(doc.defaultProfile)) {
            errors.add("defaultProfile must reference a profile");
        }
        if (!errors.isEmpty()) {
            throw new IOException("Invalid profile catalog: " + String.join("; ", errors));
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private Document toDocument() {
        Document d = new Document();
        d.schemaVersion = 1;
        d.defaultProfile = defaultProfile;
        d.profiles = new ArrayList<>(profiles.values());
        d.artifacts = new ArrayList<>(artifacts.values());
        return d;
    }

    public String defaultProfile() {
        return defaultProfile;
    }

    public List<Profile> profiles() {
        return List.copyOf(profiles.values());
    }

    public Optional<Profile> profile(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public Artifact artifact(String id) {
        Artifact a = artifacts.get(id);
        if (a == null) {
            throw new IllegalArgumentException("Unknown artifact " + id);
        }
        return a;
    }

    public List<Artifact> artifacts() {
        return List.copyOf(artifacts.values());
    }

    /** Keys of all optional artifacts (the values stored in the launcher's enabledExtensions). */
    public List<String> allExtensionIds() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (Artifact a : artifacts.values()) {
            if (a.optional && !a.isResourcePack() && !a.dependencyOnly) {
                out.add(a.key());
            }
        }
        return List.copyOf(out);
    }

    /** Keys of all resource packs (the values the launcher's disabledResourcePacks may contain). */
    public List<String> allResourcePackKeys() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        artifacts.values().stream().filter(Artifact::isResourcePack).forEach(a -> out.add(a.key()));
        return List.copyOf(out);
    }

    /** The artifacts of a profile: required mods first, then its extensions. */
    public List<Artifact> profileArtifacts(Profile profile) {
        List<Artifact> out = new ArrayList<>();
        profile.mods.forEach(id -> out.add(artifact(id)));
        profile.extensions.forEach(id -> out.add(artifact(id)));
        return out;
    }

    /** The profile's resource packs that are not switched off. */
    public List<Artifact> resolvePacks(Profile profile, List<String> disabledPacks) {
        List<Artifact> out = new ArrayList<>();
        for (String id : profile.resourcePacks) {
            Artifact a = artifact(id);
            if (!disabledPacks.contains(a.key())) {
                out.add(a);
            }
        }
        return out;
    }

    /** The profile's build of the mod with this key (e.g. "sodium"), if the profile contains it. */
    public Optional<Artifact> profileArtifact(Profile profile, String key) {
        return profileArtifacts(profile).stream().filter(a -> a.key().equals(key)).findFirst();
    }

    /** Required mods followed by the enabled extensions (by key) supported by this profile. */
    public List<Artifact> resolveMods(Profile profile, List<String> enabledExtensions) {
        return resolveMods(profile, enabledExtensions, null);
    }

    /**
     * Required mods, enabled extensions and every mod that an included artifact requires (transitively, e.g.
     * Fresh Animations -> EMF -> ETF). {@code disabledPacks} == null means: resource packs are not considered.
     */
    public List<Artifact> resolveMods(Profile profile, List<String> enabledExtensions, List<String> disabledPacks) {
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        java.util.Deque<Artifact> todo = new java.util.ArrayDeque<>();
        profile.mods.forEach(id -> todo.add(artifact(id)));
        for (String id : profile.extensions) {
            Artifact a = artifact(id);
            if (enabledExtensions.contains(a.key()) && !a.dependencyOnly) {
                todo.add(a);
            }
        }
        if (disabledPacks != null) {
            todo.addAll(resolvePacks(profile, disabledPacks));
        }
        while (!todo.isEmpty()) {
            Artifact a = todo.poll();
            if (!wanted.add(a.key())) {
                continue;
            }
            if (a.requires != null) {
                a.requires.forEach(req -> profileArtifact(profile, req).ifPresent(todo::add));
            }
        }
        List<Artifact> out = new ArrayList<>();
        for (Artifact a : profileArtifacts(profile)) {
            if (wanted.contains(a.key())) {
                out.add(a);
            }
        }
        return out;
    }
}
