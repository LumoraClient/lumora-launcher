package dev.lumora.launcher.core.integrations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * What the running Lumora game reported about itself (menu, singleplayer or the server it is on, since when), read from
 * the status file the game writes into its instance ({@code -Dlumora.statusFile}, see the core's LauncherStatus).
 * Used for the Discord Rich Presence while the game runs.
 */
public record GameStatus(State state, String server, long since, String version) {
    public enum State { MENU, SINGLEPLAYER, MULTIPLAYER }

    /** The status file of an instance (its own folder, InstanceLayout#versionDir). */
    public static Path file(Path versionDir) {
        return versionDir.resolve(".lumora").resolve("status.json");
    }

    /** The last status the game wrote, empty if there is none (yet) or it cannot be read. */
    public static Optional<GameStatus> read(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4096) {
                return Optional.empty();
            }
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            State state = switch (text(o, "state")) {
                case "menu" -> State.MENU;
                case "singleplayer" -> State.SINGLEPLAYER;
                case "multiplayer" -> State.MULTIPLAYER;
                default -> null;
            };
            if (state == null) {
                return Optional.empty();
            }
            JsonElement since = o.get("since");
            long s = since != null && since.isJsonPrimitive() && since.getAsJsonPrimitive().isNumber() ? since.getAsLong() : 0;
            return Optional.of(new GameStatus(state, cleanServer(text(o, "server")), s, text(o, "version").strip()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** "Lumora 26.3" / "auf eineip.de": the two lines Discord shows (the server only if {@code showServer}). */
    public DiscordPresence.Activity activity(String flavor, String fallbackVersion, long gameStarted, boolean showServer) {
        String v = version.isEmpty() ? fallbackVersion : version;
        String line = switch (state) {
            case MENU -> "Im Hauptmenü";
            case SINGLEPLAYER -> "Einzelspieler";
            case MULTIPLAYER -> showServer && !server.isEmpty() ? "auf " + server : "Mehrspieler";
        };
        long start = state == State.MENU || since <= 0 ? gameStarted : since;
        return new DiscordPresence.Activity(flavor + " " + v, line, start);
    }

    private static String text(JsonObject o, String key) {
        String t = DiscordPresence.text(o, key);
        return t == null ? "" : t;
    }

    /** Only a host name (and port): no spaces or control characters, at most 100 characters. */
    static String cleanServer(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT);
        if (t.length() > 100 || !t.matches("[a-z0-9._:\\-\\[\\]]*")) {
            return "";
        }
        return t;
    }
}
