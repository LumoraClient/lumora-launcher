package dev.lumora.social;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Limits and input rules shared by client and server. The server enforces them; the client applies them before
 * sending so the user sees the limit while typing.
 */
public final class Rules {
    /** Protocol version; a server rejects clients with another major version. */
    public static final int PROTOCOL = 1;

    public static final int MESSAGE_MAX = 500;
    public static final int GROUP_NAME_MAX = 32;
    public static final int GROUP_DESCRIPTION_MAX = 300;
    public static final int ROLE_NAME_MAX = 20;
    public static final int ROLES_MAX = 12;
    public static final int GROUP_MEMBERS_MAX = 100;
    public static final int GROUPS_PER_USER_MAX = 30;
    public static final int FRIENDS_MAX = 300;
    /** Members of a party, the leader and open invitations included. */
    public static final int PARTY_MAX = 8;
    public static final int PRESENCE_DETAIL_MAX = 64;
    public static final int HISTORY_PAGE = 50;
    /** Messages kept per conversation on the server. */
    public static final int HISTORY_KEPT = 1000;

    /** Icons a group or role may use; clients draw them, unknown keys fall back to "circle". */
    public static final List<String> ICONS = List.of("circle", "crown", "shield", "sword", "star", "bow", "pickaxe",
            "heart", "flame", "bolt", "gem", "flag");
    /** Colors a group or role may use (RGB), same palette in launcher and game. */
    public static final List<Integer> COLORS = List.of(0x18B4CC, 0x4F7CFF, 0x8B5CF6, 0xEC4899, 0xF2555A, 0xF5B342,
            0x3DD68C, 0x22C3A6, 0x98A1AD, 0xE8EAED);

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{32}");

    private Rules() {
    }

    /**
     * Normalises user text: control and format characters (including bidi overrides and zero-width characters) are
     * removed, Minecraft formatting codes (§) are dropped, whitespace runs collapse (newlines become spaces unless
     * {@code multiline}), and the result is cut to {@code max} code points.
     */
    public static String clean(String s, int max, boolean multiline) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(Math.min(s.length(), max * 2));
        int count = 0;
        boolean space = false;
        for (int i = 0; i < s.length() && count < max; ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                if (i < s.length()) {
                    i += Character.charCount(s.codePointAt(i)); // the formatting code after it
                }
                continue;
            }
            if (cp == '\n' && multiline) {
                // at most one empty line in a row
                int len = b.length();
                if (len >= 2 && b.charAt(len - 1) == '\n' && b.charAt(len - 2) == '\n') {
                    continue;
                }
                while (b.length() > 0 && b.charAt(b.length() - 1) == ' ') {
                    b.setLength(b.length() - 1);
                }
                b.append('\n');
                count++;
                space = false;
                continue;
            }
            int type = Character.getType(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = true;
                continue;
            }
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.UNASSIGNED
                    || type == Character.PRIVATE_USE || type == Character.SURROGATE) {
                continue;
            }
            if (space && b.length() > 0 && b.charAt(b.length() - 1) != '\n') {
                b.append(' ');
                count++;
                if (count >= max) {
                    break;
                }
            }
            space = false;
            b.appendCodePoint(cp);
            count++;
        }
        return b.toString().strip();
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Profile UUIDs travel without dashes, lower case. */
    public static String uuid(String s) {
        if (s == null) {
            return null;
        }
        String u = s.replace("-", "").toLowerCase(Locale.ROOT);
        return UUID.matcher(u).matches() ? u : null;
    }

    public static String icon(String key) {
        return ICONS.contains(key) ? key : "circle";
    }

    private static final int[] NAME_COLORS = {0xA78BFA, 0xF472B6, 0x4ADE80, 0xFBBF24, 0x38BDF8, 0x818CF8, 0xFB7185, 0x2DD4BF};

    /** Stable per-player name color (RGB), the same in launcher and game. */
    public static int nameColor(String uuid) {
        return NAME_COLORS[Math.floorMod(uuid == null ? 0 : uuid.hashCode(), NAME_COLORS.length)];
    }

    public static int color(long rgb) {
        int c = (int) (rgb & 0xFFFFFF);
        return COLORS.contains(c) ? c : COLORS.get(0);
    }
}
