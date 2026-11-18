package dev.lumora.api.building;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A block in the client world: registry id ({@code minecraft:oak_stairs}), its block-state properties with the current
 * value and all allowed values, whether right-clicking it does something by itself ({@code interactive}: doors, chests,
 * levers, crafting tables …) and whether it holds contents ({@code container}: a block entity such as a chest).
 *
 * @since 1.10.0
 */
public record BlockInfo(String id, Map<String, String> properties, Map<String, List<String>> values, boolean interactive,
                        boolean container) {
    /** Air (also returned outside the loaded world). */
    public static final BlockInfo AIR = new BlockInfo("minecraft:air", Map.of(), Map.of(), false, false);

    public BlockInfo {
        properties = Map.copyOf(properties);
        values = Map.copyOf(values);
    }

    public boolean isAir() {
        return id.endsWith(":air") || id.endsWith(":cave_air") || id.endsWith(":void_air");
    }

    /** {@code minecraft:oak_stairs[facing=east,half=bottom]} (properties sorted by name), the id alone without any. */
    public String state() {
        if (properties.isEmpty()) {
            return id;
        }
        StringJoiner j = new StringJoiner(",", id + "[", "]");
        properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> j.add(e.getKey() + "=" + e.getValue()));
        return j.toString();
    }
}
