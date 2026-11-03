package dev.lumora.launcher.app.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} that wraps into several rows and reports the height of all rows as its preferred size (the
 * plain FlowLayout always asks for one row, so wrapped items would be cut off).
 */
public final class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = size(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container c = target;
            while (targetWidth == 0 && c.getParent() != null) {
                c = c.getParent();
                targetWidth = c.getSize().width;
            }
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }
            Insets in = target.getInsets();
            int maxWidth = targetWidth - (in.left + in.right + getHgap() * 2);
            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);
            dim.width += in.left + in.right + getHgap() * 2;
            dim.height += in.top + in.bottom + getVgap() * 2;
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}
