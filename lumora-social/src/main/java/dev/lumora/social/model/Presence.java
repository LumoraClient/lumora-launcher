package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;

import java.util.Locale;

/**
 * What a player is doing, as shown to friends. {@code detail} is the server address (multiplayer, only if the player
 * shares it) or empty; {@code version} the Minecraft version of the running game.
 */
public final class Presence {
    public enum State {
        OFFLINE, LAUNCHER, MENU, SINGLEPLAYER, MULTIPLAYER;

        /** Higher wins when one account is connected from launcher and game at once. */
        public int rank() {
            return ordinal();
        }
    }

    public static final Presence OFFLINE = new Presence(State.OFFLINE, "", "");

    public final State state;
    public final String detail;
    public final String version;
    /** The status the player chose: "online", "busy" or "away" ("invisible" never leaves the server: it sends OFFLINE). */
    public final String mode;

    public Presence(State state, String detail, String version) {
        this(state, detail, version, "online");
    }

    public Presence(State state, String detail, String version, String mode) {
        this.state = state;
        this.detail = detail == null ? "" : detail;
        this.version = version == null ? "" : version;
        this.mode = "busy".equals(mode) || "away".equals(mode) ? mode : "online";
    }

    /** The same activity with a chosen status. */
    public Presence withMode(String m) {
        return new Presence(state, detail, version, m);
    }

    public boolean online() {
        return state != State.OFFLINE;
    }

    public boolean inGame() {
        return state == State.MENU || state == State.SINGLEPLAYER || state == State.MULTIPLAYER;
    }

    public static Presence read(JsonObject o) {
        if (o == null) {
            return OFFLINE;
        }
        State s;
        try {
            s = State.valueOf(Js.str(o, "state", "OFFLINE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            s = State.OFFLINE;
        }
        return new Presence(s, Rules.clean(Js.str(o, "detail", ""), Rules.PRESENCE_DETAIL_MAX, false),
                Rules.clean(Js.str(o, "version", ""), 16, false), Js.str(o, "mode", "online"));
    }

    public JsonObject json() {
        Js.Obj o = Js.obj().put("state", state.name()).put("detail", detail).put("version", version);
        if (!mode.equals("online")) {
            o.put("mode", mode);
        }
        return o.json();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Presence p && p.state == state && p.detail.equals(detail) && p.version.equals(version) && p.mode.equals(mode);
    }

    @Override
    public int hashCode() {
        return state.hashCode() * 31 + detail.hashCode();
    }
}
