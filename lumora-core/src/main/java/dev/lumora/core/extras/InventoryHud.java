package dev.lumora.core.extras;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * HUD widget "Inventory": the player's inventory (the three rows, optionally the hotbar below) as a HUD panel, shown by
 * a key: while it is held, switched on and off, for a few seconds after a press, or always. Look of Minecraft's
 * inventory screen or of the Lumora widgets. Items, counts and durability bars are drawn by the version adapter
 * ({@link GameExtras#drawSlot}).
 */
final class InventoryHud extends Panels.Panel {
    static final String ID = "inventory";
    static final int SLOT = 18;
    static final int COLUMNS = 9;
    static final int ROWS = 3;
    static final int EDGE = 7;
    static final int TITLE = 11;
    static final int HOTBAR_GAP = 4;
    // Minecraft's container colours
    static final int VANILLA_BACKGROUND = 0xFFC6C6C6;
    static final int VANILLA_LIGHT = 0xFFFFFFFF;
    static final int VANILLA_DARK = 0xFF555555;
    static final int VANILLA_SLOT = 0xFF8B8B8B;
    static final int VANILLA_SLOT_DARK = 0xFF373737;
    static final int VANILLA_TITLE = 0xFF404040;

    private final Supplier<GameExtras> extras;
    private final BooleanSupplier inWorld;
    private final BooleanSupplier keyDown;
    private final java.util.function.BiFunction<String, String, String> tr;
    ChoiceSetting mode;
    IntegerSetting seconds;
    ChoiceSetting style;
    BooleanSetting title;
    BooleanSetting hotbar;
    private boolean toggled;
    private int timer;
    private boolean shown;

    InventoryHud(Supplier<GameExtras> extras, BooleanSupplier inWorld, BooleanSupplier keyDown,
                 java.util.function.BiFunction<String, String, String> tr) {
        super(ID);
        this.extras = extras;
        this.inWorld = inWorld;
        this.keyDown = keyDown;
        this.tr = tr;
    }

    @Override
    public WidgetPlacement defaultPlacement() {
        return WidgetPlacement.at(0.5f, 0.62f).enabledByDefault();
    }

    @Override
    public String category() {
        return WidgetCategory.PLAYER;
    }

    @Override
    void extra(SettingsGroup s) {
        mode = s.choice("mode", "toggle", "hold", "toggle", "flash", "always");
        seconds = s.slider("seconds", 3, 1, 10);
        seconds.visibleWhen(() -> "flash".equals(mode.get()));
        style = s.choice("style", "vanilla", "vanilla", "lumora");
        title = s.toggle("title", true);
        hotbar = s.toggle("hotbar", false);
    }

    /** The key went down (client thread). */
    void pressed() {
        switch (mode.get()) {
            case "toggle" -> toggled = !toggled;
            case "flash" -> timer = seconds.get() * 20;
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        if (timer > 0) {
            timer--;
        }
        if (!inWorld.getAsBoolean()) {
            shown = false;
            return;
        }
        shown = switch (mode.get()) {
            case "hold" -> keyDown.getAsBoolean();
            case "toggle" -> toggled;
            case "flash" -> timer > 0;
            default -> true;
        };
    }

    /** Whether the inventory is shown in game right now (tests). */
    boolean shown() {
        return shown;
    }

    @Override
    public boolean isVisible(RenderContext ctx) {
        return ctx.editing() || ctx.preview() || shown;
    }

    @Override
    public int width(RenderContext ctx) {
        return EDGE * 2 + COLUMNS * SLOT;
    }

    @Override
    public int height(RenderContext ctx) {
        return top() + ROWS * SLOT + (hotbar.get() ? HOTBAR_GAP + SLOT : 0) + EDGE;
    }

    /** y of the first row. */
    private int top() {
        return title.get() ? EDGE + TITLE : EDGE;
    }

    /** x, y of the slot at (column, row); row {@link #ROWS} is the hotbar. */
    int[] slotPos(int column, int row) {
        int y = top() + row * SLOT + (row == ROWS ? HOTBAR_GAP : 0);
        return new int[]{EDGE + column * SLOT, y};
    }

    /** The {@link GameExtras#drawSlot} number of a slot: main rows 10..36, hotbar 0..8. */
    static int slotNumber(int column, int row) {
        return row == ROWS ? column : 10 + row * COLUMNS + column;
    }

    @Override
    public void render(HudGraphics g, RenderContext ctx) {
        int w = width(ctx);
        int h = height(ctx);
        boolean vanilla = "vanilla".equals(style.get());
        if (vanilla) {
            vanillaPanel(g, w, h);
        } else {
            panel(g, w, h);
        }
        if (title.get()) {
            String text = tr.apply("lumora.hud.inventory.title", "Inventar");
            if (vanilla) {
                g.text(text, EDGE + 1, 6, VANILLA_TITLE, false);
            } else {
                g.text(text, EDGE + 1, 6, labelColor.argb(), true);
            }
        }
        int rows = ROWS + (hotbar.get() ? 1 : 0);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int[] p = slotPos(column, row);
                slot(g, p[0], p[1], vanilla);
            }
        }
        if (ctx.editing() || ctx.preview() || !inWorld.getAsBoolean()) {
            return; // no player: the empty grid shows size and place
        }
        GameExtras x = extras.get();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int[] p = slotPos(column, row);
                x.drawSlot(g, slotNumber(column, row), p[0] + 1, p[1] + 1);
            }
        }
    }

    /** Minecraft's container background: light grey, white top left edge, dark bottom right edge, black outline. */
    private static void vanillaPanel(HudGraphics g, int w, int h) {
        rect(g, 1, 1, w - 2, h - 2, VANILLA_BACKGROUND);
        rect(g, 1, 0, w - 2, 1, 0xFF000000);
        rect(g, 1, h - 1, w - 2, 1, 0xFF000000);
        rect(g, 0, 1, 1, h - 2, 0xFF000000);
        rect(g, w - 1, 1, 1, h - 2, 0xFF000000);
        rect(g, 1, 1, w - 3, 2, VANILLA_LIGHT);
        rect(g, 1, 1, 2, h - 3, VANILLA_LIGHT);
        rect(g, 3, h - 3, w - 4, 2, VANILLA_DARK);
        rect(g, w - 3, 3, 2, h - 4, VANILLA_DARK);
    }

    /** A filled rectangle by position and size ({@link HudGraphics#fill} takes two corners). */
    private static void rect(HudGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    /** One 18 x 18 slot: sunken like Minecraft's, or a translucent square in the Lumora look. */
    private static void slot(HudGraphics g, int x, int y, boolean vanilla) {
        if (vanilla) {
            rect(g, x, y, SLOT, SLOT, VANILLA_SLOT);
            rect(g, x, y, SLOT - 1, 1, VANILLA_SLOT_DARK);
            rect(g, x, y, 1, SLOT - 1, VANILLA_SLOT_DARK);
            rect(g, x + 1, y + SLOT - 1, SLOT - 1, 1, VANILLA_LIGHT);
            rect(g, x + SLOT - 1, y + 1, 1, SLOT - 1, VANILLA_LIGHT);
        } else {
            rect(g, x + 1, y + 1, SLOT - 2, SLOT - 2, Colors.withAlpha(0xFF000000, 0x50));
            g.outline(x, y, SLOT, SLOT, Colors.withAlpha(0xFFFFFFFF, 0x28));
        }
    }
}
