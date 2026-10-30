package dev.lumora.launcher.app.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Rounded surface with optional accent outline (selected state). */
public class Card extends JPanel {
    private Color fill = Theme.SURFACE;
    private Color outline = Theme.BORDER;
    private int radius = Theme.RADIUS;

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    }

    public Card fill(Color c) {
        this.fill = c;
        repaint();
        return this;
    }

    public Card outline(Color c) {
        this.outline = c;
        repaint();
        return this;
    }

    public Card radius(int r) {
        this.radius = r;
        return this;
    }

    public Card padding(int top, int left, int bottom, int right) {
        setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        if (outline != null) {
            g.setColor(outline);
            g.setStroke(new BasicStroke(outline == Theme.ACCENT ? 2f : 1f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
        g.dispose();
        super.paintComponent(g0);
    }
}
