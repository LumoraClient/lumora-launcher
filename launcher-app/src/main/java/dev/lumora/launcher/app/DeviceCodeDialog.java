package dev.lumora.launcher.app;

import dev.lumora.launcher.core.auth.MicrosoftAuthenticator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Shows the Microsoft device code. The user signs in on Microsoft's own page in the browser; the launcher
 * never sees the password.
 */
final class DeviceCodeDialog {
    /** Only Microsoft sign-in hosts are opened automatically. */
    private static final Set<String> ALLOWED_HOSTS = Set.of("microsoft.com", "www.microsoft.com", "login.microsoft.com",
            "login.live.com", "aka.ms", "microsoftonline.com", "login.microsoftonline.com", "portal.azure.com");

    private final JFrame owner;
    private final Runnable onCancel;
    private JDialog dialog;

    DeviceCodeDialog(JFrame owner, Runnable onCancel) {
        this.owner = owner;
        this.onCancel = onCancel;
    }

    void show(MicrosoftAuthenticator.DeviceCodePrompt prompt) {
        close();
        dialog = new JDialog(owner, "Mit Microsoft anmelden", false);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JLabel intro = new JLabel("<html><body style='width:380px'>Öffne <b>" + prompt.verificationUri()
                + "</b> und gib diesen Code ein. Melde dich dort mit dem Microsoft-Konto an, dem Minecraft gehört.</body></html>");
        JLabel code = new JLabel(prompt.userCode(), SwingConstants.CENTER);
        code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
        JPanel center = new JPanel(new GridLayout(2, 1, 0, 8));
        center.add(intro);
        center.add(code);
        p.add(center, BorderLayout.CENTER);

        JButton open = new JButton("Code kopieren & Browser öffnen");
        open.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(prompt.userCode()), null);
            openIfTrusted(prompt.verificationUri());
        });
        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> {
            onCancel.run();
            close();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(open);
        buttons.add(cancel);
        p.add(buttons, BorderLayout.SOUTH);
        JLabel waiting = new JLabel("Warte auf Bestätigung … (gültig " + prompt.expiresIn().toMinutes() + " Minuten)");
        p.add(waiting, BorderLayout.NORTH);
        dialog.setContentPane(p);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    static boolean isTrusted(String uri) {
        try {
            URI u = URI.create(uri);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(u.getScheme()) && (ALLOWED_HOSTS.contains(host) || host.endsWith(".microsoft.com")
                    || host.endsWith(".microsoftonline.com") || host.endsWith(".live.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void openIfTrusted(String uri) {
        if (isTrusted(uri) && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(uri));
            } catch (Exception ignored) {
                // the URL is visible in the dialog; the user can open it manually
            }
        }
    }

    void close() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }
}
