package dev.lumora.api.event.social;

import dev.lumora.api.event.LumoraEvent;

/**
 * Something on Lumora Social changed: the connection, the friends (or their online state) or the party. Read the new
 * state from {@link dev.lumora.api.social.SocialInfo}. Posted on the client thread.
 *
 * @since 1.5.0
 */
public record SocialChangedEvent(Kind kind) implements LumoraEvent {
    public enum Kind { CONNECTION, FRIENDS, PARTY }
}
