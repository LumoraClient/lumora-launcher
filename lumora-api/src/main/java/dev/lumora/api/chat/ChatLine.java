package dev.lumora.api.chat;

import java.util.List;

/**
 * A chat message as Lumora sees it: the plain text (without colours and without Lumora's timestamp), the sender if
 * Lumora could tell it (vanilla {@code <Name>}, ranks, clickable names), and the links in it.
 *
 * @param text   plain text
 * @param sender name of the player who wrote it, or null (server messages)
 * @param links  web links in the message (also those only behind a click)
 * @since 1.5.0
 */
public record ChatLine(String text, String sender, List<String> links) {
    public ChatLine {
        text = text == null ? "" : text;
        links = links == null ? List.of() : List.copyOf(links);
    }
}
