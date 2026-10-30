package dev.lumora.core.task;

import dev.lumora.api.Registration;
import dev.lumora.api.task.TaskScheduler;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Shared task infrastructure: a client-thread queue and tick timer, a worker pool of two daemon threads with a
 * bounded queue, and a single IO thread that writes Lumora's own files in order.
 */
public final class TaskService {
    /** Client-thread tasks run per tick at most (the rest waits for the next tick). */
    static final int MAX_CLIENT_TASKS_PER_TICK = 512;

    private final CoreLogger log;
    private final Queue<Runnable> clientQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Timed> pendingTimed = new ConcurrentLinkedQueue<>();
    private final List<Timed> timed = new ArrayList<>();
    private final ThreadPoolExecutor workers;
    private final ExecutorService io;
    private long tick;

    public TaskService(CoreLogger log) {
        this.log = log;
        AtomicInteger n = new AtomicInteger();
        this.workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1024), r -> {
            Thread t = new Thread(r, "Lumora-Worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.workers.allowCoreThreadTimeOut(true);
        this.io = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Lumora-IO");
            t.setDaemon(true);
            return t;
        });
        ((ThreadPoolExecutor) this.io).allowCoreThreadTimeOut(true);
    }

    private final class Timed implements Registration {
        final Runnable task;
        final int interval;
        final String owner;
        long due;
        volatile boolean active = true;

        Timed(Runnable task, int delay, int interval, String owner) {
            this.task = task;
            this.interval = interval;
            this.owner = owner;
            this.due = tick + Math.max(1, delay);
        }

        @Override
        public void unregister() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    public long currentTick() {
        return tick;
    }

    /** Executor that writes files in submission order. */
    public ExecutorService io() {
        return io;
    }

    /** Client thread, end of every tick. */
    public void tick() {
        tick++;
        Timed added;
        while ((added = pendingTimed.poll()) != null) {
            timed.add(added);
        }
        for (int i = 0; i < timed.size(); i++) {
            Timed t = timed.get(i);
            if (t.active && tick >= t.due) {
                run(t.task, t.owner);
                if (t.interval > 0) {
                    t.due = tick + t.interval;
                } else {
                    t.active = false;
                }
            }
        }
        timed.removeIf(t -> !t.active);
        for (int i = 0; i < MAX_CLIENT_TASKS_PER_TICK; i++) {
            Runnable r = clientQueue.poll();
            if (r == null) {
                break;
            }
            run(r, "task");
        }
    }

    private void run(Runnable r, String owner) {
        try {
            r.run();
        } catch (RuntimeException | LinkageError e) {
            log.warn("Task of " + owner + " failed", e);
        }
    }

    public void onClientThread(Runnable task) {
        clientQueue.add(Objects.requireNonNull(task));
    }

    public Registration schedule(Runnable task, int delayTicks, int intervalTicks, String owner) {
        Timed t = new Timed(Objects.requireNonNull(task), delayTicks, intervalTicks, owner);
        pendingTimed.add(t);
        return t;
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Future<?> f = workers.submit(() -> {
                try {
                    result.complete(task.get());
                } catch (RuntimeException | LinkageError e) {
                    result.completeExceptionally(e);
                }
            });
            result.whenComplete((v, e) -> {
                if (result.isCancelled()) {
                    f.cancel(true);
                }
            });
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /** Number of pending timed tasks (diagnostics/tests). */
    public int timedCount() {
        int n = pendingTimed.size();
        for (Timed t : timed) {
            if (t.active) {
                n++;
            }
        }
        return n;
    }

    /** View for one owner: everything it schedules is cancelled with the tracker. */
    public TaskScheduler scoped(String owner, RegistrationTracker tracker, Runnable guard) {
        return new TaskScheduler() {
            @Override
            public void onClientThread(Runnable task) {
                guard.run();
                TaskService.this.onClientThread(task);
            }

            @Override
            public Registration delay(int ticks, Runnable task) {
                guard.run();
                return tracker.track(schedule(task, ticks, 0, owner));
            }

            @Override
            public Registration repeat(int intervalTicks, Runnable task) {
                guard.run();
                if (intervalTicks < 1) {
                    throw new IllegalArgumentException("interval must be at least 1 tick");
                }
                return tracker.track(schedule(task, intervalTicks, intervalTicks, owner));
            }

            @Override
            public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
                guard.run();
                CompletableFuture<T> f = TaskService.this.supplyAsync(task);
                tracker.track(RegistrationTracker.of(() -> f.cancel(true)));
                return f;
            }
        };
    }

    /** Stops the threads; pending IO is given two seconds. */
    public void shutdown() {
        workers.shutdownNow();
        io.shutdown();
        try {
            if (!io.awaitTermination(2, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
