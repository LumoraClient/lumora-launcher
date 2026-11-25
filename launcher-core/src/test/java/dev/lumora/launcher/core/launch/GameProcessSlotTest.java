package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.integrations.GameStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Locks of runs in one instance folder: one run without "multiple instances", further runs in own slots with it. */
class GameProcessSlotTest {
    @TempDir
    Path dir;

    @Test
    void secondRunNeedsMultipleInstances() throws Exception {
        try (GameProcess.Slot first = GameProcess.lock(dir, "26.3", false)) {
            assertEquals(1, first.number());
            assertEquals("", first.suffix());
            assertEquals(GameProcess.OUTPUT_LOG, GameProcess.outputLog(first));
            assertThrows(GameProcess.AlreadyRunningException.class, () -> GameProcess.lock(dir, "26.3", false),
                    "without multiple instances the profile runs once");

            try (GameProcess.Slot second = GameProcess.lock(dir, "26.3", true);
                 GameProcess.Slot third = GameProcess.lock(dir, "26.3", true)) {
                assertEquals(2, second.number());
                assertEquals(3, third.number());
                assertEquals("logs/lumora-game-output-2.log", GameProcess.outputLog(second), "own output log");
                assertEquals(dir.resolve(".lumora").resolve("status-2.json"), GameStatus.file(dir, second.suffix()), "own status file");
            }
            // slot 2 is free again and is taken first
            try (GameProcess.Slot again = GameProcess.lock(dir, "26.3", true)) {
                assertEquals(2, again.number());
            }
        }
        try (GameProcess.Slot afterwards = GameProcess.lock(dir, "26.3", false)) {
            assertEquals(1, afterwards.number(), "free once the first run ended");
        }
    }

    /** Stand-in for a game whose child (a loader error window) inherits the output pipe and outlives it. */
    public static final class ParentWithChild {
        public static void main(String[] args) throws Exception {
            String javaExe = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(javaExe, "-cp", System.getProperty("java.class.path"), Sleeper.class.getName()).inheritIO().start();
            Thread.sleep(1500);
            System.out.println("parent done");
        }
    }

    public static final class Sleeper {
        public static void main(String[] args) throws Exception {
            System.out.println("child " + ProcessHandle.current().pid());
            System.out.flush();
            Thread.sleep(60_000);
        }
    }

    @Test
    void gameEndsWithItsProcessEvenIfAChildKeepsTheOutputOpen() throws Exception {
        String javaExe = ProcessHandle.current().info().command().orElse("java");
        java.util.List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        GameProcess game = GameProcess.start(java.util.List.of(javaExe, "-cp", System.getProperty("java.class.path"), ParentWithChild.class.getName()),
                dir, "test", lines::add);
        try {
            GameProcess.Exit exit = game.onExit().get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0, exit.code());
            org.junit.jupiter.api.Assertions.assertTrue(lines.contains("parent done"), "output up to the end is read: " + lines);
        } finally {
            lines.stream().filter(l -> l.startsWith("child ")).findFirst()
                    .flatMap(l -> ProcessHandle.of(Long.parseLong(l.substring(6).strip())))
                    .ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void atMostMaxSlots() throws Exception {
        java.util.List<GameProcess.Slot> slots = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < GameProcess.MAX_SLOTS; i++) {
                slots.add(GameProcess.lock(dir, "1.8.9", true));
            }
            assertThrows(GameProcess.AlreadyRunningException.class, () -> GameProcess.lock(dir, "1.8.9", true));
        } finally {
            for (GameProcess.Slot s : slots) {
                s.close();
            }
        }
    }
}
