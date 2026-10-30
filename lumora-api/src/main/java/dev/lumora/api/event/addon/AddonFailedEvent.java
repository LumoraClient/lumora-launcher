package dev.lumora.api.event.addon;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.event.LumoraEvent;

/** An addon could not be loaded or threw in a lifecycle method; it is now in state FAILED. */
public record AddonFailedEvent(AddonInfo addon, String reason) implements LumoraEvent {
}
