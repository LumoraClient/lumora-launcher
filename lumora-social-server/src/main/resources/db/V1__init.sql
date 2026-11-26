-- Lumora backend, schema version 1. Identity is the Minecraft UUID (verified at Mojang on every login, never the
-- name); users.id is the stable Lumora-ID. Times are epoch milliseconds (as in the protocol).

CREATE TABLE users (
    id          BIGSERIAL   PRIMARY KEY,
    uuid        CHAR(32)    NOT NULL UNIQUE,
    name        VARCHAR(16) NOT NULL,
    first_seen  BIGINT      NOT NULL,
    last_seen   BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'banned')),
    role        VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'moderator', 'admin'))
);
CREATE INDEX users_name ON users (lower(name));

-- every name a Minecraft account was seen with (name changes)
CREATE TABLE minecraft_accounts (
    uuid        CHAR(32)    NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    name        VARCHAR(16) NOT NULL,
    first_seen  BIGINT      NOT NULL,
    PRIMARY KEY (uuid, name)
);

-- one row per friendship, stored once (user_a < user_b)
CREATE TABLE friendships (
    user_a      CHAR(32) NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    user_b      CHAR(32) NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    since       BIGINT   NOT NULL,
    PRIMARY KEY (user_a, user_b),
    CHECK (user_a < user_b)
);
CREATE INDEX friendships_b ON friendships (user_b);

CREATE TABLE friend_requests (
    from_uuid   CHAR(32) NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    to_uuid     CHAR(32) NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    created     BIGINT   NOT NULL,
    PRIMARY KEY (from_uuid, to_uuid),
    CHECK (from_uuid <> to_uuid)
);
CREATE INDEX friend_requests_to ON friend_requests (to_uuid);

CREATE TABLE blocked_users (
    blocker     CHAR(32) NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    blocked     CHAR(32) NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    created     BIGINT   NOT NULL,
    PRIMARY KEY (blocker, blocked),
    CHECK (blocker <> blocked)
);
CREATE INDEX blocked_users_blocked ON blocked_users (blocked);

CREATE TABLE user_groups (
    id          VARCHAR(24)  PRIMARY KEY,
    name        VARCHAR(32)  NOT NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    icon        VARCHAR(24)  NOT NULL,
    color       INTEGER      NOT NULL,
    owner       CHAR(32)     NOT NULL REFERENCES users (uuid),
    created     BIGINT       NOT NULL
);

CREATE TABLE group_roles (
    group_id    VARCHAR(24) NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    role_id     VARCHAR(24) NOT NULL,
    name        VARCHAR(20) NOT NULL,
    icon        VARCHAR(24) NOT NULL,
    color       INTEGER     NOT NULL,
    manage      BOOLEAN     NOT NULL,
    position    INTEGER     NOT NULL,
    PRIMARY KEY (group_id, role_id)
);

CREATE TABLE group_members (
    group_id    VARCHAR(24) NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    uuid        CHAR(32)    NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    role        VARCHAR(24) NOT NULL,
    joined      BIGINT      NOT NULL,
    position    INTEGER     NOT NULL,
    PRIMARY KEY (group_id, uuid)
);
CREATE INDEX group_members_uuid ON group_members (uuid);

-- "dm:<uuid>:<uuid>" (sorted) or "g:<group id>"
CREATE TABLE conversations (
    id          VARCHAR(80) PRIMARY KEY,
    kind        VARCHAR(8)  NOT NULL CHECK (kind IN ('dm', 'group')),
    created     BIGINT      NOT NULL
);

CREATE TABLE conversation_members (
    conv        VARCHAR(80) NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    uuid        CHAR(32)    NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    last_read   BIGINT      NOT NULL DEFAULT 0,
    muted       BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conv, uuid)
);
CREATE INDEX conversation_members_uuid ON conversation_members (uuid);

CREATE TABLE messages (
    id          BIGINT      PRIMARY KEY,
    conv        VARCHAR(80) NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender      CHAR(32)    NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    body        TEXT        NOT NULL,
    sent_at     BIGINT      NOT NULL,
    edited_at   BIGINT,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX messages_conv ON messages (conv, id DESC);

CREATE TABLE user_settings (
    uuid                CHAR(32)    PRIMARY KEY REFERENCES users (uuid) ON DELETE CASCADE,
    -- who may send friend requests
    request_policy      VARCHAR(24) NOT NULL DEFAULT 'everyone'
                        CHECK (request_policy IN ('everyone', 'friends_of_friends', 'nobody')),
    -- chosen status; 'invisible' hides the player from everyone
    presence_mode       VARCHAR(16) NOT NULL DEFAULT 'online'
                        CHECK (presence_mode IN ('online', 'busy', 'away', 'invisible')),
    -- whether other Lumora players see the Lumora badge at this player's name
    show_badge          BOOLEAN     NOT NULL DEFAULT TRUE,
    updated             BIGINT      NOT NULL
);

-- connections (for statistics and moderation); no IP addresses, only a salted hash
CREATE TABLE sessions (
    id          BIGSERIAL   PRIMARY KEY,
    uuid        CHAR(32)    NOT NULL REFERENCES users (uuid) ON DELETE CASCADE,
    kind        VARCHAR(16) NOT NULL,
    ip_hash     CHAR(16)    NOT NULL,
    started     BIGINT      NOT NULL,
    ended       BIGINT
);
CREATE INDEX sessions_uuid ON sessions (uuid, started DESC);

CREATE TABLE audit_logs (
    id          BIGSERIAL   PRIMARY KEY,
    at          BIGINT      NOT NULL,
    actor       CHAR(32),
    action      VARCHAR(40) NOT NULL,
    target      VARCHAR(80),
    detail      VARCHAR(500)
);
CREATE INDEX audit_logs_at ON audit_logs (at DESC);
CREATE INDEX audit_logs_actor ON audit_logs (actor, at DESC);
