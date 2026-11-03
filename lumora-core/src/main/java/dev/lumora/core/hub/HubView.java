package dev.lumora.core.hub;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.addon.AddonRecord;
import dev.lumora.core.hud.HudLayout;
import dev.lumora.core.theme.LumoraTheme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Lumora hub (Right Shift in game, "✦ Lumora" in the main and pause menu): a sidebar with the module categories
 * and the general pages (themes, HUD editor, profiles, system), and a grid of module cards (HUD widgets, built-in
 * modules, addons, zoom) with search, on/off and settings. Drawn the same way on every Minecraft version; the
 * version only provides a {@link HubHost}.
 */
public final class HubView implements AddonView {
    /** What the main area shows. */
    public enum Page { MODULES, THEMES, PROFILES, SYSTEM, ZOOM }

    static final int TEXT = 0xFFECE8F4;
    static final int MUTED = 0xFF9D95AE;
    static final int DIM = 0xFF6B6580;
    static final int MAIN = 0xFF14121C;
    static final int SIDE = 0xFF0E0C14;
    static final int BORDER = 0xFF2A2638;
    static final int CARD = 0xFF1C1928;
    static final int CARD_HOVER = 0xFF241F34;
    static final int FIELD = 0xFF0F0D16;
    /** On / off of the module cards: blue and red. */
    static final int ON = 0xFF2F64D8;
    static final int ON_HOVER = 0xFF4579EE;
    static final int OFF = 0xFFB8243F;
    static final int OFF_HOVER = 0xFFD03755;
    static final int FAILED = 0xFFB7791F;
    static final int OK = 0xFF3DD68C;
    static final int WARN = 0xFFF5B342;
    static final TextureRef BACKGROUND = new TextureRef("lumora", "textures/gui/background_menu.png");
    static final TextureRef LOGO = new TextureRef("lumora", "icon.png");

    /** Built-in addons whose widgets get their own cards (the addon itself has no card). */
    private static final Set<String> WIDGET_CONTAINERS = Set.of("lumora", "lumora-info");
    /** Category and icon of the built-in modules. */
    private static final Map<String, String[]> BUILT_IN = Map.of(
            "lumora-crosshair", new String[]{WidgetCategory.COMBAT, "crosshair"},
            "lumora-blockoverlay", new String[]{WidgetCategory.WORLD, "blockoverlay"},
            "lumora-buttons", new String[]{WidgetCategory.GENERAL, "buttons"},
            "lumora-browser", new String[]{WidgetCategory.SERVER, "browser"},
            "lumora-connection", new String[]{WidgetCategory.SERVER, "connection"},
            "lumora-togglesprint", new String[]{WidgetCategory.PLAYER, "togglesprint"},
            "lumora-fullbright", new String[]{WidgetCategory.WORLD, "fullbright"},
            "lumora-chat", new String[]{WidgetCategory.SERVER, "chat"},
            "lumora-tab", new String[]{WidgetCategory.SERVER, "tab"});
    /** Widgets with their own icon (id without addon); others use the addon icon. */
    private static final Set<String> WIDGET_ICONS = Set.of("fps", "cps", "coords", "clock", "ping", "speed", "server",
            "keystrokes", "armor", "blockinfo", "potions", "items", "compass", "memory", "day", "packs", "combo",
            "togglesprint");
    private static final List<String> CATEGORY_ORDER = List.of(WidgetCategory.GENERAL, WidgetCategory.PLAYER,
            WidgetCategory.COMBAT, WidgetCategory.WORLD, WidgetCategory.SERVER, WidgetCategory.TIME, WidgetCategory.VOICE);
    static final String ALL = "all";
    static final String ADDONS = "addons";
    /** Addons switched with the theme (Lumora's server and world lists, connection screens, button design). */
    static final List<String> THEME_ADDONS = List.of("lumora-browser", "lumora-connection", "lumora-buttons");
    private static final int[] DURATIONS = {150, 220, 350, 500};
    private static final double[] ZOOM_FACTORS = {2, 3, 4, 6, 8, 10};
    private static final String[] SWING = {"vanilla", "smooth", "spin", "slap"};

    private static Page page = Page.MODULES;
    private static String category = ALL;

    /** One module card. */
    record Card(String id, String name, String description, String category, String icon, boolean enabled,
                String failure, Runnable toggle, Runnable settings, Runnable menu, String from) {
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final LumoraCore core;
    private final HubHost host;
    private final boolean de;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, TextureRef> icons = new LinkedHashMap<>();
    private long openedAt;
    private String search = "";
    private boolean searchFocused;
    private float scroll;
    private float scrollTarget;
    private int maxScroll;
    private float sideScroll;
    private int sideMax;
    private int contentTop;
    private int contentBottom;
    private int sideTop;
    private int sideBottom;
    private int sideLeft;
    private int sideRight;
    private String hovered;
    private long hoveredSince;
    private Card tooltipCard;
    private String toast = "";
    private long toastAt;
    private Page lastPage;
    /** Click areas are limited to the visible content (while the scrolling content is drawn). */
    private boolean clipHits;
    private String lastCategory;

    public HubView(LumoraCore core, HubHost host) {
        this.core = core;
        this.host = host;
        Translations tr = core.translations();
        String lang = tr == null ? "" : tr.language();
        this.de = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
    }

    /** Hub opened on a page (smoke test): 0 modules, 1 themes, 2 zoom, 3 profiles, 4 system. */
    public static void select(int index) {
        page = switch (index) {
            case 1 -> Page.THEMES;
            case 2 -> Page.ZOOM;
            case 3 -> Page.PROFILES;
            case 4 -> Page.SYSTEM;
            default -> Page.MODULES;
        };
        category = ALL;
    }

    String t(String german, String english) {
        return de ? german : english;
    }

    // ------------------------------------------------------------------ data

    private TextureRef icon(String name) {
        return icons.computeIfAbsent(name, n -> new TextureRef("lumora", "textures/gui/hub/" + n + ".png"));
    }

    private boolean bool(String option) {
        return Boolean.TRUE.equals(host.get(option));
    }

    private String str(String option, String fallback) {
        Object v = host.get(option);
        return v == null ? fallback : String.valueOf(v);
    }

    private double num(String option, double fallback) {
        Object v = host.get(option);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    /** All cards: zoom, built-in modules and addons, HUD widgets. */
    List<Card> cards() {
        List<Card> out = new ArrayList<>();
        if (host.has("zoomEnabled")) {
            out.add(new Card("zoom", "Zoom", t("Heranzoomen mit Taste ", "Zoom in with key ") + host.zoomKey()
                    + t(", Faktor, Modus, Kinokamera", ", factor, mode, cinematic camera"), WidgetCategory.PLAYER, "zoom",
                    bool("zoomEnabled"), null, () -> host.set("zoomEnabled", !bool("zoomEnabled")), () -> open(Page.ZOOM),
                    null, null));
        }
        List<String> addonNames = new ArrayList<>();
        for (AddonInfo a : core.addons()) {
            if (WIDGET_CONTAINERS.contains(a.id())) {
                continue;
            }
            AddonRecord r = core.addonManager().record(a.id()).orElse(null);
            boolean hasSettings = r != null && r.configuration() != null && (!r.configuration().settings().isEmpty()
                    || !core.keybindManager().of(a.id()).isEmpty());
            Runnable settings = hasSettings ? () -> host.openSettings(a.descriptor().name(), a.descriptor().description(),
                    r.configuration().settings(), core.keybindManager().of(a.id()), r.configuration()::save, false) : null;
            Runnable menu = core.views().hasMenu(a.id()) ? () -> core.views().openMenu(a.id()) : null;
            boolean failed = a.state() == AddonState.FAILED;
            String[] builtIn = BUILT_IN.get(a.id());
            Runnable toggle = failed && !a.wanted() ? null : () -> core.addonManager().setWanted(a.id(), !a.wanted())
                    .ifPresent(this::toast);
            String desc = a.descriptor().description();
            out.add(new Card("addon:" + a.id(), a.descriptor().name(), desc == null ? "" : desc,
                    builtIn != null ? builtIn[0] : ADDONS, builtIn != null ? builtIn[1] : "addon",
                    a.wanted() && !failed, failed ? a.error().orElse(t("Fehler beim Laden", "Failed to load")) : null,
                    toggle, settings, menu, builtIn == null && !a.descriptor().builtIn() ? a.descriptor().source() : null));
            addonNames.add(a.descriptor().name().toLowerCase(Locale.ROOT));
        }
        Translations tr = core.translations();
        for (WidgetInfo w : core.widgets()) {
            String name = tr == null ? w.widget().id() : tr.translateOr(w.widget().nameKey(), w.widget().id());
            if (addonNames.contains(name.toLowerCase(Locale.ROOT))) {
                name = name + " HUD";
            }
            String desc = tr != null && tr.has(w.widget().descriptionKey()) ? tr.translate(w.widget().descriptionKey()) : "";
            String from = null;
            if (!WIDGET_CONTAINERS.contains(w.addonId())) {
                from = core.addon(w.addonId()).map(a -> a.descriptor().name()).orElse(w.addonId());
            }
            boolean hasSettings = !w.widget().settings().isEmpty();
            String widgetName = name;
            String widgetDesc = desc;
            String icon = WIDGET_ICONS.contains(w.widget().id()) && (WIDGET_CONTAINERS.contains(w.addonId())
                    || w.addonId().startsWith("lumora-")) ? w.widget().id() : "widget";
            out.add(new Card("widget:" + w.id(), name, desc, w.category(), icon, w.isEnabled(), w.failure().orElse(null),
                    () -> w.setEnabled(!w.isEnabled()),
                    hasSettings ? () -> host.openSettings(widgetName, widgetDesc, w.widget().settings(), List.of(), null, true) : null,
                    null, from));
        }
        out.sort(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Categories that have cards, in sidebar order (all first, addons last). */
    static List<String> categories(List<Card> cards) {
        List<String> found = new ArrayList<>();
        for (Card c : cards) {
            if (!found.contains(c.category())) {
                found.add(c.category());
            }
        }
        List<String> out = new ArrayList<>();
        out.add(ALL);
        for (String c : CATEGORY_ORDER) {
            if (found.contains(c)) {
                out.add(c);
            }
        }
        found.stream().filter(c -> !CATEGORY_ORDER.contains(c) && !ADDONS.equals(c)).sorted().forEach(out::add);
        if (found.contains(ADDONS)) {
            out.add(ADDONS);
        }
        return out;
    }

    /** Cards of a category matching the search (name, description or source). */
    static List<Card> filter(List<Card> cards, String category, String search) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Card> out = new ArrayList<>();
        for (Card c : cards) {
            if (!ALL.equals(category) && !c.category().equals(category)) {
                continue;
            }
            if (!q.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(q)
                    && !c.description().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    String categoryName(String c) {
        return switch (c) {
            case ALL -> t("Alle", "All");
            case WidgetCategory.GENERAL -> t("Anzeige", "Display");
            case WidgetCategory.PLAYER -> t("Spieler", "Player");
            case WidgetCategory.COMBAT -> t("Kampf", "Combat");
            case WidgetCategory.WORLD -> t("Welt", "World");
            case WidgetCategory.SERVER -> "Server";
            case WidgetCategory.TIME -> t("Zeit", "Time");
            case WidgetCategory.VOICE -> "Voice";
            case ADDONS -> "Addons";
            default -> {
                Translations tr = core.translations();
                String fallback = c.isEmpty() ? c : Character.toUpperCase(c.charAt(0)) + c.substring(1);
                yield tr == null ? fallback : tr.translateOr(WidgetCategory.nameKey(c), fallback);
            }
        };
    }

    private static String categoryGlyph(String c) {
        return switch (c) {
            case ALL, WidgetCategory.GENERAL, WidgetCategory.PLAYER, WidgetCategory.COMBAT, WidgetCategory.WORLD,
                 WidgetCategory.SERVER, WidgetCategory.TIME, WidgetCategory.VOICE, ADDONS -> c;
            default -> "addons";
        };
    }

    // ------------------------------------------------------------------ actions

    private void open(Page p) {
        page = p;
        scroll = 0;
        scrollTarget = 0;
        searchFocused = false;
    }

    private void openCategory(String c) {
        category = c;
        open(Page.MODULES);
    }

    void toast(String message) {
        toast = message == null ? "" : message;
        toastAt = System.currentTimeMillis();
    }

    /** Lumora or Vanilla look for all Lumora menus (the hub itself stays as it is). */
    void applyTheme(String theme) {
        boolean lumora = !LumoraTheme.VANILLA.equals(theme);
        host.set("theme", lumora ? LumoraTheme.LUMORA : LumoraTheme.VANILLA);
        for (String option : List.of("customMainMenu", "menuBackground", "customLoadingScreen")) {
            if (host.has(option)) {
                host.set(option, lumora);
            }
        }
        if (host.has("lumoraFont") && host.fontSupported()) {
            host.set("lumoraFont", lumora);
        }
        if (host.has("menuShaders")) {
            host.set("menuShaders", lumora ? "auto" : "off");
        }
        for (String id : THEME_ADDONS) {
            if (core.addon(id).isPresent()) {
                core.addonManager().setWanted(id, lumora).ifPresent(this::toast);
            }
        }
        toast(lumora ? t("Lumora-Design aktiv", "Lumora theme on")
                : t("Vanilla-Design aktiv: Menüs wie in Minecraft", "Vanilla theme on: Minecraft's own menus"));
    }

    private void newProfile() {
        List<String> ids = core.hudEditor().profiles();
        int n = 1;
        while (ids.contains("custom-" + n)) {
            n++;
        }
        String id = "custom-" + n;
        if (core.hudEditor().activateProfile(id)) {
            toast(t("Profil gespeichert: ", "Profile saved: ") + profileName(id));
        }
    }

    String profileName(String id) {
        Translations tr = core.translations();
        String key = "lumora.hud.profile." + id;
        if (tr != null && tr.has(key)) {
            return tr.translate(key);
        }
        if (id.startsWith("custom-")) {
            return t("Eigenes Profil ", "Custom profile ") + id.substring("custom-".length());
        }
        return id;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
        hits.clear();
        tooltipCard = null;
        long now = System.currentTimeMillis();
        if (openedAt == 0) {
            openedAt = now;
        }
        if (page != lastPage || !category.equals(lastCategory)) {
            lastPage = page;
            lastCategory = category;
            scroll = 0;
            scrollTarget = 0;
        }
        float p = Math.min(1f, (now - openedAt) / 220f);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        background(g, width, height, e);

        int margin = width >= 560 ? 18 : 8;
        int pw = Math.min(width - 2 * margin, 780);
        int ph = Math.min(height - 2 * margin, 450);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        boolean compact = ph < 300;
        int sw = Math.max(104, Math.min(132, pw / 5));

        g.push();
        float s = 0.965f + 0.035f * e;
        g.translate(width / 2f, height / 2f + (1f - e) * 6f);
        g.scale(s);
        g.translate(-width / 2f, -height / 2f);
        int accent = LumoraTheme.accent();
        for (int i = 3; i >= 1; i--) {
            g.roundedRect(px - i * 2, py - i * 2, pw + i * 4, ph + i * 4, 8 + i * 2, LumoraTheme.alpha(accent, (int) (10 * e / i)));
        }
        g.roundedRect(px - 1, py - 1, pw + 2, ph + 2, 8, BORDER);
        g.roundedRect(px, py, pw, ph, 7, MAIN);
        g.roundedRect(px, py, sw + 8, ph, 7, SIDE);
        g.fill(px + sw, py, px + sw + 8, py + ph, MAIN);
        g.fill(px + sw, py, px + sw + 1, py + ph, BORDER);

        List<Card> cards = cards();
        sidebar(g, px, py, sw, ph, compact, cards, mouseX, mouseY);
        main(g, px + sw + 1, py, pw - sw - 1, ph, compact, cards, mouseX, mouseY);
        g.pop();
        tooltip(g, width, height, mouseX, mouseY, now);
        drawToast(g, px + sw + 1 + (pw - sw - 1) / 2, py + ph - 26, now);
    }

    private void background(HudGraphics g, int w, int h, float e) {
        background(g, w, h, e, host.inWorld());
    }

    /** Menu background (or a veil over the running world), shared with the settings view. */
    static void background(HudGraphics g, int w, int h, float e, boolean inWorld) {
        if (inWorld) {
            // the game stays visible behind a dark veil
            g.fill(0, 0, w, h, LumoraTheme.alpha(0x0B0910, (int) (0x60 + 0x50 * e)));
            return;
        }
        float s = Math.max(w / 1920f, h / 1080f);
        int rw = Math.min(1920, Math.round(w / s));
        int rh = Math.min(1080, Math.round(h / s));
        g.texture(BACKGROUND, 0, 0, w, h, (1920 - rw) / 2f, (1080 - rh) / 2f, rw, rh, 1920, 1080);
        g.fill(0, 0, w, h, LumoraTheme.alpha(0x0B0910, (int) (0x90 + 0x30 * e)));
    }

    // ---- sidebar

    private void sidebar(HudGraphics g, int x, int y, int w, int h, boolean compact, List<Card> cards, int mx, int my) {
        int accent = LumoraTheme.accent();
        int by = y + (compact ? 8 : 14);
        int logo = compact ? 18 : 20;
        g.texture(LOGO, x + 12, by, logo, logo, 128, 128);
        g.push();
        g.translate(x + 17 + logo, by - (compact ? 1 : 0));
        g.scale(1.25f);
        g.text("§lLumora", 0, 0, TEXT, true);
        g.pop();
        g.text("Hub", x + 17 + logo, by + (compact ? 10 : 12), LumoraTheme.accentHover(), false);

        int ih = compact ? 13 : 17;
        int gap = compact ? 1 : 2;
        int buttonH = compact ? 12 : 14;
        int bottomH = buttonH + (compact ? 4 : 6) + (compact ? 10 : 12) + 4 * ih + (compact ? 6 : 12);
        sideLeft = x;
        sideRight = x + w;
        sideTop = by + (compact ? 25 : 34);
        sideBottom = y + h - bottomH - 4;
        List<String> cats = categories(cards);
        int listH = cats.size() * (ih + gap);
        sideMax = Math.max(0, listH - (sideBottom - sideTop));
        sideScroll = Math.max(0, Math.min(sideMax, sideScroll));
        g.enableScissor(x, sideTop, x + w, sideBottom);
        int iy = sideTop - Math.round(sideScroll);
        for (String c : cats) {
            boolean selected = page == Page.MODULES && c.equals(category);
            int count = ALL.equals(c) ? cards.size() : (int) cards.stream().filter(k -> k.category().equals(c)).count();
            sideItem(g, x + 8, iy, w - 16, ih, categoryGlyph(c), categoryName(c), String.valueOf(count), selected, mx, my,
                    iy >= sideTop - ih && iy < sideBottom, () -> openCategory(c));
            iy += ih + gap;
        }
        if (sideMax > 0) {
            // more categories below / above: fade the list edge
            for (int i = 0; i < 6; i++) {
                int a = 0xE0 - i * 0x24;
                if (sideScroll < sideMax) {
                    g.fill(x, sideBottom - 1 - i, x + w, sideBottom - i, LumoraTheme.alpha(SIDE, a));
                }
                if (sideScroll > 0) {
                    g.fill(x, sideTop + i, x + w, sideTop + i + 1, LumoraTheme.alpha(SIDE, a));
                }
            }
        }
        g.disableScissor();

        int yb = y + h - bottomH;
        String label = t("+ Neues Profil", "+ New profile");
        int bw = w - 20;
        boolean hover = in(mx, my, x + 10, yb, bw, buttonH);
        g.card(x + 10, yb, bw, buttonH, 3, hover ? LumoraTheme.alpha(accent, 0x30) : SIDE, hover ? accent : 0xFF3A3450);
        g.centeredText(label, x + 10 + bw / 2, yb + (buttonH - 8) / 2, hover ? TEXT : MUTED, false);
        hit(x + 10, yb, bw, buttonH, this::newProfile);
        yb += buttonH + (compact ? 4 : 6);
        spaced(g, t("ALLGEMEIN", "GENERAL"), x + 12, yb, DIM);
        yb += compact ? 10 : 12;
        sideItem(g, x + 8, yb, w - 16, ih, "themes", "Themes", null, page == Page.THEMES, mx, my, true, () -> open(Page.THEMES));
        yb += ih;
        sideItem(g, x + 8, yb, w - 16, ih, "hudeditor", t("HUD-Editor", "HUD editor"), null, false, mx, my, true, host::openHudEditor);
        yb += ih;
        sideItem(g, x + 8, yb, w - 16, ih, "profiles", t("Profile", "Profiles"), null, page == Page.PROFILES, mx, my, true,
                () -> open(Page.PROFILES));
        yb += ih;
        sideItem(g, x + 8, yb, w - 16, ih, "system", "System", null, page == Page.SYSTEM, mx, my, true, () -> open(Page.SYSTEM));
    }

    private void sideItem(HudGraphics g, int x, int y, int w, int h, String glyph, String label, String badge,
                          boolean selected, int mx, int my, boolean clickable, Runnable action) {
        int accent = LumoraTheme.accent();
        boolean hover = clickable && in(mx, my, x, y, w, h) && (my >= sideTop && my < sideBottom || y >= sideBottom);
        if (selected) {
            g.roundedRect(x, y, w, h, 4, LumoraTheme.alpha(accent, 0x30));
            g.fill(x, y + 3, x + 2, y + h - 3, accent);
        } else if (hover) {
            g.roundedRect(x, y, w, h, 4, 0x14FFFFFF);
        }
        int gy = y + (h - HubGlyphs.SIZE) / 2;
        HubGlyphs.draw(g, glyph, x + 6, gy, selected ? LumoraTheme.accentHover() : hover ? TEXT : DIM);
        g.text(label, x + 20, y + (h - 8) / 2, selected || hover ? TEXT : MUTED, false);
        if (selected) {
            g.roundedRect(x + w - 9, y + h / 2 - 2, 4, 4, 2, accent);
        } else if (badge != null) {
            g.rightText(badge, x + w - 5, y + (h - 8) / 2, DIM, false);
        }
        if (clickable) {
            hit(x, y, w, h, action);
        }
    }

    // ---- main area

    private void main(HudGraphics g, int x, int y, int w, int h, boolean compact, List<Card> cards, int mx, int my) {
        int pad = compact ? 10 : 14;
        int left = x + pad;
        int right = x + w - pad;
        int ty = y + (compact ? 10 : 14);
        float titleScale = compact ? 1.5f : 2f;
        int titleH = Math.round(8 * titleScale);
        String title = switch (page) {
            case MODULES -> categoryName(category);
            case THEMES -> "Themes";
            case PROFILES -> t("Profile", "Profiles");
            case SYSTEM -> "System";
            case ZOOM -> "Zoom";
        };
        int titleX = left;
        if (page == Page.ZOOM) {
            boolean hover = in(mx, my, left - 2, ty - 2, 14, titleH + 4);
            HubGlyphs.draw(g, "back", left, ty + (titleH - HubGlyphs.SIZE) / 2, hover ? TEXT : MUTED);
            hit(left - 2, ty - 2, 14, titleH + 4, () -> open(Page.MODULES));
            titleX += 16;
        }
        g.push();
        g.translate(titleX, ty);
        g.scale(titleScale);
        g.text("§l" + title, 0, 0, TEXT, true);
        g.pop();

        List<Card> shown = filter(cards, category, search);
        if (page == Page.MODULES) {
            int sbw = Math.max(96, Math.min(170, (right - left) * 38 / 100));
            int sbh = 16;
            int sby = ty + (titleH - sbh) / 2;
            searchBox(g, right - sbw, sby, sbw, sbh, mx, my);
            String count = shown.size() + (shown.size() == 1 ? t(" Modul", " module") : t(" Module", " modules"));
            int titleEnd = titleX + Math.round(g.textWidth("§l" + title) * titleScale);
            if (right - sbw - 8 - g.textWidth(count) > titleEnd + 8) {
                g.rightText(count, right - sbw - 8, sby + 4, MUTED, false);
            }
        } else if (page == Page.PROFILES) {
            int bw = g.textWidth(t("+ Neues Profil", "+ New profile")) + 16;
            button(g, right - bw, ty + (titleH - 16) / 2, bw, 16, t("+ Neues Profil", "+ New profile"), true, mx, my, this::newProfile);
        }

        int ly = ty + titleH + (compact ? 8 : 12);
        accentLine(g, x, ly, w);
        contentTop = ly + (compact ? 8 : 10);
        contentBottom = y + h - (compact ? 6 : 10);
        scroll += (scrollTarget - scroll) * 0.4f;
        if (Math.abs(scrollTarget - scroll) < 0.5f) {
            scroll = scrollTarget;
        }
        g.enableScissor(x, contentTop - 2, x + w, contentBottom);
        clipHits = true;
        int top = contentTop - Math.round(scroll);
        int used = switch (page) {
            case MODULES -> grid(g, left, top, right - left, compact, shown, mx, my);
            case THEMES -> themes(g, left, top, right - left, mx, my);
            case PROFILES -> profiles(g, left, top, right - left, mx, my);
            case SYSTEM -> system(g, left, top, right - left, mx, my);
            case ZOOM -> zoom(g, left, top, right - left, mx, my);
        };
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
            g.roundedRect(x + w - 5, contentTop, 2, trackH, 1, 0x18FFFFFF);
            g.roundedRect(x + w - 5, barY, 2, barH, 1, LumoraTheme.alpha(LumoraTheme.accent(), 0xC0));
        }
    }

    /** Accent line under the title, fading out to the right. */
    private static void accentLine(HudGraphics g, int x, int y, int w) {
        int accent = LumoraTheme.accent();
        int hover = LumoraTheme.accentHover();
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            int x1 = x + w * i / steps;
            int x2 = x + w * (i + 1) / steps;
            float f = i / (float) (steps - 1);
            int c = LumoraTheme.mix(accent, hover, f * 0.6f);
            g.fill(x1, y, x2, y + 1, LumoraTheme.alpha(c, Math.round(255 - 150 * f)));
            g.fill(x1, y + 1, x2, y + 2, LumoraTheme.alpha(c, Math.round(70 - 60 * f)));
        }
    }

    private void searchBox(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        int accent = LumoraTheme.accent();
        boolean hover = in(mx, my, x, y, w, h);
        g.card(x, y, w, h, h / 2, FIELD, searchFocused ? accent : hover ? 0xFF3A3450 : BORDER);
        HubGlyphs.draw(g, "search", x + 6, y + (h - HubGlyphs.SIZE) / 2, searchFocused ? LumoraTheme.accentHover() : DIM);
        int tx = x + 19;
        int max = w - 19 - 8;
        String hint = t("Strg + F", "Ctrl + F");
        boolean showHint = !searchFocused && search.isEmpty() && w >= 140;
        if (showHint) {
            max -= g.textWidth(hint) + 6;
            g.rightText(hint, x + w - 8, y + (h - 8) / 2, DIM, false);
        }
        String text = search;
        while (g.textWidth(text) > max && !text.isEmpty()) {
            text = text.substring(1);
        }
        if (text.isEmpty() && !searchFocused) {
            g.text(trim(g, t("Module suchen…", "Search modules…"), max), tx, y + (h - 8) / 2, DIM, false);
        } else {
            g.text(text, tx, y + (h - 8) / 2, TEXT, false);
        }
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + g.textWidth(text);
            g.fill(cx, y + 4, cx + 1, y + h - 4, TEXT);
        }
        hit(x, y, w, h, () -> searchFocused = true);
    }

    // ---- module cards

    private int grid(HudGraphics g, int x, int y, int w, boolean compact, List<Card> cards, int mx, int my) {
        if (cards.isEmpty()) {
            int cy = y + 30;
            HubGlyphs.draw(g, "search", x + w / 2 - 4, cy, DIM);
            g.centeredText(search.isBlank() ? t("Keine Module in dieser Kategorie", "No modules in this category")
                    : t("Keine Module gefunden", "No modules found"), x + w / 2, cy + 16, MUTED, false);
            return 60;
        }
        int gap = compact ? 6 : 8;
        int minW = 86;
        int cols = Math.max(1, (w + gap) / (minW + gap));
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = compact ? 68 : 76;
        String now = null;
        for (int i = 0; i < cards.size(); i++) {
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy + ch < contentTop - 4 || cy > contentBottom + 4) {
                continue;
            }
            Card c = cards.get(i);
            if (card(g, c, cx, cy, cw, ch, compact, mx, my)) {
                now = c.id();
                if (c.id().equals(hovered) && System.currentTimeMillis() - hoveredSince > 450) {
                    tooltipCard = c;
                }
            }
        }
        if (now == null || !now.equals(hovered)) {
            hovered = now;
            hoveredSince = System.currentTimeMillis();
        }
        int rows = (cards.size() + cols - 1) / cols;
        return rows * (ch + gap) - gap;
    }

    /** Draws one card; returns true if the mouse is over it. */
    private boolean card(HudGraphics g, Card c, int x, int y, int w, int h, boolean compact, int mx, int my) {
        int accent = LumoraTheme.accent();
        boolean visible = my >= contentTop && my < contentBottom;
        boolean hover = visible && in(mx, my, x, y, w, h);
        if (hover) {
            y -= 1;
            g.roundedRect(x - 2, y - 2, w + 4, h + 4, 7, LumoraTheme.alpha(accent, 0x38));
        }
        g.roundedRect(x, y, w, h, 5, hover ? accent : BORDER);
        g.roundedRect(x + 1, y + 1, w - 2, h - 2, 4, hover ? CARD_HOVER : CARD);
        g.fill(x + 5, y + 1, x + w - 5, y + 2, 0x0EFFFFFF);

        int iconY = y + (compact ? 5 : 8);
        g.texture(icon(c.icon()), x + (w - 32) / 2, iconY, 32, 32, 32, 32);
        g.centeredText(trim(g, c.name(), w - 10), x + w / 2, iconY + 35, TEXT, false);

        int bh = 14;
        int bx = x + 1;
        int bw = w - 2;
        int by = y + h - 1 - bh;
        int gearW = c.settings() != null ? 16 : 0;
        int mainW = bw - (gearW > 0 ? gearW + 1 : 0);
        boolean barHover = visible && in(mx, my, bx, by, mainW, bh);
        int col;
        String label;
        if (c.failure() != null) {
            col = barHover && c.toggle() != null ? 0xFFCC8A28 : FAILED;
            label = t("FEHLER", "ERROR");
        } else if (c.enabled()) {
            col = barHover ? ON_HOVER : ON;
            label = t("AKTIV", "ENABLED");
        } else {
            col = barHover ? OFF_HOVER : OFF;
            label = t("INAKTIV", "DISABLED");
        }
        barPart(g, bx, by, mainW, bh, col, true, gearW == 0);
        g.fill(bx + (gearW == 0 ? 4 : 4), by, bx + mainW - (gearW == 0 ? 4 : 0), by + 1, 0x38FFFFFF);
        int lw = spacedWidth(g, label);
        spaced(g, label, bx + (mainW - lw) / 2, by + (bh - 8) / 2 + 1, 0xFFFFFFFF);
        if (gearW > 0) {
            int gx = bx + mainW + 1;
            boolean gearHover = visible && in(mx, my, gx, by, gearW, bh);
            int gc = gearHover ? LumoraTheme.mix(col, 0xFFFFFFFF, 0.15f) : LumoraTheme.mix(col, 0xFF000000, 0.32f);
            barPart(g, gx, by, gearW, bh, gc, false, true);
            HubGlyphs.draw(g, "gear", gx + (gearW - HubGlyphs.SIZE) / 2, by + (bh - HubGlyphs.SIZE) / 2, 0xFFFFFFFF);
        }
        if (c.menu() != null) {
            boolean menuHover = visible && in(mx, my, x + w - 15, y + 3, 12, 12);
            if (menuHover) {
                g.roundedRect(x + w - 15, y + 3, 12, 12, 3, 0x20FFFFFF);
            }
            HubGlyphs.draw(g, "menu", x + w - 13, y + 5, menuHover ? TEXT : LumoraTheme.accentHover());
        }
        if (visible) {
            int y1 = Math.max(y, contentTop);
            int y2 = Math.min(y + h, contentBottom);
            if (y2 > y1) {
                Runnable toggle = c.toggle();
                if (toggle != null) {
                    hits.add(new Hit(x, y1, x + w, y2, toggle));
                }
                if (c.settings() != null && by >= y1 && by + bh <= y2) {
                    hit(bx + mainW + 1, by, gearW, bh, c.settings());
                }
                if (c.menu() != null && y + 3 >= y1) {
                    hit(x + w - 15, y + 3, 12, 12, c.menu());
                }
            }
        }
        return hover;
    }

    /** Part of a card's bottom bar: square on top, rounded at the outer bottom corners. */
    private static void barPart(HudGraphics g, int x, int y, int w, int h, int color, boolean roundLeft, boolean roundRight) {
        g.roundedRect(x, y, w, h, 4, color);
        g.fill(x, y, x + w, y + 4, color);
        if (!roundLeft) {
            g.fill(x, y, x + 4, y + h, color);
        }
        if (!roundRight) {
            g.fill(x + w - 4, y, x + w, y + h, color);
        }
    }

    private void tooltip(HudGraphics g, int width, int height, int mx, int my, long now) {
        Card c = tooltipCard;
        if (c == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        int max = 170;
        if (!c.description().isBlank()) {
            lines.addAll(wrap(g, c.description(), max));
        }
        if (c.from() != null && !c.from().isBlank()) {
            lines.add("§7" + t("Von ", "From ") + c.from());
        }
        if (c.failure() != null) {
            for (String l : wrap(g, c.failure(), max)) {
                lines.add("§6" + l);
            }
        }
        if (c.settings() != null) {
            lines.add("§7" + t("Zahnrad: Einstellungen", "Gear: settings"));
        }
        if (lines.isEmpty()) {
            return;
        }
        int tw = 0;
        for (String l : lines) {
            tw = Math.max(tw, g.textWidth(l));
        }
        int th = lines.size() * 10 + 6 + 12;
        tw = Math.max(tw, g.textWidth("§l" + c.name())) + 12;
        int x = Math.min(mx + 10, width - tw - 4);
        int y = Math.min(my + 12, height - th - 4);
        g.card(x, y, tw, th, 4, 0xF2100E18, LumoraTheme.alpha(LumoraTheme.accent(), 0xB0));
        g.text("§l" + c.name(), x + 6, y + 5, TEXT, false);
        int ly = y + 17;
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
        int x = cx - tw / 2;
        int alpha = Math.round(0xF0 * a);
        g.roundedRect(x, y, tw, 18, 9, LumoraTheme.alpha(0x100E18, alpha));
        g.roundedRect(x + 4, y + 6, 6, 6, 3, LumoraTheme.alpha(LumoraTheme.accent(), alpha));
        g.text(trim(g, toast, 320), x + 14, y + 5, LumoraTheme.alpha(TEXT, Math.max(8, alpha)), false);
    }

    // ---- themes

    private int themes(HudGraphics g, int x, int y, int w, int mx, int my) {
        int y0 = y;
        boolean lumora = !LumoraTheme.VANILLA.equals(str("theme", LumoraTheme.LUMORA));
        spaced(g, t("STIL", "STYLE"), x, y, DIM);
        y += 13;
        int gap = 8;
        int tw = Math.min(170, (w - gap) / 2);
        int th = 76;
        themeCard(g, x, y, tw, th, true, lumora, mx, my);
        themeCard(g, x + tw + gap, y, tw, th, false, !lumora, mx, my);
        y += th + 14;
        if (lumora) {
            spaced(g, t("AKZENTFARBE", "ACCENT COLOUR"), x, y, DIM);
            y += 13;
            int d = 16;
            int sx = x + 2;
            LumoraTheme.Accent current = LumoraTheme.current();
            for (LumoraTheme.Accent a : LumoraTheme.ACCENTS) {
                boolean sel = a == current;
                boolean hover = in(mx, my, sx - 2, y - 2, d + 4, d + 4);
                if (sel) {
                    g.roundedRect(sx - 2, y - 2, d + 4, d + 4, (d + 4) / 2, 0xFFFFFFFF);
                    g.roundedRect(sx - 1, y - 1, d + 2, d + 2, (d + 2) / 2, MAIN);
                } else if (hover) {
                    g.roundedRect(sx - 2, y - 2, d + 4, d + 4, (d + 4) / 2, 0x60FFFFFF);
                    g.roundedRect(sx - 1, y - 1, d + 2, d + 2, (d + 2) / 2, MAIN);
                }
                g.roundedRect(sx, y, d, d, d / 2, a.color());
                if (sel) {
                    HubGlyphs.draw(g, "check", sx + (d - HubGlyphs.SIZE) / 2, y + (d - HubGlyphs.SIZE) / 2, 0xFFFFFFFF);
                }
                String id = a.id();
                hit(sx - 2, y - 2, d + 4, d + 4, () -> host.set("accentColor", id));
                sx += d + 8;
            }
            g.text(de ? current.german() : current.english(), sx + 4, y + (d - 8) / 2, MUTED, false);
            y += d + 14;
            spaced(g, t("LUMORA-DESIGN", "LUMORA DESIGN"), x, y, DIM);
            y += 13;
            y = toggleRow(g, x, y, w, t("Lumora-Hauptmenü", "Lumora main menu"), "customMainMenu", mx, my);
            y = toggleRow(g, x, y, w, t("Menü-Hintergrund", "Menu background"), "menuBackground", mx, my);
            if (host.has("menuShaders")) {
                boolean on = !"off".equals(str("menuShaders", "auto"));
                y = row(g, x, y, w, t("Menü-Effekte", "Menu effects"), mx, my, (rx, ry) -> switchControl(g, rx, ry, on),
                        () -> host.set("menuShaders", on ? "off" : "auto"));
            }
            if (host.fontSupported()) {
                y = toggleRow(g, x, y, w, t("Lumora-Schrift", "Lumora font"), "lumoraFont", mx, my);
            }
            y = toggleRow(g, x, y, w, t("Eigener Ladebildschirm", "Custom loading screen"), "customLoadingScreen", mx, my);
            if (bool("customMainMenu") && !host.inWorld()) {
                boolean classic = host.classicMenuUntilRestart();
                String value = classic ? t("Lumora zeigen", "Show Lumora") : t("Bis Neustart klassisch", "Classic until restart");
                y = row(g, x, y, w, t("Hauptmenü", "Main menu"), mx, my, (rx, ry) -> pill(g, rx, ry, value),
                        () -> host.showMainMenu(classic));
            }
            y += 8;
        } else {
            for (String l : wrap(g, t("Hauptmenü, Knöpfe, Schrift, Ladebildschirm, Server- und Weltliste sehen aus wie in "
                    + "Minecraft. Der Hub bleibt im Lumora-Stil, damit du jederzeit zurückwechseln kannst.",
                    "Main menu, buttons, font, loading screen and the server and world lists look like Minecraft's own. "
                            + "The hub keeps the Lumora style so you can switch back at any time."), w)) {
                g.text(l, x, y, MUTED, false);
                y += 10;
            }
            y += 10;
        }
        spaced(g, t("ANIMATIONEN", "ANIMATIONS"), x, y, DIM);
        y += 13;
        String style = str("transitionStyle", "fade");
        String styleName = switch (style) {
            case "slide" -> t("Schieben", "Slide");
            case "off" -> t("Aus", "Off");
            default -> t("Überblenden", "Fade");
        };
        y = row(g, x, y, w, t("Menü-Übergang", "Menu transition"), mx, my, (rx, ry) -> pill(g, rx, ry, styleName),
                () -> host.set("transitionStyle", switch (style) {
                    case "fade" -> "slide";
                    case "slide" -> "off";
                    default -> "fade";
                }));
        int millis = (int) num("transitionMillis", 220);
        y = row(g, x, y, w, t("Dauer", "Duration"), mx, my, (rx, ry) -> pill(g, rx, ry, millis + " ms"), () -> {
            int next = DURATIONS[0];
            for (int i = 0; i < DURATIONS.length; i++) {
                if (DURATIONS[i] == millis) {
                    next = DURATIONS[(i + 1) % DURATIONS.length];
                    break;
                }
            }
            host.set("transitionMillis", next);
        });
        y = toggleRow(g, x, y, w, t("Übergang im Spiel", "Transition in game"), "transitionInGame", mx, my);
        if (host.has("swingAnimation")) {
            String swing = str("swingAnimation", "smooth");
            String swingName = Character.toUpperCase(swing.charAt(0)) + swing.substring(1);
            y = row(g, x, y, w, t("Schlag-Animation", "Swing animation"), mx, my, (rx, ry) -> pill(g, rx, ry, swingName), () -> {
                int i = List.of(SWING).indexOf(swing);
                host.set("swingAnimation", SWING[(i + 1) % SWING.length]);
            });
        }
        return y - y0;
    }

    private void themeCard(HudGraphics g, int x, int y, int w, int h, boolean lumora, boolean selected, int mx, int my) {
        int accent = LumoraTheme.accent();
        boolean hover = in(mx, my, x, y, w, h) && my >= contentTop && my < contentBottom;
        g.roundedRect(x, y, w, h, 5, selected ? accent : hover ? 0xFF3A3450 : BORDER);
        g.roundedRect(x + 1, y + 1, w - 2, h - 2, 4, hover ? CARD_HOVER : CARD);
        int pvx = x + 6;
        int pvy = y + 6;
        int pvw = w - 12;
        int pvh = 38;
        if (lumora) {
            g.roundedRect(pvx, pvy, pvw, pvh, 3, 0xFF0F0C17);
            g.fill(pvx + 3, pvy + 3, pvx + 3 + pvw / 5, pvy + pvh - 3, SIDE);
            g.roundedRect(pvx + 5, pvy + 6, pvw / 5 - 4, 4, 2, LumoraTheme.alpha(accent, 0x90));
            int ax = pvx + 6 + pvw / 5;
            g.fill(ax, pvy + 12, pvx + pvw - 3, pvy + 13, accent);
            int cw = (pvx + pvw - 4 - ax - 6) / 3;
            for (int i = 0; i < 3; i++) {
                int cx = ax + 1 + i * (cw + 3);
                g.roundedRect(cx, pvy + 16, cw, 18, 2, CARD);
                g.fill(cx, pvy + 30, cx + cw, pvy + 34, i == 1 ? OFF : ON);
            }
        } else {
            g.fill(pvx, pvy, pvx + pvw, pvy + pvh, 0xFF2B2B2B);
            for (int i = 0; i < 2; i++) {
                int bx = pvx + pvw / 6;
                int by = pvy + 7 + i * 14;
                int bw = pvw * 2 / 3;
                g.fill(bx, by, bx + bw, by + 11, 0xFF000000);
                g.fill(bx + 1, by + 1, bx + bw - 1, by + 10, 0xFF6F6F6F);
                g.fill(bx + 1, by + 1, bx + bw - 1, by + 2, 0xFFA8A8A8);
                g.fill(bx + 1, by + 8, bx + bw - 1, by + 10, 0xFF4F4F4F);
                g.fill(bx + bw / 2 - 10, by + 4, bx + bw / 2 + 10, by + 6, 0xFFE0E0E0);
            }
        }
        g.text("§l" + (lumora ? "Lumora" : "Vanilla"), x + 8, y + h - 27, TEXT, false);
        g.text(trim(g, lumora ? t("Eigene Menüs & Farben", "Own menus & colours") : t("Menüs wie in Minecraft", "Minecraft's menus"),
                w - 16), x + 8, y + h - 15, MUTED, false);
        if (selected) {
            g.roundedRect(x + w - 17, y + 4, 13, 13, 6, accent);
            HubGlyphs.draw(g, "check", x + w - 15, y + 6, 0xFFFFFFFF);
        }
        if (my >= contentTop && my < contentBottom) {
            hit(x, y, w, h, () -> {
                if (!selected) {
                    applyTheme(lumora ? LumoraTheme.LUMORA : LumoraTheme.VANILLA);
                }
            });
        }
    }

    private interface Control {
        void draw(int right, int y);
    }

    /** A settings row with a label and a control at the right; returns the y of the next row. */
    private int row(HudGraphics g, int x, int y, int w, String label, int mx, int my, Control control, Runnable action) {
        int h = 20;
        boolean hover = in(mx, my, x, y, w, h) && my >= contentTop && my < contentBottom;
        g.roundedRect(x, y, w, h, 4, hover ? CARD_HOVER : CARD);
        g.text(label, x + 9, y + 6, hover ? TEXT : 0xFFD6D0E2, false);
        control.draw(x + w - 8, y + 4);
        if (my >= contentTop && my < contentBottom && y + h > contentTop && y < contentBottom) {
            hit(x, y, w, h, action);
        }
        return y + h + 3;
    }

    private int toggleRow(HudGraphics g, int x, int y, int w, String label, String option, int mx, int my) {
        if (!host.has(option)) {
            return y;
        }
        boolean on = bool(option);
        return row(g, x, y, w, label, mx, my, (rx, ry) -> switchControl(g, rx, ry, on), () -> host.set(option, !on));
    }

    private static void switchControl(HudGraphics g, int right, int y, boolean on) {
        int sw = 22;
        int x = right - sw;
        g.roundedRect(x, y + 1, sw, 11, 5, on ? LumoraTheme.accent() : 0xFF3A3450);
        g.roundedRect(on ? x + sw - 10 : x + 1, y + 2, 9, 9, 4, 0xFFFFFFFF);
    }

    private static void pill(HudGraphics g, int right, int y, String value) {
        int w = g.textWidth(value) + 18;
        g.card(right - w, y, w, 13, 4, FIELD, 0xFF3A3450);
        g.text(value, right - w + 6, y + 3, TEXT, false);
        g.text("›", right - 9, y + 3, LumoraTheme.accentHover(), false);
    }

    // ---- zoom

    private int zoom(HudGraphics g, int x, int y, int w, int mx, int my) {
        int y0 = y;
        y = toggleRow(g, x, y, w, t("Zoom aktiv", "Zoom enabled"), "zoomEnabled", mx, my);
        double factor = num("zoomFactor", 4);
        y = row(g, x, y, w, t("Vergrößerung", "Magnification"), mx, my,
                (rx, ry) -> pill(g, rx, ry, String.format(Locale.ROOT, "%.0f×", factor)), () -> {
                    double next = ZOOM_FACTORS[0];
                    for (int i = 0; i < ZOOM_FACTORS.length; i++) {
                        if (ZOOM_FACTORS[i] >= factor - 0.01) {
                            next = ZOOM_FACTORS[(i + 1) % ZOOM_FACTORS.length];
                            break;
                        }
                    }
                    host.set("zoomFactor", next);
                });
        boolean toggle = "toggle".equals(str("zoomMode", "hold"));
        y = row(g, x, y, w, t("Modus", "Mode"), mx, my,
                (rx, ry) -> pill(g, rx, ry, toggle ? t("Umschalten", "Toggle") : t("Halten", "Hold")),
                () -> host.set("zoomMode", toggle ? "hold" : "toggle"));
        y = toggleRow(g, x, y, w, t("Weicher Zoom", "Smooth zoom"), "zoomSmooth", mx, my);
        y = toggleRow(g, x, y, w, t("Mausrad ändert den Zoom", "Scroll wheel changes zoom"), "zoomScroll", mx, my);
        y = toggleRow(g, x, y, w, t("Geringere Mausempfindlichkeit", "Lower mouse sensitivity"), "zoomSensitivity", mx, my);
        y = toggleRow(g, x, y, w, t("Kinokamera", "Cinematic camera"), "zoomCinematic", mx, my);
        y = toggleRow(g, x, y, w, t("Vignette", "Vignette"), "zoomVignette", mx, my);
        y += 4;
        for (String l : wrap(g, t("Taste: ", "Key: ") + host.zoomKey() + t(" · änderbar unter Optionen › Steuerung",
                " · change it in Options › Controls"), w)) {
            g.text(l, x, y, DIM, false);
            y += 10;
        }
        return y - y0;
    }

    // ---- profiles

    private int profiles(HudGraphics g, int x, int y, int w, int mx, int my) {
        int y0 = y;
        String active = core.hudEditor().activeProfile();
        for (String id : core.hudEditor().profiles()) {
            int h = 24;
            boolean sel = id.equals(active);
            boolean visible = my >= contentTop && my < contentBottom;
            boolean hover = visible && in(mx, my, x, y, w, h);
            g.roundedRect(x, y, w, h, 4, sel ? LumoraTheme.accent() : hover ? 0xFF3A3450 : BORDER);
            g.roundedRect(x + 1, y + 1, w - 2, h - 2, 3, hover ? CARD_HOVER : CARD);
            HubGlyphs.draw(g, "profiles", x + 8, y + (h - HubGlyphs.SIZE) / 2, sel ? LumoraTheme.accentHover() : DIM);
            String name = profileName(id);
            g.text(name, x + 23, y + (h - 8) / 2, TEXT, false);
            int bx = x + w - 8;
            boolean builtIn = HudLayout.BUILT_IN.contains(id);
            if (!builtIn) {
                int tx = bx - 14;
                boolean th = visible && in(mx, my, tx - 2, y + 4, 14, h - 8);
                if (th) {
                    g.roundedRect(tx - 2, y + 4, 14, h - 8, 3, 0x30F2555A);
                }
                HubGlyphs.draw(g, "trash", tx, y + (h - HubGlyphs.SIZE) / 2, th ? 0xFFF2555A : DIM);
                bx = tx - 8;
            }
            if (sel) {
                String badge = t("AKTIV", "ACTIVE");
                int bw = spacedWidth(g, badge) + 10;
                g.roundedRect(bx - bw, y + 6, bw, 12, 6, LumoraTheme.alpha(LumoraTheme.accent(), 0x50));
                spaced(g, badge, bx - bw + 5, y + 8, TEXT);
            } else if (hover) {
                g.rightText(t("Laden", "Load"), bx, y + (h - 8) / 2, LumoraTheme.accentHover(), false);
            }
            if (visible) {
                hit(x, y, w, h, () -> {
                    core.hudEditor().activateProfile(id);
                    toast(t("Profil geladen: ", "Profile loaded: ") + name);
                });
                if (!builtIn) {
                    hit(x + w - 24, y + 4, 14, h - 8, () -> {
                        if (core.widgetRegistry().deleteProfile(id)) {
                            toast(t("Profil gelöscht: ", "Profile deleted: ") + name);
                        }
                    });
                }
            }
            y += h + 4;
        }
        y += 6;
        String edit = t("HUD bearbeiten", "Edit HUD");
        int bw = g.textWidth(edit) + 30;
        button(g, x, y, bw, 18, "", false, mx, my, host::openHudEditor);
        HubGlyphs.draw(g, "hudeditor", x + 8, y + 5, LumoraTheme.accentHover());
        g.text(edit, x + 22, y + 5, TEXT, false);
        y += 24;
        for (String l : wrap(g, t("Ein Profil speichert, welche Module an sind und wo sie auf dem Bildschirm stehen.",
                "A profile stores which modules are on and where they are on the screen."), w)) {
            g.text(l, x, y, DIM, false);
            y += 10;
        }
        return y - y0;
    }

    // ---- system

    private int system(HudGraphics g, int x, int y, int w, int mx, int my) {
        int y0 = y;
        int gap = 8;
        int cw = (w - gap) / 2;
        int th = 34;
        long used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
        long max = Runtime.getRuntime().maxMemory() / 1048576;
        String protocol = host.protocol();
        tile(g, x, y, cw, th, "RENDERER", host.renderer(), TEXT);
        tile(g, x + cw + gap, y, cw, th, t("GRAFIKKARTE", "GPU"), host.gpu(), TEXT);
        y += th + gap;
        tile(g, x, y, cw, th, t("PROTOKOLL", "PROTOCOL"), protocol == null ? "–" : protocol, TEXT);
        tile(g, x + cw + gap, y, cw, th, t("ARBEITSSPEICHER", "MEMORY"), used + " / " + max + " MB", used > max * 0.85 ? WARN : OK);
        y += th + gap + 4;
        int bw = (w - 2 * gap) / 3;
        int bh = 18;
        String[] labels = {
                t("Protokoll wählen", "Choose protocol"),
                "Benchmark (" + (int) num("benchmarkSeconds", 30) + " s)",
                t("Diagnose speichern", "Save diagnostics"),
                t("Diagnose-Ordner", "Diagnostics folder"),
                t("Addon-Ordner", "Addon folder")};
        Runnable[] actions = {
                () -> {
                    if (!host.openProtocol()) {
                        toast(t("ViaFabricPlus ist nicht installiert", "ViaFabricPlus is not installed"));
                    }
                },
                host::startBenchmark,
                () -> toast(t("Gespeichert: ", "Saved: ") + host.writeDiagnostics()),
                () -> openFolder(host.diagnosticsDirectory()),
                () -> openFolder(core.jarAddons().directory())};
        for (int i = 0; i < labels.length; i++) {
            if (i == 4 && core.jarAddons().directory() == null) {
                continue;
            }
            int bx = x + (i % 3) * (bw + gap);
            int by = y + (i / 3) * (bh + 6);
            button(g, bx, by, bw, bh, trim(g, labels[i], bw - 8), i == 1, mx, my, actions[i]);
        }
        y += 2 * (bh + 6) + 4;
        g.text(host.versionLine(), x, y, DIM, false);
        y += 10;
        g.text(t("Rechte Shift-Taste öffnet den Hub", "Right Shift opens the hub"), x, y, DIM, false);
        y += 10;
        return y - y0;
    }

    private void openFolder(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
            host.openFolder(dir);
        } catch (java.io.IOException | RuntimeException e) {
            toast(e.getMessage());
        }
    }

    private void tile(HudGraphics g, int x, int y, int w, int h, String label, String value, int color) {
        g.card(x, y, w, h, 4, CARD, BORDER);
        g.roundedRect(x + 1, y + 6, 2, h - 12, 1, LumoraTheme.accent());
        spaced(g, label, x + 10, y + 7, DIM);
        g.text(trim(g, value == null || value.isBlank() ? "?" : value, w - 18), x + 10, y + 19, color, false);
    }

    private void button(HudGraphics g, int x, int y, int w, int h, String label, boolean primary, int mx, int my, Runnable action) {
        boolean hover = in(mx, my, x, y, w, h) && (!clipHits || my >= contentTop && my < contentBottom);
        if (primary) {
            g.roundedRect(x, y, w, h, 4, hover ? LumoraTheme.accentHover() : LumoraTheme.accent());
        } else {
            g.card(x, y, w, h, 4, hover ? CARD_HOVER : CARD, hover ? LumoraTheme.accent() : 0xFF3A3450);
        }
        g.centeredText(label, x + w / 2 + 1, y + (h - 8) / 2, TEXT, false);
        hit(x, y, w, h, action);
    }

    // ---- text helpers

    static void spaced(HudGraphics g, String s, int x, int y, int color) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.text(ch, cx, y, color, false);
            cx += g.textWidth(ch) + 1;
        }
    }

    static int spacedWidth(HudGraphics g, String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += g.textWidth(String.valueOf(s.charAt(i))) + 1;
        }
        return Math.max(0, w - 1);
    }

    static String trim(HudGraphics g, String s, int max) {
        if (s == null) {
            return "";
        }
        if (g.textWidth(s) <= max) {
            return s;
        }
        String r = s;
        while (!r.isEmpty() && g.textWidth(r + "…") > max) {
            r = r.substring(0, r.length() - 1);
        }
        return r.stripTrailing() + "…";
    }

    static List<String> wrap(HudGraphics g, String text, int max) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (g.textWidth(next) > max && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Click area; inside the scrolling content only its visible part counts. */
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

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) {
            return false;
        }
        searchFocused = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                hits.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= sideLeft && x < sideRight && y >= sideTop && y < sideBottom && sideMax > 0) {
            sideScroll = (float) Math.max(0, Math.min(sideMax, sideScroll - delta * 18));
            return true;
        }
        scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - delta * 28));
        return true;
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (ctrl && key == Keys.F) {
            if (page != Page.MODULES) {
                open(Page.MODULES);
            }
            searchFocused = true;
            return true;
        }
        if (!searchFocused) {
            return false;
        }
        if (key == Keys.BACKSPACE) {
            if (ctrl) {
                search = "";
            } else if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
            scrollTarget = 0;
            return true;
        }
        if (key == Keys.ENTER || key == Keys.TAB) {
            searchFocused = false;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch) {
        if (ch < 32 || ch == 127 || Character.isISOControl(ch)) {
            return false;
        }
        if (System.currentTimeMillis() - openedAt < 200) {
            return true; // the key that opened the hub
        }
        if (!searchFocused) {
            if (page != Page.MODULES || ch == ' ') {
                return false;
            }
            searchFocused = true;
        }
        if (search.length() < 32) {
            search += ch;
            scrollTarget = 0;
        }
        return true;
    }

    @Override
    public boolean closeRequested() {
        if (searchFocused || !search.isEmpty()) {
            searchFocused = false;
            search = "";
            return false;
        }
        if (page == Page.ZOOM) {
            open(Page.MODULES);
            return false;
        }
        return true;
    }
}
