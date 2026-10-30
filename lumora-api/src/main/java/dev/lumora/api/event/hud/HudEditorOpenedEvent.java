package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;

/** The HUD editor opened with the given layout profile. */
public record HudEditorOpenedEvent(String profile) implements LumoraEvent {
}
