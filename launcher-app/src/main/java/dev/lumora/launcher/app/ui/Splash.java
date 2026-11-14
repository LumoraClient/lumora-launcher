package dev.lumora.launcher.app.ui;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.List;

/**
 * Start screen while the launcher loads: logo, progress bar, the current step with "Schritt n von m · etwa x s" and the
 * list of steps (check mark when done, spinner for the running one). All methods may be called from any thread.
 */
public final class Splash {
    private final JWindow window = new JWindow();
    private final List<String> steps;
    private final Image logo;
    private final long startedAt = System.currentTimeMillis();
    private volatile int current = -1;
    private volatile double spin;
    private final Timer spinner;
    private final View view = new View();

    public Splash(List<String> steps, Image logo) {
        this.steps = List.copyOf(steps);
        this.logo = logo;
        window.setContentPane(view);
        window.setSize(560, 380);
        window.setLocationRelativeTo(null);
        window.setIconImage(logo);
        spinner = new Timer(40, e -> {
            spin += Theme.reduceMotion ? 0 : 0.18;
            view.repaint();
        });
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            window.setVisible(true);
            spinner.start();
        });
    }

    /** Marks steps before index as done and index as running. */
    public void step(int index) {
        current = index;
        SwingUtilities.invokeLater(view::repaint);
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            spinner.stop();
            window.dispose();
        });
    }

    private final class View extends JComponent {
        View() {
            setPreferredSize(new Dimension(560, 380));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth();
            int h = getHeight();
            g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.BG, Theme.ACCENT, 0.10), w, h, Theme.BG));
            g.fillRect(0, 0, w, h);
            g.setColor(Theme.BORDER);
            g.drawRect(0, 0, w - 1, h - 1);
            int idx = Math.max(0, current);
            int total = steps.size();

            // logo + name
            int y = 46;
            if (logo != null) {
                g.drawImage(logo, w / 2 - 92, y - 4, 40, 40, null);
            }
            g.setFont(Theme.display(24f, Font.BOLD, 26f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            g.drawString("Lumora", w / 2 - 44, y + 26);
            int lx = w / 2 - 44 + fm.stringWidth("Lumora ");
            g.setFont(Theme.font(Font.PLAIN, 26f));
            g.setColor(Theme.MUTED);
            g.drawString("Launcher", lx, y + 26);

            // progress bar
            int bx = 60;
            int by = 112;
            int bw = w - 120;
            double done = total == 0 ? 0 : Math.min(1, (idx + 0.5) / total);
            g.setColor(Theme.SURFACE_2);
            Theme.fillRound(g, bx, by, bw, 5, Theme.arc(5), Theme.arc(5));
            g.setColor(Theme.ACCENT);
            Theme.fillRound(g, bx, by, (int) (bw * done), 5, Theme.arc(5), Theme.arc(5));

            // current step and estimate
            String now = idx < total ? steps.get(idx) : "";
            g.setFont(Theme.font(Font.BOLD, 15f));
            fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            g.drawString(now, (w - fm.stringWidth(now)) / 2, 146);
            long elapsed = System.currentTimeMillis() - startedAt;
            long perStep = idx == 0 ? 1200 : elapsed / Math.max(1, idx);
            long left = Math.max(1, Math.round(perStep * (total - idx - 0.5) / 1000.0));
            String sub = "Schritt " + (idx + 1) + " von " + total + " · etwa " + left + " s";
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            fm = g.getFontMetrics();
            g.setColor(Theme.MUTED);
            g.drawString(sub, (w - fm.stringWidth(sub)) / 2, 166);

            // step list
            int sy = 198;
            g.setFont(Theme.font(Font.PLAIN, 13f));
            int listX = w / 2 - 90;
            for (int i = 0; i < total; i++) {
                int cy = sy + i * 28;
                if (i < idx) {
                    g.setColor(Theme.ACCENT);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawPolyline(new int[]{listX, listX + 4, listX + 12}, new int[]{cy - 4, cy, cy - 9}, 3);
                    g.setColor(Theme.MUTED);
                    g.setFont(Theme.font(Font.PLAIN, 13f));
                } else if (i == idx) {
                    g.setColor(Theme.TEXT);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    double start = Math.toDegrees(spin) % 360;
                    g.draw(new Arc2D.Double(listX - 1, cy - 12, 14, 14, -start, 270, Arc2D.OPEN));
                    g.setFont(Theme.font(Font.BOLD, 13f));
                } else {
                    g.setColor(Theme.alpha(Theme.MUTED, 110));
                    g.fillOval(listX + 3, cy - 7, 6, 6);
                    g.setColor(Theme.alpha(Theme.MUTED, 150));
                    g.setFont(Theme.font(Font.PLAIN, 13f));
                }
                if (i != idx && i >= idx) {
                    g.setColor(Theme.alpha(Theme.MUTED, 150));
                } else if (i == idx) {
                    g.setColor(Theme.TEXT);
                } else {
                    g.setColor(Theme.MUTED);
                }
                g.drawString(steps.get(i), listX + 26, cy);
            }
            g.dispose();
        }
    }
}
