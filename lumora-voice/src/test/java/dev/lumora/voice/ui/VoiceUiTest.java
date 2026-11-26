package dev.lumora.voice.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.lumora.api.config.ButtonSetting;
import dev.lumora.api.config.OptionSetting;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.input.Keys;
import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.api.interaction.PlayerTarget;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.audio.SilentAudio;
import dev.lumora.voice.platform.VoicePlatform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Settings card, player menu entries and the small dialogs without Minecraft or a running Lumora client. */
class VoiceUiTest {
    @TempDir Path directory;
    private VoiceConfig config;
    private VoiceClient voice;
    private VoiceClient running;
    private Host host;
    private SettingsGroup settings;
    private int dirty;
    private FakeGraphics g;

    /** GUI sizes: 1280x800 window at GUI scale 3, scale 2, and a small 320x240 window. */
    private static final int[][] SIZES = {{426, 266}, {640, 400}, {320, 240}};

    static final class Host implements ViewHost {
        final Deque<View> stack = new ArrayDeque<>();
        final List<String> notices = new ArrayList<>();
        String clipboard = "";
        int hudEditor;
        @Override public void open(View view) { stack.push(view); }
        @Override public void back() { var top = stack.poll(); if (top != null) top.removed(); }
        @Override public boolean inWorld() { return false; }
        @Override public String clipboard() { return clipboard; }
        @Override public void openHudEditor() { hudEditor++; }
        @Override public void notify(String message) { notices.add(message); }
        @Override public String pushToTalkKey() { return "V"; }
    }

    static final class Platform implements VoicePlatform {
        List<Named> tabList = new ArrayList<>();
        @Override public Optional<Profile> profile() { return Optional.of(new Profile(new UUID(1, 1), "Me")); }
        @Override public void joinServer(String serverHash) {}
        @Override public Optional<Server> server() { return Optional.empty(); }
        @Override public boolean screenOpen() { return true; }
        @Override public boolean windowFocused() { return true; }
        @Override public Optional<Listener> listener() { return Optional.empty(); }
        @Override public List<Peer> players(int limit) { return List.of(); }
        @Override public boolean occluded(Peer peer) { return false; }
        @Override public int seaLevel() { return 63; }
        @Override public List<Named> tabList() { return tabList; }
        @Override public Optional<Named> targetPlayer() { return Optional.empty(); }
        @Override public void actionBar(String message) {}
        @Override public void uiSound() {}
        @Override public Channel channel(String id, java.util.function.Consumer<byte[]> receiver) {
            return new Channel() { public boolean serverSupports() { return false; } public void send(byte[] payload) {} };
        }
        @Override public boolean modLoaded(String id) { return false; }
        @Override public Path configDir() { return Path.of("."); }
        @Override public String minecraftVersion() { return "1.8.9"; }
    }

    record Target(UUID id, String name) implements PlayerTarget {
        @Override public boolean multiplayer() { return true; }
        @Override public boolean lumoraUser() { return false; }
        @Override public boolean friend() { return false; }
        @Override public boolean inYourParty() { return false; }
        @Override public boolean partyLeader() { return false; }
        @Override public int permissionLevel() { return 0; }
    }

    private final Platform platform = new Platform();

    @BeforeEach void setUp() throws Exception {
        config = VoiceConfig.load(directory.resolve("lumoravoice.json"));
        Constructor<VoiceClient> constructor = VoiceClient.class.getDeclaredConstructor(VoicePlatform.class, VoiceConfig.class, String.class, dev.lumora.voice.audio.VoiceAudio.class);
        constructor.setAccessible(true);
        voice = constructor.newInstance(platform, config, "0.8.4", SilentAudio.create(config));
        running = voice;
        host = new Host();
        settings = new SettingsGroup("lumoravoice.setting.");
        VoiceSettings.register(settings, config, () -> running, host, () -> { dirty++; config.save(); });
        g = new FakeGraphics();
    }

    @AfterEach void tearDown() { voice.close(); }

    @SuppressWarnings("unchecked")
    private <T> Setting<T> setting(String key) { return (Setting<T>) settings.get(key).orElseThrow(() -> new AssertionError("no setting " + key)); }

    private void press(String key) { ((ButtonSetting) settings.get(key).orElseThrow()).press(); }

    private VoiceConfig saved() { return VoiceConfig.load(directory.resolve("lumoravoice.json")); }

    // --- settings card

    @Test void settingsReadAndWriteTheVoiceConfig() {
        Setting<Boolean> muted = setting("muted");
        assertFalse(muted.get());
        assertTrue(muted.set(true));
        assertTrue(config.muted, "written into lumoravoice.json's config");
        assertTrue(saved().muted, "saved");
        assertEquals(1, dirty);
        assertTrue(muted.isBound(), "Lumora does not store a second copy");
        config.outputMuted = true; // e.g. the mute key
        assertTrue(this.<Boolean>setting("output_muted").get(), "keys show up at once");
    }

    @Test void slidersConvertUnitsAndClamp() {
        Setting<Integer> volume = setting("output_volume");
        assertEquals(100, volume.get());
        volume.set(150);
        assertEquals(1.5f, config.outputVolume, 1e-6);
        volume.set(999);
        assertEquals(2f, config.outputVolume, 1e-6, "clamped to 200 %");
        Setting<Integer> gain = setting("microphone_gain");
        assertEquals(0, gain.get(), "factor 1.0 is 0 dB");
        gain.set(12);
        assertEquals(4f, config.microphoneVolume, 0.02f);
        assertEquals(12, VoiceSettings.gainDb(VoiceSettings.gainFactor(12)), 1e-4);
        assertEquals(-30, VoiceSettings.gainDb(0));
        Setting<Integer> release = setting("release");
        release.set(333);
        assertEquals(340, config.releaseMillis, "steps of 20 ms");
    }

    @Test void dependentSettingsOnlyShowWhenTheyMatter() {
        assertTrue(setting("microphone_gain").isVisible());
        this.<Boolean>setting("automatic_gain").set(true);
        assertFalse(setting("microphone_gain").isVisible(), "manual gain hidden while automatic");
        assertFalse(setting("threshold").isVisible(), "push to talk has no voice threshold");
        assertTrue(setting("toggle_ptt").isVisible());
        ((OptionSetting) setting("activation")).select("voice");
        assertEquals(VoiceConfig.Activation.VOICE, config.activation);
        assertTrue(setting("threshold").isVisible());
        assertFalse(setting("toggle_ptt").isVisible());
        this.<Boolean>setting("auto_threshold").set(true);
        assertFalse(setting("threshold").isVisible(), "automatic threshold");
        assertFalse(setting("gate_threshold").isVisible(), "gate off");
        assertFalse(setting("channel_activation").isVisible());
        this.<Boolean>setting("channel_activation_own").set(true);
        assertTrue(setting("channel_activation").isVisible());
    }

    @Test void threeDimensionalAudioKeepsTheOldSwitchInStep() {
        var spatial = (OptionSetting) setting("spatial");
        assertEquals("normal", spatial.selectedId());
        spatial.select("off");
        assertEquals(VoiceConfig.SpatialMode.OFF, config.spatialMode);
        assertFalse(saved().spatialAudio, "the old switch follows");
        assertEquals(List.of("off", "normal", "strong"), spatial.optionIds());
    }

    @Test void buttonsOpenTheSmallDialogs() {
        press("microphone");
        assertInstanceOf(DevicePickerView.class, host.stack.peek());
        var picker = (DevicePickerView) host.stack.peek();
        picker.render(g, 640, 400, -1, -1);
        var first = picker.controls.getFirst();
        assertTrue(picker.mouseClicked(first.x() + 4, first.y() + 4, 0));
        assertTrue(host.stack.isEmpty(), "closed after picking");
        press("channel_list");
        assertInstanceOf(ChannelListView.class, host.stack.poll());
        press("block_list");
        assertInstanceOf(BlockListView.class, host.stack.poll());
        press("hud_editor");
        assertEquals(1, host.hudEditor);
    }

    @Test void resetAsksForConfirmationFirst() {
        config.muted = true;
        config.players.put(UUID.randomUUID().toString(), new VoiceConfig.Player());
        UUID blocked = UUID.randomUUID();
        config.block(blocked, "Griefer");
        press("reset");
        assertTrue(config.muted, "first click only asks");
        assertTrue(host.notices.getLast().contains("noch einmal klicken"));
        press("reset");
        assertFalse(config.muted);
        assertTrue(config.players.isEmpty());
        assertTrue(config.isBlocked(blocked), "the block list stays");
    }

    @Test void actionsWaitWhileTheAddonIsSwitchedOff() {
        running = null;
        press("channel_list");
        press("microphone");
        assertTrue(host.stack.isEmpty(), "no dialogs while LumoraVoice is off");
        this.<Boolean>setting("muted").set(true);
        assertTrue(saved().muted, "settings still change and are saved");
    }

    @Test void everySettingHasGermanAndEnglishTexts() throws Exception {
        for (String language : List.of("de_de", "en_us")) {
            Map<String, String> lang;
            try (var in = VoiceUiTest.class.getResourceAsStream("/assets/lumoravoice/lang/" + language + ".json")) {
                lang = new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() { }.getType());
            }
            for (Setting<?> s : settings) {
                assertTrue(lang.containsKey(s.nameKey()), language + ": " + s.nameKey());
                assertTrue(lang.containsKey(s.descriptionKey()), language + ": " + s.descriptionKey());
                if (s.sectionKey() != null) assertTrue(lang.containsKey(s.sectionKey()), language + ": " + s.sectionKey());
                if (s instanceof ButtonSetting b) assertTrue(lang.containsKey(b.labelKey()), language + ": " + b.labelKey());
                if (s instanceof OptionSetting o) for (String id : o.optionIds()) assertTrue(lang.containsKey(o.optionKey(id)), language + ": " + o.optionKey(id));
            }
        }
    }

    // --- player menu

    private PlayerVoiceView openVoiceWindow(Target target) {
        var entries = PlayerActions.entries(voice, host);
        assertEquals(1, entries.size());
        var entry = entries.getFirst();
        assertEquals("VoiceChat", entry.label(target));
        assertNull(entry.category(), "directly in the ring");
        entry.select(target);
        var window = assertInstanceOf(PlayerVoiceView.class, host.stack.peek());
        window.render(g, 640, 400, -1, -1);
        return window;
    }

    private void click(PlayerVoiceView window, String label) {
        var control = window.controls.stream().filter(c -> c.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no button " + label));
        assertTrue(window.mouseClicked(control.x() + 4, control.y() + 4, 0), label);
    }

    @Test void playerMenuOpensTheVoiceWindowWithMuteFocusAndBlock() {
        var steve = new Target(UUID.randomUUID(), "Steve");
        var window = openVoiceWindow(steve);
        assertTrue(g.drew("VoiceChat · Steve"));
        assertTrue(g.drew("Nicht im Voice-Chat"));
        click(window, "Stummschalten");
        assertTrue(config.existing(steve.id()).muted);
        click(window, "Stummschaltung aufheben");
        assertFalse(config.existing(steve.id()).muted);
        click(window, "Fokussieren");
        assertTrue(saved().existing(steve.id()).focused, "saved");
        click(window, "Fokus aufheben");
        assertFalse(config.existing(steve.id()).focused);
        click(window, "Blockieren");
        assertTrue(config.isBlocked(steve.id()));
        click(window, "Entblockieren");
        assertFalse(config.isBlocked(steve.id()));
        click(window, "Fertig");
        assertTrue(host.stack.isEmpty());
    }

    @Test void voiceWindowSliderSetsTheVolumeOfOnePlayer() {
        var steve = new Target(UUID.randomUUID(), "Steve");
        var window = openVoiceWindow(steve);
        assertTrue(g.drew("100 %"));
        var slider = window.slider();
        // drag the knob from the middle of the track to its right end: 200 %
        int trackLeft = slider.right() - 12 - Math.min(154, Math.max(68, (slider.right() - slider.x()) / 3));
        assertTrue(window.mouseClicked(trackLeft + 10, slider.y() + 31, 0));
        assertTrue(window.mouseDragged(slider.right() - 1, slider.y() + 31, 0));
        assertTrue(window.mouseReleased(slider.right() - 1, slider.y() + 31, 0));
        assertEquals(2f, config.existing(steve.id()).volume, 1e-6);
        assertEquals(2f, saved().existing(steve.id()).volume, 1e-6, "saved on release");
        click(window, "Stummschalten");
        click(window, "Zurücksetzen");
        assertNull(config.existing(steve.id()), "volume, mute and focus back to normal");
        window.keyPressed(Keys.ENTER, false, false);
        assertTrue(host.stack.isEmpty(), "Enter closes");
    }

    @Test void voiceWindowHasNoPlayerSwitchingAndFindsAMissingName() {
        UUID steve = UUID.randomUUID(), unknown = UUID.randomUUID();
        platform.tabList.add(new VoicePlatform.Named(steve, "Steve"));
        var window = openVoiceWindow(new Target(steve, ""));
        assertEquals("Steve", window.name(), "name from the tab list when the player menu sends none");
        assertTrue(g.drew("VoiceChat · Steve"));
        assertTrue(window.controls.stream().noneMatch(c -> c.label().contains("Vorheriger") || c.label().contains("Nächster")));
        click(window, "Stummschalten");
        assertEquals("Steve", config.existing(steve).name, "saved with the name");
        assertEquals(unknown.toString().substring(0, 8), PlayerVoiceView.displayName(voice, unknown, " "));
    }

    @Test void chatMenuFindsTheSenderOfAMessage() {
        UUID steve = UUID.randomUUID(), saved = UUID.randomUUID(), blocked = UUID.randomUUID();
        platform.tabList.addAll(List.of(new VoicePlatform.Named(steve, "Steve"), new VoicePlatform.Named(new UUID(1, 1), "Me")));
        config.player(saved, "OldFriend").volume = 1.5f;
        config.block(blocked, "Griefer");
        assertEquals(steve, PlayerActions.chatTarget(voice, "steve").orElseThrow().id(), "online, any case");
        assertEquals(saved, PlayerActions.chatTarget(voice, "OldFriend").orElseThrow().id(), "offline but with settings");
        assertEquals(blocked, PlayerActions.chatTarget(voice, " Griefer ").orElseThrow().id(), "blocked, so it can be unblocked from the chat");
        assertTrue(PlayerActions.chatTarget(voice, "Me").isEmpty(), "not for your own messages");
        assertTrue(PlayerActions.chatTarget(voice, "Nobody").isEmpty());
        assertTrue(PlayerActions.chatTarget(voice, null).isEmpty(), "server messages have no sender");
    }

    // --- dialogs

    @Test void blockListRemovesAndAddsPlayers() {
        UUID steve = UUID.randomUUID(), alex = UUID.randomUUID();
        config.block(alex, "Alex");
        platform.tabList.add(new VoicePlatform.Named(steve, "Steve"));
        var list = new BlockListView(voice, host);
        host.open(list);
        list.render(g, 640, 400, -1, -1);
        var alexRow = list.controls.stream().filter(c -> c.label().startsWith("Alex")).findFirst().orElseThrow();
        assertTrue(list.mouseClicked(alexRow.x() + 4, alexRow.y() + 4, 0));
        assertFalse(config.isBlocked(alex));
        g = new FakeGraphics(); list.render(g, 640, 400, -1, -1);
        assertTrue(g.drew("Alex ist nicht mehr blockiert."));
        assertTrue(g.drew("Niemand blockiert"));
        var add = list.controls.stream().filter(c -> c.label().equals("Hinzufügen …")).findFirst().orElseThrow();
        list.mouseClicked(add.x() + 4, add.y() + 4, 0);
        var dialog = assertInstanceOf(BlockPromptView.class, host.stack.peek());
        dialog.render(g, 640, 400, -1, -1);
        for (char ch : "steve".toCharArray()) dialog.charTyped(ch);
        assertEquals("steve", dialog.value());
        dialog.keyPressed(Keys.ENTER, false, false);
        assertTrue(config.isBlocked(steve));
        assertSame(list, host.stack.peek(), "back at the list");
        g = new FakeGraphics(); list.render(g, 640, 400, -1, -1);
        assertTrue(g.drew("Steve"), "list rebuilt with the new entry");
        assertTrue(g.drew("Steve ist blockiert."));
    }

    @Test void blockDialogRejectsInvalidInputAndSelf() {
        var dialog = new BlockPromptView(voice, host, message -> fail("nothing blocked"));
        host.open(dialog);
        dialog.render(g, 640, 400, -1, -1);
        for (char ch : "no spaces!".toCharArray()) dialog.charTyped(ch);
        dialog.keyPressed(Keys.ENTER, false, false);
        g = new FakeGraphics(); dialog.render(g, 640, 400, -1, -1);
        assertTrue(g.drew("Bitte einen Minecraft-Namen"));
        for (int i = 0; i < 20; i++) dialog.keyPressed(Keys.BACKSPACE, false, false);
        for (char ch : new UUID(1, 1).toString().toCharArray()) dialog.charTyped(ch);
        dialog.keyPressed(Keys.ENTER, false, false);
        g = new FakeGraphics(); dialog.render(g, 640, 400, -1, -1);
        assertTrue(g.drew("nicht selbst blockieren"));
        assertTrue(config.blocked.isEmpty());
    }

    @Test void longBlockListsArePaged() {
        for (int i = 0; i < ListDialog.PAGE + 3; i++) config.block(UUID.randomUUID(), "Player" + i);
        var list = new BlockListView(voice, host);
        list.render(g, 640, 400, -1, -1);
        assertEquals(2, list.pages());
        assertTrue(g.drew("1 / 2"));
        list.render(new FakeGraphics(), 320, 240, -1, -1);
        assertEquals(ListDialog.MIN_PAGE + 2, list.pageSize(), "fewer rows per page on a low GUI");
        assertEquals(3, list.pages());
        list.render(g, 640, 400, -1, -1);
        var next = list.controls.stream().filter(c -> c.label().equals("›")).findFirst().orElseThrow();
        list.mouseClicked(next.x() + 2, next.y() + 2, 0);
        assertEquals(1, list.page());
        assertTrue(list.controls.stream().anyMatch(c -> c.label().startsWith("Player" + (ListDialog.PAGE + 2))));
    }

    @Test void channelListExplainsWhyItIsEmpty() {
        var channels = new ChannelListView(voice, host);
        channels.render(g, 640, 400, -1, -1);
        assertTrue(g.drew("VoiceChat ist aus"));
        config.enabled = true;
        g = new FakeGraphics(); channels.render(g, 640, 400, -1, -1);
        assertTrue(g.drew("Noch nicht verbunden"));
        var done = channels.controls.stream().filter(c -> c.label().equals("Fertig")).findFirst().orElseThrow();
        host.open(channels);
        channels.mouseClicked(done.x() + 4, done.y() + 4, 0);
        assertTrue(host.stack.isEmpty(), "Fertig closes the dialog");
    }

    @Test void dialogsRenderAtEverySize() {
        for (int i = 0; i < 9; i++) config.block(UUID.randomUUID(), "Spieler mit sehr langem Namen " + i);
        for (int[] size : SIZES) {
            var devices = List.of(new dev.lumora.voice.audio.AudioDevices.Device("", "Systemstandard"), new dev.lumora.voice.audio.AudioDevices.Device("a", "Headset"));
            for (Dialog view : List.of(new BlockListView(voice, host), new ChannelListView(voice, host), new BlockPromptView(voice, host, m -> { }),
                    new DevicePickerView(host, "Mikrofon auswählen", devices, "a", id -> { }), new PlayerVoiceView(voice, host, UUID.randomUUID(), "Steve"))) {
                g = new FakeGraphics();
                view.render(g, size[0], size[1], -1, -1);
                assertEquals(0, g.depth, "push/pop balanced");
                assertEquals(0, g.scissors, "scissor closed");
                for (Control control : view.controls) {
                    assertTrue(control.bottom() <= size[1] && control.y() >= 0, control.label() + " on screen at " + size[0] + "x" + size[1]);
                }
            }
        }
    }

    // --- unchanged parts

    @Test void textFieldEditsPastesAndHidesPasswords() {
        host.clipboard = "a§b\nc";
        var field = new TextField(5, true, host::clipboard).bounds(0, 0, 100);
        field.focus(true);
        field.charTyped('x');
        field.keyPressed(Keys.V, true);
        assertEquals("xabc", field.value(), "section sign and control characters are dropped");
        field.keyPressed(Keys.HOME, false); field.keyPressed(Keys.DELETE, false);
        assertEquals("abc", field.value());
        for (char ch : "defg".toCharArray()) field.charTyped(ch);
        assertEquals(5, field.value().length(), "max length");
        var drawn = new FakeGraphics();
        field.render(drawn, 0xFFFFFFFF, 0);
        assertFalse(drawn.drew("abc"), "password text is never drawn");
        assertTrue(drawn.drew("•••"));
    }

    @Test void hudContentFollowsSettings() {
        assertNull(VoiceHudContent.build(voice, true, () -> "V"), "voice chat off");
        config.enabled = true;
        assertNull(VoiceHudContent.build(voice, false, () -> "V"), "no world");
        var content = VoiceHudContent.build(voice, true, () -> "V");
        assertNotNull(content);
        assertEquals("Voice nicht verbunden", content.state());
        assertTrue(content.disconnected());
        VoiceHudContent.draw(g, config, content, id -> new float[7]);
        assertEquals(VoiceHudContent.DISCONNECTED, content.icon());
        assertTrue(g.textures.contains(VoiceHudContent.DISCONNECTED), "plug while voice is not connected");
        config.muted = true;
        var muted = VoiceHudContent.build(voice, true, () -> "V");
        assertEquals("Mikrofon stumm", muted.state());
        assertEquals(VoiceHudContent.DISCONNECTED, muted.icon(), "the lost connection matters more than the muted microphone");
        var preview = VoiceHudContent.preview(config, "V");
        assertFalse(preview.disconnected());
        assertEquals(VoiceHudContent.MIC_IDLE, preview.icon());
        config.muted = false;
        assertEquals(VoiceHudContent.MIC_IDLE, VoiceHudContent.statusIcon(config, false, false, false, false));
        assertEquals(VoiceHudContent.MIC, VoiceHudContent.statusIcon(config, false, false, false, true));
        assertEquals(VoiceHudContent.MIC_MUTED, VoiceHudContent.statusIcon(config, false, true, false, false), "paused for a blocked player");
        assertEquals(VoiceHudContent.MIC_SERVER_MUTED, VoiceHudContent.statusIcon(config, false, false, true, false), "muted by the service: orange slash");
        config.outputMuted = true;
        assertEquals(VoiceHudContent.SPEAKER_MUTED, VoiceHudContent.statusIcon(config, false, false, false, true));
        config.outputMuted = false;
        assertEquals(2, preview.people().size());
        assertEquals(List.of("Steve · 6 m", "Alex · 14 m"), preview.labels());
        VoiceHudContent.draw(g, config, preview, id -> new float[7]);
        assertTrue(g.drew("Steve · 6 m"));
        assertEquals(25 + 2 * VoiceHudContent.HEAD_PITCH, VoiceHudContent.height(preview), "rows with player heads");
        assertTrue(g.texts.contains("S") && g.texts.contains("A"), "placeholder letters until a skin is loaded (preview UUIDs never go to Mojang)");
        config.showHeads = false;
        assertEquals(25 + 2 * 13, VoiceHudContent.height(VoiceHudContent.preview(config, "V")), "compact rows without heads");
    }

    @Test void themeOverridesAndBrokenFiles() {
        Theme.reload(() -> new java.io.ByteArrayInputStream("{\"colors\":{\"accent\":\"#112233\",\"text\":\"nope\"}}".getBytes()));
        assertEquals(0xFF112233, Theme.color("accent"));
        assertEquals(0xFFE8EAED, Theme.color("text"), "invalid value falls back");
        Theme.reload(() -> new java.io.ByteArrayInputStream("{broken".getBytes()));
        assertEquals(0xFF18B4CC, Theme.color("accent"));
        Theme.reload(null);
        assertEquals(0xFF18B4CC, Theme.color("accent"));
    }

    @Test void newLabyChannelsCarryOwnerAndServerLikeLabyMod() {
        UUID owner = new UUID(5, 6);
        var plain = ChannelPromptView.createProperties("Bauteam", owner, "play.example.net", "", true);
        assertEquals(List.of("name", "owner", "visibility", "serverAddress", "proximity"), List.copyOf(plain.keySet()));
        assertEquals(owner.toString(), plain.get("owner"));
        assertEquals("SERVER", plain.get("visibility"));
        assertEquals("play.example.net", plain.get("serverAddress"));
        assertEquals("true", plain.get("proximity"));
        assertFalse(plain.containsKey("password"), "no empty password");
        assertEquals("abc", ChannelPromptView.createProperties("x", owner, "s", "abc", false).get("password"));
    }

    @Test void memberLinesShowHeadAndNameButDoNothing() {
        boolean[] talking = {false}; int[] clicks = {0};
        var member = Control.member(new UUID(0, 9), "Skirsleif", () -> talking[0], () -> "Du").listItem().bounds(10, 10, 300, 22);
        assertTrue(member.isMember(), "listItem keeps the member style");
        assertFalse(member.focusable(), "not in the Tab order");
        var quiet = new FakeGraphics(); member.render(quiet, 20, 20);
        assertTrue(quiet.drew("Skirsleif")); assertTrue(quiet.drew("Du"));
        assertTrue(quiet.texts.contains("S"), "placeholder letter until the skin is loaded");
        talking[0] = true;
        var loud = new FakeGraphics(); member.render(loud, 20, 20);
        assertEquals(quiet.fills + 1, loud.fills, "white frame around the head while talking");
        var more = Control.member(null, "+ 3 weitere", () -> false, () -> "");
        var g2 = new FakeGraphics(); more.listItem().bounds(10, 40, 300, 22).render(g2, 0, 0);
        assertTrue(g2.drew("+ 3 weitere")); assertFalse(g2.texts.contains("+"), "no head");
        assertEquals(0, clicks[0]);
    }

    // --- sections

    /** Section of every setting in the order of the card (the heading set on the first setting of a section). */
    private static Map<String, String> sections(SettingsGroup group) {
        Map<String, String> out = new LinkedHashMap<>();
        String current = null;
        for (Setting<?> s : group.all()) {
            if (s.sectionKey() != null) current = s.sectionKey();
            out.put(s.key(), current);
        }
        return out;
    }

    private static Map<String, String> lang(String file) throws Exception {
        try (var in = VoiceUiTest.class.getResourceAsStream("/assets/lumoravoice/lang/" + file)) {
            assertNotNull(in, file);
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
        }
    }

    @Test void settingsAreSortedIntoSections() throws Exception {
        Map<String, String> s = sections(settings);
        assertEquals("lumoravoice.section.general", s.get("enabled"));
        assertEquals("lumoravoice.section.general", s.get("notification_sounds"));
        for (String key : List.of("muted", "output_muted", "deafened")) assertEquals("lumoravoice.section.mute", s.get(key), key);
        for (String key : List.of("microphone", "speaker", "alternative_audio", "input_backend", "output_backend", "microphone_test", "reopen_devices"))
            assertEquals("lumoravoice.section.devices", s.get(key), key);
        for (String key : List.of("activation", "toggle_ptt", "automatic_gain", "microphone_gain", "auto_threshold", "threshold", "release"))
            assertEquals("lumoravoice.section.microphone", s.get(key), key);
        for (String key : List.of("noise_gate", "gate_threshold", "high_pass", "noise_suppression")) assertEquals("lumoravoice.section.filters", s.get(key), key);
        for (String key : List.of("output_volume", "range", "spatial", "compressor")) assertEquals("lumoravoice.section.playback", s.get(key), key);
        for (String key : List.of("ambient", "echo", "muffle")) assertEquals("lumoravoice.section.ambient", s.get(key), key);
        for (String key : List.of("screamer", "screamer_level", "screamer_max")) assertEquals("lumoravoice.section.screamer", s.get(key), key);
        assertEquals("lumoravoice.section.connection", s.get("reset"), "reset stays last");
        assertNotNull(s.values().iterator().next(), "the first setting starts a section");
    }

    @Test void hudWidgetSettingsHaveSectionsAndFollowTheSpeakerList() throws Exception {
        var widget = new dev.lumora.voice.lumora.VoiceHudWidget(voice, () -> "V", () -> {});
        var group = new SettingsGroup("lumoravoice.widget.voice.");
        widget.configure(group);
        Map<String, String> s = sections(group);
        for (String key : List.of("indicator_text", "only_speaking", "block_hint")) assertEquals("lumoravoice.section.hud_status", s.get(key), key);
        List<String> list = List.of("max_speakers", "hide_inactive", "heads", "distance", "waves", "colored_names");
        assertEquals("lumoravoice.section.hud_speakers", s.get("speakers"));
        for (String key : list) assertEquals("lumoravoice.section.hud_speakers", s.get(key), key);
        assertEquals("lumoravoice.section.hud_look", s.get("opacity"));
        assertNotNull(s.values().iterator().next());

        for (String key : list) assertTrue(group.get(key).orElseThrow().isVisible(), key);
        @SuppressWarnings("unchecked") Setting<Boolean> speakers = (Setting<Boolean>) group.get("speakers").orElseThrow();
        speakers.set(false);
        assertFalse(config.showSpeakers);
        for (String key : list) assertFalse(group.get(key).orElseThrow().isVisible(), key + " hidden without the list");
        assertTrue(group.get("opacity").orElseThrow().isVisible());
    }

    @Test void everySectionHasAGermanAndEnglishHeading() throws Exception {
        var group = new SettingsGroup("lumoravoice.widget.voice.");
        new dev.lumora.voice.lumora.VoiceHudWidget(voice, () -> "V", () -> {}).configure(group);
        Set<String> keys = new TreeSet<>();
        for (SettingsGroup g : List.of(settings, group))
            for (Setting<?> s : g.all()) if (s.sectionKey() != null) keys.add(s.sectionKey());
        assertTrue(keys.size() >= 15, keys.toString());
        for (String file : List.of("de_de.json", "en_us.json")) {
            Map<String, String> texts = lang(file);
            for (String key : keys) assertTrue(texts.containsKey(key), file + " misses " + key);
        }
    }
}
