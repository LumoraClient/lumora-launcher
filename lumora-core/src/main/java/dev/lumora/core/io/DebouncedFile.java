package dev.lumora.core.io;

import dev.lumora.core.CoreLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A file that is saved some ticks after the last change instead of on every change. The content is serialised
 * on the client thread (cheap, consistent snapshot) and written on the IO thread.
 */
public final class DebouncedFile {
    /** Ticks without further change before writing (2 s). */
    public static final int DELAY_TICKS = 40;

    private final Path file;
    private final Supplier<String> serializer;
    private final Executor io;
    private final CoreLogger log;
    private Runnable afterWrite = () -> { };
    private long dirtySince = -1;

    public DebouncedFile(Path file, Supplier<String> serializer, Executor io, CoreLogger log) {
        this.file = file;
        this.serializer = serializer;
        this.io = io;
        this.log = log;
    }

    public Path file() {
        return file;
    }

    /** Runs on the IO thread after each successful write. */
    public void afterWrite(Runnable action) {
        this.afterWrite = action;
    }

    public void markDirty(long tick) {
        dirtySince = tick;
    }

    public boolean dirty() {
        return dirtySince >= 0;
    }

    /** Writes if the last change is old enough. */
    public void tick(long tick) {
        if (dirtySince >= 0 && tick - dirtySince >= DELAY_TICKS) {
            saveAsync();
        }
    }

    /** Writes soon (on the IO thread). */
    public void saveAsync() {
        dirtySince = -1;
        String content = serializer.get();
        io.execute(() -> write(content));
    }

    /** Writes now on the calling thread (shutdown). */
    public void saveNow() {
        dirtySince = -1;
        write(serializer.get());
    }

    private void write(String content) {
        try {
            AtomicFiles.write(file, content);
            afterWrite.run();
        } catch (IOException e) {
            log.warn("Could not save " + file, e);
        }
    }
}
