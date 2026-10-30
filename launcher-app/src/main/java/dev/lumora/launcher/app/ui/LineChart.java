package dev.lumora.launcher.app.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small line chart in the launcher theme: x = runs in order, one or more series, y from 0 to a rounded maximum.
 * Hovering shows the values of the nearest run.
 */
public final class LineChart extends JComponent {
    public record Series(String name, Color color, double[] values) {
    }

    private List<Series> series = List.of();
    private List<String> labels = List.of();
    private String unit = "";
    private int hover = -1;

    public LineChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(10, 220));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int n = labels.size();
                if (n == 0) {
                    return;
                }
                double step = n > 1 ? (getWidth() - 60.0) / (n - 1) : 0;
                int i = n > 1 ? (int) Math.round((e.getX() - 46.0) / step) : 0;
                hover = Math.clamp(i, 0, n - 1);
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseMotionListener(m);
        addMouseListener(m);
    }

    public void setData(List<String> labels, List<Series> series, String unit) {
        this.labels = List.copyOf(labels);
        this.series = List.copyOf(series);
        this.unit = unit;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int left = 46;
        int right = 14;
        int top = 26;
        int bottom = 26;
        int w = getWidth() - left - right;
        int h = getHeight() - top - bottom;
        int n = labels.size();
        double max = 1;
        for (Series s : series) {
            for (double v : s.values) {
                max = Math.max(max, v);
            }
        }
        max = niceCeil(max * 1.1);
        g.setFont(Theme.font(Font.PLAIN, 11f));
        // grid + y labels
        for (int i = 0; i <= 4; i++) {
            int y = top + h - (int) Math.round(h * i / 4.0);
            g.setColor(Theme.alpha(Theme.BORDER, 160));
            g.drawLine(left, y, left + w, y);
            g.setColor(Theme.MUTED);
            String t = String.format(Locale.GERMANY, "%.0f", max * i / 4);
            g.drawString(t, left - 8 - g.getFontMetrics().stringWidth(t), y + 4);
        }
        g.drawString(unit, 4, top - 10);
        // legend
        int lx = left;
        for (Series s : series) {
            g.setColor(s.color);
            g.fillRoundRect(lx, top - 19, 10, 10, 4, 4);
            g.setColor(Theme.TEXT);
            g.drawString(s.name, lx + 15, top - 10);
            lx += 25 + g.getFontMetrics().stringWidth(s.name);
        }
        if (n == 0) {
            g.dispose();
            return;
        }
        double step = n > 1 ? (double) w / (n - 1) : 0;
        for (Series s : series) {
            Path2D line = new Path2D.Double();
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(n, s.values.length); i++) {
                double x = left + (n > 1 ? step * i : w / 2.0);
                double y = top + h - h * (s.values[i] / max);
                pts.add(new double[]{x, y});
                if (i == 0) {
                    line.moveTo(x, y);
                } else {
                    line.lineTo(x, y);
                }
            }
            // soft fill under the line
            if (pts.size() > 1) {
                Path2D area = new Path2D.Double(line);
                area.lineTo(pts.getLast()[0], top + h);
                area.lineTo(pts.getFirst()[0], top + h);
                area.closePath();
                g.setColor(Theme.alpha(s.color, 28));
                g.fill(area);
            }
            g.setColor(s.color);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
            for (double[] p : pts) {
                g.fillOval((int) p[0] - 3, (int) p[1] - 3, 7, 7);
            }
        }
        // x labels: first, last, and hover
        g.setColor(Theme.MUTED);
        g.drawString(labels.getFirst(), left, top + h + 18);
        if (n > 1) {
            String last = labels.getLast();
            g.drawString(last, left + w - g.getFontMetrics().stringWidth(last), top + h + 18);
        }
        if (hover >= 0 && hover < n) {
            int x = (int) Math.round(left + (n > 1 ? step * hover : w / 2.0));
            g.setColor(Theme.alpha(Theme.TEXT, 70));
            g.drawLine(x, top, x, top + h);
            StringBuilder sb = new StringBuilder(labels.get(hover));
            for (Series s : series) {
                if (hover < s.values.length) {
                    sb.append("   ").append(s.name).append(": ").append(String.format(Locale.GERMANY, "%.0f", s.values[hover]));
                }
            }
            String t = sb.toString();
            int tw = g.getFontMetrics().stringWidth(t) + 16;
            int bx = Math.clamp(x - tw / 2, left, left + w - tw);
            g.setColor(Theme.SURFACE_2);
            g.fillRoundRect(bx, top + 4, tw, 22, 10, 10);
            g.setColor(Theme.TEXT);
            g.drawString(t, bx + 8, top + 19);
        }
        g.dispose();
    }

    static double niceCeil(double v) {
        double mag = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) {
            if (m * mag >= v) {
                return m * mag;
            }
        }
        return 10 * mag;
    }
}
