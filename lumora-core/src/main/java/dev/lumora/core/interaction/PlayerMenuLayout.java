package dev.lumora.core.interaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ring of the player menu: one segment per entry or category, the first one at the top, clockwise. A category
 * opens its entries as an outer ring next to its segment, in the same menu. Angles are compass angles in radians
 * (0 = up, clockwise, screen y pointing down).
 *
 * @param <E> the entry type
 */
public final class PlayerMenuLayout<E> {
    /** Radii in GUI pixels. */
    public static final int DEAD = 14;
    public static final int INNER = 30;
    public static final int OUTER = 70;
    public static final int CHILD_INNER = 74;
    public static final int CHILD_OUTER = 104;
    /** At most this many children share the outer ring evenly; fewer keep the width of one tenth of the circle. */
    static final int CHILD_SLOTS = 10;

    /** A segment of the ring: one entry, or a category with its entries. */
    public static final class Slot<E> {
        public final String category;
        public final String icon;
        public final List<E> entries = new ArrayList<>();

        Slot(String category, String icon) {
            this.category = category;
            this.icon = icon;
        }

        public boolean isCategory() {
            return category != null;
        }
    }

    /** What the mouse points at: a segment ({@code child} -1) or an entry of an opened category; {@code slot} -1 = nothing. */
    public record Selection(int slot, int child) {
        public static final Selection NONE = new Selection(-1, -1);

        public boolean none() {
            return slot < 0;
        }
    }

    /** Category name and icon of an entry (null name: not in a category). */
    public interface Grouping<E> {
        String category(E entry);

        String categoryIcon(E entry);
    }

    private final List<Slot<E>> slots;

    private PlayerMenuLayout(List<Slot<E>> slots) {
        this.slots = slots;
    }

    /** Segments in the order of {@code entries}; a category sits where its first entry is. */
    public static <E> PlayerMenuLayout<E> of(List<E> entries, Grouping<E> grouping) {
        List<Slot<E>> slots = new ArrayList<>();
        Map<String, Slot<E>> categories = new LinkedHashMap<>();
        for (E e : entries) {
            String c = grouping.category(e);
            if (c == null) {
                Slot<E> s = new Slot<>(null, null);
                s.entries.add(e);
                slots.add(s);
                continue;
            }
            Slot<E> s = categories.get(c);
            if (s == null) {
                s = new Slot<>(c, grouping.categoryIcon(e));
                categories.put(c, s);
                slots.add(s);
            }
            s.entries.add(e);
        }
        return new PlayerMenuLayout<>(slots);
    }

    public List<Slot<E>> slots() {
        return slots;
    }

    public int size() {
        return slots.size();
    }

    /** Angle covered by one segment. */
    public double slotWidth() {
        return slots.isEmpty() ? 2 * Math.PI : 2 * Math.PI / slots.size();
    }

    /** Middle angle of segment {@code i}. */
    public double center(int i) {
        return i * slotWidth();
    }

    /** Angle covered by one entry of the outer ring of a category with {@code count} entries. */
    public static double childWidth(int count) {
        return 2 * Math.PI / Math.max(CHILD_SLOTS, count);
    }

    /** Where the outer ring of category segment {@code i} starts (it is centered on the segment). */
    public double childStart(int i) {
        int m = slots.get(i).entries.size();
        return center(i) - m * childWidth(m) / 2;
    }

    /** Middle angle of entry {@code j} of the outer ring of segment {@code i}. */
    public double childCenter(int i, int j) {
        int m = slots.get(i).entries.size();
        return childStart(i) + (j + 0.5) * childWidth(m);
    }

    /** Compass angle of the offset (dx, dy) from the center, 0 to 2&pi;. */
    public static double compass(double dx, double dy) {
        return norm(Math.atan2(dx, -dy));
    }

    static double norm(double a) {
        double r = a % (2 * Math.PI);
        return r < 0 ? r + 2 * Math.PI : r;
    }

    /**
     * What the mouse at offset (dx, dy) from the center points at; {@code open} is the category segment whose outer
     * ring is shown (-1 none). Near the center nothing is chosen; further out the angle decides, so the mouse may
     * leave the ring.
     */
    public Selection hit(double dx, double dy, int open) {
        double dist = Math.hypot(dx, dy);
        if (slots.isEmpty() || dist < DEAD) {
            return Selection.NONE;
        }
        double a = compass(dx, dy);
        if (open >= 0 && open < slots.size() && slots.get(open).isCategory() && dist > OUTER + 2) {
            int m = slots.get(open).entries.size();
            double w = childWidth(m);
            int j = (int) Math.floor(norm(a - childStart(open)) / w);
            if (j < m) {
                return new Selection(open, j);
            }
        }
        int i = (int) Math.floor(norm(a + slotWidth() / 2) / slotWidth()) % slots.size();
        return new Selection(i, -1);
    }

    /** Everything the mouse wheel steps through: entries of the ring, and the entries of each category in its place. */
    public List<Selection> order() {
        List<Selection> l = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot<E> s = slots.get(i);
            if (s.isCategory()) {
                for (int j = 0; j < s.entries.size(); j++) {
                    l.add(new Selection(i, j));
                }
            } else {
                l.add(new Selection(i, -1));
            }
        }
        return l;
    }

    /** The entry behind a selection, or null (nothing, or a category segment itself). */
    public E entry(Selection s) {
        if (s == null || s.none() || s.slot >= slots.size()) {
            return null;
        }
        Slot<E> slot = slots.get(s.slot);
        if (s.child < 0) {
            return slot.isCategory() ? null : slot.entries.get(0);
        }
        return s.child < slot.entries.size() ? slot.entries.get(s.child) : null;
    }

    /**
     * Pixel runs of a ring split into {@code count} segments of {@code width} starting at {@code start}, with a gap of
     * {@code gap} pixels between segments, at {@code q} sub-pixels per GUI pixel: groups of four ints (row, first
     * column, end column, segment), relative to the center. Rows and columns are in sub-pixels.
     */
    public static int[] runs(double inner, double outer, int q, double start, double width, int count, double gap) {
        IntList out = new IntList();
        int rows = (int) Math.ceil(outer * q);
        for (int sy = -rows; sy < rows; sy++) {
            double y = (sy + 0.5) / q;
            if (Math.abs(y) > outer) {
                continue;
            }
            double xmax = Math.sqrt(outer * outer - y * y);
            int from = (int) Math.floor(-xmax * q);
            int to = (int) Math.ceil(xmax * q);
            int runStart = 0;
            int runSeg = -1;
            for (int sx = from; sx <= to; sx++) {
                int seg = -1;
                if (sx < to) {
                    double x = (sx + 0.5) / q;
                    double r = Math.hypot(x, y);
                    if (r >= inner && r <= outer) {
                        double d = norm(compass(x, y) - start);
                        int k = (int) Math.floor(d / width);
                        if (k < count) {
                            double edge = Math.min(d - k * width, (k + 1) * width - d) * r;
                            if (count == 1 && width >= 2 * Math.PI - 1e-9 || edge >= gap / 2) {
                                seg = k;
                            }
                        }
                    }
                }
                if (seg != runSeg) {
                    if (runSeg >= 0) {
                        out.add(sy, runStart, sx, runSeg);
                    }
                    runSeg = seg;
                    runStart = sx;
                }
            }
        }
        return out.toArray();
    }

    private static final class IntList {
        private int[] a = new int[256];
        private int n;

        void add(int... v) {
            if (n + v.length > a.length) {
                a = java.util.Arrays.copyOf(a, Math.max(a.length * 2, n + v.length));
            }
            System.arraycopy(v, 0, a, n, v.length);
            n += v.length;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }
}
