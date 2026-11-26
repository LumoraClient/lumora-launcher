package dev.lumora.voice.plasmo;

import dev.lumora.voice.VoiceLog;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Groups of the Plasmo Voice server addon pv-addon-groups (2.x) over its plugin channel {@code pv_addon_groups:channel}:
 * an envelope {@code {type, requestId, payload}} with a MessagePack payload, answers carry the request id. After the
 * handshake the server pushes {@code state_changed}; the group list comes from {@code browse}. Older versions of the
 * addon have no channel (groups only by command); voice in a group works the same with both. All on the client thread.
 */
public final class PlasmoGroups {
    public static final String CHANNEL = "pv_addon_groups:channel";
    static final int PROTOCOL_VERSION = 1;

    public record Member(UUID id, String name, boolean online) {}

    /** {@code joinState}: JOINABLE, PASSWORD, NO_PERMISSION or BANNED. */
    public record Group(UUID id, String name, boolean password, int online, int players, String joinState) {}

    /** What the server allows (defaults until the first state arrives). */
    public record Capabilities(boolean create, boolean browse, boolean leave, int minName, int maxName) {}

    private final Consumer<byte[]> send;
    private final Map<String, Consumer<List<Object>>> pending = new HashMap<>();
    private boolean started, ready;
    private Group current;
    private List<Member> members = List.of();
    private List<Group> groups = List.of();
    private Capabilities capabilities = new Capabilities(true, true, true, 1, 16);
    private String alert = "";
    private long nextRequest;

    public PlasmoGroups(Consumer<byte[]> send) { this.send = send; }

    /** Handshake, then state and list. */
    public void start() {
        if (started) return;
        started = true;
        request("handshake", w -> w.map(2).string("protocolVersion").integer(PROTOCOL_VERSION).string("minProtocolVersion").integer(PROTOCOL_VERSION), values -> {
            var response = MsgPack.map(values.isEmpty() ? null : values.getFirst());
            if (MsgPack.number(response.get("minProtocolVersion")) > PROTOCOL_VERSION) { alert = "Die Gruppen-Erweiterung des Servers ist zu neu."; return; }
            ready = true;
            refresh();
        });
    }

    public void reset() { started = ready = false; current = null; members = List.of(); groups = List.of(); alert = ""; pending.clear(); }

    /** The server answered the handshake: groups can be shown and managed. */
    public boolean ready() { return ready; }

    public Group current() { return current; }

    public List<Member> members() { return members; }

    public List<Group> groups() { return groups; }

    public Capabilities capabilities() { return capabilities; }

    public String alert() { return alert; }

    public void alert(String message) { alert = message; }

    public void refresh() {
        if (!ready) return;
        request("state_request", w -> w.map(0), values -> state(values.isEmpty() ? null : values.getFirst()));
        request("browse", w -> w.map(0), values -> groups = MsgPack.list(MsgPack.map(values.isEmpty() ? null : values.getFirst()).get("groups")).stream()
                .map(PlasmoGroups::group).filter(Objects::nonNull).sorted(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER)).toList());
    }

    /** Creates a group, then sets name and password (the server creates it with a default name). */
    public void create(String name, String password) {
        request("create", w -> w.map(0), values -> {
            if (!result(values, "create")) return;
            request("state_request", w -> w.map(0), state -> {
                state(state.isEmpty() ? null : state.getFirst());
                var created = current;
                if (created == null || (name.isBlank() && password.isEmpty())) { refresh(); return; }
                request("update_settings", w -> {
                    w.map(5).string("groupId").uuid(created.id()).string("name").string(name.isBlank() ? null : name)
                            .string("password").string(password.isEmpty() ? null : password).string("permissions").nil().string("persistent").nil();
                }, update -> { result(update, "update"); refresh(); });
            });
        });
    }

    public void join(UUID group, String password) {
        request("join", w -> w.map(2).string("groupId").uuid(group).string("password").string(password.isEmpty() ? null : password),
                values -> { result(values, "join"); refresh(); });
    }

    public void leave() { request("leave", w -> w.map(0), values -> { result(values, "leave"); refresh(); }); }

    /** A message on the channel: an answer to one of our requests or a pushed state change. */
    public void receive(byte[] payload) {
        try {
            var envelope = MsgPack.map(MsgPack.readAll(payload).stream().findFirst().orElse(null));
            byte[] body = envelope.get("payload") instanceof byte[] b ? b : new byte[0];
            var values = MsgPack.readAll(body);
            String requestId = MsgPack.text(envelope.get("requestId"));
            if (!requestId.isEmpty()) {
                var handler = pending.remove(requestId);
                if (handler != null) handler.accept(values);
                return;
            }
            if (MsgPack.text(envelope.get("type")).equals("state_changed") && !values.isEmpty()) {
                state(MsgPack.map(values.getFirst()).get("state"));
                if (ready) request("browse", w -> w.map(0), list -> groups = MsgPack.list(MsgPack.map(list.isEmpty() ? null : list.getFirst()).get("groups")).stream()
                        .map(PlasmoGroups::group).filter(Objects::nonNull).sorted(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER)).toList());
            }
        } catch (IOException | RuntimeException malformed) {
            VoiceLog.warn("Ignoring malformed Plasmo group message", malformed);
        }
    }

    // --- encoding and decoding ---

    @FunctionalInterface
    interface Body { void write(MsgPack.Writer writer) throws IOException; }

    private void request(String type, Body body, Consumer<List<Object>> answer) {
        String id = "lv-" + (++nextRequest);
        try {
            var payload = new MsgPack.Writer(); body.write(payload);
            byte[] envelope = new MsgPack.Writer().map(3).string("type").string(type).string("requestId").string(id).string("payload").bin(payload.bytes()).bytes();
            if (pending.size() > 64) pending.clear();
            pending.put(id, answer);
            send.accept(envelope);
        } catch (IOException impossible) { throw new java.io.UncheckedIOException(impossible); }
    }

    /** A sealed result: {@code "success"} or an error name (then a message for the list). */
    private boolean result(List<Object> values, String action) {
        String type = values.isEmpty() ? "" : values.getFirst() instanceof String s ? s : MsgPack.text(MsgPack.map(values.getFirst()).get("type"));
        if (type.equals("success")) { alert = ""; return true; }
        alert = switch (type) {
            case "no_permission" -> "Keine Berechtigung dafür.";
            case "group_not_found" -> "Die Gruppe gibt es nicht mehr.";
            case "already_in_group" -> "Du bist schon in dieser Gruppe.";
            case "banned" -> "Du bist aus dieser Gruppe gebannt.";
            case "password_required" -> "Diese Gruppe braucht ein Passwort.";
            case "wrong_password" -> "Falsches Passwort.";
            case "not_in_group" -> "Du bist in keiner Gruppe.";
            case "name_length_invalid" -> {
                var limits = values.size() > 1 ? MsgPack.map(values.get(1)) : Map.<String, Object>of();
                yield "Name: " + MsgPack.number(limits.get("min")) + " bis " + MsgPack.number(limits.get("max")) + " Zeichen.";
            }
            default -> "Gruppe: Aktion fehlgeschlagen (" + action + (type.isEmpty() ? "" : ", " + type) + ").";
        };
        return false;
    }

    private void state(Object value) {
        var state = MsgPack.map(value);
        if (state.isEmpty()) return;
        var caps = MsgPack.map(state.get("capabilities"));
        if (!caps.isEmpty()) capabilities = new Capabilities(MsgPack.flag(caps.get("canCreate")), MsgPack.flag(caps.get("canBrowse")), MsgPack.flag(caps.get("canLeave")),
                Math.max(1, MsgPack.number(caps.get("minNameLength"))), Math.max(1, MsgPack.number(caps.get("maxNameLength"))));
        var details = MsgPack.map(state.get("current"));
        current = details.isEmpty() ? null : group(details.get("summary"));
        members = current == null ? List.of() : MsgPack.list(details.get("members")).stream().map(PlasmoGroups::member).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    static Group group(Object value) {
        var summary = MsgPack.map(value);
        UUID id = MsgPack.uuid(summary.get("id"));
        if (id == null) return null;
        return new Group(id, MsgPack.text(summary.get("name")), MsgPack.flag(summary.get("hasPassword")), MsgPack.number(summary.get("onlineCount")),
                MsgPack.number(summary.get("playerCount")), MsgPack.text(summary.get("joinState")));
    }

    static Member member(Object value) {
        var player = MsgPack.map(value); var profile = MsgPack.map(player.get("profile"));
        UUID id = MsgPack.uuid(profile.get("id"));
        return id == null ? null : new Member(id, MsgPack.text(profile.get("name")), MsgPack.flag(player.get("online")));
    }
}
