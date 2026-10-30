package dev.lumora.api.event;

/** Order in which listeners run: {@link #HIGHEST} first. Same priority = registration order. */
public enum EventPriority {
    HIGHEST, HIGH, NORMAL, LOW, LOWEST
}
