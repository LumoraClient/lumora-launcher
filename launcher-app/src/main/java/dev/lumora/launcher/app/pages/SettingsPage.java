package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Autostart;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.TabBar;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Tray;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.social.Endpoint;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Launcher settings: one tab per category, search, "Erweiterter Modus" for the settings marked "Erweitert". Settings
 * with their own control are shown as sections, on/off switches as boxes in two columns. Every change is saved right away (no save button). Only settings that really do something are listed.
 */
public final class SettingsPage extends JPanel {
    /** What the window does for settings that reach beyond the config file. */
    public interface Hooks {
        void socialChanged();

        void trayChanged();

        void memoryChanged();

        /** A Discord Rich Presence setting changed (what it shows). */
        void discordChanged();

        /** Something the home page shows changed (Quick Play, instances). */
        void homeChanged();

        /** Looks for a new launcher version on GitHub Releases (shows the result). */
        void checkLauncherUpdate();

    }

    /** @param wide the control fills the whole card (storage overview) instead of sitting on the right */
    private record Setting(String category, Icons.Kind icon, String title, String description, boolean advanced,
                           JComponent control, boolean wide) {
    }

    private StoragePanel storage;

    private static final List<String> CATEGORIES = List.of("general", "appearance", "game", "storage", "chat",
            "integrations", "debug");

    private final PageHost host;
    private final Hooks hooks;
    private final LauncherConfig cfg;
    private final List<Setting> settings = new ArrayList<>();
    private final JPanel list = Ui.column();
    private final TabBar categories = new TabBar(CATEGORIES, CATEGORIES.stream().map(SettingsPage::tabName).toList(), "general",
            this::openCategory);
    private final JTextField search = new JTextField();
    private final ToggleSwitch advanced;
    private String category = "general";

    public SettingsPage(PageHost host, Hooks hooks) {
        super(new BorderLayout(18, 0));
        this.host = host;
        this.hooks = hooks;
        this.cfg = host.ctx().config;
        setOpaque(false);
        advanced = new ToggleSwitch(cfg.advancedSettings);
        advanced.onChange(on -> {
            cfg.advancedSettings = on;
            save();
            rebuild();
        });
        build();

        // title row: name, search, advanced mode, version
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        JLabel title = Ui.label(Lang.t("settings.title"), 22, Font.BOLD, Theme.TEXT);
        title.setFont(Theme.display(32f, Font.BOLD, 22f));
        head.add(title, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        JButton openDir = Ui.openFolderButton(() -> host.openFolder(host.ctx().paths.root()));
        openDir.setToolTipText(host.ctx().paths.root().toString());
        right.add(openDir);
        right.add(search);
        right.add(Ui.label(Lang.t("settings.advanced"), 13, Font.PLAIN, Theme.MUTED));
        right.add(advanced);
        right.add(Ui.label("v" + BuildInfo.VERSION, 13, Font.PLAIN, Theme.MUTED));
        JPanel rightWrap = new JPanel(new java.awt.GridBagLayout());
        rightWrap.setOpaque(false);
        rightWrap.add(right);
        head.add(rightWrap, BorderLayout.EAST);

        // tab row: one tab per category, a line below
        JPanel tabRow = new JPanel(new BorderLayout(12, 0));
        tabRow.setOpaque(false);
        tabRow.add(categories, BorderLayout.CENTER);
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setPreferredSize(new Dimension(10, 1));
        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(tabRow, BorderLayout.CENTER);
        JPanel lineWrap = new JPanel(new BorderLayout());
        lineWrap.setOpaque(false);
        lineWrap.setBorder(BorderFactory.createEmptyBorder(16, 0, 20, 0));
        lineWrap.add(line, BorderLayout.CENTER);
        top.add(lineWrap, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        search.putClientProperty("JTextField.placeholderText", Lang.t("settings.search"));
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.setPreferredSize(new Dimension(230, Ui.CONTROL_HEIGHT));
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
        add(Ui.scroll(list), BorderLayout.CENTER);
        rebuild();
    }

    /** Opens a category (for example "appearance"); unknown names are ignored. */
    public void showCategory(String name) {
        if (CATEGORIES.contains(name)) {
            category = name;
            search.setText("");
            rebuild();
        }
    }

    private void save() {
        host.saveConfig();
    }

    // ------------------------------------------------------------------ settings

    private void add(String cat, Icons.Kind icon, String title, String desc, boolean adv, JComponent control) {
        settings.add(new Setting(cat, icon, title, desc, adv, control, false));
    }

    /** A setting whose control sits below the text across the whole width. */
    private void add(String cat, Icons.Kind icon, String title, String desc, boolean adv, JComponent control, boolean wide) {
        settings.add(new Setting(cat, icon, title, desc, adv, control, wide));
    }

    private void restartHint() {
        JOptionPane.showMessageDialog(host.frame(), Lang.t("common.restart_hint"), Lang.t("settings.category.appearance"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Opens the storage category with a fresh measurement (called when the page is shown with that category). */
    public void refreshStorage() {
        if (storage != null) {
            storage.refresh();
        }
    }

    /** Accent colour presets as tiles plus a field for an own colour; the selected one gets a ring. */
    private JComponent accentPicker() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        JPanel tiles = new JPanel(new dev.lumora.launcher.app.ui.WrapLayout(FlowLayout.LEFT, 4, 2));
        tiles.setOpaque(false);
        for (Theme.Preset preset : Theme.PRESETS) {
            Color c = Theme.parse(preset.hex());
            JComponent s = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean sel = preset.hex().equalsIgnoreCase(cfg.accentColor);
                    g.setColor(c);
                    Theme.fillRound(g, 3, 3, getWidth() - 6, getHeight() - 6, Theme.arc(8), Theme.arc(8));
                    if (sel) {
                        g.setColor(Theme.TEXT);
                        g.setStroke(new java.awt.BasicStroke(2f));
                        Theme.drawRound(g, 1, 1, getWidth() - 3, getHeight() - 3, Theme.arc(10), Theme.arc(10));
                    }
                    g.dispose();
                }
            };
            s.setPreferredSize(new Dimension(38, 38));
            s.setToolTipText(preset.name());
            s.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            s.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setAccent(preset.hex(), p);
                }
            });
            tiles.add(s);
        }
        p.add(tiles, BorderLayout.CENTER);
        // own colour: dashed box with the current colour and its hex code
        JComponent own = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                boolean custom = Theme.PRESETS.stream().noneMatch(x -> x.hex().equalsIgnoreCase(cfg.accentColor));
                g.setColor(custom ? Theme.TEXT : Theme.BORDER);
                g.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
                Theme.drawRound(g, 0, 0, getWidth() - 1, getHeight() - 1, Theme.arc(10), Theme.arc(10));
                Color c = Theme.parse(cfg.accentColor);
                g.setColor(c == null ? Theme.ACCENT : c);
                Theme.fillRound(g, 10, (getHeight() - 26) / 2, 26, 26, Theme.arc(6), Theme.arc(6));
                g.setFont(Theme.font(Font.BOLD, 13f));
                g.setColor(Theme.TEXT);
                g.drawString(Lang.t("settings.accent.custom"), 46, getHeight() / 2 - 2);
                g.setFont(Theme.font(Font.PLAIN, 11.5f));
                g.setColor(Theme.MUTED);
                g.drawString(cfg.accentColor == null ? "" : cfg.accentColor.toUpperCase(Locale.ROOT), 46, getHeight() / 2 + 13);
                g.dispose();
            }
        };
        own.setPreferredSize(new Dimension(132, 46));
        own.setToolTipText(Lang.t("settings.accent.custom_tip"));
        own.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        own.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Color chosen = javax.swing.JColorChooser.showDialog(host.frame(), Lang.t("settings.appearance.accent.title"), Theme.parse(cfg.accentColor));
                if (chosen != null) {
                    setAccent(String.format("#%02X%02X%02X", chosen.getRed(), chosen.getGreen(), chosen.getBlue()), p);
                }
            }
        });
        JPanel ow = new JPanel(new java.awt.GridBagLayout());
        ow.setOpaque(false);
        ow.add(own);
        p.add(ow, BorderLayout.EAST);
        return p;
    }

    private void setAccent(String hex, JComponent picker) {
        if (hex.equalsIgnoreCase(cfg.accentColor)) {
            return;
        }
        cfg.accentColor = hex;
        save();
        picker.repaint();
        restartHint();
    }

    /** Multi-line editor for the environment variables (KEY=VALUE per line). */
    private void editEnvironment(JTextField summary) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(String.join("\n", cfg.environmentVariables), 8, 40);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        if (JOptionPane.showConfirmDialog(host.frame(), new javax.swing.JScrollPane(area), Lang.t("settings.game.env.dialog"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        List<String> parsed = dev.lumora.launcher.core.launch.LaunchOptions.parseEnvironment(area.getText());
        List<String> bad = parsed.stream().filter(v -> !LauncherConfig.isValidEnvironmentVariable(v)).toList();
        if (!bad.isEmpty()) {
            host.error(Lang.t("settings.game.env.title"), Lang.t("settings.game.env.invalid") + String.join("\n", bad));
            return;
        }
        cfg.environmentVariables = new ArrayList<>(parsed);
        save();
        summary.setText(String.join(", ", parsed));
    }

    /** Multi-line editor for the extra JVM arguments. */
    private void editJvm(JTextField field) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(String.join("\n", cfg.extraJvmArgs), 8, 40);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        if (JOptionPane.showConfirmDialog(host.frame(), new javax.swing.JScrollPane(area), Lang.t("settings.game.jvm.dialog"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String s = area.getText().strip();
            cfg.extraJvmArgs = s.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(s.split("\\s+")));
            save();
            field.setText(String.join(" ", cfg.extraJvmArgs));
        }
    }

    /** One chip per Minecraft version plus "Alle"; at least one version stays shown. Empty list = all versions. */
    private JComponent versionChooser() {
        JPanel chips = new JPanel(new dev.lumora.launcher.app.ui.WrapLayout(java.awt.FlowLayout.LEFT, 6, 6));
        chips.setOpaque(false);
        List<dev.lumora.launcher.core.profile.ProfileCatalog.Profile> all = host.ctx().catalog.profiles();
        List<javax.swing.JToggleButton> buttons = new java.util.ArrayList<>();
        Runnable store = () -> {
            List<String> chosen = new java.util.ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if (buttons.get(i).isSelected()) {
                    chosen.add(all.get(i).id);
                }
            }
            cfg.visibleProfiles = chosen.size() == all.size() ? new java.util.ArrayList<>() : chosen;
            if (!chosen.isEmpty() && !chosen.contains(cfg.selectedProfile)) {
                cfg.selectedProfile = chosen.get(0);
            }
            save();
        };
        for (var p : all) {
            boolean on = cfg.visibleProfiles == null || cfg.visibleProfiles.isEmpty() || cfg.visibleProfiles.contains(p.id);
            javax.swing.JToggleButton b = new javax.swing.JToggleButton(p.minecraftVersion, on);
            // same look and height as the other choice buttons (Ui.segmented)
            b.setFont(Theme.font(Font.BOLD, 13f));
            b.setFocusable(false);
            if (!Theme.pixelStyle) {
                b.putClientProperty("JButton.buttonType", "roundRect");
            }
            Ui.controlHeight(b);
            b.addActionListener(e -> {
                if (buttons.stream().noneMatch(javax.swing.AbstractButton::isSelected)) {
                    b.setSelected(true); // keep at least one version
                    return;
                }
                store.run();
            });
            buttons.add(b);
            chips.add(b);
        }
        JButton allButton = button(Lang.t("common.all"));
        allButton.addActionListener(e -> {
            buttons.forEach(b -> b.setSelected(true));
            store.run();
        });
        chips.add(allButton);
        return chips;
    }

    private ToggleSwitch toggle(boolean value, Consumer<Boolean> change) {
        ToggleSwitch t = new ToggleSwitch(value);
        t.onChange(on -> {
            change.accept(on);
            save();
        });
        return t;
    }

    private static JTextField field(String value, String placeholder, int columns) {
        JTextField f = new JTextField(value == null ? "" : value, columns);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setFont(Theme.font(Font.PLAIN, 13.5f));
        Ui.controlHeight(f);
        return f;
    }

    /** Saves a text field on Enter and when it loses focus. */
    private static void onCommit(JTextField f, Consumer<String> commit) {
        f.addActionListener(e -> commit.accept(f.getText().strip()));
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit.accept(f.getText().strip());
            }
        });
    }

    private static JButton button(String text) {
        return Ui.button(text, null, null);
    }

    /**
     * The player's own CurseForge API key: masked field, saved DPAPI-encrypted, never shown again in plain text and never
     * logged. "Prüfen" makes one search request to confirm CurseForge accepts it.
     */
    private JComponent curseForgeKeyRow() {
        JPanel col = Ui.column();
        javax.swing.JPasswordField field = new javax.swing.JPasswordField(28);
        boolean stored = host.ctx().curseForgeKey() != null;
        field.putClientProperty("JTextField.placeholderText", Lang.t(stored ? "settings.integrations.curseforge.stored" : "settings.integrations.curseforge.placeholder"));
        JLabel state = Ui.label(Lang.t(stored ? "settings.integrations.curseforge.state_on" : "settings.integrations.curseforge.state_off"), 12.5f,
                Font.PLAIN, stored ? Theme.OK : Theme.MUTED);
        JPanel buttons = Ui.row(8);
        buttons.add(Ui.controlHeight(field));
        buttons.add(Ui.button(Lang.t("common.save"), Icons.Kind.CHECK, () -> {
            char[] value = field.getPassword();
            try {
                if (value.length == 0) {
                    return;
                }
                host.ctx().setCurseForgeKey(new String(value));
                field.setText("");
                field.putClientProperty("JTextField.placeholderText", Lang.t("settings.integrations.curseforge.stored"));
                state.setText(Lang.t("settings.integrations.curseforge.state_on"));
                state.setForeground(Theme.OK);
            } catch (java.io.IOException e) {
                host.error(Lang.t("settings.integrations.curseforge.title"), e.getMessage());
            } finally {
                java.util.Arrays.fill(value, '\0');
            }
        }));
        buttons.add(Ui.button(Lang.t("settings.integrations.curseforge.test"), Icons.Kind.REFRESH, () -> {
            state.setText(Lang.t("common.loading"));
            state.setForeground(Theme.MUTED);
            new Thread(() -> {
                String result;
                java.awt.Color color;
                try {
                    var cf = host.ctx().repository.require(dev.lumora.launcher.core.platform.Platform.CURSEFORGE);
                    cf.search(new dev.lumora.launcher.core.platform.PlatformModel.SearchQuery("jei", dev.lumora.launcher.core.platform.ProjectType.MOD,
                            null, java.util.List.of(), java.util.List.of(), null, false, null, 0, 1));
                    result = Lang.t("settings.integrations.curseforge.ok");
                    color = Theme.OK;
                } catch (java.io.IOException | RuntimeException e) {
                    result = Lang.t("settings.integrations.curseforge.bad", e.getMessage());
                    color = Theme.FAIL;
                }
                String r = result;
                java.awt.Color c = color;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    state.setText(r);
                    state.setForeground(c);
                });
            }, "curseforge-test").start();
        }));
        buttons.add(Ui.button(Lang.t("common.remove"), Icons.Kind.TRASH, () -> {
            try {
                host.ctx().setCurseForgeKey(null);
                field.putClientProperty("JTextField.placeholderText", Lang.t("settings.integrations.curseforge.placeholder"));
                field.repaint();
                state.setText(Lang.t("settings.integrations.curseforge.state_off"));
                state.setForeground(Theme.MUTED);
            } catch (java.io.IOException e) {
                host.error(Lang.t("settings.integrations.curseforge.title"), e.getMessage());
            }
        }));
        buttons.add(Ui.button(Lang.t("settings.integrations.curseforge.get"), Icons.Kind.GLOBE, () -> host.browse("https://console.curseforge.com/")));
        col.add(buttons);
        col.add(Box.createVerticalStrut(4));
        col.add(state);
        return col;
    }

    private static JPanel row(JComponent... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        for (JComponent c : parts) {
            p.add(c);
        }
        return p;
    }

    /** Builds the controls again from the current settings (after a change made elsewhere). */
    public void reload() {
        settings.clear();
        build();
        rebuild();
    }

    private void build() {
        // --- Allgemein
        JTextField dir = field(cfg.installDir != null ? cfg.installDir : host.ctx().paths.root().toString(), "", 22);
        dir.setEditable(false);
        JButton open = button(Lang.t("common.open"));
        open.addActionListener(e -> host.openFolder(host.ctx().paths.root()));
        JButton change = button(Lang.t("settings.change_dir"));
        change.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(dir.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                Path p = fc.getSelectedFile().toPath().toAbsolutePath();
                cfg.installDir = p.equals(host.ctx().defaultRoot) ? null : p.toString();
                dir.setText(p.toString());
                save();
                JOptionPane.showMessageDialog(host.frame(), Lang.t("settings.general.data_dir.moved"), Lang.t("settings.general.data_dir.title"), JOptionPane.INFORMATION_MESSAGE);
            }
        });
        add("general", Icons.Kind.FOLDER, Lang.t("settings.general.data_dir.title"), Lang.t("settings.general.data_dir.desc"),
                false, row(dir, open, change));
        java.util.function.Supplier<Path> standardDir = () -> dev.lumora.launcher.core.InstanceLayout.standardGameDir(cfg);
        JTextField std = field(standardDir.get().toString(), "", 22);
        std.setEditable(false);
        JButton stdOpen = button(Lang.t("common.open"));
        stdOpen.addActionListener(e -> host.openFolder(standardDir.get()));
        JButton stdChange = button(Lang.t("common.change"));
        stdChange.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(std.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                Path p = fc.getSelectedFile().toPath().toAbsolutePath();
                cfg.standardGameDir = p.equals(dev.lumora.launcher.core.InstanceLayout.minecraftDir(host.ctx().os)) ? null : p.toString();
                std.setText(standardDir.get().toString());
                save();
            }
        });
        add("general", Icons.Kind.FOLDER, Lang.t("settings.general.game_dir.title"), Lang.t("settings.general.game_dir.desc"),
                false, row(std, stdOpen, stdChange));
        JButton launcherUpdate = button(Lang.t("settings.general.update.check"));
        launcherUpdate.addActionListener(e -> hooks.checkLauncherUpdate());
        add("general", Icons.Kind.ROCKET, Lang.t("settings.general.version.title", BuildInfo.VERSION), Lang.t("settings.general.version.desc", "github.com/"
                + dev.lumora.launcher.core.update.LauncherUpdate.REPOSITORY), false,
                row(launcherUpdate));
        add("general", Icons.Kind.BOLT, Lang.t("settings.general.update_direct.title"),
                Lang.t("settings.general.update_direct.desc"),
                false, toggle(cfg.portableSelfUpdate, on -> cfg.portableSelfUpdate = on));
        add("general", Icons.Kind.REFRESH, Lang.t("settings.general.update_auto.title"),
                Lang.t("settings.general.update_auto.desc"), false,
                toggle(cfg.checkForUpdates, on -> cfg.checkForUpdates = on));
        // interface language: German (default) or English, both full; applies after a restart (all texts are built once)
        add("general", Icons.Kind.GLOBE, Lang.t("settings.general.language.title"), Lang.t("settings.general.language.desc"), false,
                Ui.segmented(List.of("de_de", "en_us"), List.of("Deutsch", "English"), cfg.language, v -> {
                    if (!v.equals(cfg.language)) {
                        cfg.language = v;
                        save();
                        javax.swing.JOptionPane.showMessageDialog(this, Lang.t("common.restart_hint"), Lang.t("settings.general.language.title"),
                                javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    }
                }));
        add("general", Icons.Kind.BOLT, Lang.t("settings.general.channel.title"), Lang.t("settings.general.channel.desc"), true,
                Ui.segmented(List.of("stable", "pre"), List.of(Lang.t("settings.general.channel.stable"), Lang.t("settings.general.channel.pre")), cfg.prereleases ? "pre" : "stable", v -> {
                    cfg.prereleases = v.equals("pre");
                    save();
                }));
        ToggleSwitch autostart = new ToggleSwitch(Autostart.enabled());
        autostart.setEnabled(Autostart.supported());
        autostart.onChange(on -> {
            if (!Autostart.set(on)) {
                host.error(Lang.t("settings.general.autostart.short"), Lang.t(on ? "settings.general.autostart.failed_on" : "settings.general.autostart.failed_off"));
            }
        });
        add("general", Icons.Kind.PLAY, Lang.t("settings.general.autostart.title"), Lang.t("settings.general.autostart.desc") + (Autostart.supported() ? "" : Lang.t("settings.general.autostart.installed_only")),
                false, autostart);
        ToggleSwitch tray = toggle(cfg.minimizeToTray, on -> cfg.minimizeToTray = on);
        tray.onChange(on -> hooks.trayChanged());
        tray.setEnabled(Tray.supported());
        add("general", Icons.Kind.ARCHIVE, Lang.t("settings.general.tray.title"),
                Lang.t("settings.general.tray.desc"),
                false, tray);
        JSlider dl = new JSlider(1, 16, cfg.parallelDownloads);
        dl.setOpaque(false);
        dl.setPreferredSize(new Dimension(150, 24));
        JSpinner dlSpin = new JSpinner(new SpinnerNumberModel(cfg.parallelDownloads, 1, 16, 1));
        dlSpin.setPreferredSize(new Dimension(64, Ui.CONTROL_HEIGHT));
        dl.addChangeListener(e -> {
            dlSpin.setValue(dl.getValue());
            if (!dl.getValueIsAdjusting()) {
                cfg.parallelDownloads = dl.getValue();
                save();
            }
        });
        dlSpin.addChangeListener(e -> dl.setValue((Integer) dlSpin.getValue()));
        add("general", Icons.Kind.ARCHIVE, Lang.t("settings.general.downloads.title"),
                Lang.t("settings.general.downloads.desc"), true, row(dl, dlSpin));
        JButton ingame = button(Lang.t("settings.general.ingame.button"));
        ingame.addActionListener(e -> ingameExperience());
        add("general", Icons.Kind.PALETTE, Lang.t("settings.general.ingame.title"), Lang.t("settings.general.ingame.desc"), false, row(ingame));

        // --- Erscheinungsbild
        JComboBox<String> start = new JComboBox<>(new String[]{Lang.t("nav.home_page"), Lang.t("nav.news"), Lang.t("nav.servers"), Lang.t("nav.social")});
        Ui.controlHeight(start);
        List<String> startIds = List.of("home", "news", "servers", "social");
        start.setSelectedIndex(Math.max(0, startIds.indexOf(cfg.startPage)));
        start.addActionListener(e -> {
            cfg.startPage = startIds.get(start.getSelectedIndex());
            save();
        });
        add("appearance", Icons.Kind.MONITOR, Lang.t("settings.appearance.start_page.title"), Lang.t("settings.appearance.start_page.desc"), false, start);
        add("appearance", Icons.Kind.LIBRARY, Lang.t("settings.appearance.snapshots.title"),
                Lang.t("settings.appearance.snapshots.desc"), false,
                toggle(cfg.showSnapshots, on -> cfg.showSnapshots = on));
        ToggleSwitch quick = toggle(cfg.quickPlay, on -> cfg.quickPlay = on);
        quick.onChange(on -> hooks.homeChanged());
        add("appearance", Icons.Kind.SERVER, Lang.t("settings.appearance.quick_play.title"), Lang.t("settings.appearance.quick_play.desc"), false, quick);
        add("appearance", Icons.Kind.PALETTE, Lang.t("settings.appearance.accent.title"), Lang.t("settings.appearance.accent.desc"), false, accentPicker(), true);
        ToggleSwitch pixel = toggle(cfg.pixelStyle, on -> cfg.pixelStyle = on);
        pixel.onChange(on -> restartHint());
        add("appearance", Icons.Kind.CUBE, Lang.t("settings.appearance.pixel.title"), Lang.t("settings.appearance.pixel.desc"),
                false, pixel);
        add("appearance", Icons.Kind.PULSE, Lang.t("settings.appearance.motion.title"), Lang.t("settings.appearance.motion.desc"), false, toggle(cfg.reduceMotion, on -> {
                    cfg.reduceMotion = on;
                    Theme.reduceMotion = on;
                }));
        ToggleSwitch hw = toggle(cfg.hardwareAcceleration, on -> cfg.hardwareAcceleration = on);
        hw.onChange(on -> restartHint());
        add("appearance", Icons.Kind.CHIP, Lang.t("settings.appearance.hw.title"), Lang.t("settings.appearance.hw.desc"), true, hw);

        // --- Spiel
        add("game", Icons.Kind.CUBE, Lang.t("settings.game.versions.title"), Lang.t("settings.game.versions.desc"), false, versionChooser(), true);
        add("game", Icons.Kind.ARCHIVE, Lang.t("settings.game.instances.title"), Lang.t("settings.game.instances.desc"), false, toggle(cfg.multipleInstances, on -> cfg.multipleInstances = on));
        add("game", Icons.Kind.ROCKET, Lang.t("settings.game.after_launch.title"), Lang.t("settings.game.after_launch.desc"), false,
                Ui.segmented(List.of("keep", "hide", "close"), List.of(Lang.t("settings.game.after_launch.keep"), Lang.t("settings.game.after_launch.hide"), Lang.t("settings.game.after_launch.close")), cfg.afterLaunch, v -> {
                    cfg.afterLaunch = v;
                    save();
                }));
        long totalRam = Math.max(4096, ramMb());
        int maxSlider = (int) Math.min(LauncherConfig.MAX_MEMORY_MB, Math.max(2048, totalRam - 1024));
        JSlider mem = new JSlider(LauncherConfig.MIN_MEMORY_MB, maxSlider, Math.min(cfg.maxMemoryMb, maxSlider));
        mem.setOpaque(false);
        mem.setPreferredSize(new Dimension(220, 24));
        JLabel memValue = Ui.label("", 14, Font.BOLD, Theme.TEXT);
        Runnable memText = () -> memValue.setText(String.format(Lang.locale(), "%.1f GB", mem.getValue() / 1024.0));
        memText.run();
        mem.addChangeListener(e -> {
            memText.run();
            if (!mem.getValueIsAdjusting()) {
                cfg.maxMemoryMb = mem.getValue();
                cfg.minMemoryMb = Math.min(cfg.minMemoryMb, cfg.maxMemoryMb);
                save();
                hooks.memoryChanged();
            }
        });
        add("game", Icons.Kind.MEMORY, Lang.t("settings.game.memory.title"), Lang.t("settings.game.memory.desc", totalRam / 1024), false, row(memValue, mem));
        JTextField w = field(cfg.windowWidth == null ? "" : cfg.windowWidth.toString(), Lang.t("settings.game.resolution.width"), 6);
        JTextField h = field(cfg.windowHeight == null ? "" : cfg.windowHeight.toString(), Lang.t("settings.game.resolution.height"), 6);
        Consumer<String> sizeCommit = s -> {
            cfg.windowWidth = parse(w.getText(), 640, 16384);
            cfg.windowHeight = parse(h.getText(), 480, 16384);
            save();
        };
        onCommit(w, sizeCommit);
        onCommit(h, sizeCommit);
        JButton nativeRes = button(Lang.t("settings.game.resolution.native"));
        nativeRes.addActionListener(e -> {
            java.awt.DisplayMode m = host.frame().getGraphicsConfiguration().getDevice().getDisplayMode();
            w.setText(Integer.toString(m.getWidth()));
            h.setText(Integer.toString(m.getHeight()));
            sizeCommit.accept("");
        });
        JButton resetRes = new JButton(Icons.of(Icons.Kind.REFRESH, 14, Theme.TEXT));
        resetRes.setToolTipText(Lang.t("settings.game.resolution.default"));
        resetRes.setFocusable(false);
        resetRes.addActionListener(e -> {
            w.setText("");
            h.setText("");
            sizeCommit.accept("");
        });
        add("game", Icons.Kind.MONITOR, Lang.t("settings.game.resolution.title"), Lang.t("settings.game.resolution.desc"), false,
                row(w, Ui.label("×", 14, Font.PLAIN, Theme.MUTED), h, nativeRes, resetRes));
        add("game", Icons.Kind.MONITOR, Lang.t("settings.game.fullscreen.title"), Lang.t("settings.game.fullscreen.desc"),
                false, toggle(cfg.forceFullscreen, on -> cfg.forceFullscreen = on));
        add("game", Icons.Kind.BOLT, Lang.t("settings.game.perf.title"), Lang.t("settings.game.perf.desc"), false,
                toggle(cfg.performanceFlags, on -> cfg.performanceFlags = on));
        JComboBox<String> backend = new JComboBox<>(new String[]{Lang.t("common.default"), "Vulkan", "OpenGL"});
        backend.setSelectedIndex(Math.max(0, dev.lumora.launcher.core.launch.GraphicsBackend.VALUES.indexOf(cfg.graphicsBackend)));
        backend.setPreferredSize(new Dimension(150, Ui.CONTROL_HEIGHT));
        backend.addActionListener(e -> {
            cfg.graphicsBackend = dev.lumora.launcher.core.launch.GraphicsBackend.VALUES.get(backend.getSelectedIndex());
            save();
        });
        add("game", Icons.Kind.MONITOR, Lang.t("settings.game.backend.title"), Lang.t("settings.game.backend.desc"), false, backend);
        add("game", Icons.Kind.CUBE, Lang.t("settings.game.patch.title"), Lang.t("settings.game.patch.desc"), false,
                toggle(cfg.rememberPatch, on -> cfg.rememberPatch = on));
        JTextField jvm = field(String.join(" ", cfg.extraJvmArgs), Lang.t("settings.game.jvm.placeholder"), 13);
        onCommit(jvm, s -> {
            cfg.extraJvmArgs = s.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(s.split("\\s+")));
            save();
        });
        JButton jvmEdit = new JButton(Lang.t("common.edit"), Icons.of(Icons.Kind.EDIT, 14, Theme.TEXT));
        jvmEdit.setFocusable(false);
        jvmEdit.addActionListener(e -> editJvm(jvm));
        add("game", Icons.Kind.TERMINAL, Lang.t("settings.game.jvm.title"), Lang.t("settings.game.jvm.desc"),
                true, row(jvm, jvmEdit));
        JTextField env = field(String.join(", ", cfg.environmentVariables), "KEY=VALUE", 13);
        env.setEditable(false);
        JButton envEdit = new JButton(Lang.t("common.edit"), Icons.of(Icons.Kind.EDIT, 14, Theme.TEXT));
        envEdit.setFocusable(false);
        envEdit.addActionListener(e -> editEnvironment(env));
        add("game", Icons.Kind.TERMINAL, Lang.t("settings.game.env.title"), Lang.t("settings.game.env.desc"),
                true, row(env, envEdit));
        JTextField javaField = field(cfg.javaPath, Lang.t("settings.game.java.placeholder"), 14);
        onCommit(javaField, s -> {
            cfg.javaPath = s.isEmpty() ? null : s;
            save();
        });
        JButton javaPick = button(Lang.t("settings.change_dir"));
        javaPick.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(cfg.javaPath);
            fc.setDialogTitle(Lang.t("settings.game.java.choose"));
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                cfg.javaPath = fc.getSelectedFile().toPath().toAbsolutePath().toString();
                javaField.setText(cfg.javaPath);
                save();
            }
        });
        add("game", Icons.Kind.CHIP, Lang.t("settings.game.java.title"), Lang.t("settings.game.java.desc"), true, row(javaField, javaPick));

        // --- Speicher: overview with bar and categories
        storage = new StoragePanel(host, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "storage-scan");
            t.setDaemon(true);
            return t;
        }));
        settings.add(new Setting("storage", Icons.Kind.HARDDRIVE, Lang.t("settings.storage.usage.title"),
                Lang.t("settings.storage.usage.desc"), false, storage, true));
        JSlider cache = new JSlider(0, 32, Math.min(32, cfg.downloadCacheGb));
        cache.setOpaque(false);
        cache.setPreferredSize(new Dimension(150, 24));
        JSpinner cacheSpin = new JSpinner(new SpinnerNumberModel(cfg.downloadCacheGb, 0, 256, 1));
        cacheSpin.setPreferredSize(new Dimension(64, Ui.CONTROL_HEIGHT));
        cache.addChangeListener(e -> {
            cacheSpin.setValue(cache.getValue());
            if (!cache.getValueIsAdjusting()) {
                cfg.downloadCacheGb = cache.getValue();
                save();
            }
        });
        cacheSpin.addChangeListener(e -> {
            int v = (Integer) cacheSpin.getValue();
            if (v <= 32) {
                cache.setValue(v);
            }
            cfg.downloadCacheGb = v;
            save();
        });
        add("storage", Icons.Kind.HARDDRIVE, Lang.t("settings.storage.cache.title"), Lang.t("settings.storage.cache.desc"), false, row(cache, cacheSpin));
        JComboBox<String> logKeep = new JComboBox<>(new String[]{Lang.t("settings.storage.logs.keep"), Lang.t("settings.storage.logs.days3"), Lang.t("settings.storage.logs.days7"), Lang.t("settings.storage.logs.days14"), Lang.t("settings.storage.logs.days30")});
        List<Integer> logDays = List.of(0, 3, 7, 14, 30);
        logKeep.setSelectedIndex(Math.max(0, logDays.indexOf(cfg.logRetentionDays)));
        logKeep.setPreferredSize(new Dimension(170, Ui.CONTROL_HEIGHT));
        logKeep.addActionListener(e -> {
            cfg.logRetentionDays = logDays.get(logKeep.getSelectedIndex());
            save();
        });
        add("storage", Icons.Kind.TERMINAL, Lang.t("settings.storage.logs.title"), Lang.t("settings.storage.logs.desc"), false, logKeep);

        // --- Chat (Lumora Social)
        add("chat", Icons.Kind.SERVER, Lang.t("settings.chat.share_server.title"),
                Lang.t("settings.chat.share_server.desc"),
                false, toggle(cfg.socialShareServer, on -> cfg.socialShareServer = on));
        ToggleSwitch badge = toggle(cfg.unreadBadge, on -> cfg.unreadBadge = on);
        badge.onChange(on -> hooks.socialChanged());
        add("chat", Icons.Kind.BELL, Lang.t("settings.chat.badge.title"), Lang.t("settings.chat.badge.desc"),
                false, badge);
        JComboBox<String> level = new JComboBox<>(new String[]{Lang.t("settings.chat.notify.all"), Lang.t("settings.chat.notify.messages"), Lang.t("common.off")});
        List<String> levelIds = List.of("all", "messages", "none");
        level.setSelectedIndex(Math.max(0, levelIds.indexOf(cfg.notificationLevel)));
        level.setPreferredSize(new Dimension(190, Ui.CONTROL_HEIGHT));
        level.addActionListener(e -> {
            cfg.notificationLevel = levelIds.get(level.getSelectedIndex());
            cfg.socialNotifications = !cfg.notificationLevel.equals("none");
            save();
            hooks.trayChanged();
        });
        add("chat", Icons.Kind.CHAT, Lang.t("settings.chat.notify.title"), Lang.t("settings.chat.notify.desc"), false, level);

        // --- Integrationen (Discord Rich Presence is always on with Lumora's own application ID, see AppIds)
        ToggleSwitch discordServer = toggle(cfg.discordShowServer, on -> cfg.discordShowServer = on);
        discordServer.onChange(on -> hooks.discordChanged());
        add("integrations", Icons.Kind.DISCORD, Lang.t("settings.integrations.discord.title"), Lang.t("settings.integrations.discord.desc"), false, discordServer);
        add("integrations", Icons.Kind.LOCK, Lang.t("settings.integrations.curseforge.title"), Lang.t("settings.integrations.curseforge.desc"), false,
                curseForgeKeyRow());
        add("integrations", Icons.Kind.CHART, Lang.t("settings.integrations.stats.title"), Lang.t("settings.integrations.stats.desc"), false,
                toggle(cfg.anonymousStats, on -> cfg.anonymousStats = on));
        add("integrations", Icons.Kind.GLOBE, Lang.t("settings.integrations.news.title"), Lang.t("settings.integrations.news.desc"),
                false, Ui.label("", 1, Font.PLAIN, Theme.MUTED));

        // --- Fehlersuche
        JButton logs = button(Lang.t("settings.debug.logs.view"));
        logs.addActionListener(e -> host.showPage("logs"));
        JButton logsDir = Ui.openFolderButton(() -> host.openFolder(host.ctx().paths.root().resolve("logs")));
        add("debug", Icons.Kind.TERMINAL, Lang.t("settings.debug.logs.title"), Lang.t("settings.debug.logs.desc"), false, row(logs, logsDir));
        JButton diag = button(Lang.t("settings.debug.diag.open"));
        diag.addActionListener(e -> host.showPage("diagnostics"));
        add("debug", Icons.Kind.PULSE, Lang.t("settings.debug.diag.title"), Lang.t("settings.debug.diag.desc"), false, row(diag));
        JButton crashes = button(Lang.t("settings.debug.crashes.open"));
        crashes.addActionListener(e -> host.showPage("crashes"));
        add("debug", Icons.Kind.ALERT, Lang.t("settings.debug.crashes.title"), Lang.t("settings.debug.crashes.desc"), false, row(crashes));
        add("debug", Icons.Kind.MEMORY, Lang.t("settings.debug.heap.title"), Lang.t("settings.debug.heap.desc"), true,
                toggle(cfg.heapDumpOnOom, on -> cfg.heapDumpOnOom = on));
        add("debug", Icons.Kind.CHIP, Lang.t("settings.debug.threads.title"), Lang.t("settings.debug.threads.desc"), true,
                toggle(cfg.hangDump, on -> cfg.hangDump = on));
        add("debug", Icons.Kind.BUG, Lang.t("settings.debug.mode.title"), Lang.t("settings.debug.mode.desc"), true,
                Ui.segmented(List.of("off", "listen", "attach"), List.of(Lang.t("common.off"), Lang.t("settings.debug.mode.listen"), Lang.t("settings.debug.mode.attach")), cfg.debugMode, v -> {
                    cfg.debugMode = v;
                    save();
                }));
        JTextField debugAddr = field(cfg.debugAddress, "127.0.0.1:5005", 14);
        onCommit(debugAddr, s -> {
            if (LauncherConfig.isValidDebugAddress(s)) {
                cfg.debugAddress = s;
                save();
            } else {
                host.error(Lang.t("settings.debug.address.title"), Lang.t("settings.debug.address.invalid"));
                debugAddr.setText(cfg.debugAddress);
            }
        });
        JButton debugReset = button(Lang.t("common.reset"));
        debugReset.addActionListener(e -> {
            cfg.debugAddress = "127.0.0.1:5005";
            debugAddr.setText(cfg.debugAddress);
            save();
        });
        add("debug", Icons.Kind.BUG, Lang.t("settings.debug.address.title"), Lang.t("settings.debug.address.desc"), true,
                row(debugAddr, debugReset));
        JButton clientReset = button(Lang.t("common.reset"));
        clientReset.setForeground(Theme.FAIL);
        clientReset.addActionListener(e -> resetClientSettings());
        add("debug", Icons.Kind.REFRESH, Lang.t("settings.debug.client_reset.title"), Lang.t("settings.debug.client_reset.desc"), true,
                row(clientReset));
        JButton restore = button(Lang.t("settings.debug.restore.button"));
        restore.addActionListener(e -> restoreInstances());
        add("debug", Icons.Kind.ARCHIVE, Lang.t("settings.debug.restore.title"), Lang.t("settings.debug.restore.desc"), true,
                row(restore));
        JButton reset = button(Lang.t("common.reset"));
        reset.setForeground(Theme.FAIL);
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(host.frame(), Lang.t("settings.debug.reset.confirm"), Lang.t("settings.debug.reset.title"),
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                LauncherConfig d = new LauncherConfig();
                cfg.maxMemoryMb = d.maxMemoryMb;
                cfg.minMemoryMb = d.minMemoryMb;
                cfg.javaPath = null;
                cfg.extraJvmArgs = new ArrayList<>();
                cfg.windowWidth = null;
                cfg.windowHeight = null;
                cfg.afterLaunch = d.afterLaunch;
                cfg.multipleInstances = d.multipleInstances;
                cfg.environmentVariables = new ArrayList<>();
                cfg.forceFullscreen = d.forceFullscreen;
                cfg.performanceFlags = d.performanceFlags;
                cfg.accentColor = d.accentColor;
                cfg.pixelStyle = d.pixelStyle;
                cfg.reduceMotion = d.reduceMotion;
                cfg.hardwareAcceleration = d.hardwareAcceleration;
                cfg.unreadBadge = d.unreadBadge;
                cfg.notificationLevel = d.notificationLevel;
                cfg.heapDumpOnOom = d.heapDumpOnOom;
                cfg.debugMode = d.debugMode;
                cfg.debugAddress = d.debugAddress;
                cfg.checkForUpdates = d.checkForUpdates;
                cfg.portableSelfUpdate = d.portableSelfUpdate;
                cfg.parallelDownloads = d.parallelDownloads;
                cfg.minimizeToTray = d.minimizeToTray;
                cfg.startPage = d.startPage;
                cfg.showSnapshots = d.showSnapshots;
                cfg.socialShareServer = d.socialShareServer;
                cfg.socialNotifications = d.socialNotifications;
                cfg.quickPlay = d.quickPlay;
                cfg.graphicsBackend = d.graphicsBackend;
                cfg.rememberPatch = d.rememberPatch;
                cfg.prereleases = d.prereleases;
                cfg.downloadCacheGb = d.downloadCacheGb;
                cfg.logRetentionDays = d.logRetentionDays;
                cfg.hangDump = d.hangDump;
                save();
                JOptionPane.showMessageDialog(host.frame(), Lang.t("settings.debug.reset.done"));
            }
        });
        add("debug", Icons.Kind.REFRESH, Lang.t("settings.debug.reset.title"), Lang.t("settings.debug.reset.desc"),
                true, row(reset));
    }

    /** "Ingame-Erlebnis anpassen": the menu design in the game, applied once per version at its next start (GameThemeSeeder). */
    private void ingameExperience() {
        Object[] options = {Lang.t("settings.ingame.lumora"), Lang.t("settings.ingame.minecraft"), Lang.t("common.cancel")};
        int pick = JOptionPane.showOptionDialog(host.frame(), Lang.t("settings.ingame.question"),
                Lang.t("settings.general.ingame.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (pick != 0 && pick != 1) {
            return;
        }
        cfg.gameTheme = pick == 0 ? dev.lumora.launcher.core.install.GameThemeSeeder.LUMORA : dev.lumora.launcher.core.install.GameThemeSeeder.VANILLA;
        // a new seed: every version takes the choice over once at its next start
        cfg.gameThemeSeed = "launcher-" + System.currentTimeMillis();
        save();
    }

    /** "Lumora-Client-Einstellungen zurücksetzen": renames the Lumora settings folders (kept as a backup). */
    private void resetClientSettings() {
        if (host.ctx().play.runningGame().isPresent()) {
            host.error(Lang.t("settings.debug.client_reset.short"), Lang.t("settings.debug.client_reset.running"));
            return;
        }
        if (JOptionPane.showConfirmDialog(host.frame(), Lang.t("settings.debug.client_reset.confirm"), Lang.t("settings.debug.client_reset.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            List<Path> kept = dev.lumora.launcher.core.InstanceLayout.resetLumoraSettings(host.ctx().paths, java.time.Instant.now());
            JOptionPane.showMessageDialog(host.frame(), kept.isEmpty() ? Lang.t("settings.debug.client_reset.none") : Lang.plural("settings.debug.client_reset.done", kept.size()));
        } catch (java.io.IOException ex) {
            host.error(Lang.t("settings.debug.client_reset.short"), Lang.t("settings.debug.client_reset.failed", ex.getMessage()));
        }
    }

    /** "Instanzen von Festplatte wiederherstellen": instance folders without a choice become own instances again. */
    private void restoreInstances() {
        try {
            List<String> added = dev.lumora.launcher.core.InstanceLayout.restoreMissing(host.ctx().paths, cfg);
            if (!added.isEmpty()) {
                save();
                hooks.homeChanged();
            }
            JOptionPane.showMessageDialog(host.frame(), added.isEmpty() ? Lang.t("settings.debug.restore.none")
                    : Lang.t("settings.debug.restore.done", String.join(", ", added)));
        } catch (java.io.IOException ex) {
            host.error(Lang.t("settings.debug.restore.title_short"), ex.getMessage());
        }
    }

    private static Integer parse(String s, int min, int max) {
        try {
            int v = Integer.parseInt(s.strip());
            return v < min || v > max ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long ramMb() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return os.getTotalMemorySize() / (1024 * 1024);
        } catch (RuntimeException e) {
            return 8192;
        }
    }

    // ------------------------------------------------------------------ layout

    /** Tab label of a category. */
    private static String tabName(String category) {
        return Lang.t("settings.category." + category);
    }

    private void openCategory(String name) {
        category = name;
        search.setText("");
        rebuild();
        if (name.equals("storage")) {
            refreshStorage();
        }
    }

    /**
     * Fills the page for the chosen tab (or the search results): settings with their own control as sections (icon,
     * title, description, control below), on/off switches as compact boxes in two columns.
     */
    private void rebuild() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        boolean adv = cfg.advancedSettings;
        categories.select(category);
        categories.setMarked(q.isEmpty());
        list.removeAll();
        int shown = 0;
        for (String cat : CATEGORIES) {
            List<Setting> in = new ArrayList<>();
            for (Setting s : settings) {
                if (!s.category.equals(cat) || (s.advanced && !adv)) {
                    continue;
                }
                boolean match = q.isEmpty() ? cat.equals(category)
                        : (s.title + " " + (s.description == null ? "" : s.description) + " " + s.category).toLowerCase(Locale.ROOT).contains(q);
                if (match) {
                    in.add(s);
                }
            }
            if (in.isEmpty()) {
                continue;
            }
            if (!q.isEmpty()) {
                JLabel h = caps(tabName(cat), 12, Theme.MUTED);
                h.setBorder(BorderFactory.createEmptyBorder(shown == 0 ? 0 : 6, 0, 12, 0));
                list.add(h);
            }
            List<Setting> switches = new ArrayList<>();
            for (Setting s : in) {
                if (s.control instanceof ToggleSwitch) {
                    switches.add(s);
                } else {
                    list.add(section(s));
                    list.add(Box.createVerticalStrut(24));
                }
                shown++;
            }
            if (!switches.isEmpty()) {
                JPanel grid = new JPanel(new GridLayout(0, 2, 14, 10));
                grid.setOpaque(false);
                grid.setAlignmentX(Component.LEFT_ALIGNMENT);
                for (Setting s : switches) {
                    grid.add(switchBox(s));
                }
                grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
                list.add(grid);
                list.add(Box.createVerticalStrut(24));
            }
        }
        if (shown == 0) {
            JLabel none = Ui.label(q.isEmpty() ? Lang.t("settings.search.empty_category") : Lang.t("settings.search.no_match", search.getText().strip()),
                    14, Font.PLAIN, Theme.MUTED);
            list.add(none);
        }
        long hidden = settings.stream().filter(s -> s.advanced && (q.isEmpty() ? s.category.equals(category) : true)).count();
        if (!adv && hidden > 0) {
            JLabel hint = Ui.label(Lang.plural("settings.hidden_advanced", hidden), 12.5f, Font.PLAIN, Theme.MUTED);
            hint.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
            list.add(hint);
        }
        list.revalidate();
        list.repaint();
    }

    /** Upper-case title with a little letter spacing, like the reference launcher. */
    private static JLabel caps(String text, float size, Color color) {
        JLabel l = new JLabel(Theme.pixelSafe(text).toUpperCase(Lang.locale()));
        l.setFont(Theme.pixelStyle ? Theme.display(16f, Font.BOLD, size)
                : Theme.font(Font.BOLD, size).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.08f)));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** A setting with its own control: icon and title, description, the control below. */
    private JComponent section(Setting s) {
        if (s.wide && s.category.equals("storage")) {
            // the storage overview brings its own heading
            Card c = new Card(new BorderLayout()).calm().padding(18, 20, 14, 18);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(s.control, BorderLayout.CENTER);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
            return c;
        }
        JPanel p = Ui.column();
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel title = Ui.row(10);
        title.add(new JLabel(Icons.of(s.icon, 17, Theme.TEXT)));
        title.add(caps(s.title, 13.5f, Theme.TEXT));
        if (s.advanced) {
            title.add(Ui.chip(Lang.t("settings.advanced"), Theme.MUTED, Theme.TEXT));
        }
        p.add(title);
        if (s.description != null) {
            p.add(Box.createVerticalStrut(8));
            p.add(Ui.wrapText(s.description, 13.5f, Theme.MUTED));
        }
        p.add(Box.createVerticalStrut(12));
        JPanel control = new JPanel(new BorderLayout());
        control.setOpaque(false);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (s.wide) {
            control.add(s.control, BorderLayout.CENTER);
        } else {
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(s.control);
            control.add(left, BorderLayout.CENTER);
        }
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        p.add(control);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        return p;
    }

    /** An on/off setting as a compact box: name on the left, switch on the right, description as tooltip. */
    private JComponent switchBox(Setting s) {
        JPanel box = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = Theme.arc(10);
                g.setColor(Theme.SURFACE);
                Theme.fillRound(g, 0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g.setColor(Theme.BORDER);
                Theme.drawRound(g, 0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 12));
        JPanel name = Ui.row(8);
        JLabel l = Ui.label(s.title, 14, Font.PLAIN, Theme.TEXT);
        name.add(l);
        if (s.advanced) {
            name.add(Ui.chip(Lang.t("settings.advanced"), Theme.MUTED, Theme.TEXT));
        }
        if (s.description != null) {
            String tip = "<html><body style='width:280px'>" + s.description.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>";
            box.setToolTipText(tip);
            l.setToolTipText(tip);
        }
        JPanel nw = new JPanel(new java.awt.GridBagLayout());
        nw.setOpaque(false);
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.weightx = 1;
        nw.add(name, c);
        box.add(nw, BorderLayout.CENTER);
        JPanel sw = new JPanel(new java.awt.GridBagLayout());
        sw.setOpaque(false);
        sw.add(s.control);
        box.add(sw, BorderLayout.EAST);
        return box;
    }
}
