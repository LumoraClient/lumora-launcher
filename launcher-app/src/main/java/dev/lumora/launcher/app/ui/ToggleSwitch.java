package dev.lumora.launcher.app.ui;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** iOS-style on/off switch (keyboard: space toggles). */
public final class ToggleSwitch extends JComponent {
    private boolean on;
    private final List<Consumer<Boolean>> listeners = new ArrayList<>();

    public ToggleSwitch(boolean on) {
        this.on = on;
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(44, 24));
        setMaximumSize(new Dimension(44, 24));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isEnabled()) {
                    toggle();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (isEnabled() && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    toggle();
                }
            }
        });
    }

    public boolean isOn() {
        return on;
    }

    public void onChange(Consumer<Boolean> l) {
        listeners.add(l);
    }

    private void toggle() {
        on = !on;
        repaint();
        for (Consumer<Boolean> l : listeners) {
            l.accept(on);
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (!isEnabled()) {
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
        }
        int w = 44;
        int h = 24;
        int y = (getHeight() - h) / 2;
        g.setColor(on ? Theme.ACCENT : Theme.BORDER);
        g.fillRoundRect(0, y, w, h, Theme.arc(h), Theme.arc(h));
        g.setColor(on ? java.awt.Color.WHITE : Theme.MUTED);
        int d = h - 6;
        if (Theme.pixelStyle) {
            g.fillRect(on ? w - d - 3 : 3, y + 3, d, d);
        } else {
            g.fillOval(on ? w - d - 3 : 3, y + 3, d, d);
        }
        if (isFocusOwner()) {
            g.setColor(Theme.alpha(Theme.ACCENT_HOVER, 120));
            g.drawRoundRect(0, y, w - 1, h - 1, Theme.arc(h), Theme.arc(h));
        }
        g.dispose();
    }
}
