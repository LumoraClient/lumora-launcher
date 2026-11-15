package dev.lumora.launcher.app.ui;

import dev.lumora.launcher.core.i18n.Lang;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The one tab row of the launcher (settings categories, account, news): outlined tabs in capitals, the chosen one with
 * the accent. Wraps into a second row instead of cutting tabs off. Choices inside a page (filters, options) use
 * {@link Ui#segmented} instead; long lists such as game versions use a dropdown.
 */
public final class TabBar extends JPanel {
    /** Height of a tab; the same as text fields, dropdowns and buttons ({@link Ui#CONTROL_HEIGHT}). */
    public static final int TAB_HEIGHT = Ui.CONTROL_HEIGHT;
    private static final int GAP = 6;
    private static final int FOCUS_MARGIN = 2;

    private final List<Tab> tabs = new ArrayList<>();
    private final Consumer<String> onSelect;
    private String selected;
    private boolean marked = true;

    public TabBar(List<String> ids, List<String> labels, String selected, Consumer<String> onSelect) {
        super(new WrapLayout(FlowLayout.LEFT, GAP, GAP));
        this.onSelect = onSelect;
        this.selected = selected;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        // WrapLayout puts one gap around everything: pull it back so the tabs line up with the content
        setBorder(BorderFactory.createEmptyBorder(-GAP, -GAP, -GAP, 0));
        for (int i = 0; i < ids.size(); i++) {
            Tab t = new Tab(ids.get(i), labels.get(i));
            tabs.add(t);
            add(t);
        }
    }

    public String selected() {
        return selected;
    }

    /** Marks {@code id} without calling the listener. */
    public void select(String id) {
        selected = id;
        repaint();
    }

    /** False shows no tab as chosen (for example while the settings search shows results of all categories). */
    public void setMarked(boolean marked) {
        this.marked = marked;
        repaint();
    }

    @Override
    public void repaint() {
        super.repaint();
        if (tabs != null) {
            tabs.forEach(Component::repaint);
        }
    }

    private final class Tab extends JComponent {
        private final String id;
        private final String text;
        private boolean hover;

        Tab(String id, String label) {
            this.id = id;
            this.text = label.toUpperCase(Lang.locale());
            setFont(Theme.font(Font.BOLD, 11.5f).deriveFont(Map.of(java.awt.font.TextAttribute.TRACKING, 0.1f)));
            FontMetrics fm = getFontMetrics(getFont());
            setPreferredSize(new Dimension(fm.stringWidth(text) + 26, TAB_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selected = id;
                    marked = true;
                    TabBar.this.repaint();
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
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            boolean sel = marked && id.equals(selected);
            int w = getWidth();
            int h = getHeight();
            int arc = Theme.arc(8);
            // the same 2 px margin FlatLaf keeps around buttons and fields for the focus ring, so all are equally high
            int top = FOCUS_MARGIN;
            int bh = h - 2 * FOCUS_MARGIN - 1;
            g.setColor(sel ? Theme.alpha(Theme.ACCENT, 40) : hover ? Theme.alpha(Theme.TEXT, 12) : Theme.SURFACE);
            Theme.fillRound(g, 0, top, w - 1, bh, arc, arc);
            g.setColor(sel ? Theme.ACCENT_HOVER : Theme.BORDER);
            g.setStroke(new java.awt.BasicStroke(sel ? 1.5f : 1f));
            Theme.drawRound(g, 0, top, w - 1, bh, arc, arc);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(sel || hover ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, 0.35));
            g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }
}
