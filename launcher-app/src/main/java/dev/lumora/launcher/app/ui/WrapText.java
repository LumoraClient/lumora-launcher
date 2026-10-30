package dev.lumora.launcher.app.ui;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.text.View;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Read-only text that wraps to whatever width the layout gives it and reports the matching height,
 * so BoxLayout/GridBag rows grow to show every line (a plain JTextArea reports a one-line height until
 * it has been sized once).
 */
public final class WrapText extends JTextArea {
    private int lastWidth = -1;

    public WrapText(String text, float size, Color color) {
        super(text);
        setLineWrap(true);
        setWrapStyleWord(true);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        setFont(Theme.font(Font.PLAIN, size));
        setForeground(color);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() != lastWidth) {
                    lastWidth = getWidth();
                    revalidate();
                }
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        int w = getWidth();
        if (w <= 0) {
            return new Dimension(1, getFontMetrics(getFont()).getHeight());
        }
        View root = getUI().getRootView(this);
        root.setSize(w, Integer.MAX_VALUE);
        int h = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
        return new Dimension(w, h + getInsets().top + getInsets().bottom);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(1, getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
