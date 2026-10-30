package dev.lumora.client;

import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.diagnostics.Diagnostics;
import dev.lumora.client.diagnostics.SmokeTest;
import dev.lumora.client.gui.LumoraHubScreen;
import dev.lumora.client.gui.ScreenTransition;
import dev.lumora.client.integration.RuntimeInfo;
import dev.lumora.client.perf.FrameStats;
import dev.lumora.client.zoom.LumoraZoom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Locale;

/**
 * Entry point for Minecraft 1.8.9 (Legacy Fabric). Lifecycle, ticks, key bindings and the HUD go through the Legacy
 * Fabric API; Legacy Fabric has no screen events, so the Lumora buttons in vanilla screens come from ScreenButtonsMixin
 * ({@link #afterScreenInit}, {@link #buttonClicked}).
 */
public final class LumoraClient implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("Lumora");
    /** Id of the Lumora buttons added to vanilla screens (vanilla ids are small numbers). */
    public static final int HUB_BUTTON_ID = 0x4C554D;
    public static final int SOCIAL_BUTTON_ID = 0x4C5553;
    private static LumoraConfig config;
    private static KeyBinding benchmarkKey;
    private static KeyBinding hubKey;
    private static SmokeTest smokeTest;

    public static LumoraConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = LumoraConfig.load(FabricLoader.getInstance().getConfigDir().resolve("lumora-client.json"));
        smokeTest = SmokeTest.fromSystemProperty();
        LOG.info("Lumora Client initialising (profile {}, Minecraft {}, ViaFabric {})",
                Diagnostics.profile(), RuntimeInfo.MINECRAFT_VERSION, RuntimeInfo.viaFabricLoaded());

        // Lumora API: addons, widgets, HUD editor (lumora-core, connected to this Minecraft version)
        LumoraRuntime.init();

        // zoom border, HUD widgets, notifications and the fade back into the game share one HUD layer on top of everything
        HudRenderCallback.EVENT.register((mc, delta) -> {
            GuiGraphics g = GuiGraphics.create();
            LumoraZoom.overlay(g);
            LumoraRuntime.renderHud(g, delta);
            if (mc.currentScreen == null) {
                ScreenTransition.draw(g, g.guiWidth(), g.guiHeight());
            }
        });

        String category = "key.categories.lumora";
        benchmarkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lumora.benchmark", Keyboard.KEY_F9, category));
        hubKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lumora.hub", Keyboard.KEY_RSHIFT, category));
        LumoraZoom.init(KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lumora.zoom", Keyboard.KEY_C, category)));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            if (config.writeDiagnosticsOnStart) {
                Diagnostics.write("game-" + Diagnostics.profile() + "-latest.json", Diagnostics.collect());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (benchmarkKey.wasPressed()) {
                startBenchmark();
            }
            LumoraZoom.tick(mc);
            while (hubKey.wasPressed()) {
                if (mc.currentScreen == null) {
                    mc.setScreen(new LumoraHubScreen(null));
                }
            }
            if (FrameStats.INSTANCE.isFinished() && smokeTest == null) {
                finishBenchmark();
            }
            if (smokeTest != null) {
                smokeTest.tick(mc);
            }
        });
    }

    /** ScreenButtonsMixin, after a vanilla screen built its buttons: the Lumora entry in the title and pause menu. */
    public static void afterScreenInit(Screen screen, List<ButtonWidget> buttons) {
        if (screen instanceof TitleScreen && config.titleScreenButton) {
            buttons.add(new ButtonWidget(HUB_BUTTON_ID, 4, 4, 80, 20, I18n.translate("lumora.title.button")));
        } else if (screen instanceof GameMenuScreen && config.pauseMenuButton) {
            buttons.add(new ButtonWidget(HUB_BUTTON_ID, 6, 6, 74, 18, "✦ " + I18n.translate("lumora.menu.hub")));
            buttons.add(new ButtonWidget(SOCIAL_BUTTON_ID, 84, 6, 84, 18, "☻ " + dev.lumora.client.gui.SocialScreen.label().getString()));
        }
    }

    /** ScreenButtonsMixin: true if the clicked vanilla button was a Lumora one (the hub opens, vanilla ignores it). */
    public static boolean buttonClicked(Screen screen, ButtonWidget button) {
        if (button.id == SOCIAL_BUTTON_ID) {
            MinecraftClient.getInstance().setScreen(new dev.lumora.client.gui.SocialScreen(screen));
            return true;
        }
        if (button.id != HUB_BUTTON_ID) {
            return false;
        }
        MinecraftClient.getInstance().setScreen(new LumoraHubScreen(screen));
        return true;
    }

    public static void startBenchmark() {
        if (FrameStats.INSTANCE.isRecording()) {
            return;
        }
        FrameStats.INSTANCE.start(config.benchmarkSeconds);
        notify(I18n.translate("lumora.benchmark.started", config.benchmarkSeconds));
    }

    private static void finishBenchmark() {
        FrameStats.Summary s = FrameStats.INSTANCE.finish();
        if (s == null) {
            return;
        }
        LOG.info("Benchmark: {}", s.format());
        var file = Diagnostics.writeBenchmark(s);
        notify(String.format(Locale.ROOT, "Ø %.0f FPS · 1%% low %.0f · p99 %.1f ms → %s",
                s.avgFps(), s.onePercentLowFps(), s.p99Ms(), file.getFileName()));
    }

    /** 1.8.9 has no toasts: benchmark messages are Lumora notifications. */
    private static void notify(String body) {
        if (LumoraRuntime.ready()) {
            LumoraRuntime.core().notifications().info(I18n.translate("lumora.toast.title"), body);
        }
    }
}
