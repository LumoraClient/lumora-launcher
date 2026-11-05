package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.HudRenderEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Built-in module "Angriffsanzeige": an attack indicator you actually see. Since 1.9 a hit only does full damage once
 * the weapon has recharged; the module shows that as a bar, ring or dot, or in the experience or boss bar, at the
 * crosshair or the hotbar, from half to double size. It also shows item cooldowns (ender pearl, chorus fruit, wind
 * charge, shield …), how far a bow, crossbow or trident is drawn, eating and drinking, block breaking and falling
 * asleep, with optional sounds when the attack is ready or a cooldown ended. While it is on, Minecraft's own attack
 * indicator is hidden ({@link #hidesVanilla()}, asked by the version adapters).
 */
public final class AttackIndicatorModule implements LumoraAddon {
    private static volatile AttackIndicatorModule active;
    static final String[] STYLES = {"bar", "ring", "dot", "xpbar", "bossbar"};
    static final String[] POSITIONS = {"crosshair", "hotbar"};
    /** How long the "ready" pop lasts. */
    static final long PULSE_MS = 260;

    private final Supplier<GameExtras> extras;
    private final LongSupplier clock;

    ChoiceSetting style;
    ChoiceSetting position;
    IntegerSetting size;
    BooleanSetting hideWhenReady;
    BooleanSetting showOnTarget;
    BooleanSetting pulse;
    BooleanSetting text;
    BooleanSetting gradient;
    BooleanSetting cooldowns;
    BooleanSetting charging;
    BooleanSetting using;
    BooleanSetting breaking;
    BooleanSetting sleeping;
    BooleanSetting soundReady;
    BooleanSetting soundCooldown;
    IntegerSetting volume;
    ColorSetting color;
    ColorSetting readyColor;
    ColorSetting cooldownColor;
    ColorSetting background;

    /** Attack strength fell below 90 % since the last "ready" (a swing or an item switch). */
    private boolean recharging;
    private long readyAt = Long.MIN_VALUE;
    private Set<String> lastCooldowns = new HashSet<>();

    public AttackIndicatorModule(Supplier<GameExtras> extras) {
        this(extras, System::currentTimeMillis);
    }

    AttackIndicatorModule(Supplier<GameExtras> extras, LongSupplier clock) {
        this.extras = extras;
        this.clock = clock;
    }

    /** The module while it is on, else null. */
    static AttackIndicatorModule current() {
        return active;
    }

    /** True while the module is on: the version adapters then hide Minecraft's own attack indicator. */
    public static boolean hidesVanilla() {
        return active != null;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        String k = "lumora.extras.attackindicator.";
        style = s.selection("style", "ring", STYLES).name(k + "style").section(k + "section.look");
        position = s.dropdown("position", "crosshair", POSITIONS).name(k + "position").visibleWhen(() -> !barStyle());
        size = s.slider("size", 100, 50, 200).step(5).unit("%").name(k + "size").visibleWhen(() -> !barStyle());
        color = s.chroma("color", 0xFFFFFFFF, true).name(k + "color").section(k + "section.colors");
        readyColor = s.chroma("ready_color", 0xFF4ADE80, true).name(k + "ready_color");
        cooldownColor = s.chroma("cooldown_color", 0xFFA78BFA, true).name(k + "cooldown_color");
        background = s.color("background", 0x70000000, true).name(k + "background").advanced();
        gradient = s.checkbox("gradient", false).name(k + "gradient");
        hideWhenReady = s.toggle("hide_when_ready", true).name(k + "hide_when_ready").section(k + "section.behaviour");
        showOnTarget = s.toggle("show_on_target", true).name(k + "show_on_target").requires(hideWhenReady);
        pulse = s.toggle("pulse", true).name(k + "pulse");
        text = s.toggle("text", false).name(k + "text");
        cooldowns = s.checkbox("cooldowns", true).name(k + "cooldowns").section(k + "section.show");
        charging = s.checkbox("charging", true).name(k + "charging");
        using = s.checkbox("using", true).name(k + "using");
        breaking = s.checkbox("breaking", true).name(k + "breaking");
        sleeping = s.checkbox("sleeping", true).name(k + "sleeping");
        soundReady = s.toggle("sound_ready", false).name(k + "sound_ready").section(k + "section.sounds");
        soundCooldown = s.toggle("sound_cooldown", true).name(k + "sound_cooldown").requires(cooldowns);
        volume = s.slider("volume", 60, 0, 100).step(5).unit("%").name(k + "volume")
                .visibleWhen(() -> soundReady.get() || (soundCooldown.get() && cooldowns.get()));
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(HudRenderEvent.class, e -> render(e.graphics(), e.context()));
        context.events().subscribe(WorldLeaveEvent.class, e -> reset());
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
        reset();
    }

    private void reset() {
        recharging = false;
        readyAt = Long.MIN_VALUE;
        lastCooldowns = new HashSet<>();
    }

    // ------------------------------------------------------------------ sounds and the "ready" moment

    /** Once per client tick: detects the moment the attack is ready again and cooldowns that ended. */
    void tick() {
        GameExtras x = extras.get();
        GameExtras.Progress p = x.progress(1f);
        float v = volume.get() / 100f;
        if (p.attack() >= 0) {
            if (p.attack() < 0.9f) {
                recharging = true;
            } else if (p.attack() >= 1f && recharging) {
                recharging = false;
                readyAt = clock.getAsLong();
                if (soundReady.get() && v > 0) {
                    x.playSound("ready", v);
                }
            }
        }
        Set<String> now = new HashSet<>();
        for (GameExtras.Cooldown c : p.cooldowns()) {
            now.add(c.itemId());
        }
        if (cooldowns.get() && soundCooldown.get() && v > 0) {
            for (String id : lastCooldowns) {
                if (!now.contains(id)) {
                    x.playSound("cooldown", v);
                    break;
                }
            }
        }
        lastCooldowns = now;
    }

    // ------------------------------------------------------------------ what is shown

    /** One indicator: kind ("attack", "bow", "eat", "breaking", "sleep", "cooldown" …), fill 0..1, colour, text. */
    record Shown(String kind, float value, int color, String label) {
    }

    /** Fraction of the "ready" pop that is left (1 right after the attack got ready, 0 when over). */
    float pulseLeft() {
        if (!pulse.get() || readyAt == Long.MIN_VALUE) {
            return 0;
        }
        long age = clock.getAsLong() - readyAt;
        return age < 0 || age >= PULSE_MS ? 0 : 1f - age / (float) PULSE_MS;
    }

    /** The main indicator (use > breaking > sleep > attack) followed by the item cooldowns. */
    List<Shown> shown(GameExtras.Progress p) {
        List<Shown> out = new ArrayList<>();
        String use = p.use() == null ? "" : p.use();
        boolean charge = use.equals("bow") || use.equals("crossbow") || use.equals("spear");
        boolean food = use.equals("eat") || use.equals("drink");
        if ((charge && charging.get()) || (food && using.get())) {
            float v = clamp(p.useProgress());
            out.add(new Shown(use, v, fill(v), percent(v)));
        } else if (breaking.get() && p.breaking() >= 0) {
            float v = clamp(p.breaking());
            out.add(new Shown("breaking", v, fill(v), percent(v)));
        } else if (sleeping.get() && p.sleep() >= 0) {
            float v = clamp(p.sleep());
            out.add(new Shown("sleep", v, fill(v), percent(v)));
        } else if (p.attack() >= 0) {
            float v = clamp(p.attack());
            boolean ready = v >= 1f;
            if (!ready || !hideWhenReady.get() || (p.target() && showOnTarget.get()) || pulseLeft() > 0) {
                out.add(new Shown("attack", v, fill(v), percent(v)));
            }
        }
        if (cooldowns.get()) {
            for (GameExtras.Cooldown c : p.cooldowns()) {
                float v = clamp(1f - c.remaining());
                out.add(new Shown("cooldown", v, v >= 1f ? readyColor.argb() : cooldownColor.argb(), c.name()));
            }
        }
        return out;
    }

    private int fill(float v) {
        if (v >= 1f) {
            return readyColor.argb();
        }
        if (!gradient.get()) {
            return color.argb();
        }
        // red -> yellow -> green, keeping the alpha of the colour setting
        int a = color.argb() & 0xFF000000;
        int rgb = v < 0.5f ? LumoraTheme.mix(0xFFEF4444, 0xFFFACC15, v * 2) : LumoraTheme.mix(0xFFFACC15, 0xFF22C55E, (v - 0.5f) * 2);
        return a | (rgb & 0xFFFFFF);
    }

    private static String percent(float v) {
        return Math.round(v * 100) + "%";
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    /** Styles "Levelleiste" and "Bossleiste": fixed full-width bars, position and size do not apply. */
    boolean barStyle() {
        String st = style.get();
        return st.equals("xpbar") || st.equals("bossbar");
    }

    /**
     * "Levelleiste": the main indicator fills Minecraft's experience bar (182 x 5 px, 29 px above the bottom), further
     * ones are short bars in the free space right of the hotbar. "Bossleiste": every indicator is a bar like a boss
     * bar at the top of the screen (from y 12, every 19 px), with its percent or item name above it.
     */
    void bars(HudGraphics g, List<Shown> list, int w, int h, boolean xp, float pop) {
        int bg = background.argb();
        int x = w / 2 - 91;
        for (int i = 0; i < list.size(); i++) {
            Shown it = list.get(i);
            boolean main = i == 0 && !it.kind().equals("cooldown");
            int c = main && pop > 0 ? readyColor.argb() : it.color();
            if (xp && i > 0) {
                int gx = w / 2 + 91 + 6;
                int gy = h - 20 + (i - 1) * 5;
                g.fill(gx - 1, gy - 1, gx + 33, gy + 4, 0xA0000000);
                g.fill(gx, gy, gx + 32, gy + 3, bg);
                g.fill(gx, gy, gx + Math.round(it.value() * 32), gy + 3, c);
                label(g, it, gx + 35, gy - 1, true);
                continue;
            }
            int y = xp ? h - 29 : 12 + i * 19;
            int grow = main && pop > 0 ? 1 : 0;
            int filled = Math.round(it.value() * 182);
            g.fill(x - 1, y - 1 - grow, x + 183, y + 6 + grow, 0xA0000000);
            g.fill(x, y - grow, x + 182, y + 5 + grow, bg);
            g.fill(x, y - grow, x + filled, y + 5 + grow, c);
            if (filled > 0) {
                // lighter top edge like Minecraft's bar textures
                g.fill(x, y - grow, x + filled, y - grow + 1, (c & 0xFF000000) | (LumoraTheme.mix(c, 0xFFFFFFFF, 0.35f) & 0xFFFFFF));
            }
            String title = it.label();
            if (text.get() && title != null && !title.isEmpty()) {
                // boss bar: title above the bar; experience bar: the percent where Minecraft shows the level
                int ty = xp ? h - 35 : y - 10;
                g.text(title, w / 2 - g.textWidth(title) / 2, ty, xp ? c | 0xFF000000 : 0xFFFFFFFF, true);
            }
        }
    }

    /** Whether the indicator sits at the hotbar (else at the crosshair). */
    boolean atHotbar() {
        return position.get().equals("hotbar");
    }

    // ------------------------------------------------------------------ drawing

    void render(HudGraphics g, RenderContext ctx) {
        if (active != this || ctx.editing() || ctx.preview()) {
            return;
        }
        GameExtras.Progress p = extras.get().progress(ctx.partialTick());
        List<Shown> list = shown(p);
        if (list.isEmpty()) {
            return;
        }
        int w = ctx.screenWidth();
        int h = ctx.screenHeight();
        float pop = list.get(0).kind().equals("attack") ? pulseLeft() : 0;
        if (barStyle()) {
            bars(g, list, w, h, style.get().equals("xpbar"), pop);
            return;
        }
        boolean hotbar = atHotbar();
        if (!hotbar && !p.firstPerson()) {
            return;
        }
        float s = size.get() / 100f;
        g.push();
        if (hotbar) {
            // middle of the 18 x 18 slot right of the hotbar, where Minecraft draws its own hotbar indicator
            g.translate(w / 2f + 91 + 6 + 9, h - 11);
        } else {
            // middle pixel of the 15 px vanilla crosshair
            g.translate((w - 15) / 2 + 7 + 0.5f, (h - 15) / 2 + 7 + 0.5f);
        }
        g.scale(s);
        draw(g, list, hotbar, pop);
        g.pop();
    }

    /** Draws the indicators around (0, 0): the crosshair's middle, or the middle of the slot right of the hotbar. */
    void draw(HudGraphics g, List<Shown> list, boolean hotbar, float pop) {
        int bg = background.argb();
        String st = style.get();
        for (int i = 0; i < list.size(); i++) {
            Shown it = list.get(i);
            boolean main = i == 0 && !it.kind().equals("cooldown");
            int n = i;
            float grow = main ? 1f + 0.25f * pop : 1f;
            int c = main && pop > 0 ? readyColor.argb() : it.color();
            g.push();
            switch (st) {
                case "bar" -> {
                    if (hotbar) {
                        g.translate(n * 22, 0);
                        g.scale(grow);
                        g.fill(-9, -2, 9, 2, bg);
                        g.fill(-9, -2, -9 + Math.round(it.value() * 18), 2, c);
                        label(g, it, -9, 4, hotbar);
                    } else {
                        g.translate(0, 11 + n * 4);
                        g.scale(grow);
                        g.fill(-12, -1, 12, 1, bg);
                        g.fill(-12, -1, -12 + Math.round(it.value() * 24), 1, c);
                        label(g, it, 14, -2, hotbar);
                    }
                }
                case "dot" -> {
                    if (hotbar) {
                        g.translate(n * 10, 0);
                    } else if (n == 0) {
                        g.translate(0, 11);
                    } else {
                        g.translate(0, 16 + (n - 1) * 5);
                    }
                    g.scale(grow);
                    // grows from 3 to 5 px (cooldowns 3 to 4) and brightens while charging, on a dark rim
                    int d = Math.round(main ? 3f + 2f * it.value() : 3f + it.value());
                    int x0 = -(d / 2);
                    g.roundedRect(x0 - 1, x0 - 1, d + 2, d + 2, Math.max(1, (d + 2) / 3), bg | 0xC0000000);
                    g.roundedRect(x0, x0, d, d, Math.max(1, d / 3), it.value() >= 1f ? c
                            : LumoraTheme.mix(0xFF4A4A4A, c, 0.3f + 0.7f * it.value()));
                    label(g, it, d / 2 + 2, -2, hotbar);
                }
                default -> { // ring
                    if (hotbar) {
                        g.translate(n * 22, 0);
                        g.scale(grow);
                        ring(g, 8, 1.5f, it.value(), c, bg);
                        label(g, it, -9, 11, hotbar);
                    } else {
                        g.scale(grow);
                        float radius = 10 + n * 3.5f;
                        ring(g, radius, main ? 1.5f : 1f, it.value(), c, bg);
                        label(g, it, Math.round(radius) + 3, -2 + (main ? 0 : n * 6 - 6), hotbar);
                    }
                }
            }
            g.pop();
        }
    }

    /** Percent of the main indicator or name of the cooldown item, at half size. */
    private void label(HudGraphics g, Shown it, int x, int y, boolean hotbar) {
        if (!text.get() || it.label() == null || it.label().isEmpty()) {
            return;
        }
        g.push();
        g.translate(x, y);
        g.scale(0.5f);
        g.text(it.label(), 0, 0, 0xFFFFFFFF, true);
        g.pop();
    }

    /**
     * A ring of radius {@code r} filled clockwise from the top: small squares on a four times finer grid, spaced so
     * neighbours just touch (a translucent background stays even).
     */
    static void ring(HudGraphics g, float r, float thick, float value, int fg, int bg) {
        g.push();
        g.scale(0.25f);
        int radius = Math.round(r * 4);
        int t = Math.max(2, Math.round(thick * 4));
        int steps = Math.max(16, (int) Math.round(2 * Math.PI * radius / t));
        int filled = Math.round(value * steps);
        for (int i = 0; i < steps; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * (i + 0.5) / steps;
            int x = (int) Math.round(Math.cos(a) * radius) - t / 2;
            int y = (int) Math.round(Math.sin(a) * radius) - t / 2;
            g.fill(x, y, x + t, y + t, i < filled ? fg : bg);
        }
        g.pop();
    }
}
