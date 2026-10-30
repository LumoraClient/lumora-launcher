package dev.lumora.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.diagnostics.Diagnostics;
import dev.lumora.client.diagnostics.SmokeTest;
import dev.lumora.client.gui.LumoraButton;
import dev.lumora.client.gui.LumoraHubScreen;
import dev.lumora.client.gui.ScreenTransition;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.zoom.LumoraZoom;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import dev.lumora.client.integration.RuntimeInfo;
import dev.lumora.client.perf.FrameStats;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Everything is wired through Fabric API events (screen init, lifecycle, ticks, key mappings);
 * the only mixin is the read-only frame timer.
 */
public final class LumoraClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Lumora");
    private static LumoraConfig config;
    private static KeyMapping benchmarkKey;
    private static KeyMapping hubKey;
    private static SmokeTest smokeTest;

    public static LumoraConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = LumoraConfig.load(FabricLoader.getInstance().getConfigDir().resolve("lumora-client.json"));
        smokeTest = SmokeTest.fromSystemProperty();
        LOG.info("Lumora Client initialising (profile {}, Sodium {}, ViaFabricPlus {})",
                Diagnostics.profile(), RuntimeInfo.sodiumLoaded(), RuntimeInfo.viaFabricPlusLoaded());

        // Lumora Theme: the whole Minecraft GUI (buttons, sliders, text fields, tabs, lists, toasts) in the
        // launcher's colors. Enabled by default, can be switched off under Options > Resource Packs.
        FabricLoader.getInstance().getModContainer("lumora").ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
                ResourceLocation.fromNamespaceAndPath("lumora", "lumora_theme"), mod,
                Component.translatable("lumora.pack.theme"), ResourcePackActivationType.DEFAULT_ENABLED));
        // Lumora Schrift (Inter): optional, switched in the Lumora hub
        FabricLoader.getInstance().getModContainer("lumora").ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
                ResourceLocation.fromNamespaceAndPath("lumora", "lumora_font"), mod,
                Component.translatable("lumora.pack.font"), ResourcePackActivationType.NORMAL));

        // Lumora API: addons, widgets, HUD editor (lumora-core, connected to this Minecraft version)
        LumoraRuntime.init();

        // zoom border, HUD widgets, notifications and the fade back into the game share one HUD layer on top of everything
        HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath("lumora", "hud"), (g, delta) -> {
            LumoraZoom.overlay(g);
            LumoraRuntime.renderHud(g, delta);
            if (Minecraft.getInstance().screen == null) {
                ScreenTransition.draw(g, g.guiWidth(), g.guiHeight());
            }
        });

        String category = "key.categories.lumora";
        benchmarkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lumora.benchmark",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, category));
        hubKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lumora.hub",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        LumoraZoom.init(KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lumora.zoom",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category)));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            RuntimeInfo.registerVfpLogging();
            if (config.writeDiagnosticsOnStart) {
                Diagnostics.write("game-" + Diagnostics.profile() + "-latest.json", Diagnostics.collect());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (benchmarkKey.consumeClick()) {
                startBenchmark();
            }
            LumoraZoom.tick(mc);
            while (hubKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new LumoraHubScreen(null));
                }
            }
            if (FrameStats.INSTANCE.isFinished() && smokeTest == null) {
                finishBenchmark(mc);
            }
            if (smokeTest != null) {
                smokeTest.tick(mc);
            }
        });

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen && config.titleScreenButton) {
                Screens.getButtons(screen).add(Button.builder(Component.translatable("lumora.title.button"),
                        b -> mc.setScreen(new LumoraHubScreen(screen))).bounds(4, 4, 80, 20).build());
            } else if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && config.pauseMenuButton) {
                // same pill as in the Lumora main menu; the hub returns to the pause menu when closed
                Screens.getButtons(screen).add(new LumoraButton(6, 6, 74, 18, Component.translatable("lumora.menu.hub"),
                        LumoraButton.Style.SECONDARY, "✦", () -> mc.setScreen(new LumoraHubScreen(screen))));
                Screens.getButtons(screen).add(new LumoraButton(84, 6, 84, 18, dev.lumora.client.gui.SocialScreen.label(),
                        LumoraButton.Style.SECONDARY, "☻", () -> mc.setScreen(new dev.lumora.client.gui.SocialScreen(screen))));
            } else if (screen instanceof JoinMultiplayerScreen && config.multiplayerProtocolLabel && RuntimeInfo.viaFabricPlusLoaded()) {
                String target = RuntimeInfo.vfpTargetVersion();
                Component text = Component.translatable("lumora.multiplayer.protocol",
                        target != null ? target : Component.translatable("lumora.multiplayer.protocol.auto"));
                Screens.getButtons(screen).add(new StringWidget(4, 4, mc.font.width(text) + 4, 12, text, mc.font));
            }
        });
    }

    public static void startBenchmark() {
        if (FrameStats.INSTANCE.isRecording()) {
            return;
        }
        FrameStats.INSTANCE.start(config.benchmarkSeconds);
        toast(Minecraft.getInstance(), Component.translatable("lumora.benchmark.started", config.benchmarkSeconds));
    }

    private static void finishBenchmark(Minecraft mc) {
        FrameStats.Summary s = FrameStats.INSTANCE.finish();
        if (s == null) {
            return;
        }
        LOG.info("Benchmark: {}", s.format());
        var file = Diagnostics.writeBenchmark(s);
        toast(mc, Component.literal(String.format(java.util.Locale.ROOT, "Ø %.0f FPS · 1%% low %.0f · p99 %.1f ms → %s",
                s.avgFps(), s.onePercentLowFps(), s.p99Ms(), file.getFileName())));
    }

    private static void toast(Minecraft mc, Component body) {
        SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("lumora.toast.title"), body);
    }
}
