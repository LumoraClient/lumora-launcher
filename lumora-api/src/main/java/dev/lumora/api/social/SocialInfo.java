package dev.lumora.api.social;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the player's Lumora Social account shows (permission {@code social}), read only: friends with their online
 * state and the party. For example, a voice addon can put the party into one voice group. Changes arrive as
 * {@link dev.lumora.api.event.social.SocialChangedEvent}. Everything is empty while Lumora Social is not connected.
 *
 * @since 1.5.0
 */
public interface SocialInfo {
    /** Connected to the Lumora social server. */
    boolean connected();

    /** The player's own account, if connected. */
    Optional<Member> self();

    /** Friends, online ones first. */
    List<Friend> friends();

    boolean isFriend(UUID player);

    /** The player's party, if in one. */
    Optional<Party> party();

    /** A player on Lumora Social. */
    record Member(UUID id, String name) {
    }

    /**
     * A friend.
     *
     * @param online in the launcher or in the game
     * @param status "offline", "launcher", "menu", "singleplayer" or "multiplayer"
     * @param server the server address if the friend shares it, else empty
     */
    record Friend(UUID id, String name, boolean online, String status, String server) {
    }

    /** A party: its leader and all members (the leader included), in the order they joined. */
    record Party(UUID leader, List<Member> members) {
        public Party {
            members = List.copyOf(members);
        }

        public boolean contains(UUID player) {
            return members.stream().anyMatch(m -> m.id().equals(player));
        }
    }
}
