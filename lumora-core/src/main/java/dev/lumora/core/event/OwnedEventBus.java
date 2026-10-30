package dev.lumora.core.event;

import dev.lumora.api.Registration;
import dev.lumora.api.event.EventBus;
import dev.lumora.api.event.EventPriority;
import dev.lumora.api.event.LumoraEvent;
import dev.lumora.core.RegistrationTracker;

import java.util.function.Consumer;

/** The bus as an addon sees it: listeners belong to the addon and are removed when it is disabled. */
public final class OwnedEventBus implements EventBus {
    private final SimpleEventBus bus;
    private final String owner;
    private final RegistrationTracker tracker;
    private final Runnable guard;

    /** @param guard throws if the owner may not register right now */
    public OwnedEventBus(SimpleEventBus bus, String owner, RegistrationTracker tracker, Runnable guard) {
        this.bus = bus;
        this.owner = owner;
        this.tracker = tracker;
        this.guard = guard;
    }

    @Override
    public <E extends LumoraEvent> Registration subscribe(Class<E> type, EventPriority priority, boolean receiveCancelled,
                                                          Consumer<? super E> listener) {
        guard.run();
        return tracker.track(bus.subscribe(owner, type, priority, receiveCancelled, listener));
    }

    @Override
    public <E extends LumoraEvent> E post(E event) {
        return bus.post(event);
    }

    @Override
    public boolean hasListeners(Class<? extends LumoraEvent> type) {
        return bus.hasListeners(type);
    }
}
