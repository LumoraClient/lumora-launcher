package dev.lumora.launcher.core.crash;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** Pulls the headline facts out of a Minecraft crash report (or a launcher exit record). */
public final class CrashReportParser {
    public record Parsed(Instant time, String description, String exception) {
    }

    private static final List<DateTimeFormatter> TIMES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT),
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.ROOT));

    private CrashReportParser() {
    }

    public static Parsed parse(String text) {
        Instant time = null;
        String description = null;
        String exception = null;
        List<String> lines = text.lines().limit(4000).toList();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).strip();
            if (time == null && l.startsWith("Time: ")) {
                time = time(l.substring(6).strip());
            } else if (description == null && l.startsWith("Description: ")) {
                description = l.substring(13).strip();
                // Minecraft puts the exception right after a blank line following the description
                for (int j = i + 1; j < Math.min(lines.size(), i + 6); j++) {
                    String c = lines.get(j).strip();
                    if (!c.isEmpty() && looksLikeException(c)) {
                        exception = c;
                        break;
                    }
                }
            }
        }
        if (exception == null) {
            // launcher records / plain logs: last "Caused by" or first exception-looking line wins
            for (String raw : lines) {
                String l = raw.strip();
                int idx = l.indexOf("Caused by: ");
                if (idx >= 0) {
                    exception = l.substring(idx + 11);
                } else if (exception == null && looksLikeException(l) && !l.startsWith("at ")) {
                    exception = l;
                }
            }
        }
        if (exception != null && exception.length() > 300) {
            exception = exception.substring(0, 297) + "…";
        }
        return new Parsed(time, description, exception);
    }

    static boolean looksLikeException(String l) {
        String head = l.contains(":") ? l.substring(0, l.indexOf(':')) : l;
        // "java.lang.IllegalStateException", "org.spongepowered...MixinApplyError", "...ModResolutionException"
        return head.matches("(?:[a-z][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error|Throwable)") || l.startsWith("Exception in thread");
    }

    static Instant time(String s) {
        for (DateTimeFormatter f : TIMES) {
            try {
                return LocalDateTime.parse(s, f).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
                // next format
            }
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
