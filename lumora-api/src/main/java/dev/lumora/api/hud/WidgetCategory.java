package dev.lumora.api.hud;

/** Standard widget categories (the editor groups by them). Other strings work too and are shown as-is. */
public final class WidgetCategory {
    public static final String GENERAL = "general";
    public static final String PLAYER = "player";
    public static final String WORLD = "world";
    public static final String SERVER = "server";
    public static final String COMBAT = "combat";
    public static final String VOICE = "voice";
    public static final String TIME = "time";

    private WidgetCategory() {
    }

    /** Translation key of a category name ({@code lumora.widget.category.<id>}). */
    public static String nameKey(String category) {
        return "lumora.widget.category." + category;
    }
}
