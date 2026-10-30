package dev.lumora.core;

import dev.lumora.api.Registration;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.Keys;
import dev.lumora.api.notify.Notification;
import dev.lumora.core.input.KeybindManager;
import dev.lumora.core.notify.NotificationCenter;
import dev.lumora.core.task.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicesTest {
    @TempDir
    Path dir;

    @Test
    void keybindsFireOncePerPressAndAvoidConflicts() throws Exception {
        FakePlatform p = new FakePlatform(dir);
        p.minecraftKeys.put(Keys.V, "Push to talk");
        LumoraCore core = new LumoraCore(p);
        KeybindManager km = core.keybindManager();
        int[] presses = {0};
        Keybind k = km.register("demo", "zoom", "demo.key.zoom", Keys.C, () -> presses[0]++);
        p.down.add(Keys.C);
        core.tick();
        core.tick();
        assertEquals(1, presses[0], "held key = one press");
        assertTrue(k.isDown());
        p.down.clear();
        core.tick();
        p.acceptKeys = false; // a menu is open
        p.down.add(Keys.C);
        core.tick();
        assertEquals(1, presses[0]);

        Keybind clash = km.register("demo", "talk", "demo.key.talk", Keys.V, () -> { });
        assertEquals(Keys.UNBOUND, clash.key(), "default already used by Minecraft -> unbound");
        assertFalse(clash.setKey(Keys.C), "used by another Lumora keybind");
        assertEquals("demo.key.zoom", clash.conflictWith(Keys.C).orElseThrow());
        assertFalse(clash.setKey(Keys.ESCAPE));
        assertTrue(clash.setKey(Keys.B));
        assertThrows(IllegalArgumentException.class, () -> km.register("demo", "zoom", "x", Keys.Z, () -> { }));
        core.stopping();
        assertTrue(Files.readString(dir.resolve("lumora/keybinds.json")).contains("\"demo:talk\":66"));

        LumoraCore again = new LumoraCore(new FakePlatform(dir));
        Keybind restored = again.keybindManager().register("demo", "talk", "demo.key.talk", Keys.V, () -> { });
        assertEquals(Keys.B, restored.key(), "the user's key is restored");
        again.stopping();
    }

    @Test
    void notificationsQueueLimitAndWrap() {
        long[] now = {0};
        NotificationCenter n = new NotificationCenter(() -> now[0]);
        for (int i = 0; i < 40; i++) {
            n.show(Notification.builder("T" + i).message("one two three four five six seven eight nine ten eleven twelve "
                    + "thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty").build());
        }
        assertEquals(32, n.queuedCount(), "flood protection");
        n.render(new FakeGraphics(), 400); // first frame: fade-in starts, text still invisible
        now[0] = 500;
        FakeGraphics g = new FakeGraphics();
        n.render(g, 400);
        assertEquals(4, n.visibleCount());
        assertEquals(16, g.texts.size(), "4 cards with title and 3 lines");
        assertTrue(g.texts.size() <= 4 * 4, "title + at most 3 lines each");
        assertTrue(g.texts.stream().anyMatch(t -> t.endsWith("…")), "long text is cut with an ellipsis");
        now[0] = 10_000;
        n.render(new FakeGraphics(), 400);
        assertEquals(4, n.visibleCount(), "expired ones are replaced by queued ones");
    }

    @Test
    void tasksRunOnTicksAndAreCancelledWithTheirOwner() throws Exception {
        FakePlatform p = new FakePlatform(dir);
        TaskService t = new TaskService(p.logger());
        RegistrationTracker tracker = new RegistrationTracker();
        var scheduler = t.scoped("demo", tracker, () -> { });
        int[] once = {0};
        int[] rep = {0};
        scheduler.delay(3, () -> once[0]++);
        Registration r = scheduler.repeat(2, () -> rep[0]++);
        for (int i = 0; i < 6; i++) {
            t.tick();
        }
        assertEquals(1, once[0]);
        assertEquals(3, rep[0]);
        assertEquals(42, scheduler.supplyAsync(() -> 42).get(5, TimeUnit.SECONDS));
        var slow = scheduler.supplyAsync(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 1;
        });
        tracker.closeAll(p.logger(), "demo");
        assertFalse(r.isActive());
        assertTrue(slow.isCancelled());
        for (int i = 0; i < 4; i++) {
            t.tick();
        }
        assertEquals(3, rep[0], "cancelled with the owner");
        int[] failing = {0};
        t.onClientThread(() -> {
            throw new IllegalStateException("x");
        });
        t.onClientThread(() -> failing[0]++);
        t.tick();
        assertEquals(1, failing[0], "a failing task does not stop the queue");
        t.shutdown();
    }
}
