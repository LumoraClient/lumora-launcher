package dev.lumora.client.gui;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.input.Keybind;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.diagnostics.Diagnostics;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.client.integration.RuntimeInfo;
import dev.lumora.core.hub.HubHost;
import dev.lumora.core.hub.HubView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Lumora hub (Right Shift in game, "✦ Lumora" in the main and pause menu) on Minecraft 1.21.8: the hub itself is
 * {@link HubView} in the core (same look on every version); this class connects it to the client settings
 * (config/lumora-client.json) and this version's screens.
 */
public final class LumoraHubScreen {
    private LumoraHubScreen() {
    }

    /** The hub as a screen that returns to {@code parent} (null: back to the game). */
    public static net.minecraft.client.gui.screens.Screen create(net.minecraft.client.gui.screens.Screen parent) {
        Host host = new Host();
        HubView view = new HubView(LumoraRuntime.core(), host);
        host.view = view;
        return AddonViewScreen.create(parent, view);
    }

    /** Hub opened on a page (0 modules, 1 themes, 2 zoom, 3 profiles, 4 system); used by the smoke test. */
    public static net.minecraft.client.gui.screens.Screen withTab(net.minecraft.client.gui.screens.Screen parent, int index) {
        HubView.select(index);
        return create(parent);
    }

    private static final class Host implements HubHost {
        private HubView view;

        private static net.minecraft.client.Minecraft mc() {
            return net.minecraft.client.Minecraft.getInstance();
        }

        private static net.minecraft.client.gui.screens.Screen current() {
            return mc().screen;
        }

        private static void show(net.minecraft.client.gui.screens.Screen screen) {
            AddonViewScreen.keepCurrent();
            mc().setScreen(screen);
        }

        private static LumoraConfig cfg() {
            return LumoraClient.config();
        }

        private static Field field(String option) {
            try {
                Field f = LumoraConfig.class.getField(option);
                return Modifier.isStatic(f.getModifiers()) ? null : f;
            } catch (NoSuchFieldException e) {
                return null;
            }
        }

        @Override
        public String versionLine() {
            return "Minecraft " + Diagnostics.profile() + " · Java " + Runtime.version().feature();
        }

        @Override
        public boolean inWorld() {
            return mc().level != null;
        }

        @Override
        public boolean has(String option) {
            return cfg() != null && field(option) != null;
        }

        @Override
        public Object get(String option) {
            Field f = field(option);
            try {
                return f == null || cfg() == null ? null : f.get(cfg());
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        @Override
        public void set(String option, Object value) {
            Field f = field(option);
            LumoraConfig c = cfg();
            if (f == null || c == null || value == null) {
                return;
            }
            try {
                Class<?> type = f.getType();
                if (type == boolean.class && value instanceof Boolean b) {
                    f.setBoolean(c, b);
                } else if (type == int.class && value instanceof Number n) {
                    f.setInt(c, n.intValue());
                } else if (type == double.class && value instanceof Number n) {
                    f.setDouble(c, n.doubleValue());
                } else if (type == String.class) {
                    f.set(c, String.valueOf(value));
                } else {
                    return;
                }
            } catch (IllegalAccessException e) {
                return;
            }
            c.validate();
            if ("lumoraFont".equals(option)) {
                LumoraFont.apply(mc(), c.lumoraFont);
            } else if ("accentColor".equals(option)) {
                LumoraUi.applyAccent(c.accentColor);
            }
            c.save(dev.lumora.client.platform.LoaderEnv.configDir().resolve("lumora-client.json"));
        }

        @Override
        public boolean fontSupported() {
            return true;
        }

        @Override
        public String zoomKey() {
            return dev.lumora.client.zoom.LumoraZoom.keyName().getString();
        }

        @Override
        public void openHudEditor() {
            show(new HudEditorScreen(current()));
        }

        @Override
        public void openSettings(String title, SettingsGroup settings, List<Keybind> keybinds, Runnable onClose) {
            show(new SettingsScreen(current(), net.minecraft.network.chat.Component.literal(title), settings, keybinds, onClose));
        }

        @Override
        public boolean classicMenuUntilRestart() {
            return LumoraTitleScreen.classicUntilRestart;
        }

        @Override
        public void showMainMenu(boolean lumora) {
            LumoraTitleScreen.classicUntilRestart = !lumora;
            mc().setScreen(lumora ? new LumoraTitleScreen() : new net.minecraft.client.gui.screens.TitleScreen());
        }

        @Override
        public String renderer() {
            Map<String, String> gpu = RuntimeInfo.gpuDevice();
            return gpu.getOrDefault("backend", "OpenGL") + (RuntimeInfo.sodiumLoaded() ? " · Sodium "
                    + RuntimeInfo.modVersion("sodium").orElse("?").replaceAll("\\+.*", "") : "");
        }

        @Override
        public String gpu() {
            return RuntimeInfo.gpuDevice().getOrDefault("renderer", "?");
        }

        @Override
        public String protocol() {
            if (!RuntimeInfo.viaFabricPlusLoaded()) {
                return null;
            }
            String target = RuntimeInfo.vfpTargetVersion();
            return (target != null ? target : "Auto") + " · ViaFabricPlus";
        }

        @Override
        public boolean openProtocol() {
            if (!RuntimeInfo.viaFabricPlusLoaded()) {
                return false;
            }
            AddonViewScreen.keepCurrent();
            return RuntimeInfo.openVfpProtocolSelection(current());
        }

        @Override
        public void startBenchmark() {
            LumoraClient.startBenchmark();
            close();
        }

        @Override
        public String writeDiagnostics() {
            return Diagnostics.write("game-" + Diagnostics.profile() + "-latest.json", Diagnostics.collect()).getFileName().toString();
        }

        @Override
        public Path diagnosticsDirectory() {
            return Diagnostics.directory();
        }

        @Override
        public void openFolder(Path dir) throws java.io.IOException {
            net.minecraft.Util.getPlatform().openPath(dir);
        }

        @Override
        public void close() {
            AddonViewScreen.close(view);
        }
    }
}
