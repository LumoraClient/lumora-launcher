package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.launcher.app.ui.VersionArt;
import dev.lumora.launcher.app.ui.VersionGrid;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Instance card of the grid view: a large picture (the instance's own icon, else Mojang's picture of that update, else
 * the version family's pixel art), the loader and the Minecraft version as badges in the top right corner, below the
 * name and who made it. Hover shows a play button on the picture; a click elsewhere opens the instance.
 */
final class InstanceTile extends JComponent {
    static final int W = 176;
    static final int IMAGE_H = 164;
    static final int H = IMAGE_H + 64;
    private static final Map<String, BufferedImage> ICONS = new ConcurrentHashMap<>();

    private final PageHost host;
    private final Instance instance;
    private final boolean selected;
    private final BufferedImage icon;
    private final Rectangle playBox = new Rectangle();
    private boolean hover;
    private boolean hoverPlay;
    private boolean dropTarget;

    /**
     * @param open   left click on the card
     * @param play   click on the play button
     * @param menu   right click (component, x, y)
     */
    InstanceTile(PageHost host, Instance instance, boolean selected, Runnable open, Runnable play, Consumer<MouseEvent> menu) {
        this.host = host;
        this.instance = instance;
        this.selected = selected;
        this.icon = loadIcon(instance.iconFile());
        setPreferredSize(new Dimension(W, H));
        setMinimumSize(new Dimension(W, H));
        setMaximumSize(new Dimension(W, H));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("<html><b>" + escape(instance.name) + "</b><br>" + escape(InstanceUi.summary(instance)) + "<br>"
                + escape(InstanceUi.lastPlayed(host.ctx(), instance)) + "</html>");
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (playBox.contains(e.getPoint())) {
                    play.run();
                } else {
                    open.run();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.accept(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.accept(e);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                hoverPlay = false;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean p = playBox.contains(e.getPoint());
                if (p != hoverPlay) {
                    hoverPlay = p;
                    repaint();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Outline while something that can be dropped here is over the card. */
    void setDropTarget(boolean on) {
        dropTarget = on;
        repaint();
    }

    static BufferedImage loadIcon(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            String key = file + "@" + Files.getLastModifiedTime(file).toMillis();
            BufferedImage cached = ICONS.get(key);
            if (cached != null) {
                return cached;
            }
            if (Files.size(file) > 4L * 1024 * 1024) {
                return null;
            }
            BufferedImage img = ImageIO.read(file.toFile());
            if (img != null) {
                ICONS.put(key, img);
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    /** "Meine Erstellung", "Von ElocinDev", "Lumora", "Importiert aus Prism" … */
    static String creator(Instance i) {
        if (i.builtIn) {
            return Lang.t("instances.creator.lumora");
        }
        if (i.author != null) {
            return Lang.t("instances.creator.by", i.author);
        }
        if (i.source != null && i.source.startsWith("import:")) {
            return Lang.t("instances.creator.imported", InstanceUi.importSource(i.source));
        }
        if (i.source != null && !"archive".equals(i.source)) {
            return Lang.t("instances.origin.modpack");
        }
        return Lang.t("instances.creator.own");
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int arc = Theme.arc(10);
        RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, arc, arc);
        g.setColor(hover ? Theme.SURFACE_2 : Theme.SURFACE);
        g.fill(shape);
        Shape clip0 = g.getClip();
        g.clip(shape);
        // picture
        g.clipRect(0, 0, w, IMAGE_H);
        String family = VersionGrid.family(instance.minecraftVersion);
        if (icon != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            cover(g, icon, w, IMAGE_H);
        } else {
            Image art = host.versionArt(family, this::repaint);
            if (art != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                cover(g, art, w, IMAGE_H);
            } else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                cover(g, VersionArt.of(family), w, IMAGE_H);
            }
        }
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 110), 0, 46, new Color(0, 0, 0, 0)));
        g.fillRect(0, 0, w, 46);
        // badges, right to left: version, loader
        g.setFont(Theme.font(Font.BOLD, 12.5f));
        FontMetrics fm = g.getFontMetrics();
        int bh = 24;
        int y = 8;
        int vw = fm.stringWidth(instance.minecraftVersion) + 30;
        int vx = w - 8 - vw;
        g.setColor(new Color(20, 22, 26, 215));
        g.fillRoundRect(vx, y, vw, bh, 6, 6);
        Icons.of(Icons.Kind.CUBE, 13, Theme.TEXT).paintIcon(this, g, vx + 7, y + (bh - 13) / 2);
        g.setColor(Theme.TEXT);
        g.drawString(instance.minecraftVersion, vx + 24, y + (bh + fm.getAscent()) / 2 - 2);
        int lx = vx - 4 - bh;
        g.setColor(new Color(20, 22, 26, 215));
        g.fillRoundRect(lx, y, bh, bh, 6, 6);
        paintLoader(g, instance.kind(), lx, y, bh);
        // play button on hover
        int pb = 40;
        playBox.setBounds(w - pb - 10, IMAGE_H - pb - 10, pb, pb);
        if (hover) {
            g.setColor(hoverPlay ? Theme.ACCENT_HOVER : Theme.ACCENT);
            g.fillOval(playBox.x, playBox.y, pb, pb);
            Icons.of(Icons.Kind.PLAY, 18, Color.WHITE).paintIcon(this, g, playBox.x + (pb - 18) / 2 + 1, playBox.y + (pb - 18) / 2);
        } else {
            playBox.setBounds(0, 0, 0, 0);
        }
        g.setClip(clip0);
        g.clip(shape);
        // text
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 14f));
        g.drawString(ellipsize(instance.name, g.getFontMetrics(), w - 24), 12, IMAGE_H + 24);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 12.5f));
        g.drawString(ellipsize(creator(instance), g.getFontMetrics(), w - 24), 12, IMAGE_H + 46);
        g.setClip(clip0);
        // outline
        Color line = dropTarget ? Theme.ACCENT_2 : selected ? Theme.ACCENT : hover ? Theme.alpha(Theme.TEXT, 70) : Theme.BORDER;
        g.setStroke(new BasicStroke(selected || dropTarget ? 2f : 1f));
        g.setColor(line);
        g.draw(shape);
        g.dispose();
    }

    /** Loader badge: Lumora star, Vanilla cube, else the loader's initials in its colour. */
    static void paintLoader(Graphics2D g, InstanceKind k, int x, int y, int size) {
        Icon icon = switch (k) {
            case LUMORA -> Icons.of(Icons.Kind.R_STAR, size - 10, InstanceUi.kindColor(k).brighter());
            case VANILLA -> Icons.of(Icons.Kind.CUBE, size - 10, InstanceUi.kindColor(k).brighter());
            default -> null;
        };
        if (icon != null) {
            icon.paintIcon(null, g, x + 5, y + 5);
            return;
        }
        String t = switch (k) {
            case FABRIC -> "F";
            case QUILT -> "Q";
            case FORGE -> "FG";
            case NEOFORGE -> "NF";
            default -> "?";
        };
        g.setFont(Theme.font(Font.BOLD, t.length() > 1 ? 10.5f : 13f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(InstanceUi.kindColor(k));
        g.drawString(t, x + (size - fm.stringWidth(t)) / 2, y + (size + fm.getAscent()) / 2 - 2);
    }

    private static void cover(Graphics2D g, Image img, int w, int h) {
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return;
        }
        double s = Math.max((double) w / iw, (double) h / ih);
        int dw = (int) Math.ceil(iw * s);
        int dh = (int) Math.ceil(ih * s);
        g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }

    static String ellipsize(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) {
            return s;
        }
        String e = "…";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + e) > max) {
            end--;
        }
        return s.substring(0, end) + e;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
