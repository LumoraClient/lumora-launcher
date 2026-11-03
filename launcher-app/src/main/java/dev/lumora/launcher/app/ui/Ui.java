package dev.lumora.launcher.app.ui;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Small, shared building blocks of the launcher pages (all in the launcher's dark theme). */
public final class Ui {
    private Ui() {
    }

    public static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    public static JPanel row(int gap) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        // FlowLayout also puts the gap before the first item; pull it back so rows align with the text above
        p.setBorder(BorderFactory.createEmptyBorder(0, -gap, 0, 0));
        return p;
    }

    public static JLabel label(String text, float size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public static JComponent pageTitle(String title, String subtitle) {
        JPanel p = column();
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label(title, 24, Font.BOLD, Theme.TEXT));
        p.add(Box.createVerticalStrut(4));
        p.add(label(subtitle, 13.5f, Font.PLAIN, Theme.MUTED));
        return p;
    }

    public static JComponent sectionTitle(String title, String hint) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label(title, 17, Font.BOLD, Theme.TEXT));
        if (hint != null) {
            p.add(Box.createHorizontalStrut(12));
            p.add(label(hint, 13, Font.PLAIN, Theme.MUTED));
        }
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return p;
    }

    public static JComponent chip(String text) {
        return chip(text, Theme.ACCENT, Theme.ACCENT_TEXT);
    }

    /** Rounded tag; {@code tint} is the background (drawn translucent), {@code fg} the text color. */
    public static JComponent chip(String text, Color tint, Color fg) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.alpha(tint, 50));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.arc(getHeight()), Theme.arc(getHeight()));
                g.dispose();
                super.paintComponent(g0);
            }
        };
        l.setFont(Theme.font(Font.BOLD, 11.5f));
        l.setForeground(fg);
        l.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return l;
    }

    public static JTextArea wrapText(String text, float size, Color color) {
        return new WrapText(text, size, color);
    }

    /** Page content that always fits the viewport width (only scrolls vertically). */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return r.height - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public static JScrollPane scroll(JComponent content) {
        JPanel holder = new WidthTrackingPanel();
        holder.add(content, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    /** Image scaled into a {@code size} x {@code size} square (keeps aspect, smooth). Null if not decodable. */
    public static ImageIcon icon(byte[] png, int size) {
        if (png == null) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            return img == null ? null : new ImageIcon(fit(img, size, size, true));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static ImageIcon icon(Path file, int w, int h, boolean contain) {
        try {
            if (file == null || Files.size(file) > 32L * 1024 * 1024) {
                return null;
            }
            BufferedImage img = ImageIO.read(file.toFile());
            return img == null ? null : new ImageIcon(fit(img, w, h, contain));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Scales into w x h; {@code contain} letterboxes, otherwise the image is cropped to fill. */
    static Image fit(BufferedImage img, int w, int h, boolean contain) {
        double s = contain ? Math.min((double) w / img.getWidth(), (double) h / img.getHeight())
                : Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int sw = Math.max(1, (int) Math.round(img.getWidth() * s));
        int sh = Math.max(1, (int) Math.round(img.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // pixel art (mod icons, world icons) stays crisp when enlarged, photos are smoothed when shrunk
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, s >= 1
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g.dispose();
        return out;
    }

    /** Rounded square with the first letter of {@code name}, used when a mod has no icon. */
    public static JComponent letterTile(String name, int size, Color tint) {
        String letter = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        JComponent c = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(Theme.alpha(tint, 55));
                g.fillRoundRect(0, 0, size, size, 12, 12);
                g.setColor(tint.brighter());
                g.setFont(Theme.font(Font.BOLD, size * 0.45f));
                var fm = g.getFontMetrics();
                g.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
                g.dispose();
            }
        };
        c.setPreferredSize(new Dimension(size, size));
        c.setMinimumSize(c.getPreferredSize());
        c.setMaximumSize(c.getPreferredSize());
        return c;
    }

    /** Image inside a rounded frame of fixed size. */
    public static JComponent imageTile(ImageIcon icon, int w, int h) {
        JComponent c = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                g.setColor(Theme.SURFACE_2);
                g.fillRect(0, 0, w, h);
                if (icon != null) {
                    icon.paintIcon(this, g, 0, 0);
                }
                g.dispose();
            }
        };
        c.setPreferredSize(new Dimension(w, h));
        c.setMinimumSize(c.getPreferredSize());
        c.setMaximumSize(c.getPreferredSize());
        return c;
    }

    /** Segmented selector (one toggle per option); {@code onSelect} receives the chosen id. */
    public static JPanel segmented(List<String> ids, List<String> labels, String selected, Consumer<String> onSelect) {
        JPanel p = row(6);
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            JToggleButton b = new JToggleButton(labels.get(i), id.equals(selected));
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.setFont(Theme.font(Font.BOLD, 13f));
            b.setFocusable(false);
            b.addActionListener(e -> onSelect.accept(id));
            group.add(b);
            p.add(b);
        }
        return p;
    }

    /** "1,2 MB", "830 KB". */
    public static String size(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1048576.0);
        }
        return Math.max(1, bytes / 1024) + " KB";
    }
}
