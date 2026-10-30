package dev.lumora.api.task;

import dev.lumora.api.Registration;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Timed work on the client thread and background work on Lumora's shared worker pool (2 threads). Tasks of an
 * addon are cancelled when it is disabled. Never block the client thread with file or network access - use
 * {@link #supplyAsync} and continue with {@link #onClientThread}.
 */
public interface TaskScheduler {
    /** Runs on the client thread at the end of the current/next tick. */
    void onClientThread(Runnable task);

    /** Runs once on the client thread after {@code ticks} ticks (20 = 1 s). */
    Registration delay(int ticks, Runnable task);

    /** Runs on the client thread every {@code intervalTicks} ticks until unregistered. */
    Registration repeat(int intervalTicks, Runnable task);

    /** Background work; the future completes on the worker thread. Cancelled (interrupted) when the owner is disabled. */
    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);

    default CompletableFuture<Void> runAsync(Runnable task) {
        return supplyAsync(() -> {
            task.run();
            return null;
        });
    }
}
