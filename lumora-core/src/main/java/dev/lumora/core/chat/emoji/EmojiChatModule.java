package dev.lumora.core.chat.emoji;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.chat.ChatIcons;
import dev.lumora.api.chat.ChatInputButton;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.config.TextSetting;
import dev.lumora.api.i18n.Translations;
import dev.lumora.core.chat.ChatApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in module "Emoji Chat": {@code :name:} in chat messages becomes the emoji (Twemoji, from Minecraft 1.16.5 on;
 * 1.8.9 and 1.12.2 have no custom fonts and keep the text), an emoji button at the chat input opens a picker, and
 * typing {@code :jo} suggests matching emojis. Written on the Lumora API 1.9 like any addon ({@code chat().addIcons},
 * {@code addInputButton}, {@code insertText}); only the suggestions are a core layer. Idea after the LabyMod addon
 * "EmojiChat" and LabyMod's chat input buttons; own code, see THIRD_PARTY_NOTICES.
 */
public final class EmojiChatModule implements LumoraAddon {
    public static final String ID = "lumora-emojichat";
    static final int MAX_RECENT = 27;
    static final String K = "lumora.emojichat.";

    private EmojiRegistry registry;
    private Translations translations;
    BooleanSetting inline;
    BooleanSetting button;
    BooleanSetting suggestions;
    IntegerSetting columns;
    TextSetting recent;
    private Runnable removeLayer;

    public EmojiChatModule() {
    }

    /** With a ready registry (tests). */
    EmojiChatModule(EmojiRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onInitialize(AddonContext context) {
        translations = context.translations();
        SettingsGroup s = context.configuration().settings();
        inline = s.toggle("inline", true).name(K + "setting.inline").description(K + "setting.inline.description")
                .section(K + "section.general");
        button = s.toggle("button", true).name(K + "setting.button").description(K + "setting.button.description");
        suggestions = s.toggle("suggestions", true).name(K + "setting.suggestions")
                .description(K + "setting.suggestions.description");
        columns = s.slider("columns", 9, 6, 12).name(K + "setting.columns").section(K + "section.picker").requires(button);
        // recently used emojis, newest first (not shown)
        recent = s.text("recent", "", 600).name(K + "setting.recent");
        recent.visibleWhen(() -> false);
    }

    @Override
    public void onEnable(AddonContext context) {
        if (registry == null) {
            try {
                registry = EmojiRegistry.load();
            } catch (java.io.IOException | RuntimeException e) {
                context.logger().warn("Emoji Chat: emojis could not be loaded", e);
                return;
            }
        }
        EmojiRegistry r = registry;
        context.chat().addIcons(name -> {
            if (!inline.get()) {
                return null;
            }
            EmojiRegistry.Emoji e = r.byName(name);
            return e == null ? null : new ChatIcons.Icon(EmojiRegistry.FONT, e.glyph());
        });
        EmojiRegistry.Emoji smiley = r.byName("smiley");
        ChatInputButton.Builder b = ChatInputButton.builder("emojis", tr("button"));
        if (smiley != null) {
            b.icon(r.sheet(smiley), r.u(smiley), r.v(smiley), r.cell(), r.cell(), r.sheetSize(), r.sheetSize());
        } else {
            b.label(":)");
        }
        context.chat().addInputButton(b
                .panel(() -> new EmojiPanel(r, this::recentEmojis, e -> {
                    context.chat().insertText(e.shortcode());
                    remember(e);
                }, this::tr, columns.get()))
                .visibleWhen(button::get)
                .build());
        ChatApi api = ChatApi.current();
        if (api != null) {
            removeLayer = api.addLayer(new EmojiSuggestions(r, api::input, api::replaceBeforeCursor, this::remember,
                    suggestions::get));
        }
    }

    @Override
    public void onDisable(AddonContext context) {
        if (removeLayer != null) {
            removeLayer.run();
            removeLayer = null;
        }
    }

    String tr(String key) {
        return translations == null ? key : translations.translateOr(K + key, key);
    }

    /** Recently used emojis, newest first. */
    List<EmojiRegistry.Emoji> recentEmojis() {
        List<EmojiRegistry.Emoji> out = new ArrayList<>();
        if (registry == null || recent == null) {
            return out;
        }
        for (String name : recent.get().split(",")) {
            EmojiRegistry.Emoji e = registry.byName(name.trim());
            if (e != null && !out.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Puts {@code e} first in the recently used ones (at most {@link #MAX_RECENT}). */
    void remember(EmojiRegistry.Emoji e) {
        if (e == null || recent == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        names.add(e.name());
        for (EmojiRegistry.Emoji old : recentEmojis()) {
            if (!old.equals(e) && names.size() < MAX_RECENT) {
                names.add(old.name());
            }
        }
        recent.set(String.join(",", names));
    }

    EmojiRegistry registry() {
        return registry;
    }
}
