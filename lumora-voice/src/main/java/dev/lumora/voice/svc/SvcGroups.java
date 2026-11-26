package dev.lumora.voice.svc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Groups of Simple Voice Chat as the server announces them ({@code voicechat:add_group}, {@code remove_group},
 * {@code joined_group}). Who is in which group comes with the player states ({@link SimpleVoiceConnection.State#group()}).
 */
public final class SvcGroups {
    /** Group type: who hears the members besides the group itself. */
    public enum Type {
        NORMAL("Normal", "Mitglieder hören sich gegenseitig, andere in der Nähe hören sie nicht"),
        OPEN("Offen", "Mitglieder hören sich gegenseitig und Spieler in der Nähe hören mit"),
        ISOLATED("Isoliert", "Mitglieder hören nur die Gruppe, niemanden in der Nähe");

        private final String label, description;

        Type(String label, String description) { this.label = label; this.description = description; }

        public String label() { return label; }

        public String description() { return description; }

        /** Id on the wire (a short). */
        public short id() { return (short) ordinal(); }

        public Type next() { return values()[(ordinal() + 1) % values().length]; }

        static Type of(short id) { return id >= 0 && id < values().length ? values()[id] : NORMAL; }
    }

    public record Group(UUID id, String name, boolean password, boolean persistent, boolean hidden, Type type) {}

    /** Names: 1-24 characters, no control characters, not starting with a space (Simple Voice Chat's rule). */
    private static final Pattern NAME = Pattern.compile("^[^\\p{C}\\s][^\\p{C}]{0,23}$");
    public static final int MAX_NAME = 24, MAX_PASSWORD = 24;

    private final Map<UUID, Group> groups = new ConcurrentHashMap<>();
    private volatile String alert = "";

    public static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }

    /** Passwords follow the same rule as names; empty means no password. */
    public static boolean validPassword(String password) { return password.isEmpty() || NAME.matcher(password).matches(); }

    public void add(Group group) { if (groups.size() < 512 || groups.containsKey(group.id())) groups.put(group.id(), group); }

    public void remove(UUID id) { groups.remove(id); }

    /** Answer to joining: {@code wrongPassword} leaves a message for the list, a successful join clears it. */
    public void joined(UUID group, boolean wrongPassword) { alert = wrongPassword ? "Falsches Passwort." : ""; }

    public void clear() { groups.clear(); alert = ""; }

    /** Visible groups by name. */
    public List<Group> list() {
        return groups.values().stream().filter(g -> !g.hidden()).sorted(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Group get(UUID id) { return id == null ? null : groups.get(id); }

    public String alert() { return alert; }

    public void alert(String message) { alert = message; }
}
