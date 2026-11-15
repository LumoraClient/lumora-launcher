package dev.lumora.launcher.core.stats;
import dev.lumora.launcher.core.i18n.Lang;

import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Play time, number of starts and last start per profile (config/playstats.json). Only real game sessions are
 * recorded (not smoke tests). A broken file is ignored and replaced on the next write.
 */
public final class PlayStats {
    public static final class Entry {
        public long seconds;
        public long launches;
        /** Epoch milliseconds of the last start, 0 = never. */
        public long lastPlayed;

        public Duration playTime() {
            return Duration.ofSeconds(seconds);
        }

        public Instant lastPlayedInstant() {
            return lastPlayed > 0 ? Instant.ofEpochMilli(lastPlayed) : null;
        }
    }

    static final class Document {
        Map<String, Entry> profiles = new LinkedHashMap<>();
    }

    private final Path file;
    private Document doc;

    public PlayStats(Path file) {
        this.file = file;
    }

    private Document doc() {
        if (doc == null) {
            Document d = null;
            if (Files.isRegularFile(file)) {
                try {
                    d = Json.read(file, Document.class);
                } catch (IOException | RuntimeException e) {
                    Log.warn("Ignoring unreadable " + file + ": " + e.getMessage());
                }
            }
            if (d == null || d.profiles == null) {
                d = new Document();
            }
            doc = d;
        }
        return doc;
    }

    /** A copy of the profile's numbers (all zero if it was never played). */
    public synchronized Entry get(String profileId) {
        Entry e = doc().profiles.get(profileId);
        Entry copy = new Entry();
        if (e != null) {
            copy.seconds = e.seconds;
            copy.launches = e.launches;
            copy.lastPlayed = e.lastPlayed;
        }
        return copy;
    }

    /** Adds one finished session. Negative or absurd durations (clock changes) are clamped. */
    public synchronized void record(String profileId, Instant start, Instant end) {
        long seconds = Math.clamp(Duration.between(start, end).toSeconds(), 0, Duration.ofDays(2).toSeconds());
        Entry e = doc().profiles.computeIfAbsent(profileId, k -> new Entry());
        e.seconds += seconds;
        e.launches++;
        e.lastPlayed = start.toEpochMilli();
        try {
            Json.write(file, doc);
        } catch (IOException ex) {
            Log.warn("Could not save play statistics: " + ex.getMessage());
        }
    }

    /** "3 h 12 min", "45 min", "unter 1 min". */
    public static String format(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 1) {
            return d.isZero() ? "–" : Lang.t("time.under_minute");
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return h == 0 ? Lang.t("time.minutes", m) : Lang.t("time.hours_minutes", h, m);
    }

    /** "gerade eben", "vor 5 min", "vor 3 h", "gestern", "vor 4 Tagen", "nie". */
    public static String ago(Instant then, Instant now) {
        if (then == null) {
            return Lang.t("time.never");
        }
        long minutes = Math.max(0, Duration.between(then, now).toMinutes());
        if (minutes < 1) {
            return Lang.t("time.just_now");
        }
        if (minutes < 60) {
            return Lang.t("time.minutes_ago", minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return Lang.t("time.hours_ago", hours);
        }
        long days = hours / 24;
        return days == 1 ? Lang.t("time.yesterday") : Lang.t("time.days_ago", days);
    }
}
