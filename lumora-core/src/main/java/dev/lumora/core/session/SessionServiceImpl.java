package dev.lumora.core.session;

import dev.lumora.api.session.SessionService;
import dev.lumora.core.social.GameSession;
import dev.lumora.social.MojangSession;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link SessionService} on the account the version adapter reports ({@code Platform.session()}). The access token is
 * read only for the Mojang join and never leaves this class.
 */
public final class SessionServiceImpl implements SessionService {
    /** The join itself, replaceable for tests. */
    public interface Joiner {
        void join(String accessToken, String uuidWithoutDashes, String serverHash) throws IOException;
    }

    private final Supplier<Optional<GameSession>> session;
    private final Joiner joiner;

    public SessionServiceImpl(Supplier<Optional<GameSession>> session) {
        this(session, MojangSession::join);
    }

    public SessionServiceImpl(Supplier<Optional<GameSession>> session, Joiner joiner) {
        this.session = session;
        this.joiner = joiner;
    }

    static UUID parse(String uuid) {
        if (uuid == null) {
            return null;
        }
        String hex = uuid.replace("-", "");
        if (!hex.matches("[0-9a-fA-F]{32}")) {
            return null;
        }
        return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
    }

    @Override
    public Optional<Profile> profile() {
        return session.get().flatMap(s -> {
            UUID id = parse(s.uuid());
            return id == null || s.name() == null || s.name().isBlank() ? Optional.empty() : Optional.of(new Profile(id, s.name()));
        });
    }

    @Override
    public void joinServer(String serverHash) throws Exception {
        if (serverHash == null || serverHash.isBlank() || serverHash.length() > 64) {
            throw new IllegalArgumentException("invalid server hash");
        }
        GameSession s = session.get().orElseThrow(() -> new IOException("Keine Minecraft-Sitzung (nicht angemeldet)."));
        UUID id = parse(s.uuid());
        if (id == null) {
            throw new IOException("Keine gültige Minecraft-Sitzung.");
        }
        joiner.join(s.accessToken().get(), id.toString().replace("-", ""), serverHash);
    }
}
