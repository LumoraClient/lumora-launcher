package dev.lumora.api.social;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the player's Lumora Social account shows (permission {@code social}): friends with their online
 * state and the party (read only), since 1.13 also a shared status line per addon. For example, a voice addon can put the party into one voice group. Changes arrive as
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

    /** Who sees a {@link #shareStatus shared status line}. @since 1.13.0 */
    enum StatusScope { ALL, FRIENDS, NONE }

    /**
     * Shares a short status line of this addon (at most 96 characters, plain text) that other Lumora players with the
     * same addon can show at this player's name, e.g. the song a music addon plays: {@link StatusScope#ALL} every
     * Lumora player, {@link StatusScope#FRIENDS} only Lumora friends, {@link StatusScope#NONE} or an empty text clears
     * it. Kept while the game is connected to Lumora Social (sent again after a reconnect), gone when the addon is
     * disabled or the game ends. Cheap: unchanged values are not sent again.
     *
     * @since 1.13.0
     */
    default void shareStatus(String text, StatusScope scope) {
    }

    /**
     * The status line this addon shares for {@code player} (see {@link #shareStatus}), as far as this player may see it;
     * empty if there is none, the player is not on Lumora or it is not known yet. Never blocks: the first call for a
     * player asks Lumora Social in the background and later calls return the answer, refreshed every few seconds while
     * the addon keeps asking. Cheap enough for {@code NameTags.Decorator#lines} every frame.
     *
     * @since 1.13.0
     */
    default Optional<String> sharedStatus(UUID player) {
        return Optional.empty();
    }

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
