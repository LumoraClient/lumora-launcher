package dev.lumora.social.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Console log. Never logs message texts, server ids or anything a client sends beyond its player name. */
final class Log {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static volatile boolean quiet;

    private Log() {
    }

    static void info(String msg) {
        if (!quiet) {
            System.out.println("[" + LocalDateTime.now().format(TIME) + "] " + msg);
        }
    }

    static void warn(String msg) {
        System.err.println("[" + LocalDateTime.now().format(TIME) + "] WARN " + msg);
    }
}
