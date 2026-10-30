package dev.lumora.client.compat;

/** Widget tooltip text (modern {@code Tooltip}); {@link Screen} draws the tooltip of the hovered widget last. */
public final class Tooltip {
    private final Component text;

    private Tooltip(Component text) {
        this.text = text;
    }

    public static Tooltip create(Component text) {
        return new Tooltip(text);
    }

    public Component text() {
        return text;
    }
}
