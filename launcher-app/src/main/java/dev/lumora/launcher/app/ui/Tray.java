package dev.lumora.launcher.app.ui;

import javax.swing.JFrame;
import java.awt.AWTException;
import java.awt.Frame;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.List;

/**
 * Lumora icon in the Windows notification area: "Öffnen" and "Beenden", double click opens the window. Used for
 * "Beim Schließen im Tray bleiben", for starts with {@code --tray} (autostart) and for Lumora Social notifications.
 */
public final class Tray {
    private final JFrame frame;
    private final Runnable quit;
    private final List<Image> icons;
    private TrayIcon icon;

    public Tray(JFrame frame, List<Image> icons, Runnable quit) {
        this.frame = frame;
        this.icons = icons;
        this.quit = quit;
    }

    public static boolean supported() {
        return SystemTray.isSupported();
    }

    public boolean active() {
        return icon != null;
    }

    public void setActive(boolean on) {
        if (!supported() || on == active()) {
            return;
        }
        SystemTray tray = SystemTray.getSystemTray();
        if (!on) {
            tray.remove(icon);
            icon = null;
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Lumora öffnen");
        open.addActionListener(e -> show());
        MenuItem exit = new MenuItem("Beenden");
        exit.addActionListener(e -> javax.swing.SwingUtilities.invokeLater(quit));
        menu.add(open);
        menu.addSeparator();
        menu.add(exit);
        Image img = icons.isEmpty() ? new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB) : icons.get(0);
        TrayIcon t = new TrayIcon(img, "Lumora Client", menu);
        t.setImageAutoSize(true);
        t.addActionListener(e -> show());
        try {
            tray.add(t);
            icon = t;
        } catch (AWTException e) {
            icon = null;
        }
    }

    /** Brings the window back (from the tray or minimized). */
    public void show() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
            frame.toFront();
            frame.requestFocus();
        });
    }

    /** Windows notification (only while the icon is shown). */
    public void notify(String title, String text) {
        if (icon != null) {
            icon.displayMessage(title, text, TrayIcon.MessageType.NONE);
        }
    }
}
