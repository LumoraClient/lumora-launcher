package dev.lumora.core.interaction;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.interaction.PlayerMenuLayout.Selection;
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
 * The player menu on screen: a ring around the player in the middle of the screen, the player's card above it and
 * the label of the chosen entry below it. Held open with the middle mouse button: releasing it over an entry runs the
 * entry, releasing it quickly (a click) leaves the menu open to choose with a click. The mouse wheel steps through
 * the entries. A category opens its entries as an outer ring; "Client-Profil" shows the profile next to the ring.
 */
final class PlayerMenuView implements AddonView {
    /** Sub-pixels per GUI pixel of the ring (smoother edges). */
    private static final int Q = 2;
    /** A release within this time after opening counts as a click: the menu stays open. */
    private static final long TAP_MS = 350;
    private static final int SEGMENT = 0xE0141019;
    private static final int SEGMENT_OPEN = 0xE8241E33;
    private static final int TEXT = 0xFFEDEAF5;
    private static final int MUTED = 0xFFA49DB8;
    private static final int DIM = 0xFF6A6480;
    private static final int CARD = 0xE6141019;
    private static final int BORDER = 0xFF2E2840;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

    private final PlayerInteraction owner;
    private final PlayerInteraction.Target target;
    private final long openedAt = System.currentTimeMillis();
    private PlayerMenuLayout<Owned> layout = PlayerMenuLayout.of(List.of(), grouping());
    private Selection selection = Selection.NONE;
    private int open = -1;
    private boolean holding;
    private boolean closed;
    private boolean profile;
    private boolean wheel;
    private int wheelX;
    private int wheelY;
    private int[] rootRuns;
    private int rootKey = -1;
    private int[] childRuns;
    private long childKey = -1;

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

    private void refresh() {
        layout = PlayerMenuLayout.of(owner.registry().visible(target), grouping());
    }

    /** "Client-Profil": shows or hides the profile next to the ring. */
    void toggleProfile() {
        profile = !profile;
    }

    boolean profileShown() {
        return profile;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) {
            if (open >= 0) {
                open = -1;
                selection = Selection.NONE;
            } else {
                close();
            }
            return true;
        }
        if (button == 0 || button == 2) {
            holding = false;
            choose(true);
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
        refresh();
        List<Selection> order = layout.order();
        if (order.isEmpty() || delta == 0) {
            return true;
        }
        int i = order.indexOf(selection);
        int step = delta < 0 ? 1 : -1;
        i = i < 0 ? (step > 0 ? 0 : order.size() - 1) : Math.floorMod(i + step, order.size());
        selection = order.get(i);
        open = selection.child() >= 0 ? selection.slot() : -1;
        wheel = true;
        wheelX = (int) x;
        wheelY = (int) y;
        return true;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key == dev.lumora.api.input.Keys.ENTER) {
            holding = false;
            choose(true);
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        closed = true;
        owner.closed(this);
    }

    /** The middle button went up while held. */
    void release() {
        holding = false;
        Owned e = layout.entry(selection);
        if (e != null && owner.registry().enabled(e, target)) {
            choose(false);
            return;
        }
        boolean category = !selection.none() && selection.child() < 0 && layout.slots().get(selection.slot()).isCategory();
        if (category || System.currentTimeMillis() - openedAt < TAP_MS) {
            return; // stays open: choose with a click or the wheel
        }
        close();
    }

    /** Runs the chosen entry; a category opens; a click on nothing closes the menu. */
    private void choose(boolean click) {
        Owned e = layout.entry(selection);
        if (e == null) {
            if (!selection.none() && layout.slots().get(selection.slot()).isCategory()) {
                open = selection.slot();
            } else if (click) {
                close();
            }
            return;
        }
        if (!owner.registry().enabled(e, target)) {
            return;
        }
        if (e.entry().keepsMenuOpen()) {
            owner.registry().select(e, target);
            return;
        }
        close();
        owner.registry().select(e, target);
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
        refresh();
        int cx = width / 2;
        int cy = height / 2;
        boolean categories = layout.slots().stream().anyMatch(Slot::isCategory);
        int reach = categories ? PlayerMenuLayout.CHILD_OUTER : PlayerMenuLayout.OUTER;
        // card above and label below the ring must fit: smaller on small windows
        float s = Math.min(1f, (height / 2f - 4) / (reach + 46));
        if (wheel && Math.abs(mouseX - wheelX) + Math.abs(mouseY - wheelY) > 3) {
            wheel = false;
        }
        if (!wheel) {
            selection = layout.hit((mouseX - cx) / s, (mouseY - cy) / s, open);
            if (selection.none()) {
                open = -1;
            } else if (selection.child() < 0) {
                open = layout.slots().get(selection.slot()).isCategory() ? selection.slot() : -1;
            }
        }
        if (selection.slot() >= layout.size()) {
            selection = Selection.NONE;
            open = -1;
        }
        g.push();
        g.translate(cx, cy);
        g.scale(s);
        drawRing(g, 0, 0);
        drawCard(g, 0, -reach - 30);
        drawLabel(g, 0, reach + 8);
        if (profile) {
            drawProfile(g, reach, (int) (width / 2f / s));
        }
        g.pop();
    }

    private void drawRing(HudGraphics g, int cx, int cy) {
        int n = layout.size();
        if (n == 0) {
            g.centeredText(owner.t("Keine Aktionen", "No actions"), cx, cy - 4, MUTED, true);
            return;
        }
        if (rootRuns == null || rootKey != n) {
            double w = layout.slotWidth();
            rootRuns = PlayerMenuLayout.runs(PlayerMenuLayout.INNER, PlayerMenuLayout.OUTER, Q, -w / 2, w, n, 2.0);
            rootKey = n;
        }
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            Slot<Owned> s = layout.slots().get(i);
            boolean chosen = selection.slot() == i && selection.child() < 0;
            boolean enabled = s.isCategory() || owner.registry().enabled(s.entries.get(0), target);
            colors[i] = chosen ? (enabled ? accent(0xE6) : 0xC0484058) : i == open ? SEGMENT_OPEN : enabled ? SEGMENT : 0xA0141019;
        }
        fillRuns(g, rootRuns, colors, cx, cy);
        if (open >= 0 && open < n && layout.slots().get(open).isCategory()) {
            Slot<Owned> s = layout.slots().get(open);
            int m = s.entries.size();
            long key = ((long) n << 32) | ((long) open << 16) | m;
            if (childRuns == null || childKey != key) {
                childRuns = PlayerMenuLayout.runs(PlayerMenuLayout.CHILD_INNER, PlayerMenuLayout.CHILD_OUTER, Q,
                        layout.childStart(open), PlayerMenuLayout.childWidth(m), m, 2.0);
                childKey = key;
            }
            int[] cc = new int[m];
            for (int j = 0; j < m; j++) {
                boolean enabled = owner.registry().enabled(s.entries.get(j), target);
                cc[j] = selection.slot() == open && selection.child() == j ? (enabled ? accent(0xE6) : 0xC0484058)
                        : enabled ? SEGMENT : 0xA0141019;
            }
            fillRuns(g, childRuns, cc, cx, cy);
            double mid = (PlayerMenuLayout.CHILD_INNER + PlayerMenuLayout.CHILD_OUTER) / 2.0;
            for (int j = 0; j < m; j++) {
                icon(g, s.entries.get(j), layout.childCenter(open, j), mid, cx, cy);
            }
        }
        double mid = (PlayerMenuLayout.INNER + PlayerMenuLayout.OUTER) / 2.0;
        for (int i = 0; i < n; i++) {
            Slot<Owned> s = layout.slots().get(i);
            if (s.isCategory()) {
                String icon = s.icon != null ? s.icon : s.category.substring(0, 1);
                glyph(g, icon, layout.center(i), mid, cx, cy, TEXT);
                // small dots: how many entries the category holds
                double a = layout.center(i);
                int dx = (int) Math.round(cx + Math.sin(a) * (PlayerMenuLayout.OUTER - 6));
                int dy = (int) Math.round(cy - Math.cos(a) * (PlayerMenuLayout.OUTER - 6));
                g.fill(dx - 1, dy - 1, dx + 1, dy + 1, MUTED);
            } else {
                icon(g, s.entries.get(0), layout.center(i), mid, cx, cy);
            }
        }
    }

    private void icon(HudGraphics g, Owned e, double angle, double radius, int cx, int cy) {
        boolean enabled = owner.registry().enabled(e, target);
        if (e.entry().texture() != null) {
            int x = (int) Math.round(cx + Math.sin(angle) * radius) - 6;
            int y = (int) Math.round(cy - Math.cos(angle) * radius) - 6;
            g.texture(e.entry().texture(), x, y, 12, 12, 16, 16);
            return;
        }
        String icon = e.entry().icon() != null ? e.entry().icon() : owner.registry().label(e, target).substring(0, 1);
        glyph(g, icon, angle, radius, cx, cy, enabled ? TEXT : DIM);
    }

    private static void glyph(HudGraphics g, String s, double angle, double radius, int cx, int cy, int color) {
        float scale = 1.5f;
        double x = cx + Math.sin(angle) * radius;
        double y = cy - Math.cos(angle) * radius;
        g.push();
        g.translate((float) x, (float) y);
        g.scale(scale);
        g.text(s, -g.textWidth(s) / 2, -4, color, true);
        g.pop();
    }

    private static void fillRuns(HudGraphics g, int[] runs, int[] colors, int cx, int cy) {
        g.push();
        g.scale(1f / Q);
        int ox = cx * Q;
        int oy = cy * Q;
        for (int i = 0; i + 3 < runs.length; i += 4) {
            int seg = runs[i + 3];
            if (seg < colors.length) {
                g.fill(ox + runs[i + 1], oy + runs[i], ox + runs[i + 2], oy + runs[i] + 1, colors[seg]);
            }
        }
        g.pop();
    }

    private static int accent(int alpha) {
        return (alpha << 24) | (LumoraTheme.accent() & 0xFFFFFF);
    }

    /** Head, name and how the two of you are connected, above the ring. */
    private void drawCard(HudGraphics g, int cx, int y) {
        String name = target.name();
        String status = owner.status(target);
        int w = Math.max(g.textWidth(name), g.textWidth(status)) + 34;
        int x = cx - w / 2;
        g.card(x, y, w, 24, 5, CARD, BORDER);
        SocialView.face(g, target.uuid32(), name, x + 4, y + 4, 16);
        g.text(name, x + 26, y + 3, TEXT, false);
        g.text(status, x + 26, y + 13, MUTED, false);
    }

    /** Label of the chosen entry and what the buttons do, below the ring. */
    private void drawLabel(HudGraphics g, int cx, int y) {
        String label = null;
        boolean enabled = true;
        Owned e = layout.entry(selection);
        if (e != null) {
            label = owner.registry().label(e, target);
            enabled = owner.registry().enabled(e, target);
        } else if (!selection.none()) {
            label = layout.slots().get(selection.slot()).category;
        }
        int ly = y;
        if (label != null) {
            int w = g.textWidth(label) + 16;
            g.card(cx - w / 2, ly, w, 16, 5, CARD, enabled ? accent(0xFF) : BORDER);
            g.centeredText(label, cx, ly + 4, enabled ? TEXT : DIM, false);
        }
        String hint = holding ? owner.t("Loslassen: ausführen · Mausrad: wählen", "Release: run · Wheel: choose")
                : owner.t("Klick: ausführen · Rechtsklick: zurück", "Click: run · Right click: back");
        g.push();
        g.translate(cx, ly + 20);
        g.scale(0.75f);
        g.text(hint, -g.textWidth(hint) / 2, 0, DIM, true);
        g.pop();
    }

    /** The profile card ("Client-Profil") next to the ring. */
    private void drawProfile(HudGraphics g, int reach, int halfWidth) {
        List<String[]> rows = new ArrayList<>();
        PlayerCard c = target.card();
        if (!owner.socialOnline()) {
            rows.add(new String[] {owner.t("Lumora-Server nicht verbunden", "Lumora server not connected"), null});
        } else if (c == null) {
            rows.add(new String[] {owner.t("Lädt …", "Loading …"), null});
        } else if (!c.lumora) {
            rows.add(new String[] {owner.t("Nutzt Lumora nicht", "Does not use Lumora"), null});
        } else {
            rows.add(new String[] {owner.t("Lumora-Nutzer", "Lumora user"), owner.t("ja", "yes")});
            if (c.friend) {
                rows.add(new String[] {owner.t("Freund seit", "Friends since"), c.friendSince > 0 ? DATE.format(Instant.ofEpochMilli(c.friendSince)) : "–"});
                if (c.presence != null) {
                    rows.add(new String[] {owner.t("Status", "Status"), owner.presence(c.presence)});
                }
            } else {
                rows.add(new String[] {owner.t("Freund", "Friend"), c.request == PlayerCard.Request.INCOMING ? owner.t("fragt an", "asks you")
                        : c.request == PlayerCard.Request.OUTGOING ? owner.t("angefragt", "requested") : owner.t("nein", "no")});
            }
            String party = c.sameParty ? owner.t("in deiner Party", "in your party") : c.partyInvite != null
                    ? owner.t("lädt dich ein", "invites you") : c.invited ? owner.t("eingeladen", "invited") : "–";
            rows.add(new String[] {"Party", party});
            rows.add(new String[] {owner.t("Gemeinsame Gruppen", "Shared groups"), c.groups.isEmpty() ? "–" : String.valueOf(c.groups.size())});
            for (String gr : c.groups.subList(0, Math.min(4, c.groups.size()))) {
                rows.add(new String[] {"  " + gr, null});
            }
        }
        int pw = 0;
        for (String[] r : rows) {
            pw = Math.max(pw, g.textWidth(r[0]) + (r[1] == null ? 0 : g.textWidth(r[1]) + 12));
        }
        pw += 16;
        int ph = 22 + rows.size() * 11;
        int x = reach + 12;
        if (x + pw > halfWidth - 4) {
            x = Math.max(-halfWidth + 4, -reach - 12 - pw);
        }
        int y = -ph / 2;
        g.card(x, y, pw, ph, 5, CARD, BORDER);
        g.text(owner.t("Client-Profil", "Client profile"), x + 8, y + 6, accent(0xFF), false);
        int yy = y + 19;
        for (String[] r : rows) {
            g.text(r[0], x + 8, yy, r[1] == null ? MUTED : TEXT, false);
            if (r[1] != null) {
                g.rightText(r[1], x + pw - 8, yy, MUTED, false);
            }
            yy += 11;
        }
    }
}
