package dev.lumora.voice.lumora;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.chat.ChatMenuEntry;
import dev.lumora.api.event.client.ClientStoppingEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.NameTags;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.VoiceLog;
import dev.lumora.voice.render.NameTagState;
import dev.lumora.voice.ui.ChannelListView;
import dev.lumora.voice.ui.PlayerActions;
import dev.lumora.voice.ui.Theme;
import dev.lumora.voice.ui.View;
import dev.lumora.voice.ui.ViewHost;
import dev.lumora.voice.ui.VoiceSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Entry of the LumoraVoice addon ({@code lumora-addon.json}). Wires the version-independent {@link VoiceClient} to the
 * Lumora API: settings card, tick, keys, HUD widget, player menu and name tag icons. There is no menu of its own any
 * more: the settings are in the addon card of the Lumora hub, everything about one player is in the player menu, and
 * only lists (devices, channels, block list) open small dialogs. Switching the addon off in the Lumora hub closes the
 * voice connection and the audio devices; Lumora removes keys, widget, entries and icons.
 */
public final class LumoraVoiceAddon implements LumoraAddon {
    private VoiceConfig config;
    private volatile VoiceClient voice;
    private VoiceHudWidget widget;
    private Host host;
    private volatile Keybind ptt;
    private volatile boolean dirty;

    @Override
    public void onInitialize(AddonContext context) {
        VoiceLog.use(new VoiceLog.Sink() {
            @Override public void info(String message) { context.logger().info(message); }
            @Override public void warn(String message, Throwable error) { if (error == null) context.logger().warn(message); else context.logger().warn(message, error); }
        });
        config = VoiceConfig.load(configFile(context.configDirectory()));
        host = new Host(context, () -> { var key = ptt; return key == null ? "?" : context.keybinds().keyName(key.key()); });
        // the settings card of the Lumora hub; values stay in lumoravoice.json (written at most once per second)
        VoiceSettings.register(context.configuration().settings(), config, () -> voice, host, () -> {
            if (voice != null) dirty = true;
            else config.save();
        });
    }

    @Override
    public void onEnable(AddonContext context) throws Exception {
        Theme.reload(() -> LumoraVoiceAddon.class.getResourceAsStream("/assets/lumoravoice/theme.json"));
        // the voice windows take the look of the client (accent, surfaces, text) chosen in the hub
        Theme.follow(context.theme());
        String version = context.descriptor().version().toString();
        VoiceClient voice = new VoiceClient(new LumoraPlatform(context), config, version);
        this.voice = voice;
        var game = context.game();

        var keys = context.keybinds();
        Keybind ptt = keys.register("ptt", "lumoravoice.key.ptt", Keys.V, () -> { if (!game.screenOpen()) voice.pushToTalkPressed(); });
        Keybind whisper = keys.register("whisper", "lumoravoice.key.whisper", Keys.UNBOUND, () -> { });
        this.ptt = ptt;
        voice.keys(ptt::isDown, whisper::isDown);
        active = this;
        keys.register("channels", "lumoravoice.key.channels", Keys.B, () -> { if (!game.screenOpen()) host.open(new ChannelListView(voice, host)); });
        keys.register("mute", "lumoravoice.key.mute", Keys.M, () -> { if (!game.screenOpen()) voice.toggleMute(); });
        keys.register("deafen", "lumoravoice.key.deafen", Keys.N, () -> { if (!game.screenOpen()) voice.toggleOutputMute(); });
        keys.register("icons", "lumoravoice.key.icons", Keys.H, () -> { if (!game.screenOpen()) voice.toggleHud(); });

        widget = new VoiceHudWidget(voice, host::pushToTalkKey, () -> dirty = true);
        context.widgets().register(widget);
        // settings card and widget options changed: write lumoravoice.json at most once per second
        context.tasks().repeat(20, () -> { if (dirty) { dirty = false; voice.save(); } });

        context.nameTags().register(this::icon);

        // player menu (hold the mouse wheel on a player, Lumora API 1.4): volume, mute, focus and block in "VoiceChat"
        var menu = context.playerMenu();
        PlayerActions.entries(voice, host).forEach(menu::add);
        // chat menu of a message (right click or "⋯", Lumora API 1.5): the same window for the sender
        context.chat().addMenuEntry(ChatMenuEntry.builder("voicechat", "VoiceChat")
                .visibleWhen(line -> PlayerActions.chatTarget(voice, line.sender()).isPresent())
                .label(line -> "VoiceChat · " + PlayerActions.chatTarget(voice, line.sender()).map(p -> p.name()).orElse(""))
                .onSelect(line -> PlayerActions.chatTarget(voice, line.sender())
                        .ifPresent(p -> host.open(PlayerActions.window(voice, host, p.id(), p.name()))))
                .build());

        context.events().subscribe(ClientTickEvent.class, e -> {
            voice.tick();
            widget.update(game.inWorld());
        });
        context.events().subscribe(ClientStoppingEvent.class, e -> voice.close());
        context.logger().info("LumoraVoice " + version + " on Minecraft " + context.lumora().minecraftVersion() + " enabled. The microphone opens only when requested.");
    }

    private Optional<NameTags.Icon> icon(java.util.UUID id, NameTags.Place place) {
        var voice = this.voice;
        if (voice == null) return Optional.empty();
        var c = voice.config();
        if (place == NameTags.Place.WORLD ? !c.showNameIcons : !c.showTabIcons) return Optional.empty();
        var state = voice.nameTagState(id);
        if (state == NameTagState.ABSENT) return Optional.empty();
        // channel under the name only when it differs from the own channel
        String channel = place == NameTags.Place.WORLD && c.showChannelNames ? voice.channelOf(id) : "";
        var channels = voice.channels();
        if (!channel.isEmpty() && channels != null && channel.equals(channels.current().name())) channel = "";
        return Optional.of(new NameTags.Icon(NameTagState.FONT, state.glyph(), state.color(), state.fallback(), channel.isEmpty() ? "" : "[" + channel + "]"));
    }

    @Override
    public void onDisable(AddonContext context) {
        if (active == this) active = null;
        Theme.follow(null);
        var client = voice;
        voice = null;
        ptt = null;
        dirty = false;
        if (client != null) { client.close(); client.save(); }
    }

    // --- developer smoke test of the Lumora client (called by reflection; no microphone, no connection) ---

    private static volatile LumoraVoiceAddon active;

    /** Switches voice chat on or off without connecting (singleplayer); returns the previous value, or null without LumoraVoice. */
    public static Boolean smokeSetEnabled(boolean on) {
        var addon = active;
        if (addon == null || addon.voice == null) return null;
        boolean previous = addon.voice.config().enabled;
        addon.voice.config().enabled = on;
        return previous;
    }

    public static void smokeRestore(Boolean previous) {
        var addon = active;
        if (addon != null && addon.voice != null && previous != null) { addon.voice.config().enabled = previous; addon.voice.save(); }
    }

    /**
     * Opens a LumoraVoice dialog through the Lumora screen API (the settings themselves are a Lumora settings screen):
     * 1 = the voice window of a sample player (player menu), anything else = voice channels. The name stays for the
     * client's smoke test.
     */
    public static boolean smokeOpenSettings(int tab) {
        var addon = active;
        var voice = addon == null ? null : addon.voice;
        if (voice == null) return false;
        addon.host.open(tab == 1 ? PlayerActions.window(voice, addon.host, new java.util.UUID(0, 7), "Steve") : new ChannelListView(voice, addon.host));
        return true;
    }

    /** {@code lumoravoice.json}; the settings of LabyVoice (labyvoice.json) are taken over once. */
    static Path configFile(Path directory) {
        Path file = directory.resolve("lumoravoice.json"), legacy = directory.resolve("labyvoice.json");
        try {
            if (Files.notExists(file) && Files.isRegularFile(legacy)) {
                Files.createDirectories(directory);
                Files.copy(legacy, file);
                VoiceLog.info("Settings taken over from labyvoice.json");
            }
        } catch (IOException failure) { VoiceLog.warn("Could not take over " + legacy, failure); }
        return file;
    }

    /** Dialogs, settings card and notifications through the Lumora API. */
    private static final class Host implements ViewHost {
        private final AddonContext context;
        private final java.util.function.Supplier<String> pttKey;

        Host(AddonContext context, java.util.function.Supplier<String> pttKey) {
            this.context = context; this.pttKey = pttKey;
        }

        @Override public void open(View view) { context.screens().open(view); }
        @Override public void back() { context.screens().back(); }
        @Override public boolean inWorld() { return context.game().inWorld(); }
        @Override public String clipboard() { return context.screens().clipboard(); }
        @Override public void openHudEditor() { context.lumora().hudEditor().open(); }
        @Override public String pushToTalkKey() { return pttKey.get(); }

        @Override public void notify(String message) {
            try { context.notifications().info("LumoraVoice", message); }
            catch (RuntimeException switchedOff) { VoiceLog.info(message); }
        }
    }
}
