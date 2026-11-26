package dev.lumora.voice.svc;

import dev.lumora.voice.protocol.LabyVoiceConnection;
import dev.lumora.voice.protocol.VoiceSendQueue;
import dev.lumora.voice.protocol.VoiceService;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * UDP-Verbindung zu einem Simple-Voice-Chat-Server. Der Schlüssel kommt vorher über den Minecraft-Plugin-Kanal
 * {@code voicechat:secret}; danach Anmeldung (5 → 6), Verbindungsprüfung (9 → 10) und Keepalive-Echo (8).
 */
public final class SimpleVoiceConnection implements VoiceService {
    /** Daten aus dem Secret-Paket des Servers, Host und Port bereits aufgelöst. */
    public record Init(String host, int port, UUID player, byte[] secret, float distance, int keepAliveMillis, boolean groups) {}
    public record State(UUID id, String name, boolean disabled, boolean disconnected, UUID group) {}
    private final Init init;
    private final SimpleVoiceProtocol protocol;
    private final VoiceSendQueue queue = new VoiceSendQueue();
    private final Consumer<String> status;
    private final LabyVoiceConnection.AudioReceiver audio;
    /** Vom Plugin-Kanal gepflegt (voicechat:states/state/remove_state); trifft oft vor dem Schlüssel ein. */
    private final Map<UUID, State> states;
    private final Map<UUID, Float> ranges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> groupSpeakers = new ConcurrentHashMap<>();
    private volatile boolean running = true, authenticated, connected, whispering;
    private long sequence;

    public SimpleVoiceConnection(Init init, Map<UUID, State> states, Consumer<String> status, LabyVoiceConnection.AudioReceiver audio) {
        this.init = init; this.states = states; this.status = status; this.audio = audio;
        protocol = new SimpleVoiceProtocol(init.player(), init.secret());
        var thread = new Thread(this::run, "LumoraVoice Simple Voice Chat"); thread.setDaemon(true); thread.start();
    }
    @Override public String name() { return "Simple Voice Chat"; }
    @Override public boolean connected() { return running && connected; }
    @Override public boolean serverMuted() { return false; }
    public UUID player() { return init.player(); }
    public boolean groupsEnabled() { return init.groups(); }
    public float distance() { return init.distance(); }
    /** Flüstern: die nächsten Mikrofon-Frames tragen das Flüster-Kennzeichen (kleinere Hörweite beim Server). */
    public void whispering(boolean value) { whispering = value; }
    @Override public void audio(byte[] opus) {
        if (!connected()) return;
        try { queue.audio(protocol.mic(opus, sequence++, whispering), System.nanoTime()); }
        catch (Exception ignored) { /* Einzelnen Frame verwerfen. */ }
    }
    @Override public void silenceAudio() { queue.silence(); }
    @Override public boolean participant(UUID id) { var state = states.get(id); return connected() && state != null && !state.disconnected(); }
    @Override public boolean disconnected(UUID id) { var state = states.get(id); return connected() && state != null && state.disconnected(); }
    /** {@code id} is in a group you are not in (Simple Voice Chat groups). */
    @Override public boolean otherGroup(UUID id) { if (!connected() || id.equals(init.player())) return false; var state = states.get(id); if (state == null || state.group() == null) return false; var own = states.get(init.player()); return own == null || !state.group().equals(own.group()); }
    @Override public float range(UUID id) { return ranges.getOrDefault(id, init.distance()); }
    @Override public Map<UUID, String> unpositioned() {
        long now = System.currentTimeMillis(); var result = new LinkedHashMap<UUID, String>();
        groupSpeakers.forEach((id, last) -> { if (now - last < 1500) { var state = states.get(id); result.put(id, state == null ? "" : state.name()); } });
        return result;
    }

    private void run() {
        try (var socket = new DatagramSocket()) {
            status.accept("Simple Voice Chat wird verbunden …");
            socket.connect(new InetSocketAddress(init.host(), init.port())); socket.setSoTimeout(5);
            long started = System.currentTimeMillis(), lastHandshake = 0, lastKeepAlive = started;
            long timeout = Math.max(5000, init.keepAliveMillis() * 10L);
            byte[] buffer = new byte[SimpleVoiceProtocol.MAX_PACKET + 8];
            while (running) {
                long now = System.currentTimeMillis();
                if (!connected && now - lastHandshake >= 1000) {
                    send(socket, authenticated ? protocol.connectionCheck() : protocol.authenticate()); lastHandshake = now;
                }
                if (!connected && now - started > 15000)
                    throw new IOException("Simple-Voice-Chat-Server antwortet nicht. UDP-Port " + init.port() + " muss beim Server offen sein.");
                if (connected && now - lastKeepAlive > timeout) throw new IOException("Verbindung zum Simple-Voice-Chat-Server unterbrochen.");
                byte[] voice = queue.audio(System.nanoTime()); if (voice != null) send(socket, voice);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); } catch (SocketTimeoutException idle) { continue; }
                SimpleVoiceProtocol.Message message;
                try { message = protocol.server(Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength())); }
                catch (Exception foreign) { continue; } // Fremde oder beschädigte Datagramme ignorieren.
                switch (message.type()) {
                    case SimpleVoiceProtocol.AUTHENTICATE_ACK -> { if (!authenticated) { authenticated = true; lastHandshake = 0; } }
                    case SimpleVoiceProtocol.CONNECTION_CHECK_ACK -> {
                        if (authenticated && !connected) { connected = true; lastKeepAlive = now; status.accept("Mit Simple Voice Chat verbunden"); }
                    }
                    case SimpleVoiceProtocol.KEEP_ALIVE -> { lastKeepAlive = now; send(socket, protocol.keepAlive()); }
                    case SimpleVoiceProtocol.PING -> send(socket, protocol.pong(message.body()));
                    default -> {
                        if (!connected) continue;
                        SimpleVoiceProtocol.Sound sound;
                        try { sound = SimpleVoiceProtocol.sound(message); } catch (IOException malformed) { continue; }
                        // Negative Sequenzen sind serverseitig erzeugtes PCM (Plugin-Audiokanäle), kein Opus.
                        if (sound == null || sound.sequence() < 0 || sound.sender().equals(init.player())) continue;
                        if (sound.type() == SimpleVoiceProtocol.GROUP_SOUND) groupSpeakers.put(sound.sender(), now);
                        else { groupSpeakers.remove(sound.sender()); if (sound.distance() > 0) ranges.put(sound.sender(), sound.distance()); }
                        audio.receive(sound.sender(), sound.sequence(), sound.opus());
                    }
                }
            }
        } catch (Exception failure) {
            if (running) status.accept("Fehler: " + friendly(failure));
        } finally { connected = false; running = false; queue.clear(); }
    }
    private static void send(DatagramSocket socket, byte[] bytes) throws IOException { socket.send(new DatagramPacket(bytes, bytes.length)); }
    private static String friendly(Exception e) {
        String message = e.getMessage();
        if (e instanceof UnknownHostException) return "Adresse des Simple-Voice-Chat-Servers ist unbekannt.";
        if (message == null || message.length() > 220) return "Simple-Voice-Chat-Verbindung fehlgeschlagen. Bitte erneut versuchen.";
        return message.replaceAll("[\\p{Cntrl}]", " ");
    }
    @Override public void close() { running = false; queue.clear(); }
}
