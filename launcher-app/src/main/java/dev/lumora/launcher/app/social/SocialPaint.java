package dev.lumora.launcher.app.social;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.social.SkinFaces;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Role;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Drawing helpers shared by the social page: faces, status dots, name colors, role chips, time labels. */
final class SocialPaint {
    /** Own message bubble (dark Lumora cyan, readable with white text). */
    static final Color OWN_BUBBLE = new Color(0x0F5E6B);
    static final Color OTHER_BUBBLE = Theme.SURFACE_2;
    static final Color SELECTED = Theme.mix(Theme.SURFACE, Theme.ACCENT, 0.22);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY);

    /** True when a face was still loading in the last paint (the page repaints a little later). */
    static volatile boolean facesPending;

    private SocialPaint() {
    }

    static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Color rgb(int rgb) {
        return new Color(rgb & 0xFFFFFF);
    }

    /** Stable per-player color for names (like the mockup: every player has an own color). */
    static Color nameColor(String uuid) {
        return new Color(dev.lumora.social.Rules.nameColor(uuid));
    }

    /** Skin face in a rounded square; initials until the face is loaded. */
    static void face(Graphics2D g, String uuid, String name, int x, int y, int size) {
        int[] px = SkinFaces.face(uuid);
        java.awt.Shape old = g.getClip();
        RoundRectangle2D clip = new RoundRectangle2D.Double(x, y, size, size, size / 3.0, size / 3.0);
        g.clip(clip);
        if (px == null) {
            facesPending = true;
            g.setColor(Theme.alpha(nameColor(uuid), 90));
            g.fill(clip);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, size * 0.42f));
            String l = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(l, x + (size - fm.stringWidth(l)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
        } else {
            double p = size / 8.0;
            for (int i = 0; i < 64; i++) {
                g.setColor(new Color(px[i], false));
                int x0 = x + (int) Math.round((i % 8) * p);
                int y0 = y + (int) Math.round((i / 8) * p);
                int x1 = x + (int) Math.round((i % 8 + 1) * p);
                int y1 = y + (int) Math.round((i / 8 + 1) * p);
                g.fillRect(x0, y0, x1 - x0, y1 - y0);
            }
        }
        g.setClip(old);
    }

    static Color presenceColor(Presence p) {
        return switch (p.state) {
            case OFFLINE -> new Color(0x6B7280);
            case LAUNCHER, MENU -> Theme.OK;
            case SINGLEPLAYER, MULTIPLAYER -> Theme.WARN;
        };
    }

    /** "Online", "Im Hauptmenü (1.8.9)", "Spielt auf hypixel.net", "Offline" … */
    static String presenceText(Presence p) {
        String v = p.version.isEmpty() ? "" : " · " + p.version;
        return switch (p.state) {
            case OFFLINE -> "Offline";
            case LAUNCHER -> "Online";
            case MENU -> "Im Hauptmenü" + v;
            case SINGLEPLAYER -> "Einzelspieler" + v;
            case MULTIPLAYER -> (p.detail.isEmpty() ? "Mehrspieler" : "Auf " + p.detail) + v;
        };
    }

    /** Friends list subtitle; for a shared server only the prefix (the address is drawn bold after it). */
    static String listText(Presence p) {
        String v = p.version.isEmpty() ? "" : " · " + p.version;
        return switch (p.state) {
            case OFFLINE -> "offline";
            case LAUNCHER -> "im Launcher";
            case MENU -> "im Hauptmenü" + v;
            case SINGLEPLAYER -> "im Einzelspieler" + v;
            case MULTIPLAYER -> p.detail.isEmpty() ? "im Mehrspieler" + v : "spielt auf";
        };
    }

    /** Status dot with a ring in the background color (bottom right of an avatar). */
    static void statusDot(Graphics2D g, Presence p, int cx, int cy, int r, Color ring) {
        g.setColor(ring);
        g.fill(new Ellipse2D.Double(cx - r - 2, cy - r - 2, 2 * r + 4, 2 * r + 4));
        g.setColor(presenceColor(p));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** Group avatar: rounded tile in the group color with its symbol. */
    static void groupTile(Graphics2D g, String icon, int color, int x, int y, int size) {
        Color c = rgb(color);
        g.setColor(Theme.alpha(c, 70));
        g.fill(new RoundRectangle2D.Double(x, y, size, size, size / 3.0, size / 3.0));
        int is = Math.round(size * 0.56f);
        Icons.of(Icons.symbol(icon), is, c.brighter()).paintIcon(null, g, x + (size - is) / 2, y + (size - is) / 2);
    }

    /** Role symbol and name in the role color; returns the width used. */
    static int roleTag(Graphics2D g, Role r, int x, int baseline, float size) {
        Color c = rgb(r.color);
        int is = Math.round(size + 2);
        Icons.of(Icons.symbol(r.icon), is, c).paintIcon(null, g, x, baseline - is + 2);
        g.setFont(Theme.font(Font.BOLD, size));
        g.setColor(c);
        g.drawString(r.name, x + is + 4, baseline);
        return is + 4 + g.getFontMetrics().stringWidth(r.name);
    }

    static String time(long at) {
        return TIME.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
    }

    static LocalDate day(long at) {
        return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static String dayLabel(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) {
            return "HEUTE";
        }
        if (d.equals(today.minusDays(1))) {
            return "GESTERN";
        }
        return (d.getYear() == today.getYear() ? DAY.format(d) : DAY_YEAR.format(d)).toUpperCase(Locale.GERMANY);
    }

    /** "16:45", "Gestern", "Mo." or "12.10." for list previews. */
    static String shortTime(long at) {
        LocalDate d = day(at);
        LocalDate today = LocalDate.now();
        if (d.equals(today)) {
            return time(at);
        }
        if (d.equals(today.minusDays(1))) {
            return "Gestern";
        }
        if (d.isAfter(today.minusDays(7))) {
            return DateTimeFormatter.ofPattern("EE", Locale.GERMANY).format(d);
        }
        return DateTimeFormatter.ofPattern("d.M.", Locale.GERMANY).format(d);
    }

    /** Word wrap for {@code maxWidth} (long words are broken by character). Keeps explicit line breaks. */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String w = word;
                while (fm.stringWidth(w) > maxWidth && w.length() > 1) {
                    int cut = w.length() - 1;
                    while (cut > 1 && fm.stringWidth(w.substring(0, cut)) > maxWidth) {
                        cut--;
                    }
                    out.add(w.substring(0, cut));
                    w = w.substring(cut);
                }
                line.append(w);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** Text cut with "…" to fit. */
    static String ellipsize(String s, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(s) <= maxWidth) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }
}
