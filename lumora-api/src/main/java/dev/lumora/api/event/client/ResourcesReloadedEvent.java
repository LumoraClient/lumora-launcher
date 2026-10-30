package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** Resource packs were reloaded (pack change, F3+T). Re-read anything cached from resources. */
public record ResourcesReloadedEvent() implements LumoraEvent {
}
