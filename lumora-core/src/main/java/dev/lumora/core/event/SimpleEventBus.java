package dev.lumora.core.event;

import dev.lumora.api.Registration;
import dev.lumora.api.event.Cancellable;
import dev.lumora.api.event.EventBus;
import dev.lumora.api.event.EventPriority;
import dev.lumora.api.event.LumoraEvent;
import dev.lumora.core.CoreLogger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Event bus implementation. Listener lists are copy-on-write (posting never locks, changes apply to the next
 * post). Each listener has an owner (addon id) for logging; after {@value #MAX_FAILURES} consecutive exceptions a
 * listener is removed and the failure handler is told.
 */
public final class SimpleEventBus implements EventBus {
    public static final int MAX_FAILURES = 20;

    private final CoreLogger log;
    private final Map<Class<?>, CopyOnWriteArrayList<Listener>> listeners = new ConcurrentHashMap<>();
    private final AtomicLong order = new AtomicLong();
    private volatile BiConsumer<String, Throwable> failureHandler = (owner, error) -> { };

    public SimpleEventBus(CoreLogger log) {
        this.log = log;
    }

    /** Called (once) when a listener of {@code owner} is removed for failing too often. */
    public void onListenerRemoved(BiConsumer<String, Throwable> handler) {
        this.failureHandler = Objects.requireNonNull(handler);
    }

    private final class Listener implements Registration {
        final Class<?> type;
        final EventPriority priority;
        final boolean receiveCancelled;
        final Consumer<Object> consumer;
        final String owner;
        final long seq = order.incrementAndGet();
        volatile boolean active = true;
        int failures;

        Listener(Class<?> type, EventPriority priority, boolean receiveCancelled, Consumer<Object> consumer, String owner) {
            this.type = type;
            this.priority = priority;
            this.receiveCancelled = receiveCancelled;
            this.consumer = consumer;
            this.owner = owner;
        }

        @Override
        public void unregister() {
            active = false;
            CopyOnWriteArrayList<Listener> list = listeners.get(type);
            if (list != null) {
                list.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    @Override
    public <E extends LumoraEvent> Registration subscribe(Class<E> type, EventPriority priority, boolean receiveCancelled,
                                                          Consumer<? super E> listener) {
        return subscribe("lumora", type, priority, receiveCancelled, listener);
    }

    /** Subscribes on behalf of {@code owner} (used for error messages). */
    @SuppressWarnings("unchecked")
    public <E extends LumoraEvent> Registration subscribe(String owner, Class<E> type, EventPriority priority, boolean receiveCancelled,
                                                          Consumer<? super E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(listener, "listener");
        Listener l = new Listener(type, priority, receiveCancelled, (Consumer<Object>) listener, owner);
        CopyOnWriteArrayList<Listener> list = listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            int i = 0;
            while (i < list.size() && list.get(i).priority.ordinal() <= priority.ordinal()) {
                i++;
            }
            list.add(i, l);
        }
        return l;
    }

    @Override
    public <E extends LumoraEvent> E post(E event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        Cancellable cancellable = event instanceof Cancellable c ? c : null;
        for (Listener l : list) {
            if (!l.active || (cancellable != null && cancellable.isCancelled() && !l.receiveCancelled)) {
                continue;
            }
            try {
                l.consumer.accept(event);
                l.failures = 0;
            } catch (RuntimeException | LinkageError | AssertionError e) {
                l.failures++;
                if (l.failures == 1 || l.failures == MAX_FAILURES) {
                    log.warn("Listener of " + l.owner + " for " + event.getClass().getSimpleName() + " threw (" + l.failures + "x)", e);
                }
                if (l.failures >= MAX_FAILURES) {
                    l.unregister();
                    log.warn("Removed failing listener of " + l.owner + " for " + event.getClass().getSimpleName());
                    failureHandler.accept(l.owner, e);
                }
            }
        }
        return event;
    }

    @Override
    public boolean hasListeners(Class<? extends LumoraEvent> type) {
        List<Listener> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    /** Number of active listeners of an owner (for tests and diagnostics). */
    public int listenerCount(String owner) {
        int n = 0;
        for (List<Listener> list : listeners.values()) {
            for (Listener l : list) {
                if (l.active && l.owner.equals(owner)) {
                    n++;
                }
            }
        }
        return n;
    }
}
