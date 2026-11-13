package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.Redactor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A running Minecraft process. Holds an exclusive file lock on the instance directory while the
 * game runs, so the same profile cannot be started twice (also across launcher instances).
 */
public final class GameProcess {
    public static final String LOCK_FILE = ".lumora-running.lock";
    public static final String OUTPUT_LOG = "logs/lumora-game-output.log";

    public record Exit(int code, Optional<Path> crashReport, Path outputLog) {
        public boolean crashed() {
            return code != 0;
        }
    }

    private final Process process;
    private final CompletableFuture<Exit> exit;
    private final Path gameDir;

    private GameProcess(Process process, CompletableFuture<Exit> exit, Path gameDir) {
        this.process = process;
        this.exit = exit;
        this.gameDir = gameDir;
    }

    /** The instance folder the game runs in. */
    public Path gameDir() {
        return gameDir;
    }

    public static final class AlreadyRunningException extends IOException {
        private static final long serialVersionUID = 1L;

        public AlreadyRunningException(String profile) {
            super("Das Profil '" + profile + "' läuft bereits.");
        }
    }

    public static GameProcess start(List<String> command, Path gameDir, String profileId, Consumer<String> outputListener) throws IOException {
        return start(command, gameDir, profileId, java.util.Map.of(), outputListener);
    }

    /** @param environment extra environment variables for the game process (KEY -> value) */
    public static GameProcess start(List<String> command, Path gameDir, String profileId, java.util.Map<String, String> environment,
                                    Consumer<String> outputListener) throws IOException {
        Files.createDirectories(gameDir.resolve("logs"));
        FileChannel lockChannel = FileChannel.open(gameDir.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        }
        if (lock == null) {
            lockChannel.close();
            throw new AlreadyRunningException(profileId);
        }
        Log.info("Launching: " + String.join(" ", Redactor.redactCommand(command)));
        long startedAt = System.currentTimeMillis();
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(command).directory(gameDir.toFile()).redirectErrorStream(true);
            pb.environment().putAll(environment);
            p = pb.start();
        } catch (IOException e) {
            lock.release();
            lockChannel.close();
            throw new IOException("Java-Prozess konnte nicht gestartet werden: " + e.getMessage(), e);
        }
        Path outputLog = gameDir.resolve(OUTPUT_LOG);
        FileLock heldLock = lock;
        CompletableFuture<Exit> exit = new CompletableFuture<>();
        Thread pump = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = Files.newBufferedWriter(outputLog, StandardCharsets.UTF_8)) {
                String line;
                while ((line = in.readLine()) != null) {
                    String safe = Redactor.redact(line);
                    out.write(safe);
                    out.newLine();
                    out.flush();
                    if (outputListener != null) {
                        outputListener.accept(safe);
                    }
                }
            } catch (IOException e) {
                Log.warn("Game output pump stopped: " + e.getMessage());
            }
            int code;
            try {
                code = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            try {
                heldLock.release();
                lockChannel.close();
            } catch (IOException ignored) {
                // lock is released by the OS when the channel is gone anyway
            }
            Optional<Path> crash = code != 0 ? newestCrashReport(gameDir, startedAt) : Optional.empty();
            Log.info("Minecraft exited with code " + code + crash.map(c -> ", crash report: " + c).orElse(""));
            exit.complete(new Exit(code, crash, outputLog));
        }, "game-output");
        pump.setDaemon(true);
        pump.start();
        return new GameProcess(p, exit, gameDir);
    }

    static Optional<Path> newestCrashReport(Path gameDir, long sinceMillis) {
        Path dir = gameDir.resolve("crash-reports");
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(f -> f.getFileName().toString().endsWith(".txt"))
                    .filter(f -> f.toFile().lastModified() >= sinceMillis - 1000)
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public CompletableFuture<Exit> onExit() {
        return exit;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public long pid() {
        return process.pid();
    }

    /** Asks the game to close; force-kills after the timeout. */
    public void stop(java.time.Duration grace) {
        process.destroy();
        try {
            if (!process.waitFor(grace.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
