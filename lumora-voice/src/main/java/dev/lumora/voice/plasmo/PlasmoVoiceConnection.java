package dev.lumora.voice.plasmo;

import dev.lumora.voice.protocol.LabyVoiceConnection;
import dev.lumora.voice.protocol.VoiceSendQueue;
import dev.lumora.voice.protocol.VoiceService;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.*;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Voice connection to a Plasmo Voice server. Started by the {@code Connection} control packet (secret, UDP address);
 * the UDP thread pings until the server answers, then the server sends the config with the AES key. Control packets
 * back to the server (end of speech, source info requests, mute state) are queued and sent by {@link #flush} on the
 * client thread.
 */
public final class PlasmoVoiceConnection implements VoiceService {
    /** Like the real client: no ping for 30 s means the connection is gone. */
    static final long TIMEOUT_MILLIS = 30_000, CONNECT_MILLIS = 15_000;

    private final String host;
    private final int port;
    private final UUID secret, self;
    private final KeyPair keys;
    private final Consumer<String> status;
    private final LabyVoiceConnection.AudioReceiver audio;
    private final VoiceSendQueue queue = new VoiceSendQueue();
    /** The group copy of each frame: its own 20 ms clock, so it never delays the proximity frames. */
    private final VoiceSendQueue groupQueue = new VoiceSendQueue();
    private final Queue<byte[]> control = new ConcurrentLinkedQueue<>();
    private final Map<UUID, PlasmoVoiceProtocol.PlayerInfo> players = new ConcurrentHashMap<>();
    /** Source id → player id (player sources only). */
    private final Map<UUID, UUID> sources = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sourceStates = new ConcurrentHashMap<>();
    private final Set<UUID> requested = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Float> ranges = new ConcurrentHashMap<>();
    /** Source id → source type (PLAYER, DIRECT …). */
    private final Map<UUID, String> sourceTypes = new ConcurrentHashMap<>();
    /** Players heard through a group (broadcast source) recently → time; their proximity copy is dropped meanwhile. */
    private final Map<UUID, Long> groupSpeakers = new ConcurrentHashMap<>();
    private volatile boolean running = true, udpConnected, groupVoice = true;
    private volatile SecretKeySpec key;
    private volatile PlasmoVoiceProtocol.Activation activation, groupActivation;
    private long sequence, groupSequence;
    private boolean talking, talkingGroup;

    public PlasmoVoiceConnection(String host, int port, UUID secret, UUID self, KeyPair keys, Consumer<String> status, LabyVoiceConnection.AudioReceiver audio) {
        this.host = host; this.port = port; this.secret = secret; this.self = self; this.keys = keys; this.status = status; this.audio = audio;
        var thread = new Thread(this::run, "LumoraVoice Plasmo Voice"); thread.setDaemon(true); thread.start();
    }

    @Override public String name() { return "Plasmo Voice"; }

    /** Ready to speak: UDP answers and the config with the AES key arrived. */
    @Override public boolean connected() { return running && udpConnected && key != null; }

    @Override public boolean serverMuted() { return serverMuted(self); }

    @Override public boolean serverMuted(UUID id) { var info = players.get(id); return connected() && info != null && info.muted(); }

    @Override public boolean participant(UUID id) { return connected() && players.containsKey(id); }

    @Override public float range(UUID id) {
        Float range = ranges.get(id);
        if (range != null) return range;
        var current = activation;
        return current == null ? -1 : current.defaultDistance();
    }

    // --- control packets from the server (client thread) ---

    /** Config: decrypts the AES key with our RSA key and picks the proximity activation. */
    public void config(PlasmoVoiceProtocol.Config config) {
        if (!config.codec().equalsIgnoreCase("opus")) { fail("Plasmo Voice: Der Server nutzt keinen Opus-Codec (" + config.codec() + ")."); return; }
        if (config.sampleRate() != PlasmoVoiceProtocol.SAMPLE_RATE) { fail("Plasmo Voice: Abtastrate " + config.sampleRate() + " Hz wird nicht unterstützt (nur 48000)."); return; }
        if (config.encryptedKey().length == 0) { fail("Plasmo Voice: Der Server schickt keinen Schlüssel."); return; }
        try { key = PlasmoVoiceProtocol.aesKey(keys.getPrivate(), config.encryptedKey()); }
        catch (Exception wrongKey) { fail("Plasmo Voice: Schlüssel konnte nicht entschlüsselt werden."); return; }
        activation = config.proximity();
        if (activation == null) { fail("Plasmo Voice: Der Server bietet keine Sprechart an."); return; }
        groupActivation = config.groups();
        if (udpConnected) status.accept("Mit Plasmo Voice verbunden");
    }

    public void players(List<PlasmoVoiceProtocol.PlayerInfo> list) { players.clear(); list.forEach(this::player); }

    public void player(PlasmoVoiceProtocol.PlayerInfo info) { players.put(info.id(), info); }

    public void playerDisconnect(UUID id) { players.remove(id); ranges.remove(id); sources.values().removeIf(id::equals); }

    public void source(PlasmoVoiceProtocol.Source source) {
        requested.remove(source.id());
        sourceStates.put(source.id(), source.state());
        sourceTypes.put(source.id(), source.type());
        if (source.player() != null) sources.put(source.id(), source.player()); else sources.remove(source.id());
    }

    /** Our mute state for the server (speaker off = voice disabled). */
    public void localState(boolean inputMuted, boolean outputMuted) { control.offer(PlasmoVoiceProtocol.playerState(outputMuted, inputMuted)); }

    /** Sends queued control packets (client thread). */
    public void flush(Consumer<byte[]> send) { for (byte[] packet; (packet = control.poll()) != null; ) send.accept(packet); }

    // --- microphone ---

    /**
     * Whether the microphone also goes to the group (activation "groups" of pv-addon-groups, which is transitive: the
     * real client sends the same frame to the group and to proximity). The server ignores it outside a group.
     */
    public void groupVoice(boolean value) { groupVoice = value; }

    /** The server has groups (pv-addon-groups activation). */
    public boolean hasGroups() { return groupActivation != null; }

    @Override public void audio(byte[] opus) {
        var current = activation; var group = groupVoice ? groupActivation : null; var aes = key;
        if (!connected() || current == null || aes == null) return;
        try {
            byte[] packet, groupPacket = null;
            synchronized (this) {
                talking = true;
                packet = PlasmoVoiceProtocol.playerAudio(secret, ++sequence, PlasmoVoiceProtocol.encrypt(aes, opus), current.id(), current.defaultDistance());
                if (group != null) { talkingGroup = true; groupPacket = PlasmoVoiceProtocol.playerAudio(secret, ++groupSequence, PlasmoVoiceProtocol.encrypt(aes, opus), group.id(), 0); }
            }
            long now = System.nanoTime();
            queue.audio(packet, now);
            if (groupPacket != null) groupQueue.audio(groupPacket, now);
        } catch (Exception ignored) { /* drop this frame */ }
    }

    @Override public void silenceAudio() {
        queue.silence(); groupQueue.silence();
        var current = activation; var group = groupActivation;
        synchronized (this) {
            if (talking && current != null) control.offer(PlasmoVoiceProtocol.audioEnd(sequence, current.id(), current.defaultDistance()));
            if (talkingGroup && group != null) control.offer(PlasmoVoiceProtocol.audioEnd(groupSequence, group.id(), 0));
            talking = talkingGroup = false;
        }
    }

    /** Group speakers of the last 1.5 s with their names: played without position (like Simple Voice Chat groups). */
    @Override public Map<UUID, String> unpositioned() {
        long now = System.currentTimeMillis(); var result = new LinkedHashMap<UUID, String>();
        groupSpeakers.forEach((id, last) -> { if (now - last < 1500) { var info = players.get(id); result.put(id, info == null ? "" : info.name()); } });
        return result;
    }

    // --- UDP ---

    private void run() {
        try (var socket = new DatagramSocket()) {
            status.accept("Plasmo Voice wird verbunden …");
            socket.connect(new InetSocketAddress(host, port)); socket.setSoTimeout(5);
            long started = System.currentTimeMillis(), lastPing = 0, keepAlive = started;
            byte[] buffer = new byte[PlasmoVoiceProtocol.MAX_DATA + 256];
            while (running) {
                long now = System.currentTimeMillis();
                if (!udpConnected && now - lastPing >= 1000) { send(socket, PlasmoVoiceProtocol.ping(secret, host, port)); lastPing = now; }
                if (!udpConnected && now - started > CONNECT_MILLIS)
                    throw new IOException("Plasmo-Voice-Server antwortet nicht. UDP-Port " + port + " muss beim Server offen sein.");
                if (udpConnected && now - keepAlive > TIMEOUT_MILLIS) throw new IOException("Verbindung zum Plasmo-Voice-Server unterbrochen.");
                byte[] voice = queue.audio(System.nanoTime()); if (voice != null) send(socket, voice);
                byte[] groupFrame = groupQueue.audio(System.nanoTime()); if (groupFrame != null) send(socket, groupFrame);
                var packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); } catch (SocketTimeoutException idle) { continue; }
                var datagram = PlasmoVoiceProtocol.readDatagram(Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength()));
                if (datagram == null) continue; // not Plasmo Voice
                switch (datagram.type()) {
                    case PlasmoVoiceProtocol.PING -> {
                        keepAlive = now;
                        if (!udpConnected) { udpConnected = true; status.accept(key != null ? "Mit Plasmo Voice verbunden" : "Plasmo Voice: warte auf Konfiguration …"); }
                        send(socket, PlasmoVoiceProtocol.ping(secret, null, 0));
                    }
                    case PlasmoVoiceProtocol.SOURCE_AUDIO -> receive(datagram);
                    default -> { }
                }
            }
        } catch (Exception failure) {
            if (running) status.accept("Fehler: " + friendly(failure));
        } finally { udpConnected = false; running = false; queue.clear(); groupQueue.clear(); }
    }

    private void receive(PlasmoVoiceProtocol.Datagram datagram) {
        var aes = key;
        if (aes == null) return;
        PlasmoVoiceProtocol.SourceAudio sound;
        try { sound = PlasmoVoiceProtocol.readSourceAudio(datagram.body()); } catch (IOException malformed) { return; }
        UUID player = sources.get(sound.source());
        Integer state = sourceStates.get(sound.source());
        if (player == null || state == null || state != sound.state()) {
            // unknown source or changed state: ask the server once what it is
            if (requested.add(sound.source())) control.offer(PlasmoVoiceProtocol.sourceInfoRequest(sound.source()));
            if (player == null) return;
        }
        if (player.equals(self)) return;
        long now = System.currentTimeMillis();
        boolean group = "DIRECT".equals(sourceTypes.get(sound.source()));
        // a group member nearby arrives twice (group + proximity): keep the group stream, it has no distance limit
        if (!group) { Long heard = groupSpeakers.get(player); if (heard != null && now - heard < 1000) return; }
        byte[] opus;
        try { opus = PlasmoVoiceProtocol.decrypt(aes, sound.data()); } catch (Exception broken) { return; }
        if (group) groupSpeakers.put(player, now);
        else if (sound.distance() > 0) ranges.put(player, (float) sound.distance());
        audio.receive(player, sound.sequence(), opus);
    }

    private void fail(String message) { status.accept(message); running = false; }

    private static void send(DatagramSocket socket, byte[] bytes) throws IOException { socket.send(new DatagramPacket(bytes, bytes.length)); }

    private static String friendly(Exception e) {
        String message = e.getMessage();
        if (e instanceof UnknownHostException) return "Adresse des Plasmo-Voice-Servers ist unbekannt.";
        if (message == null || message.length() > 220) return "Plasmo-Voice-Verbindung fehlgeschlagen. Bitte erneut versuchen.";
        return message.replaceAll("[\\p{Cntrl}]", " ");
    }

    @Override public void close() { running = false; queue.clear(); groupQueue.clear(); }
}
