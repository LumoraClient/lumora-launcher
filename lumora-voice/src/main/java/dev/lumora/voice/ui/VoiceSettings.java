package dev.lumora.voice.ui;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.audio.AudioBackend;
import dev.lumora.voice.audio.AudioDevices;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings card of LumoraVoice in the Lumora hub (gear of the addon card, or the menu key): every setting as a
 * Lumora setting in sections, bound to {@link VoiceConfig} so {@code lumoravoice.json} stays the only store and keys
 * like "mute" show up at once. Lists that change all the time (audio devices, voice channels, block list) open small
 * dialogs; everything about one player is in the player menu ({@link PlayerActions}).
 * <p>
 * Labels and help texts come from the language files: {@code lumoravoice.setting.<key>} (+ {@code .description},
 * {@code .button}, option ids) and {@code lumoravoice.section.<name>}.
 */
public final class VoiceSettings {
    static final long CONFIRM_MILLIS = 5000;
    private final VoiceConfig config;
    private final Supplier<VoiceClient> voice;
    private final ViewHost host;
    private final Runnable dirty;
    private final SettingsGroup s;

    private VoiceSettings(SettingsGroup settings, VoiceConfig config, Supplier<VoiceClient> voice, ViewHost host, Runnable dirty) {
        this.s = settings; this.config = config; this.voice = voice; this.host = host; this.dirty = dirty;
    }

    /**
     * Adds all settings to {@code settings} (the addon configuration, once in {@code onInitialize}). They read and write
     * {@code config}, which outlives switching the addon off and on; {@code voice} is the running client or null while the
     * addon is off (then actions like reconnecting are skipped). {@code dirty} runs after every change; the addon then
     * writes {@code lumoravoice.json}.
     */
    public static void register(SettingsGroup settings, VoiceConfig config, Supplier<VoiceClient> voice, ViewHost host, Runnable dirty) {
        new VoiceSettings(settings, config, voice, host, dirty).build();
    }

    private VoiceConfig c() { return config; }

    /** Runs {@code action} with the running client; nothing while the addon is switched off. */
    private void live(Consumer<VoiceClient> action) {
        VoiceClient client = voice.get();
        if (client != null) action.accept(client);
    }

    private Runnable withVoice(Consumer<VoiceClient> action) { return () -> live(action); }

    private void build() {
        // --- general
        bind(s.toggle("enabled", false), () -> c().enabled, v -> c().enabled = v, VoiceClient::changed).section("lumoravoice.section.general");
        bind(s.dropdown("service", VoiceConfig.Service.AUTO), () -> c().service, v -> c().service = v, VoiceClient::reconnect);
        bind(s.toggle("speak_in_menus", false), () -> c().speakInMenus, v -> c().speakInMenus = v, null);
        bind(s.toggle("pause_unfocused", true), () -> c().pauseUnfocused, v -> c().pauseUnfocused = v, null);
        bind(s.toggle("notification_sounds", true), () -> c().notificationSounds, v -> c().notificationSounds = v, null);

        // --- mute
        bind(s.toggle("muted", false), () -> c().muted, v -> c().muted = v, VoiceClient::changed).section("lumoravoice.section.mute");
        bind(s.toggle("output_muted", false), () -> c().outputMuted, v -> c().outputMuted = v, VoiceClient::changed);
        bind(s.toggle("deafened", false), () -> c().deafened, v -> c().deafened = v, VoiceClient::changed);

        // --- devices (microphone and speaker)
        s.button("microphone", withVoice(v -> pickDevice(true))).section("lumoravoice.section.devices");
        s.button("speaker", withVoice(v -> pickDevice(false)));
        BooleanSupplier engines = AudioBackend.OPENAL::available;
        bind(s.toggle("alternative_audio", false), () -> c().alternativeAudioInterfaces, v -> c().alternativeAudioInterfaces = v, VoiceClient::devicesChanged)
                .visibleWhen(engines);
        bind(s.dropdown("input_backend", AudioBackend.JAVAX), () -> c().inputBackend, v -> { c().inputBackend = v; c().inputDevice = ""; }, VoiceClient::devicesChanged)
                .visibleWhen(() -> engines.getAsBoolean() && c().alternativeAudioInterfaces);
        bind(s.dropdown("output_backend", AudioBackend.JAVAX), () -> c().outputBackend, v -> { c().outputBackend = v; c().outputDevice = ""; }, VoiceClient::devicesChanged)
                .visibleWhen(() -> engines.getAsBoolean() && c().alternativeAudioInterfaces);
        s.button("microphone_test", withVoice(this::toggleTest));
        s.button("reopen_devices", withVoice(v -> { v.devicesChanged(); host.notify("Mikrofon und Lautsprecher werden neu geöffnet."); }));

        // --- microphone
        bind(s.selection("activation", VoiceConfig.Activation.PUSH_TO_TALK), () -> c().activation, v -> c().activation = v, VoiceClient::changed)
                .section("lumoravoice.section.microphone");
        bind(s.toggle("toggle_ptt", false), () -> c().togglePtt, v -> c().togglePtt = v, null)
                .visibleWhen(() -> c().activation == VoiceConfig.Activation.PUSH_TO_TALK);
        bind(s.toggle("automatic_gain", false), () -> c().automaticGain, v -> c().automaticGain = v, VoiceClient::apply);
        bind(s.slider("microphone_gain", 0, -30, 12).unit(" dB"), () -> (int) Math.round(gainDb(c().microphoneVolume)), v -> c().microphoneVolume = gainFactor(v), VoiceClient::apply)
                .visibleWhen(() -> !c().automaticGain);
        bind(s.toggle("auto_threshold", false), () -> c().autoThreshold, v -> c().autoThreshold = v, VoiceClient::apply)
                .visibleWhen(() -> c().activation == VoiceConfig.Activation.VOICE);
        bind(s.slider("threshold", -40, -70, -10).unit(" dB"), () -> Math.round(c().activationThresholdDb), v -> c().activationThresholdDb = v, VoiceClient::apply)
                .visibleWhen(() -> c().activation == VoiceConfig.Activation.VOICE && !c().autoThreshold);
        bind(s.slider("release", 300, 0, 1000).step(20).unit(" ms"), () -> c().releaseMillis, v -> c().releaseMillis = v, VoiceClient::apply)
                .visibleWhen(() -> c().activation == VoiceConfig.Activation.VOICE);

        // --- microphone filters
        BooleanSetting gate = bind(s.toggle("noise_gate", false), () -> c().noiseGate, v -> c().noiseGate = v, VoiceClient::apply)
                .section("lumoravoice.section.filters");
        bind(s.slider("gate_threshold", -55, -80, -20).unit(" dB"), () -> Math.round(c().gateThresholdDb), v -> c().gateThresholdDb = v, VoiceClient::apply).requires(gate);
        bind(s.toggle("high_pass", true), () -> c().highPassFilter, v -> c().highPassFilter = v, VoiceClient::apply);
        bind(s.toggle("noise_suppression", true), () -> c().noiseSuppression, v -> c().noiseSuppression = v, VoiceClient::devicesChanged);

        // --- playback
        percent("output_volume", 100, 200, () -> c().outputVolume, v -> c().outputVolume = v).section("lumoravoice.section.playback");
        bind(s.slider("range", 18, 5, 64).unit(" Blöcke"), () -> Math.round(c().surroundRange), v -> c().surroundRange = v, VoiceClient::apply);
        bind(s.selection("spatial", VoiceConfig.SpatialMode.NORMAL), () -> c().spatial(),
                v -> { c().spatialMode = v; c().spatialAudio = v != VoiceConfig.SpatialMode.OFF; }, VoiceClient::apply);
        bind(s.toggle("compressor", true), () -> c().volumeCompressor, v -> c().volumeCompressor = v, VoiceClient::apply);

        // --- surroundings (echo and muffling)
        BooleanSetting ambient = bind(s.toggle("ambient", false), () -> c().ambientSound, v -> c().ambientSound = v, VoiceClient::apply)
                .section("lumoravoice.section.ambient");
        bind(s.toggle("echo", true), () -> c().ambientEcho, v -> c().ambientEcho = v, VoiceClient::apply).requires(ambient);
        bind(s.toggle("muffle", true), () -> c().ambientMuffle, v -> c().ambientMuffle = v, VoiceClient::apply).requires(ambient);

        // --- screamer protection
        BooleanSetting screamer = bind(s.toggle("screamer", true), () -> c().screamerProtection, v -> c().screamerProtection = v, VoiceClient::apply)
                .section("lumoravoice.section.screamer");
        bind(s.slider("screamer_level", 75, 20, 100).unit(" %"), () -> Math.round(c().screamerProtectionLevel), v -> c().screamerProtectionLevel = v, VoiceClient::apply).requires(screamer);
        bind(s.slider("screamer_max", 15, 1, 100).unit(" %"), () -> Math.round(c().screamerMaxVolume), v -> c().screamerMaxVolume = v, VoiceClient::apply).requires(screamer);

        // --- display (the HUD widget's own options are in the "HUD · LumoraVoice" part of the card)
        bind(s.toggle("show_hud", true), () -> c().showHud, v -> c().showHud = v, null).section("lumoravoice.section.display");
        bind(s.toggle("name_icons", true), () -> c().showNameIcons, v -> c().showNameIcons = v, null);
        bind(s.toggle("tab_icons", true), () -> c().showTabIcons, v -> c().showTabIcons = v, null);
        bind(s.toggle("channel_names", true), () -> c().showChannelNames, v -> c().showChannelNames = v, null);
        bind(s.toggle("speech_wave", false), () -> c().speechBubbleWave, v -> c().speechBubbleWave = v, null);
        s.button("hud_editor", withVoice(v -> host.openHudEditor()));

        // --- players (volume, mute and focus of one player: player menu)
        percent("focused_volume", 150, 200, () -> c().focusedVolume, v -> c().focusedVolume = v).section("lumoravoice.section.players");
        percent("unfocused_volume", 50, 200, () -> c().unfocusedVolume, v -> c().unfocusedVolume = v);
        s.button("reset_players", confirmed("Spielerlautstärken zurücksetzen", () -> {
            int count = c().players.size();
            c().players.clear(); dirty.run(); live(VoiceClient::apply);
            host.notify(count + " Spielereinstellungen zurückgesetzt.");
        }));

        // --- blocking
        s.button("block_list", withVoice(v -> host.open(new BlockListView(v, host)))).section("lumoravoice.section.blocking");
        BooleanSetting pause = bind(s.toggle("block_pause_mic", true), () -> c().blockPauseMic, v -> c().blockPauseMic = v, null);
        bind(s.slider("block_pause_range", 48, 8, 128).step(4).unit(" Blöcke"), () -> Math.round(c().blockPauseRange), v -> c().blockPauseRange = v, null).requires(pause);
        bind(s.toggle("block_match_names", true), () -> c().blockMatchNames, v -> c().blockMatchNames = v, null);

        // --- channels (Laby Voice)
        s.button("channel_list", withVoice(v -> host.open(new ChannelListView(v, host)))).section("lumoravoice.section.channels");
        BooleanSetting own = bind(s.toggle("channel_activation_own", false), () -> c().useChannelActivation, v -> c().useChannelActivation = v, VoiceClient::changed);
        bind(s.selection("channel_activation", VoiceConfig.Activation.VOICE), () -> c().channelActivation, v -> c().channelActivation = v, VoiceClient::changed).requires(own);

        // --- connection
        s.button("reconnect", withVoice(v -> { v.reconnect(); host.notify("Verbindung wird neu aufgebaut …"); })).section("lumoravoice.section.connection");
        bind(s.toggle("backup_server", false), () -> c().backupServer, v -> c().backupServer = v, null);
        bind(s.toggle("test_server", false), () -> c().testServer, v -> c().testServer = v, null);
        s.button("reset", confirmed("Einstellungen zurücksetzen", () -> {
            c().reset(); live(v -> { v.disconnect(); v.audio().testing(false); v.changed(); });
            host.notify("LumoraVoice ist auf die Standardwerte zurückgesetzt. Die Blockliste bleibt.");
        }));
    }

    /** Reads and writes the setting through {@link VoiceConfig}; after a change {@code after} runs with the running client (null: nothing). */
    private <T, S extends Setting<T>> S bind(S setting, Supplier<T> get, Consumer<T> set, Consumer<VoiceClient> after) {
        setting.bind(get, value -> {
            set.accept(value);
            dirty.run();
            if (after != null) live(after);
        });
        return setting;
    }

    /** A volume factor (1.0 = 100 %) as a percent slider. */
    private Setting<Integer> percent(String key, int defaultPercent, int maxPercent, Supplier<Float> get, Consumer<Float> set) {
        return bind(s.slider(key, defaultPercent, 0, maxPercent).step(5).unit(" %"), () -> Math.round(get.get() * 100), v -> set.accept(v / 100f), VoiceClient::apply);
    }

    /** The first click asks for confirmation (notification), a second one within five seconds runs {@code action}. */
    Runnable confirmed(String what, Runnable action) {
        long[] armed = {0};
        return () -> {
            long now = System.currentTimeMillis();
            if (armed[0] == 0 || now - armed[0] > CONFIRM_MILLIS) {
                armed[0] = now;
                host.notify(what + ": zum Bestätigen innerhalb von 5 Sekunden noch einmal klicken.");
                return;
            }
            armed[0] = 0;
            action.run();
        };
    }

    private void toggleTest(VoiceClient client) {
        boolean on = !client.audio().testing();
        client.audio().testing(on);
        host.notify(on ? "Mikrofontest läuft: Du hörst dich selbst. Noch einmal klicken beendet ihn." : "Mikrofontest beendet.");
    }

    private void pickDevice(boolean input) {
        List<AudioDevices.Device> devices;
        try { devices = AudioDevices.list(input ? c().inputBackend : c().outputBackend, input); }
        catch (RuntimeException | LinkageError unavailable) {
            host.notify("Audiogeräte nicht verfügbar. Andere Engine wählen oder Geräte neu öffnen.");
            devices = List.of(new AudioDevices.Device("", "Systemstandard"));
        }
        String current = input ? c().inputDevice : c().outputDevice;
        host.open(new DevicePickerView(host, input ? "Mikrofon auswählen" : "Lautsprecher auswählen", devices, current, id -> {
            if (input) c().inputDevice = id; else c().outputDevice = id;
            dirty.run(); live(VoiceClient::devicesChanged);
        }));
    }

    /** Microphone gain: factor 1.0 = 0 dB, -30 dB (almost silent) to +12 dB (factor 4). */
    static double gainDb(float factor) { return factor <= 0 ? -30 : Math.clamp(20 * Math.log10(factor), -30, 12); }

    static float gainFactor(double db) { return (float) Math.pow(10, Math.clamp(db, -30, 12) / 20); }
}
