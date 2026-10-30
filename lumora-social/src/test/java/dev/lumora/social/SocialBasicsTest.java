package dev.lumora.social;

import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Member;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Profile;
import dev.lumora.social.model.Role;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialBasicsTest {
    static final String A = "0123456789abcdef0123456789abcdef";
    static final String B = "fedcba9876543210fedcba9876543210";

    @Test
    void cleanRemovesControlFormattingAndBidiCharacters() {
        assertEquals("Hallo Welt", Rules.clean("  Hallo\t\u0000 §cWelt‮ ", 100, false));
        assertEquals("a b", Rules.clean("a\nb", 100, false));
        assertEquals("a\n\nb", Rules.clean("a\n\n\n\nb", 100, true));
        assertEquals("Moin 😀", Rules.clean("Moin 😀​", 100, false));
        assertEquals("abc", Rules.clean("abcdef", 3, false));
        assertEquals("", Rules.clean(null, 3, false));
    }

    @Test
    void namesUuidsIconsAndColorsAreValidated() {
        assertTrue(Rules.validName("CuzImMax"));
        assertFalse(Rules.validName("Max Mustermann"));
        assertFalse(Rules.validName("x".repeat(17)));
        assertEquals(A, Rules.uuid("01234567-89AB-cdef-0123-456789ABCDEF"));
        assertNull(Rules.uuid("../etc/passwd"));
        assertEquals("circle", Rules.icon("<script>"));
        assertEquals(Rules.COLORS.get(0), Rules.color(0x123456));
        assertEquals(0xF5B342, Rules.color(0xF5B342));
    }

    @Test
    void endpointsRequireTlsOutsideTheLocalNetwork() {
        assertEquals(URI.create("wss://chat.example.com:443/"), Endpoint.parse("chat.example.com"));
        assertEquals(URI.create("ws://192.168.0.10:25600/"), Endpoint.parse("192.168.0.10"));
        assertEquals(URI.create("ws://localhost:4000/"), Endpoint.parse("localhost:4000"));
        assertEquals(URI.create("wss://example.org:8443/social"), Endpoint.parse("wss://example.org:8443/social"));
        assertEquals(URI.create("ws://gaming-pc:25600/"), Endpoint.parse("ws://gaming-pc"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Endpoint.parse("ws://chat.example.com"));
        assertTrue(e.getMessage().contains("wss://"));
        assertThrows(IllegalArgumentException.class, () -> Endpoint.parse("https://chat.example.com"));
        assertThrows(IllegalArgumentException.class, () -> Endpoint.parse("wss://user:pw@chat.example.com"));
        assertThrows(IllegalArgumentException.class, () -> Endpoint.parse(" "));
        assertTrue(Endpoint.local("10.0.0.1"));
        assertTrue(Endpoint.local("172.20.1.1"));
        assertFalse(Endpoint.local("172.32.1.1"));
        assertFalse(Endpoint.local("8.8.8.8"));
        assertEquals("chat.example.com", Endpoint.display(Endpoint.parse("chat.example.com")));
        assertEquals("192.168.0.10", Endpoint.display(Endpoint.parse("192.168.0.10")));
        assertEquals("192.168.0.10:4000", Endpoint.display(Endpoint.parse("192.168.0.10:4000")));
    }

    @Test
    void conversationIdsAreTheSameOnBothSides() {
        assertEquals(Conv.dm(A, B), Conv.dm(B, A));
        assertTrue(Conv.isDm(Conv.dm(A, B)));
        assertEquals(B, Conv.other(Conv.dm(A, B), A));
        assertEquals(A, Conv.other(Conv.dm(A, B), B));
        assertNull(Conv.other(Conv.dm(A, B), "11111111111111111111111111111111"));
        assertEquals("abc", Conv.groupId(Conv.group("abc")));
        assertFalse(Conv.isGroup("dm:x"));
    }

    @Test
    void modelSurvivesJsonRoundTrip() {
        Role owner = new Role(Role.OWNER, "Owner", "crown", 0xF5B342, true);
        Role fighter = new Role("k1", "Kämpfer", "sword", 0x4F7CFF, false);
        Group g = new Group("bedwars1", "BedWars Squad", "Unsere BedWars Truppe!\nStrategien und Spaß", "flag", 0x8B5CF6, A,
                List.of(owner, fighter), List.of(new Member(new Profile(A, "CuzImMax"), Role.OWNER, 1),
                new Member(new Profile(B, "Toby"), "k1", 2)), 5);
        Group back = Group.read(Js.object(g.json().toString()));
        assertEquals("BedWars Squad", back.name);
        assertEquals("Unsere BedWars Truppe!\nStrategien und Spaß", back.description);
        assertEquals("Kämpfer", back.role("k1").name);
        assertEquals("sword", back.roleOf(B).icon);
        assertTrue(back.canManage(A));
        assertFalse(back.canManage(B));
        assertEquals(Role.MEMBER, back.role("gone").id);
        ChatMessage m = ChatMessage.read(Js.object(new ChatMessage(7, g.conv(), A, "Moin Leute!", 99).json().toString()));
        assertEquals("Moin Leute!", m.text);
        Presence p = Presence.read(Js.object(new Presence(Presence.State.MULTIPLAYER, "hypixel.net", "1.8.9").json().toString()));
        assertEquals(Presence.State.MULTIPLAYER, p.state);
        assertEquals("hypixel.net", p.detail);
        assertEquals(Presence.State.OFFLINE, Presence.read(Js.object("{\"state\":\"HACKING\"}")).state);
    }
}
