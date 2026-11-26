package dev.lumora.voice.plasmo;

import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** The UDP side against a small fake Plasmo Voice server on localhost. */
class PlasmoVoiceConnectionTest {
    private static final UUID SECRET = new UUID(7, 7), SELF = new UUID(1, 1), FRIEND = new UUID(2, 2), SOURCE = new UUID(3, 3);

    private static void waitFor(java.util.function.BooleanSupplier condition, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) { if (System.currentTimeMillis() > end) fail("timed out: " + what); Thread.sleep(10); }
    }

    private static DatagramPacket receive(DatagramSocket server) throws Exception {
        var packet = new DatagramPacket(new byte[4096], 4096); server.receive(packet); return packet;
    }

    @Test void handshakeConfigAndVoiceBothWays() throws Exception {
        try (var server = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var keys = PlasmoVoiceProtocol.rsaKeyPair();
            byte[] aes = new byte[16]; new Random(3).nextBytes(aes);
            var key = new SecretKeySpec(aes, "AES");
            List<String> statuses = new CopyOnWriteArrayList<>();
            List<Object[]> heard = new CopyOnWriteArrayList<>();
            var connection = new PlasmoVoiceConnection("127.0.0.1", server.getLocalPort(), SECRET, SELF, keys, statuses::add,
                    (id, sequence, data) -> heard.add(new Object[] {id, sequence, data}));
            try {
                // the client pings with the server's address until the server pings back
                var first = receive(server);
                var ping = PlasmoVoiceProtocol.readDatagram(Arrays.copyOf(first.getData(), first.getLength()));
                assertEquals(PlasmoVoiceProtocol.PING, ping.type()); assertEquals(SECRET, ping.secret());
                SocketAddress client = first.getSocketAddress();
                byte[] serverPing = PlasmoVoiceProtocol.ping(new UUID(0, 0), null, 0);
                server.send(new DatagramPacket(serverPing, serverPing.length, client));
                var answer = receive(server);
                assertEquals(PlasmoVoiceProtocol.PING, PlasmoVoiceProtocol.readDatagram(Arrays.copyOf(answer.getData(), answer.getLength())).type());
                assertFalse(connection.connected(), "not ready before the config");

                var rsa = Cipher.getInstance("RSA"); rsa.init(Cipher.ENCRYPT_MODE, keys.getPublic());
                var activation = new PlasmoVoiceProtocol.Activation(PlasmoVoiceProtocol.activationId("proximity"), "proximity", List.of(8, 16), 16, true);
                connection.config(new PlasmoVoiceProtocol.Config(new UUID(9, 9), 48_000, 1024, "opus", "AES/CBC/PKCS5Padding", rsa.doFinal(aes), List.of(activation)));
                connection.players(List.of(new PlasmoVoiceProtocol.PlayerInfo(SELF, "Me", false, false, false), new PlasmoVoiceProtocol.PlayerInfo(FRIEND, "Friend", false, false, false)));
                waitFor(connection::connected, "connected");
                assertTrue(connection.participant(FRIEND)); assertFalse(connection.serverMuted());
                assertTrue(statuses.contains("Mit Plasmo Voice verbunden"), statuses.toString());

                // microphone → server: encrypted Opus with the proximity activation and its distance
                byte[] opus = {1, 2, 3, 4, 5};
                connection.audio(opus);
                DatagramPacket voice;
                do { voice = receive(server); } while (PlasmoVoiceProtocol.readDatagram(Arrays.copyOf(voice.getData(), voice.getLength())).type() != PlasmoVoiceProtocol.PLAYER_AUDIO);
                var body = PlasmoVoiceProtocol.readDatagram(Arrays.copyOf(voice.getData(), voice.getLength())).body();
                assertEquals(1, body.readLong(), "sequence starts at 1");
                assertArrayEquals(opus, PlasmoVoiceProtocol.decrypt(key, PlasmoVoiceProtocol.bytes(body, 2048)));
                assertEquals(activation.id(), PlasmoVoiceProtocol.uuid(body)); assertEquals(16, body.readShort()); assertFalse(body.readBoolean());

                // end of speech goes over the plugin channel (flushed on the client thread)
                connection.silenceAudio();
                List<byte[]> control = new ArrayList<>();
                connection.flush(control::add);
                assertEquals(1, control.size()); assertEquals(PlasmoVoiceProtocol.PLAYER_AUDIO_END, PlasmoVoiceProtocol.type(control.getFirst()));

                // server → voice of an unknown source: ask what it is, then play it as the friend
                byte[] sealed = PlasmoVoiceProtocol.encrypt(key, new byte[] {9, 9, 9});
                byte[] sound = PlasmoVoiceProtocol.datagram(PlasmoVoiceProtocol.SOURCE_AUDIO, new UUID(0, 0), out -> {
                    out.writeLong(4); PlasmoVoiceProtocol.bytes(out, sealed); PlasmoVoiceProtocol.uuid(out, SOURCE); out.writeByte(1); out.writeShort(24);
                });
                server.send(new DatagramPacket(sound, sound.length, client));
                waitFor(() -> { connection.flush(control::add); return control.size() == 2; }, "source info request");
                assertEquals(PlasmoVoiceProtocol.SOURCE_INFO_REQUEST, PlasmoVoiceProtocol.type(control.get(1)));
                assertTrue(heard.isEmpty(), "unknown sources stay silent");
                connection.source(new PlasmoVoiceProtocol.Source(SOURCE, "PLAYER", "Friend", 1, FRIEND));
                server.send(new DatagramPacket(sound, sound.length, client));
                waitFor(() -> !heard.isEmpty(), "voice played");
                assertEquals(FRIEND, heard.getFirst()[0]); assertEquals(4L, heard.getFirst()[1]);
                assertArrayEquals(new byte[] {9, 9, 9}, (byte[]) heard.getFirst()[2]);
                assertEquals(24, connection.range(FRIEND), 1e-6, "the speaker's distance");
            } finally { connection.close(); }
        }
    }

    @Test void unsupportedServersExplainWhy() throws Exception {
        var keys = PlasmoVoiceProtocol.rsaKeyPair();
        List<String> statuses = new CopyOnWriteArrayList<>();
        var connection = new PlasmoVoiceConnection("127.0.0.1", 9, SECRET, SELF, keys, statuses::add, (id, sequence, data) -> { });
        connection.config(new PlasmoVoiceProtocol.Config(new UUID(9, 9), 48_000, 1024, "pcm", "", new byte[0], List.of()));
        assertTrue(statuses.stream().anyMatch(s -> s.contains("Opus")), statuses.toString());
        assertFalse(connection.connected());
        connection.close();
    }

    @Test void groupVoiceGoesToTheGroupActivationAndGroupSpeakersPlayWithoutPosition() throws Exception {
        try (var server = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var keys = PlasmoVoiceProtocol.rsaKeyPair();
            byte[] aes = new byte[16]; new Random(4).nextBytes(aes);
            var key = new SecretKeySpec(aes, "AES");
            List<Object[]> heard = new CopyOnWriteArrayList<>();
            var connection = new PlasmoVoiceConnection("127.0.0.1", server.getLocalPort(), SECRET, SELF, keys, s -> { }, (id, sequence, data) -> heard.add(new Object[] {id, sequence}));
            try {
                var first = receive(server);
                SocketAddress client = first.getSocketAddress();
                byte[] serverPing = PlasmoVoiceProtocol.ping(new UUID(0, 0), null, 0);
                server.send(new DatagramPacket(serverPing, serverPing.length, client));
                var rsa = Cipher.getInstance("RSA"); rsa.init(Cipher.ENCRYPT_MODE, keys.getPublic());
                var proximity = new PlasmoVoiceProtocol.Activation(PlasmoVoiceProtocol.activationId("proximity"), "proximity", List.of(16), 16, true);
                var groups = new PlasmoVoiceProtocol.Activation(PlasmoVoiceProtocol.activationId("groups"), "groups", List.of(), 0, false);
                connection.config(new PlasmoVoiceProtocol.Config(new UUID(9, 9), 48_000, 1024, "opus", "AES/CBC/PKCS5Padding", rsa.doFinal(aes), List.of(proximity, groups)));
                connection.players(List.of(new PlasmoVoiceProtocol.PlayerInfo(FRIEND, "Friend", false, false, false)));
                waitFor(connection::connected, "connected");
                assertTrue(connection.hasGroups());

                connection.audio(new byte[] {1, 2, 3});
                Set<UUID> activations = new HashSet<>(); List<Integer> distances = new ArrayList<>();
                while (activations.size() < 2) {
                    var packet = receive(server);
                    var datagram = PlasmoVoiceProtocol.readDatagram(Arrays.copyOf(packet.getData(), packet.getLength()));
                    if (datagram.type() != PlasmoVoiceProtocol.PLAYER_AUDIO) continue;
                    var body = datagram.body(); body.readLong(); PlasmoVoiceProtocol.bytes(body, 2048);
                    activations.add(PlasmoVoiceProtocol.uuid(body)); distances.add((int) body.readShort());
                }
                assertEquals(Set.of(proximity.id(), groups.id()), activations, "the same frame to proximity and to the group");
                assertTrue(distances.contains(0) && distances.contains(16));
                connection.silenceAudio();
                List<byte[]> control = new ArrayList<>(); connection.flush(control::add);
                assertEquals(2, control.size(), "an end packet per activation");

                // the friend's group voice: a broadcast (DIRECT) source with the friend as sender
                UUID groupSource = new UUID(5, 5);
                connection.source(new PlasmoVoiceProtocol.Source(groupSource, "DIRECT", "Friend", 0, FRIEND));
                connection.source(new PlasmoVoiceProtocol.Source(SOURCE, "PLAYER", "Friend", 0, FRIEND));
                byte[] sealed = PlasmoVoiceProtocol.encrypt(key, new byte[] {7});
                byte[] group = PlasmoVoiceProtocol.datagram(PlasmoVoiceProtocol.SOURCE_AUDIO, new UUID(0, 0), out -> {
                    out.writeLong(11); PlasmoVoiceProtocol.bytes(out, sealed); PlasmoVoiceProtocol.uuid(out, groupSource); out.writeByte(0); out.writeShort(0);
                });
                byte[] near = PlasmoVoiceProtocol.datagram(PlasmoVoiceProtocol.SOURCE_AUDIO, new UUID(0, 0), out -> {
                    out.writeLong(12); PlasmoVoiceProtocol.bytes(out, sealed); PlasmoVoiceProtocol.uuid(out, SOURCE); out.writeByte(0); out.writeShort(16);
                });
                server.send(new DatagramPacket(group, group.length, client));
                waitFor(() -> heard.size() == 1, "group voice");
                assertEquals(Map.of(FRIEND, "Friend"), connection.unpositioned(), "played without position");
                server.send(new DatagramPacket(near, near.length, client));
                server.send(new DatagramPacket(group, group.length, client));
                waitFor(() -> heard.size() == 2, "second group frame");
                Thread.sleep(50);
                assertEquals(2, heard.size(), "the proximity copy of a group speaker is dropped");
                assertEquals(11L, heard.get(1)[1]);

                connection.groupVoice(false);
                connection.audio(new byte[] {4});
                PlasmoVoiceProtocol.Datagram datagram;
                do {
                    var packet = receive(server);
                    datagram = PlasmoVoiceProtocol.readDatagram(Arrays.copyOf(packet.getData(), packet.getLength()));
                } while (datagram.type() != PlasmoVoiceProtocol.PLAYER_AUDIO);
                var body = datagram.body(); body.readLong(); PlasmoVoiceProtocol.bytes(body, 2048);
                assertEquals(proximity.id(), PlasmoVoiceProtocol.uuid(body), "outside a group only proximity");
            } finally { connection.close(); }
        }
    }
}
