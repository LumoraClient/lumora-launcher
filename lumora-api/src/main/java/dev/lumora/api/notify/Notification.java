package dev.lumora.api.notify;

import dev.lumora.api.render.TextureRef;

import java.util.Objects;

/** Immutable notification. Build with {@link #builder(String)}; texts are shown as given (translate them first). */
public final class Notification {
    private final String title;
    private final String message;
    private final NotificationType type;
    private final TextureRef icon;
    private final long durationMillis;

    private Notification(Builder b) {
        this.title = b.title;
        this.message = b.message;
        this.type = b.type;
        this.icon = b.icon;
        this.durationMillis = b.durationMillis;
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public NotificationType type() {
        return type;
    }

    /** Optional 16x16 icon (drawn from a 16x16 texture). */
    public TextureRef icon() {
        return icon;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public static final class Builder {
        private final String title;
        private String message = "";
        private NotificationType type = NotificationType.INFO;
        private TextureRef icon;
        private long durationMillis = 4000;

        private Builder(String title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        public Builder message(String message) {
            this.message = Objects.requireNonNull(message, "message");
            return this;
        }

        public Builder type(NotificationType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        public Builder icon(TextureRef icon) {
            this.icon = icon;
            return this;
        }

        /** How long it stays (1-30 s, default 4 s). */
        public Builder duration(long millis) {
            this.durationMillis = Math.max(1000, Math.min(30_000, millis));
            return this;
        }

        public Notification build() {
            return new Notification(this);
        }
    }
}
