package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Conv;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;

import java.util.List;

/** A chat group with its roles and members (each member has exactly one role). */
public final class Group {
    public final String id;
    public final String name;
    public final String description;
    public final String icon;
    public final int color;
    public final String owner;
    public final List<Role> roles;
    public final List<Member> members;
    public final long created;

    public Group(String id, String name, String description, String icon, int color, String owner, List<Role> roles,
                 List<Member> members, long created) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.color = color;
        this.owner = owner;
        this.roles = List.copyOf(roles);
        this.members = List.copyOf(members);
        this.created = created;
    }

    public String conv() {
        return Conv.group(id);
    }

    /** The role with this id; unknown ids fall back to the member role. */
    public Role role(String roleId) {
        Role member = null;
        for (Role r : roles) {
            if (r.id.equals(roleId)) {
                return r;
            }
            if (r.id.equals(Role.MEMBER)) {
                member = r;
            }
        }
        return member != null ? member : new Role(Role.MEMBER, "Mitglied", "circle", Rules.COLORS.get(8), false);
    }

    public Member member(String uuid) {
        for (Member m : members) {
            if (m.profile.uuid.equals(uuid)) {
                return m;
            }
        }
        return null;
    }

    /** Roles for display: owner first, then the custom roles in creation order, the plain member role last. */
    public List<Role> rolesInOrder() {
        List<Role> out = new java.util.ArrayList<>();
        Role member = null;
        for (Role r : roles) {
            if (Role.OWNER.equals(r.id)) {
                out.add(0, r);
            } else if (Role.MEMBER.equals(r.id)) {
                member = r;
            } else {
                out.add(r);
            }
        }
        if (member != null) {
            out.add(member);
        }
        return out;
    }

    /** The role of a member (member role for non-members). */
    public Role roleOf(String uuid) {
        Member m = member(uuid);
        return role(m == null ? Role.MEMBER : m.role);
    }

    /** Owner, or a member whose role may manage. */
    public boolean canManage(String uuid) {
        Member m = member(uuid);
        return m != null && (uuid.equals(owner) || role(m.role).manage);
    }

    public int membersWithRole(String roleId) {
        int n = 0;
        for (Member m : members) {
            if (m.role.equals(roleId)) {
                n++;
            }
        }
        return n;
    }

    public static Group read(JsonObject o) {
        String id = Js.str(o, "id", null);
        String owner = Rules.uuid(Js.str(o, "owner", null));
        if (id == null || !id.matches("[a-z0-9]{1,24}") || owner == null) {
            return null;
        }
        return new Group(id, Rules.clean(Js.str(o, "name", "?"), Rules.GROUP_NAME_MAX, false),
                Rules.clean(Js.str(o, "description", ""), Rules.GROUP_DESCRIPTION_MAX, true),
                Rules.icon(Js.str(o, "icon", "circle")), Rules.color(Js.num(o, "color", 0)), owner,
                Js.list(o, "roles", Role::read), Js.list(o, "members", Member::read), Js.num(o, "created", 0));
    }

    public JsonObject json() {
        return Js.obj().put("id", id).put("name", name).put("description", description).put("icon", icon)
                .put("color", color).put("owner", owner).put("roles", Js.array(roles, Role::json))
                .put("members", Js.array(members, Member::json)).put("created", created).json();
    }
}
