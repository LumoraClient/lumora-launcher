package dev.lumora.client;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import com.mojang.blaze3d.platform.InputConstants;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.diagnostics.Diagnostics;
import dev.lumora.client.diagnostics.SmokeTest;
import dev.lumora.client.gui.LumoraButton;
import dev.lumora.client.gui.LumoraFont;
import dev.lumora.client.gui.LumoraHubScreen;
import dev.lumora.client.gui.ScreenTransition;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.zoom.LumoraZoom;
import dev.lumora.client.integration.RuntimeInfo;
import dev.lumora.client.perf.FrameStats;
import dev.lumora.client.platform.Hooks;
import dev.lumora.client.platform.LoaderEnv;
import dev.lumora.client.platform.Packs;
import dev.lumora.loader.api.ClientEntrypoint;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiComponent;
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
 * Entry point, started by the Lumora Loader before Minecraft runs. Everything is wired through Lumora's own hooks
 * ({@link Hooks}: screen init, lifecycle, ticks, HUD, key mappings) and resource packs ({@link Packs}).
 */
public final class LumoraClient implements ClientEntrypoint {
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
        config = LumoraConfig.load(LoaderEnv.configDir().resolve("lumora-client.json"));
        dev.lumora.client.gui.LumoraUi.applyAccent(config.accentColor);
        smokeTest = SmokeTest.fromSystemProperty();
        LOG.info("Lumora Client initialising (profile {}, Sodium {}, ViaFabricPlus {})",
                Diagnostics.profile(), RuntimeInfo.sodiumLoaded(), RuntimeInfo.viaFabricPlusLoaded());

        // Lumora Theme: the whole Minecraft GUI (buttons, sliders, text fields, tabs, lists, toasts) in the
        // launcher's colors. Enabled by default, can be switched off under Options > Resource Packs.
        Packs.builtin("lumora_theme", null);
        // Lumora Schrift (Inter): optional, switched in the Lumora hub
        Packs.builtin("lumora_font", null);
        for (dev.lumora.core.theme.LumoraFonts.Font f : dev.lumora.core.theme.LumoraFonts.ALL) {
            if (f.pack() != null && !f.pack().equals("lumora_font")) {
                Packs.builtin(f.pack(), null);
            }
        }

        // Lumora API: addons, widgets, HUD editor (lumora-core, connected to this Minecraft version)
        LumoraRuntime.init();

        // zoom border, HUD widgets, notifications and the fade back into the game share one HUD layer on top of everything
        Hooks.hudLayer((pose, delta) -> {
            GuiGraphics g = GuiGraphics.of(pose);
            LumoraZoom.overlay(g);
            LumoraRuntime.renderHud(g, delta);
            if (Minecraft.getInstance().screen == null) {
                ScreenTransition.draw(g, g.guiWidth(), g.guiHeight());
            }
        });

        String category = "key.categories.lumora";
        benchmarkKey = Hooks.registerKey(new KeyMapping("key.lumora.benchmark",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, category));
        hubKey = Hooks.registerKey(new KeyMapping("key.lumora.hub",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        LumoraZoom.init(Hooks.registerKey(new KeyMapping("key.lumora.zoom",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category)));

        Hooks.onStarted(mc -> {
            RuntimeInfo.registerVfpLogging();
            if (config.writeDiagnosticsOnStart) {
                Diagnostics.write("game-" + Diagnostics.profile() + "-latest.json", Diagnostics.collect());
            }
        });

        Hooks.onEndTick(mc -> {
            // first start with Lumora: switch the theme pack on once, after the start-up reload (see LumoraConfig#themePackApplied)
            if (!config.themePackApplied && mc.getOverlay() == null) {
                config.themePackApplied = true;
                config.save(LoaderEnv.configDir().resolve("lumora-client.json"));
                LumoraFont.applyPack(mc, LumoraFont.THEME_PACK_ID, true);
            }
            while (benchmarkKey.consumeClick()) {
                startBenchmark();
            }
            LumoraZoom.tick(mc);
            while (hubKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(LumoraHubScreen.create(null));
                }
            }
            if (FrameStats.INSTANCE.isFinished() && smokeTest == null) {
                finishBenchmark(mc);
            }
            if (smokeTest != null) {
                smokeTest.tick(mc);
            }
        });

        Hooks.afterScreenInit((mc, screen, w, h) -> {
            // menu cross-fade after the screen and its tooltips (1.17.1 has no Screen#renderWithTooltip to hook)
            Hooks.afterRender(screen, (s, pose, mx, my, delta) -> ScreenTransition.draw(GuiGraphics.of(pose), s));
            if (screen instanceof TitleScreen && config.titleScreenButton) {
                Hooks.addWidget(screen, new Button(4, 4, 80, 20, new TranslatableComponent("lumora.title.button"),
                        b -> mc.setScreen(LumoraHubScreen.create(screen))));
                Hooks.addWidget(screen, new LumoraButton(88, 4, 20, 20, new net.minecraft.network.chat.TextComponent("Screenshots"), LumoraButton.Style.SECONDARY, "#camera",
                        () -> dev.lumora.core.hub.ScreenshotView.open(dev.lumora.client.api.LumoraRuntime.core())));
            } else if (screen instanceof PauseScreen pause && !pause.children().isEmpty() /* 1.17.1: F3+Esc pause has no buttons */ && config.pauseMenuButton) {
                // same pill as in the Lumora main menu; the hub returns to the pause menu when closed
                Hooks.addWidget(screen, new LumoraButton(6, 6, 74, 18, new TranslatableComponent("lumora.menu.hub"),
                        LumoraButton.Style.SECONDARY, "✦", () -> mc.setScreen(LumoraHubScreen.create(screen))));
                Hooks.addWidget(screen, new LumoraButton(84, 6, 84, 18, dev.lumora.client.gui.SocialScreen.label(),
                        LumoraButton.Style.SECONDARY, "☻", () -> mc.setScreen(new dev.lumora.client.gui.SocialScreen(screen))));
                Hooks.addWidget(screen, new LumoraButton(172, 6, 18, 18, new net.minecraft.network.chat.TextComponent("Screenshots"), LumoraButton.Style.SECONDARY, "#camera",
                        () -> dev.lumora.core.hub.ScreenshotView.open(dev.lumora.client.api.LumoraRuntime.core())));
            } else if (screen instanceof JoinMultiplayerScreen && config.multiplayerProtocolLabel && RuntimeInfo.viaFabricPlusLoaded()) {
                String target = RuntimeInfo.vfpTargetVersion();
                Component text = new TranslatableComponent("lumora.multiplayer.protocol",
                        target != null ? target : new TranslatableComponent("lumora.multiplayer.protocol.auto"));
                // 1.17.1 has no StringWidget: the label is drawn after the screen
                Hooks.afterRender(screen, (s, pose, mx, my, delta) -> GuiComponent.drawString(pose, mc.font, text, 6, 6, 0xFFFFFFFF));
            }
        });
    }

    public static void startBenchmark() {
        if (FrameStats.INSTANCE.isRecording()) {
            return;
        }
        FrameStats.INSTANCE.start(config.benchmarkSeconds);
        toast(Minecraft.getInstance(), new TranslatableComponent("lumora.benchmark.started", config.benchmarkSeconds));
    }

    private static void finishBenchmark(Minecraft mc) {
        FrameStats.Summary s = FrameStats.INSTANCE.finish();
        if (s == null) {
            return;
        }
        LOG.info("Benchmark: {}", s.format());
        var file = Diagnostics.writeBenchmark(s);
        toast(mc, new TextComponent(String.format(java.util.Locale.ROOT, "Ø %.0f FPS · 1%% low %.0f · p99 %.1f ms → %s",
                s.avgFps(), s.onePercentLowFps(), s.p99Ms(), file.getFileName())));
    }

    private static void toast(Minecraft mc, Component body) {
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT /* 1.17.1 has no PERIODIC_NOTIFICATION */,
                new TranslatableComponent("lumora.toast.title"), body);
    }
}
