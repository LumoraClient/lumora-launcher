package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.diagnostics.SystemCheck;
import dev.lumora.launcher.core.perf.GraphicsPresets;
import dev.lumora.launcher.core.profile.Flavor;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.stats.BenchmarkHistory;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * "Performance": system at a glance, graphics profiles for the selected version's options.txt and the Java/memory
 * settings that matter for FPS. No performance mods: Lumora runs without a mod loader.
 */
public final class PerformancePage extends JPanel {
    private final PageHost host;
    private final ExecutorService io;
    private final JPanel content = Ui.column();
    private final JLabel cpu = Ui.label("…", 15, Font.BOLD, Theme.TEXT);
    private final JLabel ram = Ui.label("…", 15, Font.BOLD, Theme.TEXT);
    private final JLabel gpu = Ui.label("…", 15, Font.BOLD, Theme.TEXT);
    private final JLabel javaLabel = Ui.label("…", 15, Font.BOLD, Theme.TEXT);
    private long totalRamMb;

    public PerformancePage(PageHost host, ExecutorService io) {
        super(new BorderLayout());
        this.host = host;
        this.io = io;
        setOpaque(false);
        add(Ui.scroll(content), BorderLayout.CENTER);
    }

    /** Rebuilds for the selected version and flavor; the system check runs in the background. */
    public void refresh() {
        content.removeAll();
        ProfileCatalog.Profile p = host.ctx().selectedProfile();
        Flavor flavor = Flavor.of(host.ctx().config.selectedFlavor);
        Path gameDir = host.ctx().gameDir(flavor.instanceId(p.id));

        content.add(Ui.pageTitle("Performance", "Mehr FPS für Minecraft " + p.minecraftVersion + " (" + flavor.label()
                + "): Grafik-Profile, Performance-Mods und Java-Einstellungen."));
        content.add(Box.createVerticalStrut(18));

        JPanel tiles = new JPanel(new GridLayout(1, 4, 14, 0));
        tiles.setOpaque(false);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        tiles.add(tile(Icons.Kind.CHIP, "Prozessor", cpu));
        tiles.add(tile(Icons.Kind.MEMORY, "Arbeitsspeicher", ram));
        tiles.add(tile(Icons.Kind.MONITOR, "Grafikkarte", gpu));
        tiles.add(tile(Icons.Kind.CUBE, "Java", javaLabel));
        tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        content.add(tiles);
        cpu.setText(Runtime.getRuntime().availableProcessors() + " Threads");
        javaLabel.setText(p.javaComponent != null && flavor == Flavor.LUMORA ? javaName(p.javaComponent) : "von Mojang");
        io.submit(() -> {
            SystemCheck.Report r = SystemCheck.run(host.ctx().os, host.ctx().paths);
            SwingUtilities.invokeLater(() -> {
                totalRamMb = r.totalRamMb();
                gpu.setText(r.gpus().isEmpty() ? "unbekannt" : r.gpus().getFirst().replaceAll(" \\(Treiber.*", ""));
                gpu.setToolTipText(String.join(", ", r.gpus()));
                ram.setText(String.format(Locale.GERMANY, "%.1f von %d GB", host.ctx().config.maxMemoryMb / 1024.0,
                        Math.round(r.totalRamMb() / 1024.0)));
            });
        });

        // graphics profiles
        content.add(Box.createVerticalStrut(24));
        content.add(Ui.sectionTitle("Grafik-Profil", "Ändert die Video-Einstellungen in options.txt dieser Version. "
                + "Das Original wird vorher gesichert."));
        GraphicsPresets.Preset active = GraphicsPresets.current(gameDir);
        boolean hasOptions = Files.isRegularFile(gameDir.resolve("options.txt"));
        JPanel presets = new JPanel(new GridLayout(1, 3, 14, 0));
        presets.setOpaque(false);
        presets.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (GraphicsPresets.Preset preset : GraphicsPresets.Preset.values()) {
            presets.add(presetCard(preset, preset == active, hasOptions, gameDir));
        }
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        content.add(presets);
        JPanel under = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        under.setOpaque(false);
        under.setAlignmentX(Component.LEFT_ALIGNMENT);
        under.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        if (!hasOptions) {
            under.add(Ui.label("Starte Minecraft " + p.minecraftVersion + " (" + flavor.label() + ") einmal, dann lassen sich die Profile anwenden.",
                    12.5f, Font.PLAIN, Theme.WARN));
        } else if (Files.isRegularFile(gameDir.resolve(GraphicsPresets.BACKUP))) {
            JButton restore = new JButton("Ursprüngliche Einstellungen wiederherstellen", Icons.of(Icons.Kind.REFRESH, 14, Theme.TEXT));
            restore.setFocusable(false);
            restore.addActionListener(e -> {
                try {
                    GraphicsPresets.restore(gameDir);
                } catch (Exception ex) {
                    host.error("Wiederherstellen", ex.getMessage());
                }
                refresh();
            });
            under.add(restore);
        }
        under.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        content.add(under);

        // java and memory
        content.add(Box.createVerticalStrut(24));
        content.add(Ui.sectionTitle("Java & Speicher", null));
        Card jm = new Card(new GridLayout(0, 1, 0, 14));
        jm.setAlignmentX(Component.LEFT_ALIGNMENT);
        ToggleSwitch flags = new ToggleSwitch(host.ctx().config.performanceFlags);
        flags.onChange(on -> {
            host.ctx().config.performanceFlags = on;
            host.saveConfig();
        });
        jm.add(settingRow(Icons.Kind.BOLT, "Performance-Verbesserungen", "G1-Garbage-Collector mit kurzen Pausen gegen Ruckler beim Laden.", flags));
        JButton recommend = new JButton("Empfohlen übernehmen");
        recommend.setFocusable(false);
        recommend.addActionListener(e -> {
            long total = totalRamMb > 0 ? totalRamMb : 8192;
            int mb = total >= 16384 ? 6144 : total >= 12288 ? 5120 : total >= 8192 ? 4096 : 3072;
            host.ctx().config.maxMemoryMb = mb;
            host.ctx().config.minMemoryMb = Math.min(host.ctx().config.minMemoryMb, mb);
            host.saveConfig();
            JOptionPane.showMessageDialog(host.frame(), String.format(Locale.GERMANY, "Minecraft bekommt jetzt %.1f GB Arbeitsspeicher.", mb / 1024.0),
                    "Performance", JOptionPane.INFORMATION_MESSAGE);
            refresh();
        });
        jm.add(settingRow(Icons.Kind.MEMORY, "Arbeitsspeicher", String.format(Locale.GERMANY,
                "Zugewiesen: %.1f GB. Empfohlen sind 4–6 GB; mehr bringt meist keine FPS.", host.ctx().config.maxMemoryMb / 1024.0), recommend));
        List<BenchmarkHistory.Run> runs = BenchmarkHistory.load(host.ctx().paths.diagnostics(), p.id);
        if (!runs.isEmpty()) {
            BenchmarkHistory.Run last = runs.getLast();
            jm.add(settingRow(Icons.Kind.CHART, "Letzter Benchmark", String.format(Locale.GERMANY, "%.0f FPS im Schnitt, 1%%-Low %.0f FPS (%s)",
                    last.avgFps(), last.onePercentLowFps(), last.renderer() == null ? "" : last.renderer()), new JLabel()));
        }
        jm.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        content.add(jm);
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private JComponent presetCard(GraphicsPresets.Preset preset, boolean active, boolean enabled, Path gameDir) {
        Card c = new Card(new BorderLayout(0, 10));
        if (active) {
            c.outline(Theme.ACCENT);
        }
        JPanel t = Ui.column();
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        title.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.add(new JLabel(Icons.of(preset == GraphicsPresets.Preset.MAX_FPS ? Icons.Kind.BOLT
                : preset == GraphicsPresets.Preset.BALANCED ? Icons.Kind.SLIDERS : Icons.Kind.IMAGE, 18, Theme.ACCENT_HOVER)));
        title.add(Ui.label(preset.label, 15, Font.BOLD, Theme.TEXT));
        if (active) {
            title.add(Ui.chip("aktiv"));
        }
        t.add(title);
        t.add(Box.createVerticalStrut(6));
        t.add(Ui.wrapText(preset.description, 12.5f, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        JButton apply = new JButton(active ? "Angewendet" : "Anwenden");
        apply.setFocusable(false);
        apply.setEnabled(enabled && !active);
        apply.addActionListener(e -> {
            if (host.gameRunning()) {
                JOptionPane.showMessageDialog(host.frame(), "Bitte zuerst Minecraft beenden – das Spiel überschreibt options.txt beim Schließen.",
                        "Grafik-Profil", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                List<String> changed = GraphicsPresets.apply(gameDir, preset);
                Log.info("Graphics preset " + preset + " applied to " + gameDir + ": " + changed);
            } catch (Exception ex) {
                host.error("Grafik-Profil", ex.getMessage());
            }
            refresh();
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.add(apply);
        c.add(south, BorderLayout.SOUTH);
        return c;
    }

    private static JComponent settingRow(Icons.Kind icon, String title, String desc, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.add(new JLabel(Icons.of(icon, 20, Theme.TEXT)), BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(title, 14.5f, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(2));
        t.add(Ui.label(desc, 12.5f, Font.PLAIN, Theme.MUTED));
        row.add(t, BorderLayout.CENTER);
        JPanel east = new JPanel(new java.awt.GridBagLayout());
        east.setOpaque(false);
        east.add(control);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private static JComponent tile(Icons.Kind icon, String title, JLabel value) {
        Card c = new Card(new BorderLayout(12, 0));
        c.padding(14, 16, 14, 16);
        JLabel i = new JLabel(Icons.of(icon, 22, Theme.ACCENT_HOVER));
        i.setVerticalAlignment(JLabel.TOP);
        c.add(i, BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(title.toUpperCase(Locale.GERMANY), 11, Font.BOLD, Theme.MUTED));
        t.add(Box.createVerticalStrut(4));
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.add(value);
        c.add(t, BorderLayout.CENTER);
        return c;
    }

    private static String javaName(String component) {
        return switch (component) {
            case "jre-legacy" -> "Java 8";
            case "java-runtime-alpha" -> "Java 16";
            case "java-runtime-beta", "java-runtime-gamma", "java-runtime-gamma-snapshot" -> "Java 17";
            case "java-runtime-delta" -> "Java 21";
            case "java-runtime-epsilon" -> "Java 25";
            default -> component;
        };
    }
}
