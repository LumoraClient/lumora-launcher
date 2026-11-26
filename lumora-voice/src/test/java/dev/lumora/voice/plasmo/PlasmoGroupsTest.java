package dev.lumora.voice.plasmo;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** pv-addon-groups over its channel, against a scripted server (messages as kotlinx-serialization-msgpack writes them). */
class PlasmoGroupsTest {
    private static final UUID GROUP = new UUID(10, 10), ME = new UUID(1, 1), FRIEND = new UUID(2, 2);
    private final List<byte[]> sent = new ArrayList<>();
    private final PlasmoGroups groups = new PlasmoGroups(sent::add);

    /** The last request: type, request id and decoded payload. */
    private record Request(String type, String id, List<Object> payload) {}

    private Request last() throws IOException {
        var envelope = MsgPack.map(MsgPack.readAll(sent.getLast()).getFirst());
        return new Request(MsgPack.text(envelope.get("type")), MsgPack.text(envelope.get("requestId")), MsgPack.readAll((byte[]) envelope.get("payload")));
    }

    private void answer(Request request, String type, MsgPack.Writer payload) throws IOException {
        groups.receive(new MsgPack.Writer().map(3).string("type").string(type).string("requestId").string(request.id()).string("payload").bin(payload.bytes()).bytes());
    }

    private static MsgPack.Writer profile(MsgPack.Writer w, UUID id, String name, boolean online) throws IOException {
        return w.map(2).string("profile").map(3).string("id").uuid(id).string("name").string(name).string("properties").array(0).string("online").bool(online);
    }

    private static MsgPack.Writer summary(MsgPack.Writer w, String name, boolean password, int online) throws IOException {
        w.map(9).string("id").uuid(GROUP).string("name").string(name).string("owner").nil().string("hasPassword").bool(password).string("persistent").bool(false)
                .string("restricted").bool(false).string("onlineCount").integer(online).string("playerCount").integer(online).string("joinState").string(password ? "PASSWORD" : "JOINABLE");
        return w;
    }

    private static byte[] stateBytes(boolean inGroup, boolean wrapped) throws IOException {
        var w = new MsgPack.Writer();
        if (wrapped) w.map(1).string("state");
        w.map(3).string("current");
        if (inGroup) {
            summary(w.map(3).string("summary"), "Bauteam", false, 2);
            w.string("members").array(2);
            profile(w, ME, "Me", true); profile(w, FRIEND, "Friend", true);
            w.string("actions").map(0);
        } else w.nil();
        w.string("invites").map(0);
        w.string("capabilities").map(6).string("canCreate").bool(true).string("canBrowse").bool(true).string("canLeave").bool(true)
                .string("minNameLength").integer(3).string("maxNameLength").integer(16).string("remoteInviteSearch").bool(false);
        return w.bytes();
    }

    @Test void messagePackReadsSealedValuesAsTwoValues() throws IOException {
        byte[] success = new MsgPack.Writer().string("success").map(0).bytes();
        assertEquals(List.of("success", Map.of()), MsgPack.readAll(success));
        byte[] tooShort = new MsgPack.Writer().string("name_length_invalid").map(2).string("min").integer(3).string("max").integer(300).bytes();
        var values = MsgPack.readAll(tooShort);
        assertEquals(300, MsgPack.number(MsgPack.map(values.get(1)).get("max")));
        assertEquals(GROUP, MsgPack.uuid(MsgPack.readAll(new MsgPack.Writer().uuid(GROUP).bytes()).getFirst()));
        assertArrayEquals(new byte[] {(byte) 0x81, (byte) 0xA1, 'a', (byte) 0xC3}, new MsgPack.Writer().map(1).string("a").bool(true).bytes());
    }

    @Test void handshakeStateBrowseJoinAndLeave() throws IOException {
        groups.start();
        var handshake = last();
        assertEquals("handshake", handshake.type());
        assertEquals(1, MsgPack.number(MsgPack.map(handshake.payload().getFirst()).get("protocolVersion")));
        assertFalse(groups.ready());
        answer(handshake, "handshake_response", new MsgPack.Writer().map(2).string("protocolVersion").integer(1).string("minProtocolVersion").integer(1));
        assertTrue(groups.ready());
        // state and browse go out together
        var browse = last();
        assertEquals("browse", browse.type());
        var stateRequest = new Request("state_request", MsgPack.text(MsgPack.map(MsgPack.readAll(sent.get(sent.size() - 2)).getFirst()).get("requestId")), List.of());
        var w = new MsgPack.Writer().map(1).string("groups").array(2);
        summary(w, "Bauteam", false, 2); summary(w, "Zeta", true, 0);
        answer(browse, "browse_response", w);
        assertEquals(List.of("Bauteam", "Zeta"), groups.groups().stream().map(PlasmoGroups.Group::name).toList());
        groups.receive(new MsgPack.Writer().map(3).string("type").string("state").string("requestId").string(stateRequest.id()).string("payload").bin(stateBytes(false, false)).bytes());
        assertNull(groups.current());
        assertEquals(3, groups.capabilities().minName());

        groups.join(GROUP, "falsch");
        var join = last();
        assertEquals("join", join.type());
        var payload = MsgPack.map(join.payload().getFirst());
        assertEquals(GROUP, MsgPack.uuid(payload.get("groupId"))); assertEquals("falsch", payload.get("password"));
        answer(join, "join_result", new MsgPack.Writer().string("wrong_password").map(0));
        assertEquals("Falsches Passwort.", groups.alert());

        // the server pushes the new state after a successful join
        groups.receive(new MsgPack.Writer().map(3).string("type").string("state_changed").string("requestId").nil().string("payload").bin(stateBytes(true, true)).bytes());
        assertEquals("Bauteam", groups.current().name());
        assertEquals(List.of("Friend", "Me"), groups.members().stream().map(PlasmoGroups.Member::name).toList());

        groups.leave();
        assertEquals("leave", last().type());
        assertTrue(last().payload().getFirst() instanceof Map<?, ?> m && m.isEmpty(), "data object = empty map");
    }

    @Test void createSetsNameAndPasswordAfterwards() throws IOException {
        groups.start(); answer(last(), "handshake_response", new MsgPack.Writer().map(2).string("protocolVersion").integer(1).string("minProtocolVersion").integer(1));
        sent.clear();
        groups.create("Bauteam", "geheim");
        var create = last(); assertEquals("create", create.type());
        answer(create, "create_result", new MsgPack.Writer().string("success").map(0));
        var state = last(); assertEquals("state_request", state.type());
        groups.receive(new MsgPack.Writer().map(3).string("type").string("state").string("requestId").string(state.id()).string("payload").bin(stateBytes(true, false)).bytes());
        var update = last(); assertEquals("update_settings", update.type());
        var settings = MsgPack.map(update.payload().getFirst());
        assertEquals(GROUP, MsgPack.uuid(settings.get("groupId"))); assertEquals("Bauteam", settings.get("name")); assertEquals("geheim", settings.get("password"));
        assertTrue(settings.containsKey("persistent") && settings.get("persistent") == null);
        answer(update, "update_settings_result", new MsgPack.Writer().string("name_length_invalid").map(2).string("min").integer(3).string("max").integer(16));
        assertEquals("Name: 3 bis 16 Zeichen.", groups.alert());
    }

    @Test void brokenMessagesAreIgnored() {
        groups.receive(new byte[] {(byte) 0xC1});
        groups.receive(new byte[0]);
        assertFalse(groups.ready());
        assertNull(groups.current());
    }
}
