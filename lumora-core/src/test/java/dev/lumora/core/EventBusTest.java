package dev.lumora.core;

import dev.lumora.api.Registration;
import dev.lumora.api.event.Cancellable;
import dev.lumora.api.event.EventPriority;
import dev.lumora.api.event.LumoraEvent;
import dev.lumora.core.event.SimpleEventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {
    record Ping(String text) implements LumoraEvent {
    }

    static final class Stop implements LumoraEvent, Cancellable {
        boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean c) {
            cancelled = c;
        }
    }

    @TempDir
    Path dir;

    @Test
    void priorityOrderAndUnregister() {
        FakePlatform p = new FakePlatform(dir);
        SimpleEventBus bus = new SimpleEventBus(p.logger());
        List<String> calls = new ArrayList<>();
        bus.subscribe(Ping.class, EventPriority.LOW, e -> calls.add("low"));
        Registration normal = bus.subscribe(Ping.class, e -> calls.add("normal"));
        bus.subscribe(Ping.class, EventPriority.HIGHEST, e -> calls.add("highest"));
        bus.subscribe(Ping.class, e -> calls.add("normal2"));
        bus.post(new Ping("x"));
        assertEquals(List.of("highest", "normal", "normal2", "low"), calls);

        calls.clear();
        normal.unregister();
        normal.unregister(); // idempotent
        assertFalse(normal.isActive());
        bus.post(new Ping("x"));
        assertEquals(List.of("highest", "normal2", "low"), calls);
    }

    @Test
    void onlyExactTypeIsDelivered() {
        SimpleEventBus bus = new SimpleEventBus(new FakePlatform(dir).logger());
        List<Object> got = new ArrayList<>();
        bus.subscribe(Ping.class, got::add);
        bus.post(new Stop());
        assertTrue(got.isEmpty());
        assertTrue(bus.hasListeners(Ping.class));
        assertFalse(bus.hasListeners(Stop.class));
    }

    @Test
    void cancelledEventsSkipLaterListenersUnlessRequested() {
        SimpleEventBus bus = new SimpleEventBus(new FakePlatform(dir).logger());
        List<String> calls = new ArrayList<>();
        bus.subscribe(Stop.class, EventPriority.HIGH, e -> e.setCancelled(true));
        bus.subscribe(Stop.class, e -> calls.add("normal"));
        bus.subscribe(Stop.class, EventPriority.LOW, true, e -> calls.add("monitor"));
        Stop s = bus.post(new Stop());
        assertTrue(s.isCancelled());
        assertEquals(List.of("monitor"), calls);
    }

    @Test
    void failingListenerIsIsolatedAndRemovedAfterRepeatedErrors() {
        FakePlatform p = new FakePlatform(dir);
        SimpleEventBus bus = new SimpleEventBus(p.logger());
        List<String> removed = new ArrayList<>();
        bus.onListenerRemoved((owner, e) -> removed.add(owner));
        int[] ok = {0};
        bus.subscribe("bad-addon", Ping.class, EventPriority.HIGHEST, false, e -> {
            throw new IllegalStateException("broken");
        });
        bus.subscribe(Ping.class, e -> ok[0]++);
        for (int i = 0; i < SimpleEventBus.MAX_FAILURES + 5; i++) {
            bus.post(new Ping("x"));
        }
        assertEquals(SimpleEventBus.MAX_FAILURES + 5, ok[0], "other listeners keep running");
        assertEquals(List.of("bad-addon"), removed);
        assertEquals(0, bus.listenerCount("bad-addon"));
        assertTrue(p.logged("Removed failing listener of bad-addon"));
    }

    @Test
    void subscribingDuringPostAppliesToNextPost() {
        SimpleEventBus bus = new SimpleEventBus(new FakePlatform(dir).logger());
        int[] late = {0};
        bus.subscribe(Ping.class, e -> bus.subscribe(Ping.class, x -> late[0]++));
        bus.post(new Ping("1"));
        assertEquals(0, late[0]);
        bus.post(new Ping("2"));
        assertEquals(1, late[0]);
    }
}
