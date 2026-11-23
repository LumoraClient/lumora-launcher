package dev.lumora.core.chat.windows;

/**
 * One received chat message as the Lumora chat windows keep it.
 *
 * @param id        increasing number
 * @param component Minecraft's message as Minecraft draws it (with Lumora's timestamp), opaque for the core
 * @param clean     plain text without Lumora's timestamp and "(x3)"
 * @param sender    who wrote it, null for messages without a player
 * @param prefix    the first word or bracket of the message ("[Baublase]", "Baublase |"), null if there is none
 * @param rank      the word right before the sender ("Spieler", "[VIP]"), null if there is none
 * @param head      the sender's skin for the head in front of the message (opaque), null without one
 * @param time      when it came in (milliseconds)
 */
public record ChatEntry(long id, Object component, String clean, String sender, String prefix, String rank, Object head,
                        long time) {
}
