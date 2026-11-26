package dev.lumora.voice.plasmo;

import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Packets built the way a Plasmo Voice server writes them (field by field from its protocol). */
class PlasmoVoiceProtocolTest {
    private static final UUID SERVER = new UUID(1, 1), SECRET = new UUID(2, 2), PLAYER = new UUID(3, 3), SOURCE = new UUID(4, 4);

    private static byte[] packet(int id, PlasmoVoiceProtocol.Writer body) {
        return PlasmoVoiceProtocol.write(out -> { out.writeByte(id); body.write(out); });
    }

    private static void codec(DataOutputStream out) throws IOException {
        out.writeUTF("opus"); out.writeInt(2); out.writeUTF("mode"); out.writeUTF("VOIP"); out.writeUTF("bitrate"); out.writeUTF("-1000");
    }

    private static void activation(DataOutputStream out, String name, boolean proximity, int defaultDistance) throws IOException {
        out.writeUTF(name); out.writeUTF("pv.activation." + name); out.writeUTF("icon");
        out.writeInt(3); out.writeInt(8); out.writeInt(16); out.writeInt(32); out.writeInt(defaultDistance);
        out.writeBoolean(proximity); out.writeBoolean(false); out.writeBoolean(true);
        out.writeBoolean(false); // no own encoder
        out.writeInt(1);
    }

    private static void playerInfo(DataOutputStream out, UUID id, String name, boolean muted) throws IOException {
        PlasmoVoiceProtocol.uuid(out, id); out.writeUTF(name); out.writeBoolean(muted); out.writeBoolean(false); out.writeBoolean(false);
    }

    @Test void configGivesTheAesKeyAndTheProximityActivation() throws Exception {
        var keys = PlasmoVoiceProtocol.rsaKeyPair();
        byte[] aes = new byte[16]; new Random(5).nextBytes(aes);
        var rsa = Cipher.getInstance("RSA"); rsa.init(Cipher.ENCRYPT_MODE, keys.getPublic());
        byte[] encryptedKey = rsa.doFinal(aes);
        byte[] config = packet(PlasmoVoiceProtocol.CONFIG, out -> {
            PlasmoVoiceProtocol.uuid(out, SERVER);
            out.writeInt(48_000); out.writeInt(1024); out.writeBoolean(true); codec(out);
            out.writeBoolean(true); out.writeUTF("AES/CBC/PKCS5Padding"); PlasmoVoiceProtocol.bytes(out, encryptedKey);
            out.writeInt(1); // one source line with a player list
            out.writeUTF("proximity"); out.writeUTF("pv.line"); out.writeUTF("icon"); out.writeDouble(1); out.writeInt(1);
            out.writeBoolean(true); out.writeInt(1); PlasmoVoiceProtocol.uuid(out, PLAYER); out.writeUTF("Steve"); out.writeInt(1); out.writeUTF("textures"); out.writeUTF("v"); out.writeUTF("");
            out.writeInt(2); activation(out, "groups", false, 0); activation(out, "proximity", true, 16);
            out.writeInt(1); out.writeUTF("pv.allow.freecam"); out.writeBoolean(true); // permissions
            out.writeInt(0); out.writeDouble(0); out.writeDouble(0.5); out.writeDouble(0); // icon config
        });
        assertEquals(PlasmoVoiceProtocol.CONFIG, PlasmoVoiceProtocol.type(config));
        var read = PlasmoVoiceProtocol.readConfig(config);
        assertEquals(SERVER, read.serverId()); assertEquals(48_000, read.sampleRate()); assertEquals("opus", read.codec());
        assertEquals(2, read.activations().size());
        var proximity = read.proximity();
        assertEquals("proximity", proximity.name()); assertEquals(16, proximity.defaultDistance()); assertEquals(List.of(8, 16, 32), proximity.distances());
        assertEquals(UUID.nameUUIDFromBytes("proximity_activation".getBytes(java.nio.charset.StandardCharsets.UTF_8)), proximity.id());
        assertArrayEquals(aes, PlasmoVoiceProtocol.aesKey(keys.getPrivate(), read.encryptedKey()).getEncoded());
    }

    @Test void playersSourcesAndConnection() throws IOException {
        var connection = PlasmoVoiceProtocol.readConnection(packet(PlasmoVoiceProtocol.CONNECTION, out -> { PlasmoVoiceProtocol.uuid(out, SECRET); out.writeUTF("0.0.0.0"); out.writeInt(60606); }));
        assertEquals(new PlasmoVoiceProtocol.Connection(SECRET, "0.0.0.0", 60606), connection);
        var list = PlasmoVoiceProtocol.readPlayerList(packet(PlasmoVoiceProtocol.PLAYER_LIST, out -> { out.writeInt(2); playerInfo(out, PLAYER, "Steve", false); playerInfo(out, SECRET, "Alex", true); }));
        assertEquals(List.of("Steve", "Alex"), list.stream().map(PlasmoVoiceProtocol.PlayerInfo::name).toList());
        assertTrue(list.get(1).muted());
        var source = PlasmoVoiceProtocol.readSource(packet(PlasmoVoiceProtocol.SOURCE_INFO, out -> {
            out.writeUTF("PLAYER"); out.writeUTF("pv-addon-proximity"); PlasmoVoiceProtocol.uuid(out, SOURCE);
            out.writeBoolean(true); out.writeUTF("Steve"); out.writeByte(3);
            out.writeBoolean(true); codec(out); out.writeBoolean(false); PlasmoVoiceProtocol.uuid(out, new UUID(9, 9)); out.writeBoolean(true); out.writeInt(360);
            playerInfo(out, PLAYER, "Steve", false);
        }));
        assertEquals(new PlasmoVoiceProtocol.Source(SOURCE, "PLAYER", "Steve", 3, PLAYER), source);
        var statik = PlasmoVoiceProtocol.readSource(packet(PlasmoVoiceProtocol.SOURCE_INFO, out -> {
            out.writeUTF("STATIC"); out.writeUTF("addon"); PlasmoVoiceProtocol.uuid(out, SOURCE); out.writeBoolean(false); out.writeByte(0);
            out.writeBoolean(false); out.writeBoolean(false); PlasmoVoiceProtocol.uuid(out, new UUID(9, 9)); out.writeBoolean(false); out.writeInt(0);
            for (int i = 0; i < 6; i++) out.writeDouble(i);
        }));
        assertNull(statik.player(), "only player sources are mapped to a player");
        var broadcast = PlasmoVoiceProtocol.readSource(packet(PlasmoVoiceProtocol.SOURCE_INFO, out -> {
            out.writeUTF("DIRECT"); out.writeUTF("pv-addon-groups"); PlasmoVoiceProtocol.uuid(out, SOURCE); out.writeBoolean(false); out.writeByte(2);
            out.writeBoolean(false); out.writeBoolean(false); PlasmoVoiceProtocol.uuid(out, new UUID(9, 9)); out.writeBoolean(true); out.writeInt(0);
            out.writeBoolean(true); PlasmoVoiceProtocol.uuid(out, PLAYER); out.writeUTF("Steve"); out.writeInt(0); // sender profile
            out.writeBoolean(false); out.writeBoolean(false); // no relative position, not camera relative
        }));
        assertEquals(new PlasmoVoiceProtocol.Source(SOURCE, "DIRECT", "Steve", 2, PLAYER), broadcast, "group voice: the sender is the speaker");
    }

    @Test void clientPacketsMatchTheServerReader() throws IOException {
        byte[] hello = PlasmoVoiceProtocol.playerInfo("1.21.11", "2.1.0", new byte[] {1, 2, 3}, false, true);
        var in = new DataInputStream(new ByteArrayInputStream(hello));
        assertEquals(PlasmoVoiceProtocol.PLAYER_INFO, in.readUnsignedByte());
        assertFalse(in.readBoolean()); assertTrue(in.readBoolean());
        assertEquals("1.21.11", in.readUTF()); assertEquals("2.1.0", in.readUTF()); assertEquals(3, in.readInt());
        byte[] end = PlasmoVoiceProtocol.audioEnd(7, SOURCE, 16);
        assertEquals(1 + 8 + 16 + 2, end.length);
        assertArrayEquals(new byte[] {PlasmoVoiceProtocol.PLAYER_STATE, 1, 0}, PlasmoVoiceProtocol.playerState(true, false));
        assertEquals(17, PlasmoVoiceProtocol.sourceInfoRequest(SOURCE).length);
    }

    @Test void voiceDatagramsAreEncryptedAndFiltered() throws Exception {
        var key = new javax.crypto.spec.SecretKeySpec(new byte[16], "AES");
        byte[] opus = {10, 20, 30, 40};
        byte[] sealed = PlasmoVoiceProtocol.encrypt(key, opus);
        assertEquals(32, sealed.length, "16 IV + one AES block");
        assertArrayEquals(opus, PlasmoVoiceProtocol.decrypt(key, sealed));

        byte[] mine = PlasmoVoiceProtocol.playerAudio(SECRET, 5, sealed, SOURCE, 16);
        var datagram = PlasmoVoiceProtocol.readDatagram(mine);
        assertEquals(PlasmoVoiceProtocol.PLAYER_AUDIO, datagram.type()); assertEquals(SECRET, datagram.secret());
        assertEquals(5, datagram.body().readLong());

        byte[] incoming = PlasmoVoiceProtocol.datagram(PlasmoVoiceProtocol.SOURCE_AUDIO, SECRET, out -> {
            out.writeLong(9); PlasmoVoiceProtocol.bytes(out, sealed); PlasmoVoiceProtocol.uuid(out, SOURCE); out.writeByte(3); out.writeShort(24);
        });
        var sound = PlasmoVoiceProtocol.readSourceAudio(PlasmoVoiceProtocol.readDatagram(incoming).body());
        assertEquals(9, sound.sequence()); assertEquals(SOURCE, sound.source()); assertEquals(3, sound.state()); assertEquals(24, sound.distance());
        assertArrayEquals(opus, PlasmoVoiceProtocol.decrypt(key, sound.data()));

        assertNull(PlasmoVoiceProtocol.readDatagram(new byte[] {1, 2, 3}), "too short");
        assertNull(PlasmoVoiceProtocol.readDatagram(new byte[30]), "wrong magic number");
        var ping = PlasmoVoiceProtocol.readDatagram(PlasmoVoiceProtocol.ping(SECRET, "play.example.net", 60606));
        assertEquals(PlasmoVoiceProtocol.PING, ping.type());
        ping.body().readLong(); assertEquals("play.example.net", ping.body().readUTF()); assertEquals(60606, ping.body().readUnsignedShort());
    }

    @Test void channelDispatchesAndSurvivesBrokenPackets() {
        List<String> calls = new ArrayList<>();
        var handler = new PlasmoVoiceChannel.Handler() {
            @Override public void playerInfoRequest() { calls.add("request"); }
            @Override public void connection(PlasmoVoiceProtocol.Connection connection) { calls.add("connection"); }
            @Override public void config(PlasmoVoiceProtocol.Config config) { calls.add("config"); }
            @Override public void players(List<PlasmoVoiceProtocol.PlayerInfo> players) { calls.add("players"); }
            @Override public void player(PlasmoVoiceProtocol.PlayerInfo player) { calls.add("player"); }
            @Override public void playerDisconnect(UUID id) { calls.add("disconnect " + id); }
            @Override public void source(PlasmoVoiceProtocol.Source source) { calls.add("source"); }
        };
        PlasmoVoiceChannel.receive(handler, new byte[] {PlasmoVoiceProtocol.PLAYER_INFO_REQUEST});
        PlasmoVoiceChannel.receive(handler, packet(PlasmoVoiceProtocol.PLAYER_DISCONNECT, out -> PlasmoVoiceProtocol.uuid(out, PLAYER)));
        PlasmoVoiceChannel.receive(handler, new byte[] {PlasmoVoiceProtocol.CONFIG, 1, 2}); // truncated: ignored
        PlasmoVoiceChannel.receive(handler, new byte[] {26}); // action bar: not needed
        PlasmoVoiceChannel.receive(handler, new byte[0]);
        assertEquals(List.of("request", "disconnect " + PLAYER), calls);
    }
}
