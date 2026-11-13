package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The game builds the tooltip of an item (inventory, containers, creative menu). Listeners can append their own lines
 * with {@link #add}; they appear below the game's lines. Posted on the client thread every time a tooltip is built,
 * which is every frame while one is shown: only look up prepared data here.
 * <p>
 * Lines are plain text; the formatting codes of Minecraft ({@code §e}, {@code §7}, ...) set colors.
 *
 * @since 1.7.0
 */
public final class ItemTooltipEvent implements LumoraEvent {
    private final String item;
    private final int count;
    private final List<String> lines = new ArrayList<>();

    /**
     * @param item  item id, e.g. {@code "minecraft:diamond"}
     * @param count size of the stack
     */
    public ItemTooltipEvent(String item, int count) {
        this.item = item == null ? "" : item;
        this.count = count;
    }

    /** Item id {@code namespace:path}, e.g. {@code "minecraft:diamond"}. */
    public String item() {
        return item;
    }

    /** Size of the stack the tooltip is for. */
    public int count() {
        return count;
    }

    /** Appends a line to the tooltip ({@code ""} for an empty line); {@code null} is ignored. */
    public void add(String line) {
        if (line != null) {
            lines.add(line);
        }
    }

    /** The lines added so far, in order. */
    public List<String> lines() {
        return List.copyOf(lines);
    }
}
