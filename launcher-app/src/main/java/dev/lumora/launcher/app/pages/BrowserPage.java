package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.modrinth.ModrinthBrowser;
import dev.lumora.launcher.core.profile.ProfileCatalog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * "Entdecken": resource pack browser on Modrinth. Search resource packs for the selected Minecraft version and
 * install them with one click (SHA-512 checked). Mods are not offered any more: Lumora runs without a mod loader.
 */
public final class BrowserPage extends JPanel {
    private static final int PAGE = 20;
    private static final String[] SORTS = {"Relevanz", "Downloads", "Beliebt", "Neu", "Aktualisiert"};
    private static final Map<String, ImageIcon> ICONS = new ConcurrentHashMap<>();

    private final PageHost host;
    private final ExecutorService io;
    private final ModrinthBrowser modrinth;
    private final JPanel filters = Ui.column();
    /** Selected Modrinth categories (all must match). */
    private final java.util.Set<String> categories = new java.util.LinkedHashSet<>();
    private boolean allCategories;
    private boolean allVersions;
    private final JTextField search = new JTextField();
    private final JComboBox<String> sort = new JComboBox<>(SORTS);
    private final JLabel summary = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private final JPanel list = Ui.column();
    private final Timer debounce;
    /** Only resource packs: they work without a mod loader. */
    private final ModrinthBrowser.Kind kind = ModrinthBrowser.Kind.RESOURCE_PACK;
    private final List<ModrinthBrowser.Hit> hits = new ArrayList<>();
    private int total;
    private int generation;

    public BrowserPage(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 14));
        this.host = host;
        this.io = io;
        this.modrinth = new ModrinthBrowser(host.ctx().downloader);
        setOpaque(false);
        JPanel head = Ui.column();
        head.add(Ui.pageTitle("Entdecken", "Ressourcenpakete von Modrinth – passend zur Version, mit Prüfsumme installiert."));
        head.add(Box.createVerticalStrut(14));
        JPanel tools = new JPanel(new BorderLayout(10, 0));
        tools.setOpaque(false);
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = Ui.row(12);
        tools.add(left, BorderLayout.WEST);
        JPanel right = Ui.row(8);
        search.putClientProperty("JTextField.placeholderText", "Auf Modrinth suchen…");
        search.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(240, 34));
        debounce = new Timer(350, e -> reload());
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        sort.setPreferredSize(new Dimension(140, 34));
        sort.addActionListener(e -> reload());
        right.add(search);
        right.add(sort);
        tools.add(right, BorderLayout.EAST);
        tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        head.add(tools);
        head.add(Box.createVerticalStrut(10));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(summary);
        add(head, BorderLayout.NORTH);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(Ui.scroll(list), BorderLayout.CENTER);
        javax.swing.JScrollPane fs = Ui.scroll(filters);
        fs.setPreferredSize(new Dimension(272, 10));
        fs.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        add(fs, BorderLayout.WEST);
    }

    private ProfileCatalog.Profile profile() {
        return host.ctx().selectedProfile();
    }

    /** Called when the page is shown. */
    public void refresh() {
        buildFilters();
        reload();
    }

    /** Modrinth category slugs with German names, in the order shown (first six visible, the rest behind "Mehr"). */
    private static final String[][] PACK_CATEGORIES = {
            {"vanilla-like", "Vanilla-ähnlich"}, {"simplistic", "Schlicht"}, {"realistic", "Realistisch"}, {"themed", "Thema"},
            {"combat", "PvP"}, {"gui", "Oberfläche"}, {"16x", "16x"}, {"32x", "32x"}, {"64x", "64x"}, {"128x", "128x"},
            {"blocks", "Blöcke"}, {"items", "Gegenstände"}, {"entities", "Kreaturen"}, {"environment", "Umgebung"},
            {"fonts", "Schriften"}, {"audio", "Klänge"}, {"tweaks", "Verbesserungen"}, {"utility", "Werkzeuge"}};

    /** Left filter column: categories, loader, game versions (like modrinth.com). */
    private void buildFilters() {
        filters.removeAll();
        String[][] cats = PACK_CATEGORIES;
        filters.add(filterTitle("Kategorien"));
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);
        int shown = allCategories ? cats.length : Math.min(6, cats.length);
        for (int i = 0; i < shown; i++) {
            String slug = cats[i][0];
            grid.add(filterChip(cats[i][1], categories.contains(slug), on -> {
                if (on) {
                    categories.add(slug);
                } else {
                    categories.remove(slug);
                }
                reload();
            }));
        }
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        filters.add(grid);
        if (cats.length > 6) {
            filters.add(more(allCategories ? "Weniger anzeigen" : (cats.length - 6) + " weitere anzeigen", () -> {
                allCategories = !allCategories;
                buildFilters();
            }));
        }
        filters.add(Box.createVerticalStrut(10));
        filters.add(filterTitle("Spielversionen"));
        JPanel versions = new JPanel(new java.awt.GridLayout(0, 3, 8, 8));
        versions.setOpaque(false);
        List<ProfileCatalog.Profile> ps = host.ctx().catalog.profiles();
        String current = profile().id;
        int vShown = allVersions ? ps.size() : Math.min(9, ps.size());
        for (int i = 0; i < vShown; i++) {
            ProfileCatalog.Profile p = ps.get(i);
            versions.add(filterChip(p.minecraftVersion, p.id.equals(current), on -> {
                host.selectProfile(p.id);
                refresh();
            }));
        }
        versions.setAlignmentX(Component.LEFT_ALIGNMENT);
        filters.add(versions);
        if (ps.size() > 9) {
            filters.add(more(allVersions ? "Weniger anzeigen" : (ps.size() - 9) + " weitere anzeigen", () -> {
                allVersions = !allVersions;
                buildFilters();
            }));
        }
        filters.add(Box.createVerticalGlue());
        filters.revalidate();
        filters.repaint();
    }

    private static JComponent filterTitle(String text) {
        JLabel l = Ui.label(text, 15, Font.BOLD, Theme.TEXT);
        l.setBorder(BorderFactory.createEmptyBorder(4, 2, 10, 0));
        return l;
    }

    private static JComponent filterChip(String text, boolean selected, java.util.function.Consumer<Boolean> change) {
        javax.swing.JToggleButton b = new javax.swing.JToggleButton(text, selected);
        b.setFont(Theme.font(Font.BOLD, 12.5f));
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(10, 34));
        b.addActionListener(e -> change.accept(b.isSelected()));
        return b;
    }

    private static JComponent more(String text, Runnable action) {
        JButton b = new JButton(text, Icons.of(Icons.Kind.CHEVRON_DOWN, 14, Theme.TEXT));
        b.setFocusable(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setForeground(Theme.TEXT);
        b.setFont(Theme.font(Font.BOLD, 12.5f));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(b);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return p;
    }

    private void reload() {
        hits.clear();
        total = 0;
        load(0);
    }

    private void load(int offset) {
        int gen = offset == 0 ? ++generation : generation;
        String query = search.getText();
        ModrinthBrowser.Sort s = ModrinthBrowser.Sort.values()[Math.max(0, sort.getSelectedIndex())];
        String mc = profile().minecraftVersion;
        ModrinthBrowser.Kind k = kind;
        List<String> cats = List.copyOf(categories);
        summary.setText("Suche auf Modrinth…");
        io.submit(() -> {
            try {
                ModrinthBrowser.Page page = modrinth.search(query, mc, k, s, cats, offset, PAGE);
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    hits.addAll(page.hits());
                    total = page.total();
                    render();
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        summary.setText("Modrinth ist gerade nicht erreichbar: " + e.getMessage());
                        list.removeAll();
                        list.revalidate();
                        list.repaint();
                    }
                });
            }
        });
    }

    private void render() {
        list.removeAll();
        Path instance = host.ctx().paths.instance(profile().id);
        Map<String, String> installed = ModrinthBrowser.installed(instance, kind);
        Set<String> managed = ModrinthBrowser.managedProjects(host.ctx().catalog, profile());
        summary.setText(String.format(Locale.GERMANY, "%,d Treffer für Minecraft %s", total, profile().minecraftVersion)
                + "  ·  Quelle: modrinth.com");
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) {
                list.add(Box.createVerticalStrut(8));
            }
            ModrinthBrowser.Hit h = hits.get(i);
            list.add(row(h, installed.containsKey(h.projectId()), managed.contains(h.projectId())));
        }
        if (hits.size() < total) {
            list.add(Box.createVerticalStrut(12));
            JButton more = new JButton("Mehr laden (" + hits.size() + " von " + total + ")");
            more.setFocusable(false);
            more.addActionListener(e -> load(hits.size()));
            JPanel w = Ui.row(0);
            w.add(more);
            list.add(w);
        }
        if (hits.isEmpty()) {
            list.add(Ui.label("Nichts gefunden – anderer Suchbegriff oder andere Version?", 14, Font.PLAIN, Theme.MUTED));
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent row(ModrinthBrowser.Hit h, boolean installed, boolean managed) {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(12, 14, 12, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel pic = new JPanel(new BorderLayout());
        pic.setOpaque(false);
        ImageIcon cached = h.iconUrl() == null ? null : ICONS.get(h.iconUrl());
        pic.add(cached != null ? Ui.imageTile(cached, 52, 52) : Ui.letterTile(h.title(), 52, new Color(0x3DD68C)), BorderLayout.NORTH);
        if (cached == null && h.iconUrl() != null && h.iconUrl().startsWith("https://cdn.modrinth.com/")) {
            io.submit(() -> {
                try {
                    ImageIcon icon = Ui.icon(host.ctx().downloader.fetchBytes(URI.create(h.iconUrl()), 512 * 1024), 52);
                    if (icon != null) {
                        ICONS.put(h.iconUrl(), icon);
                        SwingUtilities.invokeLater(() -> {
                            pic.removeAll();
                            pic.add(Ui.imageTile(icon, 52, 52), BorderLayout.NORTH);
                            pic.revalidate();
                            pic.repaint();
                        });
                    }
                } catch (IOException | RuntimeException ignored) {
                    // icon is cosmetic
                }
            });
        }
        c.add(pic, BorderLayout.WEST);

        JPanel text = Ui.column();
        JPanel head = Ui.row(8);
        head.add(Ui.label(h.title(), 15.5f, Font.BOLD, Theme.TEXT));
        head.add(Ui.label("von " + h.author(), 12.5f, Font.PLAIN, Theme.MUTED));
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        text.add(head);
        text.add(Box.createVerticalStrut(4));
        if (h.description() != null) {
            text.add(Ui.wrapText(h.description().length() > 220 ? h.description().substring(0, 217) + "…" : h.description(), 13f, Theme.MUTED));
            text.add(Box.createVerticalStrut(4));
        }
        List<String> meta = new ArrayList<>();
        meta.add(String.format(Locale.GERMANY, "%,d Downloads", h.downloads()));
        if (h.license() != null) {
            meta.add(h.license().replace("LicenseRef-", ""));
        }
        h.categories().stream().filter(cat -> !cat.equals("fabric") && !cat.equals("minecraft")).limit(3).forEach(meta::add);
        text.add(Ui.label(String.join("  ·  ", meta), 12, Font.PLAIN, new Color(0x7D8794)));
        c.add(text, BorderLayout.CENTER);

        JPanel side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        JButton action;
        if (managed) {
            action = new JButton("In Lumora enthalten", Icons.of(Icons.Kind.CHECK, 15, Theme.OK));
            action.setEnabled(false);
        } else if (installed) {
            action = new JButton("Installiert", Icons.of(Icons.Kind.CHECK, 15, Theme.OK));
            action.setToolTipText("Erneut klicken aktualisiert auf die neueste passende Version");
            action.addActionListener(e -> install(h, (JButton) e.getSource()));
        } else {
            action = new JButton("Installieren", Icons.of(Icons.Kind.PLUS, 15, Color.WHITE));
            action.putClientProperty("JButton.buttonType", "roundRect");
            action.setBackground(Theme.ACCENT_PRESSED);
            action.setForeground(Color.WHITE);
            action.addActionListener(e -> install(h, (JButton) e.getSource()));
        }
        action.setFocusable(false);
        side.add(action, BorderLayout.NORTH);
        JLabel page = Ui.label("Modrinth ↗", 12.5f, Font.BOLD, Theme.ACCENT_HOVER);
        page.setHorizontalAlignment(JLabel.RIGHT);
        page.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        page.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        page.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                host.browse(h.pageUrl(kind));
            }
        });
        side.add(page, BorderLayout.SOUTH);
        c.add(side, BorderLayout.EAST);
        return c;
    }

    private void install(ModrinthBrowser.Hit h, JButton button) {
        if (host.gameRunning()) {
            host.error("Installation nicht möglich", "Minecraft läuft gerade. Beende das Spiel, dann installiere das Paket.");
            return;
        }
        ProfileCatalog.Profile p = profile();
        Path instance = host.ctx().paths.instance(p.id);
        ModrinthBrowser.Kind k = kind;
        Set<String> managed = ModrinthBrowser.managedProjects(host.ctx().catalog, p);
        button.setEnabled(false);
        button.setText("Installiere…");
        io.submit(() -> {
            try {
                ModrinthBrowser.Installed r = modrinth.install(h.projectId(), p.minecraftVersion, k, instance, managed);
                SwingUtilities.invokeLater(() -> {
                    render();
                    StringBuilder sb = new StringBuilder(h.title() + " ist für Minecraft " + p.minecraftVersion + " installiert.\n\n");
                    r.files().forEach(f -> sb.append("✓ ").append(f).append('\n'));
                    if (!r.skippedManaged().isEmpty()) {
                        sb.append("\nSchon von Lumora mitgebracht: ").append(r.skippedManaged().size()).append('\n');
                    }
                    r.notes().forEach(n -> sb.append("\nHinweis: ").append(n));
                    sb.append("\nJede Datei wurde mit der SHA-512-Prüfsumme von Modrinth verifiziert.");
                    JOptionPane.showMessageDialog(host.frame(), sb.toString(), "Installiert", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    button.setEnabled(true);
                    button.setText("Installieren");
                    host.error("Installation fehlgeschlagen", h.title() + ": " + e.getMessage());
                });
            }
        });
    }
}
