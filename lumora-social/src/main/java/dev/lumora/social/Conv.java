package dev.lumora.social;

/**
 * Conversation ids: {@code dm:<uuidA>:<uuidB>} (UUIDs sorted, so both sides use the same id) for direct messages and
 * {@code g:<groupId>} for group chats.
 */
public final class Conv {
    private Conv() {
    }

    public static String dm(String a, String b) {
        return a.compareTo(b) <= 0 ? "dm:" + a + ":" + b : "dm:" + b + ":" + a;
    }

    public static String group(String groupId) {
        return "g:" + groupId;
    }

    public static boolean isDm(String conv) {
        return conv != null && conv.startsWith("dm:") && conv.length() == 3 + 32 + 1 + 32;
    }

    public static boolean isGroup(String conv) {
        return conv != null && conv.startsWith("g:") && conv.length() > 2;
    }

    /** The group id of a group conversation, else null. */
    public static String groupId(String conv) {
        return isGroup(conv) ? conv.substring(2) : null;
    }

    /** The other participant of a direct conversation, or null if {@code self} is not part of it. */
    public static String other(String conv, String self) {
        if (!isDm(conv)) {
            return null;
        }
        String a = conv.substring(3, 35);
        String b = conv.substring(36);
        return a.equals(self) ? b : b.equals(self) ? a : null;
    }

    public static boolean involves(String conv, String uuid) {
        return other(conv, uuid) != null;
    }
}
