package dev.lumora.client.api;

import dev.lumora.api.event.client.PlayerChangedEvent;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.hud.BuiltInHudAddon;
import dev.lumora.client.hud.ClickTracker;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.addon.AddonCandidate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects the Minecraft-independent {@link LumoraCore} to Minecraft 26.1.2. Where each API event comes from:
 * <ul>
 *   <li>ClientStarted - Fabric {@code CLIENT_STARTED} (addons are loaded right before, when options and
 *       resources exist); ClientStopping - {@code CLIENT_STOPPING}.</li>
 *   <li>ClientTick, WorldJoin/WorldLeave, PlayerChanged, ScreenChanged - {@code END_CLIENT_TICK}: the game state is
 *       snapshotted and compared with the previous tick.</li>
 *   <li>HUD rendering - the Fabric HUD element "lumora:hud" ({@link #renderHud}); notifications additionally after
 *       every screen ({@code ScreenEvents.afterExtract}).</li>
 *   <li>ResourcesReloaded - {@code MinecraftReloadMixin} on {@code Minecraft.reloadResourcePacks()}.</li>
 *   <li>Widget clicks - {@code ScreenMouseEvents.allowMouseClick} on the chat screen.</li>
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
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            ScreenEvents.afterRender(screen).register((s, pose, mx, my, delta) -> core.renderNotifications(GRAPHICS.with(GuiGraphics.of(pose)), s.width));
            if (screen instanceof ChatScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> !core.hudClick(mx, my, button));
            }
        });
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
                "FPS, CPS, Koordinaten, Tasten, Ping, Uhr, Tempo, Rüstung, Server", BuiltInHudAddon.class), BuiltInHudAddon::new));
        candidates.addAll(FabricAddonDiscovery.discover());
        platform.game().update(mc, 0);
        core.loadAddons(candidates);
        LumoraClient.LOG.info("Lumora API {}: {} addon(s), {} widget(s)", core.apiVersion(), core.addons().size(), core.widgets().size());
        core.clientStarted();
    }

    private static void tick(Minecraft mc) {
        platform.game().update(mc, core.taskService().currentTick());
        boolean inWorld = mc.player != null && mc.level != null;
        if (inWorld) {
            String dim = mc.level.dimension().location().toString();
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
        String screen = mc.screen == null ? "" : mc.screen.getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            core.screenChanged(lastScreen, screen);
            lastScreen = screen;
        }
        core.tick();
    }

    /** HUD layer (every frame in game). The editor draws the widgets itself. */
    public static void renderHud(GuiGraphics g, float delta) {
        Minecraft mc = Minecraft.getInstance();
        ClickTracker.update(mc);
        if (core == null || mc.options.hideGui) {
            return;
        }
        if (mc.player != null && !(mc.screen instanceof HudEditorScreen)) {
            core.renderHud(GRAPHICS.with(g), context.set(false, false, delta, g.guiWidth(), g.guiHeight(),
                    (int) mc.getWindow().getGuiScale()));
        }
        if (mc.screen == null) {
            core.renderNotifications(GRAPHICS.with(g), g.guiWidth());
        }
    }

    /** Graphics and context for the editor screen (same reused instances). */
    public static McHudGraphics graphics(GuiGraphics g) {
        return GRAPHICS.with(g);
    }

    public static McRenderContext editorContext(int width, int height, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return context.set(mc.player == null, true, partialTick, width, height, (int) mc.getWindow().getGuiScale());
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
