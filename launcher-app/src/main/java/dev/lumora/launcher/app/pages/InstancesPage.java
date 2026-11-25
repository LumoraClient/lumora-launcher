package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.app.ui.WrapLayout;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * "Instanzen": every instance in one place, the Lumora versions and the player's own (Vanilla, Fabric, Quilt, Forge,
 * NeoForge, Lumora). Cards start the instance directly or open its page; a right click offers the other actions.
 * Search, sort and grid/list view are remembered.
 */
public final class InstancesPage extends JPanel {
    private final PageHost host;
    private final ExecutorService io;
    private final CardLayout cards = new CardLayout();
    private final JPanel listView = new JPanel(new BorderLayout(0, 14));
    private final JPanel content = Ui.column();
    private final JTextField search = new JTextField();
    private final JLabel summary = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private InstanceView detail;
    private JButton importButton;
    /** Drop zones, shown while something is dragged over the page. */
    private final JPanel zones = Ui.row(12);
    private final javax.swing.Timer hideZones = new javax.swing.Timer(700, e -> setZones(false));

    public InstancesPage(PageHost host, ExecutorService io) {
        this.host = host;
        this.io = io;
        setOpaque(false);
        setLayout(cards);
        listView.setOpaque(false);

        JPanel head = Ui.column();
        head.add(Ui.pageTitle(Lang.t("nav.instances"), Lang.t("instances.subtitle")));
        head.add(Box.createVerticalStrut(14));
        JPanel tools = new JPanel(new BorderLayout(10, 0));
        tools.setOpaque(false);
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = Ui.row(8);
        search.putClientProperty("JTextField.placeholderText", Lang.t("instances.search"));
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(260, Ui.CONTROL_HEIGHT));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });
        left.add(search);
        List<String> sortIds = List.of("recent", "name", "version", "loader");
        JComboBox<String> sort = Ui.choice(sortIds, sortIds.stream().map(s -> Lang.t("instances.sort." + s)).toList(),
                host.ctx().config.instancesSort, id -> {
                    host.ctx().config.instancesSort = id;
                    host.saveConfig();
                    rebuild();
                });
        sort.setToolTipText(Lang.t("instances.sort"));
        left.add(sort);
        left.add(Ui.segmented(List.of("grid", "list"), List.of(Lang.t("view.grid"), Lang.t("view.list")), host.ctx().config.instancesView, id -> {
            host.ctx().config.instancesView = id;
            host.saveConfig();
            rebuild();
        }));
        tools.add(left, BorderLayout.WEST);
        JPanel right = Ui.row(8);
        importButton = Ui.button(Lang.t("instances.import"), Icons.Kind.DOWNLOAD, this::importPack);
        right.add(importButton);
        right.add(Ui.primary(Lang.t("instances.create"), Icons.Kind.PLUS, this::create));
        tools.add(right, BorderLayout.EAST);
        tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        head.add(tools);
        head.add(Box.createVerticalStrut(10));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(summary);
        hideZones.setRepeats(false);
        zones.add(zone(Icons.Kind.DOWNLOAD, Lang.t("drop.zone.import"), Lang.t("drop.zone.import.hint"),
                new DropSupport.Handlers(files -> ImportFlow.open(host, io, files, null, this::open), null, null, this::dragActive)));
        zones.add(zone(Icons.Kind.COPY, Lang.t("drop.zone.copy"), Lang.t("drop.zone.copy.hint"),
                new DropSupport.Handlers(null, key -> host.ctx().instances.find(host.ctx().config, key)
                        .ifPresent(i -> InstanceActions.duplicate(host, io, i, this::open)), null, this::dragActive)));
        zones.setVisible(false);
        head.add(Box.createVerticalStrut(8));
        head.add(zones);
        listView.add(head, BorderLayout.NORTH);
        // files dropped anywhere on the page: modpacks become instances, mods and packs ask for the instance
        DropSupport.target(listView, new DropSupport.Handlers(files -> ImportFlow.open(host, io, files, null, this::open), null, null, this::dragActive));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        listView.add(Ui.scroll(content), BorderLayout.CENTER);
        add(listView, "list");
    }

    /** Called when the page is shown. */
    public void refresh() {
        if (detail != null && detail.isShowing()) {
            detail.refresh();
            return;
        }
        rebuild();
    }

    public void showList() {
        cards.show(this, "list");
        rebuild();
    }

    /** Shows one content section ("mods", "updates", "worlds", "settings") of the open instance page. */
    public void showSection(String section) {
        if (detail != null) {
            detail.showSection(section);
        }
    }

    /** Opens the page of one instance. */
    public void open(Instance i) {
        if (detail != null) {
            remove(detail);
        }
        detail = new InstanceView(host, io, i, this::showList);
        add(detail, "detail");
        cards.show(this, "detail");
        revalidate();
        detail.refresh();
    }

    private void rebuild() {
        List<Instance> all = host.ctx().instances.list(host.ctx().config);
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        List<Instance> shown = new ArrayList<>();
        for (Instance i : all) {
            if (q.isEmpty() || i.name.toLowerCase(Locale.ROOT).contains(q) || i.minecraftVersion.contains(q)
                    || i.kind().label().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(i);
            }
        }
        shown.sort(comparator());
        String selected = host.ctx().selectedInstance().key();
        content.removeAll();
        List<Instance> own = shown.stream().filter(i -> !i.builtIn).toList();
        List<Instance> builtIn = shown.stream().filter(i -> i.builtIn).toList();
        if (all.stream().noneMatch(i -> !i.builtIn) && q.isEmpty()) {
            content.add(emptyState());
            content.add(Box.createVerticalStrut(18));
        }
        if (!own.isEmpty()) {
            content.add(Ui.sectionTitle(Lang.t("instances.section.own"), Lang.plural("instances.count", own.size())));
            content.add(group(own, selected));
            content.add(Box.createVerticalStrut(18));
        }
        if (!builtIn.isEmpty()) {
            content.add(Ui.sectionTitle(Lang.t("instances.section.lumora"), Lang.t("instances.section.lumora.hint")));
            content.add(group(builtIn, selected));
        }
        if (shown.isEmpty() && !q.isEmpty()) {
            content.add(Ui.label(Lang.t("instances.no_match", search.getText().strip()), 14, Font.PLAIN, Theme.MUTED));
        }
        summary.setText(Lang.t("instances.summary", own.size(), builtIn.size()));
        content.revalidate();
        content.repaint();
    }

    private Comparator<Instance> comparator() {
        return switch (host.ctx().config.instancesSort) {
            case "name" -> Comparator.comparing(i -> i.name.toLowerCase(Locale.ROOT));
            case "version" -> Comparator.comparing((Instance i) -> versionKey(i.minecraftVersion)).reversed();
            case "loader" -> Comparator.comparing((Instance i) -> i.kind().ordinal()).thenComparing(i -> i.name.toLowerCase(Locale.ROOT));
            default -> Comparator.comparingLong((Instance i) -> InstanceUi.lastPlayedMillis(host.ctx(), i)).reversed();
        };
    }

    /** Sortable form of a version ("1.21.4" -> "00001.00021.00004"; "26.3" sorts after "1.21"). */
    static String versionKey(String v) {
        StringBuilder sb = new StringBuilder();
        for (String p : v.split("[.\\-]")) {
            sb.append(p.matches("\\d{1,5}") ? String.format("%05d", Integer.parseInt(p)) : p).append('.');
        }
        return sb.toString();
    }

    private JComponent emptyState() {
        Card c = new Card(new BorderLayout(14, 0)).calm();
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel text = Ui.column();
        text.add(Ui.heading(Lang.t("instances.empty.title")));
        text.add(Box.createVerticalStrut(6));
        text.add(Ui.wrapText(Lang.t("instances.empty.text"), 13, Theme.MUTED));
        c.add(text, BorderLayout.CENTER);
        JPanel actions = Ui.row(8);
        actions.add(Ui.primary(Lang.t("instances.create"), Icons.Kind.PLUS, this::create));
        actions.add(Ui.button(Lang.t("instances.find_modpack"), Icons.Kind.COMPASS,
                () -> host.discover(dev.lumora.launcher.core.platform.ProjectType.MODPACK, null)));
        c.add(actions, BorderLayout.SOUTH);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 20));
        return c;
    }

    private JComponent group(List<Instance> list, String selected) {
        boolean grid = "grid".equals(host.ctx().config.instancesView);
        JPanel p = grid ? new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12)) : Ui.column();
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (grid) {
            p.setBorder(BorderFactory.createEmptyBorder(0, -12, 0, 0));
        }
        for (Instance i : list) {
            JComponent c = card(i, i.key().equals(selected), grid);
            p.add(c);
            if (!grid) {
                p.add(Box.createVerticalStrut(8));
            }
        }
        return p;
    }

    private JComponent card(Instance i, boolean selected, boolean grid) {
        Card c = new Card(new BorderLayout(12, 0));
        c.padding(12, 14, 12, 12);
        if (selected) {
            c.outline(Theme.ACCENT);
        }
        c.add(InstanceUi.icon(i, grid ? 46 : 40), BorderLayout.WEST);
        JPanel text = Ui.column();
        JLabel name = Ui.label(i.name, 14.5f, Font.BOLD, Theme.TEXT);
        text.add(name);
        text.add(Box.createVerticalStrut(3));
        JPanel line = Ui.row(6);
        line.add(Ui.chip(i.kind().label(), InstanceUi.kindColor(i.kind()), InstanceUi.kindColor(i.kind()).brighter()));
        line.add(Ui.label(i.minecraftVersion + (i.kind().modded() && i.loaderVersion != null ? " · " + i.loaderVersion : ""),
                12.5f, Font.PLAIN, Theme.MUTED));
        text.add(line);
        text.add(Box.createVerticalStrut(3));
        text.add(Ui.label(InstanceUi.origin(i) + " · " + InstanceUi.lastPlayed(host.ctx(), i), 12, Font.PLAIN, Theme.MUTED));
        c.add(text, BorderLayout.CENTER);
        // drag: onto another instance or the copy zone; drop: files into this instance, an instance to merge
        DropSupport.instanceSource(c, i);
        DropSupport.target(c, new DropSupport.Handlers(files -> ImportFlow.open(host, io, files, i, this::open),
                key -> host.ctx().instances.find(host.ctx().config, key).ifPresent(src -> InstanceActions.mergeInto(host, io, src, i, this::open)),
                on -> c.outline(on ? Theme.ACCENT_2 : selected ? Theme.ACCENT : Theme.BORDER), this::dragActive));
        JButton play = Ui.button("", Icons.Kind.PLAY, () -> {
            host.selectInstance(i);
            host.launch(null, false);
        });
        play.setToolTipText(Lang.t("instances.play_tip", i.name));
        c.add(play, BorderLayout.EAST);
        if (grid) {
            c.setPreferredSize(new Dimension(360, Math.max(86, c.getPreferredSize().height)));
        } else {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.setToolTipText(InstanceUi.summary(i));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
                    open(i);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            private void popup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu(i).show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        c.addMouseListener(mouse);
        name.addMouseListener(mouse);
        text.addMouseListener(mouse);
        return c;
    }

    /** Context menu of an instance card. */
    JPopupMenu menu(Instance i) {
        JPopupMenu m = new JPopupMenu();
        m.add(item(Lang.t("instances.action.play"), Icons.Kind.PLAY, () -> {
            host.selectInstance(i);
            host.launch(null, false);
        }));
        m.add(item(Lang.t("instances.action.open"), Icons.Kind.INFO, () -> open(i)));
        m.add(item(Lang.t("instances.action.select"), Icons.Kind.CHECK, () -> {
            host.selectInstance(i);
            rebuild();
        }));
        m.add(item(Lang.t("common.open_folder"), Icons.Kind.FOLDER, () -> host.openFolder(host.ctx().layout(i).gameDir())));
        m.addSeparator();
        m.add(item(Lang.t("instances.action.duplicate"), Icons.Kind.COPY, () -> InstanceActions.duplicate(host, io, i, this::open)));
        if (!i.builtIn) {
            m.add(item(Lang.t("instances.action.rename"), Icons.Kind.EDIT, () -> InstanceActions.rename(host, i, r -> rebuild())));
            m.addSeparator();
            m.add(item(Lang.t("instances.action.delete"), Icons.Kind.TRASH, () -> InstanceActions.delete(host, io, i, this::showList)));
        }
        return m;
    }

    static JMenuItem item(String text, Icons.Kind icon, Runnable action) {
        JMenuItem it = new JMenuItem(text, Icons.of(icon, 15, Theme.TEXT));
        it.addActionListener(e -> action.run());
        return it;
    }

    private void create() {
        CreateInstanceDialog.show(host, io, this::open);
    }

    private void importPack() {
        JPopupMenu m = new JPopupMenu();
        m.add(item(Lang.t("instances.import.file"), Icons.Kind.ARCHIVE, () -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle(Lang.t("instances.import.title"));
            fc.setFileFilter(new FileNameExtensionFilter(Lang.t("import.pack_filter"), "mrpack", "zip"));
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                Path file = fc.getSelectedFile().toPath();
                ImportFlow.open(host, io, List.of(file), null, this::open);
            }
        }));
        m.add(item(Lang.t("instances.import.launcher"), Icons.Kind.FOLDER, () -> LauncherImportDialog.show(host, io, null, this::open)));
        m.show(importButton, 0, importButton.getHeight());
    }

    private void dragActive() {
        setZones(true);
        hideZones.restart();
    }

    private void setZones(boolean on) {
        if (zones.isVisible() != on) {
            zones.setVisible(on);
            listView.revalidate();
            listView.repaint();
        }
    }

    /** A drop zone: large target with icon, title and hint; outlined while something it accepts is over it. */
    private JComponent zone(Icons.Kind icon, String title, String hint, DropSupport.Handlers h) {
        Card c = new Card(new BorderLayout(10, 0)).outline(Theme.alpha(Theme.ACCENT, 140)).calm().padding(12, 14, 12, 14);
        JLabel l = new JLabel(Icons.of(icon, 20, Theme.ACCENT_HOVER));
        c.add(l, BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(title, 14, Font.BOLD, Theme.TEXT));
        t.add(Ui.label(hint, 12, Font.PLAIN, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        c.setPreferredSize(new Dimension(340, 64));
        DropSupport.target(c, new DropSupport.Handlers(h.files(), h.instance(),
                on -> c.outline(on ? Theme.ACCENT_2 : Theme.alpha(Theme.ACCENT, 140)), h.active()));
        return c;
    }

    /** Shows a short confirmation that something happened. */
    static void info(PageHost host, String text) {
        JOptionPane.showMessageDialog(host.frame(), text, Lang.t("nav.instances"), JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean isBuiltInLumora(Instance i) {
        return i.builtIn && i.kind() == InstanceKind.LUMORA;
    }
}
