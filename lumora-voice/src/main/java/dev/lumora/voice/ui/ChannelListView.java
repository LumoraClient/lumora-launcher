package dev.lumora.voice.ui;

import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.protocol.VoicePackets;
import dev.lumora.voice.protocol.VoiceProtocol;
import dev.lumora.voice.svc.SvcGroups;
import dev.lumora.voice.platform.VoicePlatform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * "Sprachkanäle" (key B, or the button in the settings card): the Laby voice channels the server announces, or the
 * groups of Simple Voice Chat. A click joins one (password dialog if needed); create one, reload the Laby list or leave
 * the Simple Voice Chat group.
 */
public final class ChannelListView extends ListDialog {
    /** Member lines per channel or group before "+ n weitere". */
    static final int MAX_MEMBERS = 8;
    private final VoiceClient voice;

    public ChannelListView(VoiceClient voice, ViewHost host) {
        super(host, "Sprachkanäle");
        this.voice = voice;
    }

    @Override
    protected String title() { return voice.groups() != null || voice.plasmoGroups() != null ? "Gruppen" : super.title(); }

    @Override
    protected Object state() {
        var state = voice.channels();
        if (state != null) return List.of(state.channels(), state.currentId(), state.users(), state.alert());
        var groups = voice.groups();
        if (groups != null) {
            var list = groups.list();
            return List.of(list, Objects.toString(voice.ownGroup()), list.stream().map(g -> voice.groupMemberList(g.id())).toList(), groups.alert());
        }
        var plasmo = voice.plasmoGroups();
        if (plasmo != null) return List.of(plasmo.groups(), Objects.toString(plasmo.current()), plasmo.members(), plasmo.alert());
        return voice.connected();
    }

    @Override
    protected List<Control> entries() {
        var state = voice.channels();
        if (state == null) return voice.plasmoGroups() != null ? plasmoEntries() : groupEntries();
        UUID current = state.currentId();
        var users = state.users();
        List<Control> out = new ArrayList<>();
        for (var channel : state.channels()) {
            long members = users.stream().filter(u -> u.channelId().equals(channel.id())).count();
            boolean joined = current.equals(channel.id());
            String label = channel.name() + (members > 0 ? " · " + members : "");
            String description = channel.proximity() ? "Umgebungskanal mit räumlichem Klang" : "Sprachkanal ohne Entfernungsbegrenzung";
            out.add(new Control(label, description, () -> joined ? "Verbunden" : channel.password() ? "Passwort  ›" : "Beitreten  ›", () -> join(channel, joined))
                    .toggle(() -> joined));
            members(out, users.stream().filter(u -> u.channelId().equals(channel.id()) && !voice.blocked(u.id()))
                    .map(u -> new VoicePlatform.Named(u.id(), u.name())).sorted(Comparator.comparing(VoicePlatform.Named::name, String.CASE_INSENSITIVE_ORDER)).toList());
        }
        return out;
    }

    /** Simple Voice Chat groups. */
    private List<Control> groupEntries() {
        var groups = voice.groups();
        if (groups == null) return List.of();
        UUID own = voice.ownGroup();
        List<Control> out = new ArrayList<>();
        for (var group : groups.list()) {
            int members = voice.groupMembers(group.id());
            boolean joined = group.id().equals(own);
            String label = group.name() + (members > 0 ? " · " + members : "");
            String description = group.type().label() + ": " + group.type().description() + (group.password() ? " · mit Passwort" : "");
            out.add(new Control(label, description, () -> joined ? "Verbunden" : group.password() ? "Passwort  ›" : "Beitreten  ›", () -> joinGroup(group, joined))
                    .toggle(() -> joined));
            members(out, voice.groupMemberList(group.id()));
        }
        return out;
    }

    /** Groups of Plasmo Voice (pv-addon-groups): members are known for the own group only. */
    private List<Control> plasmoEntries() {
        var plasmo = voice.plasmoGroups();
        var current = plasmo.current();
        List<Control> out = new ArrayList<>();
        var list = new ArrayList<>(plasmo.groups());
        if (current != null && list.stream().noneMatch(g -> g.id().equals(current.id()))) list.addFirst(current);
        for (var group : list) {
            boolean joined = current != null && group.id().equals(current.id());
            int members = joined ? plasmo.members().size() : group.online();
            String label = group.name() + (members > 0 ? " · " + members : "");
            String description = switch (group.joinState()) {
                case "BANNED" -> "Du bist aus dieser Gruppe gebannt";
                case "NO_PERMISSION" -> "Beitreten nicht erlaubt";
                default -> "Gruppe ohne Entfernungsbegrenzung" + (group.password() ? " · mit Passwort" : "");
            };
            boolean closed = group.joinState().equals("BANNED") || group.joinState().equals("NO_PERMISSION");
            var row = new Control(label, description, () -> joined ? "Verbunden" : closed ? "" : group.password() ? "Passwort  ›" : "Beitreten  ›", () -> joinPlasmo(group, joined))
                    .toggle(() -> joined);
            if (closed && !joined) row.inactive();
            out.add(row);
            if (joined) members(out, plasmo.members().stream().filter(m -> m.online() && !voice.blocked(m.id())).map(m -> new VoicePlatform.Named(m.id(), m.name())).toList());
        }
        return out;
    }

    private void joinPlasmo(dev.lumora.voice.plasmo.PlasmoGroups.Group group, boolean joined) {
        if (joined) return;
        if (group.password()) { host.open(PlasmoGroupPromptView.join(voice, host, group)); return; }
        voice.plasmoGroups().join(group.id(), "");
        info = "Wechsel zu „" + group.name() + "“ …"; error = "";
    }

    /** Member lines under a channel or group: head and name, highlighted while talking; long lists end with "+ n weitere". */
    private void members(List<Control> out, List<VoicePlatform.Named> members) {
        UUID self = voice.platform().profile().map(p -> p.id()).orElse(null);
        for (int i = 0; i < Math.min(members.size(), MAX_MEMBERS); i++) {
            var member = members.get(i);
            out.add(Control.member(member.id(), member.name(), () -> voice.talking(member.id()), () -> member.id().equals(self) ? "Du" : ""));
        }
        if (members.size() > MAX_MEMBERS) out.add(Control.member(null, "+ " + (members.size() - MAX_MEMBERS) + " weitere", () -> false, () -> ""));
    }

    private void join(dev.lumora.voice.protocol.VoiceChannels.Channel channel, boolean joined) {
        UUID self = voice.platform().profile().map(p -> p.id()).orElse(null);
        if (joined || self == null) return;
        if (channel.password()) { host.open(ChannelPromptView.join(voice, host, channel.id(), channel.name())); return; }
        voice.control(VoicePackets.MOVE_USER, out -> { VoiceProtocol.uuid(out, self); VoiceProtocol.uuid(out, channel.id()); VoiceProtocol.string(out, ""); });
        info = "Wechsel zu „" + channel.name() + "“ …"; error = "";
    }

    private void joinGroup(SvcGroups.Group group, boolean joined) {
        if (joined) return;
        if (group.password()) { host.open(GroupPromptView.join(voice, host, group)); return; }
        voice.joinGroup(group.id(), "");
        info = "Wechsel zu „" + group.name() + "“ …"; error = "";
    }

    @Override
    protected String emptyText() {
        if (!voice.config().enabled) return "VoiceChat ist aus – in den Einstellungen aktivieren.";
        if (!voice.connected()) return "Noch nicht verbunden. Einen Multiplayer-Server betreten.";
        if (voice.channels() != null) return "Keine Kanäle angekündigt.";
        if (voice.groups() != null) return "Noch keine Gruppen. Mit „Erstellen …“ eine anlegen.";
        if (voice.plasmoGroups() != null) return "Noch keine Gruppen. Mit „Erstellen …“ eine anlegen.";
        if (voice.plasmoGroupsByCommand()) return "Gruppen gibt es hier nur per Befehl /groups (ältere Gruppen-Erweiterung des Servers).";
        return voice.serviceName() + " hat hier keine Kanäle oder Gruppen (der Server erlaubt keine Gruppen).";
    }

    @Override
    protected List<Control> buttons() {
        var plasmo = voice.plasmoGroups();
        if (voice.channels() == null && plasmo != null) {
            var create = new Control("Erstellen …", "Eigene Gruppe, optional mit Passwort", () -> "", () -> host.open(PlasmoGroupPromptView.create(voice, host))).primary();
            var leave = new Control("Verlassen", "Die Gruppe verlassen und wieder nur in der Nähe sprechen", () -> "", () -> {
                plasmo.leave();
                info = "Gruppe wird verlassen …"; error = "";
            }).compact();
            create.active = plasmo.capabilities().create();
            leave.active = plasmo.current() != null && plasmo.capabilities().leave();
            return List.of(create, leave);
        }
        if (voice.channels() == null && voice.groups() != null) {
            var create = new Control("Erstellen …", "Eigene Gruppe, optional mit Passwort", () -> "", () -> host.open(GroupPromptView.create(voice, host))).primary();
            var leave = new Control("Verlassen", "Die Gruppe verlassen und wieder nur in der Nähe sprechen", () -> "", () -> {
                voice.leaveGroup();
                info = "Gruppe wird verlassen …"; error = "";
            }).compact();
            leave.active = voice.ownGroup() != null;
            return List.of(create, leave);
        }
        var create = new Control("Erstellen …", "Eigener Kanal, optional mit Passwort und Näherungsmodus", () -> "",
                () -> host.open(ChannelPromptView.create(voice, host))).primary();
        var reload = new Control("Aktualisieren", "Die Kanäle neu vom Sprachdienst laden", () -> "", () -> {
            voice.control(VoicePackets.RESET_CHANNEL_REQUEST, out -> { });
            info = "Kanäle werden geladen …"; error = "";
        }).compact();
        create.active = reload.active = voice.channels() != null;
        return List.of(create, reload);
    }

    @Override
    protected void drawContent(dev.lumora.api.render.HudGraphics g) {
        var state = voice.channels();
        if (state != null && !state.alert().isEmpty() && error.isEmpty()) error = state.alert();
        var groups = voice.groups();
        if (state == null && groups != null && !groups.alert().isEmpty()) { error = groups.alert(); info = ""; }
        var plasmo = voice.plasmoGroups();
        if (state == null && plasmo != null && !plasmo.alert().isEmpty()) { error = plasmo.alert(); info = ""; }
        super.drawContent(g);
    }
}
