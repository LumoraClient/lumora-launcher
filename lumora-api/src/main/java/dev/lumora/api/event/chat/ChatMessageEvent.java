package dev.lumora.api.event.chat;

import dev.lumora.api.chat.ChatLine;
import dev.lumora.api.event.LumoraEvent;

/**
 * A message was added to the chat (from the server, a command or the game). Posted on the client thread before the
 * line is shown; only a copy, it cannot be changed or cancelled.
 *
 * @since 1.5.0
 */
public record ChatMessageEvent(ChatLine line) implements LumoraEvent {
}
