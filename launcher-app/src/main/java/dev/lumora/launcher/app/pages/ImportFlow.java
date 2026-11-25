package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.core.content.CompatibilityChecker;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.pack.FileClassifier;
import dev.lumora.launcher.core.pack.LauncherImport;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Where a file goes, whether it was chosen or dropped: modpacks (.mrpack, CurseForge zip) and Lumora archives become a
 * new instance after a preview, a folder of another launcher opens the launcher import, mods and packs go into an
 * instance (the given one, else the player picks one). Every file is recognised by its content first.
 */
public final class ImportFlow {
    private ImportFlow() {
    }

    /**
     * @param target instance for mods and packs (null = ask), {@code done} receives a newly created instance
     */
    public static void open(PageHost host, ExecutorService io, List<Path> paths, Instance target, Consumer<Instance> done) {
        if (paths.isEmpty()) {
            return;
        }
        io.submit(() -> {
            List<Path> content = new ArrayList<>();
            List<String> unknown = new ArrayList<>();
            Path pack = null;
            Path archive = null;
            Path folder = null;
            for (Path p : paths) {
                if (Files.isDirectory(p)) {
                    folder = folder == null ? p : folder;
                    continue;
                }
                FileClassifier.Kind k = FileClassifier.classify(p).kind();
                switch (k) {
                    case MODPACK -> pack = pack == null ? p : pack;
                    case INSTANCE_ARCHIVE -> archive = archive == null ? p : archive;
                    case MOD, RESOURCE_PACK, SHADER, DATAPACK -> content.add(p);
                    default -> unknown.add(Lang.t("instance.add_file.refused", p.getFileName(),
                            Lang.t("instance.file_kind." + k.name().toLowerCase(Locale.ROOT))));
                }
            }
            Path packFile = pack;
            Path archiveFile = archive;
            Path folderPath = folder;
            SwingUtilities.invokeLater(() -> {
                if (!unknown.isEmpty()) {
                    host.error(Lang.t("drop.unknown.title"), String.join("\n", unknown));
                }
                if (packFile != null) {
                    MrpackDialog.show(host, io, packFile, null, null, done);
                } else if (archiveFile != null) {
                    importArchive(host, io, archiveFile, done);
                } else if (folderPath != null) {
                    LauncherImportDialog.show(host, io, folderPath, done);
                }
                if (!content.isEmpty()) {
                    Instance t = target != null ? target : chooseInstance(host, content);
                    if (t != null) {
                        addFiles(host, io, t, content, () -> host.instancesChanged());
                    }
                }
            });
        });
    }

    /** Files dropped anywhere else in the window: modpacks become instances, mods and packs ask for the instance. */
    public static void installWindowDrop(PageHost host, ExecutorService io, java.awt.Component root) {
        DropSupport.target(root, new DropSupport.Handlers(files -> open(host, io, files, null, host::openInstance), null, null, null));
    }

    /** Asks which instance mods or packs go into (the selected one is preselected). */
    static Instance chooseInstance(PageHost host, List<Path> files) {
        List<Instance> all = host.ctx().instances.list(host.ctx().config);
        List<String> names = all.stream().map(i -> i.name + " (" + i.kind().label() + " " + i.minecraftVersion + ")").toList();
        JComboBox<String> box = new JComboBox<>(names.toArray(String[]::new));
        String selected = host.ctx().selectedInstance().key();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).key().equals(selected)) {
                box.setSelectedIndex(i);
            }
        }
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new javax.swing.JLabel(Lang.plural("drop.choose_instance", files.size())), BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(host.frame(), p, Lang.t("drop.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION && box.getSelectedIndex() >= 0 ? all.get(box.getSelectedIndex()) : null;
    }

    /** Copies local mods and packs into an instance after checking what they are; asks before replacing anything. */
    static void addFiles(PageHost host, ExecutorService io, Instance instance, List<Path> files, Runnable after) {
        Path game = host.ctx().layout(instance).gameDir();
        io.submit(() -> {
            List<String> added = new ArrayList<>();
            List<String> refused = new ArrayList<>();
            for (Path f : files) {
                FileClassifier.Result r = FileClassifier.classify(f);
                ProjectType t = r.kind().projectType();
                if (t == null || !CompatibilityChecker.supports(instance.kind(), t)) {
                    refused.add(Lang.t("instance.add_file.refused", f.getFileName(),
                            Lang.t("instance.file_kind." + r.kind().name().toLowerCase(Locale.ROOT))));
                    continue;
                }
                if (t == ProjectType.MOD && r.mod() != null
                        && r.mod().loaders().stream().noneMatch(instance.kind().modLoaders(instance.minecraftVersion)::contains)) {
                    refused.add(Lang.t("instance.add_file.wrong_loader", f.getFileName(), String.join(", ", r.mod().loaders()), instance.kind().label()));
                    continue;
                }
                Path dir = game.resolve(t.folder());
                if (t == ProjectType.DATAPACK) {
                    Path world = chooseWorld(host, game);
                    if (world == null) {
                        refused.add(Lang.t("drop.datapack.no_world", f.getFileName()));
                        continue;
                    }
                    dir = world.resolve("datapacks");
                }
                try {
                    Path target = FileUtil.resolveInside(dir, f.getFileName().toString());
                    if (Files.exists(target)) {
                        boolean[] ok = {false};
                        SwingUtilities.invokeAndWait(() -> ok[0] = JOptionPane.showConfirmDialog(host.frame(),
                                Lang.t("instance.add_file.replace", target.getFileName()), Lang.t("instance.add_file"),
                                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
                        if (!ok[0]) {
                            continue;
                        }
                    }
                    Path tmp = target.resolveSibling(target.getFileName() + ".lumora-part");
                    Files.createDirectories(target.getParent());
                    Files.copy(f, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    FileUtil.moveReplacing(tmp, target);
                    added.add(f.getFileName().toString());
                } catch (IOException | java.lang.reflect.InvocationTargetException e) {
                    refused.add(f.getFileName() + ": " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (!refused.isEmpty()) {
                    host.error(Lang.t("instance.add_file.problems"), String.join("\n", refused));
                } else if (!added.isEmpty()) {
                    InstancesPage.info(host, Lang.plural("instance.add_file.done", added.size()) + " → " + instance.name);
                }
                after.run();
            });
        });
    }

    /** Asks for a world of the instance (data packs live in a world); called from a background thread. */
    private static Path chooseWorld(PageHost host, Path game) {
        List<dev.lumora.launcher.core.library.InstanceLibrary.World> worlds = dev.lumora.launcher.core.library.InstanceLibrary.worlds(game);
        if (worlds.isEmpty()) {
            return null;
        }
        Path[] chosen = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                Object r = JOptionPane.showInputDialog(host.frame(), Lang.t("drop.datapack.world"), Lang.t("drop.title"), JOptionPane.PLAIN_MESSAGE,
                        null, worlds.stream().map(w -> w.name()).toArray(), worlds.get(0).name());
                for (var w : worlds) {
                    if (w.name().equals(r)) {
                        chosen[0] = w.dir();
                    }
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException e) {
            Log.warn("World choice failed: " + e.getMessage());
        }
        return chosen[0];
    }

    /** Lumora archive: asks for a name, then imports it as a new instance. */
    static void importArchive(PageHost host, ExecutorService io, Path archive, Consumer<Instance> done) {
        String suggested = archive.getFileName().toString().replaceAll("(?i)\\.zip$", "");
        if (suggested.length() > InstanceManager.MAX_NAME) {
            suggested = suggested.substring(0, InstanceManager.MAX_NAME);
        }
        Object name = JOptionPane.showInputDialog(host.frame(), Lang.t("import.archive.name"), Lang.t("import.archive.title"),
                JOptionPane.PLAIN_MESSAGE, null, null, suggested);
        if (!(name instanceof String s) || s.isBlank()) {
            return;
        }
        io.submit(() -> {
            try {
                Instance inst = LauncherImport.importArchive(archive, s.strip(), host.ctx().instances);
                SwingUtilities.invokeLater(() -> {
                    host.instancesChanged();
                    InstancesPage.info(host, Lang.t("mrpack.done", inst.name));
                    done.accept(inst);
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("import.archive.failed"), e.getMessage()));
            }
        });
    }
}
