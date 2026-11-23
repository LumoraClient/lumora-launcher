package dev.lumora.core.chat.windows;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * The window for a new chat tab in three steps: what it collects (suggestions found in the chat or a rule of one's
 * own), what happens with matching messages (keep them in the main chat, sound, highlight, with a preview) and its
 * name and place (this window or a window of its own). Editing a tab opens the same window with everything filled in.
 */
final class TabWizard implements AddonView {
    private static final int W = 300;
    private static final int PAD = 12;
    private static final int TEXT = 0xFFE8EEFA;
    private static final int MUTED = 0xFF8D9BB8;
    private static final int DIM = 0xFF5D6878;
    private static final int CARD = 0xFF181D27;
    private static final int PANEL = 0xF2131821;
    private static final int ROW = 0xFF202633;
    private static final int ROW_HOVER = 0xFF283041;
    private static final int BORDER = 0xFF2C3444;
    private static final int FIELD = 0xFF0B0F15;
    private static final int DANGER = 0xFFD03755;
    private static final int[] COLORS = {0xFFE8B84A, 0xFF5BC0EB, 0xFF7CE38B, 0xFFF0506E, 0xFFB57CF0, 0xFFF59342};

    private final ChatWindows windows;
    private final ChatWindows.Host host;
    private final ChatTab edit;
    private final ChatWindow window;
    private final ChatEntry from;
    private int step;
    private ChatRule rule;
    private final ChatTab draft;
    private boolean newWindow;
    private final TextBox value = new TextBox(64);
    private final TextBox name = new TextBox(24);
    private TextBox focus;
    private boolean nameEdited;
    private int listScroll;
    private int listMax;
    private final long openedAt = System.currentTimeMillis();
    private final List<Region> regions = new ArrayList<>();
    private List<ChatSuggestions.Suggestion> recent;
    private List<ChatSuggestions.Suggestion> fromMessage;

    private record Region(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A one-line text field: typing appends, Backspace deletes, Ctrl+Backspace clears, Ctrl+V pastes. */
    static final class TextBox {
        final int max;
        final StringBuilder text = new StringBuilder();

        TextBox(int max) {
            this.max = max;
        }

        String get() {
            return text.toString();
        }

        void set(String s) {
            text.setLength(0);
            append(s);
        }

        void append(String s) {
            for (char c : (s == null ? "" : s).toCharArray()) {
                if (text.length() < max && c >= 32 && c != 127 && !Character.isISOControl(c)) {
                    text.append(c);
                }
            }
        }

        boolean key(int key, boolean ctrl, String clipboard) {
            if (key == Keys.BACKSPACE) {
                if (ctrl) {
                    text.setLength(0);
                } else if (!text.isEmpty()) {
                    text.setLength(text.length() - 1);
                }
                return true;
            }
            if (ctrl && key == Keys.V) {
                append(clipboard == null ? "" : clipboard.replace('\n', ' '));
                return true;
            }
            return false;
        }
    }

    TabWizard(ChatWindows windows, ChatTab edit, ChatWindow window, ChatEntry from) {
        this.windows = windows;
        this.host = windows.host();
        this.edit = edit;
        this.window = window == null ? windows.layout().main() : window;
        this.from = from;
        if (edit != null) {
            draft = edit.copy();
            rule = edit.rule;
            value.set(rule.value());
            name.set(edit.main() && edit.name.isEmpty() ? host.tr("tabs.main") : edit.name);
            nameEdited = true;
            ChatWindow w = windows.layout().windowOf(edit);
            newWindow = w != null && !w.main();
            step = edit.main() ? 2 : 1;
        } else {
            draft = new ChatTab("", "", ChatRule.ALL);
            rule = null;
            step = 0;
        }
    }

    private String tr(String key, Object... args) {
        return host.tr("tabs." + key, args);
    }

    // --- rules as text

    private String ruleTitle(ChatRule r) {
        return switch (r.kind()) {
            case MENTION -> tr("rule.mention");
            case SERVER -> tr("rule.server");
            case ALL -> tr("rule.all");
            default -> r.value().isEmpty() ? tr("rule." + r.kind().id) : r.value();
        };
    }

    private String ruleText(ChatRule r) {
        String v = r.value();
        return switch (r.kind()) {
            case PLAYER -> v.isEmpty() ? tr("rule.player.choose") : tr("rule.player.desc", v);
            case PREFIX -> v.isEmpty() ? tr("rule.prefix.choose") : tr("rule.prefix.desc", v);
            case RANK -> v.isEmpty() ? tr("rule.rank.choose") : tr("rule.rank.desc", v);
            case CONTAINS -> v.isEmpty() ? tr("rule.contains.choose") : tr("rule.contains.desc", v);
            case MENTION -> tr("rule.mention.desc", host.own().isEmpty() ? "Steve" : host.own());
            case SERVER -> tr("rule.server.desc");
            case ALL -> tr("rule.all.desc");
        };
    }

    private String defaultName(ChatRule r) {
        String v = r.value();
        return switch (r.kind()) {
            case PLAYER, RANK -> v;
            case PREFIX -> ChatAnalysis.bare(v);
            case CONTAINS -> ChatRule.words(v).isEmpty() ? "" : ChatRule.words(v).get(0);
            case MENTION -> tr("name.mentions");
            case SERVER -> tr("name.server");
            case ALL -> tr("name.all");
        };
    }

    private void choose(ChatRule r) {
        rule = r;
        value.set(r.value());
        if (!nameEdited) {
            name.set(defaultName(r));
        }
        draft.keepInMain = true;
        step = 1;
        focus = r.kind().needsValue && r.value().isEmpty() ? value : null;
    }

    private ChatRule currentRule() {
        return rule == null ? ChatRule.ALL : new ChatRule(rule.kind(), value.get());
    }

    // --- drawing

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        regions.clear();
        g.fill(0, 0, width, height, 0x90000000);
        int w = Math.min(W, width - 16);
        int contentH = switch (step) {
            case 0 -> Math.min(listHeight(g, w), Math.max(80, height - 110));
            case 1 -> adjustHeight();
            default -> placeHeight();
        };
        int h = 64 + contentH + 34;
        int x = (width - w) / 2;
        int y = Math.max(6, (height - h) / 2);
        int accent = LumoraTheme.accent();
        g.card(x, y, w, h, 6, PANEL, BORDER);

        // title, subtitle, close
        String title = step == 2 ? (edit != null ? tr("edit_title") : tr("new_title")) : tr("title");
        g.push();
        g.translate(x + PAD, y + 10);
        g.scale(1.25f);
        g.text(title, 0, 0, TEXT, false);
        g.pop();
        String subtitle = tr("subtitle");
        float sub = Math.min(1f, (w - 2f * PAD - 18) / Math.max(1, g.textWidth(subtitle)));
        g.push();
        g.translate(x + PAD, y + 24);
        g.scale(Math.max(0.6f, sub));
        g.text(subtitle, 0, 0, MUTED, false);
        g.pop();
        int cx = x + w - PAD - 14;
        boolean overClose = UiHover.in(mx, my, cx, y + 8, 14, 14);
        g.card(cx, y + 8, 14, 14, 3, overClose ? ROW_HOVER : ROW, BORDER);
        ChatIcons.cross(g, cx + 4, y + 12, TEXT);
        regions.add(new Region(cx, y + 8, 14, 14, this::close));

        // steps
        int sy = y + 38;
        int sw = (w - 2 * PAD - 8) / 3;
        String[] labels = {tr("step.start"), tr("step.adjust"), tr("step.place")};
        for (int i = 0; i < 3; i++) {
            int sx = x + PAD + i * (sw + 4);
            g.fill(sx, sy, sx + sw, sy + 2, i <= step ? accent : 0xFF2A3140);
            g.text(fit(g, labels[i], sw), sx, sy + 5, i == step ? TEXT : DIM, false);
        }
        int top = y + 60;
        switch (step) {
            case 0 -> renderStart(g, x, top, w, contentH, mx, my);
            case 1 -> renderAdjust(g, x, top, w, mx, my);
            default -> renderPlace(g, x, top, w, mx, my);
        }
        renderFooter(g, x, y + h - 26, w, mx, my);
    }

    private static String fit(HudGraphics g, String s, int max) {
        return ChatWindows.fit(g, s, max);
    }

    private void renderFooter(HudGraphics g, int x, int y, int w, int mx, int my) {
        int right = x + w - PAD;
        if (step == 0) {
            right = button(g, right, y, tr("cancel"), false, mx, my, this::close);
            return;
        }
        String primary = step == 1 ? tr("next") : edit != null ? tr("save") : tr("create");
        boolean ok = step == 1 ? currentRule().complete() : !name.get().strip().isEmpty();
        right = button(g, right, y, primary, true, ok, mx, my, () -> {
            if (step == 1) {
                if (currentRule().complete()) {
                    step = 2;
                    focus = null;
                }
            } else {
                finish();
            }
        });
        right = button(g, right - 6, y, tr("cancel"), false, mx, my, this::close);
        boolean canBack = edit == null || !edit.main();
        if (canBack) {
            int bw = g.textWidth(tr("back")) + 16;
            boolean over = UiHover.in(mx, my, x + PAD, y, bw, 16);
            g.card(x + PAD, y, bw, 16, 4, over ? ROW_HOVER : ROW, BORDER);
            g.text(tr("back"), x + PAD + 8, y + 4, TEXT, false);
            regions.add(new Region(x + PAD, y, bw, 16, () -> {
                step--;
                focus = null;
            }));
        }
        if (step == 2 && edit != null && !edit.main()) {
            int bx = x + PAD + (canBack ? g.textWidth(tr("back")) + 22 : 0);
            String d = tr("delete");
            int bw = g.textWidth(d) + 16;
            boolean over = UiHover.in(mx, my, bx, y, bw, 16);
            g.card(bx, y, bw, 16, 4, over ? DANGER : 0xFF3A1F27, DANGER);
            g.text(d, bx + 8, y + 4, TEXT, false);
            regions.add(new Region(bx, y, bw, 16, () -> {
                windows.delete(edit);
                close();
            }));
        }
    }

    private int button(HudGraphics g, int right, int y, String label, boolean primary, int mx, int my, Runnable action) {
        return button(g, right, y, label, primary, true, mx, my, action);
    }

    /** A button ending at {@code right}; returns its left edge. */
    private int button(HudGraphics g, int right, int y, String label, boolean primary, boolean enabled, int mx, int my, Runnable action) {
        int bw = g.textWidth(label) + 18;
        int bx = right - bw;
        boolean over = enabled && UiHover.in(mx, my, bx, y, bw, 16);
        int accent = LumoraTheme.accent();
        if (primary) {
            g.roundedRect(bx, y, bw, 16, 4, !enabled ? 0xFF2A3140 : over ? LumoraTheme.accentHover() : accent);
        } else {
            g.card(bx, y, bw, 16, 4, over ? ROW_HOVER : ROW, BORDER);
        }
        g.text(label, bx + 9, y + 4, enabled ? TEXT : DIM, false);
        if (enabled) {
            regions.add(new Region(bx, y, bw, 16, action));
        }
        return bx;
    }

    // step 1: what the tab collects

    private List<ChatSuggestions.Suggestion> recent() {
        if (recent == null) {
            List<ChatRule> existing = new ArrayList<>();
            for (ChatTab t : windows.layout().tabs()) {
                existing.add(t.rule);
            }
            recent = ChatSuggestions.recent(windows.store().recent(ChatSuggestions.RECENT), host.own(), existing, 4);
            fromMessage = from == null ? List.of() : ChatSuggestions.of(from, windows.store().recent(ChatSuggestions.RECENT), host.own());
        }
        return recent;
    }

    private static final ChatRule[] OWN = {
            new ChatRule(ChatRule.Kind.MENTION, ""), new ChatRule(ChatRule.Kind.PLAYER, ""),
            new ChatRule(ChatRule.Kind.PREFIX, ""), new ChatRule(ChatRule.Kind.RANK, ""),
            new ChatRule(ChatRule.Kind.CONTAINS, ""), new ChatRule(ChatRule.Kind.SERVER, ""),
            new ChatRule(ChatRule.Kind.ALL, "")};

    private int listHeight(HudGraphics g, int w) {
        List<ChatSuggestions.Suggestion> r = recent();
        int h = 0;
        if (!fromMessage.isEmpty()) {
            h += 24 + fromMessage.size() * 40;
        }
        h += 24 + (r.isEmpty() ? 14 : r.size() * 40);
        h += 16 + OWN.length * 18;
        return h;
    }

    private void renderStart(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        int full = listHeight(g, w);
        listMax = Math.max(0, full - h);
        listScroll = Math.max(0, Math.min(listScroll, listMax));
        g.enableScissor(x + 1, y, x + w - 1, y + h);
        int yy = y - listScroll;
        boolean inList = my >= y && my < y + h;
        if (!fromMessage.isEmpty()) {
            yy = section(g, x, yy, tr("from_message"), tr("from_message.hint"));
            for (ChatSuggestions.Suggestion s : fromMessage) {
                yy = suggestion(g, x, yy, w, s, inList, mx, my);
            }
        }
        yy = section(g, x, yy, tr("suggestions"), tr("suggestions.hint"));
        if (recent().isEmpty()) {
            g.text(tr("suggestions.none"), x + PAD, yy, DIM, false);
            yy += 14;
        }
        for (ChatSuggestions.Suggestion s : recent()) {
            yy = suggestion(g, x, yy, w, s, inList, mx, my);
        }
        g.text(tr("own"), x + PAD, yy + 3, TEXT, false);
        yy += 16;
        for (ChatRule r : OWN) {
            boolean over = inList && UiHover.in(mx, my, x + PAD, yy, w - 2 * PAD, 16);
            g.card(x + PAD, yy, w - 2 * PAD, 16, 3, over ? ROW_HOVER : ROW, BORDER);
            ChatIcons.of(g, r.kind(), x + PAD + 6, yy + 4, MUTED);
            g.text(fit(g, tr("own." + r.kind().id), w - 2 * PAD - 26), x + PAD + 20, yy + 4, TEXT, false);
            if (inList) {
                regions.add(new Region(x + PAD, yy, w - 2 * PAD, 16, () -> choose(r)));
            }
            yy += 18;
        }
        g.disableScissor();
        if (listMax > 0) {
            int th = Math.max(10, h * h / full);
            int ty = y + (h - th) * listScroll / listMax;
            g.fill(x + w - 5, ty, x + w - 3, ty + th, 0x80FFFFFF);
        }
    }

    private int section(HudGraphics g, int x, int y, String title, String hint) {
        g.text(title, x + PAD, y + 2, TEXT, false);
        g.text(hint, x + PAD, y + 12, MUTED, false);
        return y + 24;
    }

    private int suggestion(HudGraphics g, int x, int y, int w, ChatSuggestions.Suggestion s, boolean inList, int mx, int my) {
        int cw = w - 2 * PAD;
        boolean over = inList && UiHover.in(mx, my, x + PAD, y, cw, 36);
        g.card(x + PAD, y, cw, 36, 4, over ? ROW_HOVER : ROW, over ? LumoraTheme.alpha(LumoraTheme.accent(), 0xA0) : BORDER);
        ChatIcons.of(g, s.rule().kind(), x + PAD + 7, y + 6, TEXT);
        String count = String.valueOf(s.count());
        int bw = g.textWidth(count) + 8;
        int bx = x + PAD + cw - bw - 6;
        g.roundedRect(bx, y + 4, bw, 11, 5, LumoraTheme.accent());
        g.text(count, bx + 4, y + 6, TEXT, false);
        int tx = x + PAD + 22;
        int room = bx - tx - 4;
        g.text(fit(g, ruleTitle(s.rule()), room), tx, y + 4, TEXT, false);
        g.text(fit(g, ruleText(s.rule()), room), tx, y + 14, MUTED, false);
        example(g, s.example(), tx, y + 25, x + PAD + cw - 6 - tx);
        if (inList) {
            regions.add(new Region(x + PAD, y, cw, 36, () -> choose(s.rule())));
        }
        return y + 40;
    }

    /** One line of a message in Minecraft's own colors, cut at {@code width}. */
    private void example(HudGraphics g, ChatEntry e, int x, int y, int width) {
        ChatText text = host.text();
        if (text == null || e == null || width <= 4) {
            return;
        }
        List<Object> lines;
        try {
            lines = text.wrap(e.component(), 4000);
        } catch (RuntimeException ex) {
            return;
        }
        if (lines.isEmpty()) {
            return;
        }
        g.push();
        g.translate(x, y);
        g.scale(0.75f);
        g.enableScissor(x, y - 1, x + width, y + 8);
        text.draw(g, lines.get(0), 0, 0, 0xFFFFFFFF, false);
        g.disableScissor();
        g.pop();
    }

    // step 2: what happens with matching messages

    private boolean showKeep() {
        return rule != null && rule.kind() != ChatRule.Kind.ALL && (edit == null || !edit.main());
    }

    private int adjustHeight() {
        int h = 26;
        if (rule != null && (rule.kind().needsValue || rule.kind() == ChatRule.Kind.MENTION)) {
            h += 32;
        }
        h += 14 + (showKeep() ? 26 : 0) + 20 + 26;
        h += 8 + 62;
        return h;
    }

    private void renderAdjust(HudGraphics g, int x, int y, int w, int mx, int my) {
        ChatRule r = currentRule();
        int cw = w - 2 * PAD;
        g.text(fit(g, ruleTitle(r), cw), x + PAD, y, TEXT, false);
        g.text(fit(g, ruleText(r), cw), x + PAD, y + 10, MUTED, false);
        int yy = y + 26;
        if (r.kind().needsValue || r.kind() == ChatRule.Kind.MENTION) {
            g.text(tr("value." + r.kind().id), x + PAD, yy, MUTED, false);
            field(g, x + PAD, yy + 10, cw, value, tr("value." + r.kind().id + ".hint"), mx, my);
            yy += 32;
        }
        g.text(tr("when"), x + PAD, yy + 2, TEXT, false);
        yy += 14;
        if (showKeep()) {
            yy = toggle(g, x + PAD, yy, cw, tr("keep"), tr("keep.desc"), draft.keepInMain, mx, my, () -> draft.keepInMain = !draft.keepInMain, 0);
        }
        yy = toggle(g, x + PAD, yy, cw, tr("sound"), null, draft.sound, mx, my, () -> draft.sound = !draft.sound, 0);
        yy = toggle(g, x + PAD, yy, cw, tr("highlight"), tr("highlight.desc"), draft.highlight, mx, my,
                () -> draft.highlight = !draft.highlight, draft.highlightColor);
        yy += 8;
        preview(g, x + PAD, yy, cw, r);
    }

    private int toggle(HudGraphics g, int x, int y, int w, String label, String desc, boolean on, int mx, int my, Runnable flip, int color) {
        int h = desc == null ? 18 : 24;
        boolean over = UiHover.in(mx, my, x, y, w, h);
        g.card(x, y, w, h, 4, over ? ROW_HOVER : ROW, BORDER);
        g.text(label, x + 6, y + (desc == null ? 5 : 4), TEXT, false);
        if (desc != null) {
            g.push();
            g.translate(x + 6, y + 14);
            g.scale(0.8f);
            g.text(desc, 0, 0, MUTED, false);
            g.pop();
        }
        int tx = x + w - 26;
        int ty = y + (h - 10) / 2;
        int accent = LumoraTheme.accent();
        g.roundedRect(tx, ty, 20, 10, 5, on ? accent : 0xFF3A4252);
        g.roundedRect(on ? tx + 11 : tx + 1, ty + 1, 8, 8, 4, 0xFFFFFFFF);
        if (color != 0) {
            int sx = tx - 14;
            boolean overSwatch = UiHover.in(mx, my, sx, ty, 10, 10);
            g.roundedRect(sx - 1, ty - 1, 12, 12, 3, overSwatch ? 0xFFFFFFFF : BORDER);
            g.roundedRect(sx, ty, 10, 10, 3, color);
            regions.add(new Region(sx - 1, ty - 1, 12, 12, this::nextColor));
        }
        regions.add(new Region(x, y, w, h, flip));
        return y + h + 2;
    }

    private void nextColor() {
        int i = 0;
        while (i < COLORS.length && COLORS[i] != draft.highlightColor) {
            i++;
        }
        draft.highlightColor = COLORS[(i + 1) % COLORS.length];
        draft.highlight = true;
    }

    private void preview(HudGraphics g, int x, int y, int w, ChatRule r) {
        g.text(tr("preview"), x, y, TEXT, false);
        int by = y + 11;
        g.card(x, by, w, 50, 4, FIELD, BORDER);
        List<ChatEntry> recentMsgs = windows.store().recent(ChatSuggestions.RECENT);
        List<ChatEntry> hits = new ArrayList<>();
        String own = host.own();
        for (ChatEntry e : recentMsgs) {
            if (r.complete() && r.matches(e, own)) {
                hits.add(e);
            }
        }
        g.push();
        g.translate(x + 5, by + 4);
        g.scale(0.8f);
        g.text(tr("preview.count", hits.size(), recentMsgs.size()), 0, 0, MUTED, false);
        g.pop();
        ChatText text = host.text();
        if (text == null) {
            return;
        }
        int ly = by + 14;
        int maxLines = 3;
        List<Object> lines = new ArrayList<>();
        for (int i = hits.size() - 1; i >= 0 && lines.size() < maxLines; i--) {
            List<Object> l;
            try {
                l = text.wrap(hits.get(i).component(), (int) ((w - 10) / 0.9));
            } catch (RuntimeException ex) {
                continue;
            }
            for (int j = l.size() - 1; j >= 0 && lines.size() < maxLines; j--) {
                lines.add(0, l.get(j));
            }
        }
        g.enableScissor(x + 1, by + 1, x + w - 1, by + 49);
        for (Object l : lines) {
            g.push();
            g.translate(x + 5, ly);
            g.scale(0.9f);
            text.draw(g, l, 0, 0, 0xFFFFFFFF, false);
            g.pop();
            ly += 10;
        }
        g.disableScissor();
        if (hits.isEmpty()) {
            g.text(fit(g, tr("preview.none"), w - 10), x + 5, by + 24, DIM, false);
        }
    }

    // step 3: name and place

    private boolean showWhere() {
        return edit == null || !edit.main();
    }

    private int placeHeight() {
        return 36 + (showWhere() ? 14 + 2 * 28 : 0) + 14;
    }

    private void renderPlace(HudGraphics g, int x, int y, int w, int mx, int my) {
        int cw = w - 2 * PAD;
        g.text(tr("name"), x + PAD, y, TEXT, false);
        field(g, x + PAD, y + 11, cw, name, tr("name.hint"), mx, my);
        int yy = y + 36;
        if (showWhere()) {
            g.text(tr("where"), x + PAD, yy, TEXT, false);
            yy += 14;
            boolean editing = edit != null;
            yy = radio(g, x + PAD, yy, cw, editing ? tr("where.main") : tr("where.here"),
                    editing ? tr("where.main.desc") : tr("where.here.desc"), !newWindow, mx, my, () -> newWindow = false);
            yy = radio(g, x + PAD, yy, cw, editing ? tr("where.own") : tr("where.new"),
                    tr("where.new.desc"), newWindow, mx, my, () -> newWindow = true);
        }
        g.push();
        g.translate(x + PAD, yy + 2);
        g.scale(0.8f);
        g.text(tr("later"), 0, 0, DIM, false);
        g.pop();
    }

    private int radio(HudGraphics g, int x, int y, int w, String label, String desc, boolean on, int mx, int my, Runnable pick) {
        boolean over = UiHover.in(mx, my, x, y, w, 26);
        int accent = LumoraTheme.accent();
        g.card(x, y, w, 26, 4, on ? LumoraTheme.alpha(accent, 0x30) : over ? ROW_HOVER : ROW, on ? accent : BORDER);
        g.roundedRect(x + 7, y + 9, 8, 8, 4, on ? accent : 0xFF3A4252);
        if (on) {
            g.roundedRect(x + 9, y + 11, 4, 4, 2, 0xFFFFFFFF);
        }
        g.text(label, x + 22, y + 4, TEXT, false);
        g.push();
        g.translate(x + 22, y + 15);
        g.scale(0.8f);
        g.text(desc, 0, 0, MUTED, false);
        g.pop();
        regions.add(new Region(x, y, w, 26, pick));
        return y + 28;
    }

    private void field(HudGraphics g, int x, int y, int w, TextBox box, String hint, int mx, int my) {
        boolean focused = focus == box;
        g.card(x, y, w, 16, 3, FIELD, focused ? LumoraTheme.accent() : BORDER);
        String t = box.get();
        if (t.isEmpty() && !focused) {
            g.text(fit(g, hint, w - 10), x + 5, y + 4, DIM, false);
        } else {
            String shown = t;
            while (g.textWidth(shown) > w - 12 && !shown.isEmpty()) {
                shown = shown.substring(1);
            }
            g.text(shown, x + 5, y + 4, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = x + 5 + g.textWidth(shown);
                g.fill(cx, y + 3, cx + 1, y + 13, TEXT);
            }
        }
        regions.add(new Region(x, y, w, 16, () -> focus = box));
    }

    // --- input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) {
            return true;
        }
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(x, y)) {
                focus = null; // a text field focuses itself again in its action
                host.clickSound();
                r.action().run();
                return true;
            }
        }
        focus = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (step == 0) {
            listScroll = Math.max(0, Math.min(listMax, listScroll - (int) Math.signum(delta) * 20));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (focus == name) {
            nameEdited = true;
        }
        if (focus != null && focus.key(key, ctrl, host.clipboard())) {
            return true;
        }
        if (key == Keys.ENTER) {
            if (step == 1 && currentRule().complete()) {
                step = 2;
                focus = null;
            } else if (step == 2 && !name.get().strip().isEmpty()) {
                finish();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch) {
        if (System.currentTimeMillis() - openedAt < 150 || focus == null) {
            return focus != null;
        }
        if (focus == name) {
            nameEdited = true;
        }
        focus.append(String.valueOf(ch));
        return true;
    }

    private void finish() {
        ChatRule r = currentRule();
        String n = name.get().strip();
        if (edit != null) {
            ChatTab changed = draft.copy();
            changed.name = edit.main() && n.equals(host.tr("tabs.main")) ? "" : n;
            changed.rule = edit.main() ? ChatRule.ALL : r;
            windows.edited(edit, changed, newWindow);
        } else {
            if (!r.complete()) {
                return;
            }
            ChatTab t = new ChatTab(windows.layout().newTabId(), n, r);
            t.keepInMain = draft.keepInMain || r.kind() == ChatRule.Kind.ALL;
            t.sound = draft.sound;
            t.highlight = draft.highlight;
            t.highlightColor = draft.highlightColor;
            windows.create(t, newWindow ? null : window);
        }
        close();
    }

    private void close() {
        host.closeView(this);
    }

    /** Mouse inside a rectangle. */
    static final class UiHover {
        private UiHover() {
        }

        static boolean in(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
