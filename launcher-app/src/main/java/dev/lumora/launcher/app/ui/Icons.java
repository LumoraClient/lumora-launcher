package dev.lumora.launcher.app.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Small vector icons drawn with Java2D (no icon font or image assets needed). */
public final class Icons {
    public enum Kind { PLAY, PUZZLE, SLIDERS, PULSE, TERMINAL, FOLDER, REFRESH, USER, CHECK, CHIP, MEMORY, GLOBE, MONITOR,
        LIBRARY, IMAGE, SERVER, CLOCK, LOCK, PLUS, SEARCH, CUBE, ALERT, COPY, ARCHIVE,
        USERS, USER_PLUS, NEWS, CHAT, SEND, INFO, BELL, BELL_OFF, MORE, X, TRASH, EDIT, LOGOUT,
        HOME, GEAR, BOLT, PALETTE, BUG, CHEVRON_DOWN, CHART, DISCORD, BROOM, HARDDRIVE, ROCKET, SHIRT,
        // role and group symbols (filled), keys as in dev.lumora.social.Rules.ICONS
        R_CIRCLE, R_CROWN, R_SHIELD, R_SWORD, R_STAR, R_BOW, R_PICKAXE, R_HEART, R_FLAME, R_BOLT, R_GEM, R_FLAG }

    private Icons() {
    }

    /** Role / group icon for a key of dev.lumora.social.Rules.ICONS. */
    public static Kind symbol(String key) {
        return switch (key == null ? "" : key) {
            case "crown" -> Kind.R_CROWN;
            case "shield" -> Kind.R_SHIELD;
            case "sword" -> Kind.R_SWORD;
            case "star" -> Kind.R_STAR;
            case "bow" -> Kind.R_BOW;
            case "pickaxe" -> Kind.R_PICKAXE;
            case "heart" -> Kind.R_HEART;
            case "flame" -> Kind.R_FLAME;
            case "bolt" -> Kind.R_BOLT;
            case "gem" -> Kind.R_GEM;
            case "flag" -> Kind.R_FLAG;
            default -> Kind.R_CIRCLE;
        };
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
    }

    private static Path2D poly(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        p.closePath();
        return p;
    }

    /** The social icons; false for kinds drawn by {@link #draw}. */
    private static boolean drawSocial(Kind kind, Graphics2D g) {
        switch (kind) {
            case USERS -> {
                g.draw(new Ellipse2D.Double(5.5, 4.5, 7, 7));
                g.draw(new java.awt.geom.Arc2D.Double(2.5, 13.5, 13, 11, 0, 180, java.awt.geom.Arc2D.OPEN));
                g.drawArc(13, 5, 6, 6, -90, 220);
                g.draw(new java.awt.geom.Arc2D.Double(14, 14, 8, 8, 0, 150, java.awt.geom.Arc2D.OPEN));
            }
            case NEWS -> {
                g.draw(new RoundRectangle2D.Double(3.5, 4.5, 14, 15, 2.5, 2.5));
                line(g, 17.5, 8, 20.5, 8);
                line(g, 20.5, 8, 20.5, 17);
                g.draw(new java.awt.geom.Arc2D.Double(17.5, 15, 3, 4.5, 180, 180, java.awt.geom.Arc2D.OPEN));
                g.fill(new RoundRectangle2D.Double(6.5, 7.5, 8, 4, 1, 1));
                line(g, 6.5, 14, 14.5, 14);
                line(g, 6.5, 16.8, 12, 16.8);
            }
            case USER_PLUS -> {
                g.draw(new Ellipse2D.Double(5, 4.5, 7, 7));
                g.draw(new java.awt.geom.Arc2D.Double(2, 13.5, 13, 11, 0, 180, java.awt.geom.Arc2D.OPEN));
                line(g, 18, 8, 18, 14);
                line(g, 15, 11, 21, 11);
            }
            case CHAT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 4.5);
                p.lineTo(18, 4.5);
                p.quadTo(20.5, 4.5, 20.5, 7);
                p.lineTo(20.5, 14);
                p.quadTo(20.5, 16.5, 18, 16.5);
                p.lineTo(10, 16.5);
                p.lineTo(5.5, 20);
                p.lineTo(5.5, 16.5);
                p.quadTo(3.5, 16.3, 3.5, 14);
                p.lineTo(3.5, 7);
                p.quadTo(3.5, 4.5, 6, 4.5);
                g.draw(p);
            }
            case SEND -> {
                g.fill(poly(3, 4, 21, 12, 3, 20, 5.5, 12));
            }
            case INFO -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                line(g, 12, 11, 12, 16);
                g.fill(new Ellipse2D.Double(11, 7, 2, 2));
            }
            case BELL, BELL_OFF -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 16.5);
                p.lineTo(6, 11);
                p.quadTo(6, 5, 12, 5);
                p.quadTo(18, 5, 18, 11);
                p.lineTo(18, 16.5);
                p.lineTo(19.5, 17.5);
                p.lineTo(4.5, 17.5);
                p.closePath();
                g.draw(p);
                g.drawArc(10, 18, 4, 3, 180, 180);
                if (kind == Kind.BELL_OFF) {
                    line(g, 4, 4, 20, 20);
                }
            }
            case MORE -> {
                g.fill(new Ellipse2D.Double(4, 10.5, 3.2, 3.2));
                g.fill(new Ellipse2D.Double(10.4, 10.5, 3.2, 3.2));
                g.fill(new Ellipse2D.Double(16.8, 10.5, 3.2, 3.2));
            }
            case X -> {
                line(g, 6, 6, 18, 18);
                line(g, 18, 6, 6, 18);
            }
            case TRASH -> {
                line(g, 4.5, 7, 19.5, 7);
                g.draw(new RoundRectangle2D.Double(6.5, 7, 11, 13, 2.5, 2.5));
                line(g, 10, 4.5, 14, 4.5);
                line(g, 10.5, 11, 10.5, 16);
                line(g, 13.5, 11, 13.5, 16);
            }
            case EDIT -> {
                g.draw(poly(15.5, 4.5, 19.5, 8.5, 9, 19, 4.5, 19.5, 5, 15));
            }
            case LOGOUT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(10, 4.5);
                p.lineTo(5.5, 4.5);
                p.lineTo(5.5, 19.5);
                p.lineTo(10, 19.5);
                g.draw(p);
                line(g, 10, 12, 20, 12);
                g.draw(new java.awt.geom.Line2D.Double(16, 8, 20, 12));
                g.draw(new java.awt.geom.Line2D.Double(16, 16, 20, 12));
            }
            case R_CIRCLE -> {
                g.draw(new Ellipse2D.Double(4.5, 4.5, 15, 15));
                g.fill(new Ellipse2D.Double(9, 9, 6, 6));
            }
            case R_CROWN -> {
                g.fill(poly(3.5, 8, 8, 12, 12, 5, 16, 12, 20.5, 8, 18.5, 17.5, 5.5, 17.5));
                g.fill(new RoundRectangle2D.Double(5.5, 18.5, 13, 2.5, 1, 1));
            }
            case R_SHIELD -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3);
                p.lineTo(19.5, 6);
                p.lineTo(19.5, 11.5);
                p.quadTo(19.5, 18, 12, 21);
                p.quadTo(4.5, 18, 4.5, 11.5);
                p.lineTo(4.5, 6);
                p.closePath();
                g.fill(p);
            }
            case R_SWORD -> {
                g.fill(poly(19.5, 3, 21, 4.5, 11, 14.5, 9.5, 13));
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line(g, 6.5, 12.5, 11.5, 17.5);
                line(g, 8.5, 15.5, 4, 20);
            }
            case R_STAR -> g.fill(poly(12, 2.5, 14.6, 8.9, 21.5, 9.3, 16.2, 13.7, 17.9, 20.5, 12, 16.8, 6.1, 20.5, 7.8, 13.7,
                    2.5, 9.3, 9.4, 8.9));
            case R_BOW -> {
                g.draw(new java.awt.geom.Arc2D.Double(-1, 3, 18, 18, -60, 120, java.awt.geom.Arc2D.OPEN));
                g.setStroke(new BasicStroke(1.2f));
                line(g, 12.5, 4.2, 12.5, 19.8);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line(g, 5, 12, 21, 12);
                g.fill(poly(21.5, 12, 18, 9.5, 18, 14.5));
            }
            case R_PICKAXE -> {
                Path2D head = new Path2D.Double();
                head.moveTo(4, 8);
                head.quadTo(12, 1, 20, 8);
                head.quadTo(12, 4.5, 4, 8);
                g.fill(head);
                g.draw(head);
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line(g, 12, 5.5, 12, 21);
            }
            case R_HEART -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 20);
                p.curveTo(2, 13, 3, 5, 8, 5);
                p.curveTo(10.5, 5, 12, 7, 12, 8);
                p.curveTo(12, 7, 13.5, 5, 16, 5);
                p.curveTo(21, 5, 22, 13, 12, 20);
                g.fill(p);
            }
            case R_FLAME -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 2.5);
                p.curveTo(13, 7, 18.5, 9, 18.5, 14.5);
                p.curveTo(18.5, 18.5, 15.5, 21, 12, 21);
                p.curveTo(8.5, 21, 5.5, 18.5, 5.5, 14.5);
                p.curveTo(5.5, 11, 8, 9.5, 8.5, 7);
                p.curveTo(10, 9, 10.5, 10, 10.5, 11.5);
                p.curveTo(11.5, 9, 12.5, 6, 12, 2.5);
                g.fill(p);
            }
            case R_BOLT -> g.fill(poly(13.5, 2.5, 5, 13.5, 11, 13.5, 10, 21.5, 19, 10, 13, 10));
            case R_GEM -> g.fill(poly(7, 4, 17, 4, 21, 9, 12, 20.5, 3, 9));
            case R_FLAG -> {
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line(g, 5.5, 3.5, 5.5, 21);
                g.fill(poly(6.5, 4, 19.5, 4, 16, 8.5, 19.5, 13, 6.5, 13));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static Icon of(Kind kind, int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setColor(color);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(kind, g);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    private static void draw(Kind kind, Graphics2D g) {
        if (drawSocial(kind, g)) {
            return;
        }
        switch (kind) {
            case HOME -> {
                g.draw(poly(3.5, 11, 12, 4, 20.5, 11, 20.5, 20, 3.5, 20));
                g.draw(new RoundRectangle2D.Double(9.5, 13.5, 5, 6.5, 1.5, 1.5));
            }
            case GEAR -> {
                g.draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    line(g, 12 + Math.cos(a) * 6, 12 + Math.sin(a) * 6, 12 + Math.cos(a) * 8.8, 12 + Math.sin(a) * 8.8);
                }
            }
            case BOLT -> g.draw(poly(13.5, 3, 5.5, 13.5, 11.5, 13.5, 10.5, 21, 18.5, 10.5, 12.5, 10.5));
            case PALETTE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5);
                p.curveTo(6.5, 3.5, 3.5, 7.5, 3.5, 12);
                p.curveTo(3.5, 17, 7.5, 20.5, 12, 20.5);
                p.curveTo(14, 20.5, 14.5, 18.5, 13.5, 17);
                p.curveTo(12.5, 15.5, 13.5, 14, 15.5, 14);
                p.lineTo(17.5, 14);
                p.curveTo(19.5, 14, 20.5, 12.5, 20.5, 11);
                p.curveTo(20.5, 6.5, 16.5, 3.5, 12, 3.5);
                g.draw(p);
                g.fill(new Ellipse2D.Double(7, 10, 2.6, 2.6));
                g.fill(new Ellipse2D.Double(10, 6.3, 2.6, 2.6));
                g.fill(new Ellipse2D.Double(14.4, 7.2, 2.6, 2.6));
            }
            case BUG -> {
                g.draw(new RoundRectangle2D.Double(7.5, 8, 9, 12, 8, 8));
                line(g, 12, 9, 12, 19);
                line(g, 9.5, 5, 10.5, 7.5);
                line(g, 14.5, 5, 13.5, 7.5);
                line(g, 4, 11, 7.5, 12);
                line(g, 20, 11, 16.5, 12);
                line(g, 4, 17, 7.5, 16);
                line(g, 20, 17, 16.5, 16);
            }
            case CHEVRON_DOWN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6.5, 9.5);
                p.lineTo(12, 15);
                p.lineTo(17.5, 9.5);
                g.draw(p);
            }
            case CHART -> {
                g.draw(new RoundRectangle2D.Double(4.5, 12, 3.5, 8, 1, 1));
                g.draw(new RoundRectangle2D.Double(10.25, 7, 3.5, 13, 1, 1));
                g.draw(new RoundRectangle2D.Double(16, 4, 3.5, 16, 1, 1));
            }
            case DISCORD -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 6.5);
                p.curveTo(9.5, 4.5, 14.5, 4.5, 18, 6.5);
                p.curveTo(20, 10, 20.8, 13.5, 20.5, 17);
                p.curveTo(19, 18.3, 17.5, 19, 16, 19.3);
                p.lineTo(15, 17.5);
                p.curveTo(13, 18, 11, 18, 9, 17.5);
                p.lineTo(8, 19.3);
                p.curveTo(6.5, 19, 5, 18.3, 3.5, 17);
                p.curveTo(3.2, 13.5, 4, 10, 6, 6.5);
                p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(8.2, 11.2, 2.8, 3));
                g.fill(new Ellipse2D.Double(13, 11.2, 2.8, 3));
            }
            case BROOM -> {
                line(g, 19.5, 4.5, 12.5, 11.5);
                g.draw(poly(11, 10, 14, 13, 11, 20, 4, 20, 4.5, 15.5));
                line(g, 7.5, 20, 9.5, 16);
            }
            case HARDDRIVE -> {
                g.draw(new RoundRectangle2D.Double(3.5, 6, 17, 12, 3, 3));
                line(g, 3.5, 13, 20.5, 13);
                g.fill(new Ellipse2D.Double(16, 14.6, 2, 2));
            }
            case ROCKET -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3);
                p.curveTo(16, 6, 16.5, 11, 15, 16);
                p.lineTo(9, 16);
                p.curveTo(7.5, 11, 8, 6, 12, 3);
                p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(10.5, 8, 3, 3));
                g.draw(poly(9, 12.5, 6, 15.5, 6.5, 18.5, 9, 16));
                g.draw(poly(15, 12.5, 18, 15.5, 17.5, 18.5, 15, 16));
                line(g, 12, 18, 12, 21);
            }
            case SHIRT -> g.draw(poly(8.5, 4, 4, 6.5, 3, 11, 6.5, 11.5, 6.5, 20, 17.5, 20, 17.5, 11.5, 21, 11, 20, 6.5, 15.5, 4, 12, 6.5));
            case ALERT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 4);
                p.lineTo(21, 19.5);
                p.lineTo(3, 19.5);
                p.closePath();
                g.draw(p);
                g.draw(new java.awt.geom.Line2D.Double(12, 10, 12, 14));
                g.fill(new Ellipse2D.Double(11, 16, 2, 2));
            }
            case COPY -> {
                g.draw(new RoundRectangle2D.Double(8, 8, 12, 12, 3, 3));
                Path2D p = new Path2D.Double();
                p.moveTo(16, 4.5);
                p.lineTo(6, 4.5);
                p.quadTo(4.5, 4.5, 4.5, 6);
                p.lineTo(4.5, 16);
                g.draw(p);
            }
            case ARCHIVE -> {
                g.draw(new RoundRectangle2D.Double(3.5, 4.5, 17, 5, 2, 2));
                g.draw(new RoundRectangle2D.Double(5, 9.5, 14, 10, 2, 2));
                g.draw(new java.awt.geom.Line2D.Double(10, 13, 14, 13));
            }
            case LIBRARY -> {
                g.draw(new RoundRectangle2D.Double(4, 4, 4.5, 16, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(10, 4, 4.5, 16, 1.5, 1.5));
                Path2D p = new Path2D.Double();
                p.moveTo(16.5, 5.5);
                p.lineTo(20.5, 19);
                g.draw(p);
            }
            case IMAGE -> {
                g.draw(new RoundRectangle2D.Double(3.5, 5, 17, 14, 3, 3));
                g.fill(new Ellipse2D.Double(7, 8, 3.5, 3.5));
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 17);
                p.lineTo(10, 12.5);
                p.lineTo(13.5, 15.5);
                p.lineTo(16, 13);
                p.lineTo(19.5, 16.5);
                g.draw(p);
            }
            case SERVER -> {
                g.draw(new RoundRectangle2D.Double(4, 4.5, 16, 6, 2, 2));
                g.draw(new RoundRectangle2D.Double(4, 13.5, 16, 6, 2, 2));
                g.fill(new Ellipse2D.Double(6.5, 6.5, 2, 2));
                g.fill(new Ellipse2D.Double(6.5, 15.5, 2, 2));
            }
            case CLOCK -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.draw(new java.awt.geom.Line2D.Double(12, 7.5, 12, 12));
                g.draw(new java.awt.geom.Line2D.Double(12, 12, 15.5, 14));
            }
            case LOCK -> {
                g.draw(new RoundRectangle2D.Double(5, 10.5, 14, 10, 3, 3));
                g.drawArc(8, 4, 8, 11, 0, 180);
                g.draw(new java.awt.geom.Line2D.Double(8, 9.5, 8, 10.5));
                g.draw(new java.awt.geom.Line2D.Double(16, 9.5, 16, 10.5));
            }
            case PLUS -> {
                g.drawLine(12, 5, 12, 19);
                g.drawLine(5, 12, 19, 12);
            }
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(new java.awt.geom.Line2D.Double(14.5, 14.5, 20, 20));
            }
            case CUBE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5);
                p.lineTo(20, 7.5);
                p.lineTo(20, 16.5);
                p.lineTo(12, 20.5);
                p.lineTo(4, 16.5);
                p.lineTo(4, 7.5);
                p.closePath();
                p.moveTo(4, 7.5);
                p.lineTo(12, 11.5);
                p.lineTo(20, 7.5);
                p.moveTo(12, 11.5);
                p.lineTo(12, 20.5);
                g.draw(p);
            }
            case PLAY -> {
                Path2D p = new Path2D.Double();
                p.moveTo(7, 4.5);
                p.lineTo(19, 12);
                p.lineTo(7, 19.5);
                p.closePath();
                g.fill(p);
            }
            case PUZZLE -> {
                g.draw(new RoundRectangle2D.Double(4, 4, 7, 7, 3, 3));
                g.draw(new RoundRectangle2D.Double(13, 4, 7, 7, 3, 3));
                g.draw(new RoundRectangle2D.Double(4, 13, 7, 7, 3, 3));
                g.fill(new RoundRectangle2D.Double(13, 13, 7, 7, 3, 3));
            }
            case SLIDERS -> {
                g.drawLine(4, 7, 20, 7);
                g.drawLine(4, 17, 20, 17);
                g.fill(new Ellipse2D.Double(12, 4, 6, 6));
                g.fill(new Ellipse2D.Double(6, 14, 6, 6));
            }
            case PULSE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 12);
                p.lineTo(7, 12);
                p.lineTo(10, 5);
                p.lineTo(14, 19);
                p.lineTo(17, 12);
                p.lineTo(21, 12);
                g.draw(p);
            }
            case TERMINAL -> {
                g.draw(new RoundRectangle2D.Double(3, 4.5, 18, 15, 4, 4));
                Path2D p = new Path2D.Double();
                p.moveTo(7, 9.5);
                p.lineTo(10, 12);
                p.lineTo(7, 14.5);
                g.draw(p);
                g.drawLine(12, 15, 16, 15);
            }
            case FOLDER -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 7);
                p.lineTo(3.5, 18);
                p.quadTo(3.5, 19.5, 5, 19.5);
                p.lineTo(19, 19.5);
                p.quadTo(20.5, 19.5, 20.5, 18);
                p.lineTo(20.5, 9.5);
                p.quadTo(20.5, 8, 19, 8);
                p.lineTo(12, 8);
                p.lineTo(10, 5.5);
                p.lineTo(5, 5.5);
                p.quadTo(3.5, 5.5, 3.5, 7);
                g.draw(p);
            }
            case REFRESH -> {
                g.drawArc(5, 5, 14, 14, 60, 270);
                Path2D p = new Path2D.Double();
                p.moveTo(15.5, 3.5);
                p.lineTo(16.5, 7.5);
                p.lineTo(12.5, 8.5);
                g.draw(p);
            }
            case USER -> {
                g.draw(new Ellipse2D.Double(8, 4, 8, 8));
                g.drawArc(4, 14, 16, 12, 0, 180);
            }
            case CHECK -> {
                Path2D p = new Path2D.Double();
                p.moveTo(5, 12.5);
                p.lineTo(10, 17.5);
                p.lineTo(19, 7);
                g.draw(p);
            }
            case CHIP -> {
                g.draw(new RoundRectangle2D.Double(6, 6, 12, 12, 3, 3));
                g.draw(new RoundRectangle2D.Double(9.5, 9.5, 5, 5, 1.5, 1.5));
                for (int i = 0; i < 3; i++) {
                    int o = 8 + i * 4;
                    g.drawLine(o, 3, o, 5);
                    g.drawLine(o, 19, o, 21);
                    g.drawLine(3, o, 5, o);
                    g.drawLine(19, o, 21, o);
                }
            }
            case MEMORY -> {
                g.draw(new RoundRectangle2D.Double(3, 7, 18, 10, 3, 3));
                for (int i = 0; i < 4; i++) {
                    int o = 6 + i * 4;
                    g.drawLine(o, 10, o, 14);
                    g.drawLine(o, 17, o, 19);
                }
            }
            case GLOBE -> {
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 17));
                g.drawLine(4, 12, 20, 12);
            }
            case MONITOR -> {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 12, 3, 3));
                g.drawLine(9, 20, 15, 20);
                g.drawLine(12, 16, 12, 20);
            }
        }
    }
}
