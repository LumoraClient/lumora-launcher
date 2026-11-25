package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.InstanceLayout;
import dev.lumora.launcher.core.content.CompatibilityChecker;
import dev.lumora.launcher.core.content.ContentInstaller;
import dev.lumora.launcher.core.content.ContentTransfer;
import dev.lumora.launcher.core.content.ContentUpdates;
import dev.lumora.launcher.core.content.InstalledContent;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.BackupManager;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.library.InstanceLibrary;
import dev.lumora.launcher.core.loader.LoaderVersion;
import dev.lumora.launcher.core.pack.InstanceExporter;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.Platform;
import dev.lumora.launcher.core.util.Log;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Instance tools beyond the content list: updates, backups, export, transfer and data packs per world. */
final class InstanceTools {
    private InstanceTools() {
    }

    // ---------------------------------------------------------------- updates

    /** Result of an update check, for the "Updates" section. */
    record UpdatesData(List<ContentUpdates.Update> updates) {
    }

    static UpdatesData checkUpdates(PageHost host, Instance i) {
        return new UpdatesData(ContentUpdates.check(host.ctx().repository, i.kind(), i.minecraftVersion, host.ctx().layout(i).gameDir()));
    }

    static void fillUpdates(JPanel list, PageHost host, ExecutorService io, Instance i, UpdatesData data, Runnable reload) {
        List<ContentUpdates.Update> updates = data.updates();
        JPanel head = Ui.row(8);
        JLabel status = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
        boolean canBackup = !i.builtIn || !host.ctx().layout(i).standard();
        JCheckBox backup = new JCheckBox(Lang.t("updates.backup_first"), canBackup);
        backup.setOpaque(false);
        backup.setEnabled(canBackup);
        if (!updates.isEmpty()) {
            head.add(Ui.primary(Lang.t("updates.all", updates.size()), Icons.Kind.DOWNLOAD,
                    () -> apply(host, io, i, updates, backup.isSelected(), status, reload)));
            head.add(backup);
        }
        head.add(Ui.button(Lang.t("updates.check"), Icons.Kind.REFRESH, reload));
        list.add(head);
        list.add(status);
        list.add(Box.createVerticalStrut(8));
        if (updates.isEmpty()) {
            Card c = new Card(new BorderLayout()).calm();
            c.add(Ui.wrapText(Lang.t("updates.none") + (host.ctx().repository.provider(Platform.CURSEFORGE).filter(ModPlatformProvider::available)
                    .isEmpty() ? " " + Lang.t("updates.none.curseforge") : ""), 13, Theme.MUTED), BorderLayout.CENTER);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 10));
            list.add(c);
            return;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Lang.locale()).withZone(java.time.ZoneId.systemDefault());
        for (ContentUpdates.Update u : updates) {
            Card c = new Card(new BorderLayout(12, 0)).padding(10, 12, 10, 12);
            JPanel t = Ui.column();
            t.add(Ui.label(u.title(), 14, Font.BOLD, Theme.TEXT));
            String line = (u.current() == null ? "?" : u.current()) + "  →  " + u.latest().number()
                    + (u.latest().published() == null ? "" : " · " + fmt.format(u.latest().published())) + " · " + u.platform().label();
            t.add(Ui.label(line, 12.5f, Font.PLAIN, Theme.MUTED));
            String log = u.latest().changelog();
            if (log != null && !log.isBlank()) {
                String plain = log.replaceAll("<[^>]{0,500}>", " ").replaceAll("[#*`_>]+", "").replaceAll("\\s+", " ").strip();
                t.add(Ui.wrapText(plain.length() > 220 ? plain.substring(0, 219) + "…" : plain, 12, Theme.MUTED));
            }
            c.add(t, BorderLayout.CENTER);
            JPanel east = Ui.row(6);
            east.add(Ui.button(Lang.t("updates.pin"), Icons.Kind.LOCK, () -> io.submit(() -> {
                try {
                    ContentUpdates.setPinned(host.ctx().layout(i).gameDir(), u.item(), true);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> host.error(Lang.t("updates.pin"), e.getMessage()));
                }
                SwingUtilities.invokeLater(reload);
            })));
            east.add(Ui.primary(Lang.t("updates.one"), Icons.Kind.DOWNLOAD, () -> apply(host, io, i, List.of(u), false, status, reload)));
            c.add(east, BorderLayout.EAST);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            list.add(c);
            list.add(Box.createVerticalStrut(6));
        }
    }

    /** Installs updates one after another; each replaced file is backed up by the installer, optionally the whole instance first. */
    private static void apply(PageHost host, ExecutorService io, Instance i, List<ContentUpdates.Update> updates, boolean backupFirst,
                              JLabel status, Runnable reload) {
        if (host.gameRunning() && host.ctx().selectedInstance().key().equals(i.key())) {
            host.error(Lang.t("updates.title"), Lang.t("instances.error.running"));
            return;
        }
        status.setText(Lang.t("updates.running"));
        io.submit(() -> {
            List<String> done = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            InstanceLayout layout = host.ctx().layout(i);
            try {
                if (backupFirst) {
                    SwingUtilities.invokeLater(() -> status.setText(Lang.t("backup.running")));
                    host.ctx().backups.create(i, layout, null);
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("backup.failed"), e.getMessage()));
                return;
            }
            ContentInstaller installer = new ContentInstaller(host.ctx().downloader);
            ContentInstaller.Target target = new ContentInstaller.Target(i.kind(), i.minecraftVersion, layout.gameDir());
            for (ContentUpdates.Update u : updates) {
                try {
                    ModPlatformProvider p = host.ctx().repository.require(u.platform());
                    SwingUtilities.invokeLater(() -> status.setText(Lang.t("updates.updating", u.title())));
                    ContentInstaller.Plan plan = installer.plan(p, target, null, u.item().type(), u.latest(), Set.of());
                    if (plan.blocked()) {
                        failed.add(u.title() + ": " + plan.issues().stream().filter(x -> x.severity() == CompatibilityChecker.Severity.BLOCK)
                                .map(CompatibilityChecker.Issue::text).findFirst().orElse(""));
                        continue;
                    }
                    installer.execute(plan, u.platform().id(), null);
                    done.add(u.title());
                } catch (IOException | RuntimeException e) {
                    failed.add(u.title() + ": " + e.getMessage());
                }
            }
            SwingUtilities.invokeLater(() -> {
                status.setText(Lang.plural("updates.done", done.size()));
                if (!failed.isEmpty()) {
                    host.error(Lang.t("updates.failed"), String.join("\n", failed));
                }
                reload.run();
            });
        });
    }

    // ---------------------------------------------------------------- backups

    static void backupNow(PageHost host, ExecutorService io, Instance i) {
        io.submit(() -> {
            try {
                BackupManager.Backup b = host.ctx().backups.create(i, host.ctx().layout(i), null);
                SwingUtilities.invokeLater(() -> InstancesPage.info(host, Lang.t("backup.done", b.file().getFileName(), Ui.size(b.size()))));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("backup.failed"), e.getMessage()));
            }
        });
    }

    /** List of backups with restore and delete. */
    static void backups(PageHost host, ExecutorService io, Instance i, Runnable changed) {
        List<BackupManager.Backup> list = host.ctx().backups.list(i);
        if (list.isEmpty()) {
            InstancesPage.info(host, Lang.t("backup.none"));
            return;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Lang.locale()).withZone(java.time.ZoneId.systemDefault());
        String[] labels = list.stream().map(b -> fmt.format(b.created()) + "  ·  " + Ui.size(b.size())).toArray(String[]::new);
        JComboBox<String> box = new JComboBox<>(labels);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel(Lang.t("backup.choose", i.name)), BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        String[] options = {Lang.t("backup.restore"), Lang.t("backup.delete"), Lang.t("common.open_folder"), Lang.t("common.cancel")};
        int r = JOptionPane.showOptionDialog(host.frame(), p, Lang.t("backup.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        BackupManager.Backup chosen = list.get(Math.max(0, box.getSelectedIndex()));
        if (r == 2) {
            host.openFolder(host.ctx().backups.folder(i));
        } else if (r == 1) {
            if (JOptionPane.showConfirmDialog(host.frame(), Lang.t("backup.delete.confirm", labels[box.getSelectedIndex()]), Lang.t("backup.delete"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
                try {
                    host.ctx().backups.delete(i, chosen);
                } catch (IOException e) {
                    host.error(Lang.t("backup.delete"), e.getMessage());
                }
            }
        } else if (r == 0) {
            if (host.gameRunning() && host.ctx().selectedInstance().key().equals(i.key())) {
                host.error(Lang.t("backup.title"), Lang.t("instances.error.running"));
                return;
            }
            if (JOptionPane.showConfirmDialog(host.frame(), Lang.t("backup.restore.confirm", i.name, labels[box.getSelectedIndex()]),
                    Lang.t("backup.restore"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            io.submit(() -> {
                try {
                    host.ctx().backups.restore(i, host.ctx().layout(i), chosen);
                    SwingUtilities.invokeLater(() -> {
                        InstancesPage.info(host, Lang.t("backup.restored"));
                        changed.run();
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> host.error(Lang.t("backup.restore.failed"), e.getMessage()));
                }
            });
        }
    }

    // ---------------------------------------------------------------- export

    static void export(PageHost host, ExecutorService io, Instance i) {
        boolean mrpack = i.kind() != InstanceKind.LUMORA;
        String[] options = mrpack ? new String[]{Lang.t("export.mrpack"), Lang.t("export.archive"), Lang.t("common.cancel")}
                : new String[]{Lang.t("export.archive"), Lang.t("common.cancel")};
        int r = JOptionPane.showOptionDialog(host.frame(), Lang.t(mrpack ? "export.text" : "export.text.lumora"), Lang.t("export.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        boolean asMrpack = mrpack && r == 0;
        boolean asArchive = mrpack ? r == 1 : r == 0;
        if (!asMrpack && !asArchive) {
            return;
        }
        Path game = host.ctx().layout(i).gameDir();
        if (asArchive && i.builtIn && host.ctx().layout(i).standard()) {
            host.error(Lang.t("export.title"), Lang.t("backup.error.standard"));
            return;
        }
        JTextField name = new JTextField(i.name);
        JTextField version = new JTextField("1.0.0");
        JCheckBox config = new JCheckBox(Lang.t("export.config"), true);
        JCheckBox options2 = new JCheckBox(Lang.t("export.options"), false);
        JCheckBox servers = new JCheckBox(Lang.t("export.servers"), false);
        if (asMrpack) {
            JPanel form = Ui.column();
            form.add(new JLabel(Lang.t("export.name")));
            form.add(name);
            form.add(new JLabel(Lang.t("export.version")));
            form.add(version);
            form.add(config);
            form.add(options2);
            form.add(servers);
            form.add(Box.createVerticalStrut(6));
            form.add(new JLabel("<html><div style='width:320px'>" + Lang.t("export.mrpack.hint") + "</div></html>"));
            if (JOptionPane.showConfirmDialog(host.frame(), form, Lang.t("export.mrpack"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
        }
        JFileChooser fc = new JFileChooser();
        String base = i.name.replaceAll("[^A-Za-z0-9 _.-]", "_").strip();
        fc.setSelectedFile(new java.io.File(base + (asMrpack ? ".mrpack" : ".zip")));
        if (fc.showSaveDialog(host.frame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = fc.getSelectedFile().toPath();
        if (Files.exists(target) && JOptionPane.showConfirmDialog(host.frame(), Lang.t("export.replace", target.getFileName()), Lang.t("export.title"),
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        io.submit(() -> {
            try {
                InstanceExporter.Result res;
                if (asMrpack) {
                    String loader = i.loaderVersion;
                    if (loader == null && i.kind().modded()) {
                        loader = host.ctx().installer.loaders().recommended(i.kind().loaderId(), i.minecraftVersion)
                                .map(LoaderVersion::version).orElse(null);
                    }
                    res = InstanceExporter.exportMrpack(i, game, loader, name.getText(), version.getText(),
                            new InstanceExporter.MrpackOptions(config.isSelected(), options2.isSelected(), servers.isSelected()),
                            host.ctx().repository.provider(Platform.MODRINTH).orElse(null), target);
                } else {
                    res = InstanceExporter.exportArchive(i, game, target);
                }
                InstanceExporter.Result r2 = res;
                SwingUtilities.invokeLater(() -> InstancesPage.info(host, asMrpack
                        ? Lang.t("export.done.mrpack", r2.file().getFileName(), r2.downloads(), r2.overrides())
                        : Lang.t("export.done.archive", r2.file().getFileName(), r2.overrides())));
            } catch (IOException | RuntimeException e) {
                Log.warn("Export failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> host.error(Lang.t("export.failed"), e.getMessage()));
            }
        });
    }

    // ---------------------------------------------------------------- selected files

    static void exportSelected(PageHost host, List<InstalledContent.Item> items) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(Lang.t("selection.export"));
        if (fc.showSaveDialog(host.frame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<Path> copied = InstanceExporter.exportFiles(items.stream().map(InstalledContent.Item::file).toList(), fc.getSelectedFile().toPath());
            InstancesPage.info(host, Lang.t("selection.export.done", copied.size(), items.size() - copied.size()));
        } catch (IOException e) {
            host.error(Lang.t("selection.export"), e.getMessage());
        }
    }

    static void transferSelected(PageHost host, ExecutorService io, Instance from, List<InstalledContent.Item> items, Runnable done) {
        List<Instance> targets = host.ctx().instances.list(host.ctx().config).stream().filter(t -> !t.key().equals(from.key())).toList();
        if (targets.isEmpty()) {
            return;
        }
        JComboBox<String> box = new JComboBox<>(targets.stream().map(t -> t.name + " (" + t.kind().label() + " " + t.minecraftVersion + ")")
                .toArray(String[]::new));
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel(Lang.plural("selection.transfer.text", items.size())), BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(host.frame(), p, Lang.t("selection.transfer"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }
        Instance to = targets.get(box.getSelectedIndex());
        io.submit(() -> {
            try {
                ContentTransfer.Result r = ContentTransfer.copy(items, host.ctx().layout(from).gameDir(), to.kind(), to.minecraftVersion,
                        host.ctx().layout(to).gameDir());
                SwingUtilities.invokeLater(() -> {
                    InstancesPage.info(host, Lang.plural("merge.content.done", r.copied().size(), to.name)
                            + (r.skipped().isEmpty() ? "" : "\n\n" + Lang.t("merge.skipped") + "\n" + String.join("\n", r.skipped())));
                    done.run();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("merge.failed"), e.getMessage()));
            }
        });
    }

    // ---------------------------------------------------------------- data packs per world

    /** Data packs of a world below its name, with remove and add. */
    static JComponent worldDatapacks(PageHost host, ExecutorService io, Instance i, InstanceLibrary.World w, Runnable reload) {
        JPanel col = Ui.column();
        Path dir = w.dir().resolve("datapacks");
        List<Path> packs = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                s.filter(f -> Files.isDirectory(f) || f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().forEach(packs::add);
            } catch (IOException e) {
                Log.warn("Could not list data packs: " + e.getMessage());
            }
        }
        JPanel row = Ui.row(6);
        row.add(Ui.label(Lang.plural("world.datapacks", packs.size()), 12, Font.BOLD, Theme.MUTED));
        JButton add = Ui.button(Lang.t("world.datapacks.add"), Icons.Kind.PLUS, () -> {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip"));
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                io.submit(() -> {
                    List<String> refused = new ArrayList<>();
                    for (java.io.File f : fc.getSelectedFiles()) {
                        try {
                            if (dev.lumora.launcher.core.pack.FileClassifier.classify(f.toPath()).kind()
                                    != dev.lumora.launcher.core.pack.FileClassifier.Kind.DATAPACK) {
                                refused.add(Lang.t("instance.add_file.refused", f.getName(), Lang.t("instance.file_kind.unknown")));
                                continue;
                            }
                            Path target = dev.lumora.launcher.core.util.FileUtil.resolveInside(dir, f.getName());
                            Files.createDirectories(dir);
                            if (!Files.exists(target)) {
                                Files.copy(f.toPath(), target);
                            }
                        } catch (IOException e) {
                            refused.add(f.getName() + ": " + e.getMessage());
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (!refused.isEmpty()) {
                            host.error(Lang.t("instance.add_file.problems"), String.join("\n", refused));
                        }
                        reload.run();
                    });
                });
            }
        });
        row.add(add);
        row.add(Ui.button(Lang.t("world.datapacks.find"), Icons.Kind.COMPASS,
                () -> host.discover(dev.lumora.launcher.core.platform.ProjectType.DATAPACK, i)));
        col.add(row);
        for (Path p : packs) {
            JPanel r = Ui.row(6);
            r.add(Ui.label("• " + p.getFileName(), 12, Font.PLAIN, Theme.TEXT));
            JButton remove = Ui.button("", Icons.Kind.TRASH, () -> {
                if (JOptionPane.showConfirmDialog(host.frame(), Lang.t("world.datapacks.remove.confirm", p.getFileName(), w.name()),
                        Lang.t("instance.item.remove"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
                    try {
                        dev.lumora.launcher.core.util.FileUtil.deleteTree(p);
                    } catch (IOException e) {
                        host.error(Lang.t("instance.item.remove.failed"), e.getMessage());
                    }
                    reload.run();
                }
            });
            remove.setToolTipText(Lang.t("instance.item.remove"));
            r.add(remove);
            col.add(r);
        }
        return col;
    }
}
