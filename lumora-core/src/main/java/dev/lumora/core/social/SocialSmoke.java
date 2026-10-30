package dev.lumora.core.social;

import dev.lumora.social.SocialClient;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumora Social part of the in-game smoke test (only when the launcher passed a server, e.g. the --dev-auth test
 * server of .t/smoke-all.sh): send a friend request to {@code -Dlumora.smokeTest.socialFriend} (default CuzImMax; the
 * test bots accept and add new friends to their group), open the group chat, send a message and check that the
 * server confirmed it.
 */
public final class SocialSmoke {
    private static volatile String text;
    private static volatile String conv;

    private SocialSmoke() {
    }

    static boolean configured() {
        String s = System.getProperty("lumora.social.server");
        return s != null && !s.isBlank();
    }

    /** Before the first social screenshot. */
    public static void begin(LumoraSocial social) {
        SocialClient c = social == null ? null : social.client();
        if (c == null) {
            return;
        }
        String friend = System.getProperty("lumora.smokeTest.socialFriend", "CuzImMax");
        if (c.friends().stream().noneMatch(f -> f.profile.name.equalsIgnoreCase(friend))) {
            c.addFriend(friend);
        }
    }

    /** Opens the first group in the view and sends the test message. */
    public static void send(LumoraSocial social, SocialView view, String minecraftVersion) {
        SocialClient c = social == null ? null : social.client();
        if (c == null || c.groups().isEmpty()) {
            return;
        }
        Group g = c.groups().get(0);
        conv = g.conv();
        view.select(conv);
        text = "Hallo aus Minecraft " + minecraftVersion + " (Lumora-Smoke-Test " + (System.currentTimeMillis() % 100000) + ")";
        c.send(conv, text);
    }

    /** "PASSED: …" or "FAILED: …" ("not run" without a configured server). */
    public static String result(LumoraSocial social) {
        if (!configured()) {
            return "not run (no Lumora Social server)";
        }
        SocialClient c = social == null ? null : social.client();
        if (c == null) {
            return "FAILED: " + (social == null ? "not started" : social.disabledReason());
        }
        if (c.status() != SocialClient.Status.ONLINE) {
            return "FAILED: " + c.status() + " " + c.statusText();
        }
        if (c.friends().isEmpty()) {
            return "FAILED: online as " + c.self().name + ", but no friend accepted the request";
        }
        if (c.groups().isEmpty()) {
            return "FAILED: friends ok, but not in any group";
        }
        if (text == null || conv == null) {
            return "FAILED: test message not sent";
        }
        boolean confirmed = false;
        int replies = 0;
        for (ChatMessage m : c.messages(conv)) {
            if (m.sender.equals(c.self().uuid) && m.text.equals(text)) {
                confirmed = true;
            } else if (confirmed) {
                replies++;
            }
        }
        if (!confirmed || !c.pending(conv).isEmpty()) {
            return "FAILED: message not confirmed by the server";
        }
        return "PASSED: online as " + c.self().name + ", " + c.friends().size() + " friend(s), group \""
                + c.group(dev.lumora.social.Conv.groupId(conv)).name + "\", message delivered" + (replies > 0 ? ", " + replies + " reply(s)" : "");
    }

    /** Problems for the smoke verdict (empty when no server is configured or everything passed). */
    public static List<String> problems(LumoraSocial social) {
        List<String> p = new ArrayList<>();
        String r = result(social);
        if (r.startsWith("FAILED")) {
            p.add("social " + r);
        }
        return p;
    }
}
