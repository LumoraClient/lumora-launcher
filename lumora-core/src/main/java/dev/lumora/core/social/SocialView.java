package dev.lumora.core.social;

import dev.lumora.core.theme.LumoraTheme;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.social.Conv;
import dev.lumora.social.Endpoint;
import dev.lumora.social.Rules;
import dev.lumora.social.SkinFaces;
import dev.lumora.social.SocialClient;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.ConversationInfo;
import dev.lumora.social.model.Friend;
import dev.lumora.social.model.FriendRequest;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Member;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Role;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The Lumora Social screen of the game, independent of the Minecraft version: everything is drawn with
 * {@link HudGraphics} (every version adapter has one) and input arrives as GLFW key codes, characters and GUI
 * coordinates. Layout like the launcher page: friend and group list | chat | optional info column (overview,
 * members, settings). Immediate mode: every frame records its clickable areas, clicks are matched against the last
 * frame (later areas, i.e. overlays, first).
 */
public final class SocialView {
    /** What the version's Screen provides. */
    public interface Host {
        void close();

        /** Clipboard text for Ctrl+V (empty if none). */
        String clipboard();
    }

    static final int BG = 0xF20F0C17;
    static final int SURFACE = 0xFF17131F;
    static final int SURFACE_2 = 0xFF221C2D;
    static final int BORDER = 0xFF2F2741;
    static final int TEXT = 0xFFECE8F4;
    static final int MUTED = 0xFF9D95AE;
    static final int DIM = 0xFF6B7280;
    static final int OK = 0xFF3DD68C;
    static final int WARN = 0xFFF5B342;
    static final int FAIL = 0xFFF2555A;
    static final int OWN = 0xFF4C2D8F;
    static final int SELECTED = 0xFF2E2152;
    static final int BADGE = 0xFF3B82F6;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    /** Minimal single-line text field. */
    static final class Field {
        final StringBuilder text = new StringBuilder();
        int cursor;
        final int max;

        Field(int max) {
            this.max = max;
        }

        void insert(String s) {
            for (int i = 0; i < s.length() && text.length() < max; i++) {
                char c = s.charAt(i);
                if (c >= 32 && c != 127 && c != '§') {
                    text.insert(cursor++, c);
                }
            }
        }

        void backspace(boolean word) {
            int from = cursor;
            if (word) {
                while (from > 0 && text.charAt(from - 1) == ' ') {
                    from--;
                }
                while (from > 0 && text.charAt(from - 1) != ' ') {
                    from--;
                }
            } else if (from > 0) {
                from--;
            }
            text.delete(from, cursor);
            cursor = from;
        }

        void delete() {
            if (cursor < text.length()) {
                text.deleteCharAt(cursor);
            }
        }

        void clear() {
            text.setLength(0);
            cursor = 0;
        }

        String value() {
            return text.toString();
        }
    }

    private enum Focus { CHAT, ADD_FRIEND, NEW_GROUP }

    private final LumoraSocial social;
    private final Host host;
    private final boolean de;
    private final List<Hit> hits = new ArrayList<>();
    private final Field chatField = new Field(Rules.MESSAGE_MAX);
    private final Field addField = new Field(16);
    private final Field groupField = new Field(Rules.GROUP_NAME_MAX);
    private final Map<String, List<String>> wrapCache = new HashMap<>();
    private int wrapWidth = -1;
    private Focus focus = Focus.CHAT;
    private String selected;
    private boolean infoOpen = true;
    private boolean infoDecided;
    private int infoTab;
    private boolean picking;
    private double sideScroll;
    private double chatScroll;
    private double infoScroll;
    private int lastContentHeight;
    private long lastFirstId = -1;
    private volatile String toast;
    private volatile long toastUntil;
    private volatile String pendingSelect;
    private String confirm;
    private long confirmUntil;
    // regions of the last frame for scrolling
    private int sideX1, sideY1, sideX2, sideY2, chatX1, chatY1, chatX2, chatY2, infoX1, infoY1, infoX2, infoY2;

    public SocialView(LumoraSocial social, Host host) {
        this.social = social;
        this.host = host;
        this.de = social.german();
    }

    private String t(String german, String english) {
        return de ? german : english;
    }

    /** Opens on a conversation (e.g. from a notification); null keeps the automatic choice. */
    public void select(String conv) {
        selected = conv;
        chatScroll = 0;
        infoScroll = 0;
        infoTab = 0;
        picking = false;
        lastFirstId = -1;
        social.viewing(conv);
    }

    /** The screen closes. */
    public void removed() {
        social.viewing(null);
    }

    public String selected() {
        return selected;
    }

    private void toast(String text) {
        toast = text;
        toastUntil = System.currentTimeMillis() + 5000;
    }

    private <T> void run(CompletableFuture<T> f, String success) {
        f.whenComplete((r, e) -> {
            if (e != null) {
                Throwable c = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
                toast(c.getMessage() == null ? t("Fehlgeschlagen", "Failed") : c.getMessage());
            } else if (success != null) {
                toast(success);
            }
        });
    }

    private boolean confirmed(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(confirm) && now < confirmUntil) {
            confirm = null;
            return true;
        }
        confirm = key;
        confirmUntil = now + 3000;
        return false;
    }

    private boolean confirming(String key) {
        return key.equals(confirm) && System.currentTimeMillis() < confirmUntil;
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void small(HudGraphics g, String s, int x, int y, int color) {
        g.push();
        g.translate(x, y);
        g.scale(0.75f);
        g.text(s, 0, 0, color, false);
        g.pop();
    }

    private static int smallWidth(HudGraphics g, String s) {
        return (int) Math.ceil(g.textWidth(s) * 0.75);
    }

    private static String fit(HudGraphics g, String s, int max, boolean smallText) {
        if ((smallText ? smallWidth(g, s) : g.textWidth(s)) <= max) {
            return s;
        }
        String r = s;
        while (r.length() > 1 && (smallText ? smallWidth(g, r + "…") : g.textWidth(r + "…")) > max) {
            r = r.substring(0, r.length() - 1);
        }
        return r + "…";
    }

    /** 8x8 skin face; colored initial until it is loaded. */
    public static void face(HudGraphics g, String uuid, String name, int x, int y, int size) {
        int[] px = SkinFaces.face(uuid);
        if (px == null) {
            g.roundedRect(x, y, size, size, 2, 0xFF000000 | Rules.nameColor(uuid) & 0x7F7F7F);
            String l = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            g.text(l, x + (size - g.textWidth(l)) / 2 + 1, y + (size - 8) / 2, TEXT, false);
            return;
        }
        for (int row = 0; row < 8; row++) {
            int y0 = y + row * size / 8;
            int y1 = y + (row + 1) * size / 8;
            int col = 0;
            while (col < 8) {
                int c = px[row * 8 + col];
                int end = col + 1;
                while (end < 8 && px[row * 8 + end] == c) {
                    end++;
                }
                g.fill(x + col * size / 8, y0, x + end * size / 8, y1, c);
                col = end;
            }
        }
    }

    static int presenceColor(Presence p) {
        return switch (p.state) {
            case OFFLINE -> DIM;
            case LAUNCHER, MENU -> OK;
            case SINGLEPLAYER, MULTIPLAYER -> WARN;
        };
    }

    private String presenceText(Presence p) {
        String v = p.version.isEmpty() ? "" : " · " + p.version;
        return switch (p.state) {
            case OFFLINE -> "Offline";
            case LAUNCHER -> t("Online (Launcher)", "Online (launcher)");
            case MENU -> t("Im Hauptmenü", "In the main menu") + v;
            case SINGLEPLAYER -> t("Einzelspieler", "Singleplayer") + v;
            case MULTIPLAYER -> (p.detail.isEmpty() ? t("Mehrspieler", "Multiplayer") : t("Auf ", "On ") + p.detail) + v;
        };
    }

    private static void dot(HudGraphics g, Presence p, int x, int y, int ring) {
        g.fill(x - 1, y - 1, x + 4, y + 4, ring);
        g.fill(x, y, x + 3, y + 3, presenceColor(p));
    }

    /** BMP symbols that every Minecraft font can draw (unifont / unicode pages). */
    static String glyph(String icon) {
        return switch (icon == null ? "" : icon) {
            case "crown" -> "♛";
            case "shield" -> "⛨";
            case "sword" -> "⚔";
            case "star" -> "★";
            case "bow" -> "➶";
            case "pickaxe" -> "⛏";
            case "heart" -> "❤";
            case "flame" -> "♨";
            case "bolt" -> "⚡";
            case "gem" -> "♦";
            case "flag" -> "⚑";
            default -> "●";
        };
    }

    private static void groupTile(HudGraphics g, Group gr, int x, int y, int size) {
        int c = 0xFF000000 | gr.color;
        g.roundedRect(x, y, size, size, 3, (0x55 << 24) | gr.color);
        String s = glyph(gr.icon);
        g.text(s, x + (size - g.textWidth(s)) / 2 + 1, y + (size - 8) / 2, c, false);
    }

    private boolean button(HudGraphics g, int x, int y, int w, int h, String label, int fill, int fg, int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.roundedRect(x, y, w, h, 3, hover ? brighten(fill) : fill);
        g.text(label, x + (w - g.textWidth(label)) / 2 + 1, y + (h - 8) / 2, fg, false);
        hits.add(new Hit(x, y, x + w, y + h, action));
        return hover;
    }

    private static int brighten(int argb) {
        int a = argb >>> 24;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 24);
        int b = Math.min(255, (argb & 0xFF) + 24);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private void field(HudGraphics g, Field f, int x, int y, int w, int h, String placeholder, boolean focused, int mx, int my, Runnable onFocus) {
        g.card(x, y, w, h, 3, SURFACE_2, focused ? LumoraTheme.accent() : BORDER);
        int tx = x + 5;
        int ty = y + (h - 8) / 2;
        int room = w - 10;
        g.enableScissor(x + 2, y + 1, x + w - 2, y + h - 1);
        if (f.text.length() == 0) {
            g.text(placeholder, tx, ty, DIM, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                g.fill(tx, ty - 1, tx + 1, ty + 9, TEXT);
            }
        } else {
            String before = f.text.substring(0, f.cursor);
            int cw = g.textWidth(before);
            int shift = Math.max(0, cw - room + 4);
            g.text(f.value(), tx - shift, ty, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                g.fill(tx - shift + cw, ty - 1, tx - shift + cw + 1, ty + 9, TEXT);
            }
        }
        g.disableScissor();
        hits.add(new Hit(x, y, x + w, y + h, onFocus));
    }

    private List<String> wrap(HudGraphics g, String key, String text, int max) {
        if (max != wrapWidth) {
            wrapCache.clear();
            wrapWidth = max;
        }
        List<String> cached = wrapCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String cand = line.length() == 0 ? word : line + " " + word;
                if (g.textWidth(cand) <= max) {
                    line.setLength(0);
                    line.append(cand);
                    continue;
                }
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String w = word;
                while (g.textWidth(w) > max && w.length() > 1) {
                    int cut = w.length() - 1;
                    while (cut > 1 && g.textWidth(w.substring(0, cut)) > max) {
                        cut--;
                    }
                    out.add(w.substring(0, cut));
                    w = w.substring(cut);
                }
                line.append(w);
            }
            out.add(line.toString());
        }
        if (wrapCache.size() > 2000) {
            wrapCache.clear();
        }
        wrapCache.put(key, out);
        return out;
    }

    // ------------------------------------------------------------------ render

    public void render(HudGraphics g, int width, int height, int mx, int my) {
        hits.clear();
        String ps = pendingSelect;
        if (ps != null) {
            pendingSelect = null;
            select(ps);
        }
        int pw = Math.min(width - 12, 780);
        int ph = Math.min(height - 12, 460);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        g.fill(0, 0, width, height, 0x88000000);
        g.card(px, py, pw, ph, 6, BG, BORDER);
        SocialClient c = social.client();
        // title bar
        g.text("✦", px + 8, py + 7, LumoraTheme.accent(), false);
        g.text(t("Freunde & Gruppen", "Friends & groups"), px + 19, py + 7, TEXT, false);
        button(g, px + pw - 20, py + 4, 15, 14, "✕", SURFACE_2, MUTED, mx, my, host::close);
        if (c != null) {
            String st;
            int sc;
            switch (c.status()) {
                case ONLINE -> {
                    st = c.self().name + " · " + Endpoint.display(c.uri());
                    sc = OK;
                }
                case CONNECTING -> {
                    st = t("Verbinde mit ", "Connecting to ") + Endpoint.display(c.uri()) + "…";
                    sc = WARN;
                }
                case REJECTED -> {
                    st = c.statusText();
                    sc = FAIL;
                }
                default -> {
                    st = t("Getrennt – neuer Versuch läuft", "Disconnected – retrying") + (c.statusText().isEmpty() ? "" : " (" + c.statusText() + ")");
                    sc = WARN;
                }
            }
            st = fit(g, st, pw - 160, true);
            int sw = smallWidth(g, st);
            small(g, st, px + pw - 26 - sw, py + 9, sc);
            g.fill(px + pw - 32 - sw, py + 10, px + pw - 29 - sw, py + 13, sc);
            if (c.status() == SocialClient.Status.REJECTED) {
                button(g, px + pw - 90 - sw, py + 4, 54, 14, t("Erneut", "Retry"), SURFACE_2, TEXT, mx, my, c::reconnect);
            }
        }
        g.fill(px + 1, py + 21, px + pw - 1, py + 22, BORDER);
        int top = py + 26;
        int bottom = py + ph - 6;
        if (c == null) {
            List<String> lines = wrap(g, "disabled", social.disabledReason(), Math.min(300, pw - 40));
            int y = top + (bottom - top - lines.size() * 11) / 2;
            for (String l : lines) {
                g.text(l, px + (pw - g.textWidth(l)) / 2, y, MUTED, false);
                y += 11;
            }
            return;
        }
        if (selected == null || !exists(c, selected)) {
            autoSelect(c);
        }
        int sideW = Math.max(112, Math.min(170, (int) (pw * 0.30)));
        renderSide(g, c, px + 6, top, sideW, bottom - top, mx, my);
        int cx = px + 6 + sideW + 6;
        int cw = px + pw - 6 - cx;
        renderChat(g, c, cx, top, cw, bottom - top, mx, my);
        String tt = toast;
        if (tt != null && System.currentTimeMillis() < toastUntil) {
            int tw = Math.min(pw - 40, g.textWidth(tt) + 12);
            String shown = fit(g, tt, tw - 12, false);
            int tx = px + (pw - tw) / 2;
            int ty = py + ph - 44;
            g.card(tx, ty, tw, 16, 4, 0xF02A1F48, LumoraTheme.accent());
            g.text(shown, tx + 6, ty + 4, TEXT, false);
        }
    }

    private static boolean exists(SocialClient c, String conv) {
        if (Conv.isGroup(conv)) {
            return c.group(Conv.groupId(conv)) != null;
        }
        return c.friend(Conv.other(conv, c.self().uuid)) != null || !c.messages(conv).isEmpty();
    }

    private void autoSelect(SocialClient c) {
        if (c.status() != SocialClient.Status.ONLINE) {
            return;
        }
        List<Group> groups = c.groups();
        List<Friend> friends = c.friends();
        String next = !groups.isEmpty() ? groups.get(0).conv()
                : !friends.isEmpty() ? Conv.dm(c.self().uuid, friends.get(0).profile.uuid) : null;
        if (next != null && !next.equals(selected)) {
            select(next);
        } else if (next == null && selected != null) {
            select(null);
        }
    }

    // ------------------------------------------------------------------ side list

    private void renderSide(HudGraphics g, SocialClient c, int x, int y, int w, int h, int mx, int my) {
        g.card(x, y, w, h, 4, SURFACE, BORDER);
        sideX1 = x;
        sideY1 = y;
        sideX2 = x + w;
        sideY2 = y + h;
        String me = c.self().uuid;
        // content height first (rows are 22 px, headers 14 px)
        List<FriendRequest> requests = c.requests();
        List<Friend> friends = c.friends();
        List<Group> groups = c.groups();
        int content = 14 + (focus == Focus.ADD_FRIEND ? 20 : 0) + 22 * (requests.size() + friends.size())
                + (friends.isEmpty() && requests.isEmpty() ? 22 : 0) + 6 + 14 + (focus == Focus.NEW_GROUP ? 20 : 0)
                + 22 * groups.size() + (groups.isEmpty() ? 22 : 0) + 4;
        int maxScroll = Math.max(0, content - (h - 4));
        sideScroll = Math.max(0, Math.min(maxScroll, sideScroll));
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int yy = y + 3 - (int) sideScroll;
        // friends header
        small(g, t("FREUNDE", "FRIENDS"), x + 6, yy + 4, MUTED);
        button(g, x + w - 17, yy + 1, 13, 11, "+", SURFACE_2, TEXT, mx, my, () -> {
            focus = focus == Focus.ADD_FRIEND ? Focus.CHAT : Focus.ADD_FRIEND;
            addField.clear();
        });
        yy += 14;
        if (focus == Focus.ADD_FRIEND) {
            field(g, addField, x + 4, yy, w - 8, 16, t("Minecraft-Name, Enter", "Minecraft name, Enter"), true, mx, my, () -> focus = Focus.ADD_FRIEND);
            yy += 20;
        }
        for (FriendRequest r : requests) {
            int ry = yy;
            face(g, r.profile.uuid, r.profile.name, x + 5, ry + 3, 16);
            int textW = w - 26 - (r.incoming ? 30 : 16);
            g.text(fit(g, r.profile.name, textW, false), x + 25, ry + 3, TEXT, false);
            small(g, fit(g, r.incoming ? t("möchte dein Freund sein", "wants to be friends") : t("Anfrage gesendet", "request sent"), textW, true),
                    x + 25, ry + 13, r.incoming ? LumoraTheme.accentHover() : MUTED);
            int bx = x + w - 16;
            button(g, bx, ry + 5, 12, 12, "✖", 0x55F2555A, FAIL, mx, my, () -> run(c.declineFriend(r.profile.uuid), null));
            if (r.incoming) {
                button(g, bx - 14, ry + 5, 12, 12, "✔", 0x553DD68C, OK, mx, my, () -> run(c.acceptFriend(r.profile.uuid), null));
            }
            yy += 22;
        }
        if (friends.isEmpty() && requests.isEmpty()) {
            for (String l : wrap(g, "nofriends", t("Noch keine Freunde. Über + hinzufügen.", "No friends yet. Add one with +."), w - 12)) {
                small(g, l, x + 6, yy + 2, MUTED);
                yy += 8;
            }
            yy = Math.max(yy, yy + 6);
        }
        for (Friend f : friends) {
            String conv = Conv.dm(me, f.profile.uuid);
            row(g, x, yy, w, conv.equals(selected), mx, my, () -> select(conv));
            face(g, f.profile.uuid, f.profile.name, x + 5, yy + 3, 16);
            dot(g, f.presence, x + 18, yy + 16, conv.equals(selected) ? SELECTED : SURFACE);
            int unread = c.unread(conv);
            int textW = w - 30 - (unread > 0 ? 16 : 0);
            g.text(fit(g, f.profile.name, textW, false), x + 25, yy + 3, f.presence.online() ? TEXT : MUTED, false);
            small(g, fit(g, presenceText(f.presence), textW, true), x + 25, yy + 13, f.presence.online() ? MUTED : DIM);
            badge(g, unread, x + w - 6, yy + 6);
            yy += 22;
        }
        yy += 6;
        small(g, t("GRUPPEN", "GROUPS"), x + 6, yy + 4, MUTED);
        button(g, x + w - 17, yy + 1, 13, 11, "+", SURFACE_2, TEXT, mx, my, () -> {
            focus = focus == Focus.NEW_GROUP ? Focus.CHAT : Focus.NEW_GROUP;
            groupField.clear();
        });
        yy += 14;
        if (focus == Focus.NEW_GROUP) {
            field(g, groupField, x + 4, yy, w - 8, 16, t("Gruppenname, Enter", "Group name, Enter"), true, mx, my, () -> focus = Focus.NEW_GROUP);
            yy += 20;
        }
        if (groups.isEmpty()) {
            for (String l : wrap(g, "nogroups", t("Noch keine Gruppen. Über + erstellen.", "No groups yet. Create one with +."), w - 12)) {
                small(g, l, x + 6, yy + 2, MUTED);
                yy += 8;
            }
        }
        for (Group gr : groups) {
            String conv = gr.conv();
            row(g, x, yy, w, conv.equals(selected), mx, my, () -> select(conv));
            groupTile(g, gr, x + 5, yy + 3, 16);
            ConversationInfo ci = c.conversation(conv);
            boolean muted = ci != null && ci.muted;
            int unread = muted ? 0 : c.unread(conv);
            int textW = w - 30 - (unread > 0 ? 16 : 0);
            g.text(fit(g, gr.name, textW, false), x + 25, yy + 3, TEXT, false);
            small(g, fit(g, members(gr.members.size()) + (muted ? t(" · stumm", " · muted") : ""), textW, true), x + 25, yy + 13, MUTED);
            badge(g, unread, x + w - 6, yy + 6);
            yy += 22;
        }
        g.disableScissor();
    }

    private String members(int n) {
        return n + (n == 1 ? t(" Mitglied", " member") : t(" Mitglieder", " members"));
    }

    private void row(HudGraphics g, int x, int y, int w, boolean selectedRow, int mx, int my, Runnable action) {
        boolean hover = mx >= x + 2 && mx < x + w - 2 && my >= y && my < y + 22 && my >= sideY1 && my < sideY2;
        if (selectedRow || hover) {
            g.roundedRect(x + 2, y + 1, w - 4, 20, 3, selectedRow ? SELECTED : 0x66221C2D);
            if (selectedRow) {
                g.fill(x + 2, y + 6, x + 3, y + 16, LumoraTheme.accent());
            }
        }
        hits.add(new Hit(x + 2, Math.max(y, sideY1), x + w - 2, Math.min(y + 22, sideY2), action));
    }

    private static void badge(HudGraphics g, int n, int right, int y) {
        if (n <= 0) {
            return;
        }
        String s = n > 99 ? "99+" : Integer.toString(n);
        int w = Math.max(11, g.textWidth(s) + 5);
        g.roundedRect(right - w, y, w, 10, 3, BADGE);
        g.text(s, right - w + (w - g.textWidth(s)) / 2 + 1, y + 1, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ chat

    private void renderChat(HudGraphics g, SocialClient c, int x, int y, int w, int h, int mx, int my) {
        g.card(x, y, w, h, 4, SURFACE, BORDER);
        if (selected == null) {
            String s = c.status() == SocialClient.Status.ONLINE
                    ? t("Wähle links einen Freund oder eine Gruppe.", "Pick a friend or group on the left.")
                    : t("Nicht verbunden.", "Not connected.");
            g.text(s, x + (w - g.textWidth(s)) / 2, y + h / 2 - 4, MUTED, false);
            return;
        }
        String me = c.self().uuid;
        Group gr = Conv.isGroup(selected) ? c.group(Conv.groupId(selected)) : null;
        String other = gr == null ? Conv.other(selected, me) : null;
        // header
        int hy = y + 4;
        String title;
        String sub;
        int subColor = MUTED;
        if (gr != null) {
            groupTile(g, gr, x + 6, hy, 16);
            title = gr.name;
            sub = members(gr.members.size());
        } else {
            Presence p = c.presenceOf(other);
            face(g, other, c.nameOf(other), x + 6, hy, 16);
            dot(g, p, x + 19, hy + 13, SURFACE);
            title = c.nameOf(other);
            sub = presenceText(p);
            subColor = p.online() ? MUTED : DIM;
        }
        ConversationInfo ci = c.conversation(selected);
        boolean muted = ci != null && ci.muted;
        int bw = 34;
        int bxInfo = x + w - 6 - bw;
        int bxMute = bxInfo - 4 - bw;
        int titleW = bxMute - (x + 27) - 4;
        g.text(fit(g, title, titleW, false), x + 27, hy, TEXT, false);
        small(g, fit(g, sub, titleW, true), x + 27, hy + 10, subColor);
        button(g, bxInfo, hy + 1, bw, 13, infoOpen ? "Info ◂" : "Info ▸", infoOpen ? SELECTED : SURFACE_2, TEXT, mx, my, () -> {
            infoOpen = !infoOpen;
            picking = false;
        });
        String sel = selected;
        button(g, bxMute, hy + 1, bw, 13, muted ? t("Ton an", "Unmute") : t("Stumm", "Mute"), SURFACE_2, muted ? WARN : TEXT, mx, my,
                () -> run(c.setMuted(sel, !muted), null));
        g.fill(x + 1, y + 23, x + w - 1, y + 24, BORDER);
        // input
        int iy = y + h - 21;
        boolean canSend = gr != null || c.friend(other) != null;
        field(g, chatField, x + 6, iy, w - 12 - 20, 16, canSend ? t("Nachricht schreiben…", "Write a message…")
                : t("Ihr seid keine Freunde mehr", "You are no longer friends"), focus == Focus.CHAT, mx, my, () -> focus = Focus.CHAT);
        button(g, x + w - 6 - 17, iy, 17, 16, "➤", LumoraTheme.accent(), 0xFF0F0C17, mx, my, this::send);
        // info column: beside the chat when both fit, else (small GUI) as an overlay that starts closed
        int columnW = Math.max(120, Math.min(170, (int) (w * 0.42)));
        boolean fits = w - columnW >= 220;
        if (!infoDecided) {
            infoDecided = true;
            infoOpen = fits;
        }
        boolean overlay = infoOpen && !fits;
        int infoW = infoOpen ? (overlay ? Math.min(w - 30, 170) : columnW) : 0;
        int msgX1 = x + 1;
        int msgX2 = overlay ? x + w - 1 : x + w - 1 - infoW;
        int msgY1 = y + 25;
        int msgY2 = iy - 3;
        chatX1 = msgX1;
        chatY1 = msgY1;
        chatX2 = msgX2;
        chatY2 = msgY2;
        renderMessages(g, c, gr, msgX1, msgY1, msgX2, msgY2, mx, my);
        if (infoW > 0) {
            int ix = x + w - infoW;
            if (overlay) {
                g.fill(ix - 1, y + 24, x + w - 1, iy - 3, SURFACE);
                // clicks on the overlay background must not reach the messages below
                hits.add(new Hit(ix, y + 24, x + w - 1, iy - 3, () -> { }));
            }
            g.fill(ix, y + 24, ix + 1, iy - 3, BORDER);
            infoX1 = ix + 1;
            infoY1 = y + 24;
            infoX2 = x + w - 1;
            infoY2 = iy - 3;
            if (gr != null) {
                renderGroupInfo(g, c, gr, ix + 1, y + 24, infoW - 2, iy - 3 - (y + 24), mx, my);
            } else {
                renderFriendInfo(g, c, other, ix + 1, y + 24, infoW - 2, iy - 3 - (y + 24), mx, my);
            }
        } else {
            infoX1 = infoX2 = 0;
        }
    }

    private void send() {
        SocialClient c = social.client();
        if (c == null || selected == null || chatField.value().isBlank()) {
            return;
        }
        if (Conv.isDm(selected) && c.friend(Conv.other(selected, c.self().uuid)) == null) {
            toast(t("Nachrichten gehen nur an Freunde.", "Messages only go to friends."));
            return;
        }
        c.send(selected, chatField.value());
        chatField.clear();
        chatScroll = 0;
    }

    private void renderMessages(HudGraphics g, SocialClient c, Group gr, int x1, int y1, int x2, int y2, int mx, int my) {
        String me = c.self().uuid;
        List<ChatMessage> msgs = c.messages(selected);
        List<SocialClient.Pending> pending = c.pending(selected);
        ConversationInfo ci = c.conversation(selected);
        long othersRead = ci == null ? 0 : ci.othersRead;
        int w = x2 - x1;
        int maxBubble = Math.max(60, (int) ((w - 30) * 0.75));
        int textMax = maxBubble - 10;
        // layout pass
        record Item(int kind, String sender, String text, long at, boolean own, boolean header, int status, String nonce, List<String> lines, int h) {
        }
        List<Item> items = new ArrayList<>();
        LocalDate lastDay = null;
        String lastSender = null;
        long lastAt = 0;
        int total = 4;
        for (ChatMessage m : msgs) {
            LocalDate d = Instant.ofEpochMilli(m.at).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!d.equals(lastDay)) {
                items.add(new Item(0, null, dayLabel(d), m.at, false, false, 0, null, List.of(), 14));
                total += 14;
                lastDay = d;
                lastSender = null;
            }
            boolean own = m.sender.equals(me);
            boolean header = !own && (!m.sender.equals(lastSender) || m.at - lastAt > 300_000);
            List<String> lines = wrap(g, "m" + m.id, m.text, textMax);
            int bh = lines.size() * 10 + 9;
            int ih = bh + (header ? 11 : 0) + (header ? 4 : 2);
            items.add(new Item(1, m.sender, m.text, m.at, own, header, own ? (othersRead >= m.id ? 2 : 1) : 0, null, lines, ih));
            total += ih;
            lastSender = m.sender;
            lastAt = m.at;
        }
        for (SocialClient.Pending p : pending) {
            List<String> lines = wrap(g, "p" + p.nonce, p.text, textMax);
            int bh = lines.size() * 10 + 9;
            int ih = bh + 2 + (p.error != null ? 9 : 0);
            items.add(new Item(1, me, p.text, p.at, true, false, p.error == null ? 3 : 4, p.nonce, lines, ih));
            total += ih;
        }
        total += 4;
        int view = y2 - y1;
        long firstId = msgs.isEmpty() ? -1 : msgs.get(0).id;
        if (lastFirstId > 0 && firstId > 0 && firstId < lastFirstId && chatScroll > 0) {
            chatScroll += total - lastContentHeight; // older page loaded: keep the visible messages in place
        }
        lastFirstId = firstId;
        lastContentHeight = total;
        int maxScroll = Math.max(0, total - view);
        chatScroll = Math.max(0, Math.min(maxScroll, chatScroll));
        if (chatScroll >= maxScroll - 2 && c.historyLoaded(selected) && !c.historyComplete(selected)) {
            c.loadHistory(selected);
        }
        int y = y1 - (maxScroll - (int) chatScroll);
        g.enableScissor(x1, y1, x2, y2);
        if (items.isEmpty()) {
            String s = c.historyLoaded(selected) ? t("Noch keine Nachrichten. Schreib die erste!", "No messages yet. Write the first one!")
                    : t("Lade Nachrichten…", "Loading messages…");
            g.text(s, x1 + (w - g.textWidth(s)) / 2, y1 + 20, MUTED, false);
        }
        y += 4;
        for (Item it : items) {
            if (y > y2 + 20) {
                break;
            }
            if (y + it.h < y1 - 20) {
                y += it.h;
                continue;
            }
            if (it.kind == 0) {
                int tw = smallWidth(g, it.text);
                int cx = x1 + w / 2;
                g.fill(x1 + 16, y + 6, cx - tw / 2 - 6, y + 7, BORDER);
                g.fill(cx + tw / 2 + 6, y + 6, x2 - 16, y + 7, BORDER);
                small(g, it.text, cx - tw / 2, y + 3, MUTED);
                y += it.h;
                continue;
            }
            int top = y + (it.header ? 4 : 2);
            if (it.header) {
                face(g, it.sender, c.nameOf(it.sender), x1 + 6, top, 16);
                String name = c.nameOf(it.sender);
                g.text(name, x1 + 27, top, 0xFF000000 | Rules.nameColor(it.sender), false);
                if (gr != null) {
                    Role r = gr.roleOf(it.sender);
                    if (!Role.MEMBER.equals(r.id)) {
                        String tag = glyph(r.icon) + " " + r.name;
                        g.text(tag, x1 + 27 + g.textWidth(name) + 6, top, 0xFF000000 | r.color, false);
                    }
                }
                top += 11;
            }
            int lw = 0;
            for (String l : it.lines) {
                lw = Math.max(lw, g.textWidth(l));
            }
            String time = TIME.format(Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()));
            String marks = it.status == 2 ? " ✔✔" : it.status == 1 ? " ✔" : it.status == 3 ? " …" : it.status == 4 ? " !" : "";
            int metaW = smallWidth(g, time + marks);
            int bw = Math.min(maxBubble, Math.max(lw, metaW) + 10);
            int bh = it.lines.size() * 10 + 9;
            int bx = it.own ? x2 - 6 - bw : x1 + 27;
            int fill = it.own ? (it.status == 4 ? 0xFF5A1F24 : OWN) : SURFACE_2;
            g.roundedRect(bx, top, bw, bh, 4, fill);
            int ly = top + 3;
            for (String l : it.lines) {
                g.text(l, bx + 5, ly, TEXT, false);
                ly += 10;
            }
            int mxr = bx + bw - 4 - metaW;
            small(g, time, mxr, top + bh - 7, it.own ? 0xFFCFC4EA : MUTED);
            if (!marks.isEmpty()) {
                small(g, marks, mxr + smallWidth(g, time), top + bh - 7, it.status == 2 ? 0xFFC4B5FD : it.status == 4 ? FAIL : 0xFFB7A9DC);
            }
            if (it.status == 4) {
                String tip = t("Nicht gesendet – klicken zum Wiederholen", "Not sent – click to retry");
                small(g, tip, bx + bw - smallWidth(g, tip), top + bh + 2, FAIL);
                String nonce = it.nonce;
                hits.add(new Hit(bx, Math.max(top, y1), bx + bw, Math.min(top + bh, y2), () -> c.discardPending(nonce, true)));
            }
            y += it.h;
        }
        g.disableScissor();
        if (chatScroll > 30) {
            button(g, x2 - 22, y2 - 16, 16, 13, "▾", SURFACE_2, TEXT, mx, my, () -> chatScroll = 0);
        }
    }

    private String dayLabel(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) {
            return t("HEUTE", "TODAY");
        }
        if (d.equals(today.minusDays(1))) {
            return t("GESTERN", "YESTERDAY");
        }
        return DateTimeFormatter.ofPattern("d. MMMM yyyy", de ? Locale.GERMANY : Locale.ENGLISH).format(d).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ info column

    private int infoSection(HudGraphics g, String title, int x, int y) {
        small(g, title, x + 6, y + 4, MUTED);
        return y + 13;
    }

    private void renderGroupInfo(HudGraphics g, SocialClient c, Group gr, int x, int y, int w, int h, int mx, int my) {
        String me = c.self().uuid;
        boolean manage = gr.canManage(me);
        g.enableScissor(x, y, x + w, y + h);
        int yy = y - (int) infoScroll;
        int start = yy;
        // banner
        int col = 0xFF000000 | gr.color;
        g.fill(x, yy, x + w, yy + 18, (0xAA << 24) | gr.color);
        g.roundedRect(x + 6, yy + 8, 20, 20, 4, SURFACE);
        g.roundedRect(x + 7, yy + 9, 18, 18, 4, col);
        String gl = glyph(gr.icon);
        g.text(gl, x + 7 + (18 - g.textWidth(gl)) / 2 + 1, yy + 14, 0xFFFFFFFF, false);
        g.text(fit(g, gr.name, w - 36, false), x + 30, yy + 21, TEXT, false);
        yy += 31;
        small(g, members(gr.members.size()), x + 6, yy, MUTED);
        yy += 10;
        // tabs
        String[] tabs = {t("Übersicht", "Overview"), t("Mitglieder", "Members"), t("Einst.", "Settings")};
        int tw = (w - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int tx = x + 4 + i * tw;
            int idx = i;
            boolean sel = infoTab == i;
            boolean hover = mx >= tx && mx < tx + tw && my >= yy && my < yy + 13;
            String label = fit(g, tabs[i], tw - 4, true);
            small(g, label, tx + (tw - smallWidth(g, label)) / 2, yy + 3, sel ? TEXT : hover ? TEXT : MUTED);
            if (sel) {
                g.fill(tx + 4, yy + 11, tx + tw - 4, yy + 12, LumoraTheme.accent());
            }
            hits.add(new Hit(tx, Math.max(yy, y), tx + tw, Math.min(yy + 13, y + h), () -> {
                infoTab = idx;
                infoScroll = 0;
                picking = false;
            }));
        }
        yy += 13;
        g.fill(x + 2, yy, x + w - 2, yy + 1, BORDER);
        yy += 3;
        switch (infoTab) {
            case 1 -> {
                if (picking) {
                    yy = infoSection(g, t("FREUND HINZUFÜGEN", "ADD A FRIEND"), x, yy);
                    int n = 0;
                    for (Friend f : c.friends()) {
                        if (gr.member(f.profile.uuid) != null) {
                            continue;
                        }
                        n++;
                        int ry = yy;
                        boolean hover = mx >= x + 2 && mx < x + w - 2 && my >= ry && my < ry + 18;
                        if (hover) {
                            g.roundedRect(x + 2, ry, w - 4, 18, 3, SELECTED);
                        }
                        face(g, f.profile.uuid, f.profile.name, x + 5, ry + 2, 14);
                        g.text(fit(g, f.profile.name, w - 30, false), x + 23, ry + 5, TEXT, false);
                        String uuid = f.profile.uuid;
                        hits.add(new Hit(x + 2, Math.max(ry, y), x + w - 2, Math.min(ry + 18, y + h), () -> {
                            picking = false;
                            run(c.addMember(gr.id, uuid), null);
                        }));
                        yy += 19;
                    }
                    if (n == 0) {
                        for (String l : wrap(g, "pick-none", t("Alle Freunde sind schon in der Gruppe.", "All friends are already in the group."), w - 12)) {
                            small(g, l, x + 6, yy, MUTED);
                            yy += 8;
                        }
                    }
                    yy += 4;
                    button(g, x + 6, yy, w - 12, 13, t("Abbrechen", "Cancel"), SURFACE_2, TEXT, mx, my, () -> picking = false);
                    yy += 16;
                } else {
                    if (manage) {
                        button(g, x + 6, yy, w - 12, 13, t("+ Mitglied hinzufügen", "+ Add member"), SURFACE_2, TEXT, mx, my, () -> picking = true);
                        yy += 17;
                    }
                    List<Member> list = new ArrayList<>(gr.members);
                    List<Role> order = gr.rolesInOrder();
                    list.sort((a, b) -> {
                        int ra = order.indexOf(gr.role(a.role));
                        int rb = order.indexOf(gr.role(b.role));
                        return ra != rb ? Integer.compare(ra, rb) : a.profile.name.compareToIgnoreCase(b.profile.name);
                    });
                    for (Member m : list) {
                        Role r = gr.role(m.role);
                        face(g, m.profile.uuid, m.profile.name, x + 5, yy + 2, 14);
                        boolean self = m.profile.uuid.equals(me);
                        if (self || c.friend(m.profile.uuid) != null) {
                            dot(g, self ? new Presence(Presence.State.MENU, "", "") : c.presenceOf(m.profile.uuid), x + 16, yy + 13, SURFACE);
                        }
                        boolean canKick = manage && !self && !m.profile.uuid.equals(gr.owner) && (me.equals(gr.owner) || !gr.canManage(m.profile.uuid));
                        int textW = w - 28 - (canKick ? 14 : 0);
                        g.text(fit(g, m.profile.name + (self ? t(" (du)", " (you)") : ""), textW, false), x + 23, yy + 2, TEXT, false);
                        small(g, fit(g, glyph(r.icon) + " " + r.name, textW, true), x + 23, yy + 11, 0xFF000000 | r.color);
                        if (canKick) {
                            String key = "kick:" + gr.id + m.profile.uuid;
                            String uuid = m.profile.uuid;
                            String name = m.profile.name;
                            button(g, x + w - 16, yy + 3, 12, 12, "✖", confirming(key) ? 0xAAF2555A : 0x33F2555A, FAIL, mx, my, () -> {
                                if (confirmed(key)) {
                                    run(c.kickMember(gr.id, uuid), name + t(" entfernt", " removed"));
                                } else {
                                    toast(t("Nochmal klicken, um ", "Click again to remove ") + name + t(" zu entfernen", ""));
                                }
                            });
                        }
                        yy += 20;
                    }
                }
            }
            case 2 -> {
                yy = infoSection(g, t("BENACHRICHTIGUNGEN", "NOTIFICATIONS"), x, yy);
                ConversationInfo ci = c.conversation(gr.conv());
                boolean muted = ci != null && ci.muted;
                button(g, x + 6, yy, w - 12, 13, muted ? t("Aus – einschalten", "Off – turn on") : t("An – stummschalten", "On – mute"),
                        muted ? SURFACE_2 : SELECTED, muted ? WARN : TEXT, mx, my, () -> run(c.setMuted(gr.conv(), !muted), null));
                yy += 18;
                yy = infoSection(g, t("GRUPPE", "GROUP"), x, yy);
                for (String l : wrap(g, "launcher-hint", t("Name, Symbol, Beschreibung und Rollen im Launcher unter „Freunde“ bearbeiten.",
                        "Edit name, symbol, description and roles in the launcher under \"Freunde\"."), w - 12)) {
                    small(g, l, x + 6, yy, MUTED);
                    yy += 8;
                }
                yy += 6;
                String key = "leave:" + gr.id;
                button(g, x + 6, yy, w - 12, 13, confirming(key) ? t("Wirklich verlassen?", "Really leave?") : t("Gruppe verlassen", "Leave group"),
                        confirming(key) ? 0xAAF2555A : 0x33F2555A, FAIL, mx, my, () -> {
                            if (confirmed(key)) {
                                run(c.leaveGroup(gr.id), t("Gruppe verlassen", "Left the group"));
                            }
                        });
                yy += 18;
            }
            default -> {
                yy = infoSection(g, t("BESCHREIBUNG", "DESCRIPTION"), x, yy);
                String d = gr.description.isEmpty() ? t("Keine Beschreibung.", "No description.") : gr.description;
                for (String l : wrap(g, "desc" + gr.id + d.hashCode(), d, (int) ((w - 12) / 0.75))) {
                    small(g, l, x + 6, yy, gr.description.isEmpty() ? MUTED : TEXT);
                    yy += 8;
                }
                yy += 4;
                yy = infoSection(g, t("ROLLEN", "ROLES"), x, yy);
                for (Role r : gr.rolesInOrder()) {
                    int n = gr.membersWithRole(r.id);
                    g.roundedRect(x + 6, yy + 1, 14, 14, 3, (0x44 << 24) | r.color);
                    String gl2 = glyph(r.icon);
                    g.text(gl2, x + 6 + (14 - g.textWidth(gl2)) / 2 + 1, yy + 4, 0xFF000000 | r.color, false);
                    g.text(fit(g, r.name, w - 30, false), x + 24, yy + 1, TEXT, false);
                    small(g, members(n), x + 24, yy + 10, MUTED);
                    yy += 18;
                }
            }
        }
        int content = yy - start + 4;
        g.disableScissor();
        infoScroll = Math.max(0, Math.min(Math.max(0, content - h), infoScroll));
    }

    private void renderFriendInfo(HudGraphics g, SocialClient c, String uuid, int x, int y, int w, int h, int mx, int my) {
        Friend f = c.friend(uuid);
        String name = c.nameOf(uuid);
        Presence p = c.presenceOf(uuid);
        g.enableScissor(x, y, x + w, y + h);
        int yy = y - (int) infoScroll;
        int start = yy;
        g.fill(x, yy, x + w, yy + 18, (0xAA << 24) | Rules.nameColor(uuid));
        g.roundedRect(x + 6, yy + 6, 26, 26, 3, SURFACE);
        face(g, uuid, name, x + 7, yy + 7, 24);
        g.text(fit(g, name, w - 40, false), x + 36, yy + 21, TEXT, false);
        yy += 35;
        g.fill(x + 6, yy + 2, x + 9, yy + 5, presenceColor(p));
        small(g, fit(g, presenceText(p), w - 18, true), x + 12, yy + 1, presenceColor(p));
        yy += 12;
        if (f == null) {
            for (String l : wrap(g, "nofriend", t("Ihr seid keine Freunde mehr.", "You are no longer friends."), w - 12)) {
                small(g, l, x + 6, yy, MUTED);
                yy += 8;
            }
        } else {
            yy = infoSection(g, t("FREUND SEIT", "FRIENDS SINCE"), x, yy);
            small(g, DateTimeFormatter.ofPattern("d. MMMM yyyy", de ? Locale.GERMANY : Locale.ENGLISH)
                    .format(Instant.ofEpochMilli(f.since).atZone(ZoneId.systemDefault())), x + 6, yy, TEXT);
            yy += 12;
            String conv = Conv.dm(c.self().uuid, uuid);
            ConversationInfo ci = c.conversation(conv);
            boolean muted = ci != null && ci.muted;
            yy = infoSection(g, t("BENACHRICHTIGUNGEN", "NOTIFICATIONS"), x, yy);
            button(g, x + 6, yy, w - 12, 13, muted ? t("Aus – einschalten", "Off – turn on") : t("An – stummschalten", "On – mute"),
                    muted ? SURFACE_2 : SELECTED, muted ? WARN : TEXT, mx, my, () -> run(c.setMuted(conv, !muted), null));
            yy += 18;
            List<Group> common = new ArrayList<>();
            for (Group gr : c.groups()) {
                if (gr.member(uuid) != null) {
                    common.add(gr);
                }
            }
            if (!common.isEmpty()) {
                yy = infoSection(g, t("GEMEINSAME GRUPPEN", "SHARED GROUPS"), x, yy);
                for (Group gr : common) {
                    groupTile(g, gr, x + 6, yy, 14);
                    g.text(fit(g, gr.name, w - 30, false), x + 24, yy + 3, TEXT, false);
                    String gc = gr.conv();
                    hits.add(new Hit(x + 2, Math.max(yy, y), x + w - 2, Math.min(yy + 16, y + h), () -> select(gc)));
                    yy += 17;
                }
            }
            yy += 4;
            String key = "unfriend:" + uuid;
            button(g, x + 6, yy, w - 12, 13, confirming(key) ? t("Wirklich entfernen?", "Really remove?") : t("Freund entfernen", "Remove friend"),
                    confirming(key) ? 0xAAF2555A : 0x33F2555A, FAIL, mx, my, () -> {
                        if (confirmed(key)) {
                            run(c.removeFriend(uuid), name + t(" entfernt", " removed"));
                        }
                    });
            yy += 18;
        }
        int content = yy - start + 4;
        g.disableScissor();
        infoScroll = Math.max(0, Math.min(Math.max(0, content - h), infoScroll));
    }

    // ------------------------------------------------------------------ input

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) {
            return false;
        }
        Focus before = focus;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(x, y)) {
                h.action.run();
                // a click outside the list ends inline editing (add friend / new group)
                if (before != Focus.CHAT && focus == before && !inSide(x, y)) {
                    focus = Focus.CHAT;
                }
                return true;
            }
        }
        focus = Focus.CHAT;
        return true;
    }

    private boolean inSide(double x, double y) {
        return x >= sideX1 && x < sideX2 && y >= sideY1 && y < sideY2;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (inSide(x, y)) {
            sideScroll -= delta * 22;
        } else if (x >= infoX1 && x < infoX2 && y >= infoY1 && y < infoY2) {
            infoScroll -= delta * 18;
        } else if (x >= chatX1 && x < chatX2 && y >= chatY1 && y < chatY2) {
            chatScroll += delta * 20;
        }
        return true;
    }

    private Field field() {
        return switch (focus) {
            case ADD_FRIEND -> addField;
            case NEW_GROUP -> groupField;
            default -> chatField;
        };
    }

    /** GLFW key codes (the legacy adapters translate LWJGL 2 keys). True when the key was used. */
    public boolean keyPressed(int key, boolean ctrl) {
        Field f = field();
        switch (key) {
            case Keys.ESCAPE -> {
                if (focus != Focus.CHAT) {
                    focus = Focus.CHAT;
                } else if (picking) {
                    picking = false;
                } else {
                    host.close();
                }
                return true;
            }
            case Keys.ENTER, 335 -> {
                submit();
                return true;
            }
            case Keys.BACKSPACE -> f.backspace(ctrl);
            case Keys.DELETE -> f.delete();
            case Keys.LEFT -> f.cursor = Math.max(0, f.cursor - 1);
            case Keys.RIGHT -> f.cursor = Math.min(f.text.length(), f.cursor + 1);
            case Keys.HOME -> f.cursor = 0;
            case Keys.END -> f.cursor = f.text.length();
            case Keys.PAGE_UP -> chatScroll += 80;
            case Keys.PAGE_DOWN -> chatScroll = Math.max(0, chatScroll - 80);
            case Keys.V -> {
                if (ctrl) {
                    f.insert(host.clipboard().replace('\n', ' '));
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean charTyped(char ch) {
        if (ch < 32 || ch == 127) {
            return false;
        }
        field().insert(String.valueOf(ch));
        return true;
    }

    private void submit() {
        SocialClient c = social.client();
        if (c == null) {
            return;
        }
        switch (focus) {
            case ADD_FRIEND -> {
                String name = addField.value().strip();
                if (!name.isEmpty()) {
                    run(c.addFriend(name), t("Anfrage an ", "Request sent to ") + name + t(" gesendet", ""));
                    addField.clear();
                    focus = Focus.CHAT;
                }
            }
            case NEW_GROUP -> {
                String name = groupField.value().strip();
                if (!name.isEmpty()) {
                    c.createGroup(name, "", "flag", Rules.COLORS.get(2)).whenComplete((id, e) -> {
                        if (e != null) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            toast(cause.getMessage());
                        } else if (id != null) {
                            pendingSelect = Conv.group(id);
                            toast(t("Gruppe „", "Group \"") + name + t("“ erstellt", "\" created"));
                        }
                    });
                    groupField.clear();
                    focus = Focus.CHAT;
                }
            }
            default -> send();
        }
    }
}
