package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.PlayService;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.loader.LoaderManager;
import dev.lumora.launcher.core.loader.LoaderVersion;
import dev.lumora.launcher.core.loader.ModLoader;
import dev.lumora.launcher.core.profile.Flavor;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Mod-Loader": every loader on its own card for the selected Minecraft version. Each one is chosen, installed,
 * configured (version, own JVM arguments) and updated separately and starts with its own game folder; the card of the
 * current start type is highlighted. Version lists load in the background; installs run one at a time.
 */
public final class LoadersPage extends JPanel {
    private static final String RECOMMENDED = "Empfohlen · aktualisiert sich selbst";

    private final PageHost host;
    private final ExecutorService io;
    private final ExecutorService installs = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lumora-loader-install");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean installing = new AtomicBoolean();
    private final JPanel content = Ui.column();

    public LoadersPage(PageHost host, ExecutorService io) {
        super(new BorderLayout());
        this.host = host;
        this.io = io;
        setOpaque(false);
        add(Ui.scroll(content), BorderLayout.CENTER);
    }

    public void refresh() {
        content.removeAll();
        ProfileCatalog.Profile profile = host.ctx().selectedProfile();
        content.add(Ui.pageTitle("Mod-Loader", "Jeder Loader ist ein eigener Starttyp mit eigenem Spielordner, eigener Version "
                + "und eigenen Einstellungen. Lumora-Mods laufen nur im Starttyp „Lumora Client“."));
        content.add(Box.createVerticalStrut(16));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.add(Ui.label("Minecraft-Version", 13, Font.BOLD, Theme.MUTED));
        JComboBox<ProfileCatalog.Profile> versions = new JComboBox<>(host.ctx().catalog.profiles().toArray(new ProfileCatalog.Profile[0]));
        versions.setRenderer((list, value, index, selected, focus) -> {
            JLabel l = new JLabel(value == null ? "" : "Minecraft " + value.minecraftVersion);
            l.setOpaque(selected);
            l.setBackground(Theme.alpha(Theme.ACCENT, 90));
            l.setForeground(Theme.TEXT);
            l.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return l;
        });
        versions.setSelectedItem(profile);
        versions.addActionListener(e -> {
            if (versions.getSelectedItem() instanceof ProfileCatalog.Profile p && !p.id.equals(profile.id)) {
                host.selectProfile(p.id);
                refresh();
            }
        });
        versions.setPreferredSize(new Dimension(210, 34));
        bar.add(versions);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        content.add(bar);
        content.add(Box.createVerticalStrut(16));

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (ModLoader loader : host.ctx().loaders.loaders().all()) {
            grid.add(new LoaderCard(loader, profile).card);
        }
        content.add(grid);
        content.add(Box.createVerticalStrut(14));
        JComponent note = Ui.wrapText("Downloads kommen nur von den offiziellen Servern der Projekte (meta.fabricmc.net, meta.quiltmc.org, "
                + "maven.minecraftforge.net, maven.neoforged.net) und werden mit den dort veröffentlichten Prüfsummen geprüft. "
                + "Entfernen löscht nur die Loader-Dateien; Welten und Mods im Spielordner bleiben.", 12, Theme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(note);
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    /** Recognition colour per loader (the tile with its initial). */
    private static java.awt.Color tint(String id) {
        return switch (id) {
            case "fabric" -> new java.awt.Color(0xC9A86A);
            case "quilt" -> new java.awt.Color(0x9B6CF0);
            case "forge" -> new java.awt.Color(0x5B7FB8);
            case "neoforge" -> new java.awt.Color(0xE8893A);
            default -> Theme.ACCENT;
        };
    }

    /** One loader for one Minecraft version. */
    private final class LoaderCard {
        final Card card = new Card(new BorderLayout(0, 10));
        private final ModLoader loader;
        private final ProfileCatalog.Profile profile;
        private final Flavor flavor;
        private final String mc;
        private final JLabel status = Ui.label("…", 12, Font.BOLD, Theme.MUTED);
        private final JComboBox<String> version = new JComboBox<>();
        private final JCheckBox betas = new JCheckBox("Beta-Versionen");
        private final JButton install = new JButton("Installieren", Icons.of(Icons.Kind.PLUS, 14, Theme.TEXT));
        private final JButton use = new JButton("Als Starttyp", Icons.of(Icons.Kind.PLAY, 13, Theme.TEXT));
        private final JButton remove = new JButton(Icons.of(Icons.Kind.TRASH, 14, Theme.TEXT));
        private final JButton folder = new JButton(Icons.of(Icons.Kind.FOLDER, 14, Theme.TEXT));
        private final JProgressBar progress = new JProgressBar(0, 1000);
        private final JLabel progressText = Ui.label(" ", 11.5f, Font.PLAIN, Theme.MUTED);
        private List<LoaderVersion> list = List.of();
        private boolean filling;

        LoaderCard(ModLoader loader, ProfileCatalog.Profile profile) {
            this.loader = loader;
            this.profile = profile;
            this.flavor = Flavor.ofLoader(loader.id());
            this.mc = profile.minecraftVersion;
            boolean current = flavor == Flavor.of(host.ctx().config.selectedFlavor);
            if (current) {
                card.outline(Theme.ACCENT);
            }
            card.padding(18, 20, 16, 20);

            JPanel head = new JPanel(new BorderLayout(12, 0));
            head.setOpaque(false);
            head.add(Ui.letterTile(loader.displayName(), 42, tint(loader.id())), BorderLayout.WEST);
            JPanel titles = Ui.column();
            JLabel name = Ui.label(loader.displayName() + (current ? "  ·  aktiver Starttyp" : ""), 17, Font.BOLD, Theme.TEXT);
            titles.add(name);
            titles.add(Box.createVerticalStrut(3));
            titles.add(status);
            head.add(titles, BorderLayout.CENTER);
            card.add(head, BorderLayout.NORTH);

            JPanel body = Ui.column();
            JComponent desc = Ui.wrapText(loader.description(), 12.5f, Theme.MUTED);
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(desc);
            body.add(Box.createVerticalStrut(10));

            JPanel vrow = new JPanel(new BorderLayout(8, 0));
            vrow.setOpaque(false);
            vrow.setAlignmentX(Component.LEFT_ALIGNMENT);
            vrow.add(Ui.label("Version", 12, Font.BOLD, Theme.MUTED), BorderLayout.WEST);
            version.addItem("Lade Versionen …");
            version.setEnabled(false);
            version.addActionListener(e -> pin());
            vrow.add(version, BorderLayout.CENTER);
            betas.setOpaque(false);
            betas.setFocusable(false);
            betas.addActionListener(e -> fill());
            vrow.add(betas, BorderLayout.EAST);
            vrow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            body.add(vrow);
            body.add(Box.createVerticalStrut(8));

            JPanel jrow = new JPanel(new BorderLayout(8, 0));
            jrow.setOpaque(false);
            jrow.setAlignmentX(Component.LEFT_ALIGNMENT);
            jrow.add(Ui.label("JVM", 12, Font.BOLD, Theme.MUTED), BorderLayout.WEST);
            String args = host.ctx().config.loaderJvmArgs.getOrDefault(loader.id(), "");
            JTextField jvm = new JTextField(args);
            jvm.putClientProperty("JTextField.placeholderText", "Zusätzliche JVM-Argumente nur für " + loader.displayName());
            jvm.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    saveJvm(jvm.getText());
                }
            });
            jvm.addActionListener(e -> saveJvm(jvm.getText()));
            jrow.add(jvm, BorderLayout.CENTER);
            jrow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            body.add(jrow);
            body.add(Box.createVerticalStrut(10));

            progress.setVisible(false);
            progress.setAlignmentX(Component.LEFT_ALIGNMENT);
            progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            body.add(progress);
            progressText.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(progressText);
            card.add(body, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            for (JButton b : List.of(install, use, folder, remove)) {
                b.setFocusable(false);
                actions.add(b);
            }
            install.addActionListener(e -> runInstall(false));
            use.addActionListener(e -> {
                host.selectFlavor(flavor.id());
                refresh();
            });
            use.setEnabled(!current);
            folder.setToolTipText("Spielordner von " + loader.displayName() + " (Mods, Welten) öffnen");
            folder.addActionListener(e -> host.openFolder(host.ctx().paths.instance(flavor.instanceId(profile.id))));
            remove.setToolTipText("Installierte Version entfernen");
            remove.addActionListener(e -> removeMenu());
            if (loader.supportUrl() != null) {
                JLabel support = Ui.label(loader.id().equals("forge") ? "Forge unterstützen" : "Website", 12, Font.BOLD, Theme.ACCENT_HOVER);
                support.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                support.setToolTipText(loader.id().equals("forge")
                        ? "Forge bittet Launcher, die es automatisch installieren, auf seine Unterstützungsseite zu verweisen"
                        : loader.supportUrl().toString());
                support.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
                support.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        host.browse(loader.supportUrl().toString());
                    }
                });
                actions.add(support);
            }
            card.add(actions, BorderLayout.SOUTH);

            if (!loader.supports(mc)) {
                status.setText("Nicht verfügbar für Minecraft " + mc);
                version.removeAllItems();
                version.addItem("—");
                for (JComponent c : List.of(install, use, remove, betas, jvm)) {
                    c.setEnabled(false);
                }
                return;
            }
            updateStatus();
            io.submit(this::load);
        }

        private void load() {
            try {
                List<LoaderVersion> l = host.ctx().loaders.versions(loader.id(), mc);
                SwingUtilities.invokeLater(() -> {
                    list = l;
                    fill();
                    updateStatus();
                });
            } catch (Exception e) {
                Log.warn("Loader versions " + loader.id() + " " + mc + ": " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    version.removeAllItems();
                    version.addItem("Versionsliste nicht erreichbar");
                    status.setText(status.getText() + "  ·  offline");
                });
            }
        }

        /** Fills the version box: "recommended" first, then the versions (stable only unless betas are shown). */
        private void fill() {
            filling = true;
            try {
                version.removeAllItems();
                if (list.isEmpty()) {
                    version.addItem("Keine Version für Minecraft " + mc);
                    version.setEnabled(false);
                    install.setEnabled(false);
                    return;
                }
                String tested = loader.id().equals("fabric") ? profile.fabricLoaderVersion : null;
                Optional<LoaderVersion> rec = list.stream().filter(LoaderVersion::recommended).findFirst();
                String recLabel = tested != null ? tested + " (von Lumora getestet)" : rec.map(LoaderVersion::label).orElse("");
                version.addItem(RECOMMENDED + " (" + recLabel + ")");
                String pin = LoaderManager.pinned(host.ctx().config, loader.id(), mc);
                boolean pinListed = false;
                for (LoaderVersion v : list) {
                    if (v.stable() || betas.isSelected() || v.version().equals(pin)) {
                        version.addItem(item(v));
                        pinListed |= v.version().equals(pin);
                    }
                }
                if (pin != null && !pinListed) {
                    version.addItem(pin);
                }
                version.setEnabled(true);
                if (pin != null) {
                    for (int i = 1; i < version.getItemCount(); i++) {
                        if (versionOf(version.getItemAt(i)).equals(pin)) {
                            version.setSelectedIndex(i);
                        }
                    }
                } else {
                    version.setSelectedIndex(0);
                }
            } finally {
                filling = false;
            }
        }

        private String item(LoaderVersion v) {
            return v.label() + (v.recommended() ? "  ★" : "") + (v.stable() ? "" : "  (Beta)") + "  ​" + v.version();
        }

        /** The real version id behind a box entry (after the zero-width marker). */
        private String versionOf(String item) {
            int i = item.lastIndexOf('​');
            return i >= 0 ? item.substring(i + 1) : item.strip();
        }

        private void pin() {
            if (filling || version.getSelectedIndex() < 0) {
                return;
            }
            String key = LoaderManager.key(loader.id(), mc);
            if (version.getSelectedIndex() == 0) {
                host.ctx().config.loaderVersions.remove(key);
            } else {
                host.ctx().config.loaderVersions.put(key, versionOf((String) version.getSelectedItem()));
            }
            host.saveConfig();
            updateStatus();
        }

        private void saveJvm(String text) {
            String clean = text == null ? "" : text.strip().replaceAll("[\\r\\n]", " ");
            String old = host.ctx().config.loaderJvmArgs.getOrDefault(loader.id(), "");
            if (clean.equals(old)) {
                return;
            }
            if (clean.isEmpty()) {
                host.ctx().config.loaderJvmArgs.remove(loader.id());
            } else {
                host.ctx().config.loaderJvmArgs.put(loader.id(), clean);
            }
            host.saveConfig();
        }

        private void updateStatus() {
            LoaderManager m = host.ctx().loaders;
            List<LoaderManager.Installed> have = m.installed(loader.id(), mc);
            String selected = selectedVersion();
            boolean selectedInstalled = selected != null && have.stream().anyMatch(i -> i.version().equals(selected));
            if (have.isEmpty()) {
                status.setText("Nicht installiert");
                status.setForeground(Theme.MUTED);
                install.setText("Installieren");
            } else if (selectedInstalled) {
                status.setText("Installiert: " + label(selected));
                status.setForeground(Theme.OK);
                install.setText("Reparieren");
            } else {
                status.setText("Installiert: " + label(have.getFirst().version()) + (selected == null ? "" : "  ·  Update auf " + label(selected)));
                status.setForeground(Theme.WARN);
                install.setText(selected == null ? "Installieren" : "Aktualisieren");
            }
            install.setIcon(Icons.of(selectedInstalled ? Icons.Kind.CHECK : Icons.Kind.PLUS, 14, Theme.TEXT));
            remove.setEnabled(!have.isEmpty());
        }

        /** The version a start would use now: pinned, tested (Fabric) or recommended. */
        private String selectedVersion() {
            String pin = LoaderManager.pinned(host.ctx().config, loader.id(), mc);
            if (pin != null) {
                return pin;
            }
            if (loader.id().equals("fabric")) {
                return profile.fabricLoaderVersion;
            }
            return list.stream().filter(LoaderVersion::recommended).map(LoaderVersion::version).findFirst().orElse(null);
        }

        private String label(String v) {
            return list.stream().filter(l -> l.version().equals(v)).map(LoaderVersion::label).findFirst().orElse(v);
        }

        private void runInstall(boolean repairOnly) {
            if (host.gameRunning() || host.ctx().play.state() == PlayService.State.INSTALLING) {
                host.error("Gerade nicht möglich", "Während ein Spiel läuft oder installiert wird, kann kein Loader installiert werden.");
                return;
            }
            if (!installing.compareAndSet(false, true)) {
                host.error("Bitte warten", "Es wird bereits ein Loader installiert.");
                return;
            }
            boolean repair = repairOnly || "Reparieren".equals(install.getText());
            install.setEnabled(false);
            progress.setVisible(true);
            progress.setIndeterminate(true);
            card.revalidate();
            installs.submit(() -> {
                try {
                    host.ctx().installer.prepare(profile, flavor, host.ctx().config, repair, new ProgressListener() {
                        @Override
                        public void stage(String description) {
                            SwingUtilities.invokeLater(() -> progressText.setText(description));
                        }

                        @Override
                        public void progress(double fraction, String detail) {
                            SwingUtilities.invokeLater(() -> {
                                progress.setIndeterminate(fraction < 0);
                                if (fraction >= 0) {
                                    progress.setValue((int) (fraction * 1000));
                                }
                            });
                        }
                    });
                    SwingUtilities.invokeLater(() -> {
                        progressText.setText(loader.displayName() + " ist bereit.");
                        progressText.setForeground(Theme.OK);
                    });
                } catch (Exception e) {
                    Log.warn("Loader install " + loader.id() + " failed: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        progressText.setText(" ");
                        host.error(loader.displayName() + " konnte nicht installiert werden", String.valueOf(e.getMessage()));
                    });
                } finally {
                    installing.set(false);
                    SwingUtilities.invokeLater(() -> {
                        progress.setVisible(false);
                        install.setEnabled(true);
                        updateStatus();
                        card.revalidate();
                    });
                }
            });
        }

        private void removeMenu() {
            List<LoaderManager.Installed> have = host.ctx().loaders.installed(loader.id(), mc);
            if (have.isEmpty()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            for (LoaderManager.Installed i : new ArrayList<>(have)) {
                JMenuItem mi = new JMenuItem(loader.displayName() + " " + label(i.version()) + " entfernen", Icons.of(Icons.Kind.TRASH, 13, Theme.TEXT));
                mi.addActionListener(e -> {
                    int ok = JOptionPane.showConfirmDialog(host.frame(), loader.displayName() + " " + label(i.version()) + " für Minecraft " + mc
                                    + " entfernen?\nWelten und Mods im Spielordner bleiben erhalten.", "Loader entfernen",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (ok != JOptionPane.OK_OPTION) {
                        return;
                    }
                    try {
                        host.ctx().loaders.uninstall(loader.id(), mc, i.version());
                    } catch (Exception ex) {
                        host.error("Entfernen fehlgeschlagen", String.valueOf(ex.getMessage()));
                    }
                    updateStatus();
                });
                menu.add(mi);
            }
            menu.show(remove, 0, remove.getHeight());
        }
    }
}
