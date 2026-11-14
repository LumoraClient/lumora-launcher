package dev.lumora.client.api;

import dev.lumora.api.event.client.PlayerChangedEvent;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.hud.BuiltInHudAddon;
import dev.lumora.client.hud.ClickTracker;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.addon.AddonCandidate;
import dev.lumora.client.platform.Channels;
import dev.lumora.client.platform.Hooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects the Minecraft-independent {@link LumoraCore} to Minecraft 26.1 - 26.1.2. Where each API event comes from:
 * <ul>
 *   <li>ClientStarted - {@link Hooks#onStarted} (addons are loaded right before, when options and
 *       resources exist); ClientStopping - {@link Hooks#onStopping}.</li>
 *   <li>ClientTick, WorldJoin/WorldLeave, PlayerChanged, ScreenChanged - {@link Hooks#onEndTick}: the game state is
 *       snapshotted and compared with the previous tick.</li>
 *   <li>HUD rendering - Lumora's HUD layer ({@link #renderHud}); notifications additionally after
 *       every screen ({@link Hooks#afterScreenExtract}).</li>
 *   <li>ResourcesReloaded - {@code MinecraftReloadMixin} on {@code Minecraft.reloadResourcePacks()}.</li>
 *   <li>Widget clicks - {@link Hooks#chatClick} (chat screen).</li>
 * </ul>
 */
public final class LumoraRuntime {
    private static McPlatform platform;
    private static LumoraCore core;
    private static McRenderContext context;
    private static final McHudGraphics GRAPHICS = new McHudGraphics();
    private static boolean wasInWorld;
    private static Object lastPlayer;
    private static String lastDimension = "";
    private static String lastScreen = "";

    private LumoraRuntime() {
    }

    /** Called from the mod initializer: creates the core and registers the hooks. */
    public static void init() {
        platform = new McPlatform();
        core = new LumoraCore(platform);
        core.installGlobal();
        context = new McRenderContext(platform.game(), platform.translations());
        Hooks.onStarted(LumoraRuntime::start);
        Hooks.onStopping(mc -> core.stopping());
        Hooks.onEndTick(LumoraRuntime::tick);
        Hooks.afterScreenExtract((s, g, mx, my, delta) -> {
            if (s instanceof net.minecraft.client.gui.screens.ChatScreen) {
                dev.lumora.core.extras.ChatModule.renderChatScreen(GRAPHICS.with(g), mx, my);
            }
            core.renderNotifications(GRAPHICS.with(g), s.width);
        });
        Hooks.chatClick((s, e) -> !dev.lumora.core.extras.ChatModule.chatClick(e.x(), e.y(), e.button()) && !core.hudClick(e.x(), e.y(), e.button()));
    }

    public static LumoraCore core() {
        return core;
    }

    public static boolean ready() {
        return core != null;
    }

    private static void start(Minecraft mc) {
        List<AddonCandidate> candidates = new ArrayList<>();
        candidates.add(AddonCandidate.of(LumoraCore.builtInDescriptor("lumora", "Lumora HUD",
                "FPS, CPS, coordinates, keystrokes, ping, clock, speed, server", BuiltInHudAddon.class), BuiltInHudAddon::new));
        platform.game().update(mc, 0);
        core.loadAddons(candidates);
        LumoraClient.LOG.info("Lumora API {}: {} addon(s), {} widget(s)", core.apiVersion(), core.addons().size(), core.widgets().size());
        core.clientStarted();
    }

    private static void tick(Minecraft mc) {
        platform.game().update(mc, core.taskService().currentTick());
        boolean inWorld = mc.player != null && mc.level != null;
        if (inWorld) {
            String dim = mc.level.dimension().identifier().toString();
            if (!wasInWorld) {
                Channels.announce();
                core.worldJoin(platform.game().connection());
            } else if (!dim.equals(lastDimension)) {
                core.playerChanged(PlayerChangedEvent.Reason.DIMENSION, dim);
            } else if (mc.player != lastPlayer) {
                core.playerChanged(PlayerChangedEvent.Reason.RESPAWN, dim);
            }
            lastDimension = dim;
            lastPlayer = mc.player;
        } else if (wasInWorld) {
            core.worldLeave();
            lastPlayer = null;
            lastDimension = "";
        }
        wasInWorld = inWorld;
        String screen = mc.screen == null ? "" : mc.screen.getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            core.screenChanged(lastScreen, screen);
            lastScreen = screen;
        }
        core.tick();
    }

    /** HUD layer (every frame in game). The editor draws the widgets itself. */
    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ClickTracker.update(mc);
        if (core == null || mc.options.hideGui) {
            return;
        }
        if (mc.player != null && !(mc.screen instanceof HudEditorScreen)) {
            core.renderHud(GRAPHICS.with(g), context.set(false, false, delta.getGameTimeDeltaPartialTick(false), g.guiWidth(), g.guiHeight(),
                    mc.getWindow().getGuiScale()));
        }
        if (mc.screen == null) {
            core.renderNotifications(GRAPHICS.with(g), g.guiWidth());
        }
    }

    /** Graphics and context for the editor screen (same reused instances). */
    public static McHudGraphics graphics(GuiGraphicsExtractor g) {
        return GRAPHICS.with(g);
    }

    public static McRenderContext editorContext(int width, int height, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return context.set(mc.player == null, true, partialTick, width, height, mc.getWindow().getGuiScale());
    }

    /** Physical key state (modifiers in the editor). */
    public static boolean keyDown(int key) {
        return platform.keys().isDown(key);
    }

    public static void resourcesReloaded() {
        if (core != null) {
            core.resourcesReloaded();
        }
    }
}
