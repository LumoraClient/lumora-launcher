package dev.lumora.client.render;

import dev.lumora.core.chat.ChatApi;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Lumora API 1.9 inline chat icons: every {@code :name:} with an icon in a new message becomes the glyph of the
 * addon's font (white, so a colored texture keeps its colors), keeping clicks, hover texts and insertions of the text
 * around it. Messages without such a name stay the same object.
 */
public final class ChatEmojis {
    private ChatEmojis() {
    }

    public static Component decorate(Component message) {
        if (message == null || !ChatApi.hasIcons() || message.getString().indexOf(':') < 0) {
            return message;
        }
        MutableComponent out = Component.empty();
        boolean[] changed = {false};
        message.visit((style, text) -> {
            List<ChatApi.Segment> parts = ChatApi.split(text);
            if (parts == null) {
                out.append(Component.literal(text).setStyle(style));
                return Optional.empty();
            }
            changed[0] = true;
            for (ChatApi.Segment s : parts) {
                if (s.isIcon()) {
                    out.append(Component.literal(s.icon().glyph()).setStyle(iconStyle(style, s.icon().font())));
                } else {
                    out.append(Component.literal(s.text()).setStyle(style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : message;
    }

    private static Style iconStyle(Style around, String font) {
        try {
            return around.withFont(ResourceLocation.parse(font)).withColor(TextColor.fromRgb(0xFFFFFF)).withBold(false).withItalic(false);
        } catch (RuntimeException invalid) {
            return around;
        }
    }
}
