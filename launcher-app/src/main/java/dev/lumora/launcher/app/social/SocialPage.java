package dev.lumora.launcher.app.social;

import dev.lumora.launcher.app.pages.PageHost;
import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.ToggleSwitch;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.social.Conv;
import dev.lumora.social.Endpoint;
import dev.lumora.social.Rules;
import dev.lumora.social.SocialClient;
import dev.lumora.social.SocialEvent;
import dev.lumora.social.model.ConversationInfo;
import dev.lumora.social.model.Friend;
import dev.lumora.social.model.FriendRequest;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Member;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Profile;
import dev.lumora.social.model.Role;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * "Freunde" page: friends with live status and requests, groups with roles, direct and group chat. Three columns like
 * the in-game version: list | group/friend info (Übersicht, Mitglieder, Einstellungen; can be hidden) | chat.
 */
public final class SocialPage extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY);

    private final PageHost host;
    private final SocialController social;
    private final CardLayout cards = new CardLayout();
    private final JPanel sideList = Ui.column();
    private final JPanel selfBox = new JPanel(new BorderLayout());
    private final Card info = new Card(new BorderLayout()).fill(Theme.SURFACE).padding(0, 0, 0, 0);
    private final JPanel chatHeader = new JPanel(new BorderLayout(12, 0));
    private final ChatView chat = new ChatView();
    private final JScrollPane chatScroll;
    private final JTextField input = new JTextField();
    private final JLabel counter = Ui.label("", 11.5f, Font.PLAIN, Theme.MUTED);
    private final JPanel toastBar = new JPanel(new BorderLayout());
    private final JLabel toastText = Ui.label("", 13, Font.PLAIN, Theme.TEXT);
    private final Timer toastTimer = new Timer(6000, e -> toastBar.setVisible(false));
    private final JLabel setupStatus = Ui.label(" ", 13, Font.PLAIN, Theme.MUTED);
    private String selected;
    private String infoTab = "overview";
    private boolean infoWanted = true;
    private boolean shown;

    public SocialPage(PageHost host, SocialController social) {
        setLayout(cards);
        setOpaque(false);
        this.host = host;
        this.social = social;
        add(setupView(), "setup");
        add(signInView(), "signin");
        // directly in the viewport (ChatView is Scrollable and tracks the width itself) so it can scroll to the newest message
        chatScroll = new JScrollPane(chat, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getVerticalScrollBar().setUnitIncrement(24);
        chatScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            SocialClient c = social.client();
            if (c != null && selected != null && chat.nearTop() && c.historyLoaded(selected) && !c.historyComplete(selected)) {
                c.loadHistory(selected);
            }
        });
        add(mainView(), "main");
        social.addListener(this::onEvent);
        toastTimer.setRepeats(false);
        // faces arrive asynchronously from Mojang: repaint while some are still loading
        Timer faces = new Timer(700, e -> {
            if (SocialPaint.facesPending && isShowing()) {
                SocialPaint.facesPending = false;
                repaint();
            }
        });
        faces.start();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateInfoVisibility();
            }
        });
        refresh();
    }

    /** Called when the page is shown or hidden; while shown, the open conversation counts as read. */
    public void setShown(boolean s) {
        shown = s;
        focus();
        if (s) {
            refresh();
        }
    }

    /** Window got or lost focus. */
    public void windowActive(boolean active) {
        SocialClient c = social.client();
        if (c != null) {
            c.setFocused(active && shown ? selected : null);
        }
    }

    private void focus() {
        SocialClient c = social.client();
        if (c != null) {
            c.setFocused(shown ? selected : null);
        }
    }

    // ------------------------------------------------------------------ events

    private void onEvent(SocialEvent e) {
        SocialClient c = social.client();
        switch (e.kind) {
            case STATUS -> refresh();
            case FRIENDS, REQUESTS, GROUPS -> {
                if (selected != null && c != null && !exists(c, selected)) {
                    selected = null;
                }
                rebuildSide();
                rebuildHeader();
                rebuildInfo();
                if (selected == null) {
                    autoSelect();
                }
            }
            case CONVERSATION, HISTORY -> {
                rebuildSide();
                if (e.conv != null && e.conv.equals(selected)) {
                    chat.refresh();
                }
            }
            case NOTICE, ERROR -> toast(e.text);
            default -> {
            }
        }
    }

    private static boolean exists(SocialClient c, String conv) {
        if (Conv.isGroup(conv)) {
            return c.group(Conv.groupId(conv)) != null;
        }
        return c.friend(Conv.other(conv, c.self().uuid)) != null || !c.messages(conv).isEmpty();
    }

    /** Rebuilds everything (status change, page shown). */
    public void refresh() {
        SocialClient c = social.client();
        if (host.ctx().config.socialServer == null) {
            cards.show(this, "setup");
            return;
        }
        if (c == null) {
            cards.show(this, "signin");
            return;
        }
        cards.show(this, "main");
        if (selected == null) {
            autoSelect();
        }
        rebuildSide();
        rebuildSelf();
        rebuildHeader();
        rebuildInfo();
        chat.show(c, selected);
        focus();
    }

    private void autoSelect() {
        SocialClient c = social.client();
        if (c == null || c.status() != SocialClient.Status.ONLINE) {
            return;
        }
        List<Group> groups = c.groups();
        List<Friend> friends = c.friends();
        String next = !groups.isEmpty() ? groups.get(0).conv() : !friends.isEmpty() ? Conv.dm(c.self().uuid, friends.get(0).profile.uuid) : null;
        if (next != null) {
            select(next);
        }
    }

    private void select(String conv) {
        selected = conv;
        infoTab = "overview";
        SocialClient c = social.client();
        rebuildSide();
        rebuildHeader();
        rebuildInfo();
        chat.show(c, conv);
        input.setEnabled(conv != null);
        focus();
        if (conv != null) {
            input.requestFocusInWindow();
        }
    }

    private void toast(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        toastText.setText(text);
        toastBar.setVisible(true);
        toastTimer.restart();
    }

    private <T> void run(CompletableFuture<T> f) {
        f.whenComplete((r, ex) -> {
            if (ex != null) {
                Throwable c = ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                SwingUtilities.invokeLater(() -> toast(c.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------ setup / sign-in

    private JComponent setupView() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setOpaque(false);
        p.add(Ui.pageTitle("Freunde & Gruppen", "Mit Freunden schreiben, Gruppen mit Rollen anlegen und sehen, wer gerade spielt – im Launcher und im Spiel."),
                BorderLayout.NORTH);
        Card c = new Card(new BorderLayout(0, 14));
        JPanel col = Ui.column();
        col.add(Ui.label("Mit einem Lumora-Social-Server verbinden", 17, Font.BOLD, Theme.TEXT));
        col.add(Box.createVerticalStrut(8));
        col.add(Ui.wrapText("Freunde und Chats laufen über einen eigenen Lumora-Social-Server, den du oder jemand aus deiner Runde "
                + "betreibt (lumora-social-server.jar, Anleitung in SOCIAL.md). Es gibt keinen voreingestellten Server. "
                + "Die Anmeldung läuft über dein Minecraft-Konto (offizieller Mojang-Sitzungsdienst); der Server sieht dabei "
                + "weder Passwort noch Token. Nachrichten werden auf dem Server gespeichert.", 13.5f, Theme.MUTED));
        col.add(Box.createVerticalStrut(14));
        JTextField addr = SocialDialogs.field("", 200, "Server-Adresse, z. B. chat.meinserver.de oder 192.168.0.10");
        addr.setMaximumSize(new Dimension(520, 38));
        col.add(addr);
        col.add(Box.createVerticalStrut(10));
        JButton connect = SocialDialogs.primary("Verbinden");
        connect.addActionListener(e -> {
            try {
                String uri = Endpoint.parse(addr.getText()).toString();
                setServer(uri);
            } catch (IllegalArgumentException ex) {
                setupStatus.setForeground(Theme.FAIL);
                setupStatus.setText(ex.getMessage());
            }
        });
        addr.addActionListener(e -> connect.doClick());
        JPanel row = Ui.row(10);
        row.add(connect);
        col.add(row);
        col.add(Box.createVerticalStrut(8));
        col.add(setupStatus);
        c.add(col, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(c, BorderLayout.NORTH);
        p.add(north, BorderLayout.CENTER);
        return p;
    }

    private JComponent signInView() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setOpaque(false);
        p.add(Ui.pageTitle("Freunde & Gruppen", "Melde dich mit deinem Microsoft-Konto an, um Freunde und Chats zu nutzen."), BorderLayout.NORTH);
        Card c = new Card(new BorderLayout(0, 12));
        JPanel col = Ui.column();
        col.add(Ui.label("Nicht angemeldet", 17, Font.BOLD, Theme.TEXT));
        col.add(Box.createVerticalStrut(8));
        col.add(Ui.wrapText("Lumora Social erkennt dich an deinem Minecraft-Konto. Nach der Anmeldung verbindet sich der Launcher "
                + "automatisch mit dem eingestellten Server.", 13.5f, Theme.MUTED));
        col.add(Box.createVerticalStrut(14));
        JPanel row = Ui.row(10);
        JButton signIn = SocialDialogs.primary("Anmelden");
        signIn.addActionListener(e -> host.signIn());
        JButton server = SocialDialogs.secondary("Server ändern…");
        server.addActionListener(e -> SocialDialogs.server(this, host.ctx().config.socialServer, this::setServer));
        row.add(signIn);
        row.add(server);
        col.add(row);
        c.add(col, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(c, BorderLayout.NORTH);
        p.add(north, BorderLayout.CENTER);
        return p;
    }

    private void setServer(String uri) {
        host.ctx().config.socialServer = uri;
        host.saveConfig();
        selected = null;
        setupStatus.setText(" ");
        social.apply();
        refresh();
    }

    // ------------------------------------------------------------------ layout

    private JComponent mainView() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);

        Card side = new Card(new BorderLayout()).fill(Theme.SURFACE).padding(14, 10, 10, 10);
        side.setPreferredSize(new Dimension(290, 10));
        JScrollPane sideScroll = Ui.scroll(sideList);
        side.add(sideScroll, BorderLayout.CENTER);
        selfBox.setOpaque(false);
        selfBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 0, 0, 0)));
        side.add(selfBox, BorderLayout.SOUTH);
        root.add(side, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(14, 0));
        center.setOpaque(false);
        info.setPreferredSize(new Dimension(310, 10));
        center.add(info, BorderLayout.WEST);

        Card chatCard = new Card(new BorderLayout()).fill(Theme.SURFACE).padding(0, 0, 0, 0);
        chatHeader.setOpaque(false);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 18, 14, 14)));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(chatHeader, BorderLayout.NORTH);
        toastBar.setOpaque(true);
        toastBar.setBackground(Theme.alpha(Theme.ACCENT, 45));
        toastBar.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        toastBar.add(toastText, BorderLayout.CENTER);
        toastBar.setVisible(false);
        top.add(toastBar, BorderLayout.SOUTH);
        chatCard.add(top, BorderLayout.NORTH);
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);
        chatCard.add(chatScroll, BorderLayout.CENTER);
        chatCard.add(inputBar(), BorderLayout.SOUTH);
        center.add(chatCard, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private JComponent inputBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 16, 14, 16)));
        Card field = new Card(new BorderLayout(8, 0)).fill(Theme.SURFACE_2).outline(Theme.BORDER).radius(14).padding(4, 14, 4, 10);
        input.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        input.setOpaque(false);
        input.setFont(Theme.font(Font.PLAIN, 14.5f));
        input.putClientProperty("JTextField.placeholderText", "Nachricht schreiben…");
        input.putClientProperty("JComponent.outline", null);
        SocialDialogs.limit(input.getDocument(), Rules.MESSAGE_MAX);
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                int n = input.getDocument().getLength();
                counter.setText(n > Rules.MESSAGE_MAX - 100 ? n + " / " + Rules.MESSAGE_MAX : "");
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }
        });
        input.addActionListener(e -> send());
        field.add(input, BorderLayout.CENTER);
        field.add(counter, BorderLayout.EAST);
        bar.add(field, BorderLayout.CENTER);
        JComponent sendButton = new JComponent() {
            private boolean hover;

            {
                setPreferredSize(new Dimension(48, 48));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Senden (Enter)");
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        send();
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
                SocialPaint.quality(g);
                g.setColor(hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
                Theme.fillRound(g, 0, 0, getWidth(), getHeight(), 14, 14);
                Icons.of(Icons.Kind.SEND, 22, Theme.BG).paintIcon(this, g, (getWidth() - 22) / 2 + 1, (getHeight() - 22) / 2);
                g.dispose();
            }
        };
        bar.add(sendButton, BorderLayout.EAST);
        return bar;
    }

    private void send() {
        SocialClient c = social.client();
        String text = input.getText();
        if (c == null || selected == null || text.isBlank()) {
            return;
        }
        if (Conv.isDm(selected) && c.friend(Conv.other(selected, c.self().uuid)) == null) {
            toast("Nachrichten gehen nur an Freunde.");
            return;
        }
        c.send(selected, text);
        input.setText("");
        chat.scrollToBottom();
    }

    private void updateInfoVisibility() {
        boolean v = infoWanted && selected != null && getWidth() >= 940;
        if (info.isVisible() != v) {
            info.setVisible(v);
            revalidate();
        }
    }

    // ------------------------------------------------------------------ sidebar

    private JComponent caption(String text, Icons.Kind action, String tip, Runnable run) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        p.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 4));
        p.add(Ui.label(text, 13, Font.BOLD, Theme.MUTED), BorderLayout.WEST);
        if (action != null) {
            p.add(iconButton(action, tip, run), BorderLayout.EAST);
        }
        return p;
    }

    static JComponent iconButton(Icons.Kind kind, String tip, Runnable run) {
        JComponent b = new JComponent() {
            private boolean hover;

            {
                setPreferredSize(new Dimension(32, 32));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(tip);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        run.run();
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
                SocialPaint.quality(g);
                if (hover) {
                    g.setColor(Theme.SURFACE_2);
                    Theme.fillRound(g, 0, 0, getWidth(), getHeight(), 10, 10);
                }
                Icons.of(kind, 20, hover ? Theme.TEXT : Theme.MUTED).paintIcon(this, g, (getWidth() - 20) / 2, (getHeight() - 20) / 2);
                g.dispose();
            }
        };
        return b;
    }

    private void rebuildSide() {
        sideList.removeAll();
        SocialClient c = social.client();
        if (c == null) {
            sideList.revalidate();
            return;
        }
        String me = c.self().uuid;
        sideList.add(caption("FREUNDE", Icons.Kind.USER_PLUS, "Freund hinzufügen",
                () -> SocialDialogs.addFriend(this, name -> c.addFriend(name).thenRun(() -> SwingUtilities.invokeLater(
                        () -> toast("Anfrage an " + name + " gesendet."))))));
        for (FriendRequest r : c.requests()) {
            SocialRow row = new SocialRow((g, x, y, s) -> SocialPaint.face(g, r.profile.uuid, r.profile.name, x, y, s),
                    r.profile.name, r.incoming ? "möchte dein Freund sein" : "Anfrage gesendet", r.incoming ? Theme.ACCENT_HOVER : Theme.MUTED, null);
            if (r.incoming) {
                row.action(Icons.Kind.CHECK, "Annehmen", Theme.OK, () -> run(c.acceptFriend(r.profile.uuid)));
            }
            row.action(Icons.Kind.X, r.incoming ? "Ablehnen" : "Zurückziehen", Theme.FAIL, () -> run(c.declineFriend(r.profile.uuid)));
            sideList.add(row);
        }
        List<Friend> friends = c.friends();
        if (friends.isEmpty() && c.requests().isEmpty()) {
            sideList.add(hint(c.status() == SocialClient.Status.ONLINE ? "Noch keine Freunde. Über + per Minecraft-Namen hinzufügen." : "Nicht verbunden."));
        }
        for (Friend f : friends) {
            String conv = Conv.dm(me, f.profile.uuid);
            SocialRow row = new SocialRow((g, x, y, s) -> {
                SocialPaint.face(g, f.profile.uuid, f.profile.name, x, y, s);
                SocialPaint.statusDot(g, f.presence, x + s - 3, y + s - 3, 5, isSelected(conv) ? SocialPaint.SELECTED : Theme.SURFACE);
            }, f.profile.name, SocialPaint.listText(f.presence), f.presence.online() ? Theme.MUTED : new Color(0x6B7280), () -> select(conv))
                    .selected(isSelected(conv)).badge(c.unread(conv))
                    // name in the status colour (green online, amber in game), server in bold: "spielt auf hypixel.net"
                    .titleColor(f.presence.online() ? SocialPaint.presenceColor(f.presence) : Theme.TEXT)
                    .strong(f.presence.state == dev.lumora.social.model.Presence.State.MULTIPLAYER && !f.presence.detail.isEmpty()
                            ? f.presence.detail : null);
            if (f.presence.state == Presence.State.MULTIPLAYER && !f.presence.detail.isEmpty()) {
                // one click onto the friend's server (Quick Play, or --server on versions before 1.20)
                row.action(Icons.Kind.PLAY, "Server beitreten: " + f.presence.detail, Theme.ACCENT_HOVER, () -> joinServer(f.presence));
            }
            row.setComponentPopupMenu(friendMenu(c, f));
            sideList.add(row);
        }
        sideList.add(Box.createVerticalStrut(10));
        sideList.add(caption("GRUPPEN", Icons.Kind.PLUS, "Gruppe erstellen", this::createGroup));
        List<Group> groups = c.groups();
        if (groups.isEmpty()) {
            sideList.add(hint("Noch keine Gruppen. Über + eine Gruppe erstellen und Freunde einladen."));
        }
        for (Group g : groups) {
            String conv = g.conv();
            ConversationInfo ci = c.conversation(conv);
            SocialRow row = new SocialRow((gr, x, y, s) -> SocialPaint.groupTile(gr, g.icon, g.color, x, y, s), g.name,
                    g.members.size() + (g.members.size() == 1 ? " Mitglied" : " Mitglieder") + (ci != null && ci.muted ? " · stumm" : ""),
                    Theme.MUTED, () -> select(conv)).selected(isSelected(conv)).badge(ci != null && ci.muted ? 0 : c.unread(conv));
            sideList.add(row);
        }
        sideList.add(Box.createVerticalGlue());
        sideList.revalidate();
        sideList.repaint();
    }

    private boolean isSelected(String conv) {
        return conv.equals(selected);
    }

    private static JComponent hint(String text) {
        JComponent t = Ui.wrapText(text, 12.5f, Theme.MUTED);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(2, 12, 8, 12));
        p.add(t);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        return p;
    }

    private void rebuildSelf() {
        selfBox.removeAll();
        SocialClient c = social.client();
        if (c == null) {
            return;
        }
        Profile me = c.self();
        String status;
        Color color;
        switch (c.status()) {
            case ONLINE -> {
                status = "Online · " + Endpoint.display(c.uri());
                color = Theme.OK;
            }
            case CONNECTING -> {
                status = "Verbinde mit " + Endpoint.display(c.uri()) + "…";
                color = Theme.WARN;
            }
            case REJECTED -> {
                status = c.statusText();
                color = Theme.FAIL;
            }
            default -> {
                status = "Getrennt" + (c.statusText().isEmpty() ? "" : ": " + c.statusText()) + " – neuer Versuch gleich";
                color = Theme.WARN;
            }
        }
        Presence p = c.status() == SocialClient.Status.ONLINE ? new Presence(Presence.State.LAUNCHER, "", "") : Presence.OFFLINE;
        SocialRow row = new SocialRow((g, x, y, s) -> {
            SocialPaint.face(g, me.uuid, me.name, x, y, s);
            SocialPaint.statusDot(g, p, x + s - 3, y + s - 3, 5, Theme.SURFACE);
        }, me.name, status, color, null);
        if (c.status() == SocialClient.Status.REJECTED || c.status() == SocialClient.Status.OFFLINE) {
            row.action(Icons.Kind.REFRESH, "Neu verbinden", Theme.TEXT, () -> {
                social.reconnect();
                rebuildSelf();
            });
        }
        row.action(Icons.Kind.SLIDERS, "Lumora-Social-Einstellungen", Theme.TEXT, () -> settingsMenu().show(selfBox, selfBox.getWidth() - 220, -10));
        row.setToolTipText(status);
        selfBox.add(row, BorderLayout.CENTER);
        selfBox.revalidate();
        selfBox.repaint();
    }

    private JPopupMenu settingsMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem server = new JMenuItem("Server ändern…");
        server.addActionListener(e -> SocialDialogs.server(this, host.ctx().config.socialServer, this::setServer));
        m.add(server);
        JCheckBoxMenuItem share = new JCheckBoxMenuItem("Server-Adresse für Freunde sichtbar", host.ctx().config.socialShareServer);
        share.setToolTipText("Wirkt beim nächsten Spielstart: Freunde sehen, auf welchem Server du spielst.");
        share.addActionListener(e -> {
            host.ctx().config.socialShareServer = share.isSelected();
            host.saveConfig();
        });
        m.add(share);
        m.addSeparator();
        JMenuItem off = new JMenuItem("Lumora Social ausschalten");
        off.addActionListener(e -> {
            if (SocialDialogs.confirm(this, "Lumora Social ausschalten", "Der Launcher und das Spiel verbinden sich nicht mehr mit "
                    + Endpoint.display(java.net.URI.create(host.ctx().config.socialServer)) + ". Freunde und Gruppen bleiben auf dem Server erhalten.", "Ausschalten")) {
                setServer(null);
            }
        });
        m.add(off);
        return m;
    }

    private JPopupMenu friendMenu(SocialClient c, Friend f) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem msg = new JMenuItem("Nachricht schreiben");
        msg.addActionListener(e -> select(Conv.dm(c.self().uuid, f.profile.uuid)));
        m.add(msg);
        if (f.presence.state == Presence.State.MULTIPLAYER && !f.presence.detail.isEmpty()) {
            JMenuItem join = new JMenuItem("Server beitreten (" + f.presence.detail + ")");
            join.addActionListener(e -> joinServer(f.presence));
            m.add(join);
        }
        JMenu add = new JMenu("Zu Gruppe hinzufügen");
        for (Group g : c.groups()) {
            if (g.canManage(c.self().uuid) && g.member(f.profile.uuid) == null) {
                JMenuItem gi = new JMenuItem(g.name);
                gi.addActionListener(e -> run(c.addMember(g.id, f.profile.uuid)));
                add.add(gi);
            }
        }
        add.setEnabled(add.getItemCount() > 0);
        m.add(add);
        m.addSeparator();
        JMenuItem remove = new JMenuItem("Freund entfernen");
        remove.setForeground(Theme.FAIL);
        remove.addActionListener(e -> {
            if (SocialDialogs.confirm(this, "Freund entfernen", f.profile.name + " wird aus deiner Freundesliste entfernt. "
                    + "Ihr seht euren Status nicht mehr und könnt euch nicht mehr direkt schreiben.", "Entfernen")) {
                run(c.removeFriend(f.profile.uuid));
            }
        });
        m.add(remove);
        return m;
    }

    private void joinServer(Presence p) {
        if (host.gameRunning()) {
            // the running game cannot be redirected from outside: hand over the address
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(p.detail), null);
            javax.swing.JOptionPane.showMessageDialog(host.frame(), "Minecraft läuft schon. Die Adresse " + p.detail + " ist kopiert:\n"
                    + "im Spiel unter Mehrspieler → Direkt verbinden einfügen.", "Server beitreten", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!p.version.isEmpty() && host.ctx().catalog.profile(p.version).isPresent()) {
            host.selectProfile(p.version);
        }
        host.launch(new LaunchCommandBuilder.QuickPlay(LaunchCommandBuilder.QuickPlay.Kind.MULTIPLAYER, p.detail), false);
    }

    private void createGroup() {
        SocialClient c = social.client();
        if (c == null) {
            return;
        }
        SocialDialogs.group(this, "Gruppe erstellen", new SocialDialogs.GroupValues("", "", "flag", Rules.COLORS.get(2)), "Erstellen",
                v -> c.createGroup(v.name(), v.description(), v.icon(), v.color()).thenAccept(id -> SwingUtilities.invokeLater(() -> {
                    if (id != null) {
                        select(Conv.group(id));
                    }
                })));
    }

    // ------------------------------------------------------------------ chat header

    private void rebuildHeader() {
        chatHeader.removeAll();
        SocialClient c = social.client();
        if (c == null || selected == null) {
            chatHeader.add(Ui.label(c != null && c.status() == SocialClient.Status.ONLINE
                    ? "Wähle links einen Freund oder eine Gruppe." : "Nicht verbunden", 15, Font.BOLD, Theme.MUTED), BorderLayout.CENTER);
            chatHeader.revalidate();
            chatHeader.repaint();
            input.setEnabled(false);
            updateInfoVisibility();
            return;
        }
        input.setEnabled(true);
        String title;
        String sub;
        SocialRow.Avatar avatar;
        Group g = Conv.isGroup(selected) ? c.group(Conv.groupId(selected)) : null;
        if (g != null) {
            title = g.name;
            sub = g.members.size() + (g.members.size() == 1 ? " Mitglied" : " Mitglieder");
            avatar = (gr, x, y, s) -> SocialPaint.groupTile(gr, g.icon, g.color, x, y, s);
        } else {
            String other = Conv.other(selected, c.self().uuid);
            Presence p = c.presenceOf(other);
            title = c.nameOf(other);
            sub = SocialPaint.presenceText(p);
            avatar = (gr, x, y, s) -> {
                SocialPaint.face(gr, other, title, x, y, s);
                SocialPaint.statusDot(gr, p, x + s - 3, y + s - 3, 5, Theme.SURFACE);
            };
        }
        SocialRow head = new SocialRow(avatar, title, sub, Theme.MUTED, null).height(48, 42);
        chatHeader.add(head, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        actions.setOpaque(false);
        ConversationInfo ci = c.conversation(selected);
        boolean muted = ci != null && ci.muted;
        actions.add(iconButton(Icons.Kind.INFO, infoWanted ? "Infos ausblenden" : "Infos anzeigen", () -> {
            infoWanted = !infoWanted;
            updateInfoVisibility();
            rebuildHeader();
        }));
        actions.add(iconButton(muted ? Icons.Kind.BELL_OFF : Icons.Kind.BELL, muted ? "Benachrichtigungen an" : "Stummschalten",
                () -> run(c.setMuted(selected, !muted))));
        chatHeader.add(actions, BorderLayout.EAST);
        chatHeader.revalidate();
        chatHeader.repaint();
        updateInfoVisibility();
    }

    // ------------------------------------------------------------------ info column

    private void rebuildInfo() {
        info.removeAll();
        SocialClient c = social.client();
        if (c == null || selected == null) {
            updateInfoVisibility();
            return;
        }
        Group g = Conv.isGroup(selected) ? c.group(Conv.groupId(selected)) : null;
        JComponent content = g != null ? groupInfo(c, g) : friendInfo(c, Conv.other(selected, c.self().uuid));
        JScrollPane sp = Ui.scroll(content);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        info.add(sp, BorderLayout.CENTER);
        info.revalidate();
        info.repaint();
        updateInfoVisibility();
    }

    /** Banner in the group color with the group symbol in a circle, like a server banner. */
    private static JComponent banner(int color, SocialRow.Avatar avatar) {
        JComponent b = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                SocialPaint.quality(g);
                Color c = SocialPaint.rgb(color);
                java.awt.Shape clip = Theme.roundShape(0, 0, getWidth(), 140, Theme.RADIUS, Theme.RADIUS);
                java.awt.Shape clip0 = g.getClip();
                g.clip(clip);
                g.setPaint(new GradientPaint(0, 0, Theme.alpha(c, 200), getWidth(), 110, Theme.alpha(c.darker().darker(), 230)));
                g.fillRect(0, 0, getWidth(), 110);
                // soft stars like the menu sky
                g.setColor(new Color(255, 255, 255, 60));
                int seed = color;
                for (int i = 0; i < 26; i++) {
                    seed = seed * 1103515245 + 12345;
                    int x = Math.floorMod(seed >> 8, Math.max(1, getWidth()));
                    int y = Math.floorMod(seed >> 3, 100);
                    g.fillOval(x, y, 2, 2);
                }
                g.setClip(clip0);
                g.setColor(Theme.SURFACE);
                g.fill(new Ellipse2D.Double(14, 64, 84, 84));
                g.setColor(Theme.alpha(c, 255));
                g.fill(new Ellipse2D.Double(18, 68, 76, 76));
                g.clip(new Ellipse2D.Double(18, 68, 76, 76));
                avatar.paint(g, 22, 72, 68);
                g.dispose();
            }
        };
        b.setPreferredSize(new Dimension(300, 152));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 152));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    private JComponent tabs(List<String> ids, List<String> labels, List<Icons.Kind> icons) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, ids.size()));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String label = labels.get(i);
            Icons.Kind kind = icons.get(i);
            JComponent t = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    SocialPaint.quality(g);
                    boolean sel = id.equals(infoTab);
                    Color c = sel ? Theme.TEXT : Theme.MUTED;
                    Icons.of(kind, 20, sel ? Theme.ACCENT_HOVER : Theme.MUTED).paintIcon(this, g, (getWidth() - 20) / 2, 8);
                    g.setFont(Theme.font(sel ? Font.BOLD : Font.PLAIN, 12.5f));
                    g.setColor(c);
                    int w = g.getFontMetrics().stringWidth(label);
                    g.drawString(label, (getWidth() - w) / 2, 46);
                    if (sel) {
                        g.setColor(Theme.ACCENT);
                        Theme.fillRound(g, (getWidth() - w) / 2 - 6, getHeight() - 3, w + 12, 3, 3, 3);
                    }
                    g.dispose();
                }
            };
            t.setPreferredSize(new Dimension(90, 60));
            t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            t.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    infoTab = id;
                    rebuildInfo();
                }
            });
            p.add(t);
        }
        return p;
    }

    private static JComponent section(String title) {
        JLabel l = Ui.label(title, 12, Font.BOLD, Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        return l;
    }

    private static JComponent padded(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        p.add(c);
        return p;
    }

    private JComponent groupInfo(SocialClient c, Group g) {
        String me = c.self().uuid;
        boolean owner = me.equals(g.owner);
        boolean manage = g.canManage(me);
        JPanel col = Ui.column();
        col.add(banner(g.color, (gr, x, y, s) -> Icons.of(Icons.symbol(g.icon), 40, Color.WHITE).paintIcon(null, gr, x + (s - 40) / 2, y + (s - 40) / 2)));
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(8, 18, 10, 10));
        JPanel names = Ui.column();
        names.add(Ui.label(g.name, 20, Font.BOLD, Theme.TEXT));
        names.add(Box.createVerticalStrut(3));
        names.add(Ui.label(g.members.size() + (g.members.size() == 1 ? " Mitglied" : " Mitglieder"), 13.5f, Font.PLAIN, Theme.MUTED));
        title.add(names, BorderLayout.CENTER);
        title.add(iconButton(Icons.Kind.MORE, "Mehr", () -> groupMenu(c, g).show(title, title.getWidth() - 180, 40)), BorderLayout.EAST);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        col.add(title);
        col.add(tabs(List.of("overview", "members", "settings"), List.of("Übersicht", "Mitglieder", "Einstellungen"),
                List.of(Icons.Kind.INFO, Icons.Kind.USERS, Icons.Kind.SLIDERS)));
        switch (infoTab) {
            case "members" -> {
                if (manage) {
                    JButton add = SocialDialogs.secondary("Mitglied hinzufügen");
                    add.setIcon(Icons.of(Icons.Kind.USER_PLUS, 16, Theme.TEXT));
                    add.addActionListener(e -> {
                        List<Friend> candidates = new ArrayList<>();
                        for (Friend f : c.friends()) {
                            if (g.member(f.profile.uuid) == null) {
                                candidates.add(f);
                            }
                        }
                        SocialDialogs.pickFriend(this, "Mitglied hinzufügen", candidates, f -> c.addMember(g.id, f.profile.uuid));
                    });
                    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    row.setOpaque(false);
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setBorder(BorderFactory.createEmptyBorder(14, 18, 4, 18));
                    row.add(add);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
                    col.add(row);
                }
                col.add(section("MITGLIEDER – " + g.members.size()));
                List<Member> members = new ArrayList<>(g.members);
                List<Role> order = g.rolesInOrder();
                members.sort((a, b) -> {
                    int ra = order.indexOf(g.role(a.role));
                    int rb = order.indexOf(g.role(b.role));
                    if (a.profile.uuid.equals(g.owner) != b.profile.uuid.equals(g.owner)) {
                        return a.profile.uuid.equals(g.owner) ? -1 : 1;
                    }
                    return ra != rb ? Integer.compare(ra, rb) : a.profile.name.compareToIgnoreCase(b.profile.name);
                });
                for (Member m : members) {
                    Role r = g.role(m.role);
                    Presence p = c.presenceOf(m.profile.uuid);
                    boolean self = m.profile.uuid.equals(me);
                    SocialRow row = new SocialRow((gr, x, y, s) -> {
                        SocialPaint.face(gr, m.profile.uuid, m.profile.name, x, y, s);
                        if (self || c.friend(m.profile.uuid) != null) {
                            SocialPaint.statusDot(gr, self ? new Presence(Presence.State.LAUNCHER, "", "") : p, x + s - 3, y + s - 3, 5, Theme.SURFACE);
                        }
                    }, m.profile.name + (self ? " (du)" : ""), r.name, SocialPaint.rgb(r.color), null).height(52, 36);
                    JPopupMenu menu = memberMenu(c, g, m);
                    if (menu.getComponentCount() > 0) {
                        row.action(Icons.Kind.MORE, "Mitglied verwalten", Theme.MUTED, () -> menu.show(row, row.getWidth() - 200, row.getHeight()));
                    }
                    col.add(padded(row));
                }
            }
            case "settings" -> {
                col.add(section("BENACHRICHTIGUNGEN"));
                ConversationInfo ci = c.conversation(g.conv());
                ToggleSwitch notify = new ToggleSwitch(ci == null || !ci.muted);
                notify.onChange(on -> run(c.setMuted(g.conv(), !on)));
                col.add(padded(settingRow("Neue Nachrichten melden", "Zähler und Hinweise", notify)));
                if (manage) {
                    col.add(section("GRUPPE"));
                    JButton edit = SocialDialogs.secondary("Gruppe bearbeiten…");
                    edit.setToolTipText("Name, Beschreibung, Symbol und Farbe");
                    edit.setIcon(Icons.of(Icons.Kind.EDIT, 16, Theme.TEXT));
                    edit.addActionListener(e -> editGroup(c, g));
                    col.add(padded(edit));
                }
                if (owner) {
                    col.add(section("ROLLEN"));
                    JButton role = SocialDialogs.secondary("Neue Rolle…");
                    role.setIcon(Icons.of(Icons.Kind.PLUS, 16, Theme.TEXT));
                    role.setEnabled(g.roles.size() < Rules.ROLES_MAX);
                    role.addActionListener(e -> SocialDialogs.role(this, "Neue Rolle", new SocialDialogs.RoleValues("", "star", Rules.COLORS.get(1), false),
                            false, "Erstellen", v -> c.saveRole(g.id, null, v.name(), v.icon(), v.color(), v.manage())));
                    col.add(padded(role));
                    col.add(Box.createVerticalStrut(6));
                    col.add(padded(Ui.wrapText("Rollen bearbeiten: in der Übersicht auf eine Rolle klicken. Owner und Mitglied sind fest.",
                            12f, Theme.MUTED)));
                }
                col.add(section("GEFAHRENZONE"));
                JButton leave = SocialDialogs.danger("Gruppe verlassen");
                leave.setIcon(Icons.of(Icons.Kind.LOGOUT, 16, Theme.FAIL));
                leave.addActionListener(e -> {
                    if (SocialDialogs.confirm(this, "Gruppe verlassen", "Du verlässt „" + g.name + "“." + (owner
                            ? " Die Gruppe geht an das dienstälteste verwaltende Mitglied." : "") + " Zurück nur über eine neue Einladung.", "Verlassen")) {
                        run(c.leaveGroup(g.id));
                    }
                });
                col.add(padded(leave));
                if (owner) {
                    col.add(Box.createVerticalStrut(8));
                    JButton del = SocialDialogs.danger("Gruppe löschen");
                    del.setIcon(Icons.of(Icons.Kind.TRASH, 16, Theme.FAIL));
                    del.addActionListener(e -> {
                        if (SocialDialogs.confirm(this, "Gruppe löschen", "„" + g.name + "“ wird mit allen Nachrichten für alle Mitglieder gelöscht. "
                                + "Das lässt sich nicht rückgängig machen.", "Endgültig löschen")) {
                            run(c.deleteGroup(g.id));
                        }
                    });
                    col.add(padded(del));
                }
                col.add(Box.createVerticalStrut(14));
                col.add(padded(Ui.wrapText("Erstellt am " + DATE.format(SocialPaint.day(g.created)), 12f, Theme.MUTED)));
            }
            default -> {
                col.add(section("BESCHREIBUNG"));
                col.add(padded(Ui.wrapText(g.description.isEmpty() ? (manage ? "Noch keine Beschreibung. Unter Einstellungen hinzufügen."
                        : "Keine Beschreibung.") : g.description, 13.5f, g.description.isEmpty() ? Theme.MUTED : Theme.TEXT)));
                col.add(section("ROLLEN"));
                for (Role r : g.rolesInOrder()) {
                    int n = g.membersWithRole(r.id);
                    SocialRow row = new SocialRow((gr, x, y, s) -> {
                        gr.setColor(Theme.alpha(SocialPaint.rgb(r.color), 45));
                        Theme.fillRound(gr, x, y, s, s, 12, 12);
                        Icons.of(Icons.symbol(r.icon), s - 14, SocialPaint.rgb(r.color)).paintIcon(null, gr, x + 7, y + 7);
                    }, r.name, n + (n == 1 ? " Mitglied" : " Mitglieder") + (r.manage && !Role.OWNER.equals(r.id) ? " · verwaltet" : ""),
                            Theme.MUTED, owner ? () -> editRole(c, g, r) : null).height(52, 36);
                    col.add(padded(row));
                }
            }
        }
        col.add(Box.createVerticalStrut(16));
        return col;
    }

    private static JComponent settingRow(String title, String sub, JComponent control) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel t = Ui.column();
        t.add(Ui.label(title, 13.5f, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(2));
        t.add(Ui.wrapText(sub, 12f, Theme.MUTED));
        p.add(t, BorderLayout.CENTER);
        JPanel cw = new JPanel(new BorderLayout());
        cw.setOpaque(false);
        cw.add(control, BorderLayout.NORTH);
        p.add(cw, BorderLayout.EAST);
        return p;
    }

    private void editGroup(SocialClient c, Group g) {
        SocialDialogs.group(this, "Gruppe bearbeiten", new SocialDialogs.GroupValues(g.name, g.description, g.icon, g.color), "Speichern",
                v -> c.updateGroup(g.id, v.name(), v.description(), v.icon(), v.color()));
    }

    private void editRole(SocialClient c, Group g, Role r) {
        SocialDialogs.role(this, "Rolle bearbeiten", new SocialDialogs.RoleValues(r.name, r.icon, r.color, r.manage), r.fixed(), "Speichern",
                v -> c.saveRole(g.id, r.id, v.name(), v.icon(), v.color(), Role.OWNER.equals(r.id) || v.manage()));
    }

    private JPopupMenu groupMenu(SocialClient c, Group g) {
        String me = c.self().uuid;
        JPopupMenu m = new JPopupMenu();
        if (g.canManage(me)) {
            JMenuItem edit = new JMenuItem("Gruppe bearbeiten…");
            edit.addActionListener(e -> editGroup(c, g));
            m.add(edit);
        }
        if (me.equals(g.owner) && g.roles.size() < Rules.ROLES_MAX) {
            JMenuItem role = new JMenuItem("Neue Rolle…");
            role.addActionListener(e -> SocialDialogs.role(this, "Neue Rolle", new SocialDialogs.RoleValues("", "star", Rules.COLORS.get(1), false),
                    false, "Erstellen", v -> c.saveRole(g.id, null, v.name(), v.icon(), v.color(), v.manage())));
            m.add(role);
        }
        ConversationInfo ci = c.conversation(g.conv());
        boolean muted = ci != null && ci.muted;
        JMenuItem mute = new JMenuItem(muted ? "Benachrichtigungen einschalten" : "Stummschalten");
        mute.addActionListener(e -> run(c.setMuted(g.conv(), !muted)));
        m.add(mute);
        m.addSeparator();
        JMenuItem leave = new JMenuItem("Gruppe verlassen");
        leave.setForeground(Theme.FAIL);
        leave.addActionListener(e -> {
            if (SocialDialogs.confirm(this, "Gruppe verlassen", "Du verlässt „" + g.name + "“.", "Verlassen")) {
                run(c.leaveGroup(g.id));
            }
        });
        m.add(leave);
        return m;
    }

    private JPopupMenu memberMenu(SocialClient c, Group g, Member m) {
        String me = c.self().uuid;
        String target = m.profile.uuid;
        boolean owner = me.equals(g.owner);
        JPopupMenu menu = new JPopupMenu();
        if (target.equals(me)) {
            return menu;
        }
        if (c.friend(target) != null) {
            JMenuItem msg = new JMenuItem("Nachricht schreiben");
            msg.addActionListener(e -> select(Conv.dm(me, target)));
            menu.add(msg);
        }
        boolean targetManages = g.canManage(target);
        if (!target.equals(g.owner) && (owner || (g.canManage(me) && !targetManages))) {
            JMenu roles = new JMenu("Rolle");
            for (Role r : g.rolesInOrder()) {
                if (Role.OWNER.equals(r.id) || (!owner && r.manage)) {
                    continue;
                }
                JMenuItem ri = new JMenuItem((r.id.equals(m.role) ? "✓ " : "   ") + r.name);
                ri.addActionListener(e -> run(c.setMemberRole(g.id, target, r.id)));
                roles.add(ri);
            }
            menu.add(roles);
        }
        if (owner) {
            JMenuItem transfer = new JMenuItem("Zum Owner machen");
            transfer.addActionListener(e -> {
                if (SocialDialogs.confirm(this, "Gruppe übergeben", m.profile.name + " wird Owner von „" + g.name + "“. Du wirst Mitglied.", "Übergeben")) {
                    run(c.setMemberRole(g.id, target, Role.OWNER));
                }
            });
            menu.add(transfer);
        }
        if (!target.equals(g.owner) && g.canManage(me) && (owner || !targetManages)) {
            menu.addSeparator();
            JMenuItem kick = new JMenuItem("Aus der Gruppe entfernen");
            kick.setForeground(Theme.FAIL);
            kick.addActionListener(e -> {
                if (SocialDialogs.confirm(this, "Mitglied entfernen", m.profile.name + " wird aus „" + g.name + "“ entfernt.", "Entfernen")) {
                    run(c.kickMember(g.id, target));
                }
            });
            menu.add(kick);
        }
        return menu;
    }

    private JComponent friendInfo(SocialClient c, String uuid) {
        Friend f = c.friend(uuid);
        String name = c.nameOf(uuid);
        Presence p = c.presenceOf(uuid);
        JPanel col = Ui.column();
        col.add(banner(SocialPaint.nameColor(uuid).getRGB() & 0xFFFFFF, (g, x, y, s) -> SocialPaint.face(g, uuid, name, x, y, s)));
        JPanel names = Ui.column();
        names.setBorder(BorderFactory.createEmptyBorder(8, 18, 6, 18));
        names.setAlignmentX(Component.LEFT_ALIGNMENT);
        names.add(Ui.label(name, 20, Font.BOLD, Theme.TEXT));
        names.add(Box.createVerticalStrut(4));
        JLabel status = Ui.label("●  " + SocialPaint.presenceText(p), 13.5f, Font.PLAIN, SocialPaint.presenceColor(p));
        names.add(status);
        col.add(names);
        if (f == null) {
            col.add(padded(Ui.wrapText("Ihr seid keine Freunde mehr. Der Verlauf bleibt lesbar, neue Nachrichten gehen nicht.", 13, Theme.MUTED)));
            return col;
        }
        col.add(section("FREUND SEIT"));
        col.add(padded(Ui.label(DATE.format(SocialPaint.day(f.since)), 13.5f, Font.PLAIN, Theme.TEXT)));
        if (p.state == Presence.State.MULTIPLAYER && !p.detail.isEmpty()) {
            col.add(section("SPIELT AUF"));
            JButton join = SocialDialogs.primary("Server beitreten: " + p.detail);
            join.setIcon(Icons.of(Icons.Kind.PLAY, 16, Color.WHITE));
            join.addActionListener(e -> joinServer(p));
            col.add(padded(join));
        }
        col.add(section("BENACHRICHTIGUNGEN"));
        String conv = Conv.dm(c.self().uuid, uuid);
        ConversationInfo ci = c.conversation(conv);
        ToggleSwitch notify = new ToggleSwitch(ci == null || !ci.muted);
        notify.onChange(on -> run(c.setMuted(conv, !on)));
        col.add(padded(settingRow("Neue Nachrichten melden", "Zähler und Hinweise", notify)));
        List<Group> common = new ArrayList<>();
        for (Group g : c.groups()) {
            if (g.member(uuid) != null) {
                common.add(g);
            }
        }
        if (!common.isEmpty()) {
            col.add(section("GEMEINSAME GRUPPEN"));
            for (Group g : common) {
                col.add(padded(new SocialRow((gr, x, y, s) -> SocialPaint.groupTile(gr, g.icon, g.color, x, y, s), g.name,
                        g.roleOf(uuid).name, SocialPaint.rgb(g.roleOf(uuid).color), () -> select(g.conv())).height(50, 34)));
            }
        }
        col.add(section(""));
        JButton remove = SocialDialogs.danger("Freund entfernen");
        remove.setIcon(Icons.of(Icons.Kind.TRASH, 16, Theme.FAIL));
        remove.addActionListener(e -> {
            if (SocialDialogs.confirm(this, "Freund entfernen", name + " wird aus deiner Freundesliste entfernt.", "Entfernen")) {
                run(c.removeFriend(uuid));
            }
        });
        col.add(padded(remove));
        col.add(Box.createVerticalStrut(16));
        return col;
    }
}
