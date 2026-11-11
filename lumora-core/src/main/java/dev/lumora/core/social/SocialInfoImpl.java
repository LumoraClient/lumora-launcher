package dev.lumora.core.social;

import dev.lumora.api.social.SocialInfo;
import dev.lumora.social.SocialClient;
import dev.lumora.social.model.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Lumora Social for addons, read only ({@code AddonContext.social()}); empty while not connected. */
public final class SocialInfoImpl implements SocialInfo {
    private final Supplier<LumoraSocial> social;
    private final Runnable guard;

    public SocialInfoImpl(Supplier<LumoraSocial> social, Runnable guard) {
        this.social = social;
        this.guard = guard;
    }

    private SocialClient client() {
        guard.run();
        LumoraSocial s = social.get();
        SocialClient c = s == null ? null : s.client();
        return c != null && c.status() == SocialClient.Status.ONLINE ? c : null;
    }

    /** UUID from 32 hex digits (Lumora Social's form). */
    static UUID uuid(String hex) {
        return UUID.fromString(hex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5"));
    }

    static String hex(UUID id) {
        return id.toString().replace("-", "");
    }

    private static Member member(Profile p) {
        return new Member(uuid(p.uuid), p.name);
    }

    @Override
    public boolean connected() {
        return client() != null;
    }

    @Override
    public Optional<Member> self() {
        SocialClient c = client();
        return c == null || c.self() == null ? Optional.empty() : Optional.of(member(c.self()));
    }

    @Override
    public List<Friend> friends() {
        SocialClient c = client();
        if (c == null) {
            return List.of();
        }
        List<SocialInfo.Friend> l = new ArrayList<>();
        for (dev.lumora.social.model.Friend f : c.friends()) {
            l.add(new SocialInfo.Friend(uuid(f.profile.uuid), f.profile.name, f.presence.online(),
                    f.presence.state.name().toLowerCase(Locale.ROOT), f.presence.detail));
        }
        return l;
    }

    @Override
    public boolean isFriend(UUID player) {
        SocialClient c = client();
        return c != null && player != null && c.friend(hex(player)) != null;
    }

    @Override
    public Optional<SocialInfo.Party> party() {
        SocialClient c = client();
        dev.lumora.social.model.Party p = c == null ? null : c.party();
        if (p == null) {
            return Optional.empty();
        }
        List<Member> members = new ArrayList<>();
        for (Profile m : p.members) {
            members.add(member(m));
        }
        return Optional.of(new SocialInfo.Party(uuid(p.leader), members));
    }
}
