package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.hud.WidgetDesign;
import dev.lumora.core.Platform;

import java.util.Locale;

/**
 * Built-in module "Hotbar & Leisten": the hotbar, hearts, armor, food, air and experience as six HUD widgets, each
 * movable in the HUD editor and with its own look (Minecraft's icons, compact icon + number, a bar, or the number
 * alone). While the module is on, the version adapters skip Minecraft's own hotbar and status bars
 * ({@link #takesOver()}); a widget that is switched off is simply not shown.
 */
public final class StatusBarsModule implements LumoraAddon {
    private static volatile StatusBarsModule active;

    private final Platform platform;
    VanillaSprites sprites;
    private GameExtras.Status status = GameExtras.Status.NONE;
    private long statusTick = -1;

    public StatusBarsModule(Platform platform) {
        this.platform = platform;
    }

    /** Minecraft's hotbar, status bars and experience bar are drawn by this module's widgets instead. */
    public static boolean takesOver() {
        return active != null;
    }

    @Override
    public void onEnable(AddonContext context) {
        sprites = new VanillaSprites(platform.minecraftVersion());
        context.widgets().register(new Hotbar(this));
        context.widgets().register(new Health(this));
        context.widgets().register(new Armor(this));
        context.widgets().register(new Food(this));
        context.widgets().register(new Air(this));
        context.widgets().register(new Experience(this));
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    GameExtras extras() {
        return platform.extras();
    }

    /** Current values (read once per client tick), or an example in the editor without a world. */
    GameExtras.Status status(RenderContext ctx) {
        if (ctx.preview() || !ctx.game().inWorld()) {
            return SAMPLE;
        }
        long tick = ctx.game().clientTicks();
        if (tick != statusTick) {
            statusTick = tick;
            status = platform.extras().status();
        }
        return status;
    }

    // Default places: Minecraft's layout at a GUI height of 240 (854 x 480 at scale 2, 1280 x 720 at scale 3); on
    // taller screens the bars sit a little higher, never on top of each other or the hotbar.

    /** Example values for the editor. */
    static final GameExtras.Status SAMPLE = new GameExtras.Status(true, true, false, 17, 20, 4, 14, 15, 180, 300, true, 12,
            0.45f, false, "normal", false, 2);

    // ------------------------------------------------------------------ text formats

    /** {@code value}, {@code value/max} or {@code percent} of a value. */
    static String format(String format, double value, double max, boolean decimals) {
        String v = decimals ? number(value) : Integer.toString((int) Math.ceil(value - 1e-9));
        return switch (format) {
            case "value_max" -> v + "/" + (decimals ? number(max) : Integer.toString((int) Math.ceil(max - 1e-9)));
            case "percent" -> Math.round(max <= 0 ? 0 : value * 100 / max) + "%";
            default -> v;
        };
    }

    private static String number(double v) {
        double r = Math.round(v * 2) / 2.0;
        return r == Math.floor(r) ? Integer.toString((int) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    // ------------------------------------------------------------------ status bars

    /** Common base of hearts, armor, food and air: the four looks, text format, colours, background. */
    abstract static class Bar extends HudWidget {
        static final int PAD = 3;
        final StatusBarsModule module;
        ChoiceSetting style;
        ChoiceSetting format;
        IntegerSetting barWidth;
        ColorSetting textColor;
        ColorSetting barColor;
        BooleanSetting background;
        BooleanSetting shadow;
        int textWidth = -1;

        Bar(String id, StatusBarsModule module) {
            super(id);
            this.module = module;
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        public float minScale() {
            return 0.5f;
        }

        @Override
        public void configure(SettingsGroup s) {
            String p = s.translationPrefix();
            style = s.selection("style", "vanilla", "vanilla", "compact", "bar", "number").section(p + "section.look");
            format = s.selection("format", "value", "value", "value_max", "percent").visibleWhen(() -> !vanilla());
            extraLook(s);
            barWidth = s.slider("bar_width", 80, 40, 160).step(5).visibleWhen(() -> "bar".equals(style.get()));
            // background and shadow come from "Widget-Design" while it is on
            background = s.checkbox("background", true).visibleWhen(() -> !vanilla() && !WidgetDesign.active());
            shadow = s.checkbox("shadow", true).visibleWhen(() -> !WidgetDesign.active());
            textColor = s.chroma("text_color", defaultText(), false).section(p + "section.colors")
                    .visibleWhen(() -> !vanilla());
            barColor = s.color("bar_color", barDefault(), false).visibleWhen(() -> "bar".equals(style.get()));
            extraBehaviour(s);
        }

        void extraLook(SettingsGroup s) {
        }

        void extraBehaviour(SettingsGroup s) {
        }

        boolean vanilla() {
            return "vanilla".equals(style.get());
        }

        int defaultText() {
            return 0xFFFFFFFF;
        }

        abstract int barDefault();

        abstract double value(GameExtras.Status s);

        abstract double max(GameExtras.Status s);

        boolean decimals() {
            return false;
        }

        /** Minecraft's look: width and height. */
        abstract int vanillaWidth(GameExtras.Status s);

        abstract int vanillaHeight(GameExtras.Status s);

        abstract void drawVanilla(HudGraphics g, GameExtras.Status s, RenderContext ctx);

        /** The full icon for the compact look. */
        abstract void icon(HudGraphics g, int x, int y, GameExtras.Status s);

        /** Shown at all (besides the game mode): armor without armor, air above water. */
        boolean shows(GameExtras.Status s) {
            return true;
        }

        String text(GameExtras.Status s) {
            return format(format.get(), value(s), max(s), decimals());
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            if (ctx.preview() || ctx.editing()) {
                return true;
            }
            GameExtras.Status s = module.status(ctx);
            return s.present() && s.survival() && shows(s);
        }

        private int textWidth(GameExtras.Status s) {
            return textWidth >= 0 ? textWidth : text(s).length() * 6;
        }

        private int pad() {
            return WidgetDesign.background(background.get()) ? PAD : 0;
        }

        @Override
        public int width(RenderContext ctx) {
            GameExtras.Status s = module.status(ctx);
            return switch (style.get()) {
                case "compact" -> pad() * 2 + 11 + textWidth(s);
                case "bar" -> barWidth.get();
                case "number" -> pad() * 2 + textWidth(s);
                default -> vanillaWidth(s);
            };
        }

        @Override
        public int height(RenderContext ctx) {
            GameExtras.Status s = module.status(ctx);
            return switch (style.get()) {
                case "compact", "number" -> 9 + pad() * 2;
                case "bar" -> 11;
                default -> vanillaHeight(s);
            };
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            GameExtras.Status s = module.status(ctx);
            String st = style.get();
            if ("vanilla".equals(st)) {
                drawVanilla(g, s, ctx);
                return;
            }
            String text = text(s);
            textWidth = g.textWidth(text);
            int color = textColor.argbAt(ctx.timeMillis());
            int w = width(ctx);
            int h = height(ctx);
            if ("bar".equals(st)) {
                double max = max(s);
                float f = max <= 0 ? 0 : (float) Math.max(0, Math.min(1, value(s) / max));
                WidgetDesign.track(g, 0, 0, w, h, 2, background.get() ? 0xB0101018 : 0x60101018);
                int fill = Math.round((w - 2) * f);
                if (fill > 0) {
                    g.roundedRect(1, 1, fill, h - 2, 2, barColor.argb());
                }
                g.centeredText(text, w / 2, 2, color, WidgetDesign.shadow(shadow.get()));
                return;
            }
            if (WidgetDesign.active() || background.get()) {
                WidgetDesign.box(g, 0, 0, w, h, 3, Colors.withAlpha(Colors.BACKGROUND, 0xB0));
            }
            int x = pad();
            if ("compact".equals(st)) {
                icon(g, x, pad(), s);
                x += 11;
            }
            g.text(text, x, pad() + 1, color, WidgetDesign.shadow(shadow.get()));
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.5f, 0.9f).enabledByDefault();
        }
    }

    /** Hearts: rows of ten like Minecraft (more rows above 20 health), golden absorption hearts behind. */
    static final class Health extends Bar {
        BooleanSetting decimals;

        Health(StatusBarsModule m) {
            super("health", m);
        }

        @Override
        void extraLook(SettingsGroup s) {
            decimals = s.checkbox("decimals", false).visibleWhen(() -> !vanilla());
        }

        @Override
        int barDefault() {
            return 0xFFE0343A;
        }

        @Override
        int defaultText() {
            return 0xFFFF6B6B;
        }

        @Override
        boolean decimals() {
            return decimals.get();
        }

        @Override
        double value(GameExtras.Status s) {
            return s.health() + s.absorption();
        }

        @Override
        double max(GameExtras.Status s) {
            return s.maxHealth();
        }

        private static int hearts(GameExtras.Status s) {
            return (int) Math.ceil(s.maxHealth() / 2.0) + (int) Math.ceil(s.absorption() / 2.0);
        }

        private static int rows(GameExtras.Status s) {
            return Math.max(1, (hearts(s) + 9) / 10);
        }

        /** Minecraft packs the rows closer the more there are. */
        private static int rowHeight(GameExtras.Status s) {
            return Math.max(10 - (rows(s) - 2), 3);
        }

        @Override
        int vanillaWidth(GameExtras.Status s) {
            return 81;
        }

        @Override
        int vanillaHeight(GameExtras.Status s) {
            return 9 + (rows(s) - 1) * rowHeight(s);
        }

        @Override
        void drawVanilla(HudGraphics g, GameExtras.Status s, RenderContext ctx) {
            VanillaSprites sp = module.sprites;
            int maxHearts = (int) Math.ceil(s.maxHealth() / 2.0);
            int total = hearts(s);
            int rows = rows(s);
            int rowH = rowHeight(s);
            int health = (int) Math.ceil(s.health());
            int absorption = (int) Math.ceil(s.absorption());
            // with 4 health or less the hearts shake, like in Minecraft
            boolean shake = health + absorption <= 4;
            java.util.Random random = new java.util.Random(ctx.game().clientTicks() * 312871L);
            for (int i = total - 1; i >= 0; i--) {
                int row = i / 10;
                int x = (i % 10) * 8;
                int y = (rows - 1 - row) * rowH;
                if (shake) {
                    y += random.nextInt(2);
                }
                sp.heart(g, x, y, "container", false, s.hardcore());
                if (i < maxHearts) {
                    if (i * 2 < health) {
                        sp.heart(g, x, y, s.hearts(), i * 2 + 1 == health, s.hardcore());
                    }
                } else {
                    int a = (i - maxHearts) * 2;
                    if (a < absorption) {
                        sp.heart(g, x, y, "withered".equals(s.hearts()) ? "withered" : "absorbing",
                                a + 1 == absorption, s.hardcore());
                    }
                }
            }
        }

        @Override
        void icon(HudGraphics g, int x, int y, GameExtras.Status s) {
            module.sprites.heart(g, x, y, s.hearts(), false, s.hardcore());
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.373f, 0.870f).enabledByDefault();
        }
    }

    /** Armor points: ten icons above the hearts. */
    static final class Armor extends Bar {
        BooleanSetting autoHide;

        Armor(StatusBarsModule m) {
            super("armor", m);
        }

        @Override
        void extraBehaviour(SettingsGroup s) {
            autoHide = s.toggle("auto_hide", true).section(s.translationPrefix() + "section.behaviour");
        }

        @Override
        int barDefault() {
            return 0xFFB0B8C8;
        }

        @Override
        double value(GameExtras.Status s) {
            return s.armor();
        }

        @Override
        double max(GameExtras.Status s) {
            return 20;
        }

        @Override
        boolean shows(GameExtras.Status s) {
            return !autoHide.get() || s.armor() > 0;
        }

        @Override
        int vanillaWidth(GameExtras.Status s) {
            return 81;
        }

        @Override
        int vanillaHeight(GameExtras.Status s) {
            return 9;
        }

        @Override
        void drawVanilla(HudGraphics g, GameExtras.Status s, RenderContext ctx) {
            for (int i = 0; i < 10; i++) {
                int v = i * 2 + 1;
                module.sprites.armor(g, i * 8, 0, v < s.armor() ? 2 : v == s.armor() ? 1 : 0);
            }
        }

        @Override
        void icon(HudGraphics g, int x, int y, GameExtras.Status s) {
            module.sprites.armor(g, x, y, 2);
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.373f, 0.827f).enabledByDefault();
        }
    }

    /** Food: ten drumsticks filling from the right, greenish with the hunger effect. */
    static final class Food extends Bar {
        Food(StatusBarsModule m) {
            super("food", m);
        }

        @Override
        int barDefault() {
            return 0xFFC87A3C;
        }

        @Override
        int defaultText() {
            return 0xFFF0B070;
        }

        @Override
        double value(GameExtras.Status s) {
            return s.food();
        }

        @Override
        double max(GameExtras.Status s) {
            return 20;
        }

        @Override
        int vanillaWidth(GameExtras.Status s) {
            return 81;
        }

        @Override
        int vanillaHeight(GameExtras.Status s) {
            return 9;
        }

        @Override
        void drawVanilla(HudGraphics g, GameExtras.Status s, RenderContext ctx) {
            for (int i = 0; i < 10; i++) {
                int v = i * 2 + 1;
                module.sprites.food(g, 72 - i * 8, 0, v < s.food() ? 2 : v == s.food() ? 1 : 0, s.hunger());
            }
        }

        @Override
        void icon(HudGraphics g, int x, int y, GameExtras.Status s) {
            module.sprites.food(g, x, y, 2, s.hunger());
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.627f, 0.870f).enabledByDefault();
        }
    }

    /** Air: bubbles above the food while under water. */
    static final class Air extends Bar {
        BooleanSetting autoHide;

        Air(StatusBarsModule m) {
            super("air", m);
        }

        @Override
        void extraBehaviour(SettingsGroup s) {
            autoHide = s.toggle("auto_hide", true).section(s.translationPrefix() + "section.behaviour");
        }

        @Override
        int barDefault() {
            return 0xFF4A9BE8;
        }

        @Override
        int defaultText() {
            return 0xFF8FC8FF;
        }

        @Override
        double value(GameExtras.Status s) {
            return Math.max(0, s.air()) * 20.0 / Math.max(1, s.maxAir());
        }

        @Override
        double max(GameExtras.Status s) {
            return 20;
        }

        @Override
        boolean shows(GameExtras.Status s) {
            return !autoHide.get() || s.underwater() || s.air() < s.maxAir();
        }

        @Override
        int vanillaWidth(GameExtras.Status s) {
            return 81;
        }

        @Override
        int vanillaHeight(GameExtras.Status s) {
            return 9;
        }

        @Override
        void drawVanilla(HudGraphics g, GameExtras.Status s, RenderContext ctx) {
            int max = Math.max(1, s.maxAir());
            int air = Math.max(0, Math.min(s.air(), max));
            int full = (int) Math.ceil((air - 2) * 10.0 / max);
            int partial = (int) Math.ceil(air * 10.0 / max) - full;
            for (int i = 0; i < 10; i++) {
                module.sprites.air(g, 72 - i * 8, 0, i < full ? 2 : i < full + partial ? 1 : 0);
            }
        }

        @Override
        void icon(HudGraphics g, int x, int y, GameExtras.Status s) {
            module.sprites.air(g, x, y, 2);
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.627f, 0.827f).enabledByDefault();
        }
    }

    /** Experience: Minecraft's green bar with the level above it, or level/progress as text or bar. */
    static final class Experience extends Bar {
        static final int LEVEL_GREEN = 0xFF80FF20;

        Experience(StatusBarsModule m) {
            super("xp", m);
        }

        @Override
        public void configure(SettingsGroup s) {
            String p = s.translationPrefix();
            style = s.selection("style", "vanilla", "vanilla", "compact", "bar", "number").section(p + "section.look");
            format = s.selection("format", "value", "value", "value_max", "percent").visibleWhen(() -> !vanilla());
            barWidth = s.slider("bar_width", 80, 40, 182).step(2).visibleWhen(() -> "bar".equals(style.get()));
            // background and shadow come from "Widget-Design" while it is on
            background = s.checkbox("background", true).visibleWhen(() -> !vanilla() && !WidgetDesign.active());
            shadow = s.checkbox("shadow", true).visibleWhen(() -> !WidgetDesign.active());
            textColor = s.chroma("text_color", LEVEL_GREEN, false).section(p + "section.colors").visibleWhen(() -> !vanilla());
            barColor = s.color("bar_color", 0xFF7CD63A, false).visibleWhen(() -> "bar".equals(style.get()));
        }

        @Override
        int barDefault() {
            return 0xFF7CD63A;
        }

        @Override
        double value(GameExtras.Status s) {
            return s.xpProgress();
        }

        @Override
        double max(GameExtras.Status s) {
            return 1;
        }

        /** Level, level with progress, or progress in percent. */
        @Override
        String text(GameExtras.Status s) {
            int percent = Math.round(s.xpProgress() * 100);
            return switch (format.get()) {
                case "value_max" -> s.xpLevel() + " (" + percent + "%)";
                case "percent" -> percent + "%";
                default -> Integer.toString(s.xpLevel());
            };
        }

        @Override
        int vanillaWidth(GameExtras.Status s) {
            return 182;
        }

        @Override
        int vanillaHeight(GameExtras.Status s) {
            return 11;
        }

        @Override
        void drawVanilla(HudGraphics g, GameExtras.Status s, RenderContext ctx) {
            module.sprites.experience(g, 0, 6, s.xpProgress());
            if (s.xpLevel() > 0) {
                String level = Integer.toString(s.xpLevel());
                int x = (182 - g.textWidth(level)) / 2;
                // Minecraft's outlined green number
                g.text(level, x + 1, 0, 0xFF000000, false);
                g.text(level, x - 1, 0, 0xFF000000, false);
                g.text(level, x, 1, 0xFF000000, false);
                g.text(level, x, -1, 0xFF000000, false);
                g.text(level, x, 0, LEVEL_GREEN, false);
            }
        }

        /** A small green experience orb. */
        @Override
        void icon(HudGraphics g, int x, int y, GameExtras.Status s) {
            g.fill(x + 2, y + 1, x + 7, y + 8, 0xFF3E8E1A);
            g.fill(x + 1, y + 2, x + 8, y + 7, 0xFF3E8E1A);
            g.fill(x + 2, y + 2, x + 7, y + 7, 0xFF8CEB3C);
            g.fill(x + 3, y + 3, x + 5, y + 5, 0xFFE8FFB0);
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.5f, 0.895f).enabledByDefault();
        }
    }

    // ------------------------------------------------------------------ hotbar

    /** The hotbar: Minecraft's look or Lumora's rounded slots, horizontal or vertical, offhand left/right/off. */
    static final class Hotbar extends HudWidget {
        private final StatusBarsModule module;
        ChoiceSetting style;
        BooleanSetting vertical;
        BooleanSetting numbers;
        ChoiceSetting offhand;
        BooleanSetting offhandSpecial;
        ColorSetting accent;

        Hotbar(StatusBarsModule module) {
            super("hotbar");
            this.module = module;
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            // the room for the offhand is the same on both sides, so the bar is centred like Minecraft's
            return WidgetPlacement.at(0.5f, 1f).enabledByDefault();
        }

        @Override
        public void configure(SettingsGroup s) {
            String p = s.translationPrefix();
            style = s.selection("style", "vanilla", "vanilla", "lumora").section(p + "section.look");
            vertical = s.checkbox("vertical", false);
            numbers = s.checkbox("numbers", false);
            accent = s.chroma("accent", Colors.ACCENT, false).visibleWhen(() -> "lumora".equals(style.get()) && !WidgetDesign.active());
            offhand = s.selection("offhand", "left", "left", "right", "off").section(p + "section.offhand");
            offhandSpecial = s.toggle("offhand_special", true).visibleWhen(() -> !"off".equals(offhand.get()));
        }

        boolean lumora() {
            return "lumora".equals(style.get());
        }

        /** Size of one slot step: 20 px like Minecraft, 22 px for Lumora's spaced slots. */
        int step() {
            return lumora() ? 22 : 20;
        }

        int barLength() {
            return lumora() ? 9 * 22 - 2 : 182;
        }

        int barThickness() {
            return lumora() ? 20 : 22;
        }

        /** Room for the offhand slot on each side of the bar (kept even when the slot is empty, so the bar stays centred). */
        int offhandRoom() {
            if ("off".equals(offhand.get()) || !module.sprites.offhand) {
                return 0;
            }
            return lumora() ? 26 : 29;
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            if (ctx.preview() || ctx.editing()) {
                return true;
            }
            GameExtras.Status s = module.status(ctx);
            return s.present() && !s.spectator();
        }

        @Override
        public int width(RenderContext ctx) {
            return vertical.get() ? 24 : barLength() + 2 * offhandRoom();
        }

        @Override
        public int height(RenderContext ctx) {
            return vertical.get() ? barLength() + 2 * offhandRoom() + 1 : 24;
        }

        /** Whether the offhand slot is drawn now: it has an item (shield and totem only if chosen). */
        boolean offhandShown(RenderContext ctx) {
            if (offhandRoom() == 0) {
                return false;
            }
            if (ctx.preview() || !ctx.game().inWorld()) {
                return ctx.editing() || ctx.preview();
            }
            String item = module.extras().slotItem(9);
            if (item.isEmpty()) {
                return false;
            }
            return offhandSpecial.get() || !(item.endsWith(":shield") || item.endsWith(":totem_of_undying"));
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            GameExtras.Status s = module.status(ctx);
            boolean v = vertical.get();
            boolean left = "left".equals(offhand.get());
            int room = offhandRoom();
            // start of the bar along its length, and where the offhand slot goes
            int bar = room;
            int off = left ? (lumora() ? room - 26 : 0) : room + barLength() + (lumora() ? 6 : v ? 7 : 0);
            boolean showOffhand = offhandShown(ctx);
            if (lumora()) {
                drawLumora(g, ctx, s, v, bar, off, showOffhand);
            } else {
                drawVanilla(g, ctx, s, v, bar, off, showOffhand, left);
            }
        }

        private void drawVanilla(HudGraphics g, RenderContext ctx, GameExtras.Status s, boolean v, int bar, int off,
                                 boolean showOffhand, boolean left) {
            VanillaSprites sp = module.sprites;
            boolean world = !ctx.preview() && ctx.game().inWorld();
            if (v) {
                // vertical: one 22 px piece of the strip per slot, the selection frame over the chosen one
                for (int i = 0; i < 9; i++) {
                    sp.hotbar(g, 1, bar + 1 + i * 20, i);
                }
                sp.selection(g, 0, bar + s.selected() * 20);
                if (showOffhand) {
                    sp.hotbar(g, 1, off + 1, 0);
                }
            } else {
                sp.hotbar(g, bar, 1, -1);
                sp.selection(g, bar - 1 + s.selected() * 20, 0);
                if (showOffhand) {
                    sp.offhand(g, off, 0, left);
                }
            }
            for (int i = 0; i < 9; i++) {
                int x = v ? 4 : bar + 3 + i * 20;
                int y = v ? bar + 4 + i * 20 : 4;
                slot(g, world, i, x, y);
            }
            if (showOffhand && world) {
                module.extras().drawSlot(g, 9, v ? 4 : off + (left ? 3 : 10), v ? off + 4 : 4);
            }
        }

        private void drawLumora(HudGraphics g, RenderContext ctx, GameExtras.Status s, boolean v, int bar, int off,
                                boolean showOffhand) {
            WidgetDesign.Look look = WidgetDesign.look();
            int accentColor = look != null ? look.accent() : accent.argbAt(ctx.timeMillis());
            boolean world = !ctx.preview() && ctx.game().inWorld();
            for (int i = 0; i < 9; i++) {
                int x = v ? 2 : bar + i * 22;
                int y = v ? bar + i * 22 : 2;
                boolean sel = i == s.selected();
                g.roundedRect(x, y, 20, 20, 4, sel ? accentColor : 0xC0101018);
                g.roundedRect(x + 1, y + 1, 18, 18, 3, sel ? Colors.mix(0xE0101018, accentColor, 0.25f) : 0xA0181424);
                slot(g, world, i, x + 2, y + 2);
            }
            if (showOffhand) {
                int x = v ? 2 : off;
                int y = v ? off : 2;
                g.roundedRect(x, y, 20, 20, 4, 0xC0101018);
                g.roundedRect(x + 1, y + 1, 18, 18, 3, 0xA0181424);
                if (world) {
                    module.extras().drawSlot(g, 9, x + 2, y + 2);
                }
            }
        }

        /** Item of a hotbar slot at (x, y) and, if chosen, its number in the corner. */
        private void slot(HudGraphics g, boolean world, int i, int x, int y) {
            if (world) {
                module.extras().drawSlot(g, i, x, y);
            }
            if (numbers.get()) {
                g.push();
                g.translate(x - 1, y - 1);
                g.scale(0.5f);
                g.text(Integer.toString(i + 1), 0, 0, 0xFFB8B4C4, true);
                g.pop();
            }
        }
    }
}
