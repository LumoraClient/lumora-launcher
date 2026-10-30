package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.SettingsGroup;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Built-in module "Chat": timestamps in front of every chat line and identical messages stacked into one line
 * ("… (x3)"). The version adapters call {@link #onMessage} from Minecraft's chat before a line is added.
 */
public final class ChatModule implements LumoraAddon {
    private static volatile ChatModule active;

    BooleanSetting timestamps;
    ChoiceSetting format;
    ColorSetting timestampColor;
    BooleanSetting stack;
    private final Stacker stacker = new Stacker();
    private final Supplier<LocalTime> clock;

    public ChatModule() {
        this(LocalTime::now);
    }

    ChatModule(Supplier<LocalTime> clock) {
        this.clock = clock;
    }

    public static ChatModule active() {
        return active;
    }

    /** What the adapter does with an incoming line. */
    public record Line(String timestamp, int count, boolean replacePrevious) {
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        timestamps = (BooleanSetting) s.toggle("timestamps", true).name("lumora.extras.chat.timestamps");
        format = (ChoiceSetting) s.choice("format", "24h", "24h", "24h_seconds", "12h").name("lumora.extras.chat.format");
        timestampColor = (ColorSetting) s.color("timestamp_color", 0xFF9D95AE, false).name("lumora.extras.chat.timestamp_color").advanced();
        stack = (BooleanSetting) s.toggle("stack", true).name("lumora.extras.chat.stack");
    }

    @Override
    public void onEnable(AddonContext context) {
        stacker.reset();
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    /**
     * @param plain the message as plain text (before Lumora's own decorations)
     * @return timestamp text or null, how often the same line came in a row, and whether the previous line has to be
     *         replaced by this one
     */
    public synchronized Line onMessage(String plain) {
        int count = stack.get() ? stacker.add(plain) : 1;
        return new Line(timestamps.get() ? timestamp() : null, count, count > 1);
    }

    public int timestampColor() {
        return timestampColor.argb();
    }

    String timestamp() {
        DateTimeFormatter f = switch (format.get()) {
            case "24h_seconds" -> DateTimeFormatter.ofPattern("HH:mm:ss");
            case "12h" -> DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
            default -> DateTimeFormatter.ofPattern("HH:mm");
        };
        return "[" + clock.get().format(f) + "]";
    }

    /** Counts identical lines in a row; blank lines never stack. */
    static final class Stacker {
        private String last;
        private int count;

        int add(String plain) {
            String p = plain == null ? "" : plain.strip();
            if (!p.isEmpty() && p.equals(last)) {
                count++;
            } else {
                last = p;
                count = 1;
            }
            return count;
        }

        void reset() {
            last = null;
            count = 0;
        }
    }
}
