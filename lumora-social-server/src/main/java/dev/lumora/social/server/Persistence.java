package dev.lumora.social.server;

import dev.lumora.social.model.ChatMessage;

import java.io.IOException;
import java.util.Collection;

/**
 * Where the server keeps its data. {@link Hub} works on the in-memory {@link State} and hands it over once a second when
 * something changed; new and changed messages are passed on as they happen. Implementations: {@link PgStore}
 * (PostgreSQL, production) and {@link FileStore} (files, for development, tests and importing old data).
 */
interface Persistence extends AutoCloseable {
    State load() throws IOException;

    /** Persists the state (players, friends, requests, blocks, groups, read positions, settings). */
    void save(State s) throws IOException;

    /** A new message (persisted at the latest with the next {@link #save}). */
    void append(ChatMessage m) throws IOException;

    /**
     * A message was edited or deleted. {@code conversation} is the conversation's current in-memory history, for
     * stores that rewrite whole conversations.
     */
    void update(ChatMessage m, Collection<ChatMessage> conversation) throws IOException;

    void deleteConversation(String conv) throws IOException;

    /** Records a security-relevant action (login, block, ban, settings). Never throws. */
    default void audit(String actor, String action, String target, String detail) {
    }

    @Override
    default void close() {
    }
}
