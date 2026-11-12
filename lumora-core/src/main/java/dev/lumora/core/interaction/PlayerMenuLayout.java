package dev.lumora.core.interaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The action list of the player menu: one row per entry or category, in the order of the entries. A category sits
 * where its first entry is and opens its entries as a list of their own (with a row back to the main list).
 *
 * @param <E> the entry type
 */
public final class PlayerMenuLayout<E> {
    /** A row of the main list: one entry, or a category with its entries. */
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

    /** Category name and icon of an entry (null name: not in a category). */
    public interface Grouping<E> {
        String category(E entry);

        String categoryIcon(E entry);
    }

    private final List<Slot<E>> slots;

    private PlayerMenuLayout(List<Slot<E>> slots) {
        this.slots = slots;
    }

    /** Rows in the order of {@code entries}; a category sits where its first entry is. */
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

    /** The open category of that name, or null (it is gone, e.g. its entries are no longer shown). */
    public Slot<E> category(String name) {
        if (name == null) {
            return null;
        }
        for (Slot<E> s : slots) {
            if (name.equals(s.category)) {
                return s;
            }
        }
        return null;
    }
}
