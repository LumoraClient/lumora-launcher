package dev.lumora.core.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All HUD profiles plus editor preferences ({@code config/lumora/hud-layout.json}). */
public final class HudLayout {
    public static final String DEFAULT = "default";
    /** Profiles that always exist, in display order. */
    public static final List<String> BUILT_IN = List.of(DEFAULT, "pvp", "survival", "minimal");

    public String activeProfile = DEFAULT;
    public boolean grid;
    public int gridSize = 8;
    public boolean snap = true;
    public final Map<String, HudProfile> profiles = new LinkedHashMap<>();

    public HudLayout() {
        profiles.put(DEFAULT, new HudProfile());
    }

    public HudProfile active() {
        HudProfile p = profiles.get(activeProfile);
        if (p == null) {
            activeProfile = DEFAULT;
            p = profiles.computeIfAbsent(DEFAULT, k -> new HudProfile());
        }
        return p;
    }

    /** Built-in profiles first, then own ones. */
    public List<String> profileIds() {
        List<String> ids = new ArrayList<>(BUILT_IN);
        for (String id : profiles.keySet()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static boolean validProfileId(String id) {
        return id != null && id.matches("[a-z0-9_-]{1,32}");
    }
}
