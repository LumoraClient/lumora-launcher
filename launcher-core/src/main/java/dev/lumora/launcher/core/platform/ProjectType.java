package dev.lumora.launcher.core.platform;

import java.util.Locale;

/** Kind of content a project provides, and the instance folder it is installed into. */
public enum ProjectType {
    MOD("mod", "mods"),
    MODPACK("modpack", null),
    RESOURCE_PACK("resourcepack", "resourcepacks"),
    SHADER("shader", "shaderpacks"),
    DATAPACK("datapack", "datapacks");

    private final String id;
    private final String folder;

    ProjectType(String id, String folder) {
        this.id = id;
        this.folder = folder;
    }

    /** Id as Modrinth uses it ("mod", "modpack", "resourcepack", "shader", "datapack"). */
    public String id() {
        return id;
    }

    /** Folder below the game folder; null for modpacks (they become an instance). */
    public String folder() {
        return folder;
    }

    public static ProjectType of(String id) {
        if (id != null) {
            for (ProjectType t : values()) {
                if (t.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return t;
                }
            }
        }
        return null;
    }
}
