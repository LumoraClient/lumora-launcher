package dev.lumora.voice.protocol;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/** Presence is independent of received audio, so a silent participant keeps an idle icon. */
public final class VoicePresence {
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    public boolean contains(UUID id) { return active.contains(id); }
    public boolean receive(int packet, DataInputStream input) throws IOException {
        if (packet != 10 && packet != 11) return false;
        int count = packet == 10 ? input.readUnsignedShort() : input.readUnsignedByte();
        if (count > 512 || input.available() < count * 16) throw new IOException("Invalid voice presence length");
        List<UUID> update = new ArrayList<>(count);
        for (int i = 0; i < count; i++) update.add(VoiceProtocol.uuid(input));
        if (packet == 10) {
            // Protocol 6 always appends a u16 metadata count, including when it is zero.
            // Metadata belongs to presence, not to the short-lived speaking indicator.
            int metadataCount = input.readUnsignedShort();
            if (metadataCount > 512) throw new IOException("Too much voice presence metadata");
            for (int i = 0; i < metadataCount; i++) {
                VoiceProtocol.uuid(input);
                int length = input.readInt();
                if (length < 2 || length > VoiceProtocol.MAX_FRAME || length > input.available()) throw new IOException("Invalid presence metadata length");
                String json = new String(input.readNBytes(length), StandardCharsets.UTF_8);
                try { if (!new JsonParser().parse(json).isJsonObject()) throw new IOException("Expected presence metadata object"); }
                catch (RuntimeException malformed) { throw new IOException("Invalid presence metadata", malformed); }
            }
        }
        if (input.available() != 0) throw new IOException("Trailing voice presence data");
        if (packet == 10) { for (UUID id : update) if (active.size() < 512) active.add(id); }
        else active.removeAll(update);
        return true;
    }
    public void clear() { active.clear(); }
}
