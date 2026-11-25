package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.content.CompatibilityChecker;
import dev.lumora.launcher.core.content.ContentInstaller;
import dev.lumora.launcher.core.content.DependencyResolver;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.library.InstanceLibrary;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.PlatformModel.Channel;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * "Installieren": choose the instance (and for one project the version), see what will happen — compatibility, the
 * dependency tree with optional dependencies to tick, files that get replaced, the download size — then install with
 * progress and cancel. Blocking problems disable the button and say why; warnings need a confirmation.
 */
final class InstallDialog {
    private final PageHost host;
    private final ExecutorService io;
    private final List<ProjectSummary> projects;
    private final ProjectType type;
    private final Runnable done;
    private final JDialog dialog;
    private final JPanel planPanel = Ui.column();
    private final JLabel status = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final Set<String> optional = new HashSet<>();
    private final Map<String, ProjectVersion> versions = new LinkedHashMap<>();
    private List<Instance> instances = List.of();
    private JComboBox<String> instanceBox;
    private JComboBox<String> versionBox;
    private JComboBox<String> worldBox;
    private List<InstanceLibrary.World> worlds = List.of();
    private final List<ProjectVersion> versionChoices = new ArrayList<>();
    private JCheckBox allVersions;
    private JButton installButton;
    private Instance target;
    private List<ContentInstaller.Plan> plans = List.of();
    private int generation;
    private Future<?> running;

    private InstallDialog(PageHost host, ExecutorService io, List<ProjectSummary> projects, ProjectType type, Instance target, Runnable done) {
        this.host = host;
        this.io = io;
        this.projects = projects;
        this.type = type;
        this.target = target;
        this.done = done;
        dialog = new JDialog(host.frame(), Lang.t("install.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    }

    static void show(PageHost host, ExecutorService io, List<ProjectSummary> projects, ProjectType type, Instance target, Runnable done) {
        if (projects.isEmpty()) {
            return;
        }
        new InstallDialog(host, io, projects, type, target, done).open();
    }

    private ModPlatformProvider provider(ProjectSummary p) throws IOException {
        return host.ctx().repository.require(p.platform());
    }

    private void open() {
        Card c = new Card(new BorderLayout(0, 12)).fill(Theme.SURFACE).outline(null).radius(0).padding(18, 22, 18, 22);
        JPanel head = Ui.column();
        head.add(Ui.heading(projects.size() == 1 ? Lang.t("install.title.one", projects.get(0).title()) : Lang.t("install.title.many", projects.size())));
        head.add(Box.createVerticalStrut(10));
        JPanel row = Ui.row(8);
        row.add(Ui.label(Lang.t("install.instance"), 13, Font.BOLD, Theme.MUTED));
        instances = host.ctx().instances.list(host.ctx().config);
        List<String> names = new ArrayList<>();
        for (Instance i : instances) {
            boolean fits = CompatibilityChecker.supports(i.kind(), type);
            names.add((fits ? "" : "⚠ ") + i.name + " (" + i.kind().label() + " " + i.minecraftVersion + ")");
        }
        names.add(Lang.t("install.new_instance"));
        instanceBox = Ui.dropdown(names);
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).key().equals(target.key())) {
                instanceBox.setSelectedIndex(i);
            }
        }
        instanceBox.setPreferredSize(new Dimension(340, Ui.CONTROL_HEIGHT));
        instanceBox.addActionListener(e -> {
            int i = instanceBox.getSelectedIndex();
            if (i == instances.size()) {
                CreateInstanceDialog.show(host, io, inst -> {
                    target = inst;
                    dialog.dispose();
                    show(host, io, projects, type, inst, done);
                });
                return;
            }
            if (i >= 0 && i < instances.size()) {
                target = instances.get(i);
                versions.clear();
                optional.clear();
                loadVersions();
            }
        });
        row.add(instanceBox);
        head.add(row);
        if (projects.size() == 1) {
            head.add(Box.createVerticalStrut(8));
            JPanel vrow = Ui.row(8);
            vrow.add(Ui.label(Lang.t("install.version"), 13, Font.BOLD, Theme.MUTED));
            versionBox = Ui.dropdown(List.of(Lang.t("common.loading")));
            versionBox.setPreferredSize(new Dimension(340, Ui.CONTROL_HEIGHT));
            versionBox.addActionListener(e -> {
                int i = versionBox.getSelectedIndex();
                if (i >= 0 && i < versionChoices.size() && versionBox.isEnabled()) {
                    versions.put(projects.get(0).id(), versionChoices.get(i));
                    plan();
                }
            });
            vrow.add(versionBox);
            allVersions = new JCheckBox(Lang.t("install.all_versions"));
            allVersions.setOpaque(false);
            allVersions.setToolTipText(Lang.t("install.all_versions.tip"));
            allVersions.addActionListener(e -> loadVersions());
            vrow.add(allVersions);
            head.add(vrow);
        }
        if (type == ProjectType.DATAPACK) {
            head.add(Box.createVerticalStrut(8));
            JPanel wrow = Ui.row(8);
            wrow.add(Ui.label(Lang.t("install.world"), 13, Font.BOLD, Theme.MUTED));
            worldBox = Ui.dropdown(List.of());
            worldBox.setPreferredSize(new Dimension(340, Ui.CONTROL_HEIGHT));
            worldBox.addActionListener(e -> plan());
            wrow.add(worldBox);
            head.add(wrow);
        }
        c.add(head, BorderLayout.NORTH);
        planPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.add(Ui.scroll(planPanel), BorderLayout.CENTER);
        JPanel south = Ui.column();
        bar.setVisible(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(bar);
        south.add(status);
        south.add(Box.createVerticalStrut(6));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(Ui.button(Lang.t("common.cancel"), null, () -> {
            if (running != null && !running.isDone()) {
                running.cancel(true);
                status.setText(Lang.t("install.cancelling"));
            } else {
                dialog.dispose();
            }
        }));
        installButton = Ui.primary(Lang.t("install.install"), Icons.Kind.DOWNLOAD, this::install);
        installButton.setEnabled(false);
        buttons.add(installButton);
        south.add(buttons);
        c.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(c);
        JRootPane root = dialog.getRootPane();
        root.registerKeyboardAction(e -> {
            if (running == null || running.isDone()) {
                dialog.dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(760, 640);
        dialog.setLocationRelativeTo(host.frame());
        loadVersions();
        dialog.setVisible(true);
    }

    private void loadVersions() {
        int gen = ++generation;
        installButton.setEnabled(false);
        planPanel.removeAll();
        planPanel.add(Ui.label(Lang.t("install.checking"), 13, Font.PLAIN, Theme.MUTED));
        planPanel.revalidate();
        Instance t = target;
        boolean every = allVersions != null && allVersions.isSelected();
        if (worldBox != null) {
            worlds = InstanceLibrary.worlds(host.ctx().layout(t).gameDir());
            worldBox.removeAllItems();
            for (InstanceLibrary.World w : worlds) {
                worldBox.addItem(w.name());
            }
        }
        io.submit(() -> {
            try {
                List<String> loaders = loadersFor(t);
                List<ProjectVersion> forSingle = null;
                for (ProjectSummary p : projects) {
                    List<ProjectVersion> list = provider(p).versions(p.id(), every ? List.of() : loaders, every ? List.of() : List.of(t.minecraftVersion));
                    if (projects.size() == 1) {
                        forSingle = list;
                    }
                    ProjectVersion best = DependencyResolver.best(list);
                    if (best != null) {
                        versions.putIfAbsent(p.id(), best);
                    }
                }
                List<ProjectVersion> single = forSingle;
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    if (versionBox != null) {
                        versionBox.setEnabled(false);
                        versionBox.removeAllItems();
                        versionChoices.clear();
                        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Lang.locale())
                                .withZone(java.time.ZoneId.systemDefault());
                        ProjectVersion recommended = versions.get(projects.get(0).id());
                        for (ProjectVersion v : single == null ? List.<ProjectVersion>of() : single) {
                            versionChoices.add(v);
                            String channel = v.channel() == Channel.RELEASE ? "" : " · " + Lang.t("version.channel." + v.channel().name().toLowerCase(java.util.Locale.ROOT));
                            String rec = v == recommended ? " ★ " + Lang.t("install.recommended") : "";
                            versionBox.addItem(v.number() + channel + " · " + (v.published() == null ? "" : fmt.format(v.published())) + rec);
                        }
                        if (versionChoices.isEmpty()) {
                            versionBox.addItem(Lang.t("install.no_versions"));
                        } else {
                            versionBox.setSelectedIndex(Math.max(0, versionChoices.indexOf(recommended)));
                        }
                        versionBox.setEnabled(!versionChoices.isEmpty());
                    }
                    plan();
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    planPanel.removeAll();
                    planPanel.add(issue(true, Lang.t("install.error.versions", e.getMessage()), Lang.t("install.error.versions.hint")));
                    planPanel.revalidate();
                });
            }
        });
    }

    private List<String> loadersFor(Instance t) {
        return switch (type) {
            case MOD -> t.kind().modLoaders(t.minecraftVersion);
            case SHADER -> List.of();
            case DATAPACK -> List.of("datapack");
            default -> List.of("minecraft");
        };
    }

    private ContentInstaller.Target targetFor(Instance t) {
        Path game = host.ctx().layout(t).gameDir();
        if (type == ProjectType.DATAPACK && worldBox != null && worldBox.getSelectedIndex() >= 0 && worldBox.getSelectedIndex() < worlds.size()) {
            game = worlds.get(worldBox.getSelectedIndex()).dir();
        }
        return new ContentInstaller.Target(t.kind(), t.minecraftVersion, game);
    }

    private void plan() {
        int gen = ++generation;
        installButton.setEnabled(false);
        planPanel.removeAll();
        planPanel.add(Ui.label(Lang.t("install.checking"), 13, Font.PLAIN, Theme.MUTED));
        planPanel.revalidate();
        planPanel.repaint();
        Instance t = target;
        if (type == ProjectType.DATAPACK && (worldBox == null || worlds.isEmpty())) {
            planPanel.removeAll();
            planPanel.add(issue(true, Lang.t("install.no_world"), Lang.t("install.no_world.hint")));
            planPanel.revalidate();
            return;
        }
        ContentInstaller.Target tgt = targetFor(t);
        Set<String> chosen = new HashSet<>(optional);
        io.submit(() -> {
            List<ContentInstaller.Plan> result = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            try {
                ContentInstaller installer = new ContentInstaller(host.ctx().downloader);
                for (ProjectSummary p : projects) {
                    ProjectVersion v = versions.get(p.id());
                    if (v == null) {
                        missing.add(p.title());
                        continue;
                    }
                    result.add(installer.plan(provider(p), tgt, p, type, v, chosen));
                }
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    plans = result;
                    showPlans(missing);
                });
            } catch (IOException | RuntimeException e) {
                Log.warn("Install plan failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    planPanel.removeAll();
                    planPanel.add(issue(true, Lang.t("install.error.plan", e.getMessage()), Lang.t("install.error.versions.hint")));
                    planPanel.revalidate();
                });
            }
        });
    }

    private void showPlans(List<String> missing) {
        planPanel.removeAll();
        boolean blocked = false;
        long bytes = 0;
        int files = 0;
        for (String m : missing) {
            planPanel.add(issue(true, Lang.t("install.no_compatible", m, target.kind().label(), target.minecraftVersion), Lang.t("install.no_compatible.hint")));
            planPanel.add(Box.createVerticalStrut(6));
            blocked = true;
        }
        for (ContentInstaller.Plan p : plans) {
            for (CompatibilityChecker.Issue i : p.issues()) {
                planPanel.add(issue(i.severity() == CompatibilityChecker.Severity.BLOCK, i.text(), i.hint()));
                planPanel.add(Box.createVerticalStrut(6));
            }
            blocked |= p.blocked();
            planPanel.add(Ui.caption(Lang.t("install.tree")));
            tree(planPanel, p.tree(), 0, p);
            bytes += p.downloadBytes();
            files += p.steps().size();
            planPanel.add(Box.createVerticalStrut(10));
        }
        if (!blocked && files > 0) {
            List<String> replaced = new ArrayList<>();
            for (ContentInstaller.Plan p : plans) {
                for (ContentInstaller.Step s : p.steps()) {
                    if (s.replacesKey() != null) {
                        replaced.add(s.replacesKey());
                    }
                }
            }
            if (!replaced.isEmpty()) {
                planPanel.add(Ui.wrapText(Lang.t("install.replaces", String.join(", ", replaced)), 12.5f, Theme.MUTED));
                planPanel.add(Box.createVerticalStrut(6));
            }
        }
        status.setForeground(blocked ? Theme.FAIL : Theme.MUTED);
        status.setText(blocked ? Lang.t("install.blocked") : files == 0 ? Lang.t("install.nothing")
                : Lang.t("install.summary", Lang.plural("install.files", files), Ui.size(bytes)));
        installButton.setEnabled(!blocked && files > 0);
        installButton.setToolTipText(blocked ? Lang.t("install.blocked") : null);
        planPanel.revalidate();
        planPanel.repaint();
    }

    private void tree(JPanel into, DependencyResolver.Node n, int depth, ContentInstaller.Plan plan) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(3, depth * 22, 3, 0));
        JPanel left = Ui.row(6);
        if (depth > 0) {
            left.add(Ui.label("└", 13, Font.PLAIN, Theme.MUTED));
        }
        if (n.status() == DependencyResolver.Status.OPTIONAL || (n.relation() == dev.lumora.launcher.core.platform.PlatformModel.DependencyKind.OPTIONAL
                && optional.contains(n.projectId()))) {
            JCheckBox box = new JCheckBox();
            box.setOpaque(false);
            box.setSelected(optional.contains(n.projectId()));
            box.setToolTipText(Lang.t("install.optional.tip"));
            box.addActionListener(e -> {
                if (box.isSelected()) {
                    optional.add(n.projectId());
                } else {
                    optional.remove(n.projectId());
                }
                plan();
            });
            left.add(box);
        }
        left.add(Ui.label(n.title(), 13.5f, depth == 0 ? Font.BOLD : Font.PLAIN, Theme.TEXT));
        if (n.version() != null) {
            left.add(Ui.label(n.version().number(), 12, Font.PLAIN, Theme.MUTED));
        }
        row.add(left, BorderLayout.WEST);
        JPanel right = Ui.row(6);
        if (n.reason() != null && depth > 0) {
            right.add(Ui.label(n.reason(), 12, Font.PLAIN, Theme.MUTED));
        }
        right.add(statusChip(n.status()));
        row.add(right, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        into.add(row);
        for (DependencyResolver.Node c : n.children()) {
            tree(into, c, depth + 1, plan);
        }
    }

    private JComponent statusChip(DependencyResolver.Status s) {
        return switch (s) {
            case NEW -> Ui.chip(Lang.t("install.status.new"), Theme.ACCENT, Theme.ACCENT_TEXT);
            case UPDATE -> Ui.chip(Lang.t("install.status.update"), Theme.ACCENT_2, Theme.ACCENT_TEXT);
            case INSTALLED -> Ui.chip(Lang.t("install.status.installed"), Theme.OK, Theme.OK);
            case OPTIONAL -> Ui.chip(Lang.t("install.status.optional"), Theme.MUTED, Theme.TEXT);
            case MISSING -> Ui.chip(Lang.t("install.status.missing"), Theme.FAIL, Theme.FAIL);
            case CONFLICT -> Ui.chip(Lang.t("install.status.conflict"), Theme.FAIL, Theme.FAIL);
        };
    }

    private JComponent issue(boolean block, String text, String hint) {
        Card c = new Card(new BorderLayout(10, 0)).outline(Theme.alpha(block ? Theme.FAIL : Theme.WARN, 150)).calm().padding(10, 12, 10, 12);
        JLabel icon = new JLabel(Icons.of(Icons.Kind.ALERT, 16, block ? Theme.FAIL : Theme.WARN));
        icon.setVerticalAlignment(JLabel.TOP);
        c.add(icon, BorderLayout.WEST);
        JPanel col = Ui.column();
        col.add(Ui.wrapText(text, 13, Theme.TEXT));
        if (hint != null && !hint.isBlank()) {
            col.add(Box.createVerticalStrut(2));
            col.add(Ui.wrapText(hint, 12.5f, Theme.MUTED));
        }
        c.add(col, BorderLayout.CENTER);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 6));
        return c;
    }

    private void install() {
        boolean warnings = plans.stream().anyMatch(p -> p.issues().stream().anyMatch(i -> i.severity() == CompatibilityChecker.Severity.WARN
                && !i.code().equals("up_to_date")));
        if (warnings && JOptionPane.showConfirmDialog(dialog, Lang.t("install.warnings.confirm"), Lang.t("install.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        installButton.setEnabled(false);
        instanceBox.setEnabled(false);
        if (versionBox != null) {
            versionBox.setEnabled(false);
        }
        bar.setVisible(true);
        bar.setIndeterminate(true);
        status.setForeground(Theme.MUTED);
        status.setText(Lang.t("install.running"));
        List<ContentInstaller.Plan> todo = plans;
        Instance t = target;
        ContentInstaller.Target tgt = targetFor(t);
        Set<String> chosen = new HashSet<>(optional);
        running = io.submit(() -> {
            ContentInstaller installer = new ContentInstaller(host.ctx().downloader);
            List<String> installed = new ArrayList<>();
            try {
                for (int k = 0; k < todo.size(); k++) {
                    ContentInstaller.Plan p = todo.get(k);
                    if (k > 0) {
                        // later projects are planned again: a dependency the first one brought is there now
                        ProjectSummary s = summaryFor(p);
                        p = installer.plan(provider(s), tgt, s, type, p.tree().version(), chosen);
                        if (p.blocked()) {
                            throw new IOException(p.issues().stream().filter(i -> i.severity() == CompatibilityChecker.Severity.BLOCK)
                                    .map(CompatibilityChecker.Issue::text).findFirst().orElse(Lang.t("content.error.blocked")));
                        }
                    }
                    if (p.steps().isEmpty()) {
                        continue;
                    }
                    ContentInstaller.Result r = installer.execute(p, summaryFor(p).platform().id(),
                            (bytes, totalBytes, files, filesTotal) -> SwingUtilities.invokeLater(() -> {
                                bar.setIndeterminate(false);
                                bar.setValue((int) (totalBytes > 0 ? bytes * 1000 / totalBytes : files * 1000L / Math.max(1, filesTotal)));
                                status.setText(Lang.t("install.progress", files, filesTotal, Ui.size(bytes), Ui.size(Math.max(bytes, totalBytes))));
                            }));
                    installed.addAll(r.installed());
                }
                SwingUtilities.invokeLater(() -> {
                    dialog.dispose();
                    InstancesPage.info(host, Lang.t("install.done", Lang.plural("install.files", installed.size()), t.name));
                    host.instancesChanged();
                    done.run();
                });
            } catch (IOException | RuntimeException e) {
                boolean cancelled = Thread.currentThread().isInterrupted() || e instanceof java.io.InterruptedIOException;
                Log.warn("Install failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    bar.setVisible(false);
                    status.setForeground(Theme.FAIL);
                    status.setText(cancelled ? Lang.t("install.cancelled") : Lang.t("install.failed", e.getMessage()));
                    instanceBox.setEnabled(true);
                    if (versionBox != null) {
                        versionBox.setEnabled(true);
                    }
                    if (!installed.isEmpty()) {
                        host.instancesChanged();
                    }
                    plan();
                });
            }
        });
    }

    /** The project a plan was made for. */
    private ProjectSummary summaryFor(ContentInstaller.Plan p) {
        String id = p.tree().projectId();
        return projects.stream().filter(s -> s.id().equals(id) || s.slug() != null && s.slug().equals(id)).findFirst().orElse(projects.get(0));
    }
}
