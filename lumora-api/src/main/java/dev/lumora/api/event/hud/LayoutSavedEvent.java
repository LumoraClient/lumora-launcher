package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;

/** The HUD layout file was written (posted on the client thread after the background write finished). */
public record LayoutSavedEvent(String activeProfile) implements LumoraEvent {
}
