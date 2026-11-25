package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.pack.MrpackImporter;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Preview and import of a Modrinth modpack (.mrpack): what the pack contains and needs before anything is written,
 * the optional files to choose, a name, then the import into a new, independent instance with progress and cancel.
 */
final class MrpackDialog {
    private MrpackDialog() {
    }

    /**
     * @param sourceProject Modrinth project of the pack when it came from Entdecken (null for a local file)
     */
    static void show(PageHost host, ExecutorService io, Path file, String sourceProject, String sourceVersion, Consumer<Instance> done) {
        JDialog d = new JDialog(host.frame(), Lang.t("mrpack.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        Card c = new Card(new BorderLayout(0, 14)).fill(Theme.SURFACE).outline(null).radius(0).padding(18, 22, 18, 22);
        c.add(Ui.heading(Lang.t("mrpack.title")), BorderLayout.NORTH);
        JPanel center = Ui.column();
        center.add(Ui.label(Lang.t("mrpack.analyzing", file.getFileName()), 13, Font.PLAIN, Theme.MUTED));
        c.add(Ui.scroll(center), BorderLayout.CENTER);
        JLabel status = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setVisible(false);
        JButton cancel = Ui.button(Lang.t("common.cancel"), null, null);
        JButton ok = Ui.primary(Lang.t("mrpack.import"), Icons.Kind.DOWNLOAD, null);
        ok.setEnabled(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        JPanel south = Ui.column();
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(bar);
        south.add(status);
        south.add(Box.createVerticalStrut(6));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(buttons);
        c.add(south, BorderLayout.SOUTH);
        d.setContentPane(c);
        d.setSize(720, 620);
        d.setLocationRelativeTo(host.frame());

        JTextField name = new JTextField();
        Map<String, JCheckBox> optional = new LinkedHashMap<>();
        MrpackImporter.Preview[] preview = new MrpackImporter.Preview[1];
        java.util.concurrent.atomic.AtomicReference<Future<?>> running = new java.util.concurrent.atomic.AtomicReference<>();
        cancel.addActionListener(e -> {
            if (running.get() != null && !running.get().isDone()) {
                running.get().cancel(true);
                status.setText(Lang.t("mrpack.cancelling"));
            } else {
                d.dispose();
            }
        });
        io.submit(() -> {
            try {
                // a Modrinth pack (.mrpack) or a CurseForge pack (zip with manifest.json, needs the player's own key)
                MrpackImporter.Preview p = dev.lumora.launcher.core.pack.CurseForgePackImporter.isCurseForgePack(file)
                        ? dev.lumora.launcher.core.pack.CurseForgePackImporter.analyze(file, curseForge(host))
                        : MrpackImporter.analyze(file);
                SwingUtilities.invokeLater(() -> {
                    preview[0] = p;
                    center.removeAll();
                    JCheckBox skipManual = fill(center, p, name, optional);
                    center.revalidate();
                    center.repaint();
                    ok.setEnabled(p.importable() && skipManual == null);
                    if (skipManual != null) {
                        skipManual.addActionListener(ev -> ok.setEnabled(p.importable() && skipManual.isSelected()));
                    }
                    if (!p.importable()) {
                        status.setText(Lang.t("mrpack.not_importable"));
                        status.setForeground(Theme.FAIL);
                    }
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    center.removeAll();
                    center.add(Ui.wrapText(Lang.t("mrpack.error.read", e.getMessage()), 13, Theme.FAIL));
                    center.revalidate();
                });
            }
        });
        ok.addActionListener(e -> {
            MrpackImporter.Preview p = preview[0];
            String n = name.getText().strip();
            if (n.isEmpty() || n.length() > InstanceManager.MAX_NAME) {
                status.setText(Lang.t("instances.error.name_invalid", InstanceManager.MAX_NAME));
                status.setForeground(Theme.FAIL);
                return;
            }
            Set<String> skip = new HashSet<>();
            optional.forEach((path, box) -> {
                if (!box.isSelected()) {
                    skip.add(path);
                }
            });
            ok.setEnabled(false);
            name.setEnabled(false);
            bar.setVisible(true);
            bar.setIndeterminate(true);
            status.setForeground(Theme.MUTED);
            status.setText(Lang.t("mrpack.importing"));
            running.set(io.submit(() -> {
                try {
                    Instance inst = new MrpackImporter(host.ctx().downloader).importPack(p, n, skip, host.ctx().instances,
                            "curseforge".equals(p.format()) ? "curseforge" : sourceProject != null ? "modrinth" : "mrpack", sourceProject, sourceVersion,
                            (bytes, total, files, filesTotal) -> SwingUtilities.invokeLater(() -> {
                                bar.setIndeterminate(false);
                                bar.setValue((int) (total > 0 ? bytes * 1000 / total : files * 1000L / Math.max(1, filesTotal)));
                                status.setText(Lang.t("mrpack.progress", files, filesTotal, Ui.size(bytes), Ui.size(Math.max(total, bytes))));
                            }));
                    SwingUtilities.invokeLater(() -> {
                        d.dispose();
                        host.instancesChanged();
                        InstancesPage.info(host, Lang.t("mrpack.done", inst.name));
                        done.accept(inst);
                    });
                } catch (IOException | RuntimeException ex) {
                    Log.warn("Modpack import failed: " + ex.getMessage());
                    boolean cancelled = Thread.currentThread().isInterrupted() || ex instanceof java.io.InterruptedIOException;
                    SwingUtilities.invokeLater(() -> {
                        bar.setVisible(false);
                        status.setForeground(Theme.FAIL);
                        status.setText(cancelled ? Lang.t("mrpack.cancelled") : Lang.t("mrpack.failed", ex.getMessage()));
                        ok.setEnabled(true);
                        name.setEnabled(true);
                    });
                }
            }));
        });
        d.setVisible(true);
    }

    /** The CurseForge provider when the player entered a key, else null. */
    static dev.lumora.launcher.core.platform.CurseForgeProvider curseForge(PageHost host) {
        return host.ctx().repository.provider(dev.lumora.launcher.core.platform.Platform.CURSEFORGE)
                .filter(dev.lumora.launcher.core.platform.ModPlatformProvider::available)
                .map(p -> (dev.lumora.launcher.core.platform.CurseForgeProvider) p).orElse(null);
    }

    /** Fills the preview; returns the "import without them" box when files must be downloaded by hand, else null. */
    private static JCheckBox fill(JPanel center, MrpackImporter.Preview p, JTextField name, Map<String, JCheckBox> optional) {
        JCheckBox skipManual = null;
        JPanel head = new JPanel(new BorderLayout(14, 0));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        ImageIcon icon = Ui.icon(p.icon(), 64);
        head.add(icon != null ? Ui.imageTile(icon, 64, 64) : Ui.letterTile(p.name(), 64, Theme.ACCENT), BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(p.name(), 17, Font.BOLD, Theme.TEXT));
        t.add(Ui.label((p.version() == null ? "" : p.version() + " · ") + p.kind().label()
                + (p.loaderVersion() == null ? "" : " " + p.loaderVersion()) + " · Minecraft " + p.minecraftVersion(), 13, Font.PLAIN, Theme.MUTED));
        if (p.summary() != null && !p.summary().isBlank()) {
            t.add(Box.createVerticalStrut(4));
            t.add(Ui.wrapText(p.summary(), 13, Theme.TEXT));
        }
        head.add(t, BorderLayout.CENTER);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        center.add(head);
        center.add(Box.createVerticalStrut(14));

        JPanel facts = new JPanel(new GridLayout(0, 3, 10, 10));
        facts.setOpaque(false);
        facts.setAlignmentX(Component.LEFT_ALIGNMENT);
        facts.add(fact(Lang.t("mrpack.fact.mods"), String.valueOf(p.count("mods"))));
        facts.add(fact(Lang.t("mrpack.fact.resourcepacks"), String.valueOf(p.count("resourcepacks"))));
        facts.add(fact(Lang.t("mrpack.fact.shaders"), String.valueOf(p.count("shaderpacks"))));
        facts.add(fact(Lang.t("mrpack.fact.overrides"), String.valueOf(p.overrideFiles())));
        facts.add(fact(Lang.t("mrpack.fact.download"), Ui.size(p.downloadBytes())));
        facts.add(fact(Lang.t("mrpack.fact.space"), Ui.size(p.downloadBytes() + p.overrideBytes())));
        facts.setMaximumSize(new Dimension(Integer.MAX_VALUE, facts.getPreferredSize().height));
        center.add(facts);
        center.add(Box.createVerticalStrut(10));
        if (p.skippedServerOnly() > 0) {
            center.add(Ui.wrapText(Lang.plural("mrpack.server_only", p.skippedServerOnly()), 12.5f, Theme.MUTED));
            center.add(Box.createVerticalStrut(6));
        }
        if (!p.problems().isEmpty()) {
            Card prob = new Card(new BorderLayout()).outline(Theme.alpha(Theme.FAIL, 160)).calm();
            JPanel col = Ui.column();
            col.add(Ui.label(Lang.t("mrpack.problems"), 14, Font.BOLD, Theme.FAIL));
            for (String s : p.problems().subList(0, Math.min(12, p.problems().size()))) {
                col.add(Ui.wrapText("• " + s, 12.5f, Theme.TEXT));
            }
            if (p.problems().size() > 12) {
                col.add(Ui.label(Lang.t("mrpack.problems.more", p.problems().size() - 12), 12, Font.PLAIN, Theme.MUTED));
            }
            prob.add(col, BorderLayout.CENTER);
            prob.setAlignmentX(Component.LEFT_ALIGNMENT);
            prob.setMaximumSize(new Dimension(Integer.MAX_VALUE, prob.getPreferredSize().height + 10));
            center.add(prob);
            center.add(Box.createVerticalStrut(10));
        }
        center.add(Ui.caption(Lang.t("mrpack.name")));
        String suggested = p.name().length() > InstanceManager.MAX_NAME ? p.name().substring(0, InstanceManager.MAX_NAME) : p.name();
        name.setText(suggested);
        name.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(name);
        List<MrpackImporter.PackFile> opts = p.files().stream().filter(MrpackImporter.PackFile::optional).toList();
        if (!opts.isEmpty()) {
            center.add(Ui.caption(Lang.t("mrpack.optional")));
            for (MrpackImporter.PackFile f : opts) {
                JCheckBox box = new JCheckBox(f.path(), true);
                box.setOpaque(false);
                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                optional.put(f.path(), box);
                center.add(box);
            }
        }
        center.add(Box.createVerticalStrut(8));
        center.add(Ui.wrapText(Lang.t("mrpack.independent"), 12.5f, Theme.MUTED));
        if (!p.manual().isEmpty()) {
            Card man = new Card(new BorderLayout()).outline(Theme.alpha(Theme.WARN, 160)).calm();
            JPanel col = Ui.column();
            col.add(Ui.label(Lang.plural("cfpack.manual", p.manual().size()), 14, Font.BOLD, Theme.WARN));
            col.add(Ui.wrapText(Lang.t("cfpack.manual.text"), 12.5f, Theme.MUTED));
            for (String m : p.manual()) {
                col.add(Ui.wrapText("• " + m, 12.5f, Theme.TEXT));
            }
            skipManual = new JCheckBox(Lang.t("cfpack.manual.skip"));
            skipManual.setOpaque(false);
            col.add(skipManual);
            man.add(col, BorderLayout.CENTER);
            man.setAlignmentX(Component.LEFT_ALIGNMENT);
            man.setMaximumSize(new Dimension(Integer.MAX_VALUE, man.getPreferredSize().height + 10));
            center.add(Box.createVerticalStrut(10));
            center.add(man);
        }
        return skipManual;
    }

    private static JComponent fact(String label, String value) {
        Card c = new Card(new BorderLayout(0, 2)).calm().padding(10, 12, 10, 12);
        c.add(Ui.label(label, 11.5f, Font.BOLD, Theme.MUTED), BorderLayout.NORTH);
        c.add(Ui.label(value, 16, Font.BOLD, Theme.TEXT), BorderLayout.CENTER);
        c.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return c;
    }
}
