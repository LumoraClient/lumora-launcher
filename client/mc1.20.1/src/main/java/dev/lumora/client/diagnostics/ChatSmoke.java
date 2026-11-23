package dev.lumora.client.diagnostics;

import dev.lumora.core.extras.ChatModule;
import dev.lumora.core.extras.GameExtras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Smoke test of the chat module: a few messages (the newest from "Steve" with a link), the open chat, the message menu
 * by right-click, then a smaller chat without background and text shadow.
 */
final class ChatSmoke {
    private ChatSmoke() {
    }

    static void fill(Minecraft mc) {
        ChatComponent chat = mc.gui.getChat();
        for (int i = 1; i <= 6; i++) {
            chat.addMessage(Component.literal("Lumora Chat-Test, Zeile " + i));
        }
        chat.addMessage(Component.literal("Eine lange Nachricht, damit sie umbricht: " + "abc ".repeat(30)));
        String own = mc.player == null ? "Steve" : mc.player.getGameProfile().getName();
        windows(own);
        chat.addMessage(Component.literal("[Lumora] Server-Ansage: Neustart in 5 Minuten"));
        chat.addMessage(Component.literal("[Lumora] Abstimmung: /vote fuer Belohnungen"));
        chat.addMessage(Component.literal("Spieler " + own + ": Hallo zusammen!"));
        chat.addMessage(Component.literal("<").append(Component.literal("Steve").withStyle(s -> s.withInsertion("Steve")))
                .append(Component.literal("> Schau mal: https://lumora.dev/chat")));
        mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
    }

    /** Checks the chat window and the newest message, then right-clicks it (opens the menu). */
    static void check(Minecraft mc, List<String> problems) {
        GameExtras x = ApiSmoke.core().platform().extras();
        GameExtras.ChatView v = x.chatView();
        // newest lines first; a server message may arrive after the test messages
        GameExtras.ChatMessage m = null;
        GameExtras.ChatMessage wrapped = null;
        int line = -1;
        for (int i = 0; v != null && i < Math.min(v.lines(), 20); i++) {
            GameExtras.ChatMessage c = x.chatMessage(i);
            if (c != null && m == null && c.text().contains("lumora.dev")) {
                m = c;
                line = i;
            }
            if (c != null && wrapped == null && c.text().contains("Eine lange")) {
                wrapped = c;
            }
        }
        dev.lumora.client.LumoraClient.LOG.info("LUMORA_CHAT view={} newest={} wrapped={}", v, m, wrapped);
        if (v == null || m == null) {
            problems.add("chat: no view or message (" + v + ", " + m + ")");
            return;
        }
        if (!m.text().contains("lumora.dev") || !m.insertions().contains("Steve")) {
            problems.add("chat: newest message wrong: " + m);
        }
        if (wrapped == null || wrapped.lastLine() <= wrapped.firstLine()) {
            problems.add("chat: the long message is not one message over several lines: " + wrapped);
        }
        int y = (int) (v.bottom() - (line - v.scroll() + 0.5) * v.rowHeight());
        if (!ChatModule.chatClick(v.left() + 20, y, 1)) {
            problems.add("chat: right-click did not open the menu");
        }
    }

    /** Lumora chat windows: a player tab in the main chat and a window of its own for the "[Lumora]" messages. */
    private static void windows(String own) {
        dev.lumora.core.chat.windows.ChatWindows w = ChatModule.windows();
        if (w == null) {
            return;
        }
        dev.lumora.core.chat.windows.ChatLayout l = w.layout();
        l.reset();
        l.add(new dev.lumora.core.chat.windows.ChatTab(l.newTabId(), own,
                new dev.lumora.core.chat.windows.ChatRule(dev.lumora.core.chat.windows.ChatRule.Kind.PLAYER, own)), l.main(), 0, 0, 0, 0);
        dev.lumora.core.chat.windows.ChatTab server = new dev.lumora.core.chat.windows.ChatTab(l.newTabId(), "Lumora",
                new dev.lumora.core.chat.windows.ChatRule(dev.lumora.core.chat.windows.ChatRule.Kind.PREFIX, "[Lumora]"));
        server.keepInMain = false;
        server.highlight = true;
        l.add(server, null, 340, 40, 200, 70);
        l.main().select(l.mainTab());
    }

    /** The chat windows draw the chat: two windows, the "[Lumora]" messages only in their own window. */
    static void windowsCheck(List<String> problems) {
        dev.lumora.core.chat.windows.ChatWindows w = ChatModule.windows();
        boolean draws = ChatModule.drawsChat();
        int windows = w == null ? 0 : w.layout().windows.size();
        long inMain = w == null ? 0 : w.store().all().stream().filter(e -> e.clean().startsWith("[Lumora]"))
                .filter(e -> w.layout().inMain(e, "")).count();
        long heads = w == null ? 0 : w.store().all().stream().filter(e -> e.head() != null).count();
        dev.lumora.client.LumoraClient.LOG.info("LUMORA_CHAT_WINDOWS draws={} windows={} messages={} lumoraInMain={} heads={}", draws, windows,
                w == null ? 0 : w.store().size(), inMain, heads);
        if (!draws || windows != 2) {
            problems.add("chat windows: not drawn by Lumora or the window is missing (" + draws + ", " + windows + ")");
        } else if (inMain != 0) {
            problems.add("chat windows: [Lumora] messages still in the main chat");
        } else if (heads == 0) {
            problems.add("chat windows: no head for the own message");
        }
    }

    static void look() {
        setting("custom_size", "true");
        setting("width", "220");
        setting("height_open", "100");
        setting("background", "false");
        setting("text_shadow", "false");
    }

    static void reset(Minecraft mc) {
        dev.lumora.core.chat.windows.ChatWindows cw = ChatModule.windows();
        if (cw != null) {
            cw.layout().reset();
            cw.layout().save();
        }
        setting("custom_size", "false");
        setting("background", "true");
        setting("text_shadow", "true");
        mc.setScreen(null);
    }

    private static void setting(String key, String value) {
        ApiSmoke.core().addonManager().record("lumora-chat").filter(r -> r.configuration() != null)
                .flatMap(r -> r.configuration().settings().get(key)).ifPresent(s -> s.decode(value));
    }
}
