package dev.lumora.api.chat;

import dev.lumora.api.Registration;

/**
 * The chat (permission {@code chat}): own entries in the menu of a chat message, the open chat's input field, buttons
 * and panels at the chat input and inline icons in messages (since 1.9.0).
 * New messages arrive as {@link dev.lumora.api.event.chat.ChatMessageEvent} on the event bus.
 *
 * @since 1.5.0
 */
public interface Chat {
    /** Adds an entry to the menu of chat messages; removed with the registration or when the addon is disabled. */
    Registration addMenuEntry(ChatMenuEntry entry);

    /** Opens the chat with {@code text} in the input field (not sent), e.g. {@code "/msg Steve "}. */
    void openChat(String text);

    /**
     * Adds a button at the chat input (inside the input line, at its right end); removed with the registration or when the addon
     * is disabled.
     *
     * @since 1.9.0
     */
    Registration addInputButton(ChatInputButton button);

    /**
     * Adds inline icons for {@code :name:} in chat messages; removed with the registration or when the addon is
     * disabled. Several addons may add icons, the first one that knows a name wins.
     *
     * @since 1.9.0
     */
    Registration addIcons(ChatIcons icons);

    /**
     * Inserts {@code text} into the open chat input at the cursor (nothing without an open chat), e.g. an emoji
     * shortcode picked in a {@link ChatPanel}.
     *
     * @since 1.9.0
     */
    void insertText(String text);

    /**
     * What is typed in the open chat input right now, or {@code ""} without an open chat.
     *
     * @since 1.9.0
     */
    String input();

    /**
     * Closes the open {@link ChatPanel} (any addon's); the chat stays open.
     *
     * @since 1.9.0
     */
    void closePanel();
}
