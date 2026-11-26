package dev.lumora.voice.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoiceChannelsTest {
    private static DataInputStream input(VoiceProtocol.Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream(); writer.write(new DataOutputStream(bytes)); return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }
    @Test void channelSnapshotsTrackMembershipAndRejectOtherChannelAudio() throws Exception {
        UUID self = UUID.randomUUID(), friend = UUID.randomUUID(), stranger = UUID.randomUUID(), channel = UUID.randomUUID(); var state = new VoiceChannels(self);
        state.receive(34, input(out -> { out.writeShort(1); VoiceProtocol.uuid(out, channel); VoiceChannels.properties(out, Map.of("name", "Freunde", "proximity", "false", "password", "true")); }));
        state.receive(37, input(out -> {
            out.writeShort(3);
            VoiceProtocol.uuid(out, self); VoiceProtocol.uuid(out, channel); VoiceChannels.properties(out, Map.of("name", "Me"));
            VoiceProtocol.uuid(out, friend); VoiceProtocol.uuid(out, channel); VoiceChannels.properties(out, Map.of("name", "Friend"));
            VoiceProtocol.uuid(out, stranger); VoiceProtocol.uuid(out, VoiceChannels.PROXIMITY); VoiceChannels.properties(out, Map.of("name", "Other"));
        }));
        assertEquals("Freunde", state.current().name()); assertTrue(state.current().password()); assertTrue(state.remoteAllowed(friend)); assertFalse(state.remoteAllowed(stranger));
        state.receive(39, input(out -> { VoiceProtocol.uuid(out, self); VoiceProtocol.uuid(out, channel); VoiceProtocol.uuid(out, VoiceChannels.PROXIMITY); VoiceChannels.properties(out, Map.of()); }));
        assertFalse(state.remoteAllowed(friend)); assertTrue(state.current().proximity());
        assertTrue(state.otherGroup(friend), "friend stayed in the channel you left");
        assertFalse(state.otherGroup(stranger), "proximity is no group"); assertFalse(state.otherGroup(self));
    }
    @Test void membersOfYourOwnChannelAreNoOtherGroup() throws Exception {
        UUID self = UUID.randomUUID(), friend = UUID.randomUUID(), channel = UUID.randomUUID(); var state = new VoiceChannels(self);
        state.receive(34, input(out -> { out.writeShort(1); VoiceProtocol.uuid(out, channel); VoiceChannels.properties(out, Map.of("name", "Freunde", "proximity", "false")); }));
        state.receive(37, input(out -> {
            out.writeShort(2);
            VoiceProtocol.uuid(out, self); VoiceProtocol.uuid(out, channel); VoiceChannels.properties(out, Map.of("name", "Me"));
            VoiceProtocol.uuid(out, friend); VoiceProtocol.uuid(out, channel); VoiceChannels.properties(out, Map.of("name", "Friend"));
        }));
        assertFalse(state.otherGroup(friend));
        assertFalse(state.otherGroup(UUID.randomUUID()), "unknown players");
    }
    @Test void channelCountsAndTruncatedPropertyStringsAreBounded() throws Exception {
        var state = new VoiceChannels(UUID.randomUUID());
        assertThrows(IOException.class, () -> state.receive(34, input(out -> out.writeShort(5000))));
        assertThrows(IOException.class, () -> VoiceChannels.properties(input(out -> { out.writeShort(1); out.writeInt(1000); out.writeByte(1); })));
        assertThrows(IOException.class, () -> VoiceChannels.properties(input(out -> out.writeShort(1000))));
    }
    @Test void outgoingChannelControlsMatchTheCurrentWireRegistry() throws Exception {
        byte[] create = VoiceProtocol.packet(VoicePackets.CREATE_CHANNEL, out -> VoiceChannels.properties(out, Map.of("name", "x")));
        assertEquals("1d0001000000046e616d650000000178", HexFormat.of().formatHex(create));
        byte[] join = VoiceProtocol.packet(VoicePackets.MOVE_USER, out -> { VoiceProtocol.uuid(out, new UUID(0, 1)); VoiceProtocol.uuid(out, new UUID(0, 2)); VoiceProtocol.string(out, ""); });
        assertEquals(32, join[0]); assertEquals(37, join.length);
        assertEquals(33, VoicePackets.USER_PROPERTIES); assertEquals(34, VoicePackets.CHANNEL_SHOW);
    }
}
