package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.mods.ModInfo;
import dev.lumora.launcher.core.mods.ModInventory;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * "Mods" page: every mod of the selected Minecraft version in one list - core mods (always loaded), the optional
 * extensions (switch per mod, for all versions) and the player's own jars in mods/ (switchable, addable by
 * button or drag and drop). Icons, descriptions, authors and licenses come from the jars themselves.
 */
public final class ModsPage extends JPanel {
    private static final Map<String, String> EXTENSION_TEXT = Map.of(
            "modmenu", "Übersicht aller installierten Mods mit direktem Zugriff auf ihre Einstellungen.",
            "ferritecore", "Senkt den Arbeitsspeicherbedarf von Blockzuständen und Modellen deutlich.",
            "lithium", "Optimiert Spiellogik und Tick-Berechnung – spürbar vor allem im Einzelspieler.",
            "voicechat", "Proximity-Voice-Chat. Funktioniert auf Servern, die Simple Voice Chat installiert haben.");
    private static final Map<String, String> CORE_TEXT = Map.of(
            "fabric-api", "Grundbibliothek, die fast alle Fabric-Mods brauchen.",
            "sodium", "Moderner Renderer: deutlich mehr FPS und flüssigere Frametimes.",
            "viafabricplus", "Verbindet mit Servern anderer Minecraft-Versionen (Protokollübersetzung).",
            "lumora-client", "Lumora-Oberfläche: Menüs im Launcher-Design, Hub, Schlaganimation, Diagnose.");
    private static final String[] FILTERS = {"Alles", "Kern-Mods", "Erweiterungen", "Eigene Mods", "Nur aktive", "Ressourcenpakete"};

    private final PageHost host;
    private final ExecutorService io;
    private final JPanel versionBar = Ui.row(0);
    private final JTextField search = new JTextField();
    private final JComboBox<String> filter = new JComboBox<>(FILTERS);
    private final JLabel summary = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private final JPanel list = Ui.column();
    private List<ModInventory.Entry> entries = List.of();
    private int generation;

    public ModsPage(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 14));
        this.host = host;
        this.io = io;
        setOpaque(false);

        JPanel head = Ui.column();
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(Ui.pageTitle("Mods", "Alle Mods der gewählten Version – Kern-Mods, Erweiterungen und deine eigenen."), BorderLayout.WEST);
        JPanel actions = Ui.row(8);
        actions.add(button("Mod hinzufügen", Icons.Kind.PLUS, this::chooseMods));
        actions.add(button("Mods-Ordner", Icons.Kind.FOLDER, () -> host.openFolder(modsDir())));
        actions.add(button("Updates prüfen", Icons.Kind.REFRESH, host::checkUpdates));
        JPanel aw = new JPanel(new BorderLayout());
        aw.setOpaque(false);
        aw.add(actions, BorderLayout.SOUTH);
        titleRow.add(aw, BorderLayout.EAST);
        head.add(titleRow);
        head.add(Box.createVerticalStrut(14));

        JPanel tools = new JPanel(new BorderLayout(10, 0));
        tools.setOpaque(false);
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        tools.add(versionBar, BorderLayout.WEST);
        JPanel right = Ui.row(8);
        search.putClientProperty("JTextField.placeholderText", "Mods durchsuchen…");
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(240, 34));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                render();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                render();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                render();
            }
        });
        filter.setPreferredSize(new Dimension(150, 34));
        filter.addActionListener(e -> render());
        right.add(search);
        right.add(filter);
        tools.add(right, BorderLayout.EAST);
        tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        head.add(tools);
        head.add(Box.createVerticalStrut(10));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(summary);
        add(head, BorderLayout.NORTH);

        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(Ui.scroll(list), BorderLayout.CENTER);

        // drop .jar files anywhere on the page to add them
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport s) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    addMods(files);
                    return true;
                } catch (java.awt.datatransfer.UnsupportedFlavorException | IOException e) {
                    return false;
                }
            }
        });
    }

    private ProfileCatalog.Profile profile() {
        return host.ctx().selectedProfile();
    }

    private Path modsDir() {
        return host.ctx().paths.instance(profile().id).resolve("mods");
    }

    /** Re-reads the mods folder of the selected version (in the background) and redraws the list. */
    public void refresh() {
        rebuildVersionBar();
        int gen = ++generation;
        ProfileCatalog.Profile p = profile();
        List<String> enabled = List.copyOf(host.ctx().config.enabledExtensions);
        List<String> disabledPacks = List.copyOf(host.ctx().config.disabledResourcePacks);
        Path mods = modsDir();
        summary.setText("Lese Mods…");
        io.submit(() -> {
            List<ModInventory.Entry> scanned = ModInventory.scan(host.ctx().catalog, p, enabled, disabledPacks, mods, this::locate);
            SwingUtilities.invokeLater(() -> {
                if (gen == generation) {
                    entries = scanned;
                    render();
                }
            });
        });
    }

    /** Download cache or bundled folder: lets the list show icons and texts before the first start. */
    private Path locate(ProfileCatalog.Artifact a) {
        try {
            if (a.source == ProfileCatalog.Source.MODRINTH) {
                return FileUtil.resolveInside(host.ctx().paths.modCache(), a.id + "/" + a.fileName);
            }
            Path bundled = dev.lumora.launcher.core.mods.BundledMods.locate();
            return bundled == null ? null : bundled.resolve(a.fileName);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void rebuildVersionBar() {
        versionBar.removeAll();
        List<ProfileCatalog.Profile> ps = host.ctx().catalog.profiles();
        versionBar.add(Ui.segmented(ps.stream().map(p -> p.id).toList(),
                ps.stream().map(p -> "Minecraft " + p.minecraftVersion).toList(), profile().id, id -> {
                    host.selectProfile(id);
                    refresh();
                }));
        versionBar.revalidate();
        versionBar.repaint();
    }

    private void render() {
        list.removeAll();
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        int mode = filter.getSelectedIndex();
        int shown = 0;
        ModInventory.Kind section = null;
        for (ModInventory.Entry e : entries) {
            if (!matches(e, q, mode)) {
                continue;
            }
            if (e.kind() != section) {
                section = e.kind();
                if (shown > 0) {
                    list.add(Box.createVerticalStrut(14));
                }
                list.add(Ui.sectionTitle(switch (section) {
                    case CORE -> "Kern-Mods";
                    case EXTENSION -> "Erweiterungen";
                    case DEPENDENCY -> "Hilfs-Mods";
                    case RESOURCE_PACK -> "Ressourcenpakete";
                    case USER -> "Eigene Mods";
                }, switch (section) {
                    case CORE -> "immer aktiv, von Lumora geprüft";
                    case EXTENSION -> "optional, Schalter gilt für alle Versionen";
                    case DEPENDENCY -> "werden automatisch geladen, wenn ein Ressourcenpaket sie braucht";
                    case RESOURCE_PACK -> "passende Version von Modrinth, SHA-512 geprüft, im Spiel aktiviert";
                    case USER -> "aus deinem mods-Ordner, nicht von Lumora geprüft";
                }));
            } else {
                list.add(Box.createVerticalStrut(8));
            }
            list.add(row(e));
            shown++;
        }
        boolean hasUser = entries.stream().anyMatch(e -> e.kind() == ModInventory.Kind.USER);
        if (!hasUser && (mode == 0 || mode == 3) && q.isEmpty()) {
            list.add(Box.createVerticalStrut(14));
            list.add(Ui.sectionTitle("Eigene Mods", null));
            list.add(emptyUserHint());
            shown++;
        }
        if (shown == 0) {
            list.add(Ui.label("Keine Mods gefunden.", 14, Font.PLAIN, Theme.MUTED));
        }
        long mods = entries.stream().filter(e -> e.kind() != ModInventory.Kind.RESOURCE_PACK).count();
        long packs = entries.stream().filter(e -> e.kind() == ModInventory.Kind.RESOURCE_PACK && e.enabled()).count();
        long active = entries.stream().filter(e -> e.kind() != ModInventory.Kind.RESOURCE_PACK && e.enabled()).count();
        long user = entries.stream().filter(e -> e.kind() == ModInventory.Kind.USER).count();
        long pending = entries.stream().filter(e -> e.enabled() && !e.installed()).count();
        summary.setText(mods + " Mods für Minecraft " + profile().minecraftVersion + "  ·  " + active + " aktiv  ·  " + packs + " Ressourcenpakete"
                + (user > 0 ? "  ·  " + user + " eigene" : "")
                + (pending > 0 ? "  ·  " + pending + " werden beim nächsten Start geladen" : ""));
        list.revalidate();
        list.repaint();
    }

    private static boolean matches(ModInventory.Entry e, String q, int mode) {
        boolean kindOk = switch (mode) {
            case 1 -> e.kind() == ModInventory.Kind.CORE;
            case 2 -> e.kind() == ModInventory.Kind.EXTENSION;
            case 3 -> e.kind() == ModInventory.Kind.USER;
            case 4 -> e.enabled();
            case 5 -> e.kind() == ModInventory.Kind.RESOURCE_PACK;
            default -> true;
        };
        if (!kindOk) {
            return false;
        }
        if (q.isEmpty()) {
            return true;
        }
        ModInfo i = e.info();
        String hay = (e.displayName() + " " + e.version() + " " + (i != null ? i.id() + " " + String.valueOf(i.description())
                + " " + String.join(" ", i.authors()) : "")).toLowerCase(Locale.ROOT);
        return hay.contains(q);
    }

    private JComponent row(ModInventory.Entry e) {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(12, 14, 12, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        ModInfo info = e.info();
        Color tint = switch (e.kind()) {
            case CORE -> Theme.ACCENT;
            case EXTENSION -> new Color(0x8B7CF6);
            case DEPENDENCY -> new Color(0x7D8794);
            case RESOURCE_PACK -> new Color(0x3DD68C);
            case USER -> new Color(0xF2A541);
        };
        ImageIcon icon = info != null ? Ui.icon(info.icon(), 44) : null;
        JComponent pic = icon != null ? Ui.imageTile(icon, 44, 44) : Ui.letterTile(e.displayName(), 44, tint);
        JPanel picWrap = new JPanel(new BorderLayout());
        picWrap.setOpaque(false);
        picWrap.add(pic, BorderLayout.NORTH);
        c.add(picWrap, BorderLayout.WEST);

        JPanel text = Ui.column();
        JPanel head = Ui.row(8);
        head.add(Ui.label(e.displayName(), 15.5f, Font.BOLD, e.enabled() ? Theme.TEXT : Theme.MUTED));
        head.add(Ui.label(e.version(), 12.5f, Font.PLAIN, Theme.MUTED));
        head.add(Ui.chip(switch (e.kind()) {
            case CORE -> "Kern";
            case EXTENSION -> "Erweiterung";
            case DEPENDENCY -> "Hilfs-Mod";
            case RESOURCE_PACK -> "Ressourcenpaket";
            case USER -> "Eigene";
        }, tint, tint.brighter()));
        if (!e.enabled()) {
            head.add(Ui.chip("deaktiviert", Theme.MUTED, Theme.MUTED));
        } else if (!e.installed()) {
            head.add(Ui.chip("wird beim Start geladen", Theme.WARN, Theme.WARN));
        }
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        text.add(head);
        text.add(Box.createVerticalStrut(4));
        String key = e.artifact() != null ? e.artifact().key() : null;
        // German texts for the curated mods, otherwise the description from the jar
        String curated = key == null ? null : EXTENSION_TEXT.getOrDefault(key, CORE_TEXT.get(key));
        String desc = curated != null ? curated
                : info != null && info.description() != null ? info.description() : "";
        if (!desc.isBlank()) {
            text.add(Ui.wrapText(firstLines(desc), 13f, Theme.MUTED));
            text.add(Box.createVerticalStrut(4));
        }
        text.add(metaLine(e));
        c.add(text, BorderLayout.CENTER);

        JPanel side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        side.add(control(e), BorderLayout.NORTH);
        String link = link(e);
        if (link != null) {
            JLabel l = Ui.label(link.contains("modrinth.com") ? "Modrinth ↗" : "Webseite ↗", 12.5f, Font.BOLD, Theme.ACCENT_HOVER);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.setHorizontalAlignment(JLabel.RIGHT);
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent ev) {
                    host.browse(link);
                }
            });
            l.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            side.add(l, BorderLayout.SOUTH);
        }
        c.add(side, BorderLayout.EAST);
        return c;
    }

    private static String firstLines(String s) {
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() > 220 ? t.substring(0, 217) + "…" : t;
    }

    private static JComponent metaLine(ModInventory.Entry e) {
        List<String> parts = new ArrayList<>();
        ModInfo i = e.info();
        if (i != null && !i.authors().isEmpty()) {
            List<String> a = i.authors();
            parts.add("von " + String.join(", ", a.subList(0, Math.min(3, a.size()))) + (a.size() > 3 ? " u. a." : ""));
        }
        String license = i != null && i.license() != null ? i.license() : e.artifact() != null ? e.artifact().license : null;
        if (license != null) {
            parts.add(license.contains(" (") ? license.substring(0, license.indexOf(" (")) : license);
        }
        if (i != null && i.bundledModules() > 0) {
            parts.add("enthält " + i.bundledModules() + " Module");
        }
        long size = i != null ? i.size() : e.artifact() != null ? e.artifact().size : 0;
        if (size > 0) {
            parts.add(Ui.size(size));
        }
        if (e.kind() != ModInventory.Kind.USER && e.artifact() != null) {
            parts.add(e.artifact().source == ProfileCatalog.Source.BUNDLED ? "mitgeliefert" : "SHA-512 geprüft");
        } else if (e.file() != null) {
            parts.add(e.file().getFileName().toString());
        }
        return Ui.label(String.join("  ·  ", parts), 12, Font.PLAIN, new Color(0x7D8794));
    }

    private static String link(ModInventory.Entry e) {
        if (e.artifact() != null && e.artifact().modrinthProjectId != null) {
            return "https://modrinth.com/project/" + e.artifact().modrinthProjectId;
        }
        ModInfo i = e.info();
        String url = i == null ? null : i.homepage() != null ? i.homepage() : i.sources();
        return url != null && url.startsWith("https://") ? url : null;
    }

    private JComponent control(ModInventory.Entry e) {
        if (e.kind() == ModInventory.Kind.DEPENDENCY) {
            JLabel l = new JLabel(e.enabled() ? "automatisch" : "nicht benötigt", Icons.of(Icons.Kind.LOCK, 14, Theme.MUTED), JLabel.RIGHT);
            l.setFont(Theme.font(Font.PLAIN, 12.5f));
            l.setForeground(Theme.MUTED);
            return l;
        }
        if (e.kind() == ModInventory.Kind.CORE) {
            JLabel l = new JLabel("Immer aktiv", Icons.of(Icons.Kind.LOCK, 14, Theme.MUTED), JLabel.RIGHT);
            l.setFont(Theme.font(Font.PLAIN, 12.5f));
            l.setForeground(Theme.MUTED);
            return l;
        }
        ToggleSwitch sw = new ToggleSwitch(e.enabled());
        sw.onChange(on -> {
            if (e.kind() == ModInventory.Kind.RESOURCE_PACK) {
                List<String> off = host.ctx().config.disabledResourcePacks;
                String key = e.artifact().key();
                if (on) {
                    off.remove(key);
                } else if (!off.contains(key)) {
                    off.add(key);
                }
                host.saveConfig();
                refresh();
            } else if (e.kind() == ModInventory.Kind.EXTENSION) {
                List<String> ext = host.ctx().config.enabledExtensions;
                String key = e.artifact().key();
                if (on && !ext.contains(key)) {
                    ext.add(key);
                } else if (!on) {
                    ext.remove(key);
                }
                host.saveConfig();
                host.extensionsChanged();
                refresh();
            } else {
                io.submit(() -> {
                    try {
                        ModInventory.setUserModEnabled(e.file(), on);
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> host.error("Mod konnte nicht umgeschaltet werden",
                                e.file().getFileName() + ": " + ex.getMessage() + "\nLäuft Minecraft noch?"));
                    }
                    SwingUtilities.invokeLater(this::refresh);
                });
            }
        });
        JPanel w = new JPanel(new BorderLayout());
        w.setOpaque(false);
        w.add(sw, BorderLayout.EAST);
        return w;
    }

    private JComponent emptyUserHint() {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(16, 16, 16, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.add(new JLabel(Icons.of(Icons.Kind.PLUS, 24, Theme.ACCENT_HOVER)), BorderLayout.WEST);
        JPanel t = Ui.column();
        t.add(Ui.label("Eigene Fabric-Mods hinzufügen", 15, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(2));
        t.add(Ui.wrapText("Ziehe .jar-Dateien hierher oder nutze „Mod hinzufügen“. Sie landen im mods-Ordner von Minecraft "
                + profile().minecraftVersion + " und werden vor jedem Start auf Kompatibilität geprüft.", 13f, Theme.MUTED));
        c.add(t, BorderLayout.CENTER);
        return c;
    }

    private void chooseMods() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter("Fabric-Mods (*.jar)", "jar"));
        if (fc.showOpenDialog(host.frame()) == JFileChooser.APPROVE_OPTION) {
            addMods(List.of(fc.getSelectedFiles()));
        }
    }

    /** Copies Fabric mod jars into the selected version's mods folder (only after checking fabric.mod.json). */
    private void addMods(List<File> files) {
        Path mods = modsDir();
        String version = profile().minecraftVersion;
        io.submit(() -> {
            List<String> added = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            for (File f : files) {
                Path src = f.toPath();
                String name = src.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".jar") || !Files.isRegularFile(src)) {
                    rejected.add(name + " (keine .jar-Datei)");
                    continue;
                }
                try {
                    ModInfo info = ModInfo.read(src);
                    Files.createDirectories(mods);
                    Path dest = FileUtil.resolveInside(mods, name);
                    if (Files.exists(dest) && !confirmReplace(name)) {
                        continue;
                    }
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    added.add(info.name() + " " + (info.version() != null ? info.version() : ""));
                    Log.info("Added user mod " + name + " to " + mods);
                } catch (IOException e) {
                    rejected.add(name + " (" + e.getMessage() + ")");
                }
            }
            SwingUtilities.invokeLater(() -> {
                refresh();
                if (!rejected.isEmpty()) {
                    host.error("Nicht hinzugefügt", String.join("\n", rejected));
                } else if (!added.isEmpty()) {
                    JOptionPane.showMessageDialog(host.frame(), "Hinzugefügt für Minecraft " + version + ":\n" + String.join("\n", added)
                            + "\n\nVor dem Start prüft der Launcher, ob die Mods zu dieser Version passen.",
                            "Mods hinzugefügt", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        });
    }

    private boolean confirmReplace(String name) {
        boolean[] answer = {false};
        try {
            SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showConfirmDialog(host.frame(),
                    name + " existiert bereits. Ersetzen?", "Mod hinzufügen", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException e) {
            return false;
        }
        return answer[0];
    }

    private static JButton button(String text, Icons.Kind icon, Runnable action) {
        JButton b = new JButton(text, Icons.of(icon, 16, Theme.TEXT));
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }
}
