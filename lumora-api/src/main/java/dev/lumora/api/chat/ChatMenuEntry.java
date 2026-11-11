package dev.lumora.api.chat;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An entry of the menu of a chat message (right click or "⋯" on a message in the open chat), after Lumora's own
 * entries (copy, copy name, reply, copy link). Needs the chat module to be on. Build with {@link #builder}.
 *
 * <pre>{@code
 * context.chat().addMenuEntry(ChatMenuEntry.builder("translate", "Übersetzen")
 *         .visibleWhen(line -> !line.text().isBlank())
 *         .onSelect(line -> translate(line.text()))
 *         .build());
 * }</pre>
 *
 * @since 1.5.0
 */
public final class ChatMenuEntry {
    private final String id;
    private final Function<ChatLine, String> label;
    private final Predicate<ChatLine> visible;
    private final Consumer<ChatLine> action;

    private ChatMenuEntry(Builder b) {
        id = b.id;
        label = b.label;
        visible = b.visible;
        action = b.action;
    }

    /** @param id lower case letters, digits, {@code _} and {@code -} */
    public static Builder builder(String id, String label) {
        return new Builder(id, label);
    }

    public String id() {
        return id;
    }

    public String label(ChatLine line) {
        String s = label.apply(line);
        return s == null || s.isBlank() ? id : s;
    }

    public boolean visibleFor(ChatLine line) {
        return visible.test(line);
    }

    /** Runs the entry (client thread, the menu closes first). */
    public void select(ChatLine line) {
        action.accept(line);
    }

    public static final class Builder {
        private final String id;
        private Function<ChatLine, String> label;
        private Predicate<ChatLine> visible = l -> true;
        private Consumer<ChatLine> action = l -> { };

        private Builder(String id, String label) {
            if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
                throw new IllegalArgumentException("id: " + id);
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("label");
            }
            this.id = id;
            this.label = l -> label;
        }

        /** A label that depends on the message, e.g. "An Steve antworten". */
        public Builder label(Function<ChatLine, String> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        public Builder visibleWhen(Predicate<ChatLine> condition) {
            Predicate<ChatLine> before = visible;
            Predicate<ChatLine> c = Objects.requireNonNull(condition, "condition");
            visible = l -> before.test(l) && c.test(l);
            return this;
        }

        public Builder onSelect(Consumer<ChatLine> action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        public ChatMenuEntry build() {
            return new ChatMenuEntry(this);
        }
    }
}
