package dev.lumora.example.timer;

import dev.lumora.api.chat.Chat;
import dev.lumora.api.chat.ChatPanel;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.ui.Theme;

/**
 * Lumora API 1.9 example: the window of the session timer's button at the chat input. Shows the session time; a
 * click on the button inside writes it into the chat input (not sent) and closes the window.
 */
final class SessionChatPanel implements ChatPanel {
    private static final int W = 130;
    private static final int H = 38;

    private final Chat chat;
    private final Translations translations;
    private final Theme theme;
    private final SessionTimerWidget widget;

    SessionChatPanel(Chat chat, Translations translations, Theme theme, SessionTimerWidget widget) {
        this.chat = chat;
        this.translations = translations;
        this.theme = theme;
        this.widget = widget;
    }

    @Override
    public int width() {
        return W;
    }

    @Override
    public int height() {
        return H;
    }

    private boolean overButton(int mouseX, int mouseY) {
        return mouseX >= 4 && mouseX < W - 4 && mouseY >= H - 17 && mouseY < H - 3;
    }

    @Override
    public void render(HudGraphics g, int mouseX, int mouseY) {
        g.text(translations.translate("session-timer.chat.time", widget.text()), 4, 4, 0xFFFFFFFF, true);
        int accent = theme.accent();
        g.roundedRect(4, H - 17, W - 8, 14, 3, overButton(mouseX, mouseY) ? accent : 0xFF2A3340);
        g.centeredText(translations.translate("session-timer.chat.insert"), W / 2, H - 14, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !overButton(mouseX, mouseY)) {
            return false;
        }
        chat.insertText(translations.translate("session-timer.chat.message", widget.text()));
        chat.closePanel();
        return true;
    }
}
