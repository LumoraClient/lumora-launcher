package dev.lumora.launcher.app.social;

import dev.lumora.launcher.app.ui.Icons;
import dev.lumora.launcher.app.ui.Theme;
import dev.lumora.social.Conv;
import dev.lumora.social.SocialClient;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.ConversationInfo;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Role;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Messages of one conversation, drawn like the launcher's cards: day separators, the sender's face, name and role above
 * a run of messages from the same player, own messages right-aligned with time and check marks (one: delivered,
 * two in accent color: read by someone else), pending ones with a clock, failed ones in red (click = send again).
 */
final class ChatView extends JComponent implements Scrollable {
    private static final int PAD = 22;
    private static final int AVATAR = 36;
    private static final int GAP = 12;
    private static final int BUBBLE_PAD_X = 14;
    private static final int BUBBLE_PAD_Y = 9;
    private static final long GROUP_MS = 5 * 60_000;

    private enum Status { NONE, PENDING, FAILED, SENT, READ }

    private static final class Item {
        boolean day;
        String label;
        String sender;
        String name;
        Role role;
        String text;
        String time;
        boolean own;
        boolean header;
        boolean tail;
        Status status = Status.NONE;
        String nonce;
        // layout
        List<String> lines = List.of();
        int y;
        int h;
        Rectangle bubble = new Rectangle();
        boolean timeOwnLine;
    }

    private final Font body = Theme.font(Font.PLAIN, 14f);
    private final Font small = Theme.font(Font.PLAIN, 11.5f);
    private SocialClient client;
    private String conv;
    private List<Item> items = new ArrayList<>();
    private int laidOutWidth = -1;
    private int contentHeight = 120;
    private long firstId = -1;

    ChatView() {
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Item it = at(e.getPoint());
                if (it != null && it.status == Status.FAILED && client != null) {
                    client.discardPending(it.nonce, true);
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Item it = at(e.getPoint());
                setCursor(it != null && it.status == Status.FAILED ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }

    private Item at(Point p) {
        for (Item it : items) {
            if (!it.day && it.bubble.contains(p)) {
                return it;
            }
        }
        return null;
    }

    /** Shows a conversation (scrolled to the newest message). */
    void show(SocialClient c, String conversation) {
        client = c;
        conv = conversation;
        firstId = -1;
        rebuild();
        scrollToBottom();
    }

    /** New data for the shown conversation: keeps the position, follows new messages when at the bottom. */
    void refresh() {
        JViewport vp = viewport();
        boolean atBottom = vp == null || vp.getViewPosition().y + vp.getExtentSize().height >= contentHeight - 30;
        int oldHeight = contentHeight;
        long oldFirst = firstId;
        int oldY = vp == null ? 0 : vp.getViewPosition().y;
        rebuild();
        if (vp == null) {
            return;
        }
        if (oldFirst > 0 && firstId > 0 && firstId < oldFirst) {
            int dy = contentHeight - oldHeight;
            SwingUtilities.invokeLater(() -> {
                applySize(vp);
                vp.setViewPosition(new Point(0, Math.max(0, oldY + dy)));
            });
        } else if (atBottom) {
            scrollToBottom();
        }
    }

    void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JViewport vp = viewport();
            if (vp != null) {
                applySize(vp);
                vp.setViewPosition(new Point(0, Math.max(0, contentHeight - vp.getExtentSize().height)));
            }
        });
    }

    /** The viewport clamps positions to the current view size: apply the new height before scrolling. */
    private void applySize(JViewport vp) {
        if (getHeight() != contentHeight) {
            setSize(vp.getExtentSize().width, Math.max(contentHeight, vp.getExtentSize().height));
        }
    }

    boolean nearTop() {
        JViewport vp = viewport();
        return vp != null && vp.getViewPosition().y < 60;
    }

    private JViewport viewport() {
        return getParent() instanceof JViewport v ? v : null;
    }

    private void rebuild() {
        List<Item> out = new ArrayList<>();
        if (client == null || conv == null) {
            items = out;
            relayout(true);
            return;
        }
        String me = client.self().uuid;
        Group g = Conv.isGroup(conv) ? client.group(Conv.groupId(conv)) : null;
        ConversationInfo info = client.conversation(conv);
        long othersRead = info == null ? 0 : info.othersRead;
        List<ChatMessage> msgs = client.messages(conv);
        firstId = msgs.isEmpty() ? -1 : msgs.get(0).id;
        LocalDate lastDay = null;
        String lastSender = null;
        long lastAt = 0;
        for (ChatMessage m : msgs) {
            LocalDate d = SocialPaint.day(m.at);
            if (!d.equals(lastDay)) {
                Item day = new Item();
                day.day = true;
                day.label = SocialPaint.dayLabel(d);
                out.add(day);
                lastDay = d;
                lastSender = null;
            }
            Item it = new Item();
            it.sender = m.sender;
            it.own = m.sender.equals(me);
            it.name = client.nameOf(m.sender);
            it.role = g == null ? null : g.roleOf(m.sender);
            it.text = m.text;
            it.time = SocialPaint.time(m.at);
            it.header = !it.own && (!m.sender.equals(lastSender) || m.at - lastAt > GROUP_MS);
            it.status = it.own ? (othersRead >= m.id ? Status.READ : Status.SENT) : Status.NONE;
            out.add(it);
            lastSender = m.sender;
            lastAt = m.at;
        }
        for (SocialClient.Pending p : client.pending(conv)) {
            Item it = new Item();
            it.own = true;
            it.sender = me;
            it.text = p.text;
            it.time = SocialPaint.time(p.at);
            it.status = p.error == null ? Status.PENDING : Status.FAILED;
            it.label = p.error;
            it.nonce = p.nonce;
            out.add(it);
        }
        for (int i = 0; i < out.size(); i++) {
            Item it = out.get(i);
            Item next = i + 1 < out.size() ? out.get(i + 1) : null;
            it.tail = !it.day && (it.own ? next == null || next.day || !next.own : it.header);
        }
        items = out;
        relayout(true);
    }

    private void relayout(boolean force) {
        int w = getParent() != null ? getParent().getWidth() : getWidth();
        if (w <= 0) {
            w = 600;
        }
        if (!force && w == laidOutWidth) {
            return;
        }
        // a new width changes the wrapping and so the height: whoever was at the newest message stays there
        JViewport vp = viewport();
        boolean keepBottom = !force && vp != null && vp.getViewPosition().y + vp.getExtentSize().height >= contentHeight - 30;
        laidOutWidth = w;
        FontMetrics fm = metrics(body);
        FontMetrics sm = metrics(small);
        int lh = fm.getHeight();
        int maxBubble = Math.min(560, (int) ((w - 2 * PAD - AVATAR - GAP) * 0.78));
        int y = 18;
        boolean first = true;
        for (Item it : items) {
            if (it.day) {
                y += first ? 0 : 10;
                it.y = y;
                it.h = 22;
                y += it.h + 10;
                first = false;
                continue;
            }
            int textMax = maxBubble - 2 * BUBBLE_PAD_X;
            it.lines = SocialPaint.wrap(it.text, fm, textMax);
            int lineW = 0;
            for (String l : it.lines) {
                lineW = Math.max(lineW, fm.stringWidth(l));
            }
            int meta = sm.stringWidth(it.time) + (it.own ? 22 : 0) + 12;
            int lastW = fm.stringWidth(it.lines.get(it.lines.size() - 1));
            it.timeOwnLine = lastW + meta > textMax;
            int bw = Math.min(maxBubble, Math.max(lineW, it.timeOwnLine ? meta : lastW + meta) + 2 * BUBBLE_PAD_X);
            int bh = it.lines.size() * lh + (it.timeOwnLine ? sm.getHeight() : 0) + 2 * BUBBLE_PAD_Y;
            y += it.header && !first ? 12 : first ? 0 : 5;
            it.y = y;
            int top = y + (it.header ? 22 : 0);
            int bx = it.own ? w - PAD - bw : PAD + AVATAR + GAP;
            it.bubble = new Rectangle(bx, top, bw, bh);
            it.h = (top - y) + bh + (it.status == Status.FAILED ? 18 : 0);
            y += it.h;
            first = false;
        }
        contentHeight = Math.max(120, y + 18);
        setPreferredSize(new Dimension(w, contentHeight));
        revalidate();
        repaint();
        if (keepBottom) {
            scrollToBottom();
        }
    }

    private FontMetrics metrics(Font f) {
        Graphics g = getGraphics();
        if (g != null) {
            FontMetrics fm = g.getFontMetrics(f);
            g.dispose();
            return fm;
        }
        Graphics2D tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        SocialPaint.quality(tmp);
        FontMetrics fm = tmp.getFontMetrics(f);
        tmp.dispose();
        return fm;
    }

    @Override
    public void doLayout() {
        relayout(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        relayout(false);
        Graphics2D g = (Graphics2D) g0.create();
        SocialPaint.quality(g);
        Rectangle clip = g.getClipBounds();
        int w = getWidth();
        if (items.isEmpty()) {
            g.setFont(Theme.font(Font.PLAIN, 14f));
            g.setColor(Theme.MUTED);
            String t = client != null && client.historyLoaded(conv) ? "Noch keine Nachrichten. Schreib die erste!" : "Lade Nachrichten…";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t, (w - fm.stringWidth(t)) / 2, 60);
            g.dispose();
            return;
        }
        FontMetrics fm = g.getFontMetrics(body);
        for (Item it : items) {
            if (clip != null && (it.y > clip.y + clip.height + 40 || it.y + it.h < clip.y - 40)) {
                continue;
            }
            if (it.day) {
                g.setFont(Theme.font(Font.BOLD, 11.5f));
                FontMetrics dm = g.getFontMetrics();
                int tw = dm.stringWidth(it.label);
                int cy = it.y + it.h / 2;
                g.setColor(Theme.BORDER);
                g.drawLine(PAD + 40, cy, (w - tw) / 2 - 16, cy);
                g.drawLine((w + tw) / 2 + 16, cy, w - PAD - 40, cy);
                g.setColor(Theme.MUTED);
                g.drawString(it.label, (w - tw) / 2, cy + dm.getAscent() / 2 - 1);
                continue;
            }
            if (it.header) {
                SocialPaint.face(g, it.sender, it.name, PAD, it.y + 2, AVATAR);
                int nx = PAD + AVATAR + GAP;
                g.setFont(Theme.font(Font.BOLD, 13.5f));
                g.setColor(SocialPaint.nameColor(it.sender));
                g.drawString(it.name, nx, it.y + 14);
                int rx = nx + g.getFontMetrics().stringWidth(it.name) + 10;
                if (it.role != null && !Role.MEMBER.equals(it.role.id)) {
                    SocialPaint.roleTag(g, it.role, rx, it.y + 14, 12.5f);
                }
            }
            Rectangle b = it.bubble;
            Color fill = it.own ? (it.status == Status.FAILED ? new Color(0x5A1F24) : SocialPaint.OWN_BUBBLE) : SocialPaint.OTHER_BUBBLE;
            g.setColor(fill);
            g.fill(bubbleShape(b, it.own, it.tail));
            g.setFont(body);
            g.setColor(Theme.TEXT);
            int ty = b.y + BUBBLE_PAD_Y + fm.getAscent();
            for (String line : it.lines) {
                g.drawString(line, b.x + BUBBLE_PAD_X, ty);
                ty += fm.getHeight();
            }
            g.setFont(small);
            FontMetrics sm = g.getFontMetrics();
            int metaRight = b.x + b.width - BUBBLE_PAD_X;
            int metaBase = b.y + b.height - BUBBLE_PAD_Y - 1;
            int checkW = it.own ? 18 : 0;
            g.setColor(it.own ? new Color(0xB5DCE3) : Theme.MUTED);
            g.drawString(it.time, metaRight - checkW - sm.stringWidth(it.time), metaBase);
            if (it.own) {
                drawStatus(g, it.status, metaRight - 15, metaBase - 9);
            }
            if (it.status == Status.FAILED) {
                g.setFont(Theme.font(Font.PLAIN, 11.5f));
                g.setColor(Theme.FAIL);
                String t = (it.label == null ? "Nicht gesendet" : it.label) + " – klicken zum Wiederholen";
                g.drawString(t, b.x + b.width - g.getFontMetrics().stringWidth(t), b.y + b.height + 14);
            }
        }
        g.dispose();
    }

    /** Rounded bubble; the first bubble of a run gets a small tail like in chat apps. */
    private static java.awt.Shape bubbleShape(Rectangle b, boolean own, boolean tail) {
        java.awt.Shape r = Theme.roundShape(b.x, b.y, b.width, b.height, 18, 18);
        if (!tail) {
            return r;
        }
        java.awt.geom.Area a = new java.awt.geom.Area(r);
        Path2D t = new Path2D.Double();
        if (own) {
            t.moveTo(b.x + b.width - 12, b.y + b.height);
            t.lineTo(b.x + b.width + 6, b.y + b.height + 2);
            t.lineTo(b.x + b.width - 2, b.y + b.height - 12);
        } else {
            t.moveTo(b.x + 12, b.y);
            t.lineTo(b.x - 6, b.y - 2);
            t.lineTo(b.x + 2, b.y + 12);
        }
        t.closePath();
        a.add(new java.awt.geom.Area(t));
        return a;
    }

    private static void drawStatus(Graphics2D g, Status s, int x, int y) {
        switch (s) {
            case PENDING -> Icons.of(Icons.Kind.CLOCK, 12, new Color(0xB5DCE3)).paintIcon(null, g, x, y - 1);
            case FAILED -> {
                g.setColor(Theme.FAIL);
                g.setFont(Theme.font(Font.BOLD, 12f));
                g.drawString("!", x + 4, y + 10);
            }
            case SENT, READ -> {
                g.setColor(s == Status.READ ? new Color(0x6EE7F9) : new Color(0x9BC7CF));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                check(g, x, y);
                if (s == Status.READ) {
                    check(g, x + 5, y);
                }
            }
            default -> {
            }
        }
    }

    private static void check(Graphics2D g, int x, int y) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + 5);
        p.lineTo(x + 3, y + 8);
        p.lineTo(x + 9, y + 1);
        g.draw(p);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height - 40 : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport v && v.getHeight() > contentHeight;
    }
}
