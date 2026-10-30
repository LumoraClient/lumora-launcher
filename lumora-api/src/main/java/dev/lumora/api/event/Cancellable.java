package dev.lumora.api.event;

/**
 * An event whose default action can be prevented. Later listeners do not see a cancelled event unless they
 * subscribed with {@code receiveCancelled = true}.
 */
public interface Cancellable {
    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
