package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.crash.CrashDiagnosis;
import dev.lumora.launcher.core.crash.CrashReport;
import dev.lumora.launcher.core.crash.CrashReports;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.diagnostics.SystemCheck;
import dev.lumora.launcher.core.i18n.Lang;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * "Abstürze" page: crash reports of all versions, newest first, each with a plain-language summary, the most
 * likely culprit mod (with a one-click "disable" for optional and own mods), known-problem hints and tools for
 * getting help: open the report, copy an anonymized version, save a diagnostics zip, start again.
 */
public final class CrashPage extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(Lang.t("format.date_time"), Lang.locale())
            .withZone(ZoneId.systemDefault());
    private static final String ALL = "*";

    private final PageHost host;
    private final ExecutorService io;
    private final JPanel filterBar = Ui.row(8);
    private final JLabel summary = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private final JPanel list = Ui.column();
    private String filter = ALL;
    /** The crashes shown now (newest first): "Analyze" in the header looks at the first one. */
    private List<CrashReport> shown = List.of();
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
        titleRow.add(Ui.pageTitle(Lang.t("nav.crashes"), Lang.t("crash.subtitle")), BorderLayout.WEST);
        JPanel actions = Ui.row(8);
        actions.add(filterBar);
        actions.add(button(Lang.t("crash.analyze"), Icons.Kind.PULSE, () -> analyze(shown.isEmpty() ? null : shown.getFirst())));
        actions.add(button(Lang.t("common.refresh"), Icons.Kind.REFRESH, this::refresh));
        JPanel aw = new JPanel(new BorderLayout());
        aw.setOpaque(false);
        aw.add(actions, BorderLayout.SOUTH);
        titleRow.add(aw, BorderLayout.EAST);
        head.add(titleRow);
        head.add(Box.createVerticalStrut(14));
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
        summary.setText(Lang.t("crash.searching"));
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
        List<String> labels = new ArrayList<>(List.of(Lang.t("crash.all_versions")));
        for (ProfileCatalog.Profile p : host.ctx().catalog.profiles()) {
            ids.add(p.id);
            labels.add("Minecraft " + p.minecraftVersion);
        }
        filterBar.add(Ui.label(Lang.t("common.version"), 13, Font.PLAIN, Theme.MUTED));
        filterBar.add(Ui.choice(ids, labels, filter, id -> {
            filter = id;
            refresh();
        }));
        filterBar.revalidate();
        filterBar.repaint();
    }

    private void render(List<CrashReport> reports) {
        shown = List.copyOf(reports);
        list.removeAll();
        Instant now = Instant.now();
        long week = reports.stream().filter(r -> r.time().isAfter(now.minusSeconds(7 * 86400))).count();
        summary.setText(reports.isEmpty() ? " " : Lang.plural("crash.summary.count", reports.size())
                + "  ·  " + Lang.t("crash.summary.recent", week, PlayStats.ago(reports.getFirst().time(), now)));
        if (reports.isEmpty()) {
            Card c = new Card(new BorderLayout(14, 0));
            c.padding(18, 18, 18, 18);
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.add(new JLabel(Icons.of(Icons.Kind.CHECK, 26, Theme.OK)), BorderLayout.WEST);
            JPanel t = Ui.column();
            t.add(Ui.label(Lang.t("crash.none"), 16, Font.BOLD, Theme.TEXT));
            t.add(Box.createVerticalStrut(3));
            t.add(Ui.wrapText(Lang.t("crash.none.text"), 13f, Theme.MUTED));
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
        head.add(Ui.chip(r.source() == CrashReport.Source.MINECRAFT ? Lang.t("crash.chip.report") : Lang.t("crash.chip.launcher_log"), Theme.MUTED, Theme.MUTED));
        if (r.exitCode() != null) {
            head.add(Ui.chip(Lang.t("crash.chip.code", r.exitCode()), Theme.FAIL, new Color(0xFF9EA1)));
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
            body.add(Ui.label(Lang.t("crash.no_culprit"), 13, Font.PLAIN, Theme.MUTED));
        } else {
            for (CrashReport.Suspect s : r.suspects()) {
                body.add(suspectRow(r, s));
                body.add(Box.createVerticalStrut(6));
            }
        }
        for (CrashAnalyzer.Hint h : r.hints()) {
            body.add(Box.createVerticalStrut(4));
            JTextArea t = Ui.wrapText(Lang.t("crash.tip", h.title(), h.advice()), 13f, Theme.WARN);
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
        buttons.add(button(Lang.t("crash.analyze"), Icons.Kind.PULSE, () -> analyze(r)));
        JButton toggle = button(expanded ? Lang.t("crash.report.hide") : Lang.t("crash.report.show"), Icons.Kind.TERMINAL, null);
        toggle.addActionListener(e -> {
            boolean show = !detailScroll.isVisible();
            if (show && details.getText().isEmpty()) {
                loadDetails(r, details);
            }
            detailScroll.setVisible(show);
            toggle.setText(show ? Lang.t("crash.report.hide") : Lang.t("crash.report.show"));
            body.revalidate();
        });
        buttons.add(toggle);
        buttons.add(button(Lang.t("crash.copy_support"), Icons.Kind.COPY, () -> copy(r)));
        buttons.add(button(Lang.t("crash.zip.button"), Icons.Kind.ARCHIVE, () -> saveZip(r)));
        buttons.add(button(Lang.t("crash.open_file"), Icons.Kind.FOLDER, () -> openFile(r.file())));
        buttons.add(button(Lang.t("crash.dialog.restart"), Icons.Kind.PLAY, () -> {
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
        line.add(Ui.label(Lang.t("crash.likely_cause"), 13, Font.PLAIN, Theme.MUTED));
        line.add(Ui.label(s.name(), 13.5f, Font.BOLD, Theme.TEXT));
        line.add(Ui.chip(switch (kind.type) {
            case CORE, DEPENDENCY, RESOURCE_PACK -> Lang.t("crash.kind.core");
            case EXTENSION -> Lang.t("crash.kind.extension");
            case USER -> Lang.t("crash.kind.user");
        }, Theme.WARN, Theme.WARN));
        text.add(line);
        text.add(Ui.label(s.reason(), 12, Font.PLAIN, new Color(0x7D8794)));
        row.add(text, BorderLayout.CENTER);
        JButton action = switch (kind.type) {
            case USER -> button(Lang.t("crash.disable_mod"), Icons.Kind.LOCK, () -> disableUserMod(s));
            case EXTENSION -> button(Lang.t("crash.disable_extension"), Icons.Kind.LOCK, () -> disableExtension(kind.key));
            case CORE, DEPENDENCY, RESOURCE_PACK -> button(Lang.t("crash.repair_start"), Icons.Kind.REFRESH, () -> {
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
        if (JOptionPane.showConfirmDialog(host.frame(), Lang.t("crash.disable.confirm", s.name(),
                        s.file().getFileName() + ".disabled"),
                Lang.t("crash.disable_mod"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        io.submit(() -> {
            try {
                ModInventory.setUserModEnabled(s.file(), false);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(host.frame(), Lang.t("crash.disable.done", s.name()),
                            Lang.t("crash.disabled"), JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("crash.disable.failed"), e.getMessage() + "\n" + Lang.t("crash.disable.still_running")));
            }
        });
    }

    private void disableExtension(String key) {
        host.ctx().config.enabledExtensions.remove(key);
        host.saveConfig();
        host.extensionsChanged();
        JOptionPane.showMessageDialog(host.frame(), Lang.t("crash.extension_off.done"),
                Lang.t("crash.extension_off"), JOptionPane.INFORMATION_MESSAGE);
        refresh();
    }

    private void loadDetails(CrashReport r, JTextArea target) {
        target.setText(Lang.t("common.loading"));
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
                    JOptionPane.showMessageDialog(host.frame(), Lang.t("crash.copied.text"),
                            Lang.t("crash.copied"), JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("crash.copy.failed"), e.getMessage()));
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
                extra.put(Lang.t("diag.system_check"), SystemCheck.run(host.ctx().os, host.ctx().paths).render());
                CrashReports.writeDiagnosticsZip(host.ctx().paths, host.ctx().gameDir(r.profileId()), r, extra, target);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(host.frame(), Lang.t("crash.zip.saved", target),
                        Lang.t("crash.zip"), JOptionPane.INFORMATION_MESSAGE));
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> host.error(Lang.t("crash.zip.failed"), e.getMessage()));
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

    /** "Analyze": what happened, why, what helps, and a button for the fix the launcher can do itself. */
    private void analyze(CrashReport r) {
        if (r == null) {
            JOptionPane.showMessageDialog(host.frame(), Lang.t("crash.analysis.none"), Lang.t("crash.analysis.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String version = host.ctx().catalog.profile(r.profileId()).map(p -> p.minecraftVersion).orElse(r.profileId());
        CrashDiagnosis.Context c = new CrashDiagnosis.Context(version, host.ctx().config.maxMemoryMb, SystemCheck.totalRamMb(),
                host.ctx().config.graphicsBackend);
        String before = summary.getText();
        summary.setText(Lang.t("crash.analysis.running"));
        io.submit(() -> {
            CrashDiagnosis.Result res = CrashDiagnosis.analyze(r, host.ctx().gameDir(r.profileId()), c);
            SwingUtilities.invokeLater(() -> {
                summary.setText(before);
                showAnalysis(r, version, res);
            });
        });
    }

    private void showAnalysis(CrashReport r, String version, CrashDiagnosis.Result res) {
        JPanel p = Ui.column();
        p.add(Ui.label(Lang.t("crash.analysis.of", r.description(), version, DATE.format(r.time())), 12, Font.PLAIN, new Color(0x7D8794)));
        section(p, Lang.t("crash.analysis.what"), Ui.wrapText(res.what(), 13.5f, Theme.TEXT));
        section(p, Lang.t("crash.analysis.why"), Ui.wrapText(res.why(), 13f, Theme.MUTED));
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < res.steps().size(); i++) {
            steps.append(i + 1).append(". ").append(res.steps().get(i)).append(i + 1 < res.steps().size() ? "\n" : "");
        }
        section(p, Lang.t("crash.analysis.steps"), Ui.wrapText(steps.toString(), 13f, Theme.TEXT));
        if (!res.evidence().isEmpty()) {
            JTextArea ev = Ui.wrapText(res.evidence(), 12f, new Color(0xFFB4B6));
            ev.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            section(p, Lang.t("crash.analysis.evidence"), ev);
        }
        p.setPreferredSize(new Dimension(560, p.getPreferredSize().height + 40));
        String fix = fixLabel(res.fix());
        Object[] options = fix == null ? new Object[]{Lang.t("crash.analysis.close")} : new Object[]{fix, Lang.t("crash.analysis.close")};
        int choice = JOptionPane.showOptionDialog(host.frame(), p, Lang.t("crash.analysis.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (fix != null && choice == 0) {
            applyFix(r, res.fix());
        }
    }

    private static void section(JPanel p, String title, JComponent content) {
        p.add(Box.createVerticalStrut(12));
        p.add(Ui.label(title, 13, Font.BOLD, Theme.TEXT));
        p.add(Box.createVerticalStrut(3));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(content);
    }

    /** Text of the fix button, null if the launcher cannot fix it itself. */
    private static String fixLabel(CrashDiagnosis.Fix f) {
        return switch (f.action()) {
            case MORE_MEMORY, LESS_MEMORY -> Lang.t("crash.analysis.fix.memory", gb(f.memoryMb()));
            case GRAPHICS_BACKEND -> Lang.t("crash.analysis.fix.backend", backendName(f.backend()));
            case REPAIR -> Lang.t("crash.analysis.fix.repair");
            case DISABLE_SUSPECT -> Lang.t("crash.analysis.fix.disable", f.suspect().name());
            case SIGN_IN -> Lang.t("crash.analysis.fix.sign_in");
            case OPEN_LIBRARY -> Lang.t("crash.analysis.fix.library");
            case NONE -> null;
        };
    }

    private void applyFix(CrashReport r, CrashDiagnosis.Fix f) {
        var cfg = host.ctx().config;
        switch (f.action()) {
            case MORE_MEMORY, LESS_MEMORY -> {
                cfg.maxMemoryMb = f.memoryMb();
                cfg.minMemoryMb = Math.min(cfg.minMemoryMb, cfg.maxMemoryMb);
                host.saveConfig();
                host.settingsChanged();
                offerStart(r, Lang.t("crash.analysis.done.memory", gb(f.memoryMb())));
            }
            case GRAPHICS_BACKEND -> {
                cfg.graphicsBackend = f.backend();
                host.saveConfig();
                host.settingsChanged();
                offerStart(r, Lang.t("crash.analysis.done.backend", backendName(f.backend())));
            }
            case REPAIR -> {
                host.selectProfile(r.profileId());
                host.launch(null, true);
            }
            case DISABLE_SUSPECT -> {
                Kind kind = kindOf(r.profileId(), f.suspect());
                switch (kind.type) {
                    case USER -> disableUserMod(f.suspect());
                    case EXTENSION -> disableExtension(kind.key);
                    case CORE, DEPENDENCY, RESOURCE_PACK -> {
                        host.selectProfile(r.profileId());
                        host.launch(null, true);
                    }
                }
            }
            case SIGN_IN -> host.signIn();
            case OPEN_LIBRARY -> host.showPage("library");
            case NONE -> {
            }
        }
    }

    private void offerStart(CrashReport r, String message) {
        if (JOptionPane.showConfirmDialog(host.frame(), message, Lang.t("crash.analysis.done.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE) == JOptionPane.YES_OPTION) {
            host.selectProfile(r.profileId());
            host.launch(null, false);
        }
    }

    private static String gb(int mb) {
        return String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0);
    }

    private static String backendName(String backend) {
        return "vulkan".equals(backend) ? "Vulkan" : "OpenGL";
    }

    private static JButton button(String text, Icons.Kind icon, Runnable action) {
        return Ui.button(text, icon, action);
    }
}
