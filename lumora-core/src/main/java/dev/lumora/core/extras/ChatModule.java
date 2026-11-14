package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.game.WorldAccess.TabEntry;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in module "Chat": timestamps, identical messages stacked into one line ("… (x3)"), a menu on every message
 * (copy the message, the sender's name or a link, reply privately), the chat window resized by dragging its edges,
 * background, text shadow, how long messages stay, a longer history and a sound when the own name is mentioned.
 * <p>
 * The version adapters call {@link #onMessage} before a line is added, read the static getters in Minecraft's chat
 * methods and pass the open chat screen to {@link #renderChatScreen} and {@link #chatClick}.
 */
public final class ChatModule implements LumoraAddon {
    private static volatile ChatModule active;
    /** True while Minecraft draws the chat (versions that decide the text shadow outside the chat class). */
    private static boolean renderingChat;

    static final Pattern NAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");
    private static final Pattern STAMP = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?::\\d{2})?(?: [AP]M)?] ");
    private static final Pattern STACKED = Pattern.compile(" \\(x\\d+\\)$");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"]+");
    private static final Pattern COMMAND = Pattern.compile(
            "(?i)^/(?:msg|tell|w|whisper|m|pm|dm|message|r)\\s+(\\.?[A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");
    private static final Pattern VANILLA = Pattern.compile("^<(\\.?[A-Za-z0-9_]{1,16})> ");
    private static final Pattern PREFIXED = Pattern.compile(
            "^(?:\\[[^\\]]{0,24}]\\s*)*(?:[^A-Za-z0-9_\\s.]{1,3}\\s*)?(\\.?[A-Za-z0-9_]{3,16})\\s*(?::|»|>|\\||->)");
    private static final long MENTION_GAP_MS = 1000;

    BooleanSetting timestamps;
    ChoiceSetting format;
    ColorSetting timestampColor;
    BooleanSetting stack;
    IntegerSetting history;
    BooleanSetting customSize;
    IntegerSetting width;
    IntegerSetting heightOpen;
    IntegerSetting heightClosed;
    BooleanSetting resize;
    BooleanSetting background;
    IntegerSetting backgroundOpacity;
    BooleanSetting textShadow;
    BooleanSetting fade;
    IntegerSetting fadeSeconds;
    BooleanSetting menu;
    BooleanSetting mention;
    IntegerSetting mentionVolume;

    private final Stacker stacker = new Stacker();
    private final Supplier<LocalTime> clock;
    private final Platform platform;
    final ChatOverlay overlay = new ChatOverlay(this);
    private Translations translations;
    /** Counts incoming messages (line indexes shift with every message). */
    volatile int messages;
    private long lastMention;

    public ChatModule() {
        this(null, LocalTime::now);
    }

    public ChatModule(Platform platform) {
        this(platform, LocalTime::now);
    }

    ChatModule(Supplier<LocalTime> clock) {
        this(null, clock);
    }

    ChatModule(Platform platform, Supplier<LocalTime> clock) {
        this.platform = platform;
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
        translations = context.translations();
        SettingsGroup s = context.configuration().settings();
        String k = "lumora.extras.chat.";
        timestamps = s.toggle("timestamps", true).name(k + "timestamps").section(k + "section.messages");
        format = s.selection("format", "24h", "24h", "24h_seconds", "12h").name(k + "format").requires(timestamps);
        timestampColor = s.color("timestamp_color", 0xFF8D9BB8, false).name(k + "timestamp_color").requires(timestamps);
        stack = s.toggle("stack", true).name(k + "stack");
        history = s.slider("history", 500, 100, 1000).step(50).name(k + "history").description(k + "history.description");

        background = s.toggle("background", true).name(k + "background").section(k + "section.look");
        backgroundOpacity = s.slider("background_opacity", 50, 0, 100).step(5).unit("%").name(k + "background_opacity")
                .requires(background);
        textShadow = s.toggle("text_shadow", true).name(k + "text_shadow");
        fade = s.toggle("fade", true).name(k + "fade").description(k + "fade.description");
        fadeSeconds = s.slider("fade_seconds", 10, 2, 120).unit(" s").name(k + "fade_seconds").requires(fade);

        customSize = s.toggle("custom_size", false).name(k + "custom_size").description(k + "custom_size.description")
                .section(k + "section.window");
        width = s.slider("width", 320, 40, 1000).step(5).unit(" px").name(k + "width").requires(customSize);
        heightOpen = s.slider("height_open", 180, 20, 800).step(5).unit(" px").name(k + "height_open").requires(customSize);
        heightClosed = s.slider("height_closed", 90, 20, 800).step(5).unit(" px").name(k + "height_closed")
                .description(k + "height_closed.description").requires(customSize);
        resize = s.toggle("resize", true).name(k + "resize").description(k + "resize.description");
        s.button("size_reset", () -> {
            customSize.set(false);
            width.reset();
            heightOpen.reset();
            heightClosed.reset();
            refreshChat();
        }).name(k + "size_reset");

        menu = s.toggle("menu", true).name(k + "menu").description(k + "menu.description").section(k + "section.extras");
        mention = s.toggle("mention_sound", true).name(k + "mention_sound").description(k + "mention_sound.description")
                .section(k + "section.sounds");
        mentionVolume = s.slider("mention_volume", 70, 5, 100).step(5).unit("%").name(k + "mention_volume").requires(mention);

        width.onChange(v -> refreshChat());
        customSize.onChange(v -> refreshChat());
    }

    @Override
    public void onEnable(AddonContext context) {
        stacker.reset();
        overlay.reset();
        active = this;
        refreshChat();
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
        refreshChat();
    }

    private void refreshChat() {
        if (platform != null) {
            try {
                platform.extras().refreshChat();
            } catch (RuntimeException ignored) {
                // no chat yet (title screen)
            }
        }
    }

    // --- incoming messages

    /**
     * @param plain the message as plain text (before Lumora's own decorations)
     * @return timestamp text or null, how often the same line came in a row, and whether the previous line has to be
     *         replaced by this one
     */
    public synchronized Line onMessage(String plain) {
        messages++;
        int count = stack.get() ? stacker.add(plain) : 1;
        if (count == 1) {
            checkMention(plain);
        }
        return new Line(timestamps.get() ? timestamp() : null, count, count > 1);
    }

    private void checkMention(String plain) {
        if (platform == null || mention == null || !mention.get()) {
            return;
        }
        String own = platform.game().player().map(p -> p.name()).orElse("");
        long now = System.currentTimeMillis();
        if (mentions(plain, own, tabNames()) && now - lastMention >= MENTION_GAP_MS) {
            lastMention = now;
            platform.extras().playSound("mention", mentionVolume.get() / 100f);
        }
    }

    /** Whether {@code text} contains {@code own} as a word and was not written by {@code own}. */
    static boolean mentions(String text, String own, Collection<String> online) {
        if (text == null || own == null || own.isEmpty() || indexOfWord(text, own) < 0) {
            return false;
        }
        String sender = sender(text, List.of(), List.of(), online);
        return sender == null || !sender.equalsIgnoreCase(own);
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

    // --- read by the chat mixins (every frame, so cheap)

    /** Chat width in pixels, -1 for Minecraft's own setting. */
    public static int width() {
        ChatModule m = active;
        return m != null && m.customSize.get() ? m.width.get() : -1;
    }

    /** Chat height in pixels (open or closed chat), -1 for Minecraft's own setting. */
    public static int height(boolean open) {
        ChatModule m = active;
        if (m == null || !m.customSize.get()) {
            return -1;
        }
        return open ? m.heightOpen.get() : Math.min(m.heightClosed.get(), m.heightOpen.get());
    }

    /** Opacity of the line background (0..1). */
    public static double backgroundOpacity(double vanilla) {
        ChatModule m = active;
        if (m == null) {
            return vanilla;
        }
        return m.background.get() ? m.backgroundOpacity.get() / 100.0 : 0;
    }

    public static boolean textShadow() {
        ChatModule m = active;
        return m == null || m.textShadow.get();
    }

    /** Shadow of chat text for drawing code outside the chat class: only changed while the chat is drawn. */
    public static boolean textShadow(boolean shadow) {
        return renderingChat ? shadow && textShadow() : shadow;
    }

    public static void renderingChat(boolean drawing) {
        renderingChat = drawing;
    }

    /** Ticks a message stays in the closed chat (Minecraft: 200, the last tenth fading out). */
    public static double fadeTicks(double vanilla) {
        ChatModule m = active;
        if (m == null) {
            return vanilla;
        }
        return m.fade.get() ? m.fadeSeconds.get() * 20.0 : 1.0e9;
    }

    public static int fadeTicks(int vanilla) {
        return (int) Math.min(Integer.MAX_VALUE / 2, fadeTicks((double) vanilla));
    }

    /** Messages kept in the chat history (Minecraft: 100). */
    public static int history(int vanilla) {
        ChatModule m = active;
        return m == null ? vanilla : Math.max(vanilla, m.history.get());
    }

    // --- open chat screen

    /** Draws the resize frame, the message highlight and the menu on top of the open chat (GUI pixels). */
    public static void renderChatScreen(HudGraphics g, int mouseX, int mouseY) {
        ChatModule m = active;
        if (m != null && m.platform != null) {
            m.overlay.render(g, m.platform.extras(), mouseX, mouseY, System.currentTimeMillis());
        }
    }

    /** A click in the open chat; true if Lumora used it (Minecraft must not see it). */
    public static boolean chatClick(double x, double y, int button) {
        ChatModule m = active;
        return m != null && m.platform != null && m.overlay.click(m.platform.extras(), x, y, button);
    }

    String tr(String key, Object... args) {
        return translations == null ? key : translations.translate("lumora.extras.chat." + key, args);
    }

    void copy(String text) {
        if (platform != null) {
            platform.views().setClipboard(text);
        }
    }

    void click() {
        if (platform != null) {
            try {
                platform.world().playClickSound();
            } catch (RuntimeException ignored) {
                // no world access
            }
        }
    }

    /** A message for addons (Lumora API 1.5): plain text without Lumora's timestamp, the sender and the links. */
    public static dev.lumora.api.chat.ChatLine line(String plain, java.util.Collection<String> online) {
        String text = clean(plain);
        return new dev.lumora.api.chat.ChatLine(text, sender(text, List.of(), List.of(), online), links(text, List.of()));
    }

    String ownName() {
        return platform == null ? "" : platform.game().player().map(p -> p.name()).orElse("");
    }

    List<String> tabNames() {
        if (platform == null) {
            return List.of();
        }
        try {
            List<String> out = new ArrayList<>();
            for (TabEntry e : platform.world().tabList()) {
                out.add(e.name());
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // --- message text

    /** The message without Lumora's own timestamp and "(x3)". */
    static String clean(String text) {
        String t = text == null ? "" : text.strip();
        t = STAMP.matcher(t).replaceFirst("");
        return STACKED.matcher(t).replaceFirst("");
    }

    /**
     * Who wrote a message: the name a server put in a shift-click insertion or in a /msg suggestion, Minecraft's
     * "&lt;Name&gt;", a player of the tab list in front of the first ":" / "»" / "&gt;", or a name in front of such
     * a separator.
     *
     * @return the name, or null if no sender can be found
     */
    static String sender(String text, List<String> insertions, List<String> commands, Collection<String> online) {
        String t = clean(text);
        for (String s : insertions) {
            String n = s == null ? "" : s.strip();
            if (NAME.matcher(n).matches() && indexOfWord(t, n) >= 0) {
                return n;
            }
        }
        for (String c : commands) {
            Matcher m = COMMAND.matcher(c == null ? "" : c.strip());
            if (m.find()) {
                return m.group(1);
            }
        }
        Matcher v = VANILLA.matcher(t);
        if (v.find()) {
            return v.group(1);
        }
        int head = separator(t);
        String inHead = null;
        int inHeadAt = Integer.MAX_VALUE;
        String anywhere = null;
        int anywhereAt = Integer.MAX_VALUE;
        for (String n : online) {
            if (n == null || n.isEmpty()) {
                continue;
            }
            int i = indexOfWord(t, n);
            if (i < 0) {
                continue;
            }
            if (head >= 0 && i < head && (i < inHeadAt || i == inHeadAt && n.length() > inHead.length())) {
                inHead = n;
                inHeadAt = i;
            }
            if (i < anywhereAt || i == anywhereAt && n.length() > anywhere.length()) {
                anywhere = n;
                anywhereAt = i;
            }
        }
        if (inHead != null) {
            return inHead;
        }
        Matcher p = PREFIXED.matcher(t);
        if (p.find()) {
            return p.group(1);
        }
        return anywhere;
    }

    /** Links of a message: those of its click events first, then web addresses in the text (at most 3). */
    static List<String> links(String text, List<String> clickLinks) {
        Set<String> out = new LinkedHashSet<>();
        for (String l : clickLinks) {
            if (l != null && !l.isBlank()) {
                out.add(l.strip());
            }
        }
        Matcher m = URL.matcher(text == null ? "" : text);
        while (m.find()) {
            String u = m.group().replaceAll("[.,;:!?)\\]}'\"]+$", "");
            if (out.stream().noneMatch(l -> l.contains(u))) {
                out.add(u);
            }
        }
        return out.stream().limit(3).toList();
    }

    private static int separator(String t) {
        int best = -1;
        for (String s : new String[] {":", "»", ">", "|", "->"}) {
            int i = t.indexOf(s);
            if (i > 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    /** Index of {@code word} in {@code text} as a whole name (case-insensitive), -1 if it is not there. */
    static int indexOfWord(String text, String word) {
        String t = text.toLowerCase(Locale.ROOT);
        String w = word.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int i = t.indexOf(w, from);
            if (i < 0) {
                return -1;
            }
            int end = i + w.length();
            boolean before = i == 0 || !nameChar(t.charAt(i - 1));
            boolean after = end >= t.length() || !nameChar(t.charAt(end));
            if (before && after) {
                return i;
            }
            from = i + 1;
        }
    }

    private static boolean nameChar(char c) {
        return c == '_' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
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
