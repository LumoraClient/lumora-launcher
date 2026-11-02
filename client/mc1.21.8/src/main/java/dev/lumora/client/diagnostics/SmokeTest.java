package dev.lumora.client.diagnostics;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.anim.SwingAnimation;
import dev.lumora.client.gui.LumoraHubScreen;
import dev.lumora.client.gui.LumoraFont;
import dev.lumora.client.gui.SettingsScreen;
import net.minecraft.network.chat.Component;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.client.zoom.LumoraZoom;
import dev.lumora.client.gui.LumoraTitleScreen;
import dev.lumora.client.perf.FrameStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Automated start check, active only when the launcher passes -Dlumora.smokeTest=&lt;report file&gt;.
 * Once the main menu is shown it records 5 seconds of frame times, then opens a series of menus (Lumora hub,
 * options, world selection, multiplayer) and captures a screenshot of each (F2 path, saved in screenshots/),
 * then joins the demo world (one screenshot per swing animation, pause menu, hub from the pause menu),
 * writes a JSON report and closes the game normally.
 */
public final class SmokeTest {
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");
    private static final int SETTLE_TICKS = 40;
    private static final int SCREEN_TICKS = 20;

    private record Shot(String name, Function<Screen, Screen> open, int delay) {
        Shot(String name, Function<Screen, Screen> open) {
            this(name, open, SCREEN_TICKS);
        }
    }

    private enum Phase { WAIT_MENU, RECORDING, SHOTS, WORLD_LOAD, IN_WORLD, DONE }

    private static final String TEST_WORLD = "Lumora_SmokeTest";
    /** Max. wait for the demo world (generation included) before the in-world part is skipped. */
    private static final int WORLD_TIMEOUT_TICKS = 20 * 180;
    private static final int WORLD_SETTLE_TICKS = 100;

    private String worldResult = "not run";
    private int worldStep;
    private String savedSwing;
    private int zoomPhase;
    private Boolean labyVoiceBefore;
    private final List<String> extraProblems = new ArrayList<>();

    private final Path report;
    private Phase phase = Phase.WAIT_MENU;
    private int ticks;
    private FrameStats.Summary summary;
    private String menuName = "?";
    private Screen mainMenu;
    private final List<String> screenshots = new ArrayList<>();
    private final Deque<Shot> pending = new ArrayDeque<>();
    private Shot current;

    private SmokeTest(Path report) {
        this.report = report;
    }

    public static SmokeTest fromSystemProperty() {
        String p = System.getProperty("lumora.smokeTest");
        return p == null || p.isBlank() ? null : new SmokeTest(Path.of(p));
    }

    private static boolean isMainMenu(Minecraft mc) {
        return mc.screen instanceof TitleScreen || mc.screen instanceof LumoraTitleScreen;
    }

    public void tick(Minecraft mc) {
        ticks++;
        switch (phase) {
            case WAIT_MENU -> {
                if (!isMainMenu(mc)) {
                    ticks = 0;
                } else if (ticks >= SETTLE_TICKS) {
                    menuName = mc.screen.getClass().getSimpleName();
                    mainMenu = mc.screen;
                    ApiSmoke.logLoaded();
                    LOG.info("Smoke test: main menu reached ({}), recording frame times", menuName);
                    if (Boolean.getBoolean("lumora.smokeTest.crash")) {
                        // launcher crash test (--smoke-test --crash-test): let Minecraft write a real crash report
                        throw new IllegalStateException("Lumora smoke test: intentional crash");
                    }
                    FrameStats.INSTANCE.start(5);
                    phase = Phase.RECORDING;
                }
            }
            case RECORDING -> {
                if (FrameStats.INSTANCE.isFinished()) {
                    summary = FrameStats.INSTANCE.finish();
                    pending.add(new Shot("menu", parent -> null));
                    pending.add(new Shot("hub", LumoraHubScreen::new));
                    pending.add(new Shot("options", parent -> new OptionsScreen(parent, mc.options)));
                    pending.add(new Shot("worlds", SelectWorldScreen::new));
                    pending.add(new Shot("multiplayer", JoinMultiplayerScreen::new, 100));
                    // version-specific actions of the Lumora lists: vanilla screen once, create world, join (local test server or refused)
                    pending.add(new Shot("browser-classic", parent -> {
                        dev.lumora.core.browser.BrowserModule.smoke("classic");
                        return null;
                    }));
                    pending.add(new Shot("browser-create", parent -> {
                        dev.lumora.core.browser.BrowserModule.smoke("create");
                        return null;
                    }));
                    // Lumora connection screens: the connecting view while the test server that accepts and never answers (.t/SilentMc on 25602)
                    pending.add(new Shot("connecting", parent -> {
                        dev.lumora.core.browser.BrowserModule.smoke("join:127.0.0.1:25602");
                        return null;
                    }, 40));
                    pending.add(new Shot("browser-join", parent -> {
                        dev.lumora.core.connection.ConnectionTracker.get().cancelActive();
                        dev.lumora.core.browser.BrowserModule.smoke("join:127.0.0.1:25601");
                        return null;
                    }, 100));
                    pending.add(new Shot("connection-check", parent -> {
                        dev.lumora.core.connection.ConnectionTracker t = dev.lumora.core.connection.ConnectionTracker.get();
                        LOG.info("Smoke test: connection tracking last={} screens={}", t == null || t.last() == null ? "none"
                                : t.last().kind() + "@" + t.last().lastPhase(), dev.lumora.core.connection.ConnectionModule.enabled());
                        return null;
                    }, 1));
                    // about 100 ms into the cross-fade from the main menu to the options
                    pending.add(new Shot("transition", parent -> new OptionsScreen(parent, mc.options), 2));
                    pending.add(new Shot("hub-modules", parent -> LumoraHubScreen.withTab(parent, 0)));
                    pending.add(new Shot("hub-settings", parent -> LumoraHubScreen.withTab(parent, 1)));
                    pending.add(new Shot("hud-editor", HudEditorScreen::new));
                    // Lumora API: move a widget with the editor logic (saved; the next run logs it as loaded), its
                    // generated settings screen and the addon list
                    pending.add(new Shot("hud-editor-move", HudEditorScreen::new, 30));
                    pending.add(new Shot("widget-settings", parent -> ApiSmoke.core().widgetRegistry().entry(ApiSmoke.testWidget())
                            .map(e -> (Screen) new SettingsScreen(parent, Component.translatable(e.widget().nameKey()), e.widget().settings(), List.of(), null))
                            .orElse(null)));
                    pending.add(new Shot("hub-addons", parent -> LumoraHubScreen.withTab(parent, 3)));
                    if (dev.lumora.client.platform.LoaderEnv.isLoaded("lumoravoice")) {
                        // LumoraVoice menus through the Lumora screen API (Lumora API 1.1)
                        pending.add(new Shot("voice-settings", parent -> {
                            ApiSmoke.openVoiceSettings(0);
                            return null;
                        }));
                        pending.add(new Shot("voice-microphone", parent -> {
                            ApiSmoke.openVoiceSettings(1);
                            return null;
                        }));
                    }
                    if (System.getProperty("lumora.social.server") != null) {
                        // Lumora Social against the test server: friend request (the bots accept and add to their group),
                        // group chat, a message the server has to confirm
                        pending.add(new Shot("social", parent -> {
                            dev.lumora.core.social.SocialSmoke.begin(ApiSmoke.core().social());
                            return new dev.lumora.client.gui.SocialScreen(parent);
                        }, 240));
                        pending.add(new Shot("social-sent", parent -> {
                            dev.lumora.client.gui.SocialScreen s = new dev.lumora.client.gui.SocialScreen(parent);
                            dev.lumora.core.social.SocialSmoke.send(ApiSmoke.core().social(), s.view(), Diagnostics.profile());
                            return s;
                        }, 140));
                    }
                    // Lumora loading screen: a resource reload shows it over the menu
                    pending.add(new Shot("loading", parent -> {
                        mc.reloadResourcePacks();
                        return null;
                    }, 6));
                    pending.add(new Shot("loading-done", parent -> null, 120));
                    // Lumora font (Inter) on, screenshot, and off again
                    pending.add(new Shot("font", parent -> {
                        LumoraFont.apply(mc, true);
                        return null;
                    }, 160));
                    pending.add(new Shot("font-off", parent -> {
                        LumoraFont.apply(mc, false);
                        return null;
                    }, 160));
                    phase = Phase.SHOTS;
                    ticks = 0;
                }
            }
            case SHOTS -> {
                if (current == null) {
                    if (pending.isEmpty()) {
                        startWorld(mc);
                        return;
                    }
                    if (ticks < 10) {
                        return; // let the fade back to the main menu finish
                    }
                    current = pending.poll();
                    Screen next = current.open().apply(mainMenu);
                    if (next != null) {
                        mc.setScreen(next);
                    }
                    ticks = 0;
                } else if (ticks == current.delay() - 10 && current.name().equals("hud-editor-move")
                        && mc.screen instanceof HudEditorScreen editor) {
                    ApiSmoke.moveInEditor(editor.model());
                } else if (ticks >= current.delay()) {
                    if ((current.name().equals("multiplayer") || current.name().equals("worlds"))
                            && dev.lumora.core.browser.BrowserModule.enabled() && !dev.lumora.client.gui.AddonViewScreen.isShowing()) {
                        extraProblems.add(current.name() + ": Lumora list did not replace the vanilla screen");
                    }
                    if (current.name().startsWith("voice-") && !dev.lumora.client.gui.AddonViewScreen.isShowing()) {
                        extraProblems.add(current.name() + ": LumoraVoice menu did not open");
                    }
                    screenshot(mc, current.name());
                    current = null;
                    mc.setScreen(mainMenu);
                    ticks = 0;
                }
            }
            case WORLD_LOAD -> {
                if (mc.level == null || mc.player == null) {
                    if (ticks % 200 == 0) {
                        LOG.info("Smoke test: waiting for the world, screen {}", mc.screen == null ? "-" : mc.screen.getClass().getSimpleName());
                    }
                    if (ticks > WORLD_TIMEOUT_TICKS) {
                        worldResult = "skipped: demo world did not load within " + WORLD_TIMEOUT_TICKS / 20 + " s";
                        LOG.warn("Smoke test: {}", worldResult);
                        phase = Phase.DONE;
                        finish(mc);
                    }
                    return;
                }
                if (ticks < WORLD_SETTLE_TICKS) {
                    return;
                }
                LOG.info("Smoke test: demo world loaded, checking swing animations and pause menu");
                // the test window may be in the background: do not let Minecraft pause the game on its own
                mc.options.pauseOnLostFocus = false;
                mc.setScreen(null); // closes the demo intro
                giveSword(mc);
                savedSwing = LumoraClient.config().swingAnimation;
                phase = Phase.IN_WORLD;
                ticks = 0;
                worldStep = 0;
            }
            case IN_WORLD -> inWorld(mc);
            case DONE -> { }
        }
    }

    /** Creates a demo-mode world (same settings as the vanilla "Play Demo World" button). */
    private void startWorld(Minecraft mc) {
        if (!mc.isDemo()) {
            worldResult = "skipped: not a demo session";
            phase = Phase.DONE;
            finish(mc);
            return;
        }
        LOG.info("Smoke test: creating a fresh demo world ({})", TEST_WORLD);
        // own folder, recreated every run; the player's Demo_World stays untouched
        if (mc.getLevelSource().levelExists(TEST_WORLD)) {
            try (var access = mc.getLevelSource().createAccess(TEST_WORLD)) {
                access.deleteLevel();
            } catch (Exception e) {
                LOG.warn("Smoke test: could not delete old test world", e);
            }
        }
        mc.createWorldOpenFlows().createFreshLevel(TEST_WORLD, MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS,
                WorldPresets::createNormalWorldDimensions, mainMenu);
        phase = Phase.WORLD_LOAD;
        ticks = 0;
    }

    private static void giveSword(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            }
        });
    }

    /**
     * Steps: per Lumora swing mode one swing with a screenshot mid-animation, then the pause menu (with the hub
     * button) and the hub opened from it. 20 ticks between steps.
     */
    private void inWorld(Minecraft mc) {
        SwingAnimation.Mode[] modes = SwingAnimation.Mode.values();
        if (mc.player == null) {
            worldResult = "failed: player disappeared";
            phase = Phase.DONE;
            finish(mc);
            return;
        }
        if (worldStep < modes.length) {
            SwingAnimation.Mode mode = modes[worldStep];
            if (ticks == 20) {
                LumoraClient.config().swingAnimation = mode.name().toLowerCase(java.util.Locale.ROOT);
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else if (ticks == 23) {
                screenshot(mc, "swing-" + mode.name().toLowerCase(java.util.Locale.ROOT));
                worldStep++;
                ticks = 0;
            }
            return;
        }
        if (worldStep == modes.length && zoomPhase < 4) {
            // zoom: same view without and with zoom (zoom key pressed virtually), then release
            if (zoomPhase == 0) {
                mc.setScreen(null);
                zoomPhase = 1;
                ticks = 0;
            } else if (zoomPhase == 1 && ticks >= 15) {
                screenshot(mc, "zoom-before");
                LumoraZoom.key().setDown(true);
                zoomPhase = 2;
                ticks = 0;
            } else if (zoomPhase == 2 && ticks >= 20) {
                screenshot(mc, "zoom");
                LumoraZoom.key().setDown(false);
                zoomPhase = 3;
                ticks = 0;
            } else if (zoomPhase == 3 && ticks >= 10) {
                zoomPhase = 4;
            }
            return;
        }
        if (worldStep == modes.length) {
            LumoraClient.config().swingAnimation = savedSwing;
            mc.setScreen(new PauseScreen(true));
            worldStep++;
            ticks = 0;
        } else if (worldStep == modes.length + 1 && ticks >= SCREEN_TICKS) {
            screenshot(mc, "pause");
            mc.setScreen(new LumoraHubScreen(mc.screen));
            worldStep++;
            ticks = 0;
        } else if (worldStep == modes.length + 2 && ticks >= SCREEN_TICKS) {
            screenshot(mc, "hub-ingame");
            mc.setScreen(null);
            // LabyVoice HUD on for the widget screenshot (singleplayer: nothing connects, no microphone)
            labyVoiceBefore = ApiSmoke.setLabyVoiceEnabled(true);
            worldStep++;
            ticks = 0;
        } else if (worldStep == modes.length + 3 && ticks >= SCREEN_TICKS * 2) {
            screenshot(mc, "hud-ingame");
            ApiSmoke.restoreLabyVoice(labyVoiceBefore);
            worldResult = "PASSED: demo world, " + modes.length + " swing modes, pause menu, hub, HUD";
            // look at the ground for the Block-Overlay screenshot
            if (mc.player != null) {
                mc.player.setXRot(70f);
                mc.player.xRotO = 70f;
            }
            worldStep++;
            ticks = 0;
        } else if (worldStep == modes.length + 4 && ticks >= SCREEN_TICKS) {
            screenshot(mc, "blockoverlay");
            phase = Phase.DONE;
            finish(mc);
        }
    }

    private void screenshot(Minecraft mc, String name) {
        // Same path as the F2 key.
        String file = report.getFileName().toString().replace(".json", "") + "-" + name + ".png";
        try {
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1,
                    msg -> LOG.info("Smoke test screenshot: {}", msg.getString()));
            screenshots.add(mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve(file).toString());
        } catch (RuntimeException e) {
            LOG.warn("Screenshot not possible with this renderer: {}", e.toString());
        }
    }

    private void finish(Minecraft mc) {
        Map<String, Object> data = Diagnostics.collect();
        List<String> problems = new ArrayList<>(ApiSmoke.check());
        problems.addAll(extraProblems);
        problems.addAll(dev.lumora.core.social.SocialSmoke.problems(ApiSmoke.core().social()));
        data.put("smokeTest", problems.isEmpty() ? "PASSED: main menu reached, Lumora API checked" : "FAILED: " + problems);
        data.put("lumoraApi", ApiSmoke.report());
        data.put("mainMenu", menuName);
        data.put("titleScreenFrameStats", summary == null ? null : summary.format());
        data.put("screenshots", screenshots);
        data.put("inWorld", worldResult);
        data.put("social", dev.lumora.core.social.SocialSmoke.result(ApiSmoke.core().social()));
        System.setProperty("lumora.diagnosticsDir", report.toAbsolutePath().getParent().toString());
        Diagnostics.write(report.getFileName().toString(), data);
        LOG.info("Smoke test finished, stopping client");
        mc.stop();
    }
}
