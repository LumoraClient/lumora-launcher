package dev.lumora.api.interaction;

import dev.lumora.api.render.TextureRef;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One entry of the {@link PlayerMenu}: a label, an icon (a character such as {@code "✉"} or a 16x16 texture), an optional
 * category, when it is shown and what it does. Build with {@link #builder(String, String)}.
 *
 * <pre>{@code
 * context.playerMenu().add(PlayerMenuEntry.builder("report", "Melden")
 *         .icon("⚑").category("Moderation", "⚒")
 *         .requiresPermissionLevel(2)
 *         .visibleWhen(PlayerTarget::multiplayer)
 *         .onSelect(t -> chat.send("/report " + t.name()))
 *         .build());
 * }</pre>
 *
 * @since 1.4.0
 */
public final class PlayerMenuEntry {
    private final String id;
    private final Function<PlayerTarget, String> label;
    private final String icon;
    private final TextureRef texture;
    private final String category;
    private final String categoryIcon;
    private final int order;
    private final int permissionLevel;
    private final boolean keepOpen;
    private final Predicate<PlayerTarget> visible;
    private final Predicate<PlayerTarget> enabled;
    private final Consumer<PlayerTarget> action;

    private PlayerMenuEntry(Builder b) {
        id = b.id;
        label = b.label;
        icon = b.icon;
        texture = b.texture;
        category = b.category;
        categoryIcon = b.categoryIcon;
        order = b.order;
        permissionLevel = b.permissionLevel;
        keepOpen = b.keepOpen;
        visible = b.visible;
        enabled = b.enabled;
        action = b.action;
    }

    /**
     * @param id    lower case letters, digits, {@code _} and {@code -}
     * @param label shown when the entry is chosen
     */
    public static Builder builder(String id, String label) {
        return new Builder(id, label);
    }

    public String id() {
        return id;
    }

    /** The label for {@code target}. */
    public String label(PlayerTarget target) {
        String s = label.apply(target);
        return s == null || s.isBlank() ? id : s;
    }

    /** A character drawn as the icon, or null. */
    public String icon() {
        return icon;
    }

    /** A 16x16 icon texture, or null. */
    public TextureRef texture() {
        return texture;
    }

    /** Name of the category (a row that opens its entries as a list), or null for an entry in the main list. */
    public String category() {
        return category;
    }

    /** Icon of the category segment, or null. */
    public String categoryIcon() {
        return categoryIcon;
    }

    /** Lower comes first (Lumora's own entries use 0 to 100). */
    public int order() {
        return order;
    }

    /** Your operator level the entry needs (0 = none). */
    public int permissionLevel() {
        return permissionLevel;
    }

    /** The menu stays open after the entry ran (it changes what the menu shows). */
    public boolean keepsMenuOpen() {
        return keepOpen;
    }

    /** Shown for {@code target}: the permission level is reached and the visibility condition holds. */
    public boolean visibleFor(PlayerTarget target) {
        return target.permissionLevel() >= permissionLevel && visible.test(target);
    }

    /** Can be chosen for {@code target} (a disabled entry is shown greyed out). */
    public boolean enabledFor(PlayerTarget target) {
        return enabled.test(target);
    }

    /** Runs the entry (client thread; after the menu closed unless it {@linkplain #keepsMenuOpen keeps it open}). */
    public void select(PlayerTarget target) {
        action.accept(target);
    }

    public static final class Builder {
        private final String id;
        private Function<PlayerTarget, String> label;
        private String icon;
        private TextureRef texture;
        private String category;
        private String categoryIcon;
        private int order = 1000;
        private int permissionLevel;
        private boolean keepOpen;
        private Predicate<PlayerTarget> visible = t -> true;
        private Predicate<PlayerTarget> enabled = t -> true;
        private Consumer<PlayerTarget> action = t -> { };

        private Builder(String id, String label) {
            if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
                throw new IllegalArgumentException("id: " + id);
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("label");
            }
            this.id = id;
            this.label = t -> label;
        }

        /** A label that depends on the player, e.g. "Anfrage annehmen" instead of "Freund hinzufügen". */
        public Builder label(Function<PlayerTarget, String> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** A character (or two) as the icon. */
        public Builder icon(String icon) {
            this.icon = icon == null || icon.isEmpty() ? null : icon.codePointCount(0, icon.length()) > 2
                    ? icon.substring(0, icon.offsetByCodePoints(0, 2)) : icon;
            return this;
        }

        /** A 16x16 texture as the icon (drawn instead of the character). */
        public Builder icon(TextureRef texture) {
            this.texture = texture;
            return this;
        }

        /** Puts the entry into a category: one row of the menu that opens its entries as a list of their own. */
        public Builder category(String name, String icon) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("category");
            }
            this.category = name.strip();
            this.categoryIcon = icon == null || icon.isEmpty() ? null : icon;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** Only shown with at least this operator level (1 moderator ... 4 owner). */
        public Builder requiresPermissionLevel(int level) {
            this.permissionLevel = Math.max(0, Math.min(4, level));
            return this;
        }

        /** Only shown for players that use Lumora. */
        public Builder requiresLumoraUser() {
            return visibleWhen(PlayerTarget::lumoraUser);
        }

        /** Only shown when {@code condition} holds (combined with earlier conditions). */
        public Builder visibleWhen(Predicate<PlayerTarget> condition) {
            Predicate<PlayerTarget> before = visible;
            Predicate<PlayerTarget> c = Objects.requireNonNull(condition, "condition");
            visible = t -> before.test(t) && c.test(t);
            return this;
        }

        /** Shown but greyed out unless {@code condition} holds. */
        public Builder enabledWhen(Predicate<PlayerTarget> condition) {
            this.enabled = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** The menu stays open after the entry ran. */
        public Builder keepMenuOpen() {
            this.keepOpen = true;
            return this;
        }

        public Builder onSelect(Consumer<PlayerTarget> action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        public PlayerMenuEntry build() {
            return new PlayerMenuEntry(this);
        }
    }
}
