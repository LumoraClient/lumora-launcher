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

    final class Potions extends Info {
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
        List<String[]> lines(boolean preview) {
            List<GameExtras.Effect> effects = preview
                    ? List.of(new GameExtras.Effect(tr("lumora.hud.potions.sample_speed", "Schnelligkeit"), 1, 2710, true),
                    new GameExtras.Effect(tr("lumora.hud.potions.sample_fire", "Feuerresistenz"), 0, 5400, true))
                    : x().effects();
            List<String[]> out = new ArrayList<>();
            for (GameExtras.Effect e : effects) {
                String name = e.name() + (e.amplifier() > 0 ? " " + roman(e.amplifier() + 1) : "");
                out.add(new String[]{"", name + "  " + duration(e.durationTicks()),
                        Panels.hex(e.beneficial() ? Colors.TEXT : 0xFFF28A8D)});
            }
            return out;
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            return ctx.editing() || ctx.preview() || !platform.game().inWorld() || !x().effects().isEmpty();
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
