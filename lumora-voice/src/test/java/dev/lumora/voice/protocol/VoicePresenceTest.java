package dev.lumora.voice.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoicePresenceTest {
    private static DataInputStream packet(boolean alive, UUID id) throws Exception {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        if (alive) out.writeShort(1); else out.writeByte(1); VoiceProtocol.uuid(out, id);
        if (alive) out.writeShort(0);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }
    @Test void idleParticipantRemainsPresentUntilTheServiceAnnouncesDeparture() throws Exception {
        UUID id = UUID.randomUUID(); var state = new VoicePresence();
        assertFalse(state.contains(id)); assertTrue(state.receive(10, packet(true, id))); assertTrue(state.contains(id));
        assertFalse(state.receive(3, packet(true, id))); assertTrue(state.contains(id));
        assertTrue(state.receive(11, packet(false, id))); assertFalse(state.contains(id));
    }
    @Test void malformedPresenceDoesNotPartiallyApplyAnUpdate() throws Exception {
        var state = new VoicePresence(); UUID id = UUID.randomUUID();
        assertThrows(IOException.class, () -> state.receive(10, new DataInputStream(new ByteArrayInputStream(new byte[]{0, 1, 0}))));
        assertFalse(state.contains(id)); state.receive(10, packet(true, id)); state.clear(); assertFalse(state.contains(id));
    }
    @Test void protocolSixPresenceWithRankMetadataSurvivesSilenceAndLeavesCleanly() throws Exception {
        UUID id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        // Independent protocol-6 wire fixture: one UUID followed by one UUID/JSON metadata pair.
        byte[] fixture = HexFormat.of().parseHex("000100112233445566778899aabbccddeeff000100112233445566778899aabbccddeeff000000107b2272616e6b223a2241646d696e227d");
        var presence = new VoicePresence();
        assertTrue(presence.receive(10, new DataInputStream(new ByteArrayInputStream(fixture))));
        for (int tick = 0; tick < 600; tick++) assertTrue(presence.contains(id), "Idle status must not depend on audio frames");
        presence.receive(11, packet(false, id)); assertFalse(presence.contains(id));
        var truncated = Arrays.copyOf(fixture, fixture.length - 1);
        assertThrows(IOException.class, () -> presence.receive(10, new DataInputStream(new ByteArrayInputStream(truncated))));
        assertFalse(presence.contains(id), "Malformed metadata must not partially announce a participant");
    }
}
