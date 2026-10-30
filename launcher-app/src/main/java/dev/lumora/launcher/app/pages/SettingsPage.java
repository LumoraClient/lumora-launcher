package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Autostart;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Tray;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.config.LauncherConfig;
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
 * Launcher settings: categories on the left, search, "Erweiterter Modus" for the settings marked "Erweitert". Every
 * change is saved right away (no save button). Only settings that really do something are listed.
 */
public final class SettingsPage extends JPanel {
    /** What the window does for settings that reach beyond the config file. */
    public interface Hooks {
        void socialChanged();

        void trayChanged();

        void memoryChanged();

        /** Microsoft app-id setup dialog; true when an id was entered. */
        boolean clientIdHelp();

        /** Discord Rich Presence switched or its application ID changed. */
        void discordChanged();

        /** Looks for a new launcher version on GitHub Releases (shows the result). */
        void checkLauncherUpdate();
    }

    /** @param wide the control fills the whole card (storage overview) instead of sitting on the right */
    private record Setting(String category, Icons.Kind icon, String title, String description, boolean advanced,
                           JComponent control, boolean wide) {
    }

    private StoragePanel storage;

    private static final List<String> CATEGORIES = List.of("Allgemein", "Erscheinungsbild", "Spiel", "Speicher", "Chat",
            "Integrationen", "Fehlersuche");

    private final PageHost host;
    private final Hooks hooks;
    private final LauncherConfig cfg;
    private final List<Setting> settings = new ArrayList<>();
    private final JPanel list = Ui.column();
    private final JPanel categories = Ui.column();
    private final JTextField search = new JTextField();
    private final ToggleSwitch advanced;
    private String category = "Allgemein";

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

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        JLabel title = Ui.label("LAUNCHER-EINSTELLUNGEN", 18, Font.BOLD, Theme.TEXT);
        head.add(title, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(Ui.label("v" + BuildInfo.VERSION, 13, Font.PLAIN, Theme.MUTED));
        right.add(Ui.label("ERWEITERTER MODUS", 13, Font.BOLD, Theme.TEXT));
        right.add(advanced);
        head.add(right, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        JPanel side = new JPanel(new BorderLayout(0, 12));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(220, 10));
        search.putClientProperty("JTextField.placeholderText", "Einstellungen durchsuchen");
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.setPreferredSize(new Dimension(220, 36));
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
        side.add(search, BorderLayout.NORTH);
        side.add(categories, BorderLayout.CENTER);
        add(side, BorderLayout.WEST);
        add(Ui.scroll(list), BorderLayout.CENTER);
        rebuild();
    }

    private void save() {
        host.saveConfig();
    }

    // ------------------------------------------------------------------ settings

    private void add(String cat, Icons.Kind icon, String title, String desc, boolean adv, JComponent control) {
        settings.add(new Setting(cat, icon, title, desc, adv, control, false));
    }

    private void restartHint() {
        JOptionPane.showMessageDialog(host.frame(), "Wirkt nach einem Neustart des Launchers.", "Erscheinungsbild",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Opens the storage category with a fresh measurement (called when the page is shown with that category). */
    public void refreshStorage() {
        if (storage != null) {
            storage.refresh();
        }
    }

    /** Accent colour presets as swatches plus a colour picker; the selected one gets a ring. */
    private JComponent accentPicker() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        p.setOpaque(false);
        for (Theme.Preset preset : Theme.PRESETS) {
            Color c = Theme.parse(preset.hex());
            JComponent s = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean sel = preset.hex().equalsIgnoreCase(cfg.accentColor);
                    g.setColor(c);
                    g.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, Theme.arc(8), Theme.arc(8));
                    if (sel) {
                        g.setColor(Theme.TEXT);
                        g.setStroke(new java.awt.BasicStroke(2f));
                        g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, Theme.arc(10), Theme.arc(10));
                    }
                    g.dispose();
                }
            };
            s.setPreferredSize(new Dimension(34, 34));
            s.setToolTipText(preset.name());
            s.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            s.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setAccent(preset.hex(), p);
                }
            });
            p.add(s);
        }
        JButton pick = new JButton(Icons.of(Icons.Kind.PALETTE, 16, Theme.TEXT));
        pick.setToolTipText("Eigene Farbe");
        pick.setFocusable(false);
        pick.setPreferredSize(new Dimension(34, 34));
        pick.addActionListener(e -> {
            Color chosen = javax.swing.JColorChooser.showDialog(host.frame(), "Akzentfarbe", Theme.parse(cfg.accentColor));
            if (chosen != null) {
                setAccent(String.format("#%02X%02X%02X", chosen.getRed(), chosen.getGreen(), chosen.getBlue()), p);
            }
        });
        p.add(pick);
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
        if (JOptionPane.showConfirmDialog(host.frame(), new javax.swing.JScrollPane(area), "Umgebungsvariablen (KEY=VALUE pro Zeile)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        List<String> parsed = dev.lumora.launcher.core.launch.LaunchOptions.parseEnvironment(area.getText());
        List<String> bad = parsed.stream().filter(v -> !LauncherConfig.isValidEnvironmentVariable(v)).toList();
        if (!bad.isEmpty()) {
            host.error("Umgebungsvariablen", "Ungültig (Format KEY=VALUE, Name aus Buchstaben, Ziffern und _):\n" + String.join("\n", bad));
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
        if (JOptionPane.showConfirmDialog(host.frame(), new javax.swing.JScrollPane(area), "JVM-Argumente (eins pro Zeile)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String s = area.getText().strip();
            cfg.extraJvmArgs = s.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(s.split("\\s+")));
            save();
            field.setText(String.join(" ", cfg.extraJvmArgs));
        }
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
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        return b;
    }

    private static JPanel row(JComponent... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        for (JComponent c : parts) {
            p.add(c);
        }
        return p;
    }

    private void build() {
        // --- Allgemein
        JTextField dir = field(cfg.installDir != null ? cfg.installDir : host.ctx().paths.root().toString(), "", 22);
        dir.setEditable(false);
        JButton open = button("Öffnen");
        open.addActionListener(e -> host.openFolder(host.ctx().paths.root()));
        JButton change = button("Verzeichnis ändern");
        change.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(dir.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                Path p = fc.getSelectedFile().toPath().toAbsolutePath();
                cfg.installDir = p.equals(host.ctx().defaultRoot) ? null : p.toString();
                dir.setText(p.toString());
                save();
                JOptionPane.showMessageDialog(host.frame(), "Der neue Spielordner wird nach einem Neustart des Launchers verwendet.\n"
                        + "Vorhandene Daten werden nicht verschoben.", "Spielverzeichnis", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        add("Allgemein", Icons.Kind.FOLDER, "Spielverzeichnis", "Spieldaten, Bibliotheken, Mods und Logs. Eine Änderung wirkt nach einem Neustart.",
                false, row(dir, open, change));
        JButton launcherUpdate = button("Nach Update suchen");
        launcherUpdate.addActionListener(e -> hooks.checkLauncherUpdate());
        add("Allgemein", Icons.Kind.ROCKET, "Launcher-Version " + BuildInfo.VERSION, "Neue Versionen kommen von github.com/"
                + dev.lumora.launcher.core.update.LauncherUpdate.REPOSITORY + " und werden nur mit gültiger Signatur installiert.", false,
                row(launcherUpdate));
        add("Allgemein", Icons.Kind.REFRESH, "Automatische Update-Prüfung",
                "Sucht beim Start nach neuen Versionen von Fabric, Mods und Ressourcenpaketen und fragt vor jeder Änderung.", false,
                toggle(cfg.checkForUpdates, on -> cfg.checkForUpdates = on));
        ToggleSwitch autostart = new ToggleSwitch(Autostart.enabled());
        autostart.setEnabled(Autostart.supported());
        autostart.onChange(on -> {
            if (!Autostart.set(on)) {
                host.error("Autostart", "Der Autostart konnte nicht " + (on ? "eingerichtet" : "entfernt") + " werden.");
            }
        });
        add("Allgemein", Icons.Kind.PLAY, "Mit Windows starten", "Startet den Launcher beim Anmelden im Tray, damit Freunde dich im Chat erreichen, "
                + "solange du am Rechner bist." + (Autostart.supported() ? "" : " Nur mit der installierten LumoraLauncher.exe verfügbar."),
                false, autostart);
        ToggleSwitch tray = toggle(cfg.minimizeToTray, on -> cfg.minimizeToTray = on);
        tray.onChange(on -> hooks.trayChanged());
        tray.setEnabled(Tray.supported());
        add("Allgemein", Icons.Kind.ARCHIVE, "Beim Schließen im Tray bleiben",
                "Das Fenster zu schließen lässt den Launcher im Infobereich weiterlaufen. Beenden kannst du ihn über das Tray-Symbol.",
                false, tray);
        JSlider dl = new JSlider(1, 16, cfg.parallelDownloads);
        dl.setOpaque(false);
        dl.setPreferredSize(new Dimension(150, 24));
        JSpinner dlSpin = new JSpinner(new SpinnerNumberModel(cfg.parallelDownloads, 1, 16, 1));
        dlSpin.setPreferredSize(new Dimension(64, 32));
        dl.addChangeListener(e -> {
            dlSpin.setValue(dl.getValue());
            if (!dl.getValueIsAdjusting()) {
                cfg.parallelDownloads = dl.getValue();
                save();
            }
        });
        dlSpin.addChangeListener(e -> dl.setValue((Integer) dlSpin.getValue()));
        add("Allgemein", Icons.Kind.ARCHIVE, "Limit gleichzeitiger Downloads",
                "Maximale Anzahl an Dateien, die gleichzeitig heruntergeladen werden. Höhere Werte können Downloads beschleunigen, "
                        + "brauchen aber mehr Bandbreite. Wirkt nach einem Neustart.", true, row(dl, dlSpin));

        // --- Erscheinungsbild
        JComboBox<String> start = new JComboBox<>(new String[]{"Spielen", "Neuigkeiten", "Server", "Freunde"});
        List<String> startIds = List.of("home", "news", "servers", "social");
        start.setSelectedIndex(Math.max(0, startIds.indexOf(cfg.startPage)));
        start.addActionListener(e -> {
            cfg.startPage = startIds.get(start.getSelectedIndex());
            save();
        });
        add("Erscheinungsbild", Icons.Kind.MONITOR, "Startseite", "Diese Seite zeigt der Launcher nach dem Öffnen.", false, start);
        add("Erscheinungsbild", Icons.Kind.LIBRARY, "Minecraft-Snapshots im Changelog",
                "Zeigt unter „Neuigkeiten → Changelog“ auch die wöchentlichen Snapshots, nicht nur Releases.", false,
                toggle(cfg.showSnapshots, on -> cfg.showSnapshots = on));
        add("Erscheinungsbild", Icons.Kind.PALETTE, "Akzentfarbe", "Farbe für Knöpfe, Auswahl und Hervorhebungen in der gesamten "
                + "Launcher-Oberfläche. Wirkt nach einem Neustart.", false, accentPicker());
        ToggleSwitch pixel = toggle(cfg.pixelStyle, on -> cfg.pixelStyle = on);
        pixel.onChange(on -> restartHint());
        add("Erscheinungsbild", Icons.Kind.CUBE, "Pixel-Stil", "Zeichnet Karten, Knöpfe und Felder mit harten Pixelkanten im "
                + "Minecraft-Stil statt mit runden. Wirkt nach einem Neustart.", false, pixel);
        add("Erscheinungsbild", Icons.Kind.PULSE, "Reduziere Bewegungen", "Keine automatischen Animationen (drehende Skin-Vorschau, "
                + "Lade-Kreisel). Wechsel zwischen Seiten bleiben sofort.", false, toggle(cfg.reduceMotion, on -> {
                    cfg.reduceMotion = on;
                    Theme.reduceMotion = on;
                }));
        ToggleSwitch hw = toggle(cfg.hardwareAcceleration, on -> cfg.hardwareAcceleration = on);
        hw.onChange(on -> restartHint());
        add("Erscheinungsbild", Icons.Kind.CHIP, "Hardwarebeschleunigung", "Zeichnet die Launcher-Oberfläche mit der Grafikkarte "
                + "(Direct3D). Ausschalten hilft bei Grafikfehlern durch Treiber. Wirkt nach einem Neustart.", true, hw);

        // --- Spiel
        add("Spiel", Icons.Kind.ARCHIVE, "Mehrere Instanzen", "Mehrere Minecraft-Versionen gleichzeitig starten. Jede Version "
                + "(Lumora, Fabric, Vanilla) läuft trotzdem nur einmal.", false, toggle(cfg.multipleInstances, on -> cfg.multipleInstances = on));
        add("Spiel", Icons.Kind.ROCKET, "Aktion nach dem Start", "Was der Launcher macht, sobald Minecraft läuft.", false,
                Ui.segmented(List.of("keep", "hide", "close"), List.of("Offen lassen", "Verstecken", "Schließen"), cfg.afterLaunch, v -> {
                    cfg.afterLaunch = v;
                    save();
                }));
        long totalRam = Math.max(4096, ramMb());
        int maxSlider = (int) Math.min(LauncherConfig.MAX_MEMORY_MB, Math.max(2048, totalRam - 1024));
        JSlider mem = new JSlider(LauncherConfig.MIN_MEMORY_MB, maxSlider, Math.min(cfg.maxMemoryMb, maxSlider));
        mem.setOpaque(false);
        mem.setPreferredSize(new Dimension(220, 24));
        JLabel memValue = Ui.label("", 14, Font.BOLD, Theme.TEXT);
        Runnable memText = () -> memValue.setText(String.format(Locale.GERMANY, "%.1f GB", mem.getValue() / 1024.0));
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
        add("Spiel", Icons.Kind.MEMORY, "Zugewiesener Arbeitsspeicher", "Mit Vorsicht ändern: Weniger ist oft mehr, zu wenig ist schlecht. "
                + "Mehr als 6 GB bringen selten etwas. Dein System hat " + totalRam / 1024 + " GB.", false, row(memValue, mem));
        JTextField w = field(cfg.windowWidth == null ? "" : cfg.windowWidth.toString(), "Breite", 6);
        JTextField h = field(cfg.windowHeight == null ? "" : cfg.windowHeight.toString(), "Höhe", 6);
        Consumer<String> sizeCommit = s -> {
            cfg.windowWidth = parse(w.getText(), 640, 16384);
            cfg.windowHeight = parse(h.getText(), 480, 16384);
            save();
        };
        onCommit(w, sizeCommit);
        onCommit(h, sizeCommit);
        JButton nativeRes = button("Nativ verwenden");
        nativeRes.addActionListener(e -> {
            java.awt.DisplayMode m = host.frame().getGraphicsConfiguration().getDevice().getDisplayMode();
            w.setText(Integer.toString(m.getWidth()));
            h.setText(Integer.toString(m.getHeight()));
            sizeCommit.accept("");
        });
        JButton resetRes = new JButton(Icons.of(Icons.Kind.REFRESH, 14, Theme.TEXT));
        resetRes.setToolTipText("Standard von Minecraft");
        resetRes.setFocusable(false);
        resetRes.addActionListener(e -> {
            w.setText("");
            h.setText("");
            sizeCommit.accept("");
        });
        add("Spiel", Icons.Kind.MONITOR, "Fensterauflösung", "Anfängliche Minecraft-Fenstergröße in Pixeln. Leer = Standard von Minecraft.", false,
                row(w, Ui.label("×", 14, Font.PLAIN, Theme.MUTED), h, nativeRes, resetRes));
        add("Spiel", Icons.Kind.MONITOR, "Vollbild erzwingen", "Minecraft immer im Vollbild starten. Wenn aus, bleibt deine Ingame-Auswahl erhalten.",
                false, toggle(cfg.forceFullscreen, on -> cfg.forceFullscreen = on));
        add("Spiel", Icons.Kind.BOLT, "Performance-Verbesserungen", "Optimierter Garbage Collector (G1 mit kurzen Pausen) gegen Ruckler. "
                + "Wird übersprungen, wenn eigene JVM-Argumente einen anderen Collector wählen.", false,
                toggle(cfg.performanceFlags, on -> cfg.performanceFlags = on));
        JTextField jvm = field(String.join(" ", cfg.extraJvmArgs), "z. B. -XX:+UseZGC", 13);
        onCommit(jvm, s -> {
            cfg.extraJvmArgs = s.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(s.split("\\s+")));
            save();
        });
        JButton jvmEdit = new JButton("Bearbeiten", Icons.of(Icons.Kind.EDIT, 14, Theme.TEXT));
        jvmEdit.setFocusable(false);
        jvmEdit.addActionListener(e -> editJvm(jvm));
        add("Spiel", Icons.Kind.TERMINAL, "Benutzerdefinierte JVM-Argumente", "Standardargumente, die beim Start an Java übergeben werden.",
                true, row(jvm, jvmEdit));
        JTextField env = field(String.join(", ", cfg.environmentVariables), "KEY=VALUE", 13);
        env.setEditable(false);
        JButton envEdit = new JButton("Bearbeiten", Icons.of(Icons.Kind.EDIT, 14, Theme.TEXT));
        envEdit.setFocusable(false);
        envEdit.addActionListener(e -> editEnvironment(env));
        add("Spiel", Icons.Kind.TERMINAL, "Umgebungsvariablen", "Variablen für den Spielprozess, ein KEY=VALUE pro Zeile oder durch Kommas getrennt.",
                true, row(env, envEdit));
        JTextField javaField = field(cfg.javaPath, "leer = Laufzeit von Mojang", 14);
        onCommit(javaField, s -> {
            cfg.javaPath = s.isEmpty() ? null : s;
            save();
        });
        JButton javaPick = button("Verzeichnis ändern");
        javaPick.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(cfg.javaPath);
            fc.setDialogTitle("java.exe oder javaw.exe auswählen");
            if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
                cfg.javaPath = fc.getSelectedFile().toPath().toAbsolutePath().toString();
                javaField.setText(cfg.javaPath);
                save();
            }
        });
        add("Spiel", Icons.Kind.CHIP, "Eigene JRE", "Pfad zu einer eigenen Java-Installation statt der offiziellen Laufzeit von Mojang. "
                + "Nur ändern, wenn du weißt, was du tust.", true, row(javaField, javaPick));

        // --- Speicher: overview with bar and categories
        storage = new StoragePanel(host, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "storage-scan");
            t.setDaemon(true);
            return t;
        }));
        settings.add(new Setting("Speicher", Icons.Kind.HARDDRIVE, "Speicherbelegung",
                "Java, Download-Cache, Bibliotheken, Welten, Mods, Logs", false, storage, true));

        // --- Chat (Lumora Social)
        JTextField social = field(cfg.socialServer == null ? "" : Endpoint.display(java.net.URI.create(cfg.socialServer)),
                "z. B. chat.meinserver.de", 20);
        JButton apply = button("Übernehmen");
        apply.addActionListener(e -> {
            String s = social.getText().strip();
            try {
                cfg.socialServer = s.isEmpty() ? null : Endpoint.parse(s).toString();
                save();
                hooks.socialChanged();
            } catch (IllegalArgumentException ex) {
                host.error("Lumora-Social-Server", ex.getMessage());
            }
        });
        add("Chat", Icons.Kind.USERS, "Lumora-Social-Server", "Server für Freunde, Gruppen und Chat (siehe SOCIAL.md). Leer = aus. "
                + "Im Internet nur mit wss:// (TLS).", false, row(social, apply));
        add("Chat", Icons.Kind.SERVER, "Server-Adresse für Freunde sichtbar",
                "Freunde sehen, auf welchem Server du spielst. Ausgeschaltet sehen sie nur „Mehrspieler“. Wirkt beim nächsten Spielstart.",
                false, toggle(cfg.socialShareServer, on -> cfg.socialShareServer = on));
        ToggleSwitch badge = toggle(cfg.unreadBadge, on -> cfg.unreadBadge = on);
        badge.onChange(on -> hooks.socialChanged());
        add("Chat", Icons.Kind.BELL, "Ungelesen-Zähler", "Zeigt die Zahl der ungelesenen Chats und Anfragen in der Seitenleiste und am Tray-Symbol.",
                false, badge);
        JComboBox<String> level = new JComboBox<>(new String[]{"Alles", "Nur Nachrichten", "Aus"});
        List<String> levelIds = List.of("all", "messages", "none");
        level.setSelectedIndex(Math.max(0, levelIds.indexOf(cfg.notificationLevel)));
        level.setPreferredSize(new Dimension(190, 34));
        level.addActionListener(e -> {
            cfg.notificationLevel = levelIds.get(level.getSelectedIndex());
            cfg.socialNotifications = !cfg.notificationLevel.equals("none");
            save();
            hooks.trayChanged();
        });
        add("Chat", Icons.Kind.CHAT, "Benachrichtigungen", "Worüber dich der Launcher benachrichtigt, während sein Fenster im Hintergrund ist: "
                + "Nachrichten, Anfragen und Hinweise oder nur Nachrichten.", false, level);

        // --- Integrationen
        JTextField discordId = field(cfg.discordAppId, "Discord-Anwendungs-ID", 16);
        ToggleSwitch discord = new ToggleSwitch(cfg.discordRichPresence);
        onCommit(discordId, s -> {
            if (s.isEmpty() || LauncherConfig.isValidDiscordAppId(s)) {
                String before = cfg.discordAppId;
                cfg.discordAppId = s.isEmpty() ? null : s;
                if (!java.util.Objects.equals(before, cfg.discordAppId)) {
                    save();
                    hooks.discordChanged();
                }
            } else {
                host.error("Discord-Anwendungs-ID", "Die Anwendungs-ID ist eine Zahl mit 17 bis 20 Ziffern "
                        + "(Discord Developer Portal → Applications → General Information).");
            }
        });
        discord.onChange(on -> {
            cfg.discordRichPresence = on;
            save();
            hooks.discordChanged();
            if (on && !LauncherConfig.isValidDiscordAppId(cfg.discordAppId)) {
                JOptionPane.showMessageDialog(host.frame(), "Trage noch die Anwendungs-ID deiner Discord-Anwendung ein\n"
                        + "(kostenlos im Discord Developer Portal anlegen). Ein Bild namens „lumora“ unter Rich Presence → Art Assets\n"
                        + "wird als Symbol angezeigt.", "Discord Rich Presence", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        add("Integrationen", Icons.Kind.DISCORD, "Launcher Discord Rich Presence", "Zeigt in Discord, ob du im Launcher bist oder welche "
                + "Minecraft-Version du spielst. Läuft nur über die Discord-App auf diesem PC, mit deiner eigenen Anwendungs-ID.", false,
                row(discordId, discord));
        add("Integrationen", Icons.Kind.CHART, "Anonyme Launcherstatistiken", "Würde anonyme Angaben wie Betriebssystem und Version teilen. "
                + "Es gibt noch keinen Lumora-Statistikserver: Auch eingeschaltet wird nichts gesendet.", false,
                toggle(cfg.anonymousStats, on -> cfg.anonymousStats = on));
        JTextField id = field(cfg.microsoftClientId, "Anwendungs-ID (GUID)", 17);
        onCommit(id, s -> {
            if (s.isEmpty() || LauncherConfig.isValidClientId(s)) {
                cfg.microsoftClientId = s.isEmpty() ? null : s;
                save();
            } else {
                host.error("Ungültige Anwendungs-ID", "Die Anwendungs-ID muss eine GUID sein (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
            }
        });
        JButton help = button("Einrichtungshilfe");
        help.addActionListener(e -> {
            if (hooks.clientIdHelp()) {
                id.setText(cfg.microsoftClientId);
            }
        });
        add("Integrationen", Icons.Kind.LOCK, "Microsoft-Anmeldung: Anwendungs-ID",
                "Eigene, von Mojang freigeschaltete Azure-App für die Anmeldung (siehe Einrichtungshilfe).", true, row(id, help));
        add("Integrationen", Icons.Kind.GLOBE, "Minecraft-Neuigkeiten", "News und Patch Notes kommen von Mojangs Launcher-Inhalten "
                + "(launchercontent.mojang.com), Spielerzahlen der Server-Liste direkt von den Servern. Es werden keine Daten über dich gesendet.",
                false, Ui.label("", 1, Font.PLAIN, Theme.MUTED));

        // --- Fehlersuche
        JButton logs = button("Logs ansehen");
        logs.addActionListener(e -> host.showPage("logs"));
        JButton logsDir = button("Ordner öffnen");
        logsDir.addActionListener(e -> host.openFolder(host.ctx().paths.root().resolve("logs")));
        add("Fehlersuche", Icons.Kind.TERMINAL, "Logs", "Launcher- und Spielprotokolle. Zugangsdaten werden nie geschrieben.", false, row(logs, logsDir));
        JButton diag = button("Diagnose öffnen");
        diag.addActionListener(e -> host.showPage("diagnostics"));
        add("Fehlersuche", Icons.Kind.PULSE, "Diagnose", "Systemprüfung: Java, Grafik, Speicher, Netzwerk und Spieldateien.", false, row(diag));
        JButton crashes = button("Absturzberichte");
        crashes.addActionListener(e -> host.showPage("crashes"));
        add("Fehlersuche", Icons.Kind.ALERT, "Abstürze", "Absturzberichte aller Versionen mit wahrscheinlicher Ursache und Lösungen.", false, row(crashes));
        add("Fehlersuche", Icons.Kind.MEMORY, "Heap-Dump bei Speichermangel", "Schreibt einen Abzug des Spielspeichers in den Diagnose-Ordner, "
                + "wenn der Speicher ausgeht. Nützlich für Speicherlecks, die Dateien können aber mehrere Gigabyte groß sein.", true,
                toggle(cfg.heapDumpOnOom, on -> cfg.heapDumpOnOom = on));
        add("Fehlersuche", Icons.Kind.BUG, "Debug-Modus", "JVM-Debugging für den Minecraft-Prozess. „Lauschen“ wartet beim Start auf einen "
                + "Debugger, „Anhängen“ verbindet sich mit einem bereits laufenden Debugger.", true,
                Ui.segmented(List.of("off", "listen", "attach"), List.of("Aus", "Lauschen", "Anhängen"), cfg.debugMode, v -> {
                    cfg.debugMode = v;
                    save();
                }));
        JTextField debugAddr = field(cfg.debugAddress, "127.0.0.1:5005", 14);
        onCommit(debugAddr, s -> {
            if (LauncherConfig.isValidDebugAddress(s)) {
                cfg.debugAddress = s;
                save();
            } else {
                host.error("Debug-Adresse", "Nur dieser PC ist erlaubt: localhost, 127.0.0.1 oder [::1] mit Port, z. B. localhost:5005.");
                debugAddr.setText(cfg.debugAddress);
            }
        });
        JButton debugReset = button("Zurücksetzen");
        debugReset.addActionListener(e -> {
            cfg.debugAddress = "127.0.0.1:5005";
            debugAddr.setText(cfg.debugAddress);
            save();
        });
        add("Fehlersuche", Icons.Kind.BUG, "Debug-Adresse", "Host und Port für das JVM-Debugging, nur auf diesem PC (z. B. localhost:5005).", true,
                row(debugAddr, debugReset));
        JButton reset = button("Zurücksetzen");
        reset.setForeground(Theme.FAIL);
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(host.frame(), "Alle Launcher-Einstellungen auf Standard setzen?\n"
                    + "Konten, Spielordner, Welten und der Lumora-Social-Server bleiben erhalten.", "Einstellungen zurücksetzen",
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
                cfg.parallelDownloads = d.parallelDownloads;
                cfg.minimizeToTray = d.minimizeToTray;
                cfg.startPage = d.startPage;
                cfg.showSnapshots = d.showSnapshots;
                cfg.socialShareServer = d.socialShareServer;
                cfg.socialNotifications = d.socialNotifications;
                save();
                JOptionPane.showMessageDialog(host.frame(), "Zurückgesetzt. Die Anzeige aktualisiert sich nach einem Neustart des Launchers.");
            }
        });
        add("Fehlersuche", Icons.Kind.REFRESH, "Einstellungen zurücksetzen", "Setzt Speicher, Java, Fenster, Downloads und Anzeige auf Standard.",
                true, row(reset));
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

    private void rebuild() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        boolean adv = cfg.advancedSettings;
        categories.removeAll();
        for (String c : CATEGORIES) {
            categories.add(new CategoryRow(c));
        }
        categories.revalidate();
        categories.repaint();
        list.removeAll();
        String lastCat = null;
        int shown = 0;
        for (Setting s : settings) {
            if (s.advanced && !adv) {
                continue;
            }
            boolean match = q.isEmpty() ? s.category.equals(category)
                    : (s.title + " " + (s.description == null ? "" : s.description) + " " + s.category).toLowerCase(Locale.ROOT).contains(q);
            if (!match) {
                continue;
            }
            if (!s.category.equals(lastCat)) {
                JLabel h = Ui.label(s.category.toUpperCase(Locale.GERMANY), 13, Font.BOLD, Theme.MUTED);
                h.setBorder(BorderFactory.createEmptyBorder(lastCat == null ? 0 : 14, 2, 8, 0));
                list.add(h);
                lastCat = s.category;
            }
            list.add(card(s));
            list.add(Box.createVerticalStrut(10));
            shown++;
        }
        if (shown == 0) {
            JLabel none = Ui.label(q.isEmpty() ? "Keine Einstellungen in dieser Kategorie." : "Keine Einstellung passt zu „" + search.getText().strip() + "“.",
                    14, Font.PLAIN, Theme.MUTED);
            list.add(none);
        }
        long hidden = settings.stream().filter(s -> s.advanced && (q.isEmpty() ? s.category.equals(category) : true)).count();
        if (!adv && hidden > 0) {
            JLabel hint = Ui.label(hidden + " weitere im erweiterten Modus", 12.5f, Font.PLAIN, Theme.MUTED);
            hint.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
            list.add(hint);
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent card(Setting s) {
        if (s.wide) {
            Card c = new Card(new BorderLayout()).padding(18, 20, 14, 18);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(s.control, BorderLayout.CENTER);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
            return c;
        }
        Card c = new Card(new BorderLayout(16, 0)).padding(14, 18, 14, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel icon = new JLabel(Icons.of(s.icon, 20, Theme.TEXT));
        icon.setVerticalAlignment(JLabel.CENTER);
        c.add(icon, BorderLayout.WEST);
        JPanel text = Ui.column();
        text.add(Ui.label(s.title, 15, Font.BOLD, Theme.TEXT));
        if (s.description != null) {
            text.add(Box.createVerticalStrut(3));
            text.add(Ui.wrapText(s.description, 13, Theme.MUTED));
        }
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(text, BorderLayout.CENTER);
        c.add(center, BorderLayout.CENTER);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        east.setOpaque(false);
        if (s.advanced) {
            east.add(Ui.chip("Erweitert", Theme.MUTED, Theme.TEXT));
        }
        east.add(s.control);
        JPanel eastWrap = new JPanel(new GridLayout(1, 1));
        eastWrap.setOpaque(false);
        JPanel v = new JPanel(new java.awt.GridBagLayout());
        v.setOpaque(false);
        v.add(east);
        eastWrap.add(v);
        c.add(eastWrap, BorderLayout.EAST);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        return c;
    }

    private final class CategoryRow extends JComponent {
        private final String name;
        private boolean hover;

        CategoryRow(String name) {
            this.name = name;
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(220, 36));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    category = name;
                    search.setText("");
                    rebuild();
                    if (name.equals("Speicher")) {
                        refreshStorage();
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            boolean sel = name.equals(category) && search.getText().isBlank();
            if (sel || hover) {
                g.setColor(sel ? Theme.SURFACE_2 : Theme.alpha(Theme.SURFACE_2, 140));
                g.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 10, 10);
            }
            g.setFont(Theme.font(sel ? Font.BOLD : Font.PLAIN, 14f));
            g.setColor(sel ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, 0.4));
            g.drawString(name.equals("Chat") ? "Freunde & Chat" : name, 12, getHeight() / 2 + 5);
            g.dispose();
        }
    }
}
