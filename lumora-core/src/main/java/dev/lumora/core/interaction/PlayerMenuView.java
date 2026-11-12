package dev.lumora.core.interaction;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.extras.TabModule;
import dev.lumora.core.interaction.PlayerMenuLayout.Slot;
import dev.lumora.core.interaction.PlayerMenuRegistry.Owned;
import dev.lumora.core.social.SocialView;
import dev.lumora.core.theme.LumoraTheme;
import dev.lumora.social.model.PlayerCard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The player menu on screen: a panel in the middle of the screen with the player's card (head, name, ping and the
 * Lumora profile) on the left and the actions as a list on the right. Held open with the middle mouse button:
 * releasing it over an action runs it, releasing it quickly (a click) leaves the menu open to choose with a click.
 * The mouse wheel and the arrow keys step through the actions. A category opens its actions as a list of their own.
 */
final class PlayerMenuView implements AddonView {
    /** A release within this time after opening counts as a click: the menu stays open. */
    private static final long TAP_MS = 350;
    private static final long FADE_MS = 120;
    /** Panel geometry in GUI pixels (before scaling down on small windows). */
    private static final int PW = 360;
    private static final int PAD = 8;
    private static final int HEAD = 28;
    private static final int LW = 124;
    private static final int ROW = 20;
    private static final int GAP = 4;
    /** The line with what the buttons do, at the bottom of the panel. */
    private static final int FOOT = 12;

    private static final int PANEL = 0xF0110E18;
    private static final int SURFACE = 0xF0181420;
    private static final int ROW_BG = 0xF01D1828;
    private static final int ROW_OFF = 0xC0161220;
    private static final int ROW_BORDER = 0xFF2C2540;
    private static final int BORDER = 0xFF2F2741;
    private static final int TEXT = 0xFFEDEAF5;
    private static final int MUTED = 0xFFA49DB8;
    private static final int DIM = 0xFF6A6480;
    private static final int ONLINE = 0xFF3DD68C;
    private static final int STAR = 0xFFF5C542;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());

    private enum Kind { ENTRY, CATEGORY, BACK }

    /** A row of the list, in panel coordinates. */
    private record Row(Kind kind, Owned entry, Slot<Owned> slot, int x, int y, int w) {
        boolean contains(float px, float py) {
            return px >= x && px < x + w && py >= y && py < y + ROW;
        }
    }

    private final PlayerInteraction owner;
    private final PlayerInteraction.Target target;
    private final long openedAt = System.currentTimeMillis();
    private boolean holding;
    private boolean closed;
    /** The open category (its actions are listed), or null for the main list. */
    private String category;
    private int selected = -1;
    /** Chosen with the wheel or the keys: the mouse decides again once it moves. */
    private boolean keyboard;
    private int keyX;
    private int keyY;
    /** The mouse has moved since the menu opened (until then it rests where the menu happened to open). */
    private boolean moved;
    private int startX = Integer.MIN_VALUE;
    private int startY;
    private List<Row> rows = List.of();
    private float scale = 1;
    private float ox;
    private float oy;
    private int panelH;

    PlayerMenuView(PlayerInteraction owner, PlayerInteraction.Target target, boolean holding) {
        this.owner = owner;
        this.target = target;
        this.holding = holding;
    }

    private static PlayerMenuLayout.Grouping<Owned> grouping() {
        return new PlayerMenuLayout.Grouping<>() {
            @Override
            public String category(Owned e) {
                return e.entry().category();
            }

            @Override
            public String categoryIcon(Owned e) {
                return e.entry().categoryIcon();
            }
        };
    }

    /** Opens the actions of a category (smoke test; normally a click on its row). */
    void openCategory(String name) {
        category = name;
        selected = -1;
    }

    /** The label of the chosen row as of the last frame, or null (tests). */
    String chosen() {
        if (selected < 0 || selected >= rows.size()) {
            return null;
        }
        Row r = rows.get(selected);
        return r.kind == Kind.ENTRY ? owner.registry().label(r.entry, target) : r.kind == Kind.BACK ? "< " + r.slot.category : r.slot.category;
    }

    // ------------------------------------------------------------------ input

    private float localX(double x) {
        return (float) ((x - ox) / scale);
    }

    private float localY(double y) {
        return (float) ((y - oy) / scale);
    }

    private int rowAt(double x, double y) {
        float lx = localX(x);
        float ly = localY(y);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).contains(lx, ly)) {
                return i;
            }
        }
        return -1;
    }

    private boolean onClose(double x, double y) {
        float lx = localX(x);
        float ly = localY(y);
        return lx >= PW - PAD - 16 && lx < PW - PAD && ly >= 6 && ly < 22;
    }

    private boolean inPanel(double x, double y) {
        float lx = localX(x);
        float ly = localY(y);
        return lx >= 0 && lx < PW && ly >= 0 && ly < panelH;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) {
            back();
            return true;
        }
        if (button == 0 || button == 2) {
            holding = false;
            if (onClose(x, y)) {
                close();
                return true;
            }
            int i = rowAt(x, y);
            if (i >= 0) {
                selected = i;
                activate(i);
            } else if (!inPanel(x, y)) {
                close();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 2 && holding) {
            release();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (delta != 0) {
            step(delta < 0 ? 1 : -1);
            keyX = (int) x;
            keyY = (int) y;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        switch (key) {
            case Keys.ENTER -> {
                holding = false;
                if (selected >= 0) {
                    activate(selected);
                }
            }
            case Keys.DOWN -> step(1);
            case Keys.UP -> step(-1);
            case Keys.LEFT, Keys.BACKSPACE -> {
                if (category != null) {
                    back();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void removed() {
        closed = true;
        owner.closed(this);
    }

    private void step(int step) {
        if (rows.isEmpty()) {
            return;
        }
        selected = selected < 0 ? (step > 0 ? 0 : rows.size() - 1) : Math.floorMod(selected + step, rows.size());
        keyboard = true;
        keyX = Integer.MIN_VALUE;
    }

    /** Right click: from a category back to the main list, otherwise closes the menu. */
    private void back() {
        if (category != null) {
            category = null;
            selected = -1;
        } else {
            close();
        }
    }

    /** The middle button went up while held. */
    void release() {
        holding = false;
        if (System.currentTimeMillis() - openedAt < TAP_MS) {
            return; // a click: stays open to choose with a click or the wheel
        }
        if (selected < 0 || selected >= rows.size()) {
            close();
            return;
        }
        Row r = rows.get(selected);
        if (r.kind != Kind.ENTRY || owner.registry().enabled(r.entry, target)) {
            activate(selected);
        } // a greyed out action: stays open
    }

    /** Runs the action of a row, opens a category or goes back to the main list. */
    private void activate(int i) {
        if (i < 0 || i >= rows.size()) {
            return;
        }
        Row r = rows.get(i);
        switch (r.kind) {
            case BACK -> {
                category = null;
                selected = keyboard ? 0 : -1;
            }
            case CATEGORY -> {
                category = r.slot.category;
                selected = keyboard ? 1 : -1;
            }
            case ENTRY -> {
                if (!owner.registry().enabled(r.entry, target)) {
                    return;
                }
                if (r.entry.entry().keepsMenuOpen()) {
                    owner.registry().select(r.entry, target);
                    return;
                }
                close();
                owner.registry().select(r.entry, target);
            }
        }
    }

    void close() {
        if (!closed) {
            closed = true;
            owner.close(this);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
        if (holding && !owner.middleDown()) {
            release();
        }
        if (closed) {
            return;
        }
        PlayerMenuLayout<Owned> layout = PlayerMenuLayout.of(owner.registry().visible(target), grouping());
        Slot<Owned> open = layout.category(category);
        if (open == null) {
            category = null;
        }
        List<String[]> info = info();
        int listH = Math.max(ROW, (open == null ? layout.size() : open.entries.size() + 1) * (ROW + GAP) - GAP);
        int cardH = Math.max(listH, cardHeight(info, target.multiplayer()));
        panelH = HEAD + PAD + cardH + PAD + FOOT;

        // smaller on small windows; a short zoom in when it opens
        float fit = Math.min(1f, Math.min((width - 16f) / PW, (height - 16f) / panelH));
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) FADE_MS);
        float ease = 1 - (1 - t) * (1 - t);
        scale = fit * (0.94f + 0.06f * ease);
        ox = (width - PW * scale) / 2f;
        oy = (height - panelH * scale) / 2f;

        int lx = PAD + LW + PAD;
        int lw = PW - lx - PAD;
        List<Row> list = new ArrayList<>();
        int y = HEAD + PAD;
        if (open == null) {
            for (Slot<Owned> s : layout.slots()) {
                list.add(new Row(s.isCategory() ? Kind.CATEGORY : Kind.ENTRY, s.isCategory() ? null : s.entries.get(0), s, lx, y, lw));
                y += ROW + GAP;
            }
        } else {
            list.add(new Row(Kind.BACK, null, open, lx, y, lw));
            y += ROW + GAP;
            for (Owned e : open.entries) {
                list.add(new Row(Kind.ENTRY, e, open, lx, y, lw));
                y += ROW + GAP;
            }
        }
        rows = list;

        if (startX == Integer.MIN_VALUE) {
            startX = mouseX;
            startY = mouseY;
        } else if (Math.abs(mouseX - startX) + Math.abs(mouseY - startY) > 2) {
            moved = true;
        }
        if (keyboard && keyX == Integer.MIN_VALUE) {
            keyX = mouseX;
            keyY = mouseY;
        }
        if (keyboard && Math.abs(mouseX - keyX) + Math.abs(mouseY - keyY) > 3) {
            keyboard = false;
        }
        if (!keyboard) {
            selected = moved ? rowAt(mouseX, mouseY) : -1;
        }
        if (selected >= rows.size()) {
            selected = -1;
        }

        g.fill(0, 0, width, height, 0x50000000);
        g.push();
        g.translate(ox, oy);
        g.scale(scale);
        g.card(0, 0, PW, panelH, 6, PANEL, accent(0x90));
        drawHeader(g, onClose(mouseX, mouseY));
        drawCard(g, PAD, HEAD + PAD, LW, cardH, info);
        if (rows.isEmpty()) {
            g.text(owner.t("Keine Aktionen", "No actions"), lx + 8, HEAD + PAD + 6, MUTED, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            drawRow(g, rows.get(i), i == selected);
        }
        String hint = holding ? owner.t("Loslassen: ausführen · Mausrad: wählen", "Release: run · Wheel: choose")
                : owner.t("Klick: ausführen · Rechtsklick: zurück · Esc: schließen", "Click: run · Right click: back · Esc: close");
        g.push();
        g.translate(PW / 2f, panelH - FOOT);
        g.scale(0.75f);
        g.text(hint, -g.textWidth(hint) / 2, 0, DIM, false);
        g.pop();
        g.pop();
    }

    private void drawHeader(HudGraphics g, boolean closeHover) {
        g.roundedRect(PAD, 6, 16, 16, 4, accent(0x50));
        g.centeredText("☺", PAD + 8, 10, TEXT, false);
        g.text(owner.t("Spieler-Interaktionen", "Player interactions"), PAD + 22, 10, TEXT, false);
        int cx = PW - PAD - 16;
        g.card(cx, 6, 16, 16, 4, closeHover ? 0xFF2A2338 : 0x00000000, closeHover ? accent(0xFF) : BORDER);
        g.centeredText("✕", cx + 8, 10, closeHover ? TEXT : MUTED, false);
        g.fill(PAD, HEAD - 1, PW - PAD, HEAD, BORDER);
    }

    // ------------------------------------------------------------------ the player card

    /** The rows of the card below the name: label and value (value null: one line of text). */
    private List<String[]> info() {
        List<String[]> rows = new ArrayList<>();
        if (owner.client() == null) {
            return rows;
        }
        PlayerCard c = target.card();
        if (!owner.socialOnline()) {
            rows.add(new String[] {"Lumora", "offline"});
        } else if (c == null) {
            rows.add(new String[] {"Lumora", "…"});
        } else if (!c.lumora) {
            rows.add(new String[] {"Lumora", owner.t("nein", "no")});
        } else {
            rows.add(new String[] {"Lumora", owner.t("ja", "yes")});
            if (c.friend) {
                rows.add(new String[] {owner.t("Freund seit", "Friends since"), c.friendSince > 0 ? DATE.format(Instant.ofEpochMilli(c.friendSince)) : "–"});
            } else {
                rows.add(new String[] {owner.t("Freund", "Friend"), c.request == PlayerCard.Request.INCOMING ? owner.t("fragt an", "asks you")
                        : c.request == PlayerCard.Request.OUTGOING ? owner.t("angefragt", "requested") : owner.t("nein", "no")});
            }
            rows.add(new String[] {owner.t("Gruppen", "Groups"), c.groups.isEmpty() ? "–" : String.valueOf(c.groups.size())});
            if (c.friend && c.presence != null) {
                rows.add(new String[] {owner.presence(c.presence), null});
            }
        }
        return rows;
    }

    private static int cardHeight(List<String[]> info, boolean ping) {
        // head, name, badge, separator, online (and ping on servers), then the Lumora rows
        int h = 8 + 40 + 6 + 10 + 14 + 6 + (ping ? 2 : 1) * 12 + 4;
        if (!info.isEmpty()) {
            h += 6 + info.size() * 12;
        }
        return h + 4;
    }

    private void drawCard(HudGraphics g, int x, int y, int w, int h, List<String[]> info) {
        g.card(x, y, w, h, 5, SURFACE, BORDER);
        PlayerCard c = target.card();
        int fx = x + (w - 40) / 2;
        g.roundedRect(fx - 2, y + 6, 44, 44, 4, 0xFF0D0A13);
        SocialView.face(g, target.uuid32(), target.name(), fx, y + 8, 40);
        if (c != null && c.friend) {
            g.roundedRect(x + w - 17, y + 5, 12, 12, 3, 0xFF2A2338);
            g.centeredText("★", x + w - 11, y + 7, STAR, false);
        }
        int yy = y + 54;
        g.centeredText(fit(g, target.name(), w - 10), x + w / 2, yy, TEXT, false);
        yy += 12;
        String badge = c == null ? null : c.friend ? owner.t("Freund", "Friend") : c.lumora ? "Lumora" : null;
        if (badge != null) {
            int bw = g.textWidth(badge) + 12;
            g.roundedRect(x + (w - bw) / 2, yy, bw, 11, 3, accent(0x60));
            g.centeredText(badge, x + w / 2, yy + 2, TEXT, false);
        }
        yy += 16;
        g.fill(x + 8, yy, x + w - 8, yy + 1, BORDER);
        yy += 6;
        // online and ping
        g.roundedRect(x + 9, yy + 1, 6, 6, 3, ONLINE);
        g.text(target.multiplayer() ? "Online" : owner.t("In deiner Welt", "In your world"), x + 21, yy, TEXT, false);
        yy += 12;
        if (target.multiplayer()) {
            int ping = owner.latency(target);
            bars(g, x + 8, yy, ping);
            g.text(ping < 0 ? "–" : ping + " ms", x + 21, yy, ping < 0 ? DIM : TEXT, false);
            yy += 12;
        }
        if (info.isEmpty()) {
            return;
        }
        yy += 4;
        g.fill(x + 8, yy, x + w - 8, yy + 1, BORDER);
        yy += 6;
        for (String[] r : info) {
            if (r[1] == null) {
                g.text(fit(g, r[0], w - 16), x + 8, yy, MUTED, false);
            } else {
                int vw = g.textWidth(r[1]);
                g.text(fit(g, r[0], w - 22 - vw), x + 8, yy, MUTED, false);
                g.rightText(r[1], x + w - 8, yy, TEXT, false);
            }
            yy += 12;
        }
    }

    /** Four signal bars, filled by ping like the tab list. */
    private static void bars(HudGraphics g, int x, int y, int ping) {
        int filled = ping < 0 ? 0 : ping < 80 ? 4 : ping < 150 ? 3 : ping < 300 ? 2 : 1;
        int on = ping < 0 ? DIM : TabModule.pingColor(ping);
        for (int i = 0; i < 4; i++) {
            int bh = 2 + i * 2;
            g.fill(x + i * 2, y + 8 - bh, x + i * 2 + 1, y + 8, i < filled ? on : 0xFF3A334C);
        }
    }

    // ------------------------------------------------------------------ the list

    private void drawRow(HudGraphics g, Row r, boolean chosen) {
        boolean enabled = r.kind != Kind.ENTRY || owner.registry().enabled(r.entry, target);
        int fill = chosen ? (enabled ? accent(0x48) : 0xE6262030) : r.kind == Kind.BACK ? SURFACE : enabled ? ROW_BG : ROW_OFF;
        int border = chosen && enabled ? accent(0xFF) : ROW_BORDER;
        g.card(r.x, r.y, r.w, ROW, 4, fill, border);
        int ty = r.y + (ROW - 8) / 2;
        int iconColor = !enabled ? DIM : chosen ? TEXT : LumoraTheme.mix(LumoraTheme.accent(), 0xFFFFFFFF, 0.45f);
        int right = r.x + r.w - 8;
        switch (r.kind) {
            case BACK -> {
                g.text("<", r.x + 9, ty, chosen ? TEXT : MUTED, false);
                g.text(fit(g, r.slot.category, r.w - 34), r.x + 26, ty, accent(0xFF), false);
            }
            case CATEGORY -> {
                icon(g, r.slot.icon != null ? r.slot.icon : r.slot.category.substring(0, 1), r.x + 13, r.y + ROW / 2, iconColor);
                String count = String.valueOf(r.slot.entries.size());
                int cw = g.textWidth(count) + 8;
                g.roundedRect(right - 10 - cw, r.y + 5, cw, 10, 3, 0xFF2A2338);
                g.centeredText(count, right - 10 - cw / 2, r.y + 6, MUTED, false);
                g.text(fit(g, r.slot.category, r.w - 46 - cw), r.x + 26, ty, TEXT, false);
                g.rightText(">", right, ty, chosen ? TEXT : MUTED, false);
            }
            case ENTRY -> {
                Owned e = r.entry;
                if (e.entry().texture() != null) {
                    g.texture(e.entry().texture(), r.x + 7, r.y + 4, 12, 12, 16, 16);
                } else {
                    String label = owner.registry().label(e, target);
                    icon(g, e.entry().icon() != null ? e.entry().icon() : label.isEmpty() ? "?" : label.substring(0, 1), r.x + 13, r.y + ROW / 2, iconColor);
                }
                label(g, owner.registry().label(e, target), r.x + 26, ty, r.w - 44, enabled);
                g.rightText(">", right, ty, !enabled ? DIM : chosen ? TEXT : MUTED, false);
            }
        }
    }

    /** The label; a reason after " · " (e.g. "· not connected") in a dimmer color. */
    private static void label(HudGraphics g, String label, int x, int y, int max, boolean enabled) {
        int dot = label.indexOf(" · ");
        String main = dot < 0 ? label : label.substring(0, dot);
        String main2 = fit(g, main, max);
        g.text(main2, x, y, enabled ? TEXT : DIM, false);
        if (dot >= 0 && main2.equals(main)) {
            int mw = g.textWidth(main);
            String reason = fit(g, label.substring(dot), max - mw);
            if (!reason.isEmpty()) {
                g.text(reason, x + mw, y, DIM, false);
            }
        }
    }

    private static void icon(HudGraphics g, String s, int cx, int cy, int color) {
        g.push();
        g.translate(cx, cy);
        g.scale(1.25f);
        g.text(s, -g.textWidth(s) / 2, -4, color, false);
        g.pop();
    }

    private static String fit(HudGraphics g, String s, int max) {
        if (g.textWidth(s) <= max) {
            return s;
        }
        String r = s;
        while (!r.isEmpty() && g.textWidth(r + "…") > max) {
            r = r.substring(0, r.length() - 1);
        }
        return r.isEmpty() ? "" : r.strip() + "…";
    }

    private static int accent(int alpha) {
        return (alpha << 24) | (LumoraTheme.accent() & 0xFFFFFF);
    }
}
