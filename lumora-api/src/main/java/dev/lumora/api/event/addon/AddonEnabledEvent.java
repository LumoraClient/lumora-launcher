package dev.lumora.api.event.addon;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.event.LumoraEvent;

/** An addon finished {@code onEnable}. */
public record AddonEnabledEvent(AddonInfo addon) implements LumoraEvent {
}
