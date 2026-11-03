package dev.lumora.client.api;

import dev.lumora.api.screen.AddonView;
import dev.lumora.client.gui.AddonViewScreen;
import dev.lumora.client.mixin.LevelLoadTrackerAccessor;
import dev.lumora.client.mixin.LevelLoadingScreenAccessor;
import dev.lumora.core.connection.ConnectionModule;
import dev.lumora.core.connection.WorldLoadAttempt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.progress.LevelLoadListener;

import java.util.Set;
import java.util.function.Consumer;

/**
 * One singleplayer world being opened, for Lumora's loading screen (26.1.2). While it runs, every loading screen
 * Minecraft opens (reading the world, data packs, the progress screen, the level loading screen) becomes the screen
 * underneath Lumora's: Minecraft keeps ticking it and it still closes itself when the world is ready. Only reads
 * Minecraft's state.
 */
public final class McWorldLoad implements WorldLoadAttempt {
    /** Message keys of the screens Minecraft shows when an existing world starts opening. */
    private static final Set<String> START_KEYS = Set.of("selectWorld.data_read", "selectWorld.resource_load");
    /**
     * Shown when a new world is created, but also while the create world menu itself opens: it only starts a load when
     * it comes from that menu (or right after {@link #starting} reported a new world).
     */
    private static final String PREPARING = "createWorld.preparing";
    /** How long a reported start stays valid. */
    private static final long PENDING_MS = 10_000;

    private static String pendingName = "";
    private static boolean pendingNew;
    private static long pendingAt;
    private static McWorldLoad current;

    private final String name;
    private boolean created;
    private Screen underlying;
    private Screen wrapper;

    private McWorldLoad(Screen first, boolean created) {
        boolean pending = System.currentTimeMillis() - pendingAt < PENDING_MS;
        this.name = pending ? pendingName : "";
        this.created = created || (pending && pendingNew);
        pendingName = "";
        pendingNew = false;
        pendingAt = 0;
        this.underlying = first;
    }

    /** Called where a world starts opening (WorldOpenFlows), before its first screen. */
    public static void starting(String worldName, boolean newWorld) {
        pendingName = worldName == null ? "" : worldName;
        pendingNew = newWorld;
        pendingAt = System.currentTimeMillis();
    }

    /**
     * Minecraft sets {@code screen} while {@code shown} is the current screen. Returns true if Lumora took it: either
     * it is the next loading screen of the world being opened (it now runs underneath), or a world starts opening and
     * Lumora's loading screen was shown instead (through {@code show}).
     */
    public static boolean intercept(Screen shown, Screen screen, Consumer<Screen> show) {
        if (screen == null || !isLoadingScreen(screen)) {
            return false;
        }
        try {
            McWorldLoad running = current;
            if (running != null && running.wrapper != null && shown == running.wrapper) {
                running.underlying = screen;
                running.created |= PREPARING.equals(key(screen));
                return true;
            }
            current = null;
            if (!starts(shown, screen)) {
                return false;
            }
            McWorldLoad load = new McWorldLoad(screen, PREPARING.equals(key(screen)));
            AddonView view = ConnectionModule.worldLoading(load);
            if (view == null) {
                return false;
            }
            load.wrapper = AddonViewScreen.connecting(new TitleScreen(), view, load::tickUnderlying);
            current = load;
            show.accept(load.wrapper);
            return true;
        } catch (RuntimeException | LinkageError e) {
            current = null;
            return false;
        }
    }

    /** Exact classes: screens of other code that extend these stay as they are. */
    private static boolean isLoadingScreen(Screen s) {
        Class<?> c = s.getClass();
        return c == GenericMessageScreen.class || c == ProgressScreen.class || c == LevelLoadingScreen.class;
    }

    /** A world starts opening: its first message screen, or the level loading screen before any connection exists. */
    private static boolean starts(Screen shown, Screen s) {
        if (s.getClass() == GenericMessageScreen.class) {
            String k = key(s);
            return START_KEYS.contains(k) || (PREPARING.equals(k) && (shown instanceof CreateWorldScreen
                    || (pendingNew && System.currentTimeMillis() - pendingAt < PENDING_MS)));
        }
        Minecraft mc = Minecraft.getInstance();
        return s.getClass() == LevelLoadingScreen.class && mc.getConnection() == null && mc.level == null;
    }

    private static String key(Screen s) {
        Component title = s.getTitle();
        return title != null && title.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private void tickUnderlying() {
        Screen s = underlying;
        if (s != null) {
            s.tick(); // the level loading screen closes itself (and Lumora's) once the world is ready
        }
    }

    @Override
    public String worldName() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            try {
                String n = server.getWorldData().getLevelName();
                if (n != null && !n.isBlank()) {
                    return n;
                }
            } catch (RuntimeException ignored) {
                // world data not there yet
            }
        }
        return name;
    }

    @Override
    public boolean newWorld() {
        return created;
    }

    @Override
    public Stage stage() {
        Screen s = underlying;
        if (s instanceof LevelLoadingScreen l) {
            if (Minecraft.getInstance().level != null) {
                return Stage.TERRAIN;
            }
            LevelLoadTracker t = ((LevelLoadingScreenAccessor) l).lumora$loadTracker();
            LevelLoadListener.Stage st = t == null ? null : ((LevelLoadTrackerAccessor) t).lumora$serverStage();
            if (st == null || st == LevelLoadListener.Stage.START_SERVER) {
                return Stage.SERVER;
            }
            return st == LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS ? Stage.TERRAIN : Stage.SPAWN;
        }
        if (s instanceof ProgressScreen) {
            return Stage.SERVER; // Minecraft clears the previous world right before starting the server
        }
        return switch (s == null ? "" : key(s)) {
            case PREPARING -> Stage.CREATE;
            case "selectWorld.resource_load" -> Stage.RESOURCES;
            default -> Stage.READ;
        };
    }

    @Override
    public int progress() {
        if (underlying instanceof LevelLoadingScreen l) {
            LevelLoadTracker t = ((LevelLoadingScreenAccessor) l).lumora$loadTracker();
            if (t != null && t.hasProgress()) {
                return Math.round(Math.max(0f, Math.min(1f, t.serverProgress())) * 100);
            }
        }
        return -1;
    }
}
