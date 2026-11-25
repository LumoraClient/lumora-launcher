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
    /** A shared status line (e.g. the song an addon shows under the name), in code points. */
    public static final int STATUS_MAX = 96;
    /** Players asked for in one status lookup. */
    public static final int STATUS_LOOKUP_MAX = 100;
    /** Cosmetics a player shows at once, players one client watches, players one emote reaches. */
    public static final int COSMETICS_MAX = 16;
    public static final int COSMETICS_WATCH_MAX = 128;
    public static final int EMOTE_TARGETS_MAX = 64;
    /** Largest cosmetic offset in pixels per axis. */
    public static final double COSMETIC_OFFSET_MAX = 8;
    /** Who sees a shared status: every Lumora player, only friends, nobody (cleared). */
    public static final List<String> STATUS_SCOPES = List.of("all", "friends", "none");
    private static final Pattern STATUS_KEY = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern COSMETIC_ID = Pattern.compile("[a-z0-9_./-]{1,64}");
    private static final Pattern VARIANT_ID = Pattern.compile("[a-z0-9_-]{1,32}");
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

    /** The key of a shared status (an addon id), or null if it is not one. */
    public static String statusKey(String s) {
        return s != null && STATUS_KEY.matcher(s).matches() ? s : null;
    }

    /** A cosmetic or emote id of the built-in set ({@code [a-z0-9_./-]}, 1 to 64 characters), or null. */
    public static String cosmeticId(String s) {
        return s != null && COSMETIC_ID.matcher(s).matches() ? s : null;
    }

    /**
     * A cleaned loadout: at most {@link #COSMETICS_MAX} entries {@code {"id", "variant"?, "offset"?: [x, y, z]}} with
     * valid ids, valid variants and offsets clamped to {@link #COSMETIC_OFFSET_MAX} in half pixels; anything else is
     * dropped. Never null.
     */
    public static com.google.gson.JsonArray cosmetics(com.google.gson.JsonElement in) {
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        if (in == null || !in.isJsonArray()) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (com.google.gson.JsonElement e : in.getAsJsonArray()) {
            if (out.size() >= COSMETICS_MAX) {
                break;
            }
            if (!e.isJsonObject()) {
                continue;
            }
            com.google.gson.JsonObject o = e.getAsJsonObject();
            String id = o.has("id") && o.get("id").isJsonPrimitive() ? cosmeticId(o.get("id").getAsString()) : null;
            if (id == null || !seen.add(id)) {
                continue;
            }
            com.google.gson.JsonObject c = new com.google.gson.JsonObject();
            c.addProperty("id", id);
            if (o.has("variant") && o.get("variant").isJsonPrimitive() && VARIANT_ID.matcher(o.get("variant").getAsString()).matches()) {
                c.addProperty("variant", o.get("variant").getAsString());
            }
            if (o.has("offset") && o.get("offset").isJsonArray() && o.getAsJsonArray("offset").size() == 3) {
                com.google.gson.JsonArray off = new com.google.gson.JsonArray();
                boolean any = false;
                for (com.google.gson.JsonElement v : o.getAsJsonArray("offset")) {
                    double d = v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsDouble() : 0;
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        d = 0;
                    }
                    d = Math.round(Math.max(-COSMETIC_OFFSET_MAX, Math.min(COSMETIC_OFFSET_MAX, d)) * 2) / 2.0;
                    any |= d != 0;
                    off.add(new com.google.gson.JsonPrimitive(d));
                }
                if (any) {
                    c.add("offset", off);
                }
            }
            out.add(c);
        }
        return out;
    }

    /** A known status scope, "none" for anything else. */
    public static String statusScope(String s) {
        return STATUS_SCOPES.contains(s) ? s : "none";
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
