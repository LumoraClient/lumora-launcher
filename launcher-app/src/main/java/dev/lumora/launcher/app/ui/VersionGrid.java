package dev.lumora.launcher.app.ui;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Version selection as a grid of major versions (26.3, 26.2, 26.1, 1.21, 1.20 …), three per row. A card shows the
 * family's own pixel-art background (or the player's newest screenshot of that version), the major version large and,
 * when there are several, the sub-versions as chips. Clicking the card picks the version last chosen in that family
 * (else the newest), clicking a chip picks that version.
 */
public final class VersionGrid extends JPanel {
    /** One selectable version: profile id and the version text shown on the chip. */
    public record Version(String id, String version, String tooltip) {
    }

    private static final int CARD_H = 150;
    /** Card height (150 on a page, smaller in the version menu); the big version text scales with it. */
    private final int cardH;
    private final Map<String, List<Version>> families = new LinkedHashMap<>();
    private final Map<String, String> lastInFamily = new HashMap<>();
    private final Map<String, String> playInfo = new HashMap<>();
    private final Map<String, Image> shots = new HashMap<>();
    private final Consumer<String> onSelect;
    private final List<Card> cards = new ArrayList<>();
    private String selected;

    /**
     * @param versions     newest first
     * @param screenshots  profile id -> screenshots folder (newest image becomes the background), may return null
     */
    public VersionGrid(List<Version> versions, Function<String, Path> screenshots, Consumer<String> onSelect) {
        this(versions, screenshots, onSelect, CARD_H, 12);
    }

    /** @param cardHeight height of one card, @param gap space between cards (compact grid for the version menu) */
    public VersionGrid(List<Version> versions, Function<String, Path> screenshots, Consumer<String> onSelect, int cardHeight, int gap) {
        super(new GridLayout(0, 3, gap, gap));
        this.onSelect = onSelect;
        this.cardH = cardHeight;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Version v : versions) {
            families.computeIfAbsent(family(v.version()), k -> new ArrayList<>()).add(v);
        }
        for (Map.Entry<String, List<Version>> f : families.entrySet()) {
            Image shot = newestScreenshot(f.getValue(), screenshots);
            if (shot != null) {
                shots.put(f.getKey(), shot);
            }
            Card c = new Card(f.getKey(), f.getValue());
            cards.add(c);
            add(c);
        }
        int rows = (families.size() + 2) / 3;
        setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * cardH + (rows - 1) * gap));
    }

    /** "1.21.11" -> "1.21", "26.1.2" -> "26.1", "1.8.9" -> "1.8". */
    public static String family(String version) {
        String[] p = version.split("\\.");
        return p.length >= 2 ? p[0] + "." + p[1] : version;
    }

    /**
     * Patches chosen before (family -> profile id, Einstellungen › Spiel › "Ausgewählten Patch merken"): clicking such a
     * family's card picks that patch again. Unknown ids are ignored; call before {@link #setSelected}.
     */
    public void remember(Map<String, String> lastPatch) {
        lastPatch.forEach((family, id) -> {
            List<Version> vs = families.get(family);
            if (vs != null && vs.stream().anyMatch(v -> v.id().equals(id))) {
                lastInFamily.put(family, id);
            }
        });
    }

    public void setSelected(String id) {
        selected = id;
        for (Map.Entry<String, List<Version>> f : families.entrySet()) {
            if (f.getValue().stream().anyMatch(v -> v.id().equals(id))) {
                lastInFamily.put(f.getKey(), id);
            }
        }
        cards.forEach(Component::repaint);
    }

    /** Short text per profile id (play time), shown on the card of the selected or last chosen version. */
    public void setPlayInfo(Map<String, String> info) {
        playInfo.clear();
        playInfo.putAll(info);
        cards.forEach(Component::repaint);
    }

    private static Image newestScreenshot(List<Version> versions, Function<String, Path> dirs) {
        Path best = null;
        long bestTime = 0;
        for (Version v : versions) {
            Path dir = dirs.apply(v.id());
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    String n = p.getFileName().toString().toLowerCase();
                    // the launcher's own test screenshots are menus, not landscapes
                    if (!n.endsWith(".png") || n.startsWith("smoke-")) {
                        continue;
                    }
                    long t = Files.getLastModifiedTime(p).toMillis();
                    if (t > bestTime) {
                        bestTime = t;
                        best = p;
                    }
                }
            } catch (Exception ignored) {
                // no screenshots
            }
        }
        if (best == null) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(best.toFile());
            if (img == null) {
                return null;
            }
            // small copy: cards are ~450 px wide
            int w = 640;
            int h = Math.max(1, img.getHeight() * w / img.getWidth());
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private final class Card extends JComponent {
        private final String family;
        private final List<Version> versions;
        private final List<Rectangle> chipBoxes = new ArrayList<>();
        private boolean hover;
        private int hoverChip = -1;

        Card(String family, List<Version> versions) {
            this.family = family;
            this.versions = versions;
            setPreferredSize(new Dimension(cardH * 2, cardH));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int chip = chipAt(e.getX(), e.getY());
                    String id = chip >= 0 ? versions.get(chip).id() : lastInFamily.getOrDefault(family, versions.get(0).id());
                    onSelect.accept(id);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    hoverChip = -1;
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int c = chipAt(e.getX(), e.getY());
                    if (c != hoverChip) {
                        hoverChip = c;
                        repaint();
                    }
                    Version v = c >= 0 ? versions.get(c) : current();
                    setToolTipText(v.tooltip());
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private Version current() {
            String id = lastInFamily.getOrDefault(family, versions.get(0).id());
            return versions.stream().filter(v -> v.id().equals(id)).findFirst().orElse(versions.get(0));
        }

        private int chipAt(int x, int y) {
            for (int i = 0; i < chipBoxes.size(); i++) {
                if (chipBoxes.get(i).contains(x, y)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean sel = versions.stream().anyMatch(v -> v.id().equals(selected));
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 14, 14);
            java.awt.Shape clip0 = g.getClip();
            g.clip(shape);
            Image shot = shots.get(family);
            if (shot != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                cover(g, shot, w, h);
            } else {
                // blocky on purpose: nearest-neighbour upscale of the pixel art
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                cover(g, VersionArt.of(family), w, h);
            }
            g.setPaint(new GradientPaint(0, 0, new Color(8, 10, 14, hover || sel ? 10 : 40), 0, h, new Color(8, 10, 14, hover || sel ? 110 : 150)));
            g.fillRect(0, 0, w, h);
            // big version
            g.setFont(Theme.font(Font.BOLD, 44f * Math.min(1f, h / (float) CARD_H + 0.1f)));
            FontMetrics fm = g.getFontMetrics();
            int tx = (w - fm.stringWidth(family)) / 2;
            int ty = versions.size() > 1 ? h / 2 + fm.getAscent() / 2 - 12 : h / 2 + fm.getAscent() / 2 - 4;
            g.setColor(new Color(0, 0, 0, 120));
            g.drawString(family, tx + 2, ty + 3);
            g.setColor(Color.WHITE);
            g.drawString(family, tx, ty);
            // play time of the current version of this family
            String info = playInfo.get(current().id());
            if (info != null && !info.isEmpty()) {
                g.setFont(Theme.font(Font.BOLD, 11.5f));
                FontMetrics im = g.getFontMetrics();
                int iw = im.stringWidth(info) + 14;
                g.setColor(new Color(10, 12, 16, 170));
                Theme.fillRound(g, w - iw - 8, 8, iw, 20, 10, 10);
                g.setColor(new Color(0xD7DCE3));
                g.drawString(info, w - iw + 7 - 8, 22);
            }
            // sub-version chips
            chipBoxes.clear();
            if (versions.size() > 1) {
                g.setFont(Theme.font(Font.BOLD, 12f));
                FontMetrics cm = g.getFontMetrics();
                int x = 10;
                int y = h - 30;
                for (int i = 0; i < versions.size(); i++) {
                    Version v = versions.get(i);
                    int cw = cm.stringWidth(v.version()) + 16;
                    if (x + cw > w - 10) {
                        break;
                    }
                    boolean chipSel = v.id().equals(selected);
                    g.setColor(chipSel ? Theme.ACCENT_PRESSED : i == hoverChip ? new Color(30, 36, 46, 240) : new Color(14, 17, 22, 220));
                    Theme.fillRound(g, x, y, cw, 22, 8, 8);
                    g.setColor(chipSel ? Theme.ACCENT_HOVER : new Color(255, 255, 255, 40));
                    g.setStroke(new BasicStroke(1f));
                    Theme.drawRound(g, x, y, cw, 22, 8, 8);
                    g.setColor(Color.WHITE);
                    g.drawString(v.version(), x + 8, y + 15);
                    chipBoxes.add(new Rectangle(x, y, cw, 22));
                    x += cw + 6;
                }
            }
            g.setClip(clip0);
            g.setStroke(new BasicStroke(sel ? 2.5f : 1f));
            g.setColor(sel ? Theme.ACCENT : hover ? new Color(0x5A6472) : Theme.BORDER);
            g.draw(shape);
            g.dispose();
        }

        private void cover(Graphics2D g, Image img, int w, int h) {
            int iw = img.getWidth(null);
            int ih = img.getHeight(null);
            double s = Math.max((double) w / iw, (double) h / ih);
            int dw = (int) Math.ceil(iw * s);
            int dh = (int) Math.ceil(ih * s);
            g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        }
    }
}
