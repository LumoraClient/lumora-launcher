package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** Left the world or the server (disconnect, quit to title). */
public record WorldLeaveEvent() implements LumoraEvent {
}
