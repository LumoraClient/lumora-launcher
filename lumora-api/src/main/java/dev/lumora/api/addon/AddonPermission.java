package dev.lumora.api.addon;

/** Capabilities an addon must declare in {@code lumora-addon.json} before the API hands them out. */
public enum AddonPermission {
    /** Register HUD widgets. */
    HUD("hud"),
    /** Register Lumora keybinds. */
    KEYBINDS("keybinds"),
    /** Show notifications. */
    NOTIFICATIONS("notifications"),
    /** Delayed, repeating and background tasks. */
    TASKS("tasks"),
    /** Read player, world and connection information. */
    GAME("game"),
    /** Open own full-screen menus ({@code AddonContext.screens()}), since 1.1.0. */
    SCREENS("screens"),
    /** Other players with positions, tab list, line of sight ({@code AddonContext.world()}), since 1.1.0. */
    PLAYERS("players"),
    /** Account profile and Mojang session join ({@code AddonContext.session()}), since 1.1.0. */
    SESSION("session"),
    /** Plugin channels ({@code AddonContext.channels()}), since 1.1.0. */
    NETWORK("network"),
    /** Icons next to player names ({@code AddonContext.nameTags()}), since 1.1.0. */
    NAMETAGS("nametags");

    private final String id;

    AddonPermission(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Permission with this JSON name, or {@code null}. */
    public static AddonPermission byId(String id) {
        for (AddonPermission p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }
}
