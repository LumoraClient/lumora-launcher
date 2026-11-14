package dev.lumora.api.chat;

import dev.lumora.api.render.TextureRef;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A button at the chat input (Lumora's counterpart of LabyMod's {@code ChatButtonWidget}): a row of small buttons
 * inside the input line at its right end (the text field gets shorter by the row). A button shows an icon (part of a texture) or a short text, its tooltip
 * on hover, and either opens a {@link ChatPanel} or runs an action. Build with {@link #builder}, add with
 * {@link Chat#addInputButton}.
 *
 * <pre>{@code
 * context.chat().addInputButton(ChatInputButton.builder("emojis", "Emojis")
 *         .icon(new TextureRef("myaddon", "textures/gui/emoji.png"), 0, 0, 16, 16, 16, 16)
 *         .panel(() -> new EmojiPanel(context.chat()))
 *         .build());
 * }</pre>
 *
 * @since 1.9.0
 */
public final class ChatInputButton {
    private final String id;
    private final String tooltip;
    private final String label;
    private final Icon icon;
    private final Supplier<ChatPanel> panel;
    private final Runnable action;
    private final BooleanSupplier visible;

    /**
     * Part of a texture drawn as the button's icon (10 x 10 pixels on screen).
     *
     * @param u             left edge of the part in texture pixels
     * @param v             top edge of the part in texture pixels
     * @param regionWidth   width of the part in texture pixels
     * @param regionHeight  height of the part in texture pixels
     * @param textureWidth  width of the whole texture in pixels
     * @param textureHeight height of the whole texture in pixels
     */
    public record Icon(TextureRef texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        public Icon {
            Objects.requireNonNull(texture, "texture");
            if (regionWidth <= 0 || regionHeight <= 0 || textureWidth <= 0 || textureHeight <= 0) {
                throw new IllegalArgumentException("icon size");
            }
        }
    }

    private ChatInputButton(Builder b) {
        id = b.id;
        tooltip = b.tooltip;
        label = b.label;
        icon = b.icon;
        panel = b.panel;
        action = b.action;
        visible = b.visible;
    }

    /**
     * @param id      lower case letters, digits, {@code _} and {@code -}, unique per addon
     * @param tooltip text shown above the button on hover (translate it yourself)
     */
    public static Builder builder(String id, String tooltip) {
        return new Builder(id, tooltip);
    }

    public String id() {
        return id;
    }

    public String tooltip() {
        return tooltip;
    }

    /** Text on the button, or null when it shows an {@link #icon()}. */
    public String label() {
        return label;
    }

    /** The icon, or null when it shows a {@link #label()}. */
    public Icon icon() {
        return icon;
    }

    /** Whether a click opens a panel ({@link #createPanel()}) instead of running {@link #press()}. */
    public boolean hasPanel() {
        return panel != null;
    }

    /** A new panel for this button (each time it opens). */
    public ChatPanel createPanel() {
        return panel == null ? null : panel.get();
    }

    /** Runs the button's action (client thread). */
    public void press() {
        if (action != null) {
            action.run();
        }
    }

    /** Whether the button is shown right now (asked every frame). */
    public boolean visible() {
        return visible.getAsBoolean();
    }

    public static final class Builder {
        private final String id;
        private final String tooltip;
        private String label;
        private Icon icon;
        private Supplier<ChatPanel> panel;
        private Runnable action;
        private BooleanSupplier visible = () -> true;

        private Builder(String id, String tooltip) {
            if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
                throw new IllegalArgumentException("id: " + id);
            }
            if (tooltip == null || tooltip.isBlank()) {
                throw new IllegalArgumentException("tooltip");
            }
            this.id = id;
            this.tooltip = tooltip;
        }

        /** Part of a texture as icon (see {@link Icon}). */
        public Builder icon(TextureRef texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
            this.icon = new Icon(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
            this.label = null;
            return this;
        }

        /** A short text instead of an icon, e.g. {@code "☺"} or {@code "GG"}. */
        public Builder label(String label) {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("label");
            }
            this.label = label;
            this.icon = null;
            return this;
        }

        /** A click opens this panel (a new one each time); replaces {@link #onPress}. */
        public Builder panel(Supplier<ChatPanel> panel) {
            this.panel = Objects.requireNonNull(panel, "panel");
            this.action = null;
            return this;
        }

        /** A click runs this action; replaces {@link #panel}. */
        public Builder onPress(Runnable action) {
            this.action = Objects.requireNonNull(action, "action");
            this.panel = null;
            return this;
        }

        /** Shows the button only while {@code condition} is true, e.g. bound to a setting. */
        public Builder visibleWhen(BooleanSupplier condition) {
            BooleanSupplier before = visible;
            BooleanSupplier c = Objects.requireNonNull(condition, "condition");
            visible = () -> before.getAsBoolean() && c.getAsBoolean();
            return this;
        }

        public ChatInputButton build() {
            if (label == null && icon == null) {
                throw new IllegalStateException("a chat input button needs an icon or a label");
            }
            if (panel == null && action == null) {
                throw new IllegalStateException("a chat input button needs a panel or an action");
            }
            return new ChatInputButton(this);
        }
    }
}
