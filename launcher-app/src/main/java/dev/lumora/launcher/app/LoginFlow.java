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
}
