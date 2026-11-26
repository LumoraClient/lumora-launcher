package dev.lumora.voice.svc;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SimpleVoiceChannelTest {
    @Test void secretRoundTripsInMinecraftByteOrder() throws IOException {
        byte[] key = new byte[SimpleVoiceProtocol.SECRET_BYTES];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i * 7);
        UUID player = UUID.randomUUID();
        var sent = new SimpleVoiceChannel.Secret(key, 24454, player, 1, 1024, 48.5, 1000, true, "voice.example.net:24455", false);
        byte[] bytes = SimpleVoiceChannel.writeSecret(sent);
        // 16 key + port 4 + uuid 16 + codec 1 + mtu 4 + distance 8 + keepAlive 4 + groups 1 + VarInt 1 + 23 host + recording 1
        assertEquals(16 + 4 + 16 + 1 + 4 + 8 + 4 + 1 + 1 + 23 + 1, bytes.length);
        var read = SimpleVoiceChannel.readSecret(bytes);
        assertArrayEquals(key, read.secret());
        assertEquals(24454, read.port());
        assertEquals(player, read.player());
        assertEquals(48.5, read.distance());
        assertEquals("voice.example.net:24455", read.voiceHost());
        assertTrue(read.groups());
        assertEquals(0x5F, bytes[16 + 2] & 0xFF, "port is big-endian (24454 = 0x5F86)");
    }

    @Test void statesRoundTripWithAndWithoutGroups() throws IOException {
        var states = List.of(
                new SimpleVoiceConnection.State(UUID.randomUUID(), "Älex", false, false, null),
                new SimpleVoiceConnection.State(UUID.randomUUID(), "Steve", true, true, UUID.randomUUID()));
        assertEquals(states, SimpleVoiceChannel.readStates(SimpleVoiceChannel.writeStates(states)));
        var single = states.get(1);
        assertEquals(single, SimpleVoiceChannel.readState(SimpleVoiceChannel.in(SimpleVoiceChannel.writeState(single))));
    }

    @Test void varIntsAndLongStrings() throws IOException {
        for (int value : new int[]{0, 1, 127, 128, 300, 2097151, Integer.MAX_VALUE}) {
            byte[] bytes = SimpleVoiceChannel.write(out -> SimpleVoiceChannel.writeVarInt(out, value));
            assertEquals(value, SimpleVoiceChannel.readVarInt(SimpleVoiceChannel.in(bytes)));
        }
        String text = "ä".repeat(200);
        byte[] bytes = SimpleVoiceChannel.write(out -> SimpleVoiceChannel.writeUtf(out, text));
        assertEquals(2, bytes.length - 400, "400 UTF-8 bytes need a two-byte VarInt");
        assertEquals(text, SimpleVoiceChannel.readUtf(SimpleVoiceChannel.in(bytes)));
    }

    @Test void rejectsHostileCounts() {
        byte[] tooMany = SimpleVoiceChannel.write(out -> out.writeInt(100_000));
        assertThrows(IOException.class, () -> SimpleVoiceChannel.readStates(tooMany));
        byte[] negative = SimpleVoiceChannel.write(out -> out.writeInt(-1));
        assertThrows(IOException.class, () -> SimpleVoiceChannel.readStates(negative));
        byte[] truncated = Arrays.copyOf(SimpleVoiceChannel.writeSecret(new SimpleVoiceChannel.Secret(new byte[16], 1, UUID.randomUUID(), 0, 0, 0, 0, false, "", false)), 20);
        assertThrows(IOException.class, () -> SimpleVoiceChannel.readSecret(truncated));
    }

    @Test void groupPacketsMatchTheSimpleVoiceChatLayout() throws IOException {
        UUID id = new UUID(1, 2);
        var group = new SvcGroups.Group(id, "Bauteam", true, false, false, SvcGroups.Type.OPEN);
        byte[] add = SimpleVoiceChannel.writeGroup(group);
        // uuid 16 + VarInt 1 + 7 name + password, persistent, hidden 3 + type short 2
        assertEquals(16 + 1 + 7 + 3 + 2, add.length);
        assertEquals(group, SimpleVoiceChannel.readGroup(add));
        assertThrows(IOException.class, () -> SimpleVoiceChannel.readGroup(Arrays.copyOf(add, add.length - 2)), "missing type");
    }

    @Test void joiningCreatingAndAnswersAreEncodedByteByByte() throws IOException {
        UUID id = new UUID(1, 2);
        byte[] join = SimpleVoiceChannel.writeSetGroup(id, "");
        assertEquals(17, join.length, "uuid and no password");
        assertEquals(0, join[16]);
        byte[] joinSecret = SimpleVoiceChannel.writeSetGroup(id, "geheim");
        assertEquals(16 + 1 + 1 + 6, joinSecret.length);
        assertEquals(1, joinSecret[16]); assertEquals(6, joinSecret[17]);
        byte[] create = SimpleVoiceChannel.writeCreateGroup("Bau", "", SvcGroups.Type.ISOLATED);
        assertArrayEquals(new byte[] {3, 'B', 'a', 'u', 0, 0, 2}, create, "name, no password, type 2");
        var answer = SimpleVoiceChannel.readJoined(SimpleVoiceChannel.write(out -> { out.writeBoolean(true); SimpleVoiceChannel.writeUuid(out, id); out.writeBoolean(false); }));
        assertEquals(id, answer.group()); assertFalse(answer.wrongPassword());
        var wrong = SimpleVoiceChannel.readJoined(new byte[] {0, 1});
        assertNull(wrong.group()); assertTrue(wrong.wrongPassword());
    }

    @Test void groupListHidesHiddenGroupsAndChecksNames() {
        var groups = new SvcGroups();
        groups.add(new SvcGroups.Group(new UUID(0, 1), "zeta", false, false, false, SvcGroups.Type.NORMAL));
        groups.add(new SvcGroups.Group(new UUID(0, 2), "Alpha", false, false, false, SvcGroups.Type.NORMAL));
        groups.add(new SvcGroups.Group(new UUID(0, 3), "versteckt", false, false, true, SvcGroups.Type.NORMAL));
        assertEquals(List.of("Alpha", "zeta"), groups.list().stream().map(SvcGroups.Group::name).toList());
        groups.joined(null, true);
        assertEquals("Falsches Passwort.", groups.alert());
        groups.joined(new UUID(0, 2), false);
        assertEquals("", groups.alert());
        assertTrue(SvcGroups.validName("Bauteam"));
        assertFalse(SvcGroups.validName(" Bauteam"), "leading space");
        assertFalse(SvcGroups.validName("x".repeat(25)), "too long");
        assertFalse(SvcGroups.validName(""));
        assertTrue(SvcGroups.validPassword(""));
        assertEquals(SvcGroups.Type.OPEN, SvcGroups.Type.NORMAL.next());
    }
}
