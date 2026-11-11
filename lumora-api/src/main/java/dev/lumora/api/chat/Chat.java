package dev.lumora.api.chat;

import dev.lumora.api.Registration;

/**
 * The chat (permission {@code chat}): own entries in the menu of a chat message and the open chat's input field.
 * New messages arrive as {@link dev.lumora.api.event.chat.ChatMessageEvent} on the event bus.
 *
 * @since 1.5.0
 */
public interface Chat {
    /** Adds an entry to the menu of chat messages; removed with the registration or when the addon is disabled. */
    Registration addMenuEntry(ChatMenuEntry entry);

    /** Opens the chat with {@code text} in the input field (not sent), e.g. {@code "/msg Steve "}. */
    void openChat(String text);
}
