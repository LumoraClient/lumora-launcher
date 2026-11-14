package dev.lumora.launcher.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.app.ui.Splash;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;
import dev.lumora.launcher.core.util.SingleInstance;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        List<String> a = Arrays.asList(args);
        if (a.contains("--version")) {
            System.out.println(BuildInfo.NAME + " Launcher " + BuildInfo.VERSION);
            return;
        }
        if (a.stream().anyMatch(s -> s.startsWith("--") && !s.equals("--gui") && !s.equals("--tray"))) {
            System.exit(Cli.run(args));
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> Log.error("Uncaught exception in " + t.getName(), e));
        // appearance must be known before AWT starts (hardware acceleration) and before the first window (accent)
        JsonObject look = peekConfig();
        if (look.has("hardwareAcceleration") && !look.get("hardwareAcceleration").getAsBoolean()) {
            System.setProperty("sun.java2d.d3d", "false");
            System.setProperty("sun.java2d.opengl", "false");
        }
        String accent = look.has("accentColor") ? look.get("accentColor").getAsString() : null;
        // Lumora pixel design: default since schema 3 (older settings are switched once, see LauncherConfig)
        boolean pixel = !look.has("pixelStyle") || look.get("pixelStyle").getAsBoolean()
                || (look.has("schemaVersion") && look.get("schemaVersion").getAsInt() < 3);
        boolean calm = look.has("reduceMotion") && look.get("reduceMotion").getAsBoolean();
        boolean tray = a.contains("--tray");
        SingleInstance instance = singleInstance(tray);
        Splash[] splash = new Splash[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                Theme.install(accent, pixel, calm);
                if (!tray) {
                    List<java.awt.Image> icons = LauncherWindow.icons();
                    splash[0] = new Splash(List.of("Vorbereiten …", "Einstellungen laden …", "Anmeldung prüfen …",
                            "Launcher einrichten …"), icons.isEmpty() ? null : icons.getLast());
                    splash[0].show();
                    splash[0].step(0);
                }
            });
        } catch (Exception e) {
            Log.error("Start screen failed", e);
        }
        Splash s = splash[0];
        AppContext ctx;
        try {
            step(s, 1);
            ctx = new AppContext();
            step(s, 2);
            try {
                // silent token refresh (no dialog); a failure only means the play button asks later
                ctx.play.freshSession(ctx.config);
            } catch (Exception e) {
                Log.info("No silent sign-in at start: " + e.getMessage());
            }
            step(s, 3);
        } catch (Exception e) {
            Log.error("Launcher could not start", e);
            if (s != null) {
                s.close();
            }
            JOptionPane.showMessageDialog(null, "Der Launcher konnte nicht gestartet werden:\n" + e.getMessage(),
                    BuildInfo.NAME, JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            // --tray: autostart with Windows, only the tray icon until the user opens the window
            LauncherWindow window = new LauncherWindow(ctx);
            window.show(tray);
            window.attach(instance);
            if (s != null) {
                s.close();
            }
        });
    }

    /**
     * Only one launcher per Lumora folder: if one is already running (for example in the tray after closing the
     * window), it is asked to show its window and this start ends. Null if the lock could not be used.
     */
    private static SingleInstance singleInstance(boolean tray) {
        Path root = LauncherPaths.defaultRoot(OsInfo.current());
        try {
            SingleInstance mine = SingleInstance.claim(root).orElse(null);
            if (mine != null) {
                return mine;
            }
            // an autostart while a launcher already runs leaves that one as it is
            SingleInstance.Request request = tray ? SingleInstance.Request.PING : SingleInstance.Request.SHOW;
            if (SingleInstance.signal(root, request, Duration.ofSeconds(10))) {
                System.exit(0);
            }
            mine = SingleInstance.claim(root).orElse(null);
            if (mine != null) {
                return mine; // the other launcher has just ended
            }
            int r = JOptionPane.showConfirmDialog(null, "Lumora läuft bereits, reagiert aber nicht.\nTrotzdem einen weiteren Launcher starten?",
                    BuildInfo.NAME, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        } catch (Exception e) {
            Log.warn("Single instance check failed: " + e.getMessage());
        }
        return null;
    }

    private static void step(Splash s, int i) {
        if (s != null) {
            s.step(i);
        }
    }

    /** Appearance values from launcher.json without loading the whole context; empty if missing or unreadable. */
    static JsonObject peekConfig() {
        try {
            Path file = new LauncherPaths(LauncherPaths.defaultRoot(OsInfo.current())).configFile();
            if (Files.isRegularFile(file) && Files.size(file) < 1_000_000) {
                return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            }
        } catch (Exception e) {
            // a broken file is repaired by ConfigStore later; defaults until then
        }
        return new JsonObject();
    }
}
