package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Avatar;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.SkinViewer;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.auth.AccountStore;
import dev.lumora.launcher.core.skin.SkinService;
import dev.lumora.launcher.core.stats.PlayStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * "Konto": account manager (several Microsoft accounts - switch, add, remove) and a rotatable 3D preview of the
 * active account's skin. Any other player's skin can be looked up by name. Skins come from Mojang's public profile
 * services; the account tokens stay in the encrypted account store.
 */
public final class AccountPage extends JPanel {
    private final PageHost host;
    private final ExecutorService io;
    private final SkinService skins;
    private final SkinViewer viewer = new SkinViewer();
    private final JLabel skinTitle = Ui.label(" ", 18, Font.BOLD, Theme.TEXT);
    private final JLabel skinHint = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private final JPanel accounts = Ui.column();
    private final JTextField lookup = new JTextField();
    private int generation;

    public AccountPage(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 18));
        this.host = host;
        this.io = io;
        this.skins = new SkinService(host.ctx().downloader, host.ctx().paths.root().resolve("cache").resolve("skins"));
        setOpaque(false);
        JPanel top = Ui.column();
        top.add(Ui.pageTitle("Konto", "Konten, Skin, Sicherheit und Verlauf."));
        top.add(Box.createVerticalStrut(14));
        tabRow = Ui.segmented(TABS, List.of("Allgemein", "Sicherheit", "Verlauf"),
                "general", this::showTab);
        tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(tabRow);
        add(top, BorderLayout.NORTH);

        Card skinCard = new Card(new BorderLayout(0, 10));
        skinCard.padding(16, 16, 16, 16);
        JPanel st = Ui.column();
        st.add(skinTitle);
        st.add(skinHint);
        skinCard.add(st, BorderLayout.NORTH);
        viewer.setPreferredSize(new Dimension(300, 420));
        skinCard.add(viewer, BorderLayout.CENTER);
        JPanel look = new JPanel(new BorderLayout(8, 0));
        look.setOpaque(false);
        lookup.putClientProperty("JTextField.placeholderText", "Skin eines Spielers ansehen…");
        lookup.putClientProperty("JTextField.leadingIcon", Icons.of(Icons.Kind.SEARCH, 16, Theme.MUTED));
        lookup.addActionListener(e -> showByName(lookup.getText()));
        JButton go = new JButton("Ansehen");
        go.setFocusable(false);
        go.addActionListener(e -> showByName(lookup.getText()));
        look.add(lookup, BorderLayout.CENTER);
        look.add(go, BorderLayout.EAST);
        skinCard.add(look, BorderLayout.SOUTH);
        skinCard.setPreferredSize(new Dimension(360, 10));

        JPanel right = Ui.column();
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(Ui.sectionTitle("Konten", "das aktive Konto wird beim Spielen verwendet"), BorderLayout.WEST);
        JButton add = new JButton("Konto hinzufügen", Icons.of(Icons.Kind.PLUS, 15, Color.WHITE));
        add.putClientProperty("JButton.buttonType", "roundRect");
        add.setBackground(Theme.ACCENT_PRESSED);
        add.setForeground(Color.WHITE);
        add.setFocusable(false);
        add.addActionListener(e -> host.signIn());
        JPanel aw = new JPanel(new BorderLayout());
        aw.setOpaque(false);
        aw.add(add, BorderLayout.NORTH);
        head.add(aw, BorderLayout.EAST);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        right.add(head);
        accounts.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(accounts);
        right.add(Box.createVerticalStrut(18));
        right.add(skinTools());

        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.add(skinCard, BorderLayout.WEST);
        body.add(Ui.scroll(right), BorderLayout.CENTER);
        security = new SecurityTab(host, this::removeAccount, this::useAccount);
        historyTab = new HistoryTab(host);
        tabs.setOpaque(false);
        tabs.add(body, "general");
        tabs.add(security, "security");
        tabs.add(historyTab, "history");
        add(tabs, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------ tabs

    private final java.awt.CardLayout tabLayout = new java.awt.CardLayout();
    private final JPanel tabs = new JPanel(tabLayout);
    private final SecurityTab security;
    private final HistoryTab historyTab;

    private static final List<String> TABS = List.of("general", "security", "history");
    private JPanel tabRow;

    /** Opens a tab ("general", "security", "history") and marks it in the tab row. */
    public void selectTab(String id) {
        int i = TABS.indexOf(id);
        if (i < 0) {
            return;
        }
        if (tabRow.getComponent(i) instanceof javax.swing.AbstractButton b) {
            b.setSelected(true);
        }
        showTab(id);
    }

    private void showTab(String id) {
        tabLayout.show(tabs, id);
        security.refresh();
        historyTab.refresh();
    }

    /** Removes the stored sign-in of an account from this PC (after asking). */
    void removeAccount(AccountStore.Account a) {
        if (JOptionPane.showConfirmDialog(host.frame(), a.username() + " von diesem PC abmelden? Die gespeicherte Anmeldung wird "
                + "gelöscht, das Microsoft-Konto selbst bleibt unverändert.", "Abmelden", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            host.ctx().accounts.remove(a.uuid());
            host.ctx().play.history().record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.REMOVED, a.uuid(), a.username(),
                    "", Instant.now());
        } catch (IOException ex) {
            host.error("Abmelden fehlgeschlagen", ex.getMessage());
        }
        host.accountsChanged();
        refresh();
    }

    void useAccount(AccountStore.Account a) {
        try {
            host.ctx().accounts.setActive(a.uuid());
        } catch (IOException ex) {
            host.error("Wechsel fehlgeschlagen", ex.getMessage());
        }
        host.accountsChanged();
        refresh();
    }

    // ------------------------------------------------------------------ skin & cape

    private final JPanel library = Ui.column();
    private final javax.swing.JComboBox<Object> capes = new javax.swing.JComboBox<>();
    private final JLabel skinStatus = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private boolean fillingCapes;
    private dev.lumora.launcher.core.skin.SkinChanger changer;

    private dev.lumora.launcher.core.skin.SkinChanger changer() {
        if (changer == null) {
            changer = new dev.lumora.launcher.core.skin.SkinChanger(host.ctx().paths.root());
        }
        return changer;
    }

    /** Upload / reset the skin, choose the cape and the local skin library. */
    private JComponent skinTools() {
        JPanel col = Ui.column();
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(Ui.sectionTitle("Skin & Umhang", "für das aktive Konto, direkt bei Mojang"));
        Card c = new Card(new BorderLayout(0, 12));
        c.padding(14, 16, 14, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton upload = new JButton("Skin hochladen …", Icons.of(Icons.Kind.SHIRT, 15, Theme.TEXT));
        upload.setFocusable(false);
        upload.addActionListener(e -> chooseAndUpload());
        JButton reset = new JButton("Standard-Skin", Icons.of(Icons.Kind.REFRESH, 15, Theme.TEXT));
        reset.setFocusable(false);
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(host.frame(), "Skin auf den Standard (Steve/Alex) zurücksetzen?", "Skin",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                withToken("Setze Skin zurück …", t -> changer().resetSkin(t), "Skin zurückgesetzt.");
            }
        });
        buttons.add(upload);
        buttons.add(reset);
        c.add(buttons, BorderLayout.NORTH);
        JPanel capeRow = new JPanel(new BorderLayout(10, 0));
        capeRow.setOpaque(false);
        capeRow.add(Ui.label("Umhang", 13.5f, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        capes.setPreferredSize(new Dimension(220, 32));
        capes.addActionListener(e -> {
            if (fillingCapes) {
                return;
            }
            Object sel = capes.getSelectedItem();
            String id = sel instanceof dev.lumora.launcher.core.skin.SkinChanger.Texture t ? t.id() : null;
            withToken("Ändere Umhang …", t -> changer().setCape(t, id), id == null ? "Umhang ausgeblendet." : "Umhang geändert.");
        });
        capes.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                setText(v instanceof dev.lumora.launcher.core.skin.SkinChanger.Texture t
                        ? (t.alias() != null ? t.alias() : "Umhang") : String.valueOf(v));
                return this;
            }
        });
        JPanel cw = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        cw.setOpaque(false);
        cw.add(capes);
        capeRow.add(cw, BorderLayout.CENTER);
        c.add(capeRow, BorderLayout.CENTER);
        c.add(skinStatus, BorderLayout.SOUTH);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        col.add(c);
        col.add(Box.createVerticalStrut(14));
        col.add(Ui.sectionTitle("Skin-Bibliothek", "gespeicherte Skins mit einem Klick anlegen"));
        library.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(library);
        return col;
    }

    private void refreshSkinTools() {
        fillLibrary();
        if (host.ctx().play.storedSession() == null) {
            skinStatus.setText("Melde dich an, um Skin und Umhang zu ändern.");
            fillCapes(null);
            return;
        }
        io.submit(() -> {
            try {
                var s = host.ctx().play.freshSession(host.ctx().config);
                if (s == null) {
                    SwingUtilities.invokeLater(() -> skinStatus.setText("Bitte einmal neu anmelden, um den Skin zu ändern."));
                    return;
                }
                var p = changer().profile(s.accessToken());
                SwingUtilities.invokeLater(() -> {
                    fillCapes(p);
                    skinStatus.setText(p.capes().isEmpty() ? "Dieses Konto besitzt keine Umhänge." : p.capes().size() + " Umhang/Umhänge im Besitz.");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> skinStatus.setText(e.getMessage()));
            }
        });
    }

    private void fillCapes(dev.lumora.launcher.core.skin.SkinChanger.Profile p) {
        fillingCapes = true;
        capes.removeAllItems();
        capes.addItem("Kein Umhang");
        if (p != null) {
            for (var t : p.capes()) {
                capes.addItem(t);
                if (t.active()) {
                    capes.setSelectedItem(t);
                }
            }
        }
        capes.setEnabled(p != null && !p.capes().isEmpty());
        fillingCapes = false;
    }

    private void fillLibrary() {
        library.removeAll();
        var saved = changer().library();
        if (saved.isEmpty()) {
            library.add(Ui.wrapText("Noch keine Skins gespeichert. Beim Hochladen kannst du einen Skin hier ablegen und später "
                    + "mit einem Klick wieder anlegen.", 12.5f, Theme.MUTED));
        }
        for (var s : saved) {
            Card row = new Card(new BorderLayout(12, 0));
            row.padding(8, 12, 8, 10);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            byte[] png = readQuietly(s.file());
            row.add(new JLabel(face(png)), BorderLayout.WEST);
            JPanel t = Ui.column();
            t.add(Ui.label(s.name(), 14, Font.BOLD, Theme.TEXT));
            t.add(Ui.label(s.slim() ? "schmal (Alex)" : "breit (Steve)", 12, Font.PLAIN, Theme.MUTED));
            row.add(t, BorderLayout.CENTER);
            JPanel b = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
            b.setOpaque(false);
            JButton view = new JButton(Icons.of(Icons.Kind.USER, 14, Theme.TEXT));
            view.setToolTipText("Vorschau");
            view.setFocusable(false);
            view.addActionListener(e -> {
                viewer.setSkin(png, s.slim());
                skinTitle.setText(s.name());
                skinHint.setText("Vorschau aus der Bibliothek");
            });
            JButton use = new JButton("Anlegen");
            use.setFocusable(false);
            use.addActionListener(e -> upload(png, s.slim()));
            JButton del = new JButton(Icons.of(Icons.Kind.TRASH, 14, Theme.FAIL));
            del.setToolTipText("Aus der Bibliothek löschen");
            del.setFocusable(false);
            del.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(host.frame(), "„" + s.name() + "“ aus der Bibliothek löschen?", "Skin-Bibliothek",
                        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    try {
                        changer().delete(s.id());
                    } catch (IOException ex) {
                        host.error("Skin-Bibliothek", ex.getMessage());
                    }
                    fillLibrary();
                }
            });
            b.add(view);
            b.add(use);
            b.add(del);
            row.add(b, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            library.add(row);
            library.add(Box.createVerticalStrut(6));
        }
        library.revalidate();
        library.repaint();
    }

    private static byte[] readQuietly(Path f) {
        try {
            return java.nio.file.Files.readAllBytes(f);
        } catch (IOException e) {
            return null;
        }
    }

    /** 32 px head (face layer + hat layer) of a skin PNG. */
    private static javax.swing.Icon face(byte[] png) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            java.awt.Graphics2D g = out.createGraphics();
            g.drawImage(img, 0, 0, 32, 32, 8, 8, 16, 16, null);
            g.drawImage(img, 0, 0, 32, 32, 40, 8, 48, 16, null);
            g.dispose();
        } catch (IOException | RuntimeException e) {
            // empty head
        }
        return new javax.swing.ImageIcon(out);
    }

    private void chooseAndUpload() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Skin auswählen (PNG, 64×64)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Minecraft-Skin (PNG)", "png"));
        if (fc.showOpenDialog(host.frame()) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        byte[] png;
        try {
            png = java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath());
            dev.lumora.launcher.core.skin.SkinChanger.checkSkin(png);
        } catch (IOException | IllegalArgumentException e) {
            host.error("Skin", e.getMessage());
            return;
        }
        viewer.setSkin(png, false);
        javax.swing.JRadioButton classic = new javax.swing.JRadioButton("Breite Arme (Steve)", true);
        javax.swing.JRadioButton slim = new javax.swing.JRadioButton("Schmale Arme (Alex)");
        javax.swing.ButtonGroup g = new javax.swing.ButtonGroup();
        g.add(classic);
        g.add(slim);
        slim.addActionListener(e -> viewer.setSkin(png, true));
        classic.addActionListener(e -> viewer.setSkin(png, false));
        javax.swing.JCheckBox keep = new javax.swing.JCheckBox("In der Skin-Bibliothek speichern", true);
        JTextField name = new JTextField(fc.getSelectedFile().getName().replaceFirst("(?i)\\.png$", ""));
        JPanel form = Ui.column();
        form.add(Ui.label("Die Vorschau links zeigt den Skin. Modell:", 13, Font.PLAIN, Theme.TEXT));
        form.add(classic);
        form.add(slim);
        form.add(Box.createVerticalStrut(8));
        form.add(keep);
        form.add(name);
        if (JOptionPane.showConfirmDialog(host.frame(), form, "Skin hochladen", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            refresh();
            return;
        }
        if (keep.isSelected()) {
            try {
                changer().save(name.getText(), png, slim.isSelected());
            } catch (IOException | IllegalArgumentException e) {
                host.error("Skin-Bibliothek", e.getMessage());
            }
        }
        upload(png, slim.isSelected());
    }

    private void upload(byte[] png, boolean slim) {
        if (png == null) {
            return;
        }
        withToken("Lade Skin zu Mojang hoch …", t -> changer().upload(t, png, slim), "Skin geändert. Im Spiel sichtbar nach dem nächsten Beitreten.");
        viewer.setSkin(png, slim);
    }

    @FunctionalInterface
    private interface TokenCall {
        dev.lumora.launcher.core.skin.SkinChanger.Profile run(String token) throws IOException;
    }

    /** Runs a profile change with a fresh access token (refreshed silently; never shown or logged). */
    private void withToken(String busy, TokenCall call, String done) {
        if (host.ctx().play.storedSession() == null) {
            host.signIn();
            return;
        }
        skinStatus.setText(busy);
        io.submit(() -> {
            try {
                var s = host.ctx().play.freshSession(host.ctx().config);
                if (s == null) {
                    SwingUtilities.invokeLater(() -> skinStatus.setText("Bitte einmal neu anmelden, um den Skin zu ändern."));
                    return;
                }
                var p = call.run(s.accessToken());
                SwingUtilities.invokeLater(() -> {
                    fillCapes(p);
                    fillLibrary();
                    skinStatus.setText(done);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    skinStatus.setText(" ");
                    host.error("Skin & Umhang", e.getMessage());
                });
            }
        });
    }

    public void refresh() {
        refreshSkinTools();
        security.refresh();
        historyTab.refresh();
        accounts.removeAll();
        AccountStore store = host.ctx().accounts;
        List<AccountStore.Account> list = store.list();
        Optional<AccountStore.Account> active = store.activeAccount();
        if (list.isEmpty()) {
            Card c = new Card(new BorderLayout(14, 0));
            c.padding(16, 16, 16, 16);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(new JLabel(Icons.of(Icons.Kind.USER, 26, Theme.ACCENT_HOVER)), BorderLayout.WEST);
            JPanel t = Ui.column();
            t.add(Ui.label("Noch kein Konto", 15, Font.BOLD, Theme.TEXT));
            t.add(Ui.wrapText("Melde dich mit deinem Microsoft-Konto an, auf dem Minecraft gekauft ist. Du kannst danach "
                    + "weitere Konten hinzufügen und hier zwischen ihnen wechseln.", 13f, Theme.MUTED));
            c.add(t, BorderLayout.CENTER);
            accounts.add(c);
        }
        for (AccountStore.Account a : list) {
            accounts.add(row(a, active.map(x -> x.uuid().equals(a.uuid())).orElse(false)));
            accounts.add(Box.createVerticalStrut(8));
        }
        accounts.revalidate();
        accounts.repaint();
        if (active.isPresent()) {
            showByUuid(active.get().uuid(), active.get().username(), "Aktives Konto");
        } else {
            showDefault();
        }
    }

    private JComponent row(AccountStore.Account a, boolean active) {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(12, 14, 12, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (active) {
            c.outline(Theme.ACCENT);
        }
        Avatar av = new Avatar(40);
        av.setPlayer(a.username(), a.uuid());
        c.add(av, BorderLayout.WEST);
        JPanel t = Ui.column();
        JPanel line = Ui.row(8);
        line.add(Ui.label(a.username(), 15, Font.BOLD, Theme.TEXT));
        if (active) {
            line.add(Ui.chip("Aktiv"));
        }
        t.add(line);
        JLabel sub = Ui.label("zuletzt verwendet " + PlayStats.ago(a.lastUsed(), Instant.now()), 12, Font.PLAIN, Theme.MUTED);
        sub.setToolTipText("UUID " + a.uuid());
        t.add(sub);
        c.add(t, BorderLayout.CENTER);
        JPanel buttons = Ui.row(6);
        JButton skin = new JButton(Icons.of(Icons.Kind.USER, 15, Theme.TEXT));
        skin.setToolTipText("Skin ansehen");
        skin.setFocusable(false);
        skin.addActionListener(e -> showByUuid(a.uuid(), a.username(), "Konto"));
        buttons.add(skin);
        if (!active) {
            JButton use = new JButton("Verwenden");
            use.setFocusable(false);
            use.addActionListener(e -> useAccount(a));
            buttons.add(use);
        }
        JButton remove = new JButton("Abmelden");
        remove.setFocusable(false);
        remove.addActionListener(e -> removeAccount(a));
        buttons.add(remove);
        JPanel bw = new JPanel(new GridBagLayout());
        bw.setOpaque(false);
        bw.add(buttons);
        c.add(bw, BorderLayout.EAST);
        return c;
    }

    private void showByName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        int gen = ++generation;
        skinTitle.setText(name.strip());
        skinHint.setText("Lade Skin…");
        io.submit(() -> {
            try {
                SkinService.Skin s = skins.byName(name.strip());
                SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        apply(s, "Spieler");
                    }
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        skinHint.setText(e.getMessage());
                    }
                });
            }
        });
    }

    private void showByUuid(String uuid, String name, String label) {
        int gen = ++generation;
        skinTitle.setText(name);
        skinHint.setText("Lade Skin…");
        io.submit(() -> {
            try {
                SkinService.Skin s = skins.byUuid(uuid);
                SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        apply(s, label);
                    }
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        skinHint.setText("Skin nicht verfügbar: " + e.getMessage());
                        showDefaultSkinOnly();
                    }
                });
            }
        });
    }

    private void apply(SkinService.Skin s, String label) {
        skinTitle.setText(s.name() != null ? s.name() : skinTitle.getText());
        byte[] png = s.png() != null ? s.png() : defaultSkin(s.slim());
        viewer.setSkin(png, s.slim());
        skinHint.setText(label + "  ·  " + (s.slim() ? "schmale Arme (Alex)" : "klassische Arme (Steve)")
                + (s.png() == null ? "  ·  Standard-Skin" : "") + "  ·  ziehen zum Drehen");
    }

    private void showDefault() {
        skinTitle.setText("Kein Konto");
        skinHint.setText("Standard-Skin  ·  ziehen zum Drehen");
        showDefaultSkinOnly();
    }

    private void showDefaultSkinOnly() {
        viewer.setSkin(defaultSkin(false), false);
    }

    /** Steve/Alex from the newest installed Minecraft version. */
    private byte[] defaultSkin(boolean slim) {
        for (var p : host.ctx().catalog.profiles()) {
            Path jar = host.ctx().paths.versions().resolve(p.minecraftVersion).resolve(p.minecraftVersion + ".jar");
            byte[] b = SkinService.defaultSkin(jar, slim);
            if (b != null) {
                return b;
            }
        }
        return null;
    }
}
