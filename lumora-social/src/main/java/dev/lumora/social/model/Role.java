package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;

/**
 * A group role: name, icon key ({@link Rules#ICONS}), color ({@link Rules#COLORS}) and whether it may manage the group
 * (add/remove members, edit name and description). Every group has the fixed roles {@link #OWNER} and {@link #MEMBER}.
 */
public final class Role {
    public static final String OWNER = "owner";
    public static final String MEMBER = "member";

    public final String id;
    public final String name;
    public final String icon;
    public final int color;
    public final boolean manage;

    public Role(String id, String name, String icon, int color, boolean manage) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.manage = manage;
    }

    public boolean fixed() {
        return OWNER.equals(id) || MEMBER.equals(id);
    }

    public static Role read(JsonObject o) {
        String id = Js.str(o, "id", null);
        String name = Rules.clean(Js.str(o, "name", ""), Rules.ROLE_NAME_MAX, false);
        if (id == null || !id.matches("[a-z0-9]{1,16}") || name.isEmpty()) {
            return null;
        }
        return new Role(id, name, Rules.icon(Js.str(o, "icon", "circle")), Rules.color(Js.num(o, "color", 0)),
                Js.bool(o, "manage", false));
    }

    public JsonObject json() {
        return Js.obj().put("id", id).put("name", name).put("icon", icon).put("color", color).put("manage", manage).json();
    }
}
