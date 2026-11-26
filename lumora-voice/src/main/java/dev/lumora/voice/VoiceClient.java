package dev.lumora.voice;

import dev.lumora.voice.audio.VoiceAudio;
import dev.lumora.voice.platform.VoicePlatform;
import dev.lumora.voice.protocol.*;
import dev.lumora.voice.render.NameTagState;
import dev.lumora.voice.plasmo.*;
import dev.lumora.voice.svc.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The voice chat client: chooses the voice service, connects, opens and closes the microphone, mixes speakers by
 * position and keeps the state the UI shows. Version independent; everything from the game comes through
 * {@link VoicePlatform}. {@link #tick()} runs once per client tick on the client thread.
 */
public final class VoiceClient implements AutoCloseable {
    /** A speaker for HUD and settings; {@code distance} is -1 for speakers without position (channels, groups). */
    public record Nearby(UUID id, String name, float distance, boolean talking, OptionalInt teamColor) {}

    static final String STATUS_OFF = "VoiceChat ist ausgeschaltet";
    static final int CONNECT_DELAY_TICKS = 30, SVC_TIMEOUT_TICKS = 200, MAX_PLAYERS = 128;

    private final VoicePlatform platform;
    private final VoiceConfig config;
    private final VoiceAudio audio;
    private final String version;
    private final SimpleVoiceChannel svcChannel;
    /** Player states from the Simple Voice Chat server; may arrive before the secret. */
    private final Map<UUID, SimpleVoiceConnection.State> svcStates = new ConcurrentHashMap<>();
    /** Groups the Simple Voice Chat server announced. */
    private final SvcGroups svcGroups = new SvcGroups();
    private final PlasmoVoiceChannel plasmoChannel;
    /** The Plasmo Voice server asked for our player info on this server (it does that once, right after joining). */
    private boolean plasmoRequested;
    private java.security.KeyPair plasmoKeys;
    private final VoicePlatform.Channel plasmoGroupsChannel;
    private final PlasmoGroups plasmoGroups;
    private volatile VoiceService connection;
    private volatile String status = STATUS_OFF, audioStatus = "";
    private volatile long generation;
    private String server = "";
    private int connectTicks, svcRequested;
    private boolean latched, lastEnabled, lastConnected;
    private volatile List<Nearby> nearby = List.of();
    private volatile String pausedFor;
    private BooleanSupplier pushToTalk = () -> false, whisper = () -> false;
    private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public VoiceClient(VoicePlatform platform, VoiceConfig config, String version) {
        this(platform, config, version, null);
    }

    /** {@code audio} for tests; {@code null} opens the real audio engine. */
    VoiceClient(VoicePlatform platform, VoiceConfig config, String version, VoiceAudio audio) {
        this.platform = platform;
        this.config = config;
        this.version = version;
        this.audio = audio != null ? audio : new VoiceAudio(config.audioSettings(), bytes -> { var net = connection; if (net != null) net.audio(bytes); }, message -> audioStatus = message);
        svcChannel = SimpleVoiceChannel.register(platform, new SimpleVoiceChannel.Handler() {
            @Override public void secret(SimpleVoiceChannel.Secret packet) { simpleVoiceSecret(packet); }
            @Override public void states(List<SimpleVoiceConnection.State> states) { svcStates.clear(); states.forEach(s -> svcStates.put(s.id(), s)); }
            @Override public void state(SimpleVoiceConnection.State state) { svcStates.put(state.id(), state); }
            @Override public void removeState(UUID id) { svcStates.remove(id); }
            @Override public void group(SvcGroups.Group group) { svcGroups.add(group); }
            @Override public void removeGroup(UUID id) { svcGroups.remove(id); }
            @Override public void joinedGroup(UUID group, boolean wrongPassword) { svcGroups.joined(group, wrongPassword); }
        });
        if (svcChannel == null) VoiceLog.info("Simple Voice Chat mod is installed; LumoraVoice leaves its channels to it.");
        plasmoChannel = PlasmoVoiceChannel.register(platform, new PlasmoVoiceChannel.Handler() {
            // arrives right when joining (before the server shows up in tick) or again when the server restarts the handshake
            @Override public void playerInfoRequest() { plasmoRequested = true; if (connectTicks >= CONNECT_DELAY_TICKS && plasmoChosen()) plasmoHello(); }
            @Override public void connection(PlasmoVoiceProtocol.Connection packet) { plasmoConnection(packet); }
            @Override public void config(PlasmoVoiceProtocol.Config packet) { if (connection instanceof PlasmoVoiceConnection p) p.config(packet); }
            @Override public void players(List<PlasmoVoiceProtocol.PlayerInfo> players) { if (connection instanceof PlasmoVoiceConnection p) p.players(players); }
            @Override public void player(PlasmoVoiceProtocol.PlayerInfo player) { if (connection instanceof PlasmoVoiceConnection p) p.player(player); }
            @Override public void playerDisconnect(UUID id) { if (connection instanceof PlasmoVoiceConnection p) p.playerDisconnect(id); }
            @Override public void source(PlasmoVoiceProtocol.Source source) { if (connection instanceof PlasmoVoiceConnection p) p.source(source); }
        });
        if (plasmoChannel == null) VoiceLog.info("Plasmo Voice mod is installed; LumoraVoice leaves its channels to it.");
        // groups of the Plasmo Voice server addon pv-addon-groups; its own client mod keeps the channel if installed
        if (plasmoChannel != null && !platform.modLoaded("pv-addon-groups")) {
            PlasmoGroups[] holder = new PlasmoGroups[1];
            var channel = platform.channel(PlasmoGroups.CHANNEL, bytes -> { if (holder[0] != null) holder[0].receive(bytes); });
            plasmoGroupsChannel = channel;
            plasmoGroups = holder[0] = new PlasmoGroups(bytes -> { if (channel.serverSupports()) channel.send(bytes); });
        } else { plasmoGroupsChannel = null; plasmoGroups = null; }
    }

    /** Push-to-talk and whisper key state (held down). */
    public void keys(BooleanSupplier pushToTalk, BooleanSupplier whisper) {
        this.pushToTalk = pushToTalk;
        this.whisper = whisper;
    }

    /** Called after settings changed through a key or the menu (for screens that show the values). */
    public void onChange(Runnable listener) { listeners.add(listener); }

    public VoiceConfig config() { return config; }
    public VoiceAudio audio() { return audio; }
    public VoicePlatform platform() { return platform; }
    public String version() { return version; }

    // --- key actions (only while no screen is open, like the vanilla keys) ---

    public void toggleMute() { config.muted = !config.muted; saveAndApply(); }
    public void toggleOutputMute() { config.outputMuted = !config.outputMuted; saveAndApply(); }
    public void toggleHud() { config.showHud = !config.showHud; save(); }

    /** Push-to-talk pressed: switches the latch when "Push-to-talk umschalten" is on. */
    public void pushToTalkPressed() { if (config.togglePtt) latched = !latched; }

    /** Key "block the player you look at": blocks or unblocks the player under the crosshair. */
    public void toggleBlockTarget() {
        var target = platform.targetPlayer();
        if (target.isEmpty()) { platform.actionBar("Schau einen Spieler an, um ihn zu blockieren."); return; }
        toggleBlock(target.get().id(), target.get().name());
    }

    /** Blocks or unblocks a player (key or player menu); returns whether the player is blocked now. */
    public boolean toggleBlock(UUID id, String name) {
        boolean nowBlocked = !config.isBlocked(id, name);
        if (nowBlocked) config.block(id, name);
        else { config.unblock(id); config.blocked.entrySet().removeIf(e -> e.getValue().name.equalsIgnoreCase(name)); }
        save(); notifySound();
        platform.actionBar(nowBlocked ? name + " ist blockiert – ihr hört euch nicht mehr." : name + " ist nicht mehr blockiert.");
        return nowBlocked;
    }

    // --- tick ---

    public void tick() {
        var current = platform.server();
        String address = current.map(VoicePlatform.Server::address).orElse("");
        if (!address.equals(server)) { disconnect(); server = address; connectTicks = 0; svcStates.clear(); svcGroups.clear(); if (address.isEmpty()) plasmoRequested = false; audio.testing(false); }
        var activation = activation();
        audio.configure(config.audioSettings(activation));
        if (config.enabled != lastEnabled) { lastEnabled = config.enabled; disconnect(); connectTicks = 0; }
        boolean multiplayer = current.isPresent();
        if (!config.enabled) { status = STATUS_OFF; if (connection != null) disconnect(); }
        else if (!multiplayer) { status = "Für VoiceChat einen Multiplayer-Server mit gültigem Java-Konto betreten"; if (connection != null) disconnect(); }
        else if (connection == null) {
            // wait 1.5 s: by then the server has announced its plugin channels (for the automatic service choice)
            if (++connectTicks == CONNECT_DELAY_TICKS) start(current.get());
            else if (svcRequested > 0 && connectTicks - svcRequested == SVC_TIMEOUT_TICKS)
                status = "Der Server hat nicht auf Simple Voice Chat geantwortet. Benötigt wird Simple Voice Chat 2.6.";
        }
        var net = connection;
        if (net instanceof PlasmoVoiceConnection plasmo && plasmoChannel != null) {
            plasmo.flush(plasmoChannel::send);
            // pv-addon-groups 2.x: start its channel once voice works; without the channel (1.x) the microphone always goes to
            // the group activation too (the server ignores it outside a group)
            boolean groupsChannel = plasmoGroups != null && plasmoGroupsChannel.serverSupports();
            if (groupsChannel && plasmo.connected() && plasmo.hasGroups()) plasmoGroups.start();
            plasmo.groupVoice(!groupsChannel || !plasmoGroups.ready() || plasmoGroups.current() != null);
        }
        boolean screen = platform.screenOpen(), focused = platform.windowFocused();
        boolean whispering = net instanceof SimpleVoiceConnection && whisper.getAsBoolean() && (config.speakInMenus || !screen);
        if (net instanceof SimpleVoiceConnection svc) svc.whispering(whispering);
        var listener = multiplayer ? platform.listener() : Optional.<VoicePlatform.Listener>empty();
        var peers = listener.isPresent() ? platform.players(MAX_PLAYERS) : List.<VoicePlatform.Peer>of();
        pausedFor = blockedListener(net, listener.orElse(null), peers);
        boolean openMic = config.enabled && net != null && net.connected() && !net.serverMuted() && !config.muted && !config.deafened
                && (!config.pauseUnfocused || focused) && (config.speakInMenus || !screen)
                && (activation != VoiceConfig.Activation.PUSH_TO_TALK || (config.togglePtt ? latched : pushToTalk.getAsBoolean()) || whispering)
                && pausedFor == null;
        audio.gate(openMic, config.deafened || config.outputMuted);
        if (!openMic && net != null) net.silenceAudio();
        if (config.pauseUnfocused && !focused && audio.testing()) audio.testing(false);
        updatePositions(listener.orElse(null), peers);
        boolean connected = connected();
        if (connected != lastConnected) { if (connected) { notifySound(); controlQuietProperties(); } lastConnected = connected; }
    }

    private void updatePositions(VoicePlatform.Listener self, List<VoicePlatform.Peer> peers) {
        if (self == null) { nearby = List.of(); audio.positions(Map.of()); return; }
        var positions = new LinkedHashMap<UUID, VoiceAudio.Spatial>(); var entries = new ArrayList<Nearby>(); var visibleIds = new LinkedHashSet<UUID>();
        var voice = connection;
        var channelState = channels();
        boolean channelMode = channelState != null && !channelState.current().proximity();
        boolean focusActive = config.players.values().stream().anyMatch(p -> p.focused);
        // right-hand vector: look × up
        double rx = -self.lookZ(), rz = self.lookX(), rl = Math.sqrt(rx * rx + rz * rz);
        if (rl > 1e-6) { rx /= rl; rz /= rl; }
        int seaLevel = -1;
        for (var other : peers) {
            // blocked players: never played, never reported to Laby as visible, never shown
            if (blocked(other.id(), other.name())) { if (config.rename(other.id(), other.name())) save(); continue; }
            visibleIds.add(other.id());
            double dx = other.x() - self.x(), dy = other.y() - self.y(), dz = other.z() - self.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            UUID id = other.id();
            // Simple Voice Chat sets the range per speaker (normal, whisper); Laby uses the own setting
            float range = voice == null || voice.range(id) <= 0 ? config.surroundRange : voice.range(id);
            if (!channelMode && distance > range) continue;
            if (channelMode && !channelState.remoteAllowed(id)) continue;
            VoiceConfig.Player preference = config.existing(id);
            boolean muted = preference != null && preference.muted;
            float gain = (channelMode ? 1 : (float) Math.pow(Math.max(0, 1 - distance / range), 1.3)) * (preference == null ? 1 : preference.volume);
            if (focusActive) gain *= preference != null && preference.focused ? config.focusedVolume : config.unfocusedVolume;
            // 3D audio: side share -> stereo position; forward = (rz, -rx), behind is quieter on "Stark"
            var spatial = config.spatial();
            boolean positioned = !channelMode && distance > .01;
            float pan = positioned ? spatial.pan((float) ((dx * rx + dz * rz) / distance)) : 0;
            if (positioned) gain *= spatial.rearGain((float) ((dx * rz - dz * rx) / distance));
            boolean muffle = false, echo = false;
            if (config.ambientSound && !channelMode) {
                muffle = config.ambientMuffle && (other.inWater() || self.inWater() || platform.occluded(other));
                if (config.ambientEcho && !other.inWater() && !other.skyVisible()) {
                    if (seaLevel == -1) seaLevel = platform.seaLevel();
                    echo = other.y() < seaLevel - 6;
                }
            }
            if (!muted) positions.put(id, new VoiceAudio.Spatial(gain, pan, muffle, echo));
            entries.add(new Nearby(id, other.name(), (float) distance, !muted && audio.speaking(id), other.teamColor()));
        }
        if (channelMode) for (var user : channelState.users()) {
            if (user.id().equals(self.id()) || !channelState.remoteAllowed(user.id()) || blocked(user.id(), user.name()) || contains(entries, user.id())) continue;
            var preference = config.existing(user.id()); boolean muted = preference != null && preference.muted;
            float gain = preference == null ? 1 : preference.volume;
            if (focusActive) gain *= preference != null && preference.focused ? config.focusedVolume : config.unfocusedVolume;
            if (!muted) positions.put(user.id(), new VoiceAudio.Spatial(gain, 0));
            entries.add(new Nearby(user.id(), user.name(), -1, !muted && audio.speaking(user.id()), OptionalInt.empty()));
        }
        if (voice != null) voice.unpositioned().forEach((id, name) -> {
            if (contains(entries, id) || blocked(id, name)) return;
            var preference = config.existing(id); boolean muted = preference != null && preference.muted;
            if (!muted) positions.put(id, new VoiceAudio.Spatial(preference == null ? 1 : preference.volume, 0));
            entries.add(new Nearby(id, name, -1, !muted && audio.speaking(id), OptionalInt.empty()));
        });
        nearby = List.copyOf(entries); audio.positions(positions); if (voice != null) voice.visible(visibleIds);
    }

    private static boolean contains(List<Nearby> entries, UUID id) {
        for (var entry : entries) if (entry.id().equals(id)) return true;
        return false;
    }

    // --- blocking ---

    public boolean blocked(UUID id, String name) { return config.isBlocked(id, name); }

    /** Block check with the tab-list name (for name tags and tab icons). */
    public boolean blocked(UUID id) {
        if (config.blocked.isEmpty() || id == null) return false;
        if (config.isBlocked(id)) return true;
        for (var entry : platform.tabList()) if (entry.id().equals(id)) return config.isBlocked(id, entry.name());
        return false;
    }

    /** Microphone is paused because this blocked player could listen; otherwise {@code null}. */
    public String pausedFor() { return pausedFor; }

    /**
     * Servers send your voice to everyone in range or in the same channel/group; single listeners cannot be left out.
     * So nothing is sent while a blocked player could listen.
     */
    private String blockedListener(VoiceService net, VoicePlatform.Listener self, List<VoicePlatform.Peer> peers) {
        if (!config.blockPauseMic || config.blocked.isEmpty() || net == null || self == null) return null;
        double range = config.blockPauseRange;
        for (var other : peers) if (other.distance(self) <= range && config.isBlocked(other.id(), other.name())) return other.name();
        var channels = net.channels();
        if (channels != null && !channels.current().proximity())
            for (var user : channels.users()) if (channels.remoteAllowed(user.id()) && config.isBlocked(user.id(), user.name())) return user.name();
        if (net instanceof SimpleVoiceConnection svc) {
            var own = svcStates.get(svc.player());
            if (own != null && own.group() != null) for (var state : svcStates.values())
                if (!state.id().equals(svc.player()) && own.group().equals(state.group()) && !state.disconnected() && config.isBlocked(state.id(), state.name())) return state.name();
        }
        return null;
    }

    // --- services ---

    /** Chosen service; AUTO decides by the plugin channels the server announced. */
    public VoiceConfig.Service chosenService() {
        if (config.service != VoiceConfig.Service.AUTO) return config.service;
        if (svcChannel != null && svcChannel.available()) return VoiceConfig.Service.SIMPLE_VOICE_CHAT;
        return plasmoChannel != null && plasmoChannel.available() ? VoiceConfig.Service.PLASMO_VOICE : VoiceConfig.Service.LABY;
    }

    private boolean plasmoChosen() { return config.enabled && chosenService() == VoiceConfig.Service.PLASMO_VOICE; }

    public boolean plasmoVoiceModInstalled() { return plasmoChannel == null; }

    /** Answers the server's player info request: versions, mute state and our RSA key (for the AES key in the config). */
    private void plasmoHello() {
        try {
            if (plasmoKeys == null) plasmoKeys = PlasmoVoiceProtocol.rsaKeyPair();
            plasmoChannel.send(PlasmoVoiceProtocol.playerInfo(platform.minecraftVersion(), PlasmoVoiceProtocol.VERSION, plasmoKeys.getPublic().getEncoded(),
                    config.outputMuted || config.deafened, config.muted || config.deafened));
            status = "Plasmo Voice: Anmeldung beim Server …";
        } catch (Exception failure) { status = "Plasmo Voice: Schlüssel konnte nicht erzeugt werden."; }
    }

    /** The server sent the UDP address and secret (client thread). */
    private void plasmoConnection(PlasmoVoiceProtocol.Connection packet) {
        var current = platform.server(); var profile = platform.profile();
        if (!plasmoChosen() || current.isEmpty() || profile.isEmpty() || plasmoKeys == null) return;
        // "0.0.0.0": the voice server runs on the Minecraft server's address (like the real client)
        String host = packet.ip();
        if (host.isBlank() || host.equals("0.0.0.0")) host = current.get().remoteIp().isEmpty() ? hostAndPort(current.get().address())[0] : current.get().remoteIp();
        var previous = connection; long session = ++generation; if (previous != null) previous.close();
        connection = new PlasmoVoiceConnection(host, packet.port(), packet.secret(), profile.get().id(), plasmoKeys,
                message -> { if (session == generation) status = message; }, audio::receive);
        VoiceLog.info("Plasmo Voice connection packet received, voice server " + host + ":" + packet.port());
    }

    public String serviceName() { var net = connection; return net != null ? net.name() : chosenService().label(); }

    public boolean simpleVoiceModInstalled() { return svcChannel == null; }

    private void start(VoicePlatform.Server current) {
        if (chosenService() == VoiceConfig.Service.PLASMO_VOICE) { startPlasmo(); return; }
        if (chosenService() != VoiceConfig.Service.SIMPLE_VOICE_CHAT) { connect(current); return; }
        if (svcChannel == null) { status = "Die Simple-Voice-Chat-Mod ist selbst installiert. Bitte deren Menü nutzen oder sie entfernen."; return; }
        if (!svcChannel.available()) { status = "Dieser Server bietet kein Simple Voice Chat an. Unter Übersicht → Sprachdienst wechseln."; return; }
        svcRequested = connectTicks; status = "Simple Voice Chat wird beim Server angefragt …";
        svcChannel.requestSecret();
    }

    private void startPlasmo() {
        if (plasmoChannel == null) { status = "Die Plasmo-Voice-Mod ist selbst installiert. Bitte deren Menü nutzen oder sie entfernen."; return; }
        if (!plasmoChannel.available()) { status = "Dieser Server bietet kein Plasmo Voice an. Unter Allgemein → Sprachdienst wechseln."; return; }
        if (plasmoRequested) plasmoHello();
        else status = "Plasmo Voice: warte auf den Server …";
    }

    /** The server sent the UDP key (answer to voicechat:request_secret; client thread). */
    private void simpleVoiceSecret(SimpleVoiceChannel.Secret packet) {
        var current = platform.server();
        if (!config.enabled || current.isEmpty() || chosenService() != VoiceConfig.Service.SIMPLE_VOICE_CHAT) return;
        // like Simple Voice Chat: the IP the game is actually connected to (SRV records already resolved)
        String host = current.get().remoteIp().isEmpty() ? "127.0.0.1" : current.get().remoteIp();
        String[] address = SimpleVoiceProtocol.voiceAddress(packet.voiceHost(), host, packet.port());
        var previous = connection; long session = ++generation; if (previous != null) previous.close();
        var init = new SimpleVoiceConnection.Init(address[0], Integer.parseInt(address[1]), packet.player(), packet.secret(),
                (float) packet.distance(), packet.keepAlive(), packet.groups());
        connection = new SimpleVoiceConnection(init, svcStates, message -> { if (session == generation) status = message; }, audio::receive);
        svcRequested = 0;
        svcChannel.updateState(config.deafened || config.outputMuted);
        VoiceLog.info("Simple Voice Chat secret received, voice server " + address[0] + ":" + address[1]);
    }

    private void connect(VoicePlatform.Server current) {
        var profile = platform.profile();
        if (profile.isEmpty()) { status = "Keine gültige Minecraft-Sitzung. Im Launcher mit Microsoft anmelden."; return; }
        long session = ++generation;
        String[] address = hostAndPort(current.address());
        connection = new LabyVoiceConnection(profile.get().id(), profile.get().name(), address[0], Integer.parseInt(address[1]), config.backupServer, config.testServer,
                platform::joinServer, message -> { if (session == generation) status = message; }, audio::receive);
    }

    /** {@code host}, {@code host:port}, {@code [v6]} or {@code [v6]:port} → host and port (default 25565). */
    static String[] hostAndPort(String address) {
        String value = address.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                String host = value.substring(1, end), rest = value.substring(end + 1);
                return new String[]{host, port(rest.startsWith(":") ? rest.substring(1) : "")};
            }
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) return new String[]{value.substring(0, colon), port(value.substring(colon + 1))};
        return new String[]{value, "25565"};
    }

    private static String port(String text) {
        try { int port = Integer.parseInt(text.trim()); return port > 0 && port < 65536 ? Integer.toString(port) : "25565"; }
        catch (NumberFormatException none) { return "25565"; }
    }

    // --- state for UI, HUD and name tags ---

    public boolean connected() { var net = connection; return net != null && net.connected(); }
    public boolean serverMuted() { var net = connection; return net != null && net.serverMuted(); }
    public String status() { return status; }
    public String audioStatus() { return audioStatus; }
    public List<Nearby> nearby() { return nearby; }
    public VoiceChannels channels() { var net = connection; return net == null ? null : net.channels(); }

    public VoiceConfig.Activation activation() {
        var state = channels();
        return config.useChannelActivation && state != null && !state.current().proximity() ? config.channelActivation : config.activation;
    }

    public String channelOf(UUID id) {
        var state = channels();
        if (state != null) return blocked(id) ? "" : state.channelName(id);
        var plasmo = plasmoGroups();
        if (plasmo != null) {
            var current = plasmo.current();
            return current == null || blocked(id) || plasmo.members().stream().noneMatch(m -> m.id().equals(id)) ? "" : current.name();
        }
        var groups = groups(); var player = svcStates.get(id);
        var group = groups == null || player == null || blocked(id) ? null : groups.get(player.group());
        return group == null ? "" : group.name();
    }

    // --- Plasmo Voice groups (server addon pv-addon-groups) ---

    /** Groups of the Plasmo Voice server, or {@code null} (no Plasmo Voice, no groups addon, or an addon without channel). */
    public PlasmoGroups plasmoGroups() {
        return connection instanceof PlasmoVoiceConnection p && p.connected() && plasmoGroups != null && plasmoGroups.ready() ? plasmoGroups : null;
    }

    /** The Plasmo Voice server has groups, but they can only be managed by command (pv-addon-groups 1.x). */
    public boolean plasmoGroupsByCommand() { return connection instanceof PlasmoVoiceConnection p && p.connected() && p.hasGroups() && plasmoGroups() == null; }

    // --- Simple Voice Chat groups ---

    /** Groups of the Simple Voice Chat server, or {@code null} without a Simple Voice Chat connection that allows groups. */
    public SvcGroups groups() {
        return connection instanceof SimpleVoiceConnection svc && svc.connected() && svc.groupsEnabled() ? svcGroups : null;
    }

    /** The group you are in (Simple Voice Chat), or {@code null}. */
    public UUID ownGroup() {
        var self = platform.profile().map(p -> p.id()).orElse(null);
        var state = self == null ? null : svcStates.get(self);
        return state == null ? null : state.group();
    }

    /** Members of a Simple Voice Chat group by name (blocked players left out). */
    public List<VoicePlatform.Named> groupMemberList(UUID group) {
        return svcStates.values().stream().filter(s -> group.equals(s.group()) && !s.disconnected() && !blocked(s.id()))
                .map(s -> new VoicePlatform.Named(s.id(), s.name())).sorted(Comparator.comparing(VoicePlatform.Named::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Whether {@code id} is talking right now: you yourself (your microphone) or another speaker. */
    public boolean talking(UUID id) {
        boolean self = platform.profile().map(p -> p.id().equals(id)).orElse(false);
        return self ? audio.talking() : audio.speaking(id);
    }

    public int groupMembers(UUID group) { return (int) svcStates.values().stream().filter(s -> group.equals(s.group()) && !s.disconnected()).count(); }

    public void joinGroup(UUID group, String password) { svcAction(() -> svcChannel.joinGroup(group, password)); }

    public void createGroup(String name, String password, SvcGroups.Type type) { svcAction(() -> svcChannel.createGroup(name, password, type)); }

    public void leaveGroup() { svcAction(svcChannel::leaveGroup); }

    private void svcAction(BooleanSupplier send) {
        if (groups() == null || svcChannel == null) { svcGroups.alert("Gruppen gibt es nur mit Simple Voice Chat auf einem Server, der sie erlaubt."); return; }
        svcGroups.alert(send.getAsBoolean() ? "" : "Der Server nimmt keine Gruppenbefehle an.");
    }

    /** Tests: use this service as the current connection. */
    void useConnection(VoiceService net) { connection = net; }

    public NameTagState nameTagState(UUID id) {
        if (!config.enabled || blocked(id)) return NameTagState.ABSENT; // blocked players look like players without voice chat
        var server = platform.server().orElse(null);
        if (!Objects.equals(server, seenOn)) { seenInVoice.clear(); seenOn = server; }
        var net = connection; boolean speaking = audio.speaking(id);
        boolean online = net != null && net.connected();
        boolean self = platform.profile().map(p -> p.id().equals(id)).orElse(false);
        if (self) {
            if (!online) return server != null ? NameTagState.DISCONNECTED : NameTagState.ABSENT;
        } else if (!online) {
            return NameTagState.ABSENT;
        }
        // muted by the voice service (Laby Voice): microphone with a red slash, before your own mute and presence
        if (net.serverMuted(id) || self && net.serverMuted()) { seenInVoice.add(id); return NameTagState.SERVER_MUTED; }
        // in a group/channel you are not in: you do not hear them, even when they stand next to you
        if (!self && net.otherGroup(id)) { seenInVoice.add(id); return NameTagState.GROUP; }
        if (!self && !net.participant(id) && !speaking) {
            return net.disconnected(id) || seenInVoice.contains(id) ? NameTagState.DISCONNECTED : NameTagState.ABSENT;
        }
        seenInVoice.add(id);
        var preference = config.existing(id);
        if (preference != null && preference.muted) return NameTagState.MUTED;
        return speaking ? NameTagState.SPEAKING : NameTagState.IDLE;
    }

    /** Players seen in voice on {@link #seenOn}: they get the plug icon when they drop out of voice. */
    private final Set<UUID> seenInVoice = ConcurrentHashMap.newKeySet();
    private volatile VoicePlatform.Server seenOn;

    /** Sends a Laby control packet (channels); explains in the status line why not otherwise. */
    public void control(int id, VoiceProtocol.Writer writer) {
        var net = connection; if (net == null || !net.connected()) { status = "Zuerst VoiceChat auf einem Multiplayer-Server verbinden"; return; }
        if (net.channels() == null) { status = "Sprachkanäle gibt es nur bei Laby Voice."; return; }
        try { net.control(VoiceProtocol.packet(id, writer)); } catch (Exception error) { status = "Kanalaktion fehlgeschlagen: " + error.getMessage(); }
    }

    // --- changes ---

    public void save() {
        config.save();
        listeners.forEach(Runnable::run);
    }

    /** Saves and applies mute/deafen/audio changes immediately. */
    public void saveAndApply() { save(); changed(); }

    /** Mute, deafen or sending changed: applies it, tells the voice service and plays the notification sound. */
    public void changed() { apply(); latched = false; controlQuietProperties(); notifySound(); }

    /** Applies audio settings quietly (sliders in the settings card fire on every step). */
    public void apply() { audio.configure(config.audioSettings(activation())); audio.gate(false, config.deafened || config.outputMuted); }

    private void controlQuietProperties() {
        // Simple Voice Chat only knows "speaker off" (disabled); the microphone stays silent locally anyway
        if (connection instanceof SimpleVoiceConnection) { if (svcChannel != null) svcChannel.updateState(config.deafened || config.outputMuted); return; }
        if (connection instanceof PlasmoVoiceConnection plasmo) { plasmo.localState(config.muted || config.deafened, config.outputMuted || config.deafened); return; }
        if (connected()) control(VoicePackets.USER_PROPERTIES, out -> VoiceChannels.properties(out, Map.of("inputMuted", Boolean.toString(config.muted || config.deafened), "outputMuted", Boolean.toString(config.outputMuted || config.deafened))));
    }

    public void devicesChanged() { audioStatus = ""; audio.configure(config.audioSettings()); audio.retryDevices(); }
    public void reconnect() { disconnect(); connectTicks = CONNECT_DELAY_TICKS - 1; devicesChanged(); status = "Verbindung wird neu aufgebaut …"; }

    public void disconnect() {
        generation++; svcRequested = 0; if (plasmoGroups != null) plasmoGroups.reset(); var net = connection; connection = null; if (net != null) net.close(); latched = false;
        audio.gate(false, config.deafened);
    }

    public void notifySound() { if (config.notificationSounds) platform.uiSound(); }

    /** Status line for the settings screen and HUD (notice, pause reason, audio problem or connection state). */
    public String statusLine() {
        var paused = pausedFor;
        if (paused != null) return "Mikrofon pausiert: " + paused + " ist blockiert und könnte dich hören.";
        return !audioStatus.isEmpty() ? audioStatus : status;
    }

    @Override public void close() { disconnect(); audio.close(); }

    /** Applies {@code change} to the config, saves and notifies listeners. */
    public void update(Consumer<VoiceConfig> change) { change.accept(config); save(); }
}
