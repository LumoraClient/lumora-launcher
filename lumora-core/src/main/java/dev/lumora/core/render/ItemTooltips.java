package dev.lumora.core.render;

import dev.lumora.api.event.client.ItemTooltipEvent;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.event.SimpleEventBus;

import java.util.List;

/**
 * Tooltip lines of addons (Lumora API 1.7 {@link ItemTooltipEvent}). The version adapter's item tooltip mixin asks
 * {@link #extra} every time the game builds a tooltip and appends the result.
 */
public final class ItemTooltips {
    private static volatile ItemTooltips current;
    private final SimpleEventBus events;
    private final CoreLogger log;
    private boolean failed;

    public ItemTooltips(SimpleEventBus events, CoreLogger log) {
        this.events = events;
        this.log = log;
        current = this;
    }

    /** Whether an addon listens (lets the mixins skip all work otherwise). */
    public static boolean active() {
        ItemTooltips t = current;
        return t != null && t.events.hasListeners(ItemTooltipEvent.class);
    }

    /** The lines addons add to the tooltip of {@code count} x {@code item}; empty without listeners. Client thread only. */
    public static List<String> extra(String item, int count) {
        ItemTooltips t = current;
        if (t == null || item == null || item.isEmpty() || !t.events.hasListeners(ItemTooltipEvent.class)) {
            return List.of();
        }
        try {
            return t.events.post(new ItemTooltipEvent(item, count)).lines();
        } catch (RuntimeException e) {
            if (!t.failed) {
                t.failed = true;
                t.log.warn("Item tooltip event failed: " + e);
            }
            return List.of();
        }
    }
}
