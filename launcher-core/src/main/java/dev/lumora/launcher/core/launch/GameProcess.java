package dev.lumora.launcher.core.launch;
import dev.lumora.launcher.core.i18n.Lang;

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
 * A running Minecraft process. Holds an exclusive file lock on the instance directory while the game runs (a
 * {@link Slot}): without "multiple instances" the same profile cannot be started twice (also across launcher
 * instances); with it, every further run of the profile takes the next slot and writes its own output log.
 */
public final class GameProcess {
    public static final String LOCK_FILE = ".lumora-running.lock";
    public static final String OUTPUT_LOG = "logs/lumora-game-output.log";
    /** Runs of one profile at the same time with "multiple instances". */
    public static final int MAX_SLOTS = 8;

    /**
     * One run's lock in an instance folder: run 1 holds {@code .lumora-running.lock}, run n
     * {@code .lumora-running-n.lock}. Its {@link #suffix()} names the run's own files (output log, status file).
     */
    public static final class Slot implements java.io.Closeable {
        private final int number;
        private final FileChannel channel;
        private final FileLock lock;

        private Slot(int number, FileChannel channel, FileLock lock) {
            this.number = number;
            this.channel = channel;
            this.lock = lock;
        }

        /** 1 for the first run, 2.. for further runs of the same profile. */
        public int number() {
            return number;
        }

        /** "" for the first run, "-2", "-3", .. for further runs. */
        public String suffix() {
            return number == 1 ? "" : "-" + number;
        }

        @Override
        public void close() throws IOException {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }

    /**
     * Locks the first free slot of the instance folder.
     *
     * @param multiple "multiple instances": further runs take the next slot; else only slot 1
     * @throws AlreadyRunningException if no slot is free
     */
    public static Slot lock(Path gameDir, String profileId, boolean multiple) throws IOException {
        Files.createDirectories(gameDir);
        int max = multiple ? MAX_SLOTS : 1;
        for (int n = 1; n <= max; n++) {
            FileChannel ch = FileChannel.open(gameDir.resolve(n == 1 ? LOCK_FILE : ".lumora-running-" + n + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock l;
            try {
                l = ch.tryLock();
            } catch (OverlappingFileLockException e) {
                l = null;
            }
            if (l != null) {
                return new Slot(n, ch, l);
            }
            ch.close();
        }
        throw new AlreadyRunningException(profileId);
    }

    /** The output log of the run in {@code slot} ({@link #OUTPUT_LOG}, "-2" etc. for further runs). */
    public static String outputLog(Slot slot) {
        return OUTPUT_LOG.replace(".log", slot.suffix() + ".log");
    }

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

    private volatile Path statusFile;

    /** File in which the game reports menu / singleplayer / server (see GameStatus); null = none. */
    public Path statusFile() {
        return statusFile;
    }

    public void statusFile(Path file) {
        this.statusFile = file;
    }

    public static final class AlreadyRunningException extends IOException {
        private static final long serialVersionUID = 1L;

        public AlreadyRunningException(String profile) {
            super(Lang.t("play.error.already_running", profile));
        }
    }

    public static GameProcess start(List<String> command, Path gameDir, String profileId, Consumer<String> outputListener) throws IOException {
        return start(command, gameDir, profileId, java.util.Map.of(), outputListener);
    }

    /** @param environment extra environment variables for the game process (KEY -> value) */
    public static GameProcess start(List<String> command, Path gameDir, String profileId, java.util.Map<String, String> environment,
                                    Consumer<String> outputListener) throws IOException {
        return start(command, gameDir, lock(gameDir, profileId, false), environment, outputListener);
    }

    /**
     * Starts the game in a slot locked with {@link #lock}; the process owns the slot from now on and releases it when
     * it ends (or right away if it cannot start).
     */
    public static GameProcess start(List<String> command, Path gameDir, Slot slot, java.util.Map<String, String> environment,
                                    Consumer<String> outputListener) throws IOException {
        try {
            Files.createDirectories(gameDir.resolve("logs"));
        } catch (IOException e) {
            slot.close();
            throw e;
        }
        Log.info("Launching" + (slot.number() > 1 ? " (run " + slot.number() + ")" : "") + ": "
                + String.join(" ", Redactor.redactCommand(command)));
        long startedAt = System.currentTimeMillis();
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(command).directory(gameDir.toFile()).redirectErrorStream(true);
            pb.environment().putAll(environment);
            p = pb.start();
        } catch (IOException e) {
            slot.close();
            throw new IOException(Lang.t("play.error.java_start", e.getMessage()), e);
        }
        Path outputLog = gameDir.resolve(outputLog(slot));
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
                slot.close();
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
