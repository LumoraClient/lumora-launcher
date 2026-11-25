package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.util.Log;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Duplicate, rename and delete with their questions; used by the instance list and the instance page. */
final class InstanceActions {
    private InstanceActions() {
    }

    static void duplicate(PageHost host, ExecutorService io, Instance i, Consumer<Instance> done) {
        String suggestion = Lang.t("instances.copy_name", i.name);
        if (suggestion.length() > InstanceManager.MAX_NAME) {
            suggestion = suggestion.substring(0, InstanceManager.MAX_NAME);
        }
        Object name = JOptionPane.showInputDialog(host.frame(), Lang.t("instances.duplicate.text"), Lang.t("instances.action.duplicate"),
                JOptionPane.PLAIN_MESSAGE, null, null, suggestion);
        if (!(name instanceof String s) || s.isBlank()) {
            return;
        }
        io.submit(() -> {
            try {
                Instance copy = host.ctx().instances.duplicate(host.ctx().config, i, s.strip());
                SwingUtilities.invokeLater(() -> {
                    host.instancesChanged();
                    done.accept(copy);
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Copy failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> host.error(Lang.t("instances.duplicate.failed"), e.getMessage()));
            }
        });
    }

    static void rename(PageHost host, Instance i, Consumer<Instance> done) {
        Object name = JOptionPane.showInputDialog(host.frame(), Lang.t("instances.rename.text"), Lang.t("instances.action.rename"),
                JOptionPane.PLAIN_MESSAGE, null, null, i.name);
        if (!(name instanceof String s) || s.isBlank() || s.strip().equals(i.name)) {
            return;
        }
        try {
            Instance r = host.ctx().instances.rename(i, s);
            host.instancesChanged();
            done.accept(r);
        } catch (IOException e) {
            host.error(Lang.t("instances.rename.failed"), e.getMessage());
        }
    }

    /**
     * An instance was dragged onto another: copy its mods and packs over, take over its settings, or make a new copy.
     * The three are clearly separate; nothing is overwritten without a confirmation, and before settings are replaced
     * the target is backed up when it can be.
     */
    static void mergeInto(PageHost host, ExecutorService io, Instance source, Instance target, Consumer<Instance> opened) {
        if (source.key().equals(target.key())) {
            return;
        }
        String[] options = {Lang.t("merge.content"), Lang.t("merge.settings"), Lang.t("merge.copy"), Lang.t("common.cancel")};
        int r = JOptionPane.showOptionDialog(host.frame(), Lang.t("merge.text", source.name, target.name), Lang.t("merge.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (r == 2) {
            duplicate(host, io, source, opened);
            return;
        }
        if (r != 0 && r != 1) {
            return;
        }
        java.nio.file.Path from = host.ctx().layout(source).gameDir();
        java.nio.file.Path to = host.ctx().layout(target).gameDir();
        if (r == 0) {
            io.submit(() -> {
                try {
                    java.util.List<dev.lumora.launcher.core.content.InstalledContent.Item> items = new java.util.ArrayList<>();
                    dev.lumora.launcher.core.content.ContentManifest m = dev.lumora.launcher.core.content.ContentManifest.load(from);
                    for (dev.lumora.launcher.core.platform.ProjectType t : dev.lumora.launcher.core.content.InstalledContent.TYPES) {
                        if (t != dev.lumora.launcher.core.platform.ProjectType.DATAPACK) {
                            items.addAll(dev.lumora.launcher.core.content.InstalledContent.scan(from, t, m));
                        }
                    }
                    var res = dev.lumora.launcher.core.content.ContentTransfer.copy(items, from, target.kind(), target.minecraftVersion, to);
                    SwingUtilities.invokeLater(() -> {
                        host.instancesChanged();
                        String text = Lang.plural("merge.content.done", res.copied().size(), target.name)
                                + (res.skipped().isEmpty() ? "" : "\n\n" + Lang.t("merge.skipped") + "\n" + String.join("\n", res.skipped()));
                        InstancesPage.info(host, text);
                    });
                } catch (IOException | RuntimeException e) {
                    SwingUtilities.invokeLater(() -> host.error(Lang.t("merge.failed"), e.getMessage()));
                }
            });
            return;
        }
        // settings: mod configs and options.txt
        java.util.List<String> overwrite = new java.util.ArrayList<>();
        for (String f : java.util.List.of("options.txt")) {
            if (java.nio.file.Files.exists(to.resolve(f)) && java.nio.file.Files.exists(from.resolve(f))) {
                overwrite.add(f);
            }
        }
        if (java.nio.file.Files.isDirectory(to.resolve("config")) && java.nio.file.Files.isDirectory(from.resolve("config"))) {
            overwrite.add("config/");
        }
        if (!overwrite.isEmpty() && JOptionPane.showConfirmDialog(host.frame(), Lang.t("merge.settings.confirm", String.join(", ", overwrite), target.name),
                Lang.t("merge.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        io.submit(() -> {
            try {
                if (!target.builtIn || !host.ctx().layout(target).standard()) {
                    host.ctx().backups.create(target, host.ctx().layout(target), null);
                }
                if (java.nio.file.Files.isRegularFile(from.resolve("options.txt"))) {
                    dev.lumora.launcher.core.util.FileUtil.writeAtomically(to.resolve("options.txt"), java.nio.file.Files.readAllBytes(from.resolve("options.txt")));
                }
                if (java.nio.file.Files.isDirectory(from.resolve("config"))) {
                    try (var walk = java.nio.file.Files.walk(from.resolve("config"))) {
                        for (java.nio.file.Path p : walk.filter(java.nio.file.Files::isRegularFile).toList()) {
                            java.nio.file.Path dest = dev.lumora.launcher.core.util.FileUtil.resolveInside(to, from.relativize(p).toString());
                            java.nio.file.Files.createDirectories(dest.getParent());
                            java.nio.file.Files.copy(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                SwingUtilities.invokeLater(() -> InstancesPage.info(host, Lang.t("merge.settings.done", target.name)));
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("merge.failed"), e.getMessage()));
            }
        });
    }

    /** Asks (worlds are deleted too, cannot be undone), then deletes; tries the recycle bin first. */
    static void delete(PageHost host, ExecutorService io, Instance i, Runnable done) {
        if (host.gameRunning() && host.ctx().selectedInstance().key().equals(i.key())) {
            host.error(Lang.t("instances.delete.failed"), Lang.t("instances.error.running"));
            return;
        }
        int r = JOptionPane.showConfirmDialog(host.frame(), Lang.t("instances.delete.confirm", i.name), Lang.t("instances.action.delete"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        io.submit(() -> {
            try {
                boolean trashed = false;
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                    trashed = java.awt.Desktop.getDesktop().moveToTrash(i.dir.toFile());
                }
                if (!trashed) {
                    host.ctx().instances.delete(i);
                }
                Log.info("Instance " + i.id + (trashed ? " moved to the recycle bin" : " deleted"));
                SwingUtilities.invokeLater(() -> {
                    if (i.key().equals(host.ctx().config.selectedInstance)) {
                        host.ctx().config.selectedInstance = null;
                        host.saveConfig();
                    }
                    host.instancesChanged();
                    done.run();
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("instances.delete.failed"), e.getMessage()));
            }
        });
    }
}
