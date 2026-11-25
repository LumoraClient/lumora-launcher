package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.app.ui.VersionGrid;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.loader.LoaderVersion;
import dev.lumora.launcher.core.loader.ModLoader;
import dev.lumora.launcher.core.mojang.MetadataService;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.profile.ProfileCatalog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * "Neue Instanz" in three steps: what (own instance, modpack, import) → Minecraft version and loader → name and loader
 * version. A loader that does not exist for the chosen version is said so before the last step; the create button
 * explains below itself why it is not ready.
 */
final class CreateInstanceDialog {
    private final PageHost host;
    private final ExecutorService io;
    private final Consumer<Instance> created;
    private final JDialog dialog;
    private final CardLayout steps = new CardLayout();
    private final JPanel stepPanel = new JPanel(steps);
    private final JLabel[] stepLabels = new JLabel[3];
    private InstanceKind kind = InstanceKind.LUMORA;
    /** Version cards (rebuilt on every filter change) and the chosen Minecraft version. */
    private final JPanel gridHolder = new JPanel(new BorderLayout());
    private String chosen;
    private boolean loading;
    private final JLabel chosenLabel = Ui.label(" ", 13, Font.BOLD, Theme.TEXT);
    private final JCheckBox sharedFolder = new JCheckBox(Lang.t("create.shared_folder"));
    /** Name filled in automatically (replaced when kind or version change, kept once the player typed one). */
    private String autoName;
    private boolean startAtVersions;
    private final JTextField versionFilter = new JTextField();
    private final JCheckBox snapshots = new JCheckBox(Lang.t("create.snapshots"));
    private List<MetadataService.GameVersion> allVersions = List.of();
    private final JLabel versionError = Ui.label(" ", 12.5f, Font.PLAIN, Theme.FAIL);
    private final JTextField name = new JTextField();
    private final JComboBox<String> loaderVersion = Ui.dropdown(List.of());
    private final List<String> loaderIds = new ArrayList<>();
    private final JLabel createError = Ui.label(" ", 12.5f, Font.PLAIN, Theme.FAIL);
    private JButton createButton;
    private JButton next;

    private CreateInstanceDialog(PageHost host, ExecutorService io, Consumer<Instance> created) {
        this.host = host;
        this.io = io;
        this.created = created;
        dialog = new JDialog(host.frame(), Lang.t("create.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    }

    static void show(PageHost host, ExecutorService io, Consumer<Instance> created) {
        show(host, io, created, false);
    }

    /** @param versions start at the version step (own instance) */
    static void show(PageHost host, ExecutorService io, Consumer<Instance> created, boolean versions) {
        show(host, io, created, versions, null);
    }

    /** @param startKind kind chosen at the start (null = Lumora) */
    static void show(PageHost host, ExecutorService io, Consumer<Instance> created, boolean versions, InstanceKind startKind) {
        CreateInstanceDialog d = new CreateInstanceDialog(host, io, created);
        d.startAtVersions = versions;
        if (startKind != null) {
            d.kind = startKind;
        }
        d.open();
    }

    private void open() {
        Card c = new Card(new BorderLayout(0, 14)).fill(Theme.SURFACE).outline(null).radius(0).padding(18, 22, 18, 22);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.heading(Lang.t("create.title")), BorderLayout.WEST);
        JPanel stepper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        stepper.setOpaque(false);
        String[] keys = {"create.step.kind", "create.step.version", "create.step.finish"};
        for (int i = 0; i < 3; i++) {
            stepLabels[i] = Ui.label((i + 1) + "  " + Lang.t(keys[i]), 13, Font.BOLD, Theme.MUTED);
            stepper.add(stepLabels[i]);
        }
        head.add(stepper, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);
        stepPanel.setOpaque(false);
        stepPanel.add(kindStep(), "kind");
        stepPanel.add(versionStep(), "version");
        stepPanel.add(finishStep(), "finish");
        c.add(stepPanel, BorderLayout.CENTER);
        dialog.setContentPane(c);
        JRootPane root = dialog.getRootPane();
        root.registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(860, 640);
        dialog.setLocationRelativeTo(host.frame());
        if (startAtVersions) {
            loadVersions();
            step(1);
        } else {
            step(0);
        }
        dialog.setVisible(true);
    }

    private void step(int i) {
        for (int k = 0; k < 3; k++) {
            stepLabels[k].setForeground(k == i ? Theme.ACCENT_HOVER : k < i ? Theme.TEXT : Theme.MUTED);
        }
        steps.show(stepPanel, i == 0 ? "kind" : i == 1 ? "version" : "finish");
    }

    // ---------------------------------------------------------------- step 1

    private JComponent kindStep() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.add(Ui.label(Lang.t("create.kind.question"), 14, Font.PLAIN, Theme.MUTED), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setOpaque(false);
        grid.add(choice(Icons.Kind.SLIDERS, Lang.t("create.kind.own"), Lang.t("create.kind.own.text"), () -> {
            loadVersions();
            step(1);
        }));
        grid.add(choice(Icons.Kind.ARCHIVE, Lang.t("create.kind.modpack"), Lang.t("create.kind.modpack.text"), () -> {
            dialog.dispose();
            host.discover(ProjectType.MODPACK, null);
        }));
        grid.add(choice(Icons.Kind.DOWNLOAD, Lang.t("create.kind.import"), Lang.t("create.kind.import.text"), () -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(Lang.t("instances.import.filter"), "mrpack", "zip"));
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                Path file = fc.getSelectedFile().toPath();
                dialog.dispose();
                MrpackDialog.show(host, io, file, null, null, created);
            }
        }));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(grid, BorderLayout.NORTH);
        p.add(wrap, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.setOpaque(false);
        south.add(Ui.button(Lang.t("common.cancel"), null, dialog::dispose));
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JComponent choice(Icons.Kind icon, String title, String text, Runnable action) {
        Card c = new Card(new BorderLayout(12, 0));
        c.padding(16, 16, 16, 16);
        JLabel i = new JLabel(Icons.of(icon, 22, Theme.ACCENT_HOVER));
        i.setVerticalAlignment(JLabel.TOP);
        c.add(i, BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label(title, 15, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(4));
        t.add(Ui.wrapText(text, 13, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        };
        c.addMouseListener(click);
        t.addMouseListener(click);
        for (Component child : t.getComponents()) {
            child.addMouseListener(click);
        }
        return c;
    }

    // ---------------------------------------------------------------- step 2

    private JComponent versionStep() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel top = Ui.column();
        top.add(Ui.label(Lang.t("create.loader"), 13, Font.BOLD, Theme.MUTED));
        top.add(Box.createVerticalStrut(6));
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (InstanceKind k : InstanceKind.values()) {
            ids.add(k.id());
            labels.add(k.label());
        }
        top.add(Ui.segmented(ids, labels, kind.id(), id -> {
            kind = InstanceKind.of(id);
            filterVersions();
        }));
        top.add(Box.createVerticalStrut(10));
        JPanel filterRow = Ui.row(8);
        versionFilter.putClientProperty("JTextField.placeholderText", Lang.t("create.version.search"));
        versionFilter.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        versionFilter.setPreferredSize(new Dimension(260, Ui.CONTROL_HEIGHT));
        versionFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterVersions();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterVersions();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterVersions();
            }
        });
        filterRow.add(versionFilter);
        snapshots.setOpaque(false);
        snapshots.addActionListener(e -> loadVersions());
        filterRow.add(snapshots);
        top.add(filterRow);
        p.add(top, BorderLayout.NORTH);
        gridHolder.setOpaque(false);
        JScrollPane sp = Ui.scroll(gridHolder);
        sp.setBorder(BorderFactory.createEmptyBorder());
        p.add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JPanel status = Ui.column();
        chosenLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionError.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.add(chosenLabel);
        status.add(versionError);
        south.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.button(Lang.t("common.back"), null, () -> step(0)));
        next = Ui.primary(Lang.t("common.next"), null, this::toFinish);
        buttons.add(next);
        south.add(buttons, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void loadVersions() {
        loading = true;
        filterVersions();
        boolean snaps = snapshots.isSelected();
        io.submit(() -> {
            try {
                List<MetadataService.GameVersion> list = new MetadataService(host.ctx().downloader, host.ctx().paths.versions()).gameVersions(snaps);
                SwingUtilities.invokeLater(() -> {
                    loading = false;
                    allVersions = list;
                    filterVersions();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    loading = false;
                    allVersions = List.of();
                    filterVersions();
                    versionError.setText(Lang.t("create.version.offline", e.getMessage()));
                });
            }
        });
    }

    /** Versions of the chosen kind matching the search, newest first. */
    private List<String> versionSource() {
        String q = versionFilter.getText().strip();
        List<String> source = new ArrayList<>();
        if (kind == InstanceKind.LUMORA) {
            for (ProfileCatalog.Profile p : host.ctx().catalog.profiles()) {
                if (!source.contains(p.minecraftVersion)) {
                    source.add(p.minecraftVersion);
                }
            }
        } else {
            ModLoader loader = kind.loaderId() == null ? null : host.ctx().installer.loaders().loaders().byId(kind.loaderId()).orElse(null);
            for (MetadataService.GameVersion v : allVersions) {
                if (loader == null || loader.supports(v.id())) {
                    source.add(v.id());
                }
            }
        }
        source.removeIf(v -> !q.isEmpty() && !v.contains(q));
        return source;
    }

    /** Releases group by major version ("1.21"), everything else (snapshots, pre-releases) on one card. */
    private static String familyOf(String version) {
        return version.matches("\\d+\\.\\d+(\\.\\d+)?") ? VersionGrid.family(version) : Lang.t("create.snapshots.family");
    }

    /** Rebuilds the version cards (like the version menu of the start page): a click on a card or chip chooses. */
    private void filterVersions() {
        versionError.setText(" ");
        gridHolder.removeAll();
        List<String> source = loading && kind != InstanceKind.LUMORA ? List.of() : versionSource();
        if (source.isEmpty()) {
            chosen = null;
            String text = loading && kind != InstanceKind.LUMORA ? Lang.t("common.loading")
                    : versionFilter.getText().isBlank() ? Lang.t("create.version.none") : Lang.t("create.version.no_match", versionFilter.getText().strip());
            gridHolder.add(Ui.label(text, 13.5f, Font.PLAIN, Theme.MUTED), BorderLayout.NORTH);
        } else {
            if (chosen == null || !source.contains(chosen)) {
                chosen = source.get(0);
            }
            List<VersionGrid.Version> list = new ArrayList<>();
            for (String v : source) {
                list.add(new VersionGrid.Version(v, v, "Minecraft " + v));
            }
            VersionGrid[] grid = new VersionGrid[1];
            grid[0] = new VersionGrid(list, v -> null, v -> {
                chosen = v;
                grid[0].setSelected(v);
                showChosen();
            }, 104, 8, CreateInstanceDialog::familyOf);
            grid[0].setArt(host::versionArt);
            grid[0].setSelected(chosen);
            gridHolder.add(grid[0], BorderLayout.NORTH);
        }
        showChosen();
        gridHolder.revalidate();
        gridHolder.repaint();
    }

    private void showChosen() {
        chosenLabel.setText(chosen == null ? " " : Lang.t("create.version.chosen", kind.label(), chosen));
    }

    /** Checks that the loader exists for the version (network), then goes to the last step. */
    private void toFinish() {
        String mc = chosen;
        if (mc == null || mc.equals(Lang.t("common.loading"))) {
            versionError.setText(Lang.t("create.version.choose"));
            return;
        }
        name.putClientProperty("JTextField.placeholderText", kind.label() + " " + mc);
        if (name.getText().isBlank() || name.getText().equals(autoName)) {
            name.setText(kind.label() + " " + mc);
        }
        autoName = kind.label() + " " + mc;
        // Lumora and Vanilla of a Lumora version can also be the built-in instance with the shared Minecraft folder
        sharedFolder.setVisible(!kind.modded() && profileFor(mc) != null);
        sharedFolder.setSelected(false);
        name.setEnabled(true);
        loaderVersion.removeAllItems();
        loaderIds.clear();
        if (!kind.modded()) {
            loaderVersion.setEnabled(false);
            loaderVersion.addItem("–");
            createButton.setEnabled(true);
            createError.setText(" ");
            step(2);
            return;
        }
        next.setEnabled(false);
        versionError.setText(Lang.t("create.loader.checking", kind.label()));
        io.submit(() -> {
            try {
                List<LoaderVersion> list = host.ctx().installer.loaders().versions(kind.loaderId(), mc);
                SwingUtilities.invokeLater(() -> {
                    next.setEnabled(true);
                    if (list.isEmpty()) {
                        versionError.setText(Lang.t("create.loader.unavailable", kind.label(), mc));
                        return;
                    }
                    versionError.setText(" ");
                    loaderIds.add("");
                    LoaderVersion rec = list.stream().filter(LoaderVersion::recommended).findFirst().orElse(list.get(0));
                    loaderVersion.addItem(Lang.t("create.loader.recommended", rec.label()));
                    for (LoaderVersion v : list) {
                        loaderIds.add(v.version());
                        loaderVersion.addItem(v.label() + (v.stable() ? "" : " (" + Lang.t("version.channel.beta") + ")"));
                    }
                    loaderVersion.setEnabled(true);
                    createButton.setEnabled(true);
                    createError.setText(" ");
                    step(2);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    next.setEnabled(true);
                    versionError.setText(Lang.t("create.loader.failed", kind.label(), e.getMessage()));
                });
            }
        });
    }

    // ---------------------------------------------------------------- step 3

    private JComponent finishStep() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel form = Ui.column();
        form.add(Ui.caption(Lang.t("create.name")));
        name.setFont(Theme.font(Font.PLAIN, 14f));
        name.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateName();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateName();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateName();
            }
        });
        form.add(name);
        form.add(Ui.caption(Lang.t("create.loader_version")));
        loaderVersion.setAlignmentX(Component.LEFT_ALIGNMENT);
        loaderVersion.setMaximumSize(new Dimension(420, Ui.CONTROL_HEIGHT));
        form.add(loaderVersion);
        form.add(Box.createVerticalStrut(12));
        sharedFolder.setOpaque(false);
        sharedFolder.setFocusable(false);
        sharedFolder.setFont(Theme.font(Font.PLAIN, 13f));
        sharedFolder.setForeground(Theme.TEXT);
        sharedFolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        sharedFolder.setToolTipText(Lang.t("create.shared_folder.tip"));
        sharedFolder.addActionListener(e -> name.setEnabled(!sharedFolder.isSelected()));
        sharedFolder.setVisible(false);
        form.add(sharedFolder);
        form.add(Box.createVerticalStrut(10));
        form.add(Ui.wrapText(Lang.t("create.finish.hint"), 12.5f, Theme.MUTED));
        p.add(form, BorderLayout.NORTH);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(createError, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.button(Lang.t("common.back"), null, () -> step(1)));
        createButton = Ui.primary(Lang.t("create.create"), Icons.Kind.PLUS, this::create);
        buttons.add(createButton);
        south.add(buttons, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    /** Puts the built-in Lumora/Vanilla instance of the chosen version on the list (shared Minecraft folder, nothing copied). */
    private void addBuiltIn() {
        ProfileCatalog.Profile p = profileFor(chosen);
        if (p == null) {
            return;
        }
        dev.lumora.launcher.core.profile.Flavor flavor = kind == InstanceKind.VANILLA
                ? dev.lumora.launcher.core.profile.Flavor.VANILLA : dev.lumora.launcher.core.profile.Flavor.LUMORA;
        try {
            Instance inst = host.ctx().instances.addBuiltIn(host.ctx().config, p.id, flavor);
            host.saveConfig();
            dialog.dispose();
            host.instancesChanged();
            created.accept(inst);
        } catch (IOException e) {
            createError.setText(e.getMessage());
        }
    }

    /** The Lumora version (profile) of a Minecraft version, or null. */
    private ProfileCatalog.Profile profileFor(String mc) {
        return host.ctx().catalog.profiles().stream().filter(p -> p.minecraftVersion.equals(mc)).findFirst().orElse(null);
    }

    private void validateName() {
        String n = name.getText().strip();
        String reason = n.isEmpty() ? Lang.t("instances.error.name_empty")
                : n.length() > InstanceManager.MAX_NAME ? Lang.t("instances.error.name_invalid", InstanceManager.MAX_NAME) : null;
        createError.setText(reason == null ? " " : reason);
        if (createButton != null) {
            createButton.setEnabled(reason == null);
        }
    }

    private void create() {
        if (sharedFolder.isVisible() && sharedFolder.isSelected()) {
            addBuiltIn();
            return;
        }
        Instance spec = new Instance();
        spec.name = name.getText().strip();
        spec.kind = kind.id();
        spec.minecraftVersion = chosen;
        int i = loaderVersion.getSelectedIndex();
        spec.loaderVersion = kind.modded() && i > 0 && i < loaderIds.size() ? loaderIds.get(i) : null;
        spec.loaderPinned = spec.loaderVersion != null;
        try {
            Instance inst = host.ctx().instances.create(spec);
            dialog.dispose();
            host.instancesChanged();
            created.accept(inst);
        } catch (IOException e) {
            createError.setText(e.getMessage());
        }
    }
}
