package dev.lumora.launcher.app;

import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.PlayService;
import dev.lumora.launcher.core.auth.AuthException;
import dev.lumora.launcher.core.auth.MicrosoftAuthenticator;
import dev.lumora.launcher.core.auth.MinecraftSession;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.util.Log;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * The interactive Microsoft sign-in as the user sees it:
 * <ol>
 *   <li>if no Azure client ID is configured yet: a one-time setup dialog,</li>
 *   <li>"normal" sign-in: Microsoft's login page opens in the browser, the launcher receives the result
 *       automatically (authorization code + PKCE, loopback redirect),</li>
 *   <li>fallback on request: device code ("Mit Code anmelden"), e.g. when the browser runs on another device.</li>
 * </ol>
 */
final class LoginFlow {
    private final JFrame owner;
    private final AppContext ctx;
    private final AtomicBoolean switchToCode = new AtomicBoolean();
    private final AtomicBoolean userCancelled = new AtomicBoolean();
    private final AtomicReference<URI> lastUrl = new AtomicReference<>();
    private final DeviceCodeDialog codeDialog;
    private JDialog browserDialog;

    LoginFlow(JFrame owner, AppContext ctx) {
        this.owner = owner;
        this.ctx = ctx;
        this.codeDialog = new DeviceCodeDialog(owner, () -> userCancelled.set(true));
    }

    BooleanSupplier cancelled() {
        return userCancelled::get;
    }

    void cancel() {
        userCancelled.set(true);
    }

    /**
     * Ensures a client ID exists when an interactive login or a token refresh may be needed. EDT only.
     *
     * @return false if the user closed the setup dialog
     */
    boolean ensureClientId() {
        if (PlayService.effectiveClientId(ctx.config) != null) {
            return true;
        }
        MinecraftSession stored = ctx.play.storedSession();
        if (stored != null && stored.accessTokenValid(Instant.now())) {
            return true;
        }
        return showClientIdSetup(owner, ctx);
    }

    /** The interactive part used by {@link PlayService}; runs on the worker thread. */
    PlayService.InteractiveLogin interactive() {
        return (auth, cancelled) -> {
            BooleanSupplier stopBrowser = () -> cancelled.getAsBoolean() || switchToCode.get();
            try {
                SwingUtilities.invokeLater(this::showBrowserDialog);
                return auth.signInWithBrowser(url -> {
                    lastUrl.set(url);
                    openBrowser(url);
                }, stopBrowser);
            } catch (AuthException e) {
                if (!(switchToCode.get() && e.reason() == AuthException.Reason.CANCELLED)) {
                    throw e;
                }
            } finally {
                SwingUtilities.invokeLater(this::closeBrowserDialog);
            }
            Log.info("Switching to device code sign-in");
            return auth.signIn(prompt -> SwingUtilities.invokeLater(() -> codeDialog.show(prompt)), cancelled);
        };
    }

    void closeDialogs() {
        closeBrowserDialog();
        codeDialog.close();
    }

    private void showBrowserDialog() {
        closeBrowserDialog();
        browserDialog = new JDialog(owner, "Mit Microsoft anmelden", false);
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JLabel title = new JLabel("Anmeldung im Browser");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel text = new JLabel("<html><body style='width:400px'>Im Browser hat sich die Anmeldeseite von "
                + "<b>Microsoft</b> geöffnet. Melde dich dort mit dem Konto an, dem Minecraft gehört. "
                + "Danach kehrt der Launcher automatisch zurück.<br><br>Dein Passwort gibst du nur bei Microsoft ein, "
                + "der Launcher sieht es nie.</body></html>");
        p.add(title, BorderLayout.NORTH);
        p.add(text, BorderLayout.CENTER);
        JButton again = new JButton("Browser erneut öffnen");
        again.addActionListener(e -> {
            URI u = lastUrl.get();
            if (u != null) {
                openBrowser(u);
            }
        });
        JButton code = new JButton("Mit Code anmelden");
        code.setToolTipText("Falls sich kein Browser öffnet oder du dich auf einem anderen Gerät anmelden möchtest");
        code.addActionListener(e -> {
            switchToCode.set(true);
            closeBrowserDialog();
        });
        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> {
            userCancelled.set(true);
            closeBrowserDialog();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(again);
        buttons.add(code);
        buttons.add(cancel);
        p.add(buttons, BorderLayout.SOUTH);
        browserDialog.setContentPane(p);
        browserDialog.pack();
        browserDialog.setLocationRelativeTo(owner);
        browserDialog.setVisible(true);
    }

    private void closeBrowserDialog() {
        if (browserDialog != null) {
            browserDialog.dispose();
            browserDialog = null;
        }
    }

    private static void openBrowser(URI url) {
        if (!DeviceCodeDialog.isTrusted(url.toString())) {
            Log.warn("Refusing to open untrusted sign-in URL host: " + url.getHost());
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(url);
                return;
            }
        } catch (IOException | UnsupportedOperationException e) {
            Log.warn("Desktop.browse failed: " + e.getMessage());
        }
        try {
            // Fallback for Windows systems where Desktop.browse is unavailable
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url.toString()).start();
        } catch (IOException e) {
            Log.warn("Could not open browser: " + e.getMessage());
        }
    }

    /** One-time setup of the Azure application (client) ID. Returns true when a valid ID was saved. EDT only. */
    static boolean showClientIdSetup(JFrame owner, AppContext ctx) {
        JDialog d = new JDialog(owner, "Microsoft-Anmeldung einrichten", true);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JLabel intro = new JLabel("<html><body style='width:520px'>"
                + "<b>Einmalige Einrichtung.</b> Microsoft erlaubt die Minecraft-Anmeldung nur über eine "
                + "registrierte App. Jeder Launcher braucht dafür eine eigene <b>Anwendungs-ID (Client-ID)</b>, "
                + "die Mojang für Minecraft freigeschaltet hat.<br><br>"
                + "<b>So bekommst du eine:</b><ol>"
                + "<li>Im Azure-Portal mit deinem Microsoft-Konto anmelden → <i>App-Registrierungen</i> → <i>Neue Registrierung</i>.</li>"
                + "<li>Kontotyp: <i>Nur persönliche Microsoft-Konten</i>.</li>"
                + "<li>Umleitungs-URI: Plattform <i>Öffentlicher Client/nativ (mobil &amp; Desktop)</i>, Wert <code>http://localhost</code>.</li>"
                + "<li>Unter <i>Authentifizierung</i>: <i>Öffentliche Clientflows zulassen</i> = Ja.</li>"
                + "<li>Freischaltung für die Minecraft-API beantragen (Infos: aka.ms/AppRegInfo). Bis zur Freigabe "
                + "lehnt Minecraft die Anmeldung mit „HTTP 403“ ab.</li>"
                + "<li>Die <i>Anwendungs-ID</i> (Format xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx) hier eintragen.</li>"
                + "</ol>Die ID ist kein Passwort. Einmal eingetragen, meldest du dich danach ganz normal im Browser an."
                + "</body></html>");
        p.add(intro, BorderLayout.NORTH);
        JTextField field = new JTextField(ctx.config.microsoftClientId != null ? ctx.config.microsoftClientId : "");
        field.putClientProperty("JTextField.placeholderText", "Anwendungs-ID (Client-ID)");
        JPanel mid = new JPanel(new GridLayout(2, 1, 0, 6));
        mid.add(new JLabel("Anwendungs-ID (Client-ID):"));
        mid.add(field);
        p.add(mid, BorderLayout.CENTER);
        boolean[] saved = {false};
        JButton portal = new JButton("Azure-Portal öffnen");
        portal.addActionListener(e -> openBrowser(URI.create(
                "https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade")));
        JButton info = new JButton("Infos zur Freischaltung");
        info.addActionListener(e -> openBrowser(URI.create("https://aka.ms/AppRegInfo")));
        JButton save = new JButton("Speichern & anmelden");
        save.addActionListener(e -> {
            String id = field.getText().strip();
            if (!LauncherConfig.isValidClientId(id)) {
                JOptionPane.showMessageDialog(d, "Das ist keine gültige Anwendungs-ID.\nFormat: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        BuildInfo.NAME, JOptionPane.WARNING_MESSAGE);
                return;
            }
            ctx.config.microsoftClientId = id;
            try {
                ctx.saveConfig();
            } catch (IOException ex) {
                Log.warn("Could not save client id", ex);
            }
            saved[0] = true;
            d.dispose();
        });
        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(portal);
        buttons.add(info);
        buttons.add(save);
        buttons.add(cancel);
        p.add(buttons, BorderLayout.SOUTH);
        d.setContentPane(p);
        d.getRootPane().setDefaultButton(save);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return saved[0];
    }
}
