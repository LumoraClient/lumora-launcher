package dev.lumora.api.event;

import dev.lumora.api.Registration;

import java.util.function.Consumer;

/**
 * Synchronous event bus. Events are posted on the client thread (unless an event documents otherwise) and
 * delivered to listeners of exactly that event class, ordered by {@link EventPriority}. A listener that throws is
 * logged and skipped; the remaining listeners still run. A listener that keeps failing is removed.
 * Subscribing and unsubscribing are thread-safe and may happen while an event is being delivered (the change
 * applies to the next post).
 */
public interface EventBus {
    default <E extends LumoraEvent> Registration subscribe(Class<E> type, Consumer<? super E> listener) {
        return subscribe(type, EventPriority.NORMAL, false, listener);
    }

    default <E extends LumoraEvent> Registration subscribe(Class<E> type, EventPriority priority, Consumer<? super E> listener) {
        return subscribe(type, priority, false, listener);
    }

    /** @param receiveCancelled also deliver {@link Cancellable} events that an earlier listener cancelled */
    <E extends LumoraEvent> Registration subscribe(Class<E> type, EventPriority priority, boolean receiveCancelled,
                                                   Consumer<? super E> listener);

    /** Delivers the event to all listeners and returns it (check {@link Cancellable#isCancelled()} afterwards). */
    <E extends LumoraEvent> E post(E event);

    /** {@code true} if anyone listens for this type (lets callers skip building per-frame events). */
    boolean hasListeners(Class<? extends LumoraEvent> type);
}
