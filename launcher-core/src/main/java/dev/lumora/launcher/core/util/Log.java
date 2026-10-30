package dev.lumora.launcher.core.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal launcher log. Every line passes through {@link Redactor} before it reaches the file,
 * stdout or any UI listener, so tokens can never be written by accident.
 */
public final class Log {
    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static BufferedWriter writer;
    private static Path file;

    private Log() {
    }

    /** Opens logs/launcher.log, rotating the previous run to launcher.1.log (keeps 3). */
    public static synchronized void init(Path logDir) {
        try {
            Files.createDirectories(logDir);
            Path current = logDir.resolve("launcher.log");
            for (int i = 2; i >= 1; i--) {
                Path older = logDir.resolve("launcher." + i + ".log");
                Path newer = i == 1 ? current : logDir.resolve("launcher." + (i - 1) + ".log");
                if (Files.exists(newer)) {
                    FileUtil.moveReplacing(newer, older);
                }
            }
            close();
            writer = Files.newBufferedWriter(current, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            file = current;
        } catch (IOException e) {
            System.err.println("Could not open launcher log in " + logDir + ": " + e);
        }
    }

    public static synchronized Path file() {
        return file;
    }

    public static synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // nothing sensible to do while closing a log
            }
            writer = null;
        }
    }

    public static void addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
    }

    public static void debug(String msg) {
        log(Level.DEBUG, msg, null);
    }

    public static void info(String msg) {
        log(Level.INFO, msg, null);
    }

    public static void warn(String msg) {
        log(Level.WARN, msg, null);
    }

    public static void warn(String msg, Throwable t) {
        log(Level.WARN, msg, t);
    }

    public static void error(String msg, Throwable t) {
        log(Level.ERROR, msg, t);
    }

    public static void log(Level level, String msg, Throwable t) {
        StringBuilder sb = new StringBuilder()
                .append('[').append(LocalDateTime.now().format(TS)).append("] [")
                .append(level).append("] [").append(Thread.currentThread().getName()).append("] ")
                .append(msg);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(System.lineSeparator()).append(sw);
        }
        String line = Redactor.redact(sb.toString());
        synchronized (Log.class) {
            if (writer != null) {
                try {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                } catch (IOException ignored) {
                    // the log itself failed; avoid recursive logging
                }
            }
        }
        if (level != Level.DEBUG) {
            (level == Level.ERROR || level == Level.WARN ? System.err : System.out).println(line);
            for (Consumer<String> l : LISTENERS) {
                l.accept(line);
            }
        }
    }
}
