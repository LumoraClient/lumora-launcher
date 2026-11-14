package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;
import dev.lumora.core.hud.WidgetDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Built-in module "Armor HUD": helmet, chestplate, leggings, boots and the held item, each its own HUD widget with icon
 * and durability, so every piece can be moved on its own in the HUD editor. Written once on the Lumora API; items and
 * durability come from {@link GameExtras} of the version adapter. Names and texts are in the language files
 * ({@code lumora-armor.widget.<piece>}).
 */
public final class ArmorHudModule implements LumoraAddon {
    /** Widget ids in the order of {@link GameExtras#equipment(int)}. */
    static final List<String> PIECES = List.of("helmet", "chestplate", "leggings", "boots", "mainhand");
    /** Durability of the diamond pieces shown as preview (main hand: diamond sword). */
    static final int[] SAMPLE_MAX = {363, 528, 495, 429, 1561};
    static final int ICON = 16;
    static final int GAP = 3;
    static final int RED = 0xFFF2555A;

    private final Supplier<GameExtras> extras;
    private final Supplier<Boolean> inWorld;

    public ArmorHudModule(Platform platform) {
        this(platform::extras, () -> platform.game().inWorld());
    }

    ArmorHudModule(Supplier<GameExtras> extras, Supplier<Boolean> inWorld) {
        this.extras = extras;
        this.inWorld = inWorld;
    }

    @Override
    public void onEnable(AddonContext context) {
        for (Piece p : pieces()) {
            context.widgets().register(p);
        }
    }

    /** The five widgets (tests). */
    List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < PIECES.size(); i++) {
            out.add(new Piece(i));
        }
        return out;
    }

    /** Text of a piece: durability as chosen, the stack size for items without durability, "" for single items. */
    static String text(GameExtras.Equipment e, String format) {
        if (!e.present()) {
            return "—";
        }
        if (!e.damageable()) {
            return e.count() > 1 ? "×" + e.count() : "";
        }
        return switch (format) {
            case "percent" -> Math.round(100f * e.durability() / e.maxDurability()) + "%";
            case "value_max" -> e.durability() + "/" + e.maxDurability();
            default -> Integer.toString(e.durability());
        };
    }

    /** Green above half, yellow above a fifth, red below. */
    static int durabilityColor(GameExtras.Equipment e) {
        float f = (float) e.durability() / e.maxDurability();
        return f > 0.5f ? Colors.OK : f > 0.2f ? Colors.WARNING : RED;
    }

    final class Piece extends Panels.Panel {
        final int index;
        ChoiceSetting durability;
        BooleanSetting colored;
        BooleanSetting hideEmpty;
        private GameExtras.Equipment now = GameExtras.Equipment.NONE;
        private boolean preview = true;
        private int width = 40;

        Piece(int index) {
            super(PIECES.get(index));
            this.index = index;
        }

        @Override
        public String nameKey() {
            return "lumora-armor.widget." + id();
        }

        @Override
        public String descriptionKey() {
            return nameKey() + ".description";
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            // one column on the left edge below the info widgets, helmet on top, held item last (24 px each, touching)
            return WidgetPlacement.anchored("center_left", 4, (index - 1) * 24).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        void extra(SettingsGroup s) {
            durability = s.choice("durability", "value", "value", "value_max", "percent");
            colored = s.toggle("colored", true);
            hideEmpty = s.toggle("hide_empty", true);
        }

        @Override
        public void initialize() {
            tick();
        }

        @Override
        public void tick() {
            preview = !inWorld.get();
            now = preview ? new GameExtras.Equipment(true, SAMPLE_MAX[index], SAMPLE_MAX[index], 1)
                    : extras.get().equipment(index);
        }

        /** What the widget shows right now (tests). */
        GameExtras.Equipment shown() {
            return now;
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            return ctx.editing() || ctx.preview() || preview || now.present() || !hideEmpty.get();
        }

        @Override
        public int width(RenderContext ctx) {
            return width;
        }

        @Override
        public int height(RenderContext ctx) {
            return ICON + Panels.PAD * 2;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            GameExtras.Equipment e = now;
            String text = text(e, durability.get());
            width = Panels.PAD + 2 + ICON + (text.isEmpty() ? 0 : GAP + g.textWidth(text)) + Panels.PAD;
            int h = height(ctx);
            panel(g, width, h);
            // an empty slot shows the diamond piece as outline of where it goes (HUD editor, "hide when empty" off)
            extras.get().drawEquipment(g, index, preview || !e.present(), Panels.PAD + 2, Panels.PAD);
            if (!text.isEmpty()) {
                int color = !e.present() ? Colors.MUTED : e.damageable() && colored.get() ? durabilityColor(e)
                        : WidgetDesign.valueColor(valueColor.argb());
                g.text(text, Panels.PAD + 2 + ICON + GAP, (h - g.lineHeight()) / 2 + 1, color, WidgetDesign.shadow(true));
            }
        }
    }
}
