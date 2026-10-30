package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;

/** A Minecraft profile: UUID (32 hex digits, no dashes) and current name. */
public final class Profile {
    public final String uuid;
    public final String name;

    public Profile(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static Profile read(JsonObject o) {
        String uuid = Rules.uuid(Js.str(o, "uuid", null));
        String name = Js.str(o, "name", null);
        return uuid == null || !Rules.validName(name) ? null : new Profile(uuid, name);
    }

    public JsonObject json() {
        return Js.obj().put("uuid", uuid).put("name", name).json();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Profile p && p.uuid.equals(uuid) && p.name.equals(name);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
