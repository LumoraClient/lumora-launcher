package dev.lumora.client.api;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.api.screen.AddonView;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.gui.AddonViewScreen;
import dev.lumora.client.mixin.MinecraftClientRunningAccessor;
import dev.lumora.core.connection.ConnectionModule;
import dev.lumora.core.connection.WorldLoadAttempt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.Window;
import net.minecraft.server.integrated.IntegratedServer;

import java.util.function.Consumer;

/**
 * One singleplayer world being opened, for Lumora's loading screen (1.12.2). Until the server is up, Minecraft
 * draws its loading text directly (LoadingScreenRenderer, outside any screen); Lumora draws its view there instead.
 * Then Minecraft's terrain screen runs underneath Lumora's screen and still closes itself when the world is ready.
 * Only reads Minecraft's state.
 */
public final class McWorldLoad implements WorldLoadAttempt {
    /** A start that never reaches the terrain screen (error, window closed) stops drawing after this long. */
    private static final long MAX_MS = 120_000;
    private static McWorldLoad current;

    private final String name;
    private final boolean created;
    private final long startedAt = System.currentTimeMillis();
    private AddonView view;
    private Screen underlying;
    private boolean underlyingReady;
    private Screen wrapper;
    private long lastDraw;
    /** Smoke test only: called once after Lumora drew the spawn area step (the main framebuffer holds the frame). */
    public static Runnable afterFirstDraw;

    private McWorldLoad(String name, boolean created) {
        this.name = name == null ? "" : name;
        this.created = created;
    }

    /** Minecraft starts the integrated server for a world ({@code newWorld}: it is being created). */
    public static void starting(String displayName, boolean newWorld) {
        current = null;
        try {
            McWorldLoad load = new McWorldLoad(displayName, newWorld);
            load.view = ConnectionModule.worldLoading(load);
            if (load.view != null) {
                current = load;
            }
        } catch (RuntimeException | LinkageError e) {
            current = null;
        }
    }

    private static McWorldLoad running() {
        McWorldLoad load = current;
        if (load != null && load.wrapper == null && System.currentTimeMillis() - load.startedAt > MAX_MS) {
            current = null;
            return null;
        }
        return load;
    }

    /**
     * Minecraft wants to draw its loading text (server start, spawn area). Returns true if Lumora drew its view
     * instead; false keeps Minecraft's drawing (no world load running, or the game is shutting down).
     */
    public static boolean drawLoading() {
        McWorldLoad load = running();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (load == null || load.wrapper != null || mc == null || !((MinecraftClientRunningAccessor) mc).lumora$running()) {
            return false;
        }
        try {
            long now = MinecraftClient.getTime();
            if (now - load.lastDraw < 50) {
                return true;
            }
            load.lastDraw = now;
            Window w = new Window(mc);
            Framebuffer fb = mc.getFramebuffer();
            fb.bind(true);
            GlStateManager.clear(256);
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0, w.getScaledWidth(), w.getScaledHeight(), 0, 1000, 3000);
            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0, 0, -2000);
            GlStateManager.clear(16640);
            load.view.render(LumoraRuntime.graphics(GuiGraphics.create()), w.getWidth(), w.getHeight(), -1, -1);
            fb.unbind();
            Runnable hook = afterFirstDraw;
            if (hook != null && load.stage() == Stage.SPAWN) {
                afterFirstDraw = null;
                hook.run();
            }
            fb.draw(mc.width, mc.height);
            mc.updateDisplay();
            return true;
        } catch (RuntimeException | LinkageError e) {
            current = null;
            return false;
        }
    }

    /**
     * Minecraft sets {@code screen} while {@code shown} is the current screen. Returns true if Lumora took it: the
     * terrain screen of the world being opened runs underneath Lumora's screen (shown through {@code show}).
     */
    public static boolean intercept(Screen shown, Screen screen, Consumer<Screen> show) {
        McWorldLoad load = running();
        if (load == null) {
            return false;
        }
        if (screen == null || screen.getClass() != DownloadingTerrainScreen.class) {
            if (load.wrapper != null && shown == load.wrapper) {
                current = null; // Lumora's screen is being replaced: the world is open (or the load ended)
            }
            return false;
        }
        try {
            load.underlying = screen;
            load.underlyingReady = false;
            if (load.wrapper != null && shown == load.wrapper) {
                return true;
            }
            load.wrapper = AddonViewScreen.connecting(new TitleScreen(), load.view, load::tickUnderlying);
            show.accept(load.wrapper);
            return true;
        } catch (RuntimeException | LinkageError e) {
            current = null;
            return false;
        }
    }

    private void tickUnderlying() {
        Screen s = underlying;
        if (s == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!underlyingReady) {
            underlyingReady = true; // never opened itself: give it Minecraft and the window size
            Window w = new Window(mc);
            s.init(mc, w.getWidth(), w.getHeight());
        }
        s.tick();
    }

    @Override
    public String worldName() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        if (server != null && server.getLevelName() != null && !server.getLevelName().isEmpty() && name.isEmpty()) {
            return server.getLevelName();
        }
        return name;
    }

    @Override
    public boolean newWorld() {
        return created;
    }

    @Override
    public Stage stage() {
        if (underlying != null) {
            return Stage.TERRAIN;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        IntegratedServer server = mc.getServer();
        if (server == null) {
            return created ? Stage.CREATE : Stage.READ;
        }
        if (mc.world != null) {
            return Stage.TERRAIN;
        }
        return server.progressType != null ? Stage.SPAWN : Stage.SERVER;
    }

    @Override
    public int progress() {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        if (underlying == null && server != null && server.progressType != null) {
            return Math.max(0, Math.min(100, server.progress));
        }
        return -1;
    }
}
