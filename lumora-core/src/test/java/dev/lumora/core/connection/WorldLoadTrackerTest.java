package dev.lumora.core.connection;

import dev.lumora.core.connection.WorldLoadAttempt.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldLoadTrackerTest {
    @Test
    void stagesOnlyMoveForwardAndAreTimed() {
        AtomicLong now = new AtomicLong(1000);
        WorldLoadTracker t = new WorldLoadTracker(now::get);
        assertEquals(Stage.READ, t.update(Stage.READ));
        now.set(1200);
        t.update(Stage.SERVER); // data packs skipped
        now.set(1300);
        // Minecraft shows an earlier screen for a moment: the stage stays
        assertEquals(Stage.SERVER, t.update(Stage.READ));
        now.set(2000);
        t.update(Stage.SPAWN);
        now.set(2500);
        assertEquals(200, t.millis(Stage.READ));
        assertEquals(-1, t.millis(Stage.RESOURCES));
        assertFalse(t.reached(Stage.RESOURCES));
        assertEquals(800, t.millis(Stage.SERVER));
        assertEquals(500, t.millis(Stage.SPAWN), "running stage: time so far");
        t.finish();
        now.set(9000);
        assertEquals(1500, t.elapsedMillis(), "frozen after finish");
        assertEquals(Stage.SPAWN, t.update(Stage.TERRAIN), "no change after finish");
        assertEquals("read 200 ms, server 800 ms, spawn 500 ms, total 1500 ms", t.summary());
    }

    @Test
    void overallProgressCoversAllStepsOfTheWorld() {
        List<Stage> existing = List.of(Stage.READ, Stage.RESOURCES, Stage.SERVER, Stage.SPAWN, Stage.TERRAIN);
        assertEquals(0f, WorldLoadingView.overall(existing, null, -1));
        assertEquals(0f, WorldLoadingView.overall(existing, Stage.READ, -1));
        assertEquals(0.7f, WorldLoadingView.overall(existing, Stage.SPAWN, 50), 1e-4);
        assertEquals(1f, WorldLoadingView.overall(existing, Stage.TERRAIN, 100), 1e-4);
        List<Stage> created = List.of(Stage.CREATE, Stage.SERVER, Stage.SPAWN, Stage.TERRAIN);
        // data packs are not a step of a new world: placed after "create"
        assertEquals(0.25f, WorldLoadingView.overall(created, Stage.RESOURCES, -1), 1e-4);
        assertTrue(WorldLoadingView.overall(created, Stage.TERRAIN, 250) <= 1f);
    }
}
