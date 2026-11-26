package dev.lumora.voice;

import dev.lumora.voice.audio.SilentAudio;
import dev.lumora.voice.platform.VoicePlatform;
import dev.lumora.voice.render.NameTagState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoiceClientTest {
    @TempDir Path directory;
    private FakePlatform platform;
    private VoiceConfig config;
    private VoiceClient client;

    @BeforeEach void setUp() {
        platform = new FakePlatform();
        config = VoiceConfig.load(directory.resolve("lumoravoice.json"));
    }

    @AfterEach void tearDown() { if (client != null) client.close(); }

    private VoiceClient client() {
        client = new VoiceClient(platform, config, "test", SilentAudio.create(config));
        return client;
    }

    private void ticks(int count) { for (int i = 0; i < count; i++) client.tick(); }

    private static VoicePlatform.Peer peer(String name, double x, double y, double z) {
        return new VoicePlatform.Peer(UUID.nameUUIDFromBytes(name.getBytes()), name, x, y, z, false, true, OptionalInt.empty());
    }

    private void inWorld() {
        platform.server = Optional.of(new VoicePlatform.Server("play.example.net", "203.0.113.5"));
        // standing at the origin, looking south (+Z): east (+X) is on the left, west (-X) on the right
        platform.listener = Optional.of(new VoicePlatform.Listener(UUID.randomUUID(), 0, 64, 0, 0, 0, 1, false));
    }

    @Test void playerMenuBlocksAndUnblocksAPlayer() {
        var c = client();
        UUID steve = UUID.nameUUIDFromBytes("Steve".getBytes());
        assertTrue(c.toggleBlock(steve, "Steve"));
        assertTrue(c.blocked(steve, "Steve"));
        assertTrue(platform.actionBar.get(platform.actionBar.size() - 1).contains("blockiert"));
        assertFalse(c.toggleBlock(steve, "Steve"), "a second time unblocks");
        assertFalse(c.blocked(steve, "Steve"));
    }

    @Test void parsesServerAddresses() {
        assertArrayEquals(new String[]{"play.example.net", "25565"}, VoiceClient.hostAndPort("play.example.net"));
        assertArrayEquals(new String[]{"play.example.net", "25570"}, VoiceClient.hostAndPort(" play.example.net:25570 "));
        assertArrayEquals(new String[]{"2001:db8::1", "25565"}, VoiceClient.hostAndPort("[2001:db8::1]"));
        assertArrayEquals(new String[]{"2001:db8::1", "1234"}, VoiceClient.hostAndPort("[2001:db8::1]:1234"));
        assertArrayEquals(new String[]{"2001:db8::1", "25565"}, VoiceClient.hostAndPort("2001:db8::1"));
        assertArrayEquals(new String[]{"host", "25565"}, VoiceClient.hostAndPort("host:99999"));
    }

    @Test void statusFollowsEnabledAndMultiplayer() {
        client();
        client.tick();
        assertEquals(VoiceClient.STATUS_OFF, client.status());
        config.enabled = true;
        client.tick();
        assertTrue(client.status().startsWith("Für VoiceChat einen Multiplayer-Server"));
        assertFalse(client.connected());
    }

    @Test void labyNeedsASignedInAccount() {
        config.enabled = true; config.service = VoiceConfig.Service.LABY; inWorld();
        client();
        ticks(VoiceClient.CONNECT_DELAY_TICKS);
        assertTrue(client.status().startsWith("Keine gültige Minecraft-Sitzung"), client.status());
        assertTrue(platform.joins.isEmpty());
    }

    @Test void automaticServiceUsesSimpleVoiceChatWhenTheServerOffersIt() throws IOException {
        client();
        assertEquals(VoiceConfig.Service.LABY, client.chosenService());
        platform.serverChannels.add("voicechat:request_secret");
        assertEquals(VoiceConfig.Service.SIMPLE_VOICE_CHAT, client.chosenService());

        config.enabled = true; inWorld();
        ticks(VoiceClient.CONNECT_DELAY_TICKS);
        assertEquals(List.of("voicechat:request_secret"), platform.sent);
        var request = new DataInputStream(new ByteArrayInputStream(platform.lastPayload.get("voicechat:request_secret")));
        assertEquals(dev.lumora.voice.svc.SimpleVoiceProtocol.COMPATIBILITY_VERSION, request.readInt());
        assertTrue(client.status().contains("angefragt"), client.status());
    }

    @Test void installedSimpleVoiceChatModKeepsItsChannels() {
        platform.mods.add("voicechat");
        platform.serverChannels.add("voicechat:request_secret");
        client();
        assertTrue(client.simpleVoiceModInstalled());
        assertTrue(platform.receivers.keySet().stream().noneMatch(id -> id.startsWith("voicechat:")), "no channel may be registered next to the real mod");
        assertEquals(VoiceConfig.Service.LABY, client.chosenService());
    }

    @Test void automaticServiceUsesPlasmoVoiceAndAnswersTheServer() throws Exception {
        client();
        platform.serverChannels.add("plasmo:voice/v2");
        assertEquals(VoiceConfig.Service.PLASMO_VOICE, client.chosenService());
        platform.serverChannels.add("voicechat:request_secret");
        assertEquals(VoiceConfig.Service.SIMPLE_VOICE_CHAT, client.chosenService(), "Simple Voice Chat keeps its priority");
        platform.serverChannels.remove("voicechat:request_secret");

        config.enabled = true; inWorld();
        platform.deliver("plasmo:voice/v2", new byte[] {dev.lumora.voice.plasmo.PlasmoVoiceProtocol.PLAYER_INFO_REQUEST});
        assertTrue(platform.sent.isEmpty(), "answered only once the service is chosen (after the join delay)");
        ticks(VoiceClient.CONNECT_DELAY_TICKS);
        assertEquals(List.of("plasmo:voice/v2"), platform.sent);
        var hello = new DataInputStream(new ByteArrayInputStream(platform.lastPayload.get("plasmo:voice/v2")));
        assertEquals(dev.lumora.voice.plasmo.PlasmoVoiceProtocol.PLAYER_INFO, hello.readUnsignedByte());
        hello.readBoolean(); hello.readBoolean();
        assertEquals("test", hello.readUTF());
        assertEquals(dev.lumora.voice.plasmo.PlasmoVoiceProtocol.VERSION, hello.readUTF());
        byte[] key = new byte[hello.readInt()]; hello.readFully(key);
        assertEquals("RSA", java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(key)).getAlgorithm());
        assertTrue(client.status().contains("Plasmo Voice"), client.status());
    }

    @Test void otherServicesIgnorePlasmoVoiceRequests() {
        config.service = VoiceConfig.Service.LABY;
        platform.serverChannels.add("plasmo:voice/v2");
        client(); config.enabled = true; inWorld();
        ticks(VoiceClient.CONNECT_DELAY_TICKS + 1);
        platform.deliver("plasmo:voice/v2", new byte[] {dev.lumora.voice.plasmo.PlasmoVoiceProtocol.PLAYER_INFO_REQUEST});
        assertFalse(platform.sent.contains("plasmo:voice/v2"), "Laby Voice stays as it is");
    }

    @Test void installedPlasmoVoiceModKeepsItsChannels() {
        platform.mods.add("plasmovoice");
        platform.serverChannels.add("plasmo:voice/v2");
        client();
        assertTrue(client.plasmoVoiceModInstalled());
        assertTrue(platform.receivers.keySet().stream().noneMatch(id -> id.startsWith("plasmo:")), "no channel next to the real mod");
        assertEquals(VoiceConfig.Service.LABY, client.chosenService());
    }

    @Test void blockedAndOutOfRangePlayersAreNotMixed() {
        config.enabled = true; inWorld();
        var near = peer("Near", 0, 64, 5);
        var far = peer("Far", 0, 64, 40);
        var blocked = peer("Blocked", 0, 64, 3);
        config.block(blocked.id(), blocked.name());
        platform.players.addAll(List.of(near, far, blocked));
        client(); client.tick();
        var names = client.nearby().stream().map(VoiceClient.Nearby::name).toList();
        assertEquals(List.of("Near"), names);
        assertEquals(5, client.nearby().getFirst().distance(), 1e-4);
    }

    @Test void panFollowsViewDirection() throws Exception {
        config.enabled = true; inWorld();
        var west = peer("West", -4, 64, 0); // on the right when looking south
        var east = peer("East", 4, 64, 0);
        platform.players.addAll(List.of(west, east));
        client(); client.tick();
        var field = client.audio().getClass().getDeclaredField("positions"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var positions = (Map<UUID, dev.lumora.voice.audio.VoiceAudio.Spatial>) field.get(client.audio());
        assertEquals(1, positions.get(west.id()).pan(), 1e-4);
        assertEquals(-1, positions.get(east.id()).pan(), 1e-4);
    }

    @Test void ambientSoundMufflesOccludedAndEchoesInCaves() throws Exception {
        config.enabled = true; config.ambientSound = true; inWorld();
        var behindWall = peer("Wall", 0, 64, 4);
        var cave = new VoicePlatform.Peer(UUID.randomUUID(), "Cave", 0, 52, 2, false, false, OptionalInt.empty());
        platform.occluded.add(behindWall.id());
        platform.players.addAll(List.of(behindWall, cave));
        client(); client.tick();
        var field = client.audio().getClass().getDeclaredField("positions"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var positions = (Map<UUID, dev.lumora.voice.audio.VoiceAudio.Spatial>) field.get(client.audio());
        assertTrue(positions.get(behindWall.id()).muffle());
        assertFalse(positions.get(behindWall.id()).echo());
        assertTrue(positions.get(cave.id()).echo());
    }

    @Test void serverChangeDropsOldState() {
        config.enabled = true; inWorld();
        platform.players.add(peer("Near", 0, 64, 2));
        client(); client.tick();
        assertEquals(1, client.nearby().size());
        platform.server = Optional.empty(); platform.listener = Optional.empty();
        client.tick();
        assertTrue(client.nearby().isEmpty());
    }

    @Test void blockKeyTogglesTheTargetAndExplainsItself() {
        client();
        client.toggleBlockTarget();
        assertEquals("Schau einen Spieler an, um ihn zu blockieren.", platform.actionBar.getLast());
        UUID id = UUID.randomUUID();
        platform.target = Optional.of(new VoicePlatform.Named(id, "Steve"));
        client.toggleBlockTarget();
        assertTrue(config.isBlocked(id));
        assertTrue(platform.actionBar.getLast().startsWith("Steve ist blockiert"));
        assertEquals(1, platform.sounds);
        assertTrue(VoiceConfig.load(directory.resolve("lumoravoice.json")).isBlocked(id), "block list is saved");
        client.toggleBlockTarget();
        assertFalse(config.isBlocked(id));
    }

    @Test void blockedByTabListNameAndNameTagsWithoutConnection() {
        UUID id = UUID.randomUUID();
        config.block(UUID.randomUUID(), "Griefer");
        platform.tabList.add(new VoicePlatform.Named(id, "Griefer"));
        client();
        assertTrue(client.blocked(id));
        config.enabled = true;
        assertEquals(NameTagState.ABSENT, client.nameTagState(UUID.randomUUID()));
    }

    @Test void ownNameTagShowsThePlugWhileVoiceIsNotConnected() {
        UUID self = UUID.randomUUID();
        platform.profile = Optional.of(new VoicePlatform.Profile(self, "Me"));
        client();
        config.enabled = true;
        assertEquals(NameTagState.ABSENT, client.nameTagState(self), "singleplayer: no voice expected");
        inWorld();
        assertEquals(NameTagState.DISCONNECTED, client.nameTagState(self), "on a server without a voice connection");
        assertEquals(NameTagState.ABSENT, client.nameTagState(UUID.randomUUID()), "others: unknown while you are not connected");
        config.enabled = false;
        assertEquals(NameTagState.ABSENT, client.nameTagState(self), "voice chat switched off");
        assertEquals(0xFFFFFF, NameTagState.DISCONNECTED.color(), "drawn in its own colors (red slash)");
        assertEquals(0xFFFFFF, NameTagState.MUTED.color());
    }

    @Test void playersMutedByTheVoiceServiceGetTheMutedMicrophone() {
        UUID self = UUID.randomUUID(), muted = UUID.randomUUID(), other = UUID.randomUUID(), grouped = UUID.randomUUID();
        platform.profile = Optional.of(new VoicePlatform.Profile(self, "Me"));
        client();
        config.enabled = true;
        inWorld();
        boolean[] selfMuted = {false};
        client.useConnection(new dev.lumora.voice.protocol.VoiceService() {
            @Override public String name() { return "Laby Voice"; }
            @Override public boolean connected() { return true; }
            @Override public boolean serverMuted() { return selfMuted[0]; }
            @Override public boolean serverMuted(UUID id) { return id.equals(muted); }
            @Override public void audio(byte[] opus) {}
            @Override public void silenceAudio() {}
            @Override public boolean participant(UUID id) { return id.equals(other) || id.equals(grouped); }
            @Override public boolean otherGroup(UUID id) { return id.equals(grouped) || id.equals(self); }
            @Override public void close() {}
        });
        assertEquals(NameTagState.GROUP, client.nameTagState(grouped), "in a group you are not in");
        assertEquals(0xFFFFFF, NameTagState.GROUP.color());
        assertEquals(NameTagState.SERVER_MUTED, client.nameTagState(muted), "name tag and tab list");
        assertEquals(NameTagState.IDLE, client.nameTagState(other));
        assertEquals(NameTagState.IDLE, client.nameTagState(self));
        selfMuted[0] = true;
        assertEquals(NameTagState.SERVER_MUTED, client.nameTagState(self), "you yourself, muted by Laby");
        config.player(muted, "Muted").muted = true;
        assertEquals(NameTagState.SERVER_MUTED, client.nameTagState(muted), "the service mute comes before your own");
        config.block(muted, "Muted");
        assertEquals(NameTagState.ABSENT, client.nameTagState(muted), "blocked players show nothing");
        assertEquals(0xFFFFFF, NameTagState.SERVER_MUTED.color());
    }

    @Test void keyActionsSaveAndNotifyListeners() {
        client();
        int[] calls = {0};
        client.onChange(() -> calls[0]++);
        client.toggleMute();
        client.toggleOutputMute();
        client.toggleHud();
        assertTrue(config.muted); assertTrue(config.outputMuted); assertFalse(config.showHud);
        assertEquals(3, calls[0]);
        var stored = VoiceConfig.load(directory.resolve("lumoravoice.json"));
        assertTrue(stored.muted && stored.outputMuted && !stored.showHud);
    }
}
