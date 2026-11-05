package dev.lumora.core.hub;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ButtonSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.KeybindSetting;
import dev.lumora.api.config.OptionSetting;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingType;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.config.TextSetting;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Settings of a module, widget or addon in the hub's design: header with back, title, search, HUD editor and reset,
 * sliders over the full width, switches in two columns, colours with hex value, choices, numbers, text and keys.
 * Built only from the setting descriptions, so every widget and addon gets it for free. Values are validated by the
 * settings themselves; {@code onClose} (saving) runs once when the view goes away.
 */
public final class SettingsView implements AddonView {
    /** What the Minecraft version does for this view. */
    public interface Host {
        /** Closes the view (back to the screen it was opened from). */
        void close(SettingsView view);

        boolean inWorld();

        /** Opens the HUD editor on top (only called if the view was created with {@code hudEditor}). */
        void openHudEditor();
    }

    static final int TEXT = HubView.TEXT;
    static final int MUTED = HubView.MUTED;
    static final int DIM = HubView.DIM;
    static final int SWITCH_ON = 0xFF22A55A;
    static final int SWITCH_OFF = 0xFFC0304A;
    static final int TRACK = 0xFF2E2A3E;
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFF000000, 0xFF8B5CF6, 0xFF3B82F6, 0xFF06B6D4, 0xFF22C55E,
            0xFFF59E0B, 0xFFEF4444, 0xFFEC4899};
    private static boolean advanced;

    /**
     * A part shown below the own settings, after a divider with {@code title}: e.g. the HUD widget of an addon with an
     * on/off switch ({@code switchLabel}, may be null) and its settings ({@code group}, may be null).
     */
    public record Section(String title, SettingsGroup group, String switchLabel, java.util.function.BooleanSupplier on,
                          Runnable flip) {
    }

    /** Divider with a title in the list of rows. */
    private record Heading(Section section) {
    }

    /** On/off switch of a section in the list of rows. */
    private record SectionSwitch(Section section) {
    }

    /** Heading of a section of settings ({@link Setting#section}). */
    private record SubHeading(String key) {
    }

    /**
     * An editor of its own above the setting rows, e.g. the crosshair editor with preview and presets. A module
     * registers it for its settings group with {@link #registerPanel}; every settings view of that group shows it.
     */
    public interface Panel {
        /** Draws the panel at (x, y) with width {@code w} and returns its height; {@code mx/my} are -1 outside the list. */
        int render(HudGraphics g, int x, int y, int w, int mx, int my);

        /** A mouse press inside the list (any button); true if the panel used it. */
        boolean mouseClicked(double x, double y, int button);

        /** The mouse moves with a button held after a press the panel used. */
        default void mouseDragged(double x, double y, int button) {
        }

        default void mouseReleased() {
        }

        /** Whether the setting gets its own row below the panel (false: the panel edits it). */
        default boolean shows(Setting<?> setting) {
            return true;
        }

        /** Hint for the element under the mouse, or null. */
        default String tooltip() {
            return null;
        }
    }

    private static final java.util.Map<SettingsGroup, java.util.function.Function<Boolean, Panel>> PANELS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Shows a panel above the rows of {@code group}; the factory gets {@code true} for German texts. */
    public static void registerPanel(SettingsGroup group, java.util.function.Function<Boolean, Panel> factory) {
        if (group != null && factory != null) {
            PANELS.put(group, factory);
        }
    }

    /** Sections for the next view created on this thread ({@link HubHost#openSettings} with sections). */
    private static final ThreadLocal<List<Section>> NEXT = ThreadLocal.withInitial(List::of);

    /** Smoke test: the next settings view shows the drop-down list or color picker of the setting {@code key} open. */
    public static void openPopupInNextView(String key) {
        nextPopup = key;
    }

    static void nextSections(List<Section> sections) {
        NEXT.set(sections == null ? List.of() : List.copyOf(sections));
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private record Slider(Setting<?> setting, int x1, int x2, int y1, int y2) {
    }

    private final LumoraCore core;
    private final Host host;
    private final String title;
    private final String description;
    private final SettingsGroup group;
    private final List<Keybind> keybinds;
    private final Runnable onClose;
    private final boolean hudEditor;
    private final List<Section> sections;
    private final Panel panel;
    private boolean panelDrag;
    private final boolean de;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private long openedAt;
    private boolean closed;
    private Slider dragging;
    /** Focused text field: "search", "hex:<key>" or "text:<key>"; null if none. */
    private String focus;
    private final StringBuilder buffer = new StringBuilder();
    private String search = "";
    private IntConsumer capture;
    private String captureId;
    private float scroll;
    private float scrollTarget;
    private int maxScroll;
    private int contentTop;
    private int contentBottom;
    private boolean clipHits;
    private String hovered;
    private long hoveredSince;
    private String tooltip;
    private String toast = "";
    private long toastAt;
    /** Smoke test: the next view opens the drop-down list or color picker of this setting once it is drawn. */
    private static volatile String nextPopup;
    private String pendingPopup;
    // drop-down list and color picker (drawn above everything, they take the clicks while open)
    private static final int DD_ROW = 14;
    private static final int DD_MAX_ROWS = 8;
    private static final int PK_W = 168;
    private static final int PK_SV_W = 104;
    private static final int PK_SV_H = 62;
    private static final int POPUP = 0xFF15131D;
    private final List<Hit> popupHits = new ArrayList<>();
    private OptionSetting dropdown;
    private int ddX;
    private int ddY;
    private int ddW;
    private int ddScroll;
    private int[] ddBox;
    private ColorSetting picker;
    private int pkX;
    private int pkY;
    private float pkH;
    private float pkS;
    private float pkV;
    private String pkDrag;
    private int[] pkBox;
    private int[] pkSv;
    private int[] pkHue;
    private int[] pkAlpha;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    /**
     * @param group     settings to show (may be {@code null} if there are only keys)
     * @param keybinds  Lumora keys shown below the settings
     * @param onClose   run once when the view goes away (e.g. save), may be {@code null}
     * @param hudEditor show the HUD editor button (settings of a HUD widget)
     */
    public SettingsView(LumoraCore core, Host host, String title, String description, SettingsGroup group,
                        List<Keybind> keybinds, Runnable onClose, boolean hudEditor) {
        this(core, host, title, description, group, keybinds, onClose, hudEditor, NEXT.get());
        NEXT.set(List.of());
    }

    /** @param sections parts below the own settings, each after a divider (e.g. the addon's HUD widgets) */
    public SettingsView(LumoraCore core, Host host, String title, String description, SettingsGroup group,
                        List<Keybind> keybinds, Runnable onClose, boolean hudEditor, List<Section> sections) {
        this.sections = sections == null ? List.of() : List.copyOf(sections);
        this.core = core;
        this.host = host;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.group = group;
        this.keybinds = keybinds == null ? List.of() : List.copyOf(keybinds);
        this.onClose = onClose;
        this.hudEditor = hudEditor;
        Translations tr = core.translations();
        String lang = tr == null ? "" : tr.language();
        this.de = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
        this.pendingPopup = nextPopup;
        nextPopup = null;
        java.util.function.Function<Boolean, Panel> factory = group == null ? null : PANELS.get(group);
        this.panel = factory == null ? null : factory.apply(de);
    }

    private String t(String german, String english) {
        return de ? german : english;
    }

    private String tr(String key, String fallback) {
        Translations tr = core.translations();
        return tr == null ? fallback : tr.translateOr(key, fallback);
    }

    String name(Setting<?> s) {
        return tr(s.nameKey(), s.key());
    }

    private String help(String key) {
        Translations tr = core.translations();
        return tr != null && tr.has(key) ? tr.translate(key) : "";
    }

    private boolean hasAdvanced() {
        if (hasAdvanced(group)) {
            return true;
        }
        for (Section sec : sections) {
            if (hasAdvanced(sec.group())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAdvanced(SettingsGroup g) {
        if (g == null) {
            return false;
        }
        for (Setting<?> s : g.all()) {
            if (s.isAdvanced()) {
                return true;
            }
        }
        return false;
    }

    /** Settings and keys shown now (advanced only when switched on, filtered by the search). */
    List<Object> items() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Object> out = new ArrayList<>();
        if (group != null) {
            addSettings(out, group, q, true);
        }
        for (Keybind k : keybinds) {
            if (q.isEmpty() || tr(k.nameKey(), k.id()).toLowerCase(Locale.ROOT).contains(q)) {
                out.add(k);
            }
        }
        // the sections, each after a divider; a section without a match is left out while searching
        for (Section sec : sections) {
            List<Object> part = new ArrayList<>();
            if (sec.switchLabel() != null && sec.on() != null && sec.flip() != null
                    && (q.isEmpty() || sec.switchLabel().toLowerCase(Locale.ROOT).contains(q)
                    || sec.title().toLowerCase(Locale.ROOT).contains(q))) {
                part.add(new SectionSwitch(sec));
            }
            if (sec.group() != null) {
                addSettings(part, sec.group(), q, false);
            }
            if (!part.isEmpty() || q.isEmpty()) {
                out.add(new Heading(sec));
                out.addAll(part);
            }
        }
        return out;
    }

    /**
     * The shown settings of a group: advanced only when switched on, hidden while their condition is false
     * ({@link Setting#visibleWhen}), filtered by the search; a section heading ({@link Setting#section}) before the
     * first shown setting of each section.
     */
    private void addSettings(List<Object> out, SettingsGroup g, String q, boolean own) {
        String current = null;
        String printed = null;
        for (Setting<?> s : g.all()) {
            if (s.sectionKey() != null) {
                current = s.sectionKey();
            }
            boolean shown = (advanced || !s.isAdvanced()) && s.isVisible() && (!own || panel == null || panel.shows(s))
                    && (q.isEmpty() || name(s).toLowerCase(Locale.ROOT).contains(q));
            if (!shown) {
                continue;
            }
            if (current != null && !current.equals(printed)) {
                out.add(new SubHeading(current));
                printed = current;
            }
            out.add(s);
        }
    }

    private void toast(String message) {
        toast = message == null ? "" : message;
        toastAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        hits.clear();
        sliders.clear();
        tooltip = null;
        long now = System.currentTimeMillis();
        if (openedAt == 0) {
            openedAt = now;
        }
        float p = Math.min(1f, (now - openedAt) / 200f);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        HubView.background(g, width, height, e, host.inWorld());

        int margin = width >= 560 ? 18 : 8;
        int pw = Math.min(width - 2 * margin, 680);
        int ph = Math.min(height - 2 * margin, 440);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        boolean compact = ph < 300;
        int accent = LumoraTheme.accent();

        g.push();
        float s = 0.97f + 0.03f * e;
        g.translate(width / 2f, height / 2f + (1f - e) * 5f);
        g.scale(s);
        g.translate(-width / 2f, -height / 2f);
        for (int i = 3; i >= 1; i--) {
            g.roundedRect(px - i * 2, py - i * 2, pw + i * 4, ph + i * 4, 8 + i * 2, LumoraTheme.alpha(accent, (int) (10 * e / i)));
        }
        g.roundedRect(px - 1, py - 1, pw + 2, ph + 2, 8, HubView.BORDER);
        g.roundedRect(px, py, pw, ph, 7, HubView.MAIN);

        int pad = compact ? 10 : 14;
        int left = px + pad;
        int right = px + pw - pad;
        int hy = py + (compact ? 8 : 12);
        header(g, left, right, hy, mx, my);
        int ly = hy + 20;
        if (!description.isBlank()) {
            g.text(HubView.trim(g, description, right - left), left, ly, MUTED, false);
            ly += 12;
        }
        ly += 2;
        accentLine(g, px, ly, pw);
        contentTop = ly + (compact ? 6 : 8);
        boolean adv = hasAdvanced();
        contentBottom = py + ph - (adv ? 24 : 8);

        scroll += (scrollTarget - scroll) * 0.4f;
        if (Math.abs(scrollTarget - scroll) < 0.5f) {
            scroll = scrollTarget;
        }
        g.enableScissor(px, contentTop - 1, px + pw, contentBottom);
        clipHits = true;
        int used = content(g, left, contentTop - Math.round(scroll), right - left - 6, mx, my);
        clipHits = false;
        g.disableScissor();
        maxScroll = Math.max(0, used - (contentBottom - contentTop));
        if (scrollTarget > maxScroll) {
            scrollTarget = maxScroll;
        }
        if (maxScroll > 0) {
            int trackH = contentBottom - contentTop;
            int barH = Math.max(16, trackH * trackH / Math.max(1, used));
            int barY = contentTop + Math.round((trackH - barH) * (scroll / maxScroll));
            g.roundedRect(px + pw - 6, contentTop, 2, trackH, 1, 0x18FFFFFF);
            g.roundedRect(px + pw - 6, barY, 2, barH, 1, LumoraTheme.alpha(accent, 0xC0));
        }
        if (adv) {
            int fy = py + ph - 20;
            g.fill(px + 1, fy - 3, px + pw - 1, fy - 2, HubView.BORDER);
            String label = advanced ? t("Erweiterte Einstellungen ausblenden", "Hide advanced settings")
                    : t("Erweiterte Einstellungen anzeigen", "Show advanced settings");
            int bw = g.textWidth(label) + 52;
            boolean hover = HubView.in(mx, my, left, fy, bw, 14);
            g.text(label, left + 46, fy + 3, hover ? TEXT : MUTED, false);
            switchPill(g, left, fy + 1, advanced, false);
            hit(left, fy, bw, 14, () -> advanced = !advanced);
        }
        panelLeft = px;
        panelRight = px + pw;
        panelTop = py;
        panelBottom = py + ph;
        drawPopups(g, mx, my);
        g.pop();
        drawTooltip(g, width, height, mx, my);
        drawToast(g, width / 2, py + ph - 30, now);
    }

    private void header(HudGraphics g, int left, int right, int y, int mx, int my) {
        int accent = LumoraTheme.accent();
        boolean backHover = HubView.in(mx, my, left, y, 16, 16);
        g.roundedRect(left, y, 16, 16, 8, backHover ? accent : 0x26FFFFFF);
        HubGlyphs.draw(g, "back", left + 4, y + 4, backHover ? 0xFFFFFFFF : TEXT);
        hit(left, y, 16, 16, () -> host.close(this));

        int ix = right - 16;
        iconButton(g, ix, y, "reset", t("Alles zurücksetzen", "Reset all"), mx, my, () -> {
            if (group != null) {
                group.resetAll();
            }
            for (Section sec : sections) {
                if (sec.group() != null) {
                    sec.group().resetAll();
                }
            }
            for (Keybind k : keybinds) {
                k.setKey(k.defaultKey());
            }
            toast(t("Auf Standard zurückgesetzt", "Reset to defaults"));
        });
        if (hudEditor) {
            ix -= 20;
            iconButton(g, ix, y, "hudeditor", t("Im HUD-Editor verschieben", "Move in the HUD editor"), mx, my, host::openHudEditor);
        }
        int sbw = Math.max(80, Math.min(150, (right - left) / 3));
        int sbx = ix - 6 - sbw;
        searchBox(g, sbx, y, sbw, 16, mx, my);

        g.push();
        g.translate(left + 24, y + 2);
        g.scale(1.5f);
        String upper = title.toUpperCase(Locale.ROOT);
        int max = Math.round((sbx - 8 - left - 24) / 1.5f);
        while (spacedBoldWidth(g, upper) > max && upper.length() > 3) {
            upper = upper.substring(0, upper.length() - 2) + "…";
        }
        spacedBold(g, upper, 0, 0, TEXT);
        g.pop();
    }

    private static void spacedBold(HudGraphics g, String s, int x, int y, int color) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = "§l" + s.charAt(i);
            g.text(ch, cx, y, color, true);
            cx += g.textWidth(ch) + 2;
        }
    }

    private static int spacedBoldWidth(HudGraphics g, String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += g.textWidth("§l" + s.charAt(i)) + 2;
        }
        return w;
    }

    private void iconButton(HudGraphics g, int x, int y, String glyph, String hint, int mx, int my, Runnable action) {
        boolean hover = HubView.in(mx, my, x, y, 16, 16);
        if (hover) {
            g.roundedRect(x, y, 16, 16, 4, 0x26FFFFFF);
            tooltip = hint;
        }
        HubGlyphs.draw(g, glyph, x + 4, y + 4, hover ? TEXT : MUTED);
        hit(x, y, 16, 16, action);
    }

    private void searchBox(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean focused = "search".equals(focus);
        boolean hover = HubView.in(mx, my, x, y, w, h);
        g.card(x, y, w, h, h / 2, HubView.FIELD, focused ? LumoraTheme.accent() : hover ? 0xFF3A3450 : HubView.BORDER);
        HubGlyphs.draw(g, "search", x + 5, y + (h - HubGlyphs.SIZE) / 2, focused ? LumoraTheme.accentHover() : DIM);
        String text = focused ? buffer.toString() : search;
        int max = w - 24;
        while (g.textWidth(text) > max && !text.isEmpty()) {
            text = text.substring(1);
        }
        if (text.isEmpty() && !focused) {
            g.text(HubView.trim(g, t("Suchen…", "Search…"), max), x + 17, y + 4, DIM, false);
        } else {
            g.text(text, x + 17, y + 4, TEXT, false);
            caret(g, focused, x + 17 + g.textWidth(text), y + 3, y + h - 3);
        }
        hit(x, y, w, h, () -> focus("search", search));
    }

    private static void caret(HudGraphics g, boolean focused, int x, int y1, int y2) {
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            g.fill(x, y1, x + 1, y2, TEXT);
        }
    }

    private static void accentLine(HudGraphics g, int x, int y, int w) {
        int accent = LumoraTheme.accent();
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            int x1 = x + w * i / steps;
            int x2 = x + w * (i + 1) / steps;
            float f = i / (float) (steps - 1);
            int c = LumoraTheme.mix(accent, LumoraTheme.accentHover(), f * 0.6f);
            g.fill(x1, y, x2, y + 1, LumoraTheme.alpha(c, Math.round(255 - 150 * f)));
            g.fill(x1, y + 1, x2, y + 2, LumoraTheme.alpha(c, Math.round(70 - 60 * f)));
        }
    }

    // ---- rows

    private int content(HudGraphics g, int x, int y, int w, int mx, int my) {
        int y0 = y;
        List<Object> items = items();
        if (panel != null && search.isBlank()) {
            boolean in = visible(my);
            y += panel.render(g, x, y, w, in ? mx : -1, in ? my : -1) + 8;
            String hint = in ? panel.tooltip() : null;
            if (hint != null) {
                tooltip = hint;
            }
            if (items.isEmpty()) {
                return y - y0;
            }
        }
        if (items.isEmpty()) {
            g.text(search.isBlank() ? t("Keine Einstellungen", "No settings") : t("Nichts gefunden", "Nothing found"),
                    x, y + 4, MUTED, false);
            return 20;
        }
        int labelW = Math.max(90, Math.min(170, w * 36 / 100));
        int cols = w >= 300 ? 2 : 1;
        int i = 0;
        while (i < items.size()) {
            Object item = items.get(i);
            if (item instanceof BooleanSetting) {
                // a run of switches: two columns
                List<BooleanSetting> run = new ArrayList<>();
                while (i < items.size() && items.get(i) instanceof BooleanSetting b) {
                    run.add(b);
                    i++;
                }
                int cw = (w - (cols - 1) * 12) / cols;
                for (int j = 0; j < run.size(); j++) {
                    int cx = x + (j % cols) * (cw + 12);
                    int cy = y + (j / cols) * 18;
                    toggle(g, run.get(j), cx, cy, cw, mx, my);
                }
                y += ((run.size() + cols - 1) / cols) * 18 + 2;
                continue;
            }
            if (item instanceof SubHeading sh) {
                // section of the settings: bold label with a small accent mark, a faint line to the right
                y += i == 0 ? 0 : 6;
                String label = HubView.trim(g, "§l" + tr(sh.key(), sh.key()), w - 30);
                g.roundedRect(x, y + 1, 2, 7, 1, LumoraTheme.accent());
                g.text(label, x + 6, y, TEXT, false);
                g.fill(x + 6 + g.textWidth(label) + 6, y + 4, x + w, y + 5, 0x22FFFFFF);
                y += 14;
                i++;
                continue;
            }
            if (item instanceof Heading h) {
                // divider: title in the accent, a thin line to the right
                y += 8;
                String label = "§l" + h.section().title().toUpperCase(Locale.ROOT);
                label = HubView.trim(g, label, w - 20);
                int tw = g.textWidth(label);
                g.text(label, x, y, LumoraTheme.accentHover(), false);
                g.fill(x + tw + 8, y + 4, x + w, y + 5, 0x30FFFFFF);
                y += 16;
                i++;
                continue;
            }
            if (item instanceof SectionSwitch sw) {
                sectionToggle(g, sw.section(), x, y, w, mx, my);
                y += 20;
                i++;
                continue;
            }
            if (item instanceof Setting<?> s) {
                row(g, s, x, y, w, labelW, mx, my);
            } else if (item instanceof Keybind k) {
                keyRow(g, k, x, y, w, labelW, mx, my);
            }
            y += 20;
            i++;
        }
        return y - y0;
    }

    private boolean visible(int my) {
        return my >= contentTop && my < contentBottom;
    }

    private void hoverHelp(String id, String help, boolean hover) {
        if (!hover || help.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!id.equals(hovered)) {
            hovered = id;
            hoveredSince = now;
        } else if (now - hoveredSince > 450) {
            tooltip = help;
        }
    }

    /** On/off switch of a section (e.g. "Im HUD anzeigen"), drawn like a setting switch. */
    private void sectionToggle(HudGraphics g, Section sec, int x, int y, int w, int mx, int my) {
        boolean hover = visible(my) && HubView.in(mx, my, x, y, w, 16);
        switchPill(g, x, y + 2, sec.on().getAsBoolean(), hover);
        g.text(HubView.trim(g, sec.switchLabel(), w - 48), x + 46, y + 4, hover ? 0xFFFFFFFF : TEXT, false);
        hit(x, y, Math.min(w, 46 + g.textWidth(sec.switchLabel()) + 6), 16, sec.flip());
    }

    private void toggle(HudGraphics g, BooleanSetting b, int x, int y, int w, int mx, int my) {
        boolean hover = visible(my) && HubView.in(mx, my, x, y, w, 16);
        boolean on = b.enabled();
        if (b.type() == SettingType.CHECKBOX) {
            checkbox(g, x, y + 2, on, hover);
            g.text(HubView.trim(g, name(b), w - 20), x + 18, y + 4, hover ? 0xFFFFFFFF : TEXT, false);
            hoverHelp(b.key(), help(b.descriptionKey()), hover);
            hit(x, y, Math.min(w, 18 + g.textWidth(name(b)) + 6), 16, b::toggle);
            return;
        }
        switchPill(g, x, y + 2, on, hover);
        g.text(HubView.trim(g, name(b), w - 48), x + 46, y + 4, hover ? 0xFFFFFFFF : TEXT, false);
        hoverHelp(b.key(), help(b.descriptionKey()), hover);
        hit(x, y, Math.min(w, 46 + g.textWidth(name(b)) + 6), 16, b::toggle);
    }

    /** 12 x 12 check box: accent with a check mark when on. */
    private static void checkbox(HudGraphics g, int x, int y, boolean on, boolean hover) {
        int accent = LumoraTheme.accent();
        g.roundedRect(x, y, 12, 12, 3, on ? accent : hover ? 0xFF5A5470 : 0xFF3A3450);
        if (on) {
            HubGlyphs.draw(g, "check", x + 2, y + 2, 0xFFFFFFFF);
        } else {
            g.roundedRect(x + 1, y + 1, 10, 10, 2, hover ? 0xFF2A2638 : HubView.FIELD);
        }
    }

    /** ON / OFF switch: green part on the right when on, red part on the left when off. */
    private void switchPill(HudGraphics g, int x, int y, boolean on, boolean hover) {
        int w = 40;
        int h = 12;
        g.roundedRect(x, y, w, h, 3, hover ? 0xFF3C3752 : 0xFF2E2A3E);
        int sw = 24;
        String label = on ? t("AN", "ON") : t("AUS", "OFF");
        int sx = on ? x + w - sw : x;
        g.roundedRect(sx, y, sw, h, 3, on ? SWITCH_ON : SWITCH_OFF);
        g.fill(sx + 2, y, sx + sw - 2, y + 1, 0x30FFFFFF);
        g.centeredText(label, sx + sw / 2 + 1, y + 2, 0xFFFFFFFF, false);
    }

    private void row(HudGraphics g, Setting<?> s, int x, int y, int w, int labelW, int mx, int my) {
        int h = 18;
        boolean hover = visible(my) && HubView.in(mx, my, x, y, w, h);
        if (hover) {
            g.roundedRect(x - 4, y - 1, w + 8, h + 2, 4, 0x0EFFFFFF);
        }
        String name = name(s);
        g.text(HubView.trim(g, name, labelW - 6), x, y + 5, TEXT, false);
        hoverHelp(s.key(), help(s.descriptionKey()), hover);
        int cx = x + labelW;
        int right = x + w;
        SettingType type = s.type();
        if (type == SettingType.SLIDER && (s instanceof IntegerSetting || s instanceof DecimalSetting)) {
            slider(g, s, cx, right, y, h, mx, my);
        } else if (s instanceof ButtonSetting b) {
            String label = tr(b.labelKey(), name);
            int bw = Math.min(right - cx, Math.max(70, g.textWidth(label) + 20));
            int bx = right - bw;
            boolean bh = visible(my) && HubView.in(mx, my, bx, y + 2, bw, 14);
            int accent = LumoraTheme.accent();
            g.card(bx, y + 2, bw, 14, 4, bh ? LumoraTheme.alpha(accent, 0x50) : HubView.FIELD, bh ? accent : 0xFF3A3450);
            g.centeredText(HubView.trim(g, label, bw - 8), bx + bw / 2 + 1, y + 5, bh ? 0xFFFFFFFF : TEXT, false);
            hit(bx, y, bw, h, () -> press(b));
        } else if (s instanceof OptionSetting o && type == SettingType.SELECTION && selection(g, o, cx, right, y, mx, my)) {
            // drawn as a row of buttons
        } else if (s instanceof OptionSetting o && type == SettingType.DROPDOWN) {
            dropdownField(g, o, cx, right, y, h, hover, mx, my);
        } else if (s instanceof OptionSetting o) {
            String value = tr(o.optionKey(o.selectedId()), o.selectedId());
            int pw = Math.min(right - cx, Math.max(70, g.textWidth(value) + 30));
            int px = right - pw;
            g.card(px, y + 2, pw, 14, 4, HubView.FIELD, hover ? LumoraTheme.accent() : 0xFF3A3450);
            g.centeredText(HubView.trim(g, value, pw - 24), px + pw / 2, y + 5, TEXT, false);
            g.text("‹", px + 5, y + 5, LumoraTheme.accentHover(), false);
            g.rightText("›", px + pw - 5, y + 5, LumoraTheme.accentHover(), false);
            hit(px, y, pw / 2, h, () -> o.cycle(-1));
            hit(px + pw / 2, y, pw - pw / 2, h, () -> o.cycle(1));
        } else if (s instanceof IntegerSetting i) {
            stepper(g, right, y, i.get() + i.unit(), mx, my, () -> i.set(i.get() - i.step()), () -> i.set(i.get() + i.step()));
        } else if (s instanceof DecimalSetting d) {
            double step = d.step() > 0 ? d.step() : (d.max() - d.min()) / 20;
            stepper(g, right, y, format(d.get()) + d.unit(), mx, my, () -> d.set(d.get() - step), () -> d.set(d.get() + step));
        } else if (s instanceof TextSetting t) {
            String id = "text:" + s.key();
            boolean focused = id.equals(focus);
            int fw = Math.min(right - cx, 180);
            int fx = right - fw;
            g.card(fx, y + 2, fw, 14, 3, HubView.FIELD, focused ? LumoraTheme.accent() : 0xFF3A3450);
            String text = focused ? buffer.toString() : t.get();
            while (g.textWidth(text) > fw - 10 && !text.isEmpty()) {
                text = text.substring(1);
            }
            g.text(text, fx + 5, y + 5, TEXT, false);
            caret(g, focused, fx + 5 + g.textWidth(text), y + 4, y + 14);
            hit(fx, y, fw, h, () -> focus(id, t.get()));
        } else if (s instanceof ColorSetting c) {
            colorRow(g, c, cx, right, y, h, mx, my);
        } else if (s instanceof KeybindSetting k) {
            keyPill(g, "setting:" + k.key(), k.get(), right, y, mx, my, k::set);
        }
    }

    private void slider(HudGraphics g, Setting<?> s, int x, int right, int y, int h, int mx, int my) {
        double f = fraction(s);
        String value = s instanceof IntegerSetting i ? i.get() + i.unit() : format(((DecimalSetting) s).get()) + ((DecimalSetting) s).unit();
        int vw = Math.max(34, g.textWidth(value) + 6);
        g.rightText(value, x + vw - 4, y + 5, MUTED, false);
        int tx = x + vw + 4;
        int tw = right - 6 - tx;
        int cy = y + h / 2;
        boolean active = dragging != null && dragging.setting() == s;
        boolean hover = active || visible(my) && HubView.in(mx, my, tx - 6, y, tw + 12, h);
        g.roundedRect(tx, cy - 1, tw, 3, 1, TRACK);
        int kx = tx + (int) Math.round(f * tw);
        g.roundedRect(tx, cy - 1, Math.max(0, kx - tx), 3, 1, LumoraTheme.alpha(LumoraTheme.accent(), 0xC0));
        int kr = hover ? 6 : 5;
        g.roundedRect(kx - kr, cy - kr, kr * 2, kr * 2, kr, hover ? LumoraTheme.accentHover() : LumoraTheme.accent());
        g.roundedRect(kx - 2, cy - 2, 4, 4, 2, 0xE0FFFFFF);
        if (visible(my) || clipHits) {
            int y1 = Math.max(y, contentTop);
            int y2 = Math.min(y + h, contentBottom);
            if (y2 > y1) {
                sliders.add(new Slider(s, tx, tx + tw, y1, y2));
            }
        }
    }

    private static double fraction(Setting<?> s) {
        if (s instanceof IntegerSetting i) {
            return i.max() == i.min() ? 0 : (i.get() - i.min()) / (double) (i.max() - i.min());
        }
        DecimalSetting d = (DecimalSetting) s;
        return d.max() == d.min() ? 0 : (d.get() - d.min()) / (d.max() - d.min());
    }

    private static void apply(Slider sl, double mx) {
        double f = sl.x2() == sl.x1() ? 0 : Math.max(0, Math.min(1, (mx - sl.x1()) / (sl.x2() - sl.x1())));
        if (sl.setting() instanceof IntegerSetting i) {
            i.set((int) Math.round(i.min() + f * (i.max() - i.min())));
        } else if (sl.setting() instanceof DecimalSetting d) {
            double v = d.min() + f * (d.max() - d.min());
            if (d.step() > 0) {
                v = d.min() + Math.round((v - d.min()) / d.step()) * d.step();
            }
            d.set(Math.max(d.min(), Math.min(d.max(), v)));
        }
    }

    private void stepper(HudGraphics g, int right, int y, String value, int mx, int my, Runnable minus, Runnable plus) {
        int bw = 14;
        int vw = Math.max(40, g.textWidth(value) + 12);
        int x = right - (bw * 2 + vw + 4);
        stepButton(g, x, y + 2, "−", mx, my, minus);
        g.card(x + bw + 2, y + 2, vw, 14, 3, HubView.FIELD, 0xFF3A3450);
        g.centeredText(value, x + bw + 2 + vw / 2, y + 5, TEXT, false);
        stepButton(g, x + bw + vw + 4, y + 2, "+", mx, my, plus);
    }

    private void stepButton(HudGraphics g, int x, int y, String label, int mx, int my, Runnable action) {
        boolean hover = visible(my) && HubView.in(mx, my, x, y, 14, 14);
        g.roundedRect(x, y, 14, 14, 3, hover ? LumoraTheme.accent() : 0xFF2E2A3E);
        g.centeredText(label, x + 7 + 1, y + 3, TEXT, false);
        hit(x, y, 14, 14, action);
    }

    private void colorRow(HudGraphics g, ColorSetting c, int x, int right, int y, int h, int mx, int my) {
        String id = "hex:" + c.key();
        boolean focused = id.equals(focus);
        int rx = right - 12;
        boolean resetHover = visible(my) && HubView.in(mx, my, rx - 2, y + 2, 13, 14);
        HubGlyphs.draw(g, "reset", rx, y + 4, resetHover ? TEXT : DIM);
        hit(rx - 2, y, 13, h, c::reset);
        String hex = focused ? buffer.toString() : hex(c);
        int hw = 64;
        int hx = rx - 8 - hw;
        g.card(hx, y + 2, hw, 14, 3, HubView.FIELD, focused ? LumoraTheme.accent() : 0xFF3A3450);
        g.text(hex, hx + 5, y + 5, focused ? TEXT : MUTED, false);
        caret(g, focused, hx + 5 + g.textWidth(hex), y + 4, y + 14);
        hit(hx, y, hw, h, () -> focus(id, hex(c)));
        int sx = hx - 6 - 14;
        boolean swHover = visible(my) && HubView.in(mx, my, sx, y + 2, 14, 14);
        g.roundedRect(sx, y + 2, 14, 14, 3, swHover ? 0xFFFFFFFF : 0xFF4A4458);
        // checkerboard behind translucent colours
        g.fill(sx + 1, y + 3, sx + 13, y + 15, 0xFF8A8499);
        g.fill(sx + 1, y + 3, sx + 7, y + 9, 0xFFC9C4D6);
        g.fill(sx + 7, y + 9, sx + 13, y + 15, 0xFFC9C4D6);
        g.fill(sx + 1, y + 3, sx + 13, y + 15, c.argb());
        if (c.isChroma()) {
            // rainbow mark: a thin hue strip at the bottom of the swatch
            for (int i = 0; i < 12; i++) {
                g.fill(sx + 1 + i, y + 13, sx + 2 + i, y + 15, hsvToRgb(i / 12f, 1f, 1f));
            }
        }
        if (swHover) {
            tooltip = t("Farbwähler öffnen", "Open the color picker");
        }
        hit(sx, y, 14, h, () -> openPicker(c, sx, y + 18));
        if (c.key().equals(pendingPopup)) {
            pendingPopup = null;
            openPicker(c, sx, y + 18);
        }
    }

    private static String hex(ColorSetting c) {
        return c.hasAlpha() ? Colors.toHex(c.get()).toLowerCase(Locale.ROOT)
                : String.format("#%06x", c.get() & 0xFFFFFF);
    }

    private void keyRow(HudGraphics g, Keybind k, int x, int y, int w, int labelW, int mx, int my) {
        boolean hover = visible(my) && HubView.in(mx, my, x, y, w, 18);
        if (hover) {
            g.roundedRect(x - 4, y - 1, w + 8, 20, 4, 0x0EFFFFFF);
        }
        g.text(HubView.trim(g, tr(k.nameKey(), k.id()), labelW - 6), x, y + 5, TEXT, false);
        keyPill(g, "key:" + k.id(), k.key(), x + w, y, mx, my, key -> {
            if (!k.setKey(key)) {
                toast(t("Taste schon belegt: ", "Key already used: ") + k.conflictWith(key).orElse("?"));
            }
        });
    }

    private void keyPill(HudGraphics g, String id, int key, int right, int y, int mx, int my, IntConsumer setter) {
        boolean waiting = id.equals(captureId);
        String label = waiting ? t("Taste drücken…", "Press a key…") : core.keybindManager().keyName(key);
        int pw = Math.max(70, g.textWidth(label) + 16);
        int px = right - pw;
        boolean hover = visible(my) && HubView.in(mx, my, px, y + 2, pw, 14);
        g.card(px, y + 2, pw, 14, 4, waiting ? LumoraTheme.alpha(LumoraTheme.accent(), 0x40) : HubView.FIELD,
                waiting || hover ? LumoraTheme.accent() : 0xFF3A3450);
        g.centeredText(label, px + pw / 2 + 1, y + 5, waiting ? TEXT : 0xFFD6D0E2, false);
        hit(px, y, pw, 18, () -> {
            captureId = id;
            capture = setter;
        });
    }

    // ------------------------------------------------------------------ buttons, button rows, drop-downs, color picker

    private void press(ButtonSetting b) {
        try {
            b.press();
        } catch (RuntimeException | LinkageError e) {
            toast(t("Fehler: ", "Error: ") + e.getMessage());
        }
    }

    /** The options as a row of buttons (right-aligned); false and nothing drawn if they do not fit. */
    private boolean selection(HudGraphics g, OptionSetting o, int left, int right, int y, int mx, int my) {
        List<String> ids = o.optionIds();
        int[] widths = new int[ids.size()];
        int total = 0;
        for (int i = 0; i < ids.size(); i++) {
            widths[i] = g.textWidth(tr(o.optionKey(ids.get(i)), ids.get(i))) + 12;
            total += widths[i] + (i > 0 ? 2 : 0);
        }
        if (total > right - left) {
            return false;
        }
        int x = right - total;
        int accent = LumoraTheme.accent();
        String selected = o.selectedId();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            boolean on = id.equals(selected);
            boolean hover = visible(my) && HubView.in(mx, my, x, y + 2, widths[i], 14);
            g.roundedRect(x, y + 2, widths[i], 14, 4, on ? accent : hover ? 0xFF3C3752 : 0xFF2E2A3E);
            g.centeredText(tr(o.optionKey(id), id), x + widths[i] / 2 + 1, y + 5, on || hover ? 0xFFFFFFFF : 0xFFD6D0E2, false);
            hit(x, y, widths[i], 18, () -> o.select(id));
            x += widths[i] + 2;
        }
        return true;
    }

    /** Field with the selected option and ▾; a click opens the list below it. */
    private void dropdownField(HudGraphics g, OptionSetting o, int left, int right, int y, int h, boolean hover, int mx, int my) {
        int widest = 0;
        for (String id : o.optionIds()) {
            widest = Math.max(widest, g.textWidth(tr(o.optionKey(id), id)));
        }
        int fw = Math.min(right - left, Math.max(90, widest + 26));
        int fx = right - fw;
        boolean open = dropdown == o;
        g.card(fx, y + 2, fw, 14, 4, HubView.FIELD, open || hover ? LumoraTheme.accent() : 0xFF3A3450);
        String value = tr(o.optionKey(o.selectedId()), o.selectedId());
        g.text(HubView.trim(g, value, fw - 20), fx + 6, y + 5, TEXT, false);
        g.rightText(open ? "▴" : "▾", fx + fw - 5, y + 5, LumoraTheme.accentHover(), false);
        hit(fx, y, fw, h, () -> openDropdown(o, fx, y, fw));
        if (o instanceof Setting<?> s && s.key().equals(pendingPopup)) {
            pendingPopup = null;
            openDropdown(o, fx, y, fw);
        }
    }

    private void openDropdown(OptionSetting o, int fx, int y, int fw) {
        closePopups();
        dropdown = o;
        ddX = fx;
        ddY = y + 17;
        ddW = fw;
        ddScroll = Math.max(0, o.optionIds().indexOf(o.selectedId()) - DD_MAX_ROWS + 1);
    }

    private boolean closeAll() {
        closePopups();
        return true;
    }

    private void closePopups() {
        dropdown = null;
        picker = null;
        pkDrag = null;
    }

    private void drawPopups(HudGraphics g, int mx, int my) {
        popupHits.clear();
        if (dropdown != null) {
            drawDropdown(g, mx, my);
        }
        if (picker != null) {
            drawPicker(g, mx, my);
        }
    }

    private void drawDropdown(HudGraphics g, int mx, int my) {
        OptionSetting o = dropdown;
        List<String> ids = o.optionIds();
        int rows = Math.min(ids.size(), DD_MAX_ROWS);
        int h = rows * DD_ROW + 4;
        int y0 = ddY;
        if (y0 + h > panelBottom && ddY - 19 - h >= panelTop) {
            y0 = ddY - 19 - h; // no room below: above the field
        }
        ddBox = new int[]{ddX, y0, ddW, h};
        ddScroll = Math.max(0, Math.min(ids.size() - rows, ddScroll));
        int accent = LumoraTheme.accent();
        g.roundedRect(ddX - 1, y0 - 1, ddW + 2, h + 2, 5, accent);
        g.roundedRect(ddX, y0, ddW, h, 4, POPUP);
        String selected = o.selectedId();
        for (int r = 0; r < rows; r++) {
            String id = ids.get(r + ddScroll);
            int ry = y0 + 2 + r * DD_ROW;
            boolean hover = HubView.in(mx, my, ddX, ry, ddW, DD_ROW);
            if (id.equals(selected)) {
                g.roundedRect(ddX + 2, ry, ddW - 4, DD_ROW, 3, LumoraTheme.alpha(accent, 0x70));
            } else if (hover) {
                g.roundedRect(ddX + 2, ry, ddW - 4, DD_ROW, 3, 0x22FFFFFF);
            }
            g.text(HubView.trim(g, tr(o.optionKey(id), id), ddW - 14), ddX + 6, ry + 3, TEXT, false);
            popupHits.add(new Hit(ddX, ry, ddX + ddW, ry + DD_ROW, () -> {
                o.select(id);
                closePopups();
            }));
        }
        if (ids.size() > rows) {
            int track = h - 4;
            int bar = Math.max(8, track * rows / ids.size());
            int by = y0 + 2 + (track - bar) * ddScroll / Math.max(1, ids.size() - rows);
            g.roundedRect(ddX + ddW - 4, by, 2, bar, 1, LumoraTheme.alpha(accent, 0xC0));
        }
    }

    private void openPicker(ColorSetting c, int x, int y) {
        closePopups();
        picker = c;
        pkX = x;
        pkY = y;
        float[] hsv = rgbToHsv(c.get());
        pkH = hsv[0];
        pkS = hsv[1];
        pkV = hsv[2];
    }

    private int pickerHeight(ColorSetting c) {
        int h = 6 + PK_SV_H + 6;
        if (c.hasAlpha()) {
            h += 12;
        }
        h += 14;
        if (c.chromaAllowed()) {
            h += 18;
        }
        return h + 2;
    }

    /**
     * Color picker: saturation/brightness square, hue bar, preview, alpha bar (colors with alpha), palette and, where
     * offered, the rainbow with its speed. Dragging in the square or the bars changes the color live.
     */
    private void drawPicker(HudGraphics g, int mx, int my) {
        ColorSetting c = picker;
        int w = PK_W;
        int h = pickerHeight(c);
        int x = Math.max(panelLeft + 4, Math.min(pkX - w + 14, panelRight - w - 4));
        int y = pkY;
        if (y + h > panelBottom && pkY - 22 - h >= panelTop) {
            y = pkY - 22 - h;
        }
        pkBox = new int[]{x, y, w, h};
        int accent = LumoraTheme.accent();
        g.roundedRect(x - 1, y - 1, w + 2, h + 2, 6, accent);
        g.roundedRect(x, y, w, h, 5, POPUP);
        int sx = x + 6;
        int sy = y + 6;
        int sw = PK_SV_W;
        int sh = PK_SV_H;
        pkSv = new int[]{sx, sy, sw, sh};
        for (int cy = 0; cy < sh; cy += 4) {
            for (int cx = 0; cx < sw; cx += 4) {
                float s = (cx + 2f) / sw;
                float v = 1f - (cy + 2f) / sh;
                g.fill(sx + cx, sy + cy, sx + Math.min(sw, cx + 4), sy + Math.min(sh, cy + 4), hsvToRgb(pkH, s, v));
            }
        }
        int kx = sx + Math.round(pkS * (sw - 1));
        int ky = sy + Math.round((1f - pkV) * (sh - 1));
        g.outline(kx - 3, ky - 3, 7, 7, 0xFF000000);
        g.outline(kx - 2, ky - 2, 5, 5, 0xFFFFFFFF);
        int hx = sx + sw + 6;
        pkHue = new int[]{hx, sy, 10, sh};
        for (int cy = 0; cy < sh; cy += 2) {
            g.fill(hx, sy + cy, hx + 10, sy + Math.min(sh, cy + 2), hsvToRgb(cy / (float) sh, 1f, 1f));
        }
        int hy = sy + Math.round(pkH * (sh - 1));
        g.fill(hx - 2, hy - 1, hx + 12, hy + 2, 0xFF000000);
        g.fill(hx - 1, hy, hx + 11, hy + 1, 0xFFFFFFFF);
        // preview of what is drawn now (with the rainbow)
        int px = hx + 16;
        int pw = x + w - 6 - px;
        // (the hex value stays in the row below the picker)
        checker(g, px, sy, pw, sh);
        g.fill(px, sy, px + pw, sy + sh, c.argb());
        g.outline(px, sy, pw, sh, 0xFF4A4458);
        int ny = sy + sh + 6;
        pkAlpha = null;
        if (c.hasAlpha()) {
            int aw = w - 12;
            pkAlpha = new int[]{sx, ny, aw, 8};
            checker(g, sx, ny, aw, 8);
            int rgb = c.get() & 0xFFFFFF;
            for (int i = 0; i < aw; i += 2) {
                g.fill(sx + i, ny, sx + Math.min(aw, i + 2), ny + 8, (Math.round(255f * i / (aw - 1)) << 24) | rgb);
            }
            int ax = sx + Math.round(((c.get() >>> 24) / 255f) * (aw - 1));
            g.fill(ax - 1, ny - 2, ax + 2, ny + 10, 0xFF000000);
            g.fill(ax, ny - 1, ax + 1, ny + 9, 0xFFFFFFFF);
            ny += 12;
        }
        // palette
        for (int i = 0; i < PALETTE.length; i++) {
            int bx = sx + i * 13;
            int col = PALETTE[i];
            boolean hover = HubView.in(mx, my, bx, ny, 11, 11);
            g.roundedRect(bx, ny, 11, 11, 3, hover ? 0xFFFFFFFF : 0xFF4A4458);
            g.fill(bx + 1, ny + 1, bx + 10, ny + 10, col);
            popupHits.add(new Hit(bx, ny, bx + 11, ny + 11, () -> {
                c.set(c.hasAlpha() ? (c.get() & 0xFF000000) | (col & 0xFFFFFF) : col);
                float[] hsv = rgbToHsv(c.get());
                pkH = hsv[0];
                pkS = hsv[1];
                pkV = hsv[2];
            }));
        }
        ny += 14;
        if (c.chromaAllowed()) {
            boolean hover = HubView.in(mx, my, sx, ny, 70, 14);
            checkbox(g, sx, ny + 1, c.isChroma(), hover);
            g.text(t("Regenbogen", "Rainbow"), sx + 16, ny + 3, c.isChroma() ? TEXT : MUTED, false);
            popupHits.add(new Hit(sx, ny, sx + 16 + g.textWidth(t("Regenbogen", "Rainbow")), ny + 14,
                    () -> c.setChroma(!c.isChroma())));
            // speed: − n +
            int rx = x + w - 6;
            String speed = Integer.toString(c.chromaSpeed());
            int vw = 16;
            int plus = rx - 12;
            int minus = plus - vw - 14;
            popupButton(g, minus, ny + 1, "−", mx, my, () -> c.setChromaSpeed(c.chromaSpeed() - 1));
            g.centeredText(speed, minus + 12 + vw / 2 + 1, ny + 3, c.isChroma() ? TEXT : DIM, false);
            popupButton(g, plus, ny + 1, "+", mx, my, () -> c.setChromaSpeed(c.chromaSpeed() + 1));
            g.rightText(t("Tempo", "Speed"), minus - 4, ny + 3, DIM, false);
        }
    }

    private void popupButton(HudGraphics g, int x, int y, String label, int mx, int my, Runnable action) {
        boolean hover = HubView.in(mx, my, x, y, 12, 12);
        g.roundedRect(x, y, 12, 12, 3, hover ? LumoraTheme.accent() : 0xFF2E2A3E);
        g.centeredText(label, x + 6 + 1, y + 2, TEXT, false);
        popupHits.add(new Hit(x, y, x + 12, y + 12, action));
    }

    private static void checker(HudGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8A8499);
        for (int cy = 0; cy < h; cy += 4) {
            for (int cx = (cy / 4) % 2 * 4; cx < w; cx += 8) {
                g.fill(x + cx, y + cy, x + Math.min(w, cx + 4), y + Math.min(h, cy + 4), 0xFFC9C4D6);
            }
        }
    }

    /** Applies a drag in the color picker ("sv", "hue" or "alpha") at the mouse position. */
    private void dragPicker(double mx, double my) {
        ColorSetting c = picker;
        if (c == null || pkDrag == null) {
            return;
        }
        int alpha = c.get() >>> 24;
        switch (pkDrag) {
            case "sv" -> {
                pkS = clamp01((float) ((mx - pkSv[0]) / Math.max(1, pkSv[2] - 1)));
                pkV = 1f - clamp01((float) ((my - pkSv[1]) / Math.max(1, pkSv[3] - 1)));
            }
            case "hue" -> pkH = Math.min(0.999f, clamp01((float) ((my - pkHue[1]) / Math.max(1, pkHue[3] - 1))));
            case "alpha" -> alpha = Math.round(255 * clamp01((float) ((mx - pkAlpha[0]) / Math.max(1, pkAlpha[2] - 1))));
            default -> {
                return;
            }
        }
        int rgb = hsvToRgb(pkH, pkS, pkV) & 0xFFFFFF;
        c.set(c.hasAlpha() ? (alpha << 24) | rgb : 0xFF000000 | rgb);
    }

    private static boolean inBox(int[] box, double x, double y) {
        return box != null && x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
    }

    /** Mouse press while a drop-down or the color picker is open; the click never reaches the rows below. */
    private boolean popupClick(double x, double y) {
        if (picker != null) {
            if (inBox(pkSv, x, y)) {
                pkDrag = "sv";
            } else if (inBox(pkHue, x, y)) {
                pkDrag = "hue";
            } else if (inBox(pkAlpha, x, y)) {
                pkDrag = "alpha";
            }
            if (pkDrag != null) {
                dragPicker(x, y);
                return true;
            }
        }
        for (int i = popupHits.size() - 1; i >= 0; i--) {
            if (popupHits.get(i).contains(x, y)) {
                popupHits.get(i).action().run();
                return true;
            }
        }
        if ((picker != null && inBox(pkBox, x, y)) || (dropdown != null && inBox(ddBox, x, y))) {
            return true;
        }
        closePopups();
        return true;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Color of hue, saturation and value (each 0..1), opaque. */
    static int hsvToRgb(float h, float s, float v) {
        float hh = ((h % 1f) + 1f) % 1f * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** Hue, saturation and value (each 0..1) of a color. */
    static float[] rgbToHsv(int argb) {
        float r = (argb >> 16 & 0xFF) / 255f;
        float g = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) % 6f;
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
            if (h < 0) {
                h += 1f;
            }
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private void drawTooltip(HudGraphics g, int width, int height, int mx, int my) {
        if (tooltip == null || tooltip.isBlank()) {
            return;
        }
        List<String> lines = HubView.wrap(g, tooltip, 190);
        int tw = 0;
        for (String l : lines) {
            tw = Math.max(tw, g.textWidth(l));
        }
        tw += 12;
        int th = lines.size() * 10 + 8;
        int x = Math.min(mx + 10, width - tw - 4);
        int y = Math.min(my + 12, height - th - 4);
        g.card(x, y, tw, th, 4, 0xF2100E18, LumoraTheme.alpha(LumoraTheme.accent(), 0xB0));
        int ly = y + 5;
        for (String l : lines) {
            g.text(l, x + 6, ly, MUTED, false);
            ly += 10;
        }
    }

    private void drawToast(HudGraphics g, int cx, int y, long now) {
        if (toast.isEmpty()) {
            return;
        }
        long age = now - toastAt;
        if (age > 3500) {
            toast = "";
            return;
        }
        float a = age < 150 ? age / 150f : age > 3000 ? (3500 - age) / 500f : 1f;
        int tw = Math.min(g.textWidth(toast), 320) + 20;
        int alpha = Math.round(0xF0 * a);
        g.roundedRect(cx - tw / 2, y, tw, 18, 9, LumoraTheme.alpha(0x100E18, alpha));
        g.roundedRect(cx - tw / 2 + 4, y + 6, 6, 6, 3, LumoraTheme.alpha(LumoraTheme.accent(), alpha));
        g.text(HubView.trim(g, toast, 320), cx - tw / 2 + 14, y + 5, LumoraTheme.alpha(TEXT, Math.max(8, alpha)), false);
    }

    private String format(double v) {
        return String.format(de ? Locale.GERMANY : Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.2f", v);
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        int y1 = y;
        int y2 = y + h;
        if (clipHits) {
            y1 = Math.max(y1, contentTop);
            y2 = Math.min(y2, contentBottom);
        }
        if (y2 > y1) {
            hits.add(new Hit(x, y1, x + w, y2, action));
        }
    }

    // ------------------------------------------------------------------ text fields and input

    private void focus(String id, String value) {
        commit();
        focus = id;
        buffer.setLength(0);
        buffer.append(value == null ? "" : value);
    }

    /** Leaves the focused field; a colour keeps its last valid value. */
    private void commit() {
        focus = null;
        buffer.setLength(0);
    }

    private void edited() {
        if (focus == null) {
            return;
        }
        String v = buffer.toString();
        if (focus.equals("search")) {
            search = v;
            scrollTarget = 0;
            return;
        }
        String key = focus.substring(focus.indexOf(':') + 1);
        Setting<?> s = group == null ? null : group.get(key).orElse(null);
        if (s instanceof TextSetting t) {
            t.set(v);
        } else if (s instanceof ColorSetting c) {
            Integer parsed = Colors.parseHex(v);
            if (parsed != null) {
                c.set(c.hasAlpha() || v.replace("#", "").length() > 6 ? parsed : parsed | 0xFF000000);
            }
        }
    }

    private int maxLength() {
        if (focus == null) {
            return 0;
        }
        if (focus.equals("search")) {
            return 32;
        }
        if (focus.startsWith("hex:")) {
            return 9;
        }
        Setting<?> s = group == null ? null : group.get(focus.substring(5)).orElse(null);
        return s instanceof TextSetting t ? t.maxLength() : 64;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (capture != null) {
            capture = null;
            captureId = null;
        }
        if (dropdown != null || picker != null) {
            commit();
            return button != 0 ? closeAll() : popupClick(x, y);
        }
        if (panel != null && search.isBlank() && visible((int) y) && panel.mouseClicked(x, y, button)) {
            commit();
            panelDrag = true;
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (Slider sl : sliders) {
            if (x >= sl.x1() - 6 && x < sl.x2() + 6 && y >= sl.y1() && y < sl.y2()) {
                commit();
                dragging = sl;
                apply(sl, x);
                return true;
            }
        }
        // any click leaves the focused field; clicking a field focuses it again
        commit();
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                hits.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button) {
        if (pkDrag != null) {
            dragPicker(x, y);
            return true;
        }
        if (panelDrag) {
            panel.mouseDragged(x, y, button);
            return true;
        }
        if (dragging != null) {
            apply(dragging, x);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (pkDrag != null) {
            pkDrag = null;
            return true;
        }
        boolean was = dragging != null || panelDrag;
        if (panelDrag) {
            panelDrag = false;
            panel.mouseReleased();
        }
        dragging = null;
        return was;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (dropdown != null && inBox(ddBox, x, y)) {
            ddScroll -= (int) Math.signum(delta);
            return true;
        }
        closePopups();
        scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - delta * 24));
        return true;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (capture != null) {
            IntConsumer c = capture;
            capture = null;
            captureId = null;
            if (key == Keys.BACKSPACE || key == Keys.DELETE) {
                c.accept(Keys.UNBOUND);
            } else if (key != Keys.ESCAPE) {
                c.accept(key);
            }
            return true;
        }
        if (ctrl && key == Keys.F) {
            focus("search", search);
            return true;
        }
        if (focus == null) {
            return false;
        }
        if (key == Keys.BACKSPACE) {
            if (ctrl) {
                buffer.setLength(0);
            } else if (!buffer.isEmpty()) {
                buffer.setLength(buffer.length() - 1);
            }
            edited();
            return true;
        }
        if (key == Keys.ENTER || key == Keys.TAB) {
            commit();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch) {
        if (ch < 32 || ch == 127 || Character.isISOControl(ch) || System.currentTimeMillis() - openedAt < 200) {
            return focus != null;
        }
        if (focus == null) {
            if (ch == ' ') {
                return false;
            }
            focus("search", search);
        }
        if (buffer.length() < maxLength()) {
            buffer.append(ch);
            edited();
        }
        return true;
    }

    @Override
    public boolean closeRequested() {
        if (dropdown != null || picker != null) {
            closePopups();
            return false;
        }
        if (capture != null) {
            capture = null;
            captureId = null;
            return false;
        }
        if (focus != null) {
            commit();
            return false;
        }
        if (!search.isEmpty()) {
            search = "";
            return false;
        }
        return true;
    }

    @Override
    public void removed() {
        if (!closed) {
            closed = true;
            if (onClose != null) {
                onClose.run();
            }
        }
    }
}
