package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.RemoteImages;
import dev.lumora.launcher.app.ui.TabBar;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.InstanceLayout;
import dev.lumora.launcher.core.content.CompatibilityChecker;
import dev.lumora.launcher.core.content.ConflictDetector;
import dev.lumora.launcher.core.content.ContentManifest;
import dev.lumora.launcher.core.content.InstalledContent;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.library.InstanceLibrary;
import dev.lumora.launcher.core.loader.LoaderVersion;
import dev.lumora.launcher.core.pack.FileClassifier;
import dev.lumora.launcher.core.platform.ModrinthProvider;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * The page of one instance: header with start and actions, then "Inhalte" (mods, resource packs, shaders, Lumora
 * addons and worlds of this instance only, each switchable, removable, with problems found by the conflict check) and
 * "Einstellungen" (name, icon, loader version, Java, memory, window). Nothing here touches another instance.
 */
final class InstanceView extends JPanel {
    private final PageHost host;
    private final ExecutorService io;
    private final Runnable back;
    private final RemoteImages images;
    private Instance instance;
    private final JPanel body = new JPanel(new BorderLayout());
    private String tab = "content";
    private String section;
    private final JTextField filter = new JTextField();
    private final Set<String> selected = new LinkedHashSet<>();
    private int generation;

    InstanceView(PageHost host, ExecutorService io, Instance instance, Runnable back) {
        super(new BorderLayout(0, 14));
        this.host = host;
        this.io = io;
        this.instance = instance;
        this.back = back;
        this.images = DiscoverPage.images(host, io);
        this.section = instance.kind().modded() ? "mods" : instance.kind() == InstanceKind.LUMORA ? "addons" : "resourcepacks";
        setOpaque(false);
        body.setOpaque(false);
        filter.putClientProperty("JTextField.placeholderText", Lang.t("instance.filter"));
        filter.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        filter.putClientProperty("JTextField.showClearButton", true);
        filter.setPreferredSize(new Dimension(220, Ui.CONTROL_HEIGHT));
        // files dropped onto the instance page go into this instance (modpacks still become their own instance)
        DropSupport.target(this, new DropSupport.Handlers(files -> ImportFlow.open(host, io, files, this.instance, host::openInstance), null, null, null));
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                showBody();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                showBody();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                showBody();
            }
        });
    }

    void showSection(String s) {
        if (s.equals("settings")) {
            tab = "settings";
        } else {
            tab = "content";
            section = s;
        }
        refresh();
    }

    Instance instance() {
        return instance;
    }

    /** Shows the instance anew (after installs, renames, ...). */
    void refresh() {
        instance = host.ctx().instances.find(host.ctx().config, instance.key()).orElse(instance);
        removeAll();
        add(header(), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        showBody();
        revalidate();
        repaint();
    }

    private InstanceLayout folders() {
        return host.ctx().layout(instance);
    }

    // ---------------------------------------------------------------- header

    private JComponent header() {
        JPanel head = Ui.column();
        JButton backButton = Ui.button(Lang.t("instance.back"), null, back);
        backButton.putClientProperty("JButton.buttonType", "borderless");
        head.add(backButton);
        head.add(Box.createVerticalStrut(8));
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(InstanceUi.icon(instance, 64), BorderLayout.WEST);
        JPanel text = Ui.column();
        text.add(Ui.heading(instance.name));
        text.add(Box.createVerticalStrut(4));
        JPanel line = Ui.row(8);
        line.add(Ui.chip(instance.kind().label(), InstanceUi.kindColor(instance.kind()), InstanceUi.kindColor(instance.kind()).brighter()));
        line.add(Ui.label(InstanceUi.summary(instance), 13, Font.PLAIN, Theme.MUTED));
        line.add(Ui.label("· " + InstanceUi.lastPlayed(host.ctx(), instance), 13, Font.PLAIN, Theme.MUTED));
        text.add(line);
        row.add(text, BorderLayout.CENTER);
        JPanel actions = Ui.row(8);
        actions.add(Ui.primary(Lang.t("instances.action.play"), Icons.Kind.PLAY, () -> {
            host.selectInstance(instance);
            host.launch(null, false);
        }));
        actions.add(Ui.openFolderButton(() -> host.openFolder(folders().gameDir())));
        JButton more = Ui.button("", Icons.Kind.MORE, null);
        more.setToolTipText(Lang.t("instance.more"));
        more.addActionListener(e -> moreMenu().show(more, 0, more.getHeight()));
        actions.add(more);
        row.add(actions, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        head.add(row);
        head.add(Box.createVerticalStrut(14));
        TabBar tabs = new TabBar(List.of("content", "settings"), List.of(Lang.t("instance.tab.content"), Lang.t("instance.tab.settings")),
                tab, id -> {
                    tab = id;
                    showBody();
                });
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(tabs);
        return head;
    }

    private JPopupMenu moreMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(InstancesPage.item(Lang.t("instances.action.select"), Icons.Kind.CHECK, () -> host.selectInstance(instance)));
        m.add(InstancesPage.item(Lang.t("instances.action.duplicate"), Icons.Kind.COPY, () -> InstanceActions.duplicate(host, io, instance, c -> {
            instance = c;
            refresh();
        })));
        m.add(InstancesPage.item(Lang.t("instance.repair"), Icons.Kind.REFRESH, () -> {
            host.selectInstance(instance);
            host.launch(null, true);
        }));
        m.addSeparator();
        m.add(InstancesPage.item(Lang.t("backup.create"), Icons.Kind.ARCHIVE, () -> InstanceTools.backupNow(host, io, instance)));
        m.add(InstancesPage.item(Lang.t("backup.manage"), Icons.Kind.CLOCK, () -> InstanceTools.backups(host, io, instance, this::refresh)));
        m.add(InstancesPage.item(Lang.t("export.title"), Icons.Kind.DOWNLOAD, () -> InstanceTools.export(host, io, instance)));
        if (!instance.builtIn) {
            m.add(InstancesPage.item(Lang.t("instances.action.rename"), Icons.Kind.EDIT, () -> InstanceActions.rename(host, instance, r -> {
                instance = r;
                refresh();
            })));
            m.addSeparator();
            m.add(InstancesPage.item(Lang.t("instances.action.delete"), Icons.Kind.TRASH, () -> InstanceActions.delete(host, io, instance, back)));
        }
        return m;
    }

    private void showBody() {
        body.removeAll();
        body.add("settings".equals(tab) ? settings() : content(), BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    // ---------------------------------------------------------------- content

    private List<String> sections() {
        List<String> out = new ArrayList<>();
        if (instance.kind().modded()) {
            out.add("mods");
        }
        if (instance.kind() == InstanceKind.LUMORA) {
            out.add("addons");
        }
        out.add("resourcepacks");
        if (CompatibilityChecker.supports(instance.kind(), ProjectType.SHADER)) {
            out.add("shaderpacks");
        }
        out.add("updates");
        out.add("worlds");
        return out;
    }

    private JComponent content() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel top = Ui.column();
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        List<String> ids = sections();
        if (!ids.contains(section)) {
            section = ids.get(0);
        }
        Path game = folders().gameDir();
        List<String> labels = new ArrayList<>();
        for (String id : ids) {
            int n = count(game, id);
            labels.add(Lang.t("instance.section." + id) + (n < 0 ? "" : " (" + n + ")"));
        }
        bar.add(Ui.segmented(ids, labels, section, id -> {
            section = id;
            selected.clear();
            showBody();
        }), BorderLayout.WEST);
        JPanel right = Ui.row(8);
        right.add(filter);
        ProjectType type = type(section);
        if (type != null && type != ProjectType.DATAPACK) {
            right.add(Ui.button(Lang.t("instance.add_file"), Icons.Kind.PLUS, this::addFile));
            right.add(Ui.primary(Lang.t("instance.add_content"), Icons.Kind.COMPASS, () -> host.discover(type, instance)));
        }
        bar.add(right, BorderLayout.EAST);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        top.add(bar);
        p.add(top, BorderLayout.NORTH);
        JPanel list = Ui.column();
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Ui.scroll(list), BorderLayout.CENTER);
        int gen = ++generation;
        list.add(Ui.label(Lang.t("common.loading"), 13, Font.PLAIN, Theme.MUTED));
        String sec = section;
        io.submit(() -> {
            Object data = load(sec, game);
            SwingUtilities.invokeLater(() -> {
                if (gen != generation) {
                    return;
                }
                list.removeAll();
                fill(list, top, sec, data);
                list.revalidate();
                list.repaint();
            });
        });
        return p;
    }

    private static ProjectType type(String section) {
        return switch (section) {
            case "mods" -> ProjectType.MOD;
            case "resourcepacks" -> ProjectType.RESOURCE_PACK;
            case "shaderpacks" -> ProjectType.SHADER;
            default -> null;
        };
    }

    private int count(Path game, String section) {
        if (section.equals("updates")) {
            return -1;
        }
        ProjectType t = type(section);
        try {
            if (t != null) {
                Path dir = game.resolve(t.folder());
                if (!Files.isDirectory(dir)) {
                    return 0;
                }
                try (var s = Files.list(dir)) {
                    String ext = t == ProjectType.MOD ? ".jar" : ".zip";
                    return (int) s.map(f -> f.getFileName().toString().toLowerCase(Locale.ROOT))
                            .filter(n -> n.endsWith(ext) || n.endsWith(ext + InstalledContent.DISABLED)).count();
                }
            }
            if (section.equals("addons")) {
                Path dir = folders().addonsDir();
                if (!Files.isDirectory(dir)) {
                    return 0;
                }
                try (var s = Files.list(dir)) {
                    return (int) s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.jar(\\.disabled)?")).count();
                }
            }
            if (section.equals("worlds")) {
                Path dir = game.resolve("saves");
                if (!Files.isDirectory(dir)) {
                    return 0;
                }
                try (var s = Files.list(dir)) {
                    return (int) s.filter(Files::isDirectory).count();
                }
            }
        } catch (IOException e) {
            return 0;
        }
        return 0;
    }

    private record ModsData(List<InstalledContent.Item> items, List<ConflictDetector.Problem> problems) {
    }

    private Object load(String section, Path game) {
        if (section.equals("updates")) {
            return InstanceTools.checkUpdates(host, instance);
        }
        ProjectType t = type(section);
        if (t != null) {
            List<InstalledContent.Item> items = InstalledContent.scan(game, t, ContentManifest.load(game));
            List<ConflictDetector.Problem> problems = t == ProjectType.MOD ? ConflictDetector.detect(instance.kind(), items) : List.of();
            return new ModsData(items, problems);
        }
        if (section.equals("addons")) {
            return addons();
        }
        return InstanceLibrary.worlds(game);
    }

    private List<Path> addons() {
        Path dir = folders().addonsDir();
        List<Path> out = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.jar(\\.disabled)?")).sorted().forEach(out::add);
            } catch (IOException e) {
                Log.warn("Could not list addons: " + e.getMessage());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void fill(JPanel list, JPanel top, String section, Object data) {
        if (data instanceof InstanceTools.UpdatesData u) {
            InstanceTools.fillUpdates(list, host, io, instance, u, this::showBody);
            return;
        }
        String q = filter.getText().strip().toLowerCase(Locale.ROOT);
        if (data instanceof ModsData md) {
            if (!md.problems().isEmpty()) {
                list.add(problems(md.problems()));
                list.add(Box.createVerticalStrut(10));
            }
            List<InstalledContent.Item> items = md.items().stream()
                    .filter(i -> q.isEmpty() || i.displayName().toLowerCase(Locale.ROOT).contains(q) || i.fileName().toLowerCase(Locale.ROOT).contains(q))
                    .toList();
            if (!items.isEmpty()) {
                list.add(bulkBar(md.items()));
                list.add(Box.createVerticalStrut(8));
            }
            if (md.items().isEmpty()) {
                list.add(empty(section));
            }
            for (InstalledContent.Item item : items) {
                list.add(row(item));
                list.add(Box.createVerticalStrut(6));
            }
        } else if (section.equals("addons")) {
            List<Path> addons = (List<Path>) data;
            list.add(Ui.wrapText(Lang.t("instance.addons.hint"), 13, Theme.MUTED));
            list.add(Box.createVerticalStrut(10));
            if (addons.isEmpty()) {
                list.add(empty(section));
            }
            for (Path a : addons) {
                if (q.isEmpty() || a.getFileName().toString().toLowerCase(Locale.ROOT).contains(q)) {
                    list.add(addonRow(a));
                    list.add(Box.createVerticalStrut(6));
                }
            }
        } else {
            List<InstanceLibrary.World> worlds = (List<InstanceLibrary.World>) data;
            if (worlds.isEmpty()) {
                list.add(empty(section));
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Lang.locale())
                    .withZone(java.time.ZoneId.systemDefault());
            for (InstanceLibrary.World w : worlds) {
                if (!q.isEmpty() && !w.name().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                Card c = new Card(new BorderLayout(12, 0)).padding(10, 12, 10, 12);
                ImageIcon icon = w.icon() == null ? null : Ui.icon(w.icon(), 40, 40, false);
                c.add(icon != null ? Ui.imageTile(icon, 40, 40) : Ui.letterTile(w.name(), 40, Theme.ACCENT_2), BorderLayout.WEST);
                JPanel t = Ui.column();
                t.add(Ui.label(w.name(), 14, Font.BOLD, Theme.TEXT));
                t.add(Ui.label(Lang.t("gamemode." + w.mode().name().toLowerCase(Locale.ROOT))
                        + (w.hardcore() ? " · Hardcore" : "") + (w.lastPlayed() == null ? "" : " · " + fmt.format(w.lastPlayed())), 12, Font.PLAIN, Theme.MUTED));
                c.add(t, BorderLayout.CENTER);
                c.add(Ui.openFolderButton(() -> host.openFolder(w.dir())), BorderLayout.EAST);
                c.add(InstanceTools.worldDatapacks(host, io, instance, w, this::showBody), BorderLayout.SOUTH);
                c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(c);
                list.add(Box.createVerticalStrut(6));
            }
        }
    }

    private JComponent empty(String section) {
        Card c = new Card(new BorderLayout()).calm();
        c.add(Ui.wrapText(Lang.t("instance.empty." + section), 13, Theme.MUTED), BorderLayout.CENTER);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 10));
        return c;
    }

    private JComponent problems(List<ConflictDetector.Problem> problems) {
        Card c = new Card(new BorderLayout(0, 6)).outline(Theme.alpha(Theme.WARN, 160)).calm();
        JPanel col = Ui.column();
        JLabel title = Ui.label(Lang.plural("instance.problems", problems.size()), 14, Font.BOLD, Theme.WARN);
        title.setIcon(Icons.of(Icons.Kind.ALERT, 16, Theme.WARN));
        col.add(title);
        for (ConflictDetector.Problem pr : problems) {
            col.add(Box.createVerticalStrut(6));
            col.add(Ui.wrapText("• " + pr.text(), 13, Theme.TEXT));
            if (pr.hint() != null && !pr.hint().isBlank()) {
                col.add(Ui.wrapText("   " + pr.hint(), 12.5f, Theme.MUTED));
            }
        }
        c.add(col, BorderLayout.CENTER);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 10));
        return c;
    }

    private JComponent bulkBar(List<InstalledContent.Item> items) {
        JPanel bar = Ui.row(8);
        bar.add(Ui.button(Lang.t("instance.enable_all"), Icons.Kind.CHECK, () -> setAll(items, true)));
        bar.add(Ui.button(Lang.t("instance.disable_all"), Icons.Kind.X, () -> setAll(items, false)));
        if (!selected.isEmpty()) {
            List<InstalledContent.Item> chosen = items.stream().filter(x -> selected.contains(x.key())).toList();
            bar.add(Ui.button(Lang.t("selection.export"), Icons.Kind.DOWNLOAD, () -> InstanceTools.exportSelected(host, chosen)));
            bar.add(Ui.button(Lang.t("selection.transfer"), Icons.Kind.COPY, () -> InstanceTools.transferSelected(host, io, instance, chosen, this::showBody)));
            bar.add(Ui.danger(Lang.t("instance.remove_selected", selected.size()), Icons.Kind.TRASH, () -> removeSelected(items)));
        }
        return bar;
    }

    private JComponent row(InstalledContent.Item item) {
        Card c = new Card(new BorderLayout(12, 0)).padding(8, 10, 8, 12);
        JPanel west = Ui.row(8);
        JCheckBox pick = new JCheckBox();
        pick.setOpaque(false);
        pick.setSelected(selected.contains(item.key()));
        pick.addActionListener(e -> {
            if (pick.isSelected()) {
                selected.add(item.key());
            } else {
                selected.remove(item.key());
            }
            showBody();
        });
        west.add(pick);
        JPanel iconHolder = new JPanel(new BorderLayout());
        iconHolder.setOpaque(false);
        byte[] png = item.mod() != null ? item.mod().icon() : item.pack() != null ? item.pack().icon() : null;
        ImageIcon local = Ui.icon(png, 36);
        iconHolder.add(local != null ? Ui.imageTile(local, 36, 36) : Ui.letterTile(item.displayName(), 36, Theme.ACCENT_2));
        if (local == null && item.origin() != null && item.origin().iconUrl != null) {
            images.load(item.origin().iconUrl, 36, 36, false, img -> {
                iconHolder.removeAll();
                iconHolder.add(Ui.imageTile(img, 36, 36));
                iconHolder.revalidate();
                iconHolder.repaint();
            });
        }
        west.add(iconHolder);
        c.add(west, BorderLayout.WEST);
        JPanel t = Ui.column();
        JLabel name = Ui.label(item.displayName(), 14, Font.BOLD, item.enabled() ? Theme.TEXT : Theme.MUTED);
        t.add(name);
        String version = item.version();
        StringBuilder sub = new StringBuilder();
        if (version != null && !version.isBlank()) {
            sub.append(version).append(" · ");
        }
        sub.append(item.fileName()).append(" · ").append(Ui.size(item.size()));
        t.add(Ui.label(sub.toString(), 12, Font.PLAIN, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        JPanel east = Ui.row(8);
        if (item.origin() != null && item.origin().platform != null) {
            east.add(Ui.chip(item.origin().explicit() ? "Modrinth" : Lang.t("instance.dependency"), Theme.ACCENT, Theme.ACCENT_TEXT));
        } else {
            east.add(Ui.chip(Lang.t("instance.local_file"), Theme.MUTED, Theme.MUTED));
        }
        ToggleSwitch on = new ToggleSwitch(item.enabled());
        on.setToolTipText(Lang.t(item.enabled() ? "instance.switch_off" : "instance.switch_on"));
        on.onChange(v -> io.submit(() -> {
            try {
                InstalledContent.setEnabled(item, v);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("instance.switch_failed"), e.getMessage()));
            }
            SwingUtilities.invokeLater(this::showBody);
        }));
        east.add(on);
        JButton more = Ui.button("", Icons.Kind.MORE, null);
        more.addActionListener(e -> itemMenu(item).show(more, 0, more.getHeight()));
        east.add(more);
        c.add(east, BorderLayout.EAST);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (item.mod() != null && item.mod().description() != null) {
            c.setToolTipText(item.mod().description().length() > 200 ? item.mod().description().substring(0, 199) + "…" : item.mod().description());
        }
        return c;
    }

    private JPopupMenu itemMenu(InstalledContent.Item item) {
        JPopupMenu m = new JPopupMenu();
        ContentManifest.Entry o = item.origin();
        if (o != null && "modrinth".equals(o.platform) && o.projectId != null) {
            m.add(InstancesPage.item(Lang.t("instance.item.details"), Icons.Kind.INFO, () -> host.discover(item.type(), instance)));
            m.add(InstancesPage.item(Lang.t("instance.item.page"), Icons.Kind.GLOBE,
                    () -> host.browse(ModrinthProvider.pageUrl(item.type(), o.projectId))));
        }
        m.add(InstancesPage.item(Lang.t("instance.item.show"), Icons.Kind.FOLDER, () -> host.openFolder(item.file().getParent())));
        boolean pinned = o != null && o.pinned;
        m.add(InstancesPage.item(Lang.t(pinned ? "updates.unpin" : "updates.pin"), Icons.Kind.LOCK, () -> io.submit(() -> {
            try {
                dev.lumora.launcher.core.content.ContentUpdates.setPinned(folders().gameDir(), item, !pinned);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("updates.pin"), e.getMessage()));
            }
            SwingUtilities.invokeLater(this::showBody);
        })));
        if (item.mod() != null && !item.mod().depends().isEmpty()) {
            m.add(InstancesPage.item(Lang.t("instance.item.dependencies"), Icons.Kind.PUZZLE, () -> JOptionPane.showMessageDialog(host.frame(),
                    String.join("\n", item.mod().depends().keySet()), Lang.t("instance.item.dependencies"), JOptionPane.INFORMATION_MESSAGE)));
        }
        m.addSeparator();
        m.add(InstancesPage.item(Lang.t("instance.item.remove"), Icons.Kind.TRASH, () -> {
            int r = JOptionPane.showConfirmDialog(host.frame(), Lang.t("instance.item.remove.confirm", item.displayName()),
                    Lang.t("instance.item.remove"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.OK_OPTION) {
                io.submit(() -> {
                    try {
                        Path game = folders().gameDir();
                        InstalledContent.remove(game, item, ContentManifest.load(game));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> host.error(Lang.t("instance.item.remove.failed"), e.getMessage()));
                    }
                    SwingUtilities.invokeLater(this::showBody);
                });
            }
        }));
        return m;
    }

    private JComponent addonRow(Path file) {
        String name = file.getFileName().toString();
        boolean enabled = !name.toLowerCase(Locale.ROOT).endsWith(InstalledContent.DISABLED);
        Card c = new Card(new BorderLayout(12, 0)).padding(8, 12, 8, 12);
        String shown = enabled ? name : name.substring(0, name.length() - InstalledContent.DISABLED.length());
        c.add(Ui.letterTile(shown, 36, Theme.ACCENT), BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(shown.replaceAll("(?i)\\.jar$", ""), 14, Font.BOLD, enabled ? Theme.TEXT : Theme.MUTED));
        try {
            t.add(Ui.label(Lang.t("instance.addon.native") + " · " + Ui.size(Files.size(file)), 12, Font.PLAIN, Theme.MUTED));
        } catch (IOException e) {
            t.add(Ui.label(Lang.t("instance.addon.native"), 12, Font.PLAIN, Theme.MUTED));
        }
        c.add(t, BorderLayout.CENTER);
        ToggleSwitch on = new ToggleSwitch(enabled);
        on.onChange(v -> {
            try {
                Path target = file.resolveSibling(v ? shown : shown + InstalledContent.DISABLED);
                if (!Files.exists(target)) {
                    Files.move(file, target);
                }
            } catch (IOException e) {
                host.error(Lang.t("instance.switch_failed"), e.getMessage());
            }
            showBody();
        });
        c.add(on, BorderLayout.EAST);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void setAll(List<InstalledContent.Item> items, boolean on) {
        io.submit(() -> {
            List<String> failed = new ArrayList<>();
            for (InstalledContent.Item i : items) {
                try {
                    InstalledContent.setEnabled(i, on);
                } catch (IOException e) {
                    failed.add(i.fileName());
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (!failed.isEmpty()) {
                    host.error(Lang.t("instance.switch_failed"), String.join("\n", failed));
                }
                showBody();
            });
        });
    }

    private void removeSelected(List<InstalledContent.Item> items) {
        List<InstalledContent.Item> chosen = items.stream().filter(i -> selected.contains(i.key())).toList();
        int r = JOptionPane.showConfirmDialog(host.frame(), Lang.t("instance.remove_selected.confirm", chosen.size()),
                Lang.t("instance.item.remove"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        io.submit(() -> {
            Path game = folders().gameDir();
            ContentManifest m = ContentManifest.load(game);
            for (InstalledContent.Item i : chosen) {
                try {
                    InstalledContent.remove(game, i, m);
                } catch (IOException e) {
                    Log.warn("Could not remove " + i.fileName() + ": " + e.getMessage());
                }
            }
            SwingUtilities.invokeLater(() -> {
                selected.clear();
                showBody();
            });
        });
    }

    /** Copies local files into the right folder after checking what they are; asks before replacing anything. */
    private void addFile() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle(Lang.t("instance.add_file"));
        if (fc.showOpenDialog(host.frame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<Path> files = new ArrayList<>();
        for (java.io.File f : fc.getSelectedFiles()) {
            files.add(f.toPath());
        }
        addFiles(files);
    }

    void addFiles(List<Path> files) {
        ImportFlow.addFiles(host, io, instance, files, this::showBody);
    }

    // ---------------------------------------------------------------- settings

    private JComponent settings() {
        JPanel col = Ui.column();
        if (instance.builtIn) {
            Card c = new Card(new BorderLayout(0, 8)).calm();
            JPanel t = Ui.column();
            t.add(Ui.label(Lang.t("instance.settings.builtin.title"), 15, Font.BOLD, Theme.TEXT));
            t.add(Box.createVerticalStrut(6));
            t.add(Ui.wrapText(Lang.t("instance.settings.builtin.text"), 13, Theme.MUTED));
            c.add(t, BorderLayout.CENTER);
            JPanel b = Ui.row(8);
            b.add(Ui.button(Lang.t("settings.title"), Icons.Kind.GEAR, () -> host.showPage("settings/game")));
            c.add(b, BorderLayout.SOUTH);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 10));
            col.add(c);
            JComponent folder = host.folderChooser(instance);
            if (folder != null) {
                Card f = new Card(new BorderLayout(0, 8)).calm();
                JPanel ft = Ui.column();
                ft.add(Ui.label(Lang.t("instance.settings.folder"), 15, Font.BOLD, Theme.TEXT));
                ft.add(Box.createVerticalStrut(8));
                ft.add(folder);
                f.add(ft, BorderLayout.CENTER);
                f.setAlignmentX(Component.LEFT_ALIGNMENT);
                f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height + 10));
                col.add(Box.createVerticalStrut(12));
                col.add(f);
            }
            return Ui.scroll(col);
        }
        Instance edit = instance.copy();
        Card general = new Card(new BorderLayout()).calm();
        JPanel g = Ui.column();
        g.add(Ui.label(Lang.t("instance.settings.general"), 15, Font.BOLD, Theme.TEXT));
        g.add(Ui.caption(Lang.t("instance.settings.name")));
        JTextField name = new JTextField(edit.name);
        name.setMaximumSize(new Dimension(420, Ui.CONTROL_HEIGHT));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        g.add(name);
        g.add(Ui.caption(Lang.t("instance.settings.icon")));
        JPanel iconRow = Ui.row(8);
        iconRow.add(InstanceUi.icon(edit, 40));
        iconRow.add(Ui.button(Lang.t("instance.settings.icon.choose"), Icons.Kind.IMAGE, () -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG / JPEG", "png", "jpg", "jpeg"));
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                try {
                    instance = host.ctx().instances.setIcon(instance, fc.getSelectedFile().toPath());
                    host.instancesChanged();
                    refresh();
                } catch (IOException e) {
                    host.error(Lang.t("instance.settings.icon"), e.getMessage());
                }
            }
        }));
        g.add(iconRow);
        JComboBox<String> loader = null;
        JCheckBox pin = null;
        JCheckBox lumoraBox = null;
        List<String> loaderIds = new ArrayList<>();
        if (edit.kind().modded()) {
            g.add(Ui.caption(Lang.t("instance.settings.loader_version", edit.kind().label())));
            loader = Ui.dropdown(List.of(Lang.t("common.loading")));
            loader.setEnabled(false);
            loader.setMaximumSize(new Dimension(420, Ui.CONTROL_HEIGHT));
            loader.setAlignmentX(Component.LEFT_ALIGNMENT);
            g.add(loader);
            pin = new JCheckBox(Lang.t("instance.settings.pin"), edit.loaderPinned);
            pin.setOpaque(false);
            g.add(pin);
            if (host.ctx().installer.hasLumoraFor(edit.kind().loaderId(), edit.minecraftVersion)) {
                lumoraBox = new JCheckBox(Lang.t("create.lumora_client"), edit.lumoraClient);
                lumoraBox.setOpaque(false);
                lumoraBox.setToolTipText(Lang.t("create.lumora_client.tip"));
                g.add(lumoraBox);
            } else {
                g.add(Ui.wrapText(Lang.t("instance.settings.lumora_unavailable", edit.kind().label(), edit.minecraftVersion), 12f, Theme.MUTED));
            }
            JComboBox<String> box = loader;
            io.submit(() -> {
                try {
                    List<LoaderVersion> versions = host.ctx().installer.loaders().versions(edit.kind().loaderId(), edit.minecraftVersion);
                    SwingUtilities.invokeLater(() -> {
                        box.removeAllItems();
                        loaderIds.clear();
                        loaderIds.add("");
                        box.addItem(Lang.t("instance.settings.loader.recommended"));
                        for (LoaderVersion v : versions) {
                            loaderIds.add(v.version());
                            box.addItem(v.label() + (v.recommended() ? " ★" : v.stable() ? "" : " (" + Lang.t("version.channel.beta") + ")"));
                        }
                        int at = edit.loaderVersion == null ? 0 : loaderIds.indexOf(edit.loaderVersion);
                        box.setSelectedIndex(Math.max(0, at));
                        box.setEnabled(true);
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        box.removeAllItems();
                        box.addItem(edit.loaderVersion != null ? edit.loaderVersion : Lang.t("instance.settings.loader.offline"));
                    });
                }
            });
        }
        general.add(g, BorderLayout.CENTER);
        general.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(general);
        col.add(Box.createVerticalStrut(12));

        Card javaCard = new Card(new BorderLayout()).calm();
        JPanel j = Ui.column();
        j.add(Ui.label(Lang.t("instance.settings.java_memory"), 15, Font.BOLD, Theme.TEXT));
        j.add(Box.createVerticalStrut(4));
        j.add(Ui.wrapText(Lang.t("instance.settings.java_memory.hint"), 12.5f, Theme.MUTED));
        j.add(Ui.caption(Lang.t("instance.settings.java")));
        JPanel javaRow = Ui.row(8);
        JTextField java = new JTextField(edit.javaPath == null ? "" : edit.javaPath, 32);
        java.putClientProperty("JTextField.placeholderText", Lang.t("instance.settings.java.auto"));
        javaRow.add(Ui.controlHeight(java));
        javaRow.add(Ui.button(Lang.t("common.browse"), Icons.Kind.FOLDER, () -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                java.setText(fc.getSelectedFile().getAbsolutePath());
            }
        }));
        j.add(javaRow);
        j.add(Ui.caption(Lang.t("instance.settings.memory")));
        JCheckBox ownMemory = new JCheckBox(Lang.t("instance.settings.memory.own"), edit.maxMemoryMb != null);
        ownMemory.setOpaque(false);
        j.add(ownMemory);
        JSpinner max = new JSpinner(new SpinnerNumberModel(edit.maxMemoryMb != null ? edit.maxMemoryMb : host.ctx().config.maxMemoryMb,
                1024, 65536, 512));
        JSpinner min = new JSpinner(new SpinnerNumberModel(edit.minMemoryMb != null ? edit.minMemoryMb : host.ctx().config.minMemoryMb,
                256, 65536, 256));
        JPanel memRow = Ui.row(8);
        memRow.add(Ui.label(Lang.t("instance.settings.memory.max"), 13, Font.PLAIN, Theme.TEXT));
        memRow.add(Ui.controlHeight(max));
        memRow.add(Ui.label(Lang.t("instance.settings.memory.min"), 13, Font.PLAIN, Theme.TEXT));
        memRow.add(Ui.controlHeight(min));
        memRow.add(Ui.label("MB", 13, Font.PLAIN, Theme.MUTED));
        j.add(memRow);
        Runnable memEnable = () -> {
            max.setEnabled(ownMemory.isSelected());
            min.setEnabled(ownMemory.isSelected());
        };
        ownMemory.addActionListener(e -> memEnable.run());
        memEnable.run();
        j.add(Ui.caption(Lang.t("instance.settings.window")));
        JTextField width = new JTextField(edit.windowWidth == null ? "" : edit.windowWidth.toString(), 6);
        JTextField height = new JTextField(edit.windowHeight == null ? "" : edit.windowHeight.toString(), 6);
        width.putClientProperty("JTextField.placeholderText", "auto");
        height.putClientProperty("JTextField.placeholderText", "auto");
        JPanel winRow = Ui.row(8);
        winRow.add(Ui.controlHeight(width));
        winRow.add(Ui.label("×", 13, Font.PLAIN, Theme.MUTED));
        winRow.add(Ui.controlHeight(height));
        j.add(winRow);
        j.add(Ui.caption(Lang.t("instance.settings.jvm_args")));
        JTextField jvm = new JTextField(String.join(" ", edit.jvmArgs == null ? List.of() : edit.jvmArgs), 40);
        jvm.putClientProperty("JTextField.placeholderText", "-XX:+UseG1GC");
        j.add(Ui.controlHeight(jvm));
        javaCard.add(j, BorderLayout.CENTER);
        javaCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(javaCard);
        col.add(Box.createVerticalStrut(12));

        JLabel error = Ui.label(" ", 12.5f, Font.PLAIN, Theme.FAIL);
        col.add(error);
        JComboBox<String> loaderBox = loader;
        JCheckBox pinBox = pin;
        JCheckBox lumoraCheck = lumoraBox;
        JPanel save = Ui.row(8);
        save.add(Ui.primary(Lang.t("common.save"), Icons.Kind.CHECK, () -> {
            Instance c = instance.copy();
            c.name = name.getText().strip();
            if (loaderBox != null && loaderBox.isEnabled() && loaderBox.getSelectedIndex() >= 0 && loaderBox.getSelectedIndex() < loaderIds.size()) {
                String v = loaderIds.get(loaderBox.getSelectedIndex());
                c.loaderVersion = v.isEmpty() ? null : v;
            }
            if (pinBox != null) {
                c.loaderPinned = pinBox.isSelected() && c.loaderVersion != null;
            }
            if (lumoraCheck != null) {
                c.lumoraClient = lumoraCheck.isSelected();
            }
            c.javaPath = java.getText().isBlank() ? null : java.getText().strip();
            c.maxMemoryMb = ownMemory.isSelected() ? (Integer) max.getValue() : null;
            c.minMemoryMb = ownMemory.isSelected() ? (Integer) min.getValue() : null;
            try {
                c.windowWidth = width.getText().isBlank() ? null : Integer.valueOf(width.getText().strip());
                c.windowHeight = height.getText().isBlank() ? null : Integer.valueOf(height.getText().strip());
            } catch (NumberFormatException e) {
                error.setText(Lang.t("instance.settings.window.invalid"));
                return;
            }
            List<String> args = new ArrayList<>();
            for (String a : jvm.getText().strip().split("\\s+")) {
                if (!a.isBlank()) {
                    args.add(a);
                }
            }
            c.jvmArgs = args;
            try {
                host.ctx().instances.save(c);
                instance = c;
                error.setText(" ");
                host.instancesChanged();
                refresh();
                InstancesPage.info(host, Lang.t("instance.settings.saved"));
            } catch (IOException e) {
                error.setText(e.getMessage());
            }
        }));
        col.add(save);
        return Ui.scroll(col);
    }
}
