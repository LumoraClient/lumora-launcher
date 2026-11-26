package dev.lumora.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BlockListTest {
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test void blockByUuidAndOptionallyByName() {
        var config = new VoiceConfig();
        assertFalse(config.isBlocked(STEVE, "Steve"));
        config.block(STEVE, "Steve");
        assertTrue(config.isBlocked(STEVE)); assertTrue(config.isBlocked(UUID.randomUUID(), "steve"));
        config.blockMatchNames = false;
        assertFalse(config.isBlocked(UUID.randomUUID(), "Steve")); assertTrue(config.isBlocked(STEVE, "Anders"));
        config.block(STEVE, " "); assertEquals("Steve", config.blocked.get(STEVE.toString()).name);
        assertTrue(config.rename(STEVE, "Steve2")); assertFalse(config.rename(STEVE, "Steve2")); assertFalse(config.rename(UUID.randomUUID(), "X"));
        assertTrue(config.unblock(STEVE)); assertFalse(config.unblock(STEVE)); assertFalse(config.isBlocked(STEVE));
    }
    @Test void blockListSurvivesSaveSanitizeAndReset(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("labyvoice.json");
        var config = VoiceConfig.load(file); config.block(STEVE, "Steve"); config.blockPauseRange = 500; config.surroundRange = 30; config.save();
        String json = Files.readString(file).replace("\"blocked\": {", "\"blocked\": {\n    \"keine-uuid\": {\"name\": \"x\"},");
        Files.writeString(file, json);
        var loaded = VoiceConfig.load(file);
        assertEquals(1, loaded.blocked.size()); assertEquals("Steve", loaded.blocked.get(STEVE.toString()).name);
        assertTrue(loaded.blocked.get(STEVE.toString()).since > 0); assertEquals(128, loaded.blockPauseRange);
        loaded.reset();
        assertEquals(18, loaded.surroundRange); assertTrue(loaded.isBlocked(STEVE), "Zurücksetzen behält die Blockliste");
    }
    @Test void lookupParsesUuidsNamesAndMojangAnswers() {
        assertEquals(STEVE, BlockLookup.parseUuid("8667ba71b85a4004af54457a9734eed7"));
        assertEquals(STEVE, BlockLookup.parseUuid(" 8667ba71-b85a-4004-af54-457a9734eed7 "));
        assertNull(BlockLookup.parseUuid("Steve")); assertNull(BlockLookup.parseUuid("8667ba71-b85a-4004-af54-457a9734eedX"));
        assertTrue(BlockLookup.validName("Notch_123")); assertFalse(BlockLookup.validName("../evil")); assertFalse(BlockLookup.validName("abcdefghijklmnopq"));
        var profile = BlockLookup.parseProfile("{\"id\":\"8667ba71b85a4004af54457a9734eed7\",\"name\":\"Steve\"}");
        assertEquals(STEVE, profile.id()); assertEquals("Steve", profile.name());
        assertThrows(RuntimeException.class, () -> BlockLookup.parseProfile("{\"id\":\"x\",\"name\":\"Steve\"}"));
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(java.nio.charset.StandardCharsets.UTF_8)), BlockLookup.offlineUuid("Steve"));
    }
}
