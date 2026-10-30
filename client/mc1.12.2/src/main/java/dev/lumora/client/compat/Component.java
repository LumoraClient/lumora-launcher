package dev.lumora.client.compat;

import net.minecraft.client.resource.language.I18n;

/**
 * The part of the modern {@code net.minecraft.network.chat.Component} that the Lumora screens use, for Minecraft 1.12.2:
 * there the GUI draws plain strings with legacy formatting codes (section sign). A component is resolved when it is
 * drawn, so a language switch is picked up without rebuilding the screen.
 */
public abstract class Component {
    private static final Component EMPTY = literal("");
    private Style style = Style.EMPTY;

    /** Text without formatting codes. */
    protected abstract String raw();

    public static Component literal(String text) {
        return new Literal(text);
    }

    public static Component translatable(String key, Object... args) {
        return new Translatable(key, args);
    }

    public static Component empty() {
        return EMPTY;
    }

    /** The text as shown, including formatting codes from {@link #withStyle}. */
    public String getString() {
        return style.prefix() + raw();
    }

    public Component withStyle(Style style) {
        Component copy = copy();
        copy.style = style;
        return copy;
    }

    protected abstract Component copy();

    @Override
    public String toString() {
        return getString();
    }

    private static final class Literal extends Component {
        private final String text;

        Literal(String text) {
            this.text = text;
        }

        @Override
        protected String raw() {
            return text;
        }

        @Override
        protected Component copy() {
            return new Literal(text);
        }
    }

    private static final class Translatable extends Component {
        private final String key;
        private final Object[] args;

        Translatable(String key, Object[] args) {
            this.key = key;
            this.args = args;
        }

        @Override
        protected String raw() {
            Object[] resolved = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                resolved[i] = args[i] instanceof Component c ? c.getString() : args[i];
            }
            return I18n.translate(key, resolved);
        }

        @Override
        protected Component copy() {
            return new Translatable(key, args);
        }
    }

    /** Text style; 1.12.2 only knows the legacy codes, so only what Lumora uses (bold) is modelled. */
    public static final class Style {
        public static final Style EMPTY = new Style(false);
        private final boolean bold;

        private Style(boolean bold) {
            this.bold = bold;
        }

        public Style withBold(boolean bold) {
            return new Style(bold);
        }

        String prefix() {
            return bold ? "§l" : "";
        }
    }
}
