package dev.lumora.voice.svc;

import org.junit.jupiter.api.Test;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SimpleVoiceProtocolTest {
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555"), OTHER = UUID.randomUUID();
    private static byte[] secret() { byte[] secret = new byte[16]; new Random(7).nextBytes(secret); return secret; }

    /** Liest ein Client-Datagramm so, wie ein SVC-Server es tut. */
    private static SimpleVoiceProtocol.Message asServer(byte[] datagram, byte[] secret, UUID expected) throws Exception {
        var in = new DataInputStream(new ByteArrayInputStream(datagram));
        assertEquals(0xFF, in.readUnsignedByte());
        assertEquals(expected, SimpleVoiceProtocol.uuid(in));
        return SimpleVoiceProtocol.open(new SecretKeySpec(secret, "AES"), SimpleVoiceProtocol.bytes(in, SimpleVoiceProtocol.MAX_PACKET));
    }
    /** Baut ein Datagramm, wie ein SVC-Server es an den Client schickt. */
    static byte[] fromServer(byte[] secret, int type, SimpleVoiceProtocol.Writer body) throws Exception {
        byte[] sealed = SimpleVoiceProtocol.seal(new SecretKeySpec(secret, "AES"), type, body);
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeByte(0xFF); SimpleVoiceProtocol.bytes(out, sealed); return bytes.toByteArray();
    }
    static SimpleVoiceProtocol.Writer playerSound(UUID sender, byte[] opus, long sequence, float distance, boolean whisper) {
        return out -> { SimpleVoiceProtocol.uuid(out, sender); SimpleVoiceProtocol.uuid(out, sender); SimpleVoiceProtocol.bytes(out, opus); out.writeLong(sequence); out.writeFloat(distance); out.writeByte(whisper ? 1 : 0); };
    }

    @Test void authenticationCarriesPlayerAndSecret() throws Exception {
        var protocol = new SimpleVoiceProtocol(PLAYER, secret());
        var message = asServer(protocol.authenticate(), secret(), PLAYER);
        assertEquals(SimpleVoiceProtocol.AUTHENTICATE, message.type());
        var in = new DataInputStream(new ByteArrayInputStream(message.body()));
        assertEquals(PLAYER, SimpleVoiceProtocol.uuid(in)); assertArrayEquals(secret(), in.readNBytes(16)); assertEquals(0, in.available());
        assertEquals(SimpleVoiceProtocol.CONNECTION_CHECK, asServer(protocol.connectionCheck(), secret(), PLAYER).type());
        assertEquals(0, asServer(protocol.keepAlive(), secret(), PLAYER).body().length);
    }
    @Test void micPacketMatchesServerLayout() throws Exception {
        byte[] opus = new byte[300]; new Random(1).nextBytes(opus);
        var message = asServer(new SimpleVoiceProtocol(PLAYER, secret()).mic(opus, 42, true), secret(), PLAYER);
        assertEquals(SimpleVoiceProtocol.MIC, message.type());
        var in = new DataInputStream(new ByteArrayInputStream(message.body()));
        assertArrayEquals(opus, SimpleVoiceProtocol.bytes(in, 1275)); assertEquals(42, in.readLong()); assertTrue(in.readBoolean()); assertEquals(0, in.available());
        assertThrows(IOException.class, () -> new SimpleVoiceProtocol(PLAYER, secret()).mic(new byte[1276], 0, false));
    }
    @Test void serverSoundsAreDecoded() throws Exception {
        var protocol = new SimpleVoiceProtocol(PLAYER, secret()); byte[] opus = {1, 2, 3};
        var player = SimpleVoiceProtocol.sound(protocol.server(fromServer(secret(), SimpleVoiceProtocol.PLAYER_SOUND, playerSound(OTHER, opus, 9, 15.5f, true))));
        assertEquals(OTHER, player.sender()); assertArrayEquals(opus, player.opus()); assertEquals(9, player.sequence()); assertEquals(15.5f, player.distance()); assertTrue(player.whispering());
        var group = SimpleVoiceProtocol.sound(protocol.server(fromServer(secret(), SimpleVoiceProtocol.GROUP_SOUND, out -> {
            SimpleVoiceProtocol.uuid(out, OTHER); SimpleVoiceProtocol.uuid(out, OTHER); SimpleVoiceProtocol.bytes(out, opus); out.writeLong(3); out.writeByte(0); })));
        assertEquals(SimpleVoiceProtocol.GROUP_SOUND, group.type()); assertEquals(-1, group.distance()); assertEquals(3, group.sequence());
        var location = SimpleVoiceProtocol.sound(protocol.server(fromServer(secret(), SimpleVoiceProtocol.LOCATION_SOUND, out -> {
            SimpleVoiceProtocol.uuid(out, OTHER); SimpleVoiceProtocol.uuid(out, OTHER); out.writeDouble(1); out.writeDouble(2); out.writeDouble(3);
            SimpleVoiceProtocol.bytes(out, opus); out.writeLong(5); out.writeFloat(48); out.writeByte(0); })));
        assertEquals(48, location.distance()); assertArrayEquals(opus, location.opus());
        assertNull(SimpleVoiceProtocol.sound(new SimpleVoiceProtocol.Message(SimpleVoiceProtocol.KEEP_ALIVE, new byte[0])));
    }
    @Test void wrongSecretAndForeignPacketsAreRejected() throws Exception {
        byte[] other = secret(); other[0]++;
        byte[] datagram = fromServer(other, SimpleVoiceProtocol.KEEP_ALIVE, out -> {});
        assertThrows(Exception.class, () -> new SimpleVoiceProtocol(PLAYER, secret()).server(datagram));
        assertThrows(IOException.class, () -> new SimpleVoiceProtocol(PLAYER, secret()).server(new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> new SimpleVoiceProtocol(PLAYER, new byte[8]));
    }
    @Test void varIntsAndVoiceHostsFollowMinecraftRules() throws Exception {
        for (int value : new int[]{0, 1, 127, 128, 300, 2048, Integer.MAX_VALUE}) {
            var bytes = new ByteArrayOutputStream(); SimpleVoiceProtocol.varInt(new DataOutputStream(bytes), value);
            assertEquals(value, SimpleVoiceProtocol.varInt(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        assertArrayEquals(new String[]{"1.2.3.4", "24454"}, SimpleVoiceProtocol.voiceAddress("", "1.2.3.4", 24454));
        assertArrayEquals(new String[]{"1.2.3.4", "25000"}, SimpleVoiceProtocol.voiceAddress("25000", "1.2.3.4", 24454));
        assertArrayEquals(new String[]{"voice.example.org", "24454"}, SimpleVoiceProtocol.voiceAddress("voice.example.org", "1.2.3.4", 24454));
        assertArrayEquals(new String[]{"voice.example.org", "26000"}, SimpleVoiceProtocol.voiceAddress("voice.example.org:26000", "1.2.3.4", 24454));
    }

    /** Ende-zu-Ende über echtes UDP gegen einen minimalen Server, der sich wie Simple Voice Chat 2.6 verhält. */
    @Test void connectsToFakeServerAndReceivesVoice() throws Exception {
        try (var server = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var received = new LinkedBlockingQueue<long[]>(); var statuses = new CopyOnWriteArrayList<String>();
            Map<UUID, SimpleVoiceConnection.State> states = new ConcurrentHashMap<>();
            states.put(OTHER, new SimpleVoiceConnection.State(OTHER, "Steve", false, false, null));
            var init = new SimpleVoiceConnection.Init("127.0.0.1", server.getLocalPort(), PLAYER, secret(), 48, 1000, true);
            var connection = new SimpleVoiceConnection(init, states, statuses::add, (id, sequence, opus) -> received.add(new long[]{id.equals(OTHER) ? 1 : 0, sequence, opus.length}));
            try {
                byte[] buffer = new byte[4096]; SocketAddress client = null; boolean checked = false;
                while (!checked) {
                    var packet = new DatagramPacket(buffer, buffer.length); server.receive(packet); client = packet.getSocketAddress();
                    var message = asServer(Arrays.copyOf(packet.getData(), packet.getLength()), secret(), PLAYER);
                    int reply = message.type() == SimpleVoiceProtocol.AUTHENTICATE ? SimpleVoiceProtocol.AUTHENTICATE_ACK : message.type() == SimpleVoiceProtocol.CONNECTION_CHECK ? SimpleVoiceProtocol.CONNECTION_CHECK_ACK : -1;
                    if (reply < 0) continue;
                    byte[] answer = fromServer(secret(), reply, out -> {}); server.send(new DatagramPacket(answer, answer.length, client));
                    checked = reply == SimpleVoiceProtocol.CONNECTION_CHECK_ACK;
                }
                long deadline = System.currentTimeMillis() + 3000; while (!connection.connected() && System.currentTimeMillis() < deadline) Thread.sleep(10);
                assertTrue(connection.connected(), "Verbindung nach Connection-Check-ACK"); assertTrue(connection.participant(OTHER));
                // Keepalive wird beantwortet.
                byte[] keepAlive = fromServer(secret(), SimpleVoiceProtocol.KEEP_ALIVE, out -> {}); server.send(new DatagramPacket(keepAlive, keepAlive.length, client));
                int type; do { var packet = new DatagramPacket(buffer, buffer.length); server.receive(packet); type = asServer(Arrays.copyOf(packet.getData(), packet.getLength()), secret(), PLAYER).type(); } while (type != SimpleVoiceProtocol.KEEP_ALIVE);
                // Sprache eines anderen Spielers kommt an, eigenes Echo und Server-PCM nicht.
                for (var sound : List.of(playerSound(PLAYER, new byte[5], 1, 48, false), playerSound(OTHER, new byte[7], -1, 48, false), playerSound(OTHER, new byte[9], 4, 12, true))) {
                    byte[] datagram = fromServer(secret(), SimpleVoiceProtocol.PLAYER_SOUND, sound); server.send(new DatagramPacket(datagram, datagram.length, client));
                }
                long[] voice = received.poll(3, TimeUnit.SECONDS);
                assertNotNull(voice); assertArrayEquals(new long[]{1, 4, 9}, voice); assertNull(received.poll(200, TimeUnit.MILLISECONDS));
                assertEquals(12f, connection.range(OTHER)); assertEquals(48f, connection.range(UUID.randomUUID()));
                // Mikrofon-Frames gehen als MIC-Paket raus.
                connection.whispering(true); connection.audio(new byte[]{9, 9});
                SimpleVoiceProtocol.Message mic; do { var packet = new DatagramPacket(buffer, buffer.length); server.receive(packet); mic = asServer(Arrays.copyOf(packet.getData(), packet.getLength()), secret(), PLAYER); } while (mic.type() != SimpleVoiceProtocol.MIC);
                var in = new DataInputStream(new ByteArrayInputStream(mic.body()));
                assertArrayEquals(new byte[]{9, 9}, SimpleVoiceProtocol.bytes(in, 1275)); assertEquals(0, in.readLong()); assertTrue(in.readBoolean());
                assertTrue(statuses.contains("Mit Simple Voice Chat verbunden"));
            } finally { connection.close(); }
        }
    }
}
