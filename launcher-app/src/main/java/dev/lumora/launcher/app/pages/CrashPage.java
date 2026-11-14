package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.crash.CrashReport;
import dev.lumora.launcher.core.crash.CrashReports;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.diagnostics.SystemCheck;
import dev.lumora.launcher.core.mods.ModInventory;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.stats.PlayStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * "Abstürze" page: crash reports of all versions, newest first, each with a plain-language summary, the most
 * likely culprit mod (with a one-click "disable" for optional and own mods), known-problem hints and tools for
 * getting help: open the report, copy an anonymized version, save a diagnostics zip, start again.
 */
public final class CrashPage extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)
            .withZone(ZoneId.systemDefault());
    private static final String ALL = "*";

    private final PageHost host;
    private final ExecutorService io;
    private final JPanel filterBar = Ui.row(0);
    private final JLabel summary = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private final JPanel list = Ui.column();
    private String filter = ALL;
    private int generation;

    public CrashPage(PageHost host, ExecutorService io) {
        super(new BorderLayout(0, 14));
        this.host = host;
        this.io = io;
        setOpaque(false);
        JPanel head = Ui.column();
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(Ui.pageTitle("Abstürze", "Crash-Reports aller Versionen – mit wahrscheinlicher Ursache, Hilfe und Diagnosepaket."), BorderLayout.WEST);
        JPanel actions = Ui.row(8);
        actions.add(button("Aktualisieren", Icons.Kind.REFRESH, this::refresh));
        JPanel aw = new JPanel(new BorderLayout());
        aw.setOpaque(false);
        aw.add(actions, BorderLayout.SOUTH);
        titleRow.add(aw, BorderLayout.EAST);
        head.add(titleRow);
        head.add(Box.createVerticalStrut(14));
        head.add(filterBar);
        head.add(Box.createVerticalStrut(10));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(summary);
        add(head, BorderLayout.NORTH);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(Ui.scroll(list), BorderLayout.CENTER);
    }

    public void refresh() {
        rebuildFilter();
        int gen = ++generation;
        List<String> ids = host.ctx().catalog.profiles().stream().map(p -> p.id).filter(id -> filter.equals(ALL) || id.equals(filter)).toList();
        summary.setText("Suche Crash-Reports…");
        io.submit(() -> {
            List<CrashReport> found = CrashReports.list(host.ctx()::gameDir, ids);
            SwingUtilities.invokeLater(() -> {
                if (gen == generation) {
                    render(found);
                }
            });
        });
    }

    private void rebuildFilter() {
        filterBar.removeAll();
        List<String> ids = new ArrayList<>(List.of(ALL));
        List<String> labels = new ArrayList<>(List.of("Alle Versionen"));
        for (ProfileCatalog.Profile p : host.ctx().catalog.profiles()) {
            ids.add(p.id);
            labels.add("Minecraft " + p.minecraftVersion);
        }
        filterBar.add(Ui.segmented(ids, labels, filter, id -> {
            filter = id;
            refresh();
        }));
        filterBar.revalidate();
        filterBar.repaint();
    }

    private void render(List<CrashReport> reports) {
        list.removeAll();
        Instant now = Instant.now();
        long week = reports.stream().filter(r -> r.time().isAfter(now.minusSeconds(7 * 86400))).count();
        summary.setText(reports.isEmpty() ? " " : reports.size() + (reports.size() == 1 ? " Absturz" : " Abstürze")
                + "  ·  " + week + " in den letzten 7 Tagen" + "  ·  zuletzt " + PlayStats.ago(reports.getFirst().time(), now));
        if (reports.isEmpty()) {
            Card c = new Card(new BorderLayout(14, 0));
            c.padding(18, 18, 18, 18);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(new JLabel(Icons.of(Icons.Kind.CHECK, 26, Theme.OK)), BorderLayout.WEST);
            JPanel t = Ui.column();
            t.add(Ui.label("Keine Abstürze – alles stabil", 16, Font.BOLD, Theme.TEXT));
            t.add(Box.createVerticalStrut(3));
            t.add(Ui.wrapText("Stürzt Minecraft ab, erscheint der Bericht hier automatisch: mit der wahrscheinlich verantwortlichen Mod, "
                    + "Lösungshinweisen und einem anonymisierten Diagnosepaket für den Support.", 13f, Theme.MUTED));
            c.add(t, BorderLayout.CENTER);
            list.add(c);
        }
        for (int i = 0; i < reports.size(); i++) {
            if (i > 0) {
                list.add(Box.createVerticalStrut(10));
            }
            list.add(card(reports.get(i), i == 0));
        }
        list.revalidate();
        list.repaint();
    }

    private JComponent card(CrashReport r, boolean expanded) {
        Card c = new Card(new BorderLayout(14, 0));
        c.padding(14, 16, 14, 16);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel iconWrap = new JPanel(new BorderLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(new JLabel(Icons.of(Icons.Kind.ALERT, 26, Theme.FAIL)), BorderLayout.NORTH);
        c.add(iconWrap, BorderLayout.WEST);

        JPanel body = Ui.column();
        JPanel head = Ui.row(8);
        head.add(Ui.label(r.description(), 15.5f, Font.BOLD, Theme.TEXT));
        head.add(Ui.chip("Minecraft " + host.ctx().catalog.profile(r.profileId()).map(p -> p.minecraftVersion).orElse(r.profileId())));
        head.add(Ui.chip(r.source() == CrashReport.Source.MINECRAFT ? "Crash-Report" : "Launcher-Protokoll", Theme.MUTED, Theme.MUTED));
        if (r.exitCode() != null) {
            head.add(Ui.chip("Code " + r.exitCode(), Theme.FAIL, new Color(0xFF9EA1)));
        }
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        body.add(head);
        body.add(Box.createVerticalStrut(3));
        body.add(Ui.label(DATE.format(r.time()) + "  ·  " + PlayStats.ago(r.time(), Instant.now()) + "  ·  " + r.file().getFileName(),
                12, Font.PLAIN, new Color(0x7D8794)));
        if (r.exception() != null) {
            body.add(Box.createVerticalStrut(8));
            JTextArea ex = Ui.wrapText(r.exception(), 12.5f, new Color(0xFFB4B6));
            ex.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            body.add(ex);
        }
        // culprit
        body.add(Box.createVerticalStrut(10));
        if (r.suspects().isEmpty()) {
            body.add(Ui.label("Keine eindeutige Ursache in den Mods gefunden – eher Minecraft, Treiber oder Einstellungen.", 13, Font.PLAIN, Theme.MUTED));
        } else {
            for (CrashReport.Suspect s : r.suspects()) {
                body.add(suspectRow(r, s));
                body.add(Box.createVerticalStrut(6));
            }
        }
        for (CrashAnalyzer.Hint h : r.hints()) {
            body.add(Box.createVerticalStrut(4));
            JTextArea t = Ui.wrapText("Tipp – " + h.title() + ": " + h.advice(), 13f, Theme.WARN);
            body.add(t);
        }
        // details
        JTextArea details = new JTextArea();
        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setBackground(Theme.BG);
        details.setForeground(Theme.MUTED);
        details.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailScroll.setPreferredSize(new Dimension(10, 260));
        detailScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        detailScroll.setVisible(false);

        body.add(Box.createVerticalStrut(12));
        JPanel buttons = Ui.row(8);
        JButton toggle = button(expanded ? "Bericht ausblenden" : "Bericht anzeigen", Icons.Kind.TERMINAL, null);
        toggle.addActionListener(e -> {
            boolean show = !detailScroll.isVisible();
            if (show && details.getText().isEmpty()) {
                loadDetails(r, details);
            }
            detailScroll.setVisible(show);
            toggle.setText(show ? "Bericht ausblenden" : "Bericht anzeigen");
            body.revalidate();
        });
        buttons.add(toggle);
        buttons.add(button("Für Support kopieren", Icons.Kind.COPY, () -> copy(r)));
        buttons.add(button("Diagnosepaket…", Icons.Kind.ARCHIVE, () -> saveZip(r)));
        buttons.add(button("Datei öffnen", Icons.Kind.FOLDER, () -> openFile(r.file())));
        buttons.add(button("Erneut starten", Icons.Kind.PLAY, () -> {
            host.selectProfile(r.profileId());
            host.launch(null, false);
        }));
        body.add(buttons);
        body.add(Box.createVerticalStrut(8));
        body.add(detailScroll);
        if (expanded) {
            loadDetails(r, details);
            detailScroll.setVisible(true);
        }
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    private JComponent suspectRow(CrashReport r, CrashReport.Suspect s) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Kind kind = kindOf(r.profileId(), s);
        JPanel text = Ui.column();
        JPanel line = Ui.row(8);
        line.add(Ui.label("Wahrscheinliche Ursache:", 13, Font.PLAIN, Theme.MUTED));
        line.add(Ui.label(s.name(), 13.5f, Font.BOLD, Theme.TEXT));
        line.add(Ui.chip(switch (kind.type) {
            case CORE, DEPENDENCY, RESOURCE_PACK -> "Kern-Mod";
            case EXTENSION -> "Erweiterung";
            case USER -> "eigene Mod";
        }, Theme.WARN, Theme.WARN));
        text.add(line);
        text.add(Ui.label(s.reason(), 12, Font.PLAIN, new Color(0x7D8794)));
        row.add(text, BorderLayout.CENTER);
        JButton action = switch (kind.type) {
            case USER -> button("Mod deaktivieren", Icons.Kind.LOCK, () -> disableUserMod(s));
            case EXTENSION -> button("Erweiterung ausschalten", Icons.Kind.LOCK, () -> disableExtension(kind.key));
            case CORE, DEPENDENCY, RESOURCE_PACK -> button("Reparieren & starten", Icons.Kind.REFRESH, () -> {
                host.selectProfile(r.profileId());
                host.launch(null, true);
            });
        };
        JPanel aw = new JPanel(new java.awt.GridBagLayout());
        aw.setOpaque(false);
        aw.add(action);
        row.add(aw, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private record Kind(ModInventory.Kind type, String key) {
    }

    /** Core / extension (by the lockfile's file names) or the player's own mod. */
    private Kind kindOf(String profileId, CrashReport.Suspect s) {
        Optional<ProfileCatalog.Profile> p = host.ctx().catalog.profile(profileId);
        if (p.isPresent() && s.file() != null) {
            String name = s.file().getFileName().toString();
            for (ProfileCatalog.Artifact a : host.ctx().catalog.profileArtifacts(p.get())) {
                if (a.fileName.equals(name)) {
                    return new Kind(a.optional && !a.dependencyOnly ? ModInventory.Kind.EXTENSION : ModInventory.Kind.CORE, a.key());
                }
            }
        }
        return new Kind(ModInventory.Kind.USER, null);
    }

    private void disableUserMod(CrashReport.Suspect s) {
        if (s.file() == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(host.frame(), s.name() + " wird deaktiviert (die Datei wird in „"
                        + s.file().getFileName() + ".disabled“ umbenannt, nichts wird gelöscht). Fortfahren?",
                "Mod deaktivieren", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        io.submit(() -> {
            try {
                ModInventory.setUserModEnabled(s.file(), false);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(host.frame(), s.name() + " ist deaktiviert. Unter „Mods“ kannst du sie wieder einschalten.",
                            "Mod deaktiviert", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error("Mod konnte nicht deaktiviert werden", e.getMessage() + "\nLäuft Minecraft noch?"));
            }
        });
    }

    private void disableExtension(String key) {
        host.ctx().config.enabledExtensions.remove(key);
        host.saveConfig();
        host.extensionsChanged();
        JOptionPane.showMessageDialog(host.frame(), "Die Erweiterung ist ausgeschaltet (für alle Versionen). Unter „Mods“ kannst du sie wieder einschalten.",
                "Erweiterung ausgeschaltet", JOptionPane.INFORMATION_MESSAGE);
        refresh();
    }

    private void loadDetails(CrashReport r, JTextArea target) {
        target.setText("Lade…");
        io.submit(() -> {
            String text;
            try {
                text = CrashReports.shareText(r);
            } catch (IOException e) {
                text = e.getMessage();
            }
            String t = text;
            SwingUtilities.invokeLater(() -> {
                target.setText(t);
                target.setCaretPosition(0);
            });
        });
    }

    private void copy(CrashReport r) {
        io.submit(() -> {
            try {
                String text = CrashReports.shareText(r);
                SwingUtilities.invokeLater(() -> {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                    JOptionPane.showMessageDialog(host.frame(), "Der Bericht ist in der Zwischenablage – ohne Tokens, Benutzerordner "
                            + "und Windows-Benutzernamen. Du kannst ihn jetzt z. B. in einen Support-Chat einfügen.",
                            "Kopiert", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error("Kopieren fehlgeschlagen", e.getMessage()));
            }
        });
    }

    private void saveZip(CrashReport r) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(System.getProperty("user.home"), "lumora-diagnose-" + r.profileId() + ".zip"));
        if (fc.showSaveDialog(host.frame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = fc.getSelectedFile().toPath();
        io.submit(() -> {
            try {
                Map<String, String> extra = new LinkedHashMap<>();
                host.ctx().catalog.profile(r.profileId()).ifPresent(p -> {
                    StringBuilder mods = new StringBuilder();
                    for (ModInventory.Entry e : ModInventory.scan(host.ctx().catalog, p, host.ctx().config.enabledExtensions,
                            host.ctx().gameDir(p.id).resolve("mods"), null)) {
                        mods.append(e.enabled() ? "[x] " : "[ ] ").append(e.displayName()).append(' ').append(e.version())
                                .append("  (").append(e.kind()).append(")\n");
                    }
                    extra.put("Mods", mods.toString());
                });
                extra.put("Systemprüfung", SystemCheck.run(host.ctx().os, host.ctx().paths).render());
                CrashReports.writeDiagnosticsZip(host.ctx().paths, host.ctx().gameDir(r.profileId()), r, extra, target);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(host.frame(), "Gespeichert: " + target
                                + "\n\nEnthält Crash-Report, Spiel- und Launcher-Log, Modliste und Systemprüfung – anonymisiert.",
                        "Diagnosepaket", JOptionPane.INFORMATION_MESSAGE));
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> host.error("Diagnosepaket fehlgeschlagen", e.getMessage()));
            }
        });
    }

    private void openFile(Path file) {
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            host.openFolder(file.getParent());
        }
    }

    private static JButton button(String text, Icons.Kind icon, Runnable action) {
        JButton b = new JButton(text, Icons.of(icon, 15, Theme.TEXT));
        b.setFocusable(false);
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        return b;
    }
}
