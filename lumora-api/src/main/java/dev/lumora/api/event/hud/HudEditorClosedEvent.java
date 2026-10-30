package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;

/** The HUD editor closed; {@code saved} = changes were kept, {@code false} = discarded. */
public record HudEditorClosedEvent(boolean saved) implements LumoraEvent {
}
