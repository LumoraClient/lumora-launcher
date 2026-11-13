package dev.lumora.launcher.core.integrations;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.config.LauncherConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordPresenceTest {
    /**
     * Fake local Discord app: records frames and answers like the real one: the handshake with the READY dispatch, a
     * command with its result and {@code "evt": null}.
     */
    static class FakeDiscord implements DiscordPresence.Transport {
        final List<int[]> ops = new ArrayList<>();
        final List<JsonObject> frames = new ArrayList<>();
        private final Deque<Byte> answer = new ArrayDeque<>();
        boolean closed;

        @Override
        public void write(byte[] data) {
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int op = b.getInt();
            int len = b.getInt();
            assertEquals(data.length - 8, len);
            ops.add(new int[]{op});
            JsonObject frame = JsonParser.parseString(new String(data, 8, len, StandardCharsets.UTF_8)).getAsJsonObject();
            frames.add(frame);
            for (byte x : reply(op, frame)) {
                answer.add(x);
            }
        }

        byte[] reply(int op, JsonObject frame) {
            if (op == DiscordPresence.OP_HANDSHAKE) {
                return DiscordPresence.encode(1, "{\"cmd\":\"DISPATCH\",\"data\":{\"v\":1},\"evt\":\"READY\",\"nonce\":null}");
            }
            return DiscordPresence.encode(1, "{\"cmd\":\"" + frame.get("cmd").getAsString() + "\",\"data\":null,\"evt\":null,\"nonce\":\""
                    + frame.get("nonce").getAsString() + "\"}");
        }

        @Override
        public byte[] read(int n) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < n; i++) {
                out.write(answer.removeFirst());
            }
            return out.toByteArray();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void handshakeThenActivity() throws Exception {
        FakeDiscord fake = new FakeDiscord();
        DiscordPresence p = new DiscordPresence(() -> fake);
        LauncherConfig c = new LauncherConfig();
        c.discordRichPresence = true;
        c.discordAppId = "123456789012345678";
        p.update(c, new DiscordPresence.Activity("Minecraft 26.1.2", "Im Launcher", 1_800_000_000L));
        p.awaitIdle();
        assertEquals(2, fake.frames.size());
        assertEquals(0, fake.ops.get(0)[0], "handshake first");
        assertEquals("123456789012345678", fake.frames.get(0).get("client_id").getAsString());
        JsonObject cmd = fake.frames.get(1);
        assertEquals("SET_ACTIVITY", cmd.get("cmd").getAsString());
        JsonObject act = cmd.getAsJsonObject("args").getAsJsonObject("activity");
        assertEquals("Minecraft 26.1.2", act.get("details").getAsString());
        assertEquals(1_800_000_000L, act.getAsJsonObject("timestamps").get("start").getAsLong());
        assertFalse(fake.closed, "the answer with \"evt\": null keeps the connection");

        // the next update reuses the connection (no second handshake)
        p.update(c, new DiscordPresence.Activity("Spielt Minecraft 26.3", "Lumora", 1_800_000_100L));
        p.awaitIdle();
        assertEquals(3, fake.frames.size());
        assertEquals(1, fake.ops.get(2)[0]);
        assertFalse(fake.closed);

        // switched off: presence cleared, connection closed
        c.discordRichPresence = false;
        p.update(c, null);
        p.awaitIdle();
        assertFalse(fake.frames.get(3).getAsJsonObject("args").has("activity"));
        assertTrue(fake.closed);
        p.close();
    }

    @Test
    void rejectedApplicationIdClosesTheConnection() throws Exception {
        FakeDiscord fake = new FakeDiscord() {
            @Override
            byte[] reply(int op, JsonObject frame) {
                return DiscordPresence.encode(DiscordPresence.OP_CLOSE, "{\"code\":4000,\"message\":\"Invalid Client ID\"}");
            }
        };
        DiscordPresence p = new DiscordPresence(() -> fake);
        LauncherConfig c = new LauncherConfig();
        c.discordRichPresence = true;
        c.discordAppId = "123456789012345678";
        p.update(c, new DiscordPresence.Activity("x", "y", 0));
        p.awaitIdle();
        assertEquals(1, fake.frames.size(), "no activity after the refused handshake");
        assertTrue(fake.closed);
        p.close();
    }

    @Test
    void fieldsAreReadNullSafe() {
        JsonObject o = JsonParser.parseString("{\"evt\":null,\"data\":null,\"n\":3,\"s\":\"ERROR\"}").getAsJsonObject();
        assertEquals(null, DiscordPresence.text(o, "evt"));
        assertEquals(null, DiscordPresence.text(o, "missing"));
        assertEquals(null, DiscordPresence.text(o, "n"));
        assertEquals("ERROR", DiscordPresence.text(o, "s"));
    }

    @Test
    void nothingWithoutAppIdOrDiscord() throws Exception {
        FakeDiscord fake = new FakeDiscord();
        DiscordPresence p = new DiscordPresence(() -> fake);
        LauncherConfig c = new LauncherConfig();
        c.discordRichPresence = true; // but no application ID
        p.update(c, new DiscordPresence.Activity("x", "y", 0));
        p.awaitIdle();
        assertTrue(fake.frames.isEmpty());
        DiscordPresence none = new DiscordPresence(() -> null); // Discord not running
        c.discordAppId = "123456789012345678";
        none.update(c, new DiscordPresence.Activity("x", "y", 0));
        none.awaitIdle();
        none.close();
        p.close();
    }

    @Test
    void frameLayout() {
        byte[] f = DiscordPresence.encode(1, "{}");
        ByteBuffer b = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, b.getInt());
        assertEquals(2, b.getInt());
        assertEquals('{', b.get());
    }
}
