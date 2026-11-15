package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.DataTable;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.history.ConnectionHistory;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.launcher.core.stats.PlayStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * "Konto → Verlauf": the local connection history - sign-ins, renewals, sign-outs and game starts per account, and the
 * servers the game connected to (rejoin, copy, remove). Recording can be switched off; nothing leaves this PC.
 */
final class HistoryTab extends JPanel {
    /** Rows shown per table (the file keeps more). */
    static final int SHOWN = 60;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern(Lang.t("format.date_time"), Lang.locale())
            .withZone(ZoneId.systemDefault());

    private final PageHost host;
    private final DataTable events = new DataTable(List.of(Lang.t("history.col.event"), Lang.t("nav.account"), Lang.t("history.col.details"), Lang.t("history.col.time")), 1.1, 1.1, 1, 1.2);
    private final DataTable servers = new DataTable(List.of(Lang.t("history.col.server"), Lang.t("nav.account"), Lang.t("common.version"), Lang.t("history.col.last"), Lang.t("history.col.first"), Lang.t("history.col.visits")),
            1.6, 1, 0.8, 0.9, 0.9, 0.6);
    private final JLabel eventsMore = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);
    private final JLabel serversMore = Ui.label(" ", 12.5f, Font.PLAIN, Theme.MUTED);

    HistoryTab(PageHost host) {
        super(new BorderLayout());
        this.host = host;
        setOpaque(false);
        JPanel col = Ui.column();
        col.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        Card card = new Card(new BorderLayout()).calm();
        card.padding(26, 28, 26, 28);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in = Ui.column();

        ToggleSwitch keep = new ToggleSwitch(host.ctx().config.connectionHistory);
        keep.onChange(on -> {
            host.ctx().config.connectionHistory = on;
            host.ctx().play.history().setEnabled(on);
            host.saveConfig();
        });
        in.add(line(Lang.t("history.keep"), Lang.t("history.keep.desc"), keep));

        in.add(Box.createVerticalStrut(28));
        in.add(head(Lang.t("history.signins"), Lang.t("history.signins.desc"), () -> {
            if (confirm(Lang.t("history.signins.clear"))) {
                host.ctx().play.history().clearEvents();
                refresh();
            }
        }));
        in.add(Box.createVerticalStrut(14));
        in.add(events);
        eventsMore.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        in.add(eventsMore);

        in.add(Box.createVerticalStrut(26));
        in.add(head(Lang.t("history.col.server"), Lang.t("history.servers.desc"), () -> {
            if (confirm(Lang.t("history.servers.clear"))) {
                host.ctx().play.history().clearServers();
                refresh();
            }
        }));
        in.add(Box.createVerticalStrut(14));
        in.add(servers);
        serversMore.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        in.add(serversMore);

        card.add(in, BorderLayout.CENTER);
        col.add(card);
        add(Ui.scroll(col), BorderLayout.CENTER);
    }

    private boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(host.frame(), question, Lang.t("account.tab.history"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private static JComponent line(String title, String hint, JComponent control) {
        JPanel p = new JPanel(new BorderLayout(16, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel t = Ui.column();
        t.add(Ui.label(title, 15, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(3));
        t.add(Ui.label(hint, 12.5f, Font.PLAIN, Theme.MUTED));
        p.add(t, BorderLayout.CENTER);
        JPanel w = new JPanel(new GridBagLayout());
        w.setOpaque(false);
        w.add(control);
        p.add(w, BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JComponent head(String title, String hint, Runnable clear) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel t = Ui.column();
        t.add(Ui.heading(title));
        t.add(Box.createVerticalStrut(6));
        t.add(Ui.label(hint, 13.5f, Font.PLAIN, Theme.MUTED));
        p.add(t, BorderLayout.CENTER);
        JButton b = new JButton(Lang.t("history.clear"));
        if (!Theme.pixelStyle) {
            b.putClientProperty("JButton.buttonType", "roundRect");
        }
        b.setFocusable(false);
        b.addActionListener(e -> clear.run());
        JPanel w = new JPanel(new GridBagLayout());
        w.setOpaque(false);
        w.add(b);
        p.add(w, BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    void refresh() {
        ConnectionHistory h = host.ctx().play.history();
        Instant now = Instant.now();

        events.clear();
        List<ConnectionHistory.Event> ev = h.events();
        if (ev.isEmpty()) {
            events.empty(h.enabled() ? Lang.t("history.signins.empty") : Lang.t("history.off"));
        }
        for (ConnectionHistory.Event e : ev.subList(0, Math.min(SHOWN, ev.size()))) {
            String detail = e.kind() == ConnectionHistory.Kind.GAME_START ? "Minecraft " + e.detail() : e.detail();
            events.addRow(List.of(
                    DataTable.text(e.kind().label(), false),
                    DataTable.text(e.username(), false),
                    DataTable.text(detail.isBlank() ? "–" : detail, true),
                    DataTable.twoLine(PlayStats.ago(e.time(), now), WHEN.format(e.time()))), null);
        }
        eventsMore.setText(ev.size() > SHOWN ? Lang.t("history.more_entries", ev.size() - SHOWN) : " ");

        servers.clear();
        List<ConnectionHistory.Server> sv = h.servers();
        if (sv.isEmpty()) {
            servers.empty(h.enabled() ? Lang.t("history.servers.empty")
                    : Lang.t("history.off"));
        }
        for (ConnectionHistory.Server s : sv.subList(0, Math.min(SHOWN, sv.size()))) {
            servers.addRow(List.of(
                    DataTable.text(s.address(), false),
                    DataTable.text(s.username().isBlank() ? "–" : s.username(), false),
                    DataTable.text(s.version().isBlank() ? "–" : s.version(), true),
                    DataTable.text(PlayStats.ago(s.last(), now), false),
                    DataTable.text(PlayStats.ago(s.first(), now), true),
                    DataTable.text(Integer.toString(s.visits()), false)), DataTable.menu(List.of(
                    new DataTable.Action(Lang.t("library.server.join"), () -> join(s.address()), false),
                    new DataTable.Action(Lang.t("history.copy_address"), () -> copy(s.address()), false),
                    new DataTable.Action(Lang.t("history.remove"), () -> {
                        h.removeServer(s.address());
                        refresh();
                    }, true))));
        }
        serversMore.setText(sv.size() > SHOWN ? Lang.t("history.more_servers", sv.size() - SHOWN) : " ");
        revalidate();
        repaint();
    }

    /** Starts the selected version straight onto the server (Minecraft Quick Play). */
    private void join(String address) {
        if (host.gameRunning()) {
            host.error(Lang.t("social.join.title"), Lang.t("history.game_running"));
            return;
        }
        try {
            host.launch(new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, address), false);
        } catch (IllegalArgumentException e) {
            host.error(Lang.t("social.join.title"), e.getMessage());
        }
    }

    private static void copy(String text) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
    }
}
