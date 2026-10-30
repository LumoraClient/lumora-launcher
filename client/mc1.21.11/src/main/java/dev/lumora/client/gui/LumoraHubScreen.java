package dev.lumora.client.gui;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.anim.SwingAnimation;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.diagnostics.Diagnostics;
import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.client.integration.RuntimeInfo;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.addon.AddonRecord;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lumora mod hub (Right Shift in game, "✦ Lumora" in the main and pause menu), in the launcher's design:
 * <ul>
 *   <li>Module: every registered HUD widget (built-in and from addons) on/off, HUD profile, HUD editor,</li>
 *   <li>Einstellungen: menu transitions, swing animation, font, effects, loading screen,</li>
 *   <li>Zoom: zoom settings,</li>
 *   <li>Addons: installed Lumora addons with state, on/off, settings and errors,</li>
 *   <li>System: renderer, GPU, protocol, memory, benchmark and diagnostics.</li>
 * </ul>
 */
public final class LumoraHubScreen extends Screen {
    private enum Tab { MODULES, SETTINGS, ZOOM, ADDONS, SYSTEM }

    private static final int[] DURATIONS = {150, 220, 350, 500};
    private static final double[] ZOOM_FACTORS = {2, 3, 4, 6, 8, 10};
    private static Tab tab = Tab.MODULES;
    private static int modulePage;
    private static int addonPage;
    private final Screen parent;
    private Component status = Component.empty();
    /** Addons of the current page; their texts are drawn in the background pass. */
    private final java.util.List<AddonInfo> addonRows = new java.util.ArrayList<>();
    private int addonRowsY;

    /** Hub opened on a given tab (0 modules, 1 settings, 2 zoom, 3 addons, 4 system); used by the smoke test. */
    public static LumoraHubScreen withTab(Screen parent, int index) {
        tab = Tab.values()[Math.clamp(index, 0, Tab.values().length - 1)];
        return new LumoraHubScreen(parent);
    }

    public LumoraHubScreen(Screen parent) {
        super(Component.translatable("lumora.hub.title"));
        this.parent = parent;
    }

    private int panelWidth() {
        return Math.min(460, width - 32);
    }

    private int panelHeight() {
        return Math.min(270, height - 16);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int top() {
        return Math.max(8, (height - panelHeight()) / 2);
    }

    private boolean compact() {
        return panelHeight() < 256;
    }

    private static LumoraConfig cfg() {
        return LumoraClient.config();
    }

    private static void saveCfg() {
        cfg().save(FabricLoader.getInstance().getConfigDir().resolve("lumora-client.json"));
    }

    @Override
    protected void init() {
        int pw = panelWidth();
        int x = left() + 16;
        int y = top() + 40;
        // tabs
        Tab[] tabs = Tab.values();
        int tw = (pw - 32 - 6 * (tabs.length - 1)) / tabs.length;
        String[] keys = {"lumora.hub.tab.modules", "lumora.hub.tab.settings", "lumora.hub.tab.zoom", "lumora.hub.tab.addons", "lumora.hub.tab.system"};
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(new LumoraButton(x + i * (tw + 6), y, tw, 18, Component.translatable(keys[i]),
                    t == tab ? LumoraButton.Style.PRIMARY : LumoraButton.Style.SECONDARY, null, () -> {
                        tab = t;
                        rebuildWidgets();
                    }));
        }
        int contentY = y + 26;
        switch (tab) {
            case MODULES -> initModules(x, contentY, pw - 32);
            case SETTINGS -> initSettings(x, contentY, pw - 32);
            case ZOOM -> initZoom(x, contentY, pw - 32);
            case ADDONS -> initAddons(x, contentY, pw - 32);
            case SYSTEM -> initSystem(x, contentY + (compact() ? 88 : 96), pw - 32);
        }
        addRenderableWidget(new LumoraButton(left() + pw - 16 - 96, top() + panelHeight() - 28, 96, 20, Component.translatable("gui.done"),
                LumoraButton.Style.PRIMARY, null, this::onClose));
    }

    /** All registered widgets (from the widget registry, so addon widgets appear automatically), paged. */
    private void initModules(int x, int y, int w) {
        LumoraCore core = LumoraRuntime.core();
        List<WidgetInfo> all = core.widgets();
        int cols = 3;
        int bw = (w - (cols - 1) * 6) / cols;
        int bh = compact() ? 18 : 20;
        int rowsFit = Math.max(1, (panelHeight() - 66 - 26 - 32 - 30) / (bh + 4));
        int perPage = rowsFit * cols;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        modulePage = Math.min(modulePage, pages - 1);
        int start = modulePage * perPage;
        for (int i = start; i < Math.min(all.size(), start + perPage); i++) {
            WidgetInfo m = all.get(i);
            int bx = x + ((i - start) % cols) * (bw + 6);
            int by = y + ((i - start) / cols) * (bh + 4);
            String label = (m.isEnabled() ? "● " : "○ ") + Component.translatable(m.widget().nameKey()).getString();
            LumoraButton b = new LumoraButton(bx, by, bw, bh, Component.literal(label),
                    m.isEnabled() ? LumoraButton.Style.PRIMARY : LumoraButton.Style.SECONDARY, null, () -> {
                        m.setEnabled(!m.isEnabled());
                        rebuildWidgets();
                    });
            if (!m.addonId().equals("lumora")) {
                b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("lumora.hub.modules.from",
                        core.addon(m.addonId()).map(a -> a.descriptor().name()).orElse(m.addonId()))));
            }
            addRenderableWidget(b);
        }
        int by = y + rowsFit * (bh + 4) + 4;
        int third = (w - 12) / 3;
        addRenderableWidget(new LumoraButton(x, by, third, 20, Component.translatable("lumora.hud.edit"), LumoraButton.Style.PRIMARY, "✥",
                () -> minecraft.setScreen(new HudEditorScreen(this))));
        String profile = core.hudEditor().activeProfile();
        String key = "lumora.hud.profile." + profile;
        String profileName = net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key).getString() : profile;
        addRenderableWidget(new LumoraButton(x + third + 6, by, third, 20, Component.translatable("lumora.hud.profile", profileName),
                LumoraButton.Style.SECONDARY, null, () -> {
                    List<String> ids = core.hudEditor().profiles();
                    core.hudEditor().activateProfile(ids.get((ids.indexOf(profile) + 1) % ids.size()));
                    rebuildWidgets();
                }));
        if (pages > 1) {
            int px = x + 2 * (third + 6);
            LumoraButton prev = new LumoraButton(px, by, 24, 20, Component.literal("‹"), LumoraButton.Style.SECONDARY, null, () -> {
                modulePage--;
                rebuildWidgets();
            });
            prev.active = modulePage > 0;
            addRenderableWidget(prev);
            LumoraButton next = new LumoraButton(px + third - 24, by, 24, 20, Component.literal("›"), LumoraButton.Style.SECONDARY, null, () -> {
                modulePage++;
                rebuildWidgets();
            });
            next.active = modulePage < pages - 1;
            addRenderableWidget(next);
        }
    }

    /** Installed addons: state, on/off, settings (addon settings + keybinds) and the error if one failed. */
    private void initAddons(int x, int y, int w) {
        LumoraCore core = LumoraRuntime.core();
        List<AddonInfo> all = core.addons();
        int rowH = compact() ? 26 : 30;
        int perPage = Math.max(1, (panelHeight() - 66 - 36 - 22) / rowH);
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        addonPage = Math.min(addonPage, pages - 1);
        addonRows.clear();
        addonRowsY = y;
        for (int i = addonPage * perPage; i < Math.min(all.size(), (addonPage + 1) * perPage); i++) {
            AddonInfo a = all.get(i);
            addonRows.add(a);
            int ry = y + (i - addonPage * perPage) * rowH;
            int bx = x + w - 66;
            boolean failed = a.state() == AddonState.FAILED;
            LumoraButton toggle = new LumoraButton(bx, ry + 2, 66, 18, Component.translatable(a.wanted() ? "options.on" : "options.off"),
                    a.wanted() && !failed ? LumoraButton.Style.PRIMARY : LumoraButton.Style.SECONDARY, null, () -> {
                        core.addonManager().setWanted(a.id(), !a.wanted()).ifPresent(err -> status = Component.literal(err));
                        rebuildWidgets();
                    });
            toggle.active = !failed || a.wanted();
            addRenderableWidget(toggle);
            AddonRecord r = core.addonManager().record(a.id()).orElse(null);
            boolean hasSettings = r != null && r.configuration() != null && (!r.configuration().settings().isEmpty()
                    || !core.keybindManager().of(a.id()).isEmpty());
            if (hasSettings) {
                addRenderableWidget(new LumoraButton(bx - 24, ry + 2, 20, 18, Component.literal("⚙"), LumoraButton.Style.SECONDARY, null,
                        () -> minecraft.setScreen(new SettingsScreen(this, Component.literal(a.descriptor().name()),
                                r.configuration().settings(), core.keybindManager().of(a.id()), r.configuration()::save))));
            }
            if (core.views().hasMenu(a.id())) {
                // the addon's main menu (Lumora API 1.1), also when its key is unbound
                addRenderableWidget(new LumoraButton(bx - (hasSettings ? 48 : 24), ry + 2, 20, 18, Component.literal("\u25B6"), LumoraButton.Style.SECONDARY, null,
                        () -> core.views().openMenu(a.id())));
            }
        }
        if (pages > 1) {
            int py = y + perPage * rowH + 2;
            LumoraButton prev = new LumoraButton(x, py, 24, 18, Component.literal("‹"), LumoraButton.Style.SECONDARY, null, () -> {
                addonPage--;
                rebuildWidgets();
            });
            prev.active = addonPage > 0;
            addRenderableWidget(prev);
            LumoraButton next = new LumoraButton(x + 28, py, 24, 18, Component.literal("›"), LumoraButton.Style.SECONDARY, null, () -> {
                addonPage++;
                rebuildWidgets();
            });
            next.active = addonPage < pages - 1;
            addRenderableWidget(next);
        }
    }

    private void drawAddons(GuiGraphics g, int x, int w) {
        int rowH = compact() ? 26 : 30;
        if (addonRows.isEmpty()) {
            g.drawString(font, Component.translatable("lumora.hub.addons.none"), x, addonRowsY + 4, LumoraUi.MUTED, false);
        }
        for (int i = 0; i < addonRows.size(); i++) {
            AddonInfo a = addonRows.get(i);
            int ry = addonRowsY + i * rowH;
            LumoraShaders.glass(g, x, ry, w, rowH - 4, 0xC0221C2D);
            String title = a.descriptor().name() + "  " + a.descriptor().version();
            g.drawString(font, title, x + 8, ry + 4, LumoraUi.TEXT, false);
            int stateColor = switch (a.state()) {
                case ENABLED -> LumoraUi.OK;
                case FAILED -> 0xFFF2555A;
                default -> LumoraUi.MUTED;
            };
            String state = Component.translatable("lumora.addon.state." + a.state().name().toLowerCase(Locale.ROOT)).getString();
            g.drawString(font, state, x + 12 + font.width(title), ry + 4, stateColor, false);
            String line = a.error().orElse(a.descriptor().description().isEmpty() ? a.descriptor().source() : a.descriptor().description());
            int max = w - 8 - 100;
            while (font.width(line) > max && line.length() > 4) {
                line = line.substring(0, line.length() - 2);
            }
            g.drawString(font, line, x + 8, ry + 14, a.error().isPresent() ? 0xFFF5B342 : LumoraUi.MUTED, false);
        }
    }

    private void initSettings(int x, int y, int w) {
        int bw = (w - 6) / 2;
        int bh = compact() ? 18 : 20;
        int step = bh + 4;
        LumoraConfig c = cfg();
        String style = switch (c.transitionStyle) {
            case "slide" -> "lumora.hub.set.style.slide";
            case "off" -> "lumora.hub.set.style.off";
            default -> "lumora.hub.set.style.fade";
        };
        setting(x, y, bw, bh, Component.translatable("lumora.hub.set.transition", Component.translatable(style)), () -> {
            c.transitionStyle = switch (c.transitionStyle) {
                case "fade" -> "slide";
                case "slide" -> "off";
                default -> "fade";
            };
        });
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.set.duration", c.transitionMillis), () -> {
            int next = DURATIONS[0];
            for (int i = 0; i < DURATIONS.length; i++) {
                if (DURATIONS[i] == c.transitionMillis) {
                    next = DURATIONS[(i + 1) % DURATIONS.length];
                    break;
                }
            }
            c.transitionMillis = next;
        });
        y += step;
        setting(x, y, bw, bh, Component.translatable("lumora.hub.set.ingame", onOff(c.transitionInGame)), () -> c.transitionInGame = !c.transitionInGame);
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.swing", SwingAnimation.Mode.parse(c.swingAnimation).displayName()), () ->
                c.swingAnimation = SwingAnimation.Mode.parse(c.swingAnimation).next().name().toLowerCase(Locale.ROOT));
        y += step;
        setting(x, y, bw, bh, Component.translatable("lumora.hub.set.font", onOff(c.lumoraFont)), () -> {
            c.lumoraFont = !c.lumoraFont;
            LumoraFont.apply(minecraft, c.lumoraFont);
        });
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.set.shaders", onOff(!"off".equals(c.menuShaders))), () ->
                c.menuShaders = "off".equals(c.menuShaders) ? "auto" : "off");
        y += step;
        setting(x, y, bw, bh, Component.translatable("lumora.hub.set.loading", onOff(c.customLoadingScreen)), () -> c.customLoadingScreen = !c.customLoadingScreen);
        if (c.customMainMenu && minecraft.level == null) {
            if (LumoraTitleScreen.classicUntilRestart) {
                addRenderableWidget(new LumoraButton(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.lumoramenu"), LumoraButton.Style.SECONDARY, "✦",
                        () -> {
                            LumoraTitleScreen.classicUntilRestart = false;
                            minecraft.setScreen(new LumoraTitleScreen());
                        }));
            } else {
                addRenderableWidget(new LumoraButton(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.classicmenu"), LumoraButton.Style.SECONDARY, null,
                        () -> {
                            LumoraTitleScreen.classicUntilRestart = true;
                            minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                        }));
            }
        }
    }

    private void setting(int x, int y, int w, int h, Component label, Runnable change) {
        addRenderableWidget(new LumoraButton(x, y, w, h, label, LumoraButton.Style.SECONDARY, null, () -> {
            change.run();
            saveCfg();
            rebuildWidgets();
        }));
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private void initZoom(int x, int y, int w) {
        int bw = (w - 6) / 2;
        int bh = compact() ? 18 : 20;
        int step = bh + 4;
        LumoraConfig c = cfg();
        setting(x, y, bw, bh, Component.translatable("lumora.hub.zoom.enabled", onOff(c.zoomEnabled)), () -> c.zoomEnabled = !c.zoomEnabled);
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.zoom.factor", String.format(Locale.GERMANY, "%.0f", c.zoomFactor)), () -> {
            double next = ZOOM_FACTORS[0];
            for (int i = 0; i < ZOOM_FACTORS.length; i++) {
                if (ZOOM_FACTORS[i] >= c.zoomFactor - 0.01) {
                    next = ZOOM_FACTORS[(i + 1) % ZOOM_FACTORS.length];
                    break;
                }
            }
            c.zoomFactor = next;
        });
        y += step;
        setting(x, y, bw, bh, Component.translatable("lumora.hub.zoom.mode", Component.translatable("toggle".equals(c.zoomMode)
                ? "lumora.hub.zoom.mode.toggle" : "lumora.hub.zoom.mode.hold")), () -> c.zoomMode = "toggle".equals(c.zoomMode) ? "hold" : "toggle");
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.zoom.smooth", onOff(c.zoomSmooth)), () -> c.zoomSmooth = !c.zoomSmooth);
        y += step;
        setting(x, y, bw, bh, Component.translatable("lumora.hub.zoom.scroll", onOff(c.zoomScroll)), () -> c.zoomScroll = !c.zoomScroll);
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.zoom.sensitivity", onOff(c.zoomSensitivity)), () -> c.zoomSensitivity = !c.zoomSensitivity);
        y += step;
        setting(x, y, bw, bh, Component.translatable("lumora.hub.zoom.cinematic", onOff(c.zoomCinematic)), () -> c.zoomCinematic = !c.zoomCinematic);
        setting(x + bw + 6, y, bw, bh, Component.translatable("lumora.hub.zoom.vignette", onOff(c.zoomVignette)), () -> c.zoomVignette = !c.zoomVignette);
    }

    private void initSystem(int x, int y, int w) {
        int bw = (w - 8) / 2;
        int bh = compact() ? 18 : 20;
        int step = compact() ? 21 : 24;
        LumoraButton vfp = new LumoraButton(x, y, bw, bh, Component.translatable("lumora.hub.protocol"), LumoraButton.Style.SECONDARY, null,
                () -> {
                    if (!RuntimeInfo.openVfpProtocolSelection(this)) {
                        status = Component.translatable("lumora.hub.protocol.unavailable");
                    }
                });
        vfp.active = RuntimeInfo.viaFabricPlusLoaded();
        addRenderableWidget(vfp);
        addRenderableWidget(new LumoraButton(x + bw + 8, y, bw, bh,
                Component.translatable("lumora.hub.benchmark", cfg().benchmarkSeconds), LumoraButton.Style.SECONDARY, null,
                () -> {
                    LumoraClient.startBenchmark();
                    onClose();
                }));
        y += step;
        addRenderableWidget(new LumoraButton(x, y, bw, bh, Component.translatable("lumora.hub.diagnostics"), LumoraButton.Style.SECONDARY, null,
                () -> {
                    var file = Diagnostics.write("game-" + Diagnostics.profile() + "-latest.json", Diagnostics.collect());
                    status = Component.translatable("lumora.hub.diagnostics.written", file.getFileName().toString());
                }));
        addRenderableWidget(new LumoraButton(x + bw + 8, y, bw, bh, Component.translatable("lumora.hub.openfolder"), LumoraButton.Style.SECONDARY, null,
                () -> {
                    try {
                        java.nio.file.Files.createDirectories(Diagnostics.directory());
                        Util.getPlatform().openPath(Diagnostics.directory());
                    } catch (java.io.IOException e) {
                        status = Component.literal(e.getMessage());
                    }
                }));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
            LumoraShaders.sky(g, width, height, 0.8f);
        }
        g.fill(0, 0, width, height, LumoraUi.alpha(LumoraUi.BG, minecraft.level == null ? 0x70 : 0xB0));
        int x = left();
        int y = top();
        int pw = panelWidth();
        LumoraShaders.glow(g, x, y, pw, panelHeight(), 0x000000, 10);
        LumoraShaders.glass(g, x, y, pw, panelHeight(), 0xEE15111D);
        LumoraUi.logo(g, x + 16, y + 12, 20);
        LumoraUi.bigText(g, Component.literal("Lumora-Hub").withStyle(Style.EMPTY.withBold(true)), x + 42, y + 12, 1.4f, LumoraUi.TEXT);
        g.drawString(font, tr("profile") + ": " + Diagnostics.profile() + "  ·  Java " + Runtime.version().feature()
                + "  ·  " + Component.translatable("lumora.hub.hint").getString(), x + 42, y + 27, LumoraUi.MUTED, false);
        if (tab == Tab.SYSTEM) {
            int cw = (pw - 32 - 8) / 2;
            int cx = x + 16;
            int cy = y + 66;
            int row = compact() ? 42 : 46;
            String[] r = renderer();
            stat(g, cx, cy, cw, tr("renderer"), r[0], LumoraUi.TEXT);
            stat(g, cx + cw + 8, cy, cw, tr("gpu"), r[1], LumoraUi.TEXT);
            String target = RuntimeInfo.vfpTargetVersion();
            stat(g, cx, cy + row, cw, tr("protocol"), RuntimeInfo.viaFabricPlusLoaded()
                    ? (target != null ? target : tr("auto")) + " · ViaFabricPlus" : "–", LumoraUi.TEXT);
            long used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
            long max = Runtime.getRuntime().maxMemory() / 1048576;
            stat(g, cx + cw + 8, cy + row, cw, tr("memory"), used + " / " + max + " MB", used > max * 0.85 ? LumoraUi.WARN : LumoraUi.OK);
        } else if (tab == Tab.ZOOM) {
            g.drawWordWrap(font, Component.translatable("lumora.hub.zoom.hint", zoomKey()), x + 16, y + panelHeight() - 26, pw - 32 - 104, LumoraUi.MUTED, false);
        } else if (tab == Tab.MODULES) {
            g.drawWordWrap(font, Component.translatable("lumora.hub.modules.hint"), x + 16, y + panelHeight() - 26, pw - 32 - 104, LumoraUi.MUTED, false);
        } else if (tab == Tab.ADDONS) {
            drawAddons(g, x + 16, pw - 32);
            if (status.getString().isEmpty()) {
                g.drawWordWrap(font, Component.translatable("lumora.hub.addons.hint"), x + 16, y + panelHeight() - 26, pw - 32 - 104, LumoraUi.MUTED, false);
            }
        }
        if (!status.getString().isEmpty()) {
            g.drawString(font, status, x + 16, y + panelHeight() - 21, LumoraUi.ACCENT_HOVER, false);
        }
    }

    private void stat(GuiGraphics g, int x, int y, int w, String label, String value, int color) {
        int h = compact() ? 34 : 38;
        LumoraShaders.glass(g, x, y, w, h, 0xFF221C2D);
        LumoraUi.stat(g, label, value, color, x + 10, y + (h - 22) / 2, w - 20);
    }

    private static Component zoomKey() {
        return dev.lumora.client.zoom.LumoraZoom.keyName();
    }

    private static String tr(String key) {
        return Component.translatable("lumora.hub.card." + key).getString();
    }

    private static String[] renderer() {
        Map<String, String> gpu = RuntimeInfo.gpuDevice();
        return new String[]{gpu.getOrDefault("backend", "OpenGL") + (RuntimeInfo.sodiumLoaded() ? " · Sodium "
                + RuntimeInfo.modVersion("sodium").orElse("?").replaceAll("\\+.*", "") : ""), gpu.getOrDefault("renderer", "?")};
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
