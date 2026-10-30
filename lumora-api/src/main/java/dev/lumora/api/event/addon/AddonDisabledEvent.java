package dev.lumora.api.event.addon;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.event.LumoraEvent;

/** An addon was disabled and its registrations were removed. */
public record AddonDisabledEvent(AddonInfo addon) implements LumoraEvent {
}
