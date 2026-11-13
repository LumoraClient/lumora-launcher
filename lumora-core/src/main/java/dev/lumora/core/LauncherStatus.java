package dev.lumora.core;

import com.google.gson.JsonObject;
import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.GameContext;
import dev.lumora.core.hub.ScreenshotWatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Tells the Lumora launcher what the game is doing, for its Discord Rich Presence ("Lumora 26.3 auf eineip.de"): once a
 * second it looks whether the player is in the menu, in singleplayer or on a server and, when that changed, writes it with
 * the time it started to the small file the launcher named with {@code -Dlumora.statusFile}. Only a local file next to
 * the instance; without the property (game not started by the Lumora launcher) nothing happens. Version independent.
 */
public final class LauncherStatus {
    public static final String PROPERTY = "lumora.statusFile";
    private static final int EVERY_TICKS = 20;

    private final Path file;
    private final java.util.function.Supplier<GameContext> game;
    private final String version;
    private String last;
    private int ticks;

    LauncherStatus(Path file, java.util.function.Supplier<GameContext> game, String version) {
        this.file = file;
        this.game = game;
        this.version = version;
    }

    /** The status writer for this game, or null if the launcher asked for none. */
    static LauncherStatus fromSystemProperty(java.util.function.Supplier<GameContext> game, String version) {
        String p = System.getProperty(PROPERTY);
        if (p == null || p.isBlank()) {
            return null;
        }
        try {
            return new LauncherStatus(Path.of(p.strip()), game, version);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Client thread, every tick (cheap: writes only when the state changed). */
    void tick() {
        if (++ticks < EVERY_TICKS) {
            return;
        }
        ticks = 0;
        check(System.currentTimeMillis() / 1000);
    }

    /** Looks at the game now and writes the file if the state changed (tests). */
    void check(long nowEpochSeconds) {
        GameContext g = game.get();
        String state;
        String server = "";
        if (!g.inWorld()) {
            state = "menu";
        } else {
            ConnectionInfo c = g.connection();
            if (c != null && c.multiplayer()) {
                state = "multiplayer";
                server = ScreenshotWatcher.host(c.address());
            } else {
                state = "singleplayer";
            }
        }
        String key = state + "|" + server;
        if (key.equals(last)) {
            return;
        }
        last = key;
        JsonObject o = new JsonObject();
        o.addProperty("state", state);
        o.addProperty("server", server);
        o.addProperty("since", nowEpochSeconds);
        o.addProperty("version", version);
        write(o.toString());
    }

    private void write(String json) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            last = null; // try again next second
        }
    }
}
