package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.game.PlayerSnapshot;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;
import dev.lumora.core.hud.WidgetDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Built-in module "Info-Widgets": status effects with remaining time, arrow and item counter, compass, memory, day
 * counter, active resource packs and a combo counter. Written once on the Lumora API; the game details come from
 * {@link GameExtras} of the version adapter.
 */
public final class InfoWidgetsModule implements LumoraAddon {
    private final Platform platform;
    private final ComboTracker combo = new ComboTracker();

    public InfoWidgetsModule(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void onEnable(AddonContext context) {
        context.widgets().register(new Potions());
        context.widgets().register(new HeldItemTooltip(platform, this::tr));
        context.widgets().register(new Items());
        context.widgets().register(new Compass());
        context.widgets().register(new Memory());
        context.widgets().register(new Day());
        context.widgets().register(new Packs());
        context.widgets().register(new Combo());
        context.events().subscribe(ClientTickEvent.class, e -> {
            if (platform.game().inWorld()) {
                GameExtras x = platform.extras();
                int target = x.targetEntityId();
                combo.tick(target, target >= 0 ? x.hurtTime(target) : -1, x.ownHurtTime());
            }
        });
        context.events().subscribe(WorldLeaveEvent.class, e -> combo.reset());
    }

    /** For the version mixins: Minecraft's name of the held item is hidden while the "Held Item Tooltip" widget is in the HUD. */
    public static boolean heldItemTooltipTakesOver() {
        return HeldItemTooltip.takesOver();
    }

    String tr(String key, String fallback) {
        return platform.translations().translateOr(key, fallback);
    }

    abstract class Info extends Panels.Text {
        Info(String id) {
            super(id);
        }

        @Override
        boolean inWorld() {
            return platform.game().inWorld();
        }

        GameExtras x() {
            return platform.extras();
        }
    }

    /**
     * Status effects as a list like Minecraft's inventory: one box per effect with its picture, name and level, and the
     * remaining time below; effects of a beacon or conduit get a turquoise border. "Kompakt" shows the old single lines.
     */
    final class Potions extends Panels.Panel {
        static final int ICON = 18;
        static final int ROW = 24;
        static final int GAP = 2;
        static final int HARMFUL = 0xFFF28A8D;
        static final int AMBIENT = 0xFF4FD6DC;
        BooleanSetting compact;
        private List<GameExtras.Effect> effects = List.of();
        private int width = 80;

        Potions() {
            super("potions");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.3f);
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        void extra(SettingsGroup s) {
            compact = s.toggle("compact", false);
        }

        @Override
        public void initialize() {
            refresh();
        }

        @Override
        public void onEnable() {
            refresh();
        }

        @Override
        public void tick() {
            refresh();
        }

        private void refresh() {
            effects = platform.game().inWorld() ? x().effects() : sample();
        }

        private GameExtras x() {
            return platform.extras();
        }

        /** Two effects for the HUD editor and menus. */
        List<GameExtras.Effect> sample() {
            return List.of(
                    new GameExtras.Effect(tr("lumora.hud.potions.sample_speed", "Schnelligkeit"), 1, 2710, true, false,
                            x().effectIcon("minecraft:speed")),
                    new GameExtras.Effect(tr("lumora.hud.potions.sample_fire", "Feuerresistenz"), 0, 5400, true, true,
                            x().effectIcon("minecraft:fire_resistance")));
        }

        static String title(GameExtras.Effect e) {
            return e.name() + (e.amplifier() > 0 ? " " + roman(e.amplifier() + 1) : "");
        }

        private boolean compact() {
            return compact != null && compact.get();
        }

        @Override
        public int width(RenderContext ctx) {
            return width;
        }

        @Override
        public int height(RenderContext ctx) {
            int n = Math.max(1, effects.size());
            return compact() ? n * Panels.LINE + Panels.PAD * 2 - 1 : n * (ROW + GAP) - GAP;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            List<GameExtras.Effect> list = effects;
            int name = valueColor.argb();
            if (compact()) {
                int w = 0;
                for (GameExtras.Effect e : list) {
                    w = Math.max(w, WidgetDesign.lineWidth(g::textWidth, "", title(e) + "  " + duration(e.durationTicks())));
                }
                width = Math.max(20, w + Panels.PAD * 2 + 2);
                panel(g, width, height(ctx));
                int y = Panels.PAD;
                for (GameExtras.Effect e : list) {
                    WidgetDesign.line(g, Panels.PAD + 2, y, "", title(e) + "  " + duration(e.durationTicks()),
                            labelColor.argb(), e.beneficial() ? name : HARMFUL, true, !e.beneficial());
                    y += Panels.LINE;
                }
                return;
            }
            int textX = 4 + ICON + 4;
            int w = 0;
            for (GameExtras.Effect e : list) {
                w = Math.max(w, Math.max(WidgetDesign.lineWidth(g::textWidth, "", title(e)),
                        WidgetDesign.lineWidth(g::textWidth, "", duration(e.durationTicks()))));
            }
            width = Math.max(60, textX + w + 6);
            WidgetDesign.Look look = WidgetDesign.look();
            int radius = look != null ? look.radius() : 0;
            int y = 0;
            for (GameExtras.Effect e : list) {
                g.push();
                g.translate(0, y);
                panel(g, width, ROW);
                if (e.ambient()) {
                    WidgetDesign.outline(g, 0, 0, width, ROW, radius, AMBIENT);
                }
                GameExtras.EffectIcon icon = e.icon();
                if (icon != null) {
                    g.texture(icon.texture(), 4, (ROW - ICON) / 2, ICON, ICON, icon.u(), icon.v(), icon.size(), icon.size(),
                            icon.textureWidth(), icon.textureHeight());
                }
                int tx = icon != null ? textX : 6;
                WidgetDesign.line(g, tx, 3, "", title(e), labelColor.argb(), e.beneficial() ? name : HARMFUL, true, !e.beneficial());
                WidgetDesign.line(g, tx, 13, "", duration(e.durationTicks()), labelColor.argb(), Colors.MUTED, true, true);
                g.pop();
                y += ROW + GAP;
            }
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            return ctx.editing() || ctx.preview() || !platform.game().inWorld() || !x().effects().isEmpty();
        }
    }

    /**
     * "Held Item Tooltip": the name of the held item for a moment after switching, like Minecraft above the hotbar.
     * Style "Java" draws it like Minecraft (name in its rarity colour, shadow, fading out); "Pocket" puts name and
     * enchantments in a dark box with a border like the Pocket/Bedrock edition. While shown it replaces Minecraft's own
     * name (version mixins ask {@link #takesOver()}).
     */
    static final class HeldItemTooltip extends dev.lumora.api.hud.HudWidget {
        static final int SHOW_TICKS = 40;
        static final int FADE_TICKS = 10;
        static final int[] RARITY = {0xFFFFFF, 0xFFFF55, 0x55FFFF, 0xFF55FF};
        static final int JAVA_ENCHANT = 0xAAAAAA;
        static final int POCKET_ENCHANT = 0x8F8FFF;
        static final int CURSE = 0xFF5555;
        static final int POCKET_BACKGROUND = 0x2A2A2A;
        static final int POCKET_BORDER = 0x101010;
        /** When a HUD frame last asked for this widget: Minecraft's name stays hidden shortly after. */
        private static volatile long active;
        private final Platform platform;
        private final java.util.function.BiFunction<String, String, String> tr;
        dev.lumora.api.config.ChoiceSetting style;
        BooleanSetting enchantments;
        BooleanSetting always;
        private GameExtras.HeldItem item;
        private String shownKey = "";
        private int timer;

        HeldItemTooltip(Platform platform, java.util.function.BiFunction<String, String, String> tr) {
            super("helditem");
            this.platform = platform;
            this.tr = tr;
        }

        /** Whether Minecraft's own name of the held item is hidden (this widget is in the HUD). */
        public static boolean takesOver() {
            return System.currentTimeMillis() - active < 1000;
        }

        @Override
        public String nameKey() {
            return "lumora.hud." + id();
        }

        @Override
        public String descriptionKey() {
            return "lumora.hud." + id() + ".description";
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.5f, 0.76f);
        }

        @Override
        public void configure(SettingsGroup s) {
            style = s.choice("style", "pocket", "java", "pocket");
            enchantments = s.toggle("enchantments", true);
            always = s.toggle("always", false);
        }

        @Override
        public void tick() {
            GameExtras.HeldItem now = platform.game().inWorld() ? platform.extras().heldItem() : null;
            String key = key(now);
            if (!key.equals(shownKey)) {
                shownKey = key;
                timer = now == null ? 0 : SHOW_TICKS + FADE_TICKS;
            } else if (timer > 0) {
                timer--;
            }
            item = now;
        }

        /** What makes Minecraft show the name again: another slot, item, name or enchantments. */
        static String key(GameExtras.HeldItem h) {
            if (h == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder().append(h.slot()).append('|').append(h.id()).append('|').append(h.name());
            for (GameExtras.Enchant e : h.enchantments()) {
                sb.append('|').append(e.name());
            }
            return sb.toString();
        }

        /** The item to draw: the sample in the editor and menus, else the held one. */
        private GameExtras.HeldItem shown(RenderContext ctx) {
            if (ctx.editing() || ctx.preview() || !platform.game().inWorld()) {
                return new GameExtras.HeldItem("minecraft:diamond_sword", tr.apply("lumora.hud.helditem.sample", "Diamantschwert"), 2,
                        false, List.of(new GameExtras.Enchant(tr.apply("lumora.hud.helditem.sample_enchant", "Schärfe V"), false)), 0);
            }
            return item;
        }

        /** 0..255: full while shown, then fading out like Minecraft; always full with "always". */
        int alpha(RenderContext ctx) {
            if (ctx.editing() || ctx.preview() || !platform.game().inWorld() || always.get()) {
                return 255;
            }
            return Math.min(255, timer * 256 / FADE_TICKS);
        }

        List<String> lines(GameExtras.HeldItem h) {
            List<String> out = new ArrayList<>();
            out.add(h.customName() ? "§o" + h.name() : h.name());
            if (enchantments.get()) {
                for (GameExtras.Enchant e : h.enchantments()) {
                    out.add(e.name());
                }
            }
            return out;
        }

        boolean pocket() {
            return "pocket".equals(style.get());
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            active = System.currentTimeMillis();
            GameExtras.HeldItem h = shown(ctx);
            return h != null && alpha(ctx) > 4;
        }

        /** Widest line, measured while drawing (the HUD asks for the size before the first frame: an estimate). */
        private int textWidth = 60;

        @Override
        public int width(RenderContext ctx) {
            return textWidth + (pocket() ? 12 : 2);
        }

        @Override
        public int height(RenderContext ctx) {
            GameExtras.HeldItem h = shown(ctx);
            int n = h == null ? 1 : lines(h).size();
            return n * 10 - 1 + (pocket() ? 7 : 0);
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            GameExtras.HeldItem h = shown(ctx);
            int a = alpha(ctx);
            if (h == null || a <= 4) {
                return;
            }
            List<String> l = lines(h);
            int tw = 0;
            for (String line : l) {
                tw = Math.max(tw, g.textWidth(line));
            }
            textWidth = tw;
            int w = width(ctx);
            int name = RARITY[Math.max(0, Math.min(3, h.rarity()))];
            boolean pocket = pocket();
            int top = 0;
            if (pocket) {
                // dark translucent box with a black frame and a lighter inner line, like the Pocket edition
                g.fill(0, 0, w, height(ctx), (a * 0xC8 / 255) << 24 | POCKET_BACKGROUND);
                int frame = a << 24 | POCKET_BORDER;
                g.outline(0, 0, w, height(ctx), frame);
                g.outline(1, 1, w - 2, height(ctx) - 2, (a * 0x50 / 255) << 24 | 0xFFFFFF);
                top = 4;
            }
            for (int i = 0; i < l.size(); i++) {
                int rgb;
                if (i == 0) {
                    rgb = name;
                } else {
                    GameExtras.Enchant e = h.enchantments().get(i - 1);
                    rgb = e.curse() ? CURSE : pocket ? POCKET_ENCHANT : JAVA_ENCHANT;
                }
                g.centeredText(l.get(i), w / 2, top + i * 10, a << 24 | rgb, true);
            }
        }
    }

    final class Items extends Info {
        BooleanSetting arrows;
        BooleanSetting held;

        Items() {
            super("items");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.78f);
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        void extra(SettingsGroup s) {
            arrows = s.toggle("arrows", true);
            held = s.toggle("held", true);
        }

        @Override
        List<String[]> lines(boolean preview) {
            List<String[]> out = new ArrayList<>();
            if (arrows.get()) {
                int n = preview ? 42 : x().count("minecraft:arrow") + x().count("minecraft:spectral_arrow") + x().count("minecraft:tipped_arrow");
                out.add(new String[]{tr("lumora.hud.items.arrows", "Pfeile"), Integer.toString(n)});
            }
            if (held.get()) {
                String id = preview ? "minecraft:cobblestone" : x().heldItemId();
                if (!id.isEmpty()) {
                    String name = preview ? tr("lumora.hud.items.sample", "Bruchstein") : x().heldItemName();
                    out.add(new String[]{name, Integer.toString(preview ? 128 : x().count(id))});
                }
            }
            return out;
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            return arrows.get() || held.get();
        }
    }

    /** Horizontal compass strip: letters move with the view, a marker shows the exact heading. */
    final class Compass extends Info {
        static final int W = 150;
        static final float PX_PER_DEGREE = 1.4f;
        private float yaw = 180;

        Compass() {
            super("compass");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.5f, 0.01f);
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        List<String[]> lines(boolean preview) {
            yaw = platform.game().player().map(PlayerSnapshot::yaw).orElse(180f);
            return List.<String[]>of(new String[]{"", ""});
        }

        @Override
        public int width(RenderContext ctx) {
            return W;
        }

        @Override
        public int height(RenderContext ctx) {
            return 22;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            panel(g, W, 22);
            String[] de = {tr("lumora.hud.compass.s", "S"), tr("lumora.hud.compass.sw", "SW"), tr("lumora.hud.compass.w", "W"),
                    tr("lumora.hud.compass.nw", "NW"), tr("lumora.hud.compass.n", "N"), tr("lumora.hud.compass.ne", "NO"),
                    tr("lumora.hud.compass.e", "O"), tr("lumora.hud.compass.se", "SO")};
            int center = W / 2;
            for (int i = 0; i < de.length; i++) {
                float d = wrap(i * 45 - yaw);
                int xPos = Math.round(center + d * PX_PER_DEGREE);
                String label = de[i];
                int lw = g.textWidth(label);
                if (xPos - lw / 2 > 3 && xPos + lw / 2 < W - 3) {
                    boolean main = i % 2 == 0;
                    g.text(label, xPos - lw / 2, 4, main ? (i == 4 ? Colors.ACCENT_HOVER : valueColor.argb()) : Colors.MUTED, true);
                }
            }
            for (int deg = 0; deg < 360; deg += 15) {
                float d = wrap(deg - yaw);
                int xPos = Math.round(center + d * PX_PER_DEGREE);
                if (xPos > 3 && xPos < W - 3 && deg % 45 != 0) {
                    g.fill(xPos, 8, xPos + 1, 11, Colors.withAlpha(Colors.MUTED, 0xA0));
                }
            }
            g.fill(center, 15, center + 1, 21, Colors.ACCENT);
            String heading = Math.round(((yaw + 180) % 360 + 360) % 360) + "°";
            g.text(heading, W - 4 - g.textWidth(heading), 13, Colors.MUTED, true);
        }

        private static float wrap(float d) {
            float r = d % 360;
            if (r > 180) {
                r -= 360;
            } else if (r < -180) {
                r += 360;
            }
            return r;
        }
    }

    final class Memory extends Info {
        Memory() {
            super("memory");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.30f);
        }

        @Override
        public String category() {
            return WidgetCategory.GENERAL;
        }

        @Override
        List<String[]> lines(boolean preview) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long max = rt.maxMemory() / (1024 * 1024);
            int pct = max > 0 ? (int) (used * 100 / max) : 0;
            return List.<String[]>of(new String[]{tr("lumora.hud.memory.label", "RAM"),
                    pct + "% · " + used + " / " + max + " MB", Panels.hex(pct > 85 ? Colors.ERROR : pct > 70 ? Colors.WARNING : Colors.TEXT)});
        }
    }

    final class Day extends Info {
        BooleanSetting time;

        Day() {
            super("day");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.22f);
        }

        @Override
        public String category() {
            return WidgetCategory.TIME;
        }

        @Override
        void extra(SettingsGroup s) {
            time = s.toggle("time", true);
        }

        @Override
        List<String[]> lines(boolean preview) {
            long t = preview ? 3 * 24000L + 6000 : x().dayTime();
            if (t < 0) {
                return List.of();
            }
            List<String[]> out = new ArrayList<>();
            out.add(new String[]{tr("lumora.hud.day.label", "Tag"), Long.toString(t / 24000 + 1)});
            if (time.get()) {
                long ofDay = (t + 6000) % 24000; // tick 0 is 06:00
                out.add(new String[]{tr("lumora.hud.day.time", "Uhrzeit"), String.format(Locale.ROOT, "%02d:%02d", ofDay / 1000, ofDay % 1000 * 60 / 1000)});
            }
            return out;
        }
    }

    final class Packs extends Info {
        Packs() {
            super("packs");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.40f);
        }

        @Override
        public String category() {
            return WidgetCategory.GENERAL;
        }

        @Override
        List<String[]> lines(boolean preview) {
            List<String> packs = preview ? List.of("Lumora Theme", "Fresh Animations") : x().resourcePacks();
            List<String[]> out = new ArrayList<>();
            int i = 0;
            for (String p : packs) {
                if (i++ == 6) {
                    out.add(new String[]{"", "+" + (packs.size() - 6)});
                    break;
                }
                String n = p.replaceFirst("(?i)\\.zip$", "");
                out.add(new String[]{"", n.length() > 32 ? n.substring(0, 31) + "…" : n});
            }
            if (out.isEmpty()) {
                out.add(new String[]{"", tr("lumora.hud.packs.none", "Keine Ressourcenpakete")});
            }
            return out;
        }
    }

    final class Combo extends Info {
        Combo() {
            super("combo");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.5f, 0.62f);
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        List<String[]> lines(boolean preview) {
            int n = preview ? 4 : combo.combo();
            return List.<String[]>of(new String[]{tr("lumora.hud.combo.label", "Combo"), Integer.toString(n),
                    Panels.hex(n >= 5 ? Colors.ACCENT_HOVER : Colors.TEXT)});
        }
    }

    /**
     * Combo counter: a hit on the entity under the crosshair (its hurtTime jumps up) raises it; being hit yourself or
     * three seconds without a hit reset it.
     */
    static final class ComboTracker {
        static final int TIMEOUT_TICKS = 60;
        private int combo;
        private int lastTarget = -1;
        private int lastTargetHurt;
        private int lastOwnHurt;
        private int sinceHit;

        void tick(int target, int targetHurt, int ownHurt) {
            if (ownHurt > lastOwnHurt) {
                combo = 0;
            }
            lastOwnHurt = ownHurt;
            if (target >= 0 && target == lastTarget && targetHurt > lastTargetHurt) {
                combo++;
                sinceHit = 0;
            } else if (++sinceHit > TIMEOUT_TICKS) {
                combo = 0;
            }
            lastTarget = target;
            lastTargetHurt = Math.max(0, targetHurt);
        }

        int combo() {
            return combo;
        }

        void reset() {
            combo = 0;
            lastTarget = -1;
            lastTargetHurt = 0;
            lastOwnHurt = 0;
            sinceHit = 0;
        }
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }

    static String duration(int ticks) {
        if (ticks < 0 || ticks > 20 * 60 * 60 * 24) {
            return "∞";
        }
        int s = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
