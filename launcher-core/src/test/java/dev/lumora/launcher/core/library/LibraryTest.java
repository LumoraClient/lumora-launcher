package dev.lumora.launcher.core.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryTest {
    @TempDir
    Path tmp;

    // --- tiny NBT writer for the fixtures ---
    private static void tag(DataOutputStream o, int type, String name) throws IOException {
        o.writeByte(type);
        o.writeUTF(name);
    }

    private static byte[] levelDat(String name, long lastPlayed, int gameType, boolean hardcore) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(bytes))) {
            tag(o, 10, "");
            tag(o, 10, "Data");
            tag(o, 8, "LevelName");
            o.writeUTF(name);
            tag(o, 4, "LastPlayed");
            o.writeLong(lastPlayed);
            tag(o, 3, "GameType");
            o.writeInt(gameType);
            tag(o, 1, "hardcore");
            o.writeByte(hardcore ? 1 : 0);
            tag(o, 11, "DataPacksIgnoredArray");
            o.writeInt(2);
            o.writeInt(7);
            o.writeInt(8);
            o.writeByte(0); // end Data
            o.writeByte(0); // end root
        }
        return bytes.toByteArray();
    }

    private static byte[] serversDat(String... nameIp) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bytes);
        tag(o, 10, "");
        tag(o, 9, "servers");
        o.writeByte(10);
        o.writeInt(nameIp.length / 2);
        for (int i = 0; i < nameIp.length; i += 2) {
            tag(o, 8, "name");
            o.writeUTF(nameIp[i]);
            tag(o, 8, "ip");
            o.writeUTF(nameIp[i + 1]);
            o.writeByte(0);
        }
        o.writeByte(0);
        return bytes.toByteArray();
    }

    @Test
    void readsWorldsSortedByLastPlayed() throws IOException {
        Path saves = tmp.resolve("saves");
        Files.createDirectories(saves.resolve("Old"));
        Files.createDirectories(saves.resolve("New"));
        Files.createDirectories(saves.resolve("NotAWorld"));
        Files.write(saves.resolve("Old/level.dat"), levelDat("Alte Welt", 1_000_000L, 0, false));
        Files.write(saves.resolve("New/level.dat"), levelDat("Neue Welt", 2_000_000L, 1, true));
        List<InstanceLibrary.World> worlds = InstanceLibrary.worlds(tmp);
        assertEquals(List.of("Neue Welt", "Alte Welt"), worlds.stream().map(InstanceLibrary.World::name).toList());
        assertEquals(InstanceLibrary.GameMode.CREATIVE, worlds.getFirst().mode());
        assertTrue(worlds.getFirst().hardcore());
        assertEquals(Instant.ofEpochMilli(2_000_000L), worlds.getFirst().lastPlayed());
    }

    @Test
    void brokenLevelDatFallsBackToFolderName() throws IOException {
        Files.createDirectories(tmp.resolve("saves/Kaputt"));
        Files.writeString(tmp.resolve("saves/Kaputt/level.dat"), "not nbt at all");
        assertEquals("Kaputt", InstanceLibrary.worlds(tmp).getFirst().name());
    }

    @Test
    void readsServerList() throws IOException {
        Files.write(tmp.resolve("servers.dat"), serversDat("Mein Server", "play.example.net", "", "10.0.0.5:25570"));
        List<InstanceLibrary.Server> s = InstanceLibrary.servers(tmp);
        assertEquals(2, s.size());
        assertEquals("Mein Server", s.get(0).name());
        assertEquals("10.0.0.5:25570", s.get(1).name(), "empty name falls back to the address");
    }

    @Test
    void nbtRejectsHugeListLengths() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bytes);
        tag(o, 10, "");
        tag(o, 9, "evil");
        o.writeByte(1);
        o.writeInt(Integer.MAX_VALUE);
        Path f = tmp.resolve("evil.dat");
        Files.write(f, bytes.toByteArray());
        assertThrows(IOException.class, () -> Nbt.read(f));
    }

    @Test
    void listsScreenshotsAndResourcePacks() throws IOException {
        Files.createDirectories(tmp.resolve("screenshots"));
        Files.writeString(tmp.resolve("screenshots/a.png"), "x");
        Files.writeString(tmp.resolve("screenshots/notes.txt"), "x");
        Files.createDirectories(tmp.resolve("resourcepacks/Ordnerpaket"));
        Files.writeString(tmp.resolve("resourcepacks/Ordnerpaket/pack.mcmeta"), "{}");
        Files.writeString(tmp.resolve("resourcepacks/Faithful.zip"), "x");
        assertEquals(1, InstanceLibrary.screenshots(tmp).size());
        assertEquals(List.of("Faithful", "Ordnerpaket"), InstanceLibrary.resourcePacks(tmp).stream().map(InstanceLibrary.ResourcePack::name).toList());
        assertTrue(InstanceLibrary.worlds(tmp.resolve("missing")).isEmpty());
    }

    @Test
    void parsesAddresses() {
        assertEquals(new ServerPing.Address("play.example.net", -1), ServerPing.parse("play.example.net"));
        assertEquals(new ServerPing.Address("10.0.0.5", 25570), ServerPing.parse(" 10.0.0.5:25570 "));
        assertEquals(new ServerPing.Address("::1", 25566), ServerPing.parse("[::1]:25566"));
        assertEquals(new ServerPing.Address("host", -1), ServerPing.parse("host:99999"));
    }

    @Test
    void parsesStatusJson() throws IOException {
        ServerPing.Status s = ServerPing.parseStatus("""
                {"version":{"name":"Paper 1.21.8","protocol":772},"players":{"max":100,"online":7},
                 "description":{"text":"§aWillkommen ","extra":[{"text":"auf Lumora"}]}}""", 12);
        assertEquals("Willkommen auf Lumora", s.motd());
        assertEquals(7, s.online());
        assertEquals("Paper 1.21.8", s.version());
        assertThrows(IOException.class, () -> ServerPing.parseStatus("[1,2]", 1));
    }

    @Test
    void pingsALocalFakeServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket c = server.accept()) {
                    DataInputStream in = new DataInputStream(c.getInputStream());
                    int hsLen = ServerPing.readVarInt(in);
                    byte[] hs = in.readNBytes(hsLen);
                    assertEquals(0x00, hs[0]);
                    int reqLen = ServerPing.readVarInt(in);
                    in.readNBytes(reqLen);
                    byte[] json = "{\"version\":{\"name\":\"1.21.11\"},\"players\":{\"max\":20,\"online\":3},\"description\":\"Test\"}"
                            .getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    DataOutputStream b = new DataOutputStream(body);
                    ServerPing.writeVarInt(b, 0x00);
                    ServerPing.writeVarInt(b, json.length);
                    b.write(json);
                    OutputStream out = c.getOutputStream();
                    DataOutputStream o = new DataOutputStream(out);
                    ServerPing.writeVarInt(o, body.size());
                    o.write(body.toByteArray());
                    o.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
            ServerPing.Status s = ServerPing.ping("127.0.0.1:" + server.getLocalPort());
            t.join(5000);
            assertNotNull(s);
            assertEquals("Test", s.motd());
            assertEquals(3, s.online());
            assertEquals(20, s.max());
        }
    }
}
