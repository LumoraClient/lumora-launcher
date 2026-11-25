package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.pack.LauncherImport;
import dev.lumora.launcher.core.util.Log;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * "Instanzen importieren" in three steps: where from (launchers found on this PC with the number of instances, or a
 * folder) → which instances (several at once, worlds optional) → copy with progress. The other launcher's folders are
 * only read.
 */
final class LauncherImportDialog {
    private final PageHost host;
    private final ExecutorService io;
    private final Consumer<Instance> done;
    private final JDialog dialog;
    private final CardLayout steps = new CardLayout();
    private final JPanel stepPanel = new JPanel(steps);
    private final JLabel[] stepLabels = new JLabel[3];
    private final JPanel sources = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JPanel list = Ui.column();
    private final Map<LauncherImport.Found, JCheckBox> picks = new LinkedHashMap<>();
    private final JCheckBox worlds = new JCheckBox(Lang.t("import.worlds"), true);
    private final JLabel status = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private JButton importButton;
    private Future<?> running;

    private LauncherImportDialog(PageHost host, ExecutorService io, Consumer<Instance> done) {
        this.host = host;
        this.io = io;
        this.done = done;
        dialog = new JDialog(host.frame(), Lang.t("import.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    }

    /** @param folder a folder to scan right away (dropped), or null for the source step */
    static void show(PageHost host, ExecutorService io, Path folder, Consumer<Instance> done) {
        LauncherImportDialog d = new LauncherImportDialog(host, io, done);
        d.build();
        if (folder != null) {
            d.scan(LauncherImport.Source.FOLDER, folder);
        } else {
            d.loadSources();
        }
        d.dialog.setVisible(true);
    }

    private void build() {
        Card c = new Card(new BorderLayout(0, 14)).fill(Theme.SURFACE).outline(null).radius(0).padding(18, 22, 18, 22);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.heading(Lang.t("import.title")), BorderLayout.WEST);
        JPanel stepper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        stepper.setOpaque(false);
        String[] keys = {"import.step.source", "import.step.choose", "import.step.import"};
        for (int i = 0; i < 3; i++) {
            stepLabels[i] = Ui.label((i + 1) + "  " + Lang.t(keys[i]), 13, Font.BOLD, Theme.MUTED);
            stepper.add(stepLabels[i]);
        }
        head.add(stepper, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);
        stepPanel.setOpaque(false);
        stepPanel.add(sourceStep(), "source");
        stepPanel.add(chooseStep(), "choose");
        c.add(stepPanel, BorderLayout.CENTER);
        dialog.setContentPane(c);
        dialog.setSize(820, 600);
        dialog.setLocationRelativeTo(host.frame());
        step(0);
    }

    private void step(int i) {
        for (int k = 0; k < 3; k++) {
            stepLabels[k].setForeground(k == i ? Theme.ACCENT_HOVER : k < i ? Theme.TEXT : Theme.MUTED);
        }
        steps.show(stepPanel, i == 0 ? "source" : "choose");
    }

    private JComponent sourceStep() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JPanel top = Ui.column();
        top.add(Ui.label(Lang.t("import.where"), 15, Font.BOLD, Theme.TEXT));
        top.add(Ui.wrapText(Lang.t("import.where.text"), 13, Theme.MUTED));
        p.add(top, BorderLayout.NORTH);
        sources.setOpaque(false);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(sources, BorderLayout.NORTH);
        p.add(Ui.scroll(wrap), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JPanel left = Ui.row(8);
        left.add(Ui.button(Lang.t("common.cancel"), null, dialog::dispose));
        left.add(Ui.button(Lang.t("import.scan_folder"), Icons.Kind.FOLDER, () -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                scan(LauncherImport.Source.FOLDER, fc.getSelectedFile().toPath());
            }
        }));
        south.add(left, BorderLayout.WEST);
        south.add(Ui.button(Lang.t("import.pack_file"), Icons.Kind.ARCHIVE, () -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(Lang.t("import.pack_filter"), "mrpack", "zip"));
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                dialog.dispose();
                ImportFlow.open(host, io, List.of(fc.getSelectedFile().toPath()), null, done);
            }
        }), BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void loadSources() {
        sources.removeAll();
        sources.add(Ui.label(Lang.t("import.searching"), 13, Font.PLAIN, Theme.MUTED));
        io.submit(() -> {
            Map<LauncherImport.Source, Path> known = LauncherImport.knownLocations();
            Map<LauncherImport.Source, List<LauncherImport.Found>> found = new LinkedHashMap<>();
            known.forEach((s, path) -> found.put(s, LauncherImport.scan(s, path)));
            SwingUtilities.invokeLater(() -> {
                sources.removeAll();
                if (found.values().stream().allMatch(List::isEmpty)) {
                    sources.add(Ui.wrapText(Lang.t("import.none_found"), 13, Theme.MUTED));
                }
                found.forEach((s, list) -> {
                    if (!list.isEmpty()) {
                        sources.add(sourceCard(s, list));
                    }
                });
                sources.revalidate();
                sources.repaint();
            });
        });
    }

    private JComponent sourceCard(LauncherImport.Source s, List<LauncherImport.Found> found) {
        Card c = new Card(new BorderLayout(10, 0)).padding(14, 16, 14, 16);
        JPanel t = Ui.column();
        t.add(Ui.label(Lang.t("import.source." + s.name().toLowerCase(Locale.ROOT)), 15, Font.BOLD, Theme.TEXT));
        t.add(Ui.label(Lang.plural("instances.count", found.size()), 12.5f, Font.PLAIN, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        c.add(new JLabel(Icons.of(Icons.Kind.CHEVRON_DOWN, 14, Theme.MUTED)), BorderLayout.EAST);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showFound(found);
            }
        };
        c.addMouseListener(click);
        t.addMouseListener(click);
        return c;
    }

    private void scan(LauncherImport.Source source, Path folder) {
        list.removeAll();
        list.add(Ui.label(Lang.t("import.searching"), 13, Font.PLAIN, Theme.MUTED));
        step(1);
        io.submit(() -> {
            List<LauncherImport.Found> found = LauncherImport.scan(source, folder);
            SwingUtilities.invokeLater(() -> showFound(found));
        });
    }

    private JComponent chooseStep() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Ui.scroll(list), BorderLayout.CENTER);
        JPanel south = Ui.column();
        worlds.setOpaque(false);
        south.add(worlds);
        bar.setVisible(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(bar);
        south.add(status);
        south.add(Box.createVerticalStrut(6));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(Ui.button(Lang.t("common.back"), null, () -> {
            if (running == null || running.isDone()) {
                loadSources();
                step(0);
            }
        }));
        buttons.add(Ui.button(Lang.t("common.cancel"), null, () -> {
            if (running != null && !running.isDone()) {
                running.cancel(true);
                status.setText(Lang.t("install.cancelling"));
            } else {
                dialog.dispose();
            }
        }));
        importButton = Ui.primary(Lang.t("import.import"), Icons.Kind.DOWNLOAD, this::runImport);
        buttons.add(importButton);
        south.add(buttons);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void showFound(List<LauncherImport.Found> found) {
        step(1);
        list.removeAll();
        picks.clear();
        if (found.isEmpty()) {
            list.add(Ui.wrapText(Lang.t("import.folder_empty"), 13, Theme.MUTED));
        }
        for (LauncherImport.Found f : found) {
            Card c = new Card(new BorderLayout(10, 0)).padding(10, 12, 10, 12);
            JCheckBox box = new JCheckBox();
            box.setOpaque(false);
            box.setSelected(found.size() == 1);
            box.addActionListener(e -> updateButton());
            picks.put(f, box);
            c.add(box, BorderLayout.WEST);
            JPanel t = Ui.column();
            t.add(Ui.label(f.name(), 14, Font.BOLD, Theme.TEXT));
            JLabel sub = Ui.label(f.kind().label() + (f.loaderVersion() == null ? "" : " " + f.loaderVersion()) + " · Minecraft "
                    + f.minecraftVersion() + " · " + Lang.t("import.source." + f.source().name().toLowerCase(Locale.ROOT)), 12, Font.PLAIN, Theme.MUTED);
            t.add(sub);
            t.add(Ui.label(f.gameDir().toString(), 11.5f, Font.PLAIN, Theme.MUTED));
            c.add(t, BorderLayout.CENTER);
            JLabel size = Ui.label("…", 12, Font.BOLD, Theme.MUTED);
            c.add(size, BorderLayout.EAST);
            io.submit(() -> {
                long bytes = LauncherImport.size(f, true);
                SwingUtilities.invokeLater(() -> size.setText(Ui.size(bytes)));
            });
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            list.add(c);
            list.add(Box.createVerticalStrut(6));
        }
        updateButton();
        list.revalidate();
        list.repaint();
    }

    private void updateButton() {
        long n = picks.values().stream().filter(JCheckBox::isSelected).count();
        importButton.setText(Lang.t("import.import_n", n));
        importButton.setEnabled(n > 0);
    }

    private void runImport() {
        List<LauncherImport.Found> chosen = new ArrayList<>();
        picks.forEach((f, b) -> {
            if (b.isSelected()) {
                chosen.add(f);
            }
        });
        boolean withWorlds = worlds.isSelected();
        importButton.setEnabled(false);
        bar.setVisible(true);
        stepLabels[2].setForeground(Theme.ACCENT_HOVER);
        running = io.submit(() -> {
            List<String> ok = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            Instance last = null;
            for (int k = 0; k < chosen.size(); k++) {
                LauncherImport.Found f = chosen.get(k);
                int index = k;
                try {
                    last = LauncherImport.importFound(f, f.name(), withWorlds, host.ctx().instances, (n, total) -> SwingUtilities.invokeLater(() -> {
                        bar.setValue(total == 0 ? 1000 : n * 1000 / total);
                        status.setText(Lang.t("import.progress", f.name(), index + 1, chosen.size(), n, total));
                    }));
                    ok.add(f.name());
                } catch (java.io.IOException | RuntimeException e) {
                    Log.warn("Import of " + f.name() + " failed: " + e.getMessage());
                    failed.add(f.name() + ": " + e.getMessage());
                    if (Thread.currentThread().isInterrupted() || e instanceof java.io.InterruptedIOException) {
                        break;
                    }
                }
            }
            Instance open = last;
            SwingUtilities.invokeLater(() -> {
                host.instancesChanged();
                dialog.dispose();
                if (!failed.isEmpty()) {
                    host.error(Lang.t("import.failed"), String.join("\n", failed));
                }
                if (!ok.isEmpty()) {
                    InstancesPage.info(host, Lang.plural("import.done", ok.size()));
                }
                if (open != null) {
                    done.accept(open);
                }
            });
        });
    }
}
