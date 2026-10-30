package dev.lumora.client.api;

import dev.lumora.api.event.client.PlayerChangedEvent;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.hud.BuiltInHudAddon;
import dev.lumora.client.hud.ClickTracker;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.addon.AddonCandidate;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.util.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects the Minecraft-independent {@link LumoraCore} to Minecraft 1.12.2 (Legacy Fabric). Where each API event comes from:
 * <ul>
 *   <li>ClientStarted - Legacy Fabric {@code CLIENT_STARTED} (addons are loaded right before, when options and
 *       resources exist); ClientStopping - {@code CLIENT_STOPPING}.</li>
 *   <li>ClientTick, WorldJoin/WorldLeave, PlayerChanged, ScreenChanged - {@code END_CLIENT_TICK}: the game state is
 *       snapshotted and compared with the previous tick.</li>
 *   <li>HUD rendering - Legacy Fabric {@code HudRenderCallback} ({@link #renderHud}); notifications additionally after
 *       every screen ({@link #afterScreenRender}, GameRendererScreenMixin - Legacy Fabric has no screen events).</li>
 *   <li>ResourcesReloaded - {@code MinecraftReloadMixin} on {@code MinecraftClient.reloadResources()}.</li>
 *   <li>Widget clicks - {@link #chatClick} from ChatScreenClickMixin.</li>
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
        ClientLifecycleEvents.CLIENT_STARTED.register(LumoraRuntime::start);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> core.stopping());
        ClientTickEvents.END_CLIENT_TICK.register(LumoraRuntime::tick);
    }

    public static LumoraCore core() {
        return core;
    }

    public static boolean ready() {
        return core != null;
    }

    private static void start(MinecraftClient mc) {
        List<AddonCandidate> candidates = new ArrayList<>();
        candidates.add(AddonCandidate.of(LumoraCore.builtInDescriptor("lumora", "Lumora HUD",
                "FPS, CPS, Koordinaten, Tasten, Ping, Uhr, Tempo, Rüstung, Server", BuiltInHudAddon.class), BuiltInHudAddon::new));
        candidates.addAll(FabricAddonDiscovery.discover());
        platform.game().update(mc, 0);
        core.loadAddons(candidates);
        LumoraClient.LOG.info("Lumora API {}: {} addon(s), {} widget(s)", core.apiVersion(), core.addons().size(), core.widgets().size());
        core.clientStarted();
    }

    private static void tick(MinecraftClient mc) {
        platform.game().update(mc, core.taskService().currentTick());
        boolean inWorld = mc.player != null && mc.world != null;
        if (inWorld) {
            String dim = platform.game().dimension().orElse("");
            if (!wasInWorld) {
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
        String screen = mc.currentScreen == null ? "" : mc.currentScreen.getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            core.screenChanged(lastScreen, screen);
            lastScreen = screen;
        }
        core.tick();
    }

    /** HUD layer (every frame in game). The editor draws the widgets itself. */
    public static void renderHud(GuiGraphics g, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClickTracker.update(mc);
        if (core == null || mc.options.hudHidden) {
            return;
        }
        if (mc.player != null && !(mc.currentScreen instanceof HudEditorScreen)) {
            core.renderHud(GRAPHICS.with(g), context.set(false, false, delta, g.guiWidth(), g.guiHeight(),
                    new Window(mc).getScaleFactor()));
        }
        if (mc.currentScreen == null) {
            core.renderNotifications(GRAPHICS.with(g), g.guiWidth());
        }
    }

    /** Notifications on top of every screen (GameRendererScreenMixin, right after the screen was rendered). */
    public static void afterScreenRender(GuiGraphics g, int width) {
        if (core != null) {
            core.renderNotifications(GRAPHICS.with(g), width);
        }
    }

    /** Click in the chat screen: true if a HUD widget used it (ChatScreenClickMixin then cancels the vanilla click). */
    public static boolean chatClick(double mx, double my, int button) {
        return core != null && core.hudClick(mx, my, button);
    }

    /** Graphics and context for the editor screen (same reused instances). */
    public static McHudGraphics graphics(GuiGraphics g) {
        return GRAPHICS.with(g);
    }

    public static McRenderContext editorContext(int width, int height, float partialTick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return context.set(mc.player == null, true, partialTick, width, height, new Window(mc).getScaleFactor());
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
