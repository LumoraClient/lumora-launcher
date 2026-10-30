package dev.lumora.launcher.core.mojang;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gson model of the launcher version JSON format used by Mojang (piston-meta) and by Fabric's
 * profile endpoint (meta.fabricmc.net/v2/versions/loader/&lt;mc&gt;/&lt;loader&gt;/profile/json).
 */
public final class VersionJson {
    public String id;
    public String inheritsFrom;
    public String type;
    public String mainClass;
    public Arguments arguments;
    /** Legacy (pre-1.13) game argument string, e.g. 1.8.9 and 1.12.2. */
    public String minecraftArguments;
    public List<Library> libraries = new ArrayList<>();
    public AssetIndex assetIndex;
    public String assets;
    public Map<String, Download> downloads;
    public JavaVersion javaVersion;
    public Logging logging;

    public static final class Arguments {
        public List<JsonElement> game = new ArrayList<>();
        public List<JsonElement> jvm = new ArrayList<>();
    }

    public static final class Library {
        public String name;
        public LibraryDownloads downloads;
        /** Maven repository base URL (Fabric style libraries). */
        public String url;
        public String sha1;
        public Long size;
        public List<Rule> rules;
        /** Pre-1.19 native libraries: OS name to classifier (may contain ${arch}); the jar is extracted before start. */
        public Map<String, String> natives;
        public Extract extract;
    }

    public static final class Extract {
        public List<String> exclude = new ArrayList<>();
    }

    public static final class LibraryDownloads {
        public Download artifact;
        public Map<String, Download> classifiers;
    }

    public static final class Download {
        public String path;
        public String sha1;
        public long size;
        public String url;
        public String id;
    }

    public static final class AssetIndex {
        public String id;
        public String sha1;
        public long size;
        public long totalSize;
        public String url;
    }

    public static final class JavaVersion {
        public String component;
        public int majorVersion;
    }

    public static final class Logging {
        public LoggingEntry client;
    }

    public static final class LoggingEntry {
        public String argument;
        public Download file;
        public String type;
    }

    public static final class Rule {
        public String action;
        public Os os;
        public Map<String, Boolean> features;
    }

    public static final class Os {
        public String name;
        public String arch;
        public String version;
    }
}
