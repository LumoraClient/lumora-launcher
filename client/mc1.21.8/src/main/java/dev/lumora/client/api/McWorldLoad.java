package dev.lumora.client.api;

import dev.lumora.api.screen.AddonView;
import dev.lumora.client.gui.AddonViewScreen;
import dev.lumora.client.mixin.LevelLoadingScreenAccessor;
import dev.lumora.core.connection.ConnectionModule;
import dev.lumora.core.connection.WorldLoadAttempt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;

import java.util.Set;
import java.util.function.Consumer;

/**
 * One singleplayer world being opened, for Lumora's loading screen (1.21.8). While it runs, every loading screen
 * Minecraft opens (reading the world, data packs, the progress screen, the spawn area screen, the terrain screen)
 * becomes the screen underneath Lumora's: Minecraft keeps ticking it and it still closes itself when the world is
 * ready. Only reads Minecraft's state.
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
    /** The screen underneath was given Minecraft and its size (it was never opened itself). */
    private boolean underlyingReady;
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

    /** Called where a world starts opening, before its first screen. */
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
                running.underlyingReady = false;
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

    /**
     * Minecraft got the spawn position: it tells this to its terrain screen only while that screen is shown, so Lumora
     * passes it on to the one underneath (otherwise it would wait for its time limit).
     */
    public static void loadingPacketsReceived() {
        McWorldLoad running = current;
        if (running != null && running.underlying instanceof ReceivingLevelScreen r
                && Minecraft.getInstance().screen == running.wrapper) {
            // this version\x27s terrain screen does not wait for it (r unused)
        }
    }

    /** Exact classes: screens of other code that extend these stay as they are. */
    private static boolean isLoadingScreen(Screen s) {
        Class<?> c = s.getClass();
        return c == GenericMessageScreen.class || c == ProgressScreen.class || c == LevelLoadingScreen.class
                || c == ReceivingLevelScreen.class;
    }

    /** A world starts opening: its first message screen, or the spawn area screen before any connection exists. */
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
        return title != null && title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
    }

    private void tickUnderlying() {
        Screen s = underlying;
        if (s == null) {
            return;
        }
        if (!underlyingReady) {
            // never opened itself: give it Minecraft and the window size, its tick and close need them
            underlyingReady = true;
            Minecraft mc = Minecraft.getInstance();
            s.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
        s.tick(); // the terrain screen closes itself (and Lumora's) once the world is ready
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
        if (s instanceof ReceivingLevelScreen) {
            return Stage.TERRAIN;
        }
        if (s instanceof LevelLoadingScreen) {
            return Stage.SPAWN;
        }
        if (s instanceof ProgressScreen) {
            return Stage.SERVER; // Minecraft clears the previous world right before starting the server
        }
        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            return Stage.SERVER; // the message screen stays up while the server starts
        }
        String k = s == null ? "" : key(s);
        if (PREPARING.equals(k)) {
            return Stage.CREATE;
        }
        return "selectWorld.resource_load".equals(k) ? Stage.RESOURCES : Stage.READ;
    }

    @Override
    public int progress() {
        if (underlying instanceof LevelLoadingScreen l) {
            StoringChunkProgressListener p = ((LevelLoadingScreenAccessor) l).lumora$progressListener();
            if (p != null) {
                return Math.max(0, Math.min(100, p.getProgress()));
            }
        }
        return -1;
    }
}
