package dev.lumora.social;

import dev.lumora.social.model.ChatMessage;

/**
 * A change of the client state, delivered on the client's worker thread (UIs hand it to their own thread).
 * {@link Kind#MESSAGE} is a new message from someone else (for notifications); {@code conv} is set for
 * CONVERSATION/MESSAGE/HISTORY, {@code text} for NOTICE/ERROR.
 */
public final class SocialEvent {
    public enum Kind { STATUS, FRIENDS, REQUESTS, GROUPS, CONVERSATION, MESSAGE, HISTORY, NOTICE, ERROR }

    public final Kind kind;
    public final String conv;
    public final ChatMessage message;
    public final String text;

    public SocialEvent(Kind kind, String conv, ChatMessage message, String text) {
        this.kind = kind;
        this.conv = conv;
        this.message = message;
        this.text = text;
    }

    static SocialEvent of(Kind kind) {
        return new SocialEvent(kind, null, null, null);
    }

    @Override
    public String toString() {
        return kind + (conv != null ? " " + conv : "");
    }
}
