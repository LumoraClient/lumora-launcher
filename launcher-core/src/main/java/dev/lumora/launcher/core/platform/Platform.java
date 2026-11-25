package dev.lumora.launcher.core.platform;

import java.util.Locale;

/** A content platform. Every provider has one; the id is stored in content manifests and favourites. */
public enum Platform {
    MODRINTH("modrinth", "Modrinth"),
    CURSEFORGE("curseforge", "CurseForge");

    private final String id;
    private final String label;

    Platform(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    /** Product name (not translated). */
    public String label() {
        return label;
    }

    public static Platform of(String id) {
        if (id != null) {
            for (Platform p : values()) {
                if (p.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return p;
                }
            }
        }
        return null;
    }
}
