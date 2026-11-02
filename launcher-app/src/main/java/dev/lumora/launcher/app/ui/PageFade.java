package dev.lumora.launcher.app.ui;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.plaf.LayerUI;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Page change transition: the new page fades in and glides up a few pixels (about a quarter second). Wrap the page
 * container with {@link #wrap} and call {@link #play()} after switching the page.
 */
public final class PageFade extends LayerUI<JComponent> {
    private static final int MILLIS = 260;
    private static final int RISE = 14;

    private final Motion.Tween progress = new Motion.Tween(1, MILLIS);
    private JLayer<JComponent> layer;

    public JLayer<JComponent> wrap(JComponent pages) {
        layer = new JLayer<>(pages, this);
        layer.setOpaque(false);
        return layer;
    }

    public void play() {
        if (layer == null || Theme.reduceMotion) {
            return;
        }
        progress.jump(0);
        progress.set(1);
        Motion.during(layer, MILLIS + 40);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        double v = progress.value();
        if (v >= 0.999) {
            super.paint(g, c);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(0, (1 - v) * RISE);
        g2.setComposite(AlphaComposite.SrcOver.derive((float) (0.15 + 0.85 * v)));
        super.paint(g2, c);
        g2.dispose();
    }
}
