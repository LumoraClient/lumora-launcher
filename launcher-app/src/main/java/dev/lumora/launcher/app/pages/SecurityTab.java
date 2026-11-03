package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Card;
import dev.lumora.launcher.app.ui.DataTable;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.Ui;
import dev.lumora.launcher.core.auth.AccountStore;
import dev.lumora.launcher.core.stats.PlayStats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * "Konto → Sicherheit": two-factor authentication and the sessions on this PC. Lumora has no account of its own, it
 * signs in with Microsoft only, so 2FA is set up in the Microsoft account (the buttons open Microsoft's public account
 * pages in the browser). The sessions table lists every account signed in on this PC and can sign each one out.
 */
final class SecurityTab extends JPanel {
    /** Microsoft account: additional security options (two-step verification, authenticator app). */
    static final String MS_TWO_STEP = "https://account.live.com/proofs/manage/additional";
    /** Microsoft account: recent sign-in activity. */
    static final String MS_ACTIVITY = "https://account.live.com/Activity";

    private final PageHost host;
    private final Consumer<AccountStore.Account> signOut;
    private final Consumer<AccountStore.Account> use;
    private final DataTable sessions = new DataTable(List.of("Name", "Typ", "Zuletzt aktiv", "Erstmals genutzt"), 1.5, 1.1, 1, 1);

    SecurityTab(PageHost host, Consumer<AccountStore.Account> signOut, Consumer<AccountStore.Account> use) {
        super(new BorderLayout());
        this.host = host;
        this.signOut = signOut;
        this.use = use;
        setOpaque(false);

        JPanel col = Ui.column();
        col.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        Card card = new Card(new BorderLayout()).calm();
        card.padding(26, 28, 26, 28);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in = Ui.column();

        in.add(Ui.label("Zwei-Faktor-Authentifizierung", 19, Font.BOLD, Theme.TEXT));
        in.add(Box.createVerticalStrut(6));
        in.add(Ui.wrapText("Lumora meldet sich nur über dein Microsoft-Konto an. Den zweiten Schritt richtest du deshalb direkt bei "
                + "Microsoft ein, dann schützt er auch Minecraft.", 13.5f, Theme.MUTED));
        in.add(Box.createVerticalStrut(18));
        JButton setUp = new JButton("2FA bei Microsoft einrichten");
        setUp.putClientProperty("JButton.buttonType", "roundRect");
        setUp.setBackground(Theme.ACCENT);
        setUp.setForeground(Theme.onAccent());
        setUp.setFont(Theme.font(Font.BOLD, 13.5f));
        setUp.setFocusable(false);
        setUp.addActionListener(e -> host.browse(MS_TWO_STEP));
        in.add(line("Authenticator-App", "Microsoft Authenticator oder eine andere App mit Einmal-Codes", setUp));

        in.add(Box.createVerticalStrut(30));
        JButton activity = new JButton("Aktivität bei Microsoft");
        activity.putClientProperty("JButton.buttonType", "roundRect");
        activity.setFocusable(false);
        activity.addActionListener(e -> host.browse(MS_ACTIVITY));
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel ht = Ui.column();
        ht.add(Ui.label("Sitzungen", 19, Font.BOLD, Theme.TEXT));
        ht.add(Box.createVerticalStrut(6));
        ht.add(Ui.label("Alles, was auf diesem PC Zugriff auf deine Konten hat.", 13.5f, Font.PLAIN, Theme.MUTED));
        head.add(ht, BorderLayout.CENTER);
        head.add(centered(activity), BorderLayout.EAST);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        in.add(head);
        in.add(Box.createVerticalStrut(16));
        in.add(sessions);
        in.add(Box.createVerticalStrut(12));
        in.add(Ui.wrapText("Die Anmeldung jedes Kontos liegt einzeln verschlüsselt (Windows DPAPI) auf diesem PC. Passwörter "
                + "speichert der Launcher nie. Geräte außerhalb dieses PCs siehst du unter „Aktivität bei Microsoft“.", 12.5f, Theme.MUTED));

        card.add(in, BorderLayout.CENTER);
        col.add(card);
        add(Ui.scroll(col), BorderLayout.CENTER);
    }

    /** Title and hint on the left, a control on the right. */
    private static JComponent line(String title, String hint, JComponent control) {
        JPanel p = new JPanel(new BorderLayout(16, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel t = Ui.column();
        t.add(Ui.label(title, 15, Font.BOLD, Theme.TEXT));
        t.add(Box.createVerticalStrut(3));
        t.add(Ui.label(hint, 12.5f, Font.PLAIN, Theme.MUTED));
        p.add(t, BorderLayout.CENTER);
        p.add(centered(control), BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JComponent centered(JComponent c) {
        JPanel w = new JPanel(new GridBagLayout());
        w.setOpaque(false);
        w.add(c);
        return w;
    }

    void refresh() {
        sessions.clear();
        AccountStore store = host.ctx().accounts;
        List<AccountStore.Account> list = store.list();
        Optional<AccountStore.Account> active = store.activeAccount();
        Instant now = Instant.now();
        var history = host.ctx().play.history();
        if (list.isEmpty()) {
            sessions.empty("Auf diesem PC ist kein Konto angemeldet.");
        }
        for (AccountStore.Account a : list) {
            boolean isActive = active.map(x -> x.uuid().equals(a.uuid())).orElse(false);
            Instant first = a.added();
            Instant last = a.lastUsed();
            Instant[] span = history.span(a.uuid());
            if (span != null) {
                first = first == null || span[0].isBefore(first) ? span[0] : first;
                last = last == null || span[1].isAfter(last) ? span[1] : last;
            }
            List<DataTable.Action> actions = new ArrayList<>();
            if (!isActive) {
                actions.add(new DataTable.Action("Verwenden", () -> use.accept(a), false));
            }
            actions.add(new DataTable.Action("Abmelden", () -> signOut.accept(a), true));
            sessions.addRow(List.of(
                    DataTable.twoLine(isActive ? "(aktives Konto)" : null, a.username()),
                    DataTable.text("Lumora Launcher", false),
                    DataTable.text(PlayStats.ago(last, now), false),
                    DataTable.text(PlayStats.ago(first, now), false)), DataTable.menu(actions));
        }
        revalidate();
        repaint();
    }
}
