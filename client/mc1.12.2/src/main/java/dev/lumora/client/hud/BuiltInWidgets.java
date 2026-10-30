package dev.lumora.client.hud;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import dev.lumora.client.compat.Font;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.ClientPlayerEntity;
import dev.lumora.client.compat.Component;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The built-in Lumora HUD widgets (FPS, CPS, coordinates, keystrokes, ping, clock, speed, armor, server) on the
 * Lumora widget API. Text widgets collect their lines in {@link HudWidget#tick()} (20x per second) and only draw
 * the cached lines per frame. Purely client-side displays of information the player already has.
 */
public final class BuiltInWidgets {
    private static final int PAD = 4;
    private static final int LINE = 10;

    private BuiltInWidgets() {
    }

    public static List<HudWidget> create() {
        return List.of(new Fps(), new Cps(), new Coords(), new Keystrokes(), new Ping(), new Clock(), new Speed(), new Armor(), new Server());
    }

    static String t(String key) {
        return Component.translatable(key).getString();
    }

    /** Settings shared by all panel widgets; labels use common translation keys. */
    abstract static class Panel extends HudWidget {
        BooleanSetting background;
        BooleanSetting accent;
        ColorSetting labelColor;
        ColorSetting valueColor;

        Panel(String id) {
            super(id);
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
        public void configure(SettingsGroup s) {
            background = (BooleanSetting) s.toggle("background", true).name("lumora.widget.setting.background");
            accent = (BooleanSetting) s.toggle("accent", true).name("lumora.widget.setting.accent");
            labelColor = (ColorSetting) s.color("label_color", LumoraUi.ACCENT_HOVER, false).name("lumora.widget.setting.label_color").advanced();
            valueColor = (ColorSetting) s.color("value_color", LumoraUi.TEXT, false).name("lumora.widget.setting.value_color").advanced();
            extra(s);
        }

        void extra(SettingsGroup s) {
        }

        void panel(HudGraphics g, int w, int h) {
            if (background.get()) {
                g.fill(0, 0, w, h, Colors.withAlpha(LumoraUi.BG, 0x9C));
                g.fill(0, 0, w, 1, Colors.withAlpha(0xFFFFFFFF, 0x10));
            }
            if (accent.get()) {
                g.fill(0, 0, 2, h, LumoraUi.ACCENT);
            }
        }
    }

    /** Panel with label/value lines. */
    abstract static class Text extends Panel {
        private List<String[]> lines;
        private int width = 40;

        Text(String id) {
            super(id);
        }

        abstract List<String[]> lines(MinecraftClient mc, boolean preview);

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
            MinecraftClient mc = MinecraftClient.getInstance();
            List<String[]> l = lines(mc, mc.player == null);
            int w = 0;
            for (String[] line : l) {
                w = Math.max(w, Font.get().width(line[0]) + (line[0].isEmpty() ? 0 : 4) + Font.get().width(line[1]));
            }
            lines = l;
            width = w + PAD * 2 + 2;
        }

        @Override
        public int width(RenderContext ctx) {
            return width;
        }

        @Override
        public int height(RenderContext ctx) {
            return lines.size() * LINE + PAD * 2 - 1;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            List<String[]> l = lines;
            panel(g, width, l.size() * LINE + PAD * 2 - 1);
            int y = PAD;
            for (String[] line : l) {
                int x = PAD + 2;
                if (!line[0].isEmpty()) {
                    g.text(line[0], x, y, labelColor.argb(), true);
                    x += g.textWidth(line[0]) + 4;
                }
                g.text(line[1], x, y, valueColor.argb(), true);
                y += LINE;
            }
        }
    }

    static final class Fps extends Text {
        Fps() {
            super("fps");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.01f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.GENERAL;
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            return List.<String[]>of(new String[]{"FPS", Integer.toString(preview && mc.world == null ? 144 : MinecraftClient.getCurrentFps())});
        }
    }

    static final class Cps extends Text {
        Cps() {
            super("cps");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.075f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            return List.<String[]>of(new String[]{"CPS", ClickTracker.left() + " | " + ClickTracker.right()});
        }
    }

    static final class Coords extends Text {
        BooleanSetting facing;

        Coords() {
            super("coords");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.14f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        void extra(SettingsGroup s) {
            facing = s.toggle("facing", true);
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            ClientPlayerEntity p = mc.player;
            List<String[]> out = new ArrayList<>(2);
            if (p == null) {
                out.add(new String[]{"XYZ", "128 / 64 / -340"});
                if (facing.get()) {
                    out.add(new String[]{t("lumora.hud.facing"), t("lumora.hud.dir.north") + " (-Z)"});
                }
                return out;
            }
            float yaw = ((p.yaw % 360) + 360) % 360;
            String[] dirs = {"south", "west", "north", "east"};
            String[] axis = {"+Z", "-X", "-Z", "+X"};
            int i = Math.round(yaw / 90f) % 4;
            out.add(new String[]{"XYZ", String.format(Locale.ROOT, "%d / %d / %d", (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z))});
            if (facing.get()) {
                out.add(new String[]{t("lumora.hud.facing"), t("lumora.hud.dir." + dirs[i]) + " (" + axis[i] + ")"});
            }
            return out;
        }
    }

    static final class Clock extends Text {
        private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss");
        private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        private static final DateTimeFormatter H12S = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);
        ChoiceSetting format;
        BooleanSetting seconds;

        Clock() {
            super("clock");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.01f);
        }

        @Override
        public String category() {
            return WidgetCategory.TIME;
        }

        @Override
        void extra(SettingsGroup s) {
            format = s.choice("format", "24h", "24h", "12h");
            seconds = s.toggle("seconds", false);
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            boolean twelve = "12h".equals(format.get());
            DateTimeFormatter f = seconds.get() ? (twelve ? H12S : H24S) : (twelve ? H12 : H24);
            return List.<String[]>of(new String[]{"", LocalTime.now().format(f)});
        }
    }

    static final class Ping extends Text {
        Ping() {
            super("ping");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.075f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.SERVER;
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            int ping = 42;
            if (mc.player != null && mc.getNetworkHandler() != null) {
                PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                ping = info != null ? info.getLatency() : 0;
            }
            return List.<String[]>of(new String[]{"Ping", ping + " ms"});
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc.player == null || !mc.isInSingleplayer();
        }
    }

    static final class Speed extends Text {
        ChoiceSetting unit;

        Speed() {
            super("speed");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.005f, 0.22f);
        }

        @Override
        public String category() {
            return WidgetCategory.PLAYER;
        }

        @Override
        void extra(SettingsGroup s) {
            unit = s.choice("unit", "bps", "bps", "kmh");
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            double bps = 4.3;
            if (mc.player != null) {
                bps = Math.sqrt(mc.player.velocityX * mc.player.velocityX + mc.player.velocityZ * mc.player.velocityZ) * 20;
            }
            String value = "kmh".equals(unit.get())
                    ? String.format(Locale.GERMANY, "%.1f km/h", bps * 3.6)
                    : String.format(Locale.GERMANY, "%.1f B/s", bps);
            return List.<String[]>of(new String[]{t("lumora.hud.speed.label"), value});
        }
    }

    static final class Server extends Text {
        Server() {
            super("server");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.14f);
        }

        @Override
        public String category() {
            return WidgetCategory.SERVER;
        }

        @Override
        List<String[]> lines(MinecraftClient mc, boolean preview) {
            var server = mc.getCurrentServerEntry();
            return List.<String[]>of(new String[]{"", server != null ? server.address : (mc.isInSingleplayer() ? t("lumora.hud.singleplayer") : "play.example.net")});
        }
    }

    /** W A S D, mouse buttons and jump - lit while pressed. */
    static final class Keystrokes extends Panel {
        static final int K = 18;
        static final int G = 2;
        BooleanSetting mouse;
        BooleanSetting space;
        ColorSetting pressed;
        private final String[] labels = {"W", "A", "S", "D"};

        Keystrokes() {
            super("keystrokes");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.86f).enabledByDefault();
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        void extra(SettingsGroup s) {
            mouse = s.toggle("mouse", true);
            space = s.toggle("space", true);
            pressed = (ColorSetting) s.color("pressed", LumoraUi.ACCENT, false).advanced();
        }

        @Override
        public void tick() {
            var o = MinecraftClient.getInstance().options;
            labels[0] = label(o.forwardKey);
            labels[1] = label(o.leftKey);
            labels[2] = label(o.backKey);
            labels[3] = label(o.rightKey);
        }

        @Override
        public int width(RenderContext ctx) {
            return K * 3 + G * 2;
        }

        @Override
        public int height(RenderContext ctx) {
            return K * 2 + G + (mouse.get() ? K + G : 0) + (space.get() ? 9 + G : 0);
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            var o = MinecraftClient.getInstance().options;
            key(g, K + G, 0, K, K, labels[0], o.forwardKey.isPressed());
            key(g, 0, K + G, K, K, labels[1], o.leftKey.isPressed());
            key(g, K + G, K + G, K, K, labels[2], o.backKey.isPressed());
            key(g, (K + G) * 2, K + G, K, K, labels[3], o.rightKey.isPressed());
            int y = (K + G) * 2;
            if (mouse.get()) {
                int half = (K * 3 + G * 2 - G) / 2;
                key(g, 0, y, half, K, "LMB", o.attackKey.isPressed());
                key(g, half + G, y, half, K, "RMB", o.useKey.isPressed());
                y += K + G;
            }
            if (space.get()) {
                key(g, 0, y, K * 3 + G * 2, 9, "———", o.jumpKey.isPressed());
            }
        }

        private static String label(KeyBinding k) {
            String s = GameOptions.getFormattedNameForKeyCode(k.getCode());
            return s.length() > 3 ? s.substring(0, 1) : s.toUpperCase(Locale.ROOT);
        }

        private void key(HudGraphics g, int x, int y, int w, int h, String text, boolean down) {
            g.fill(x, y, x + w, y + h, down ? Colors.withAlpha(pressed.argb(), 0xD0) : Colors.withAlpha(LumoraUi.BG, background.get() ? 0x9C : 0x50));
            int tw = g.textWidth(text);
            g.text(text, x + (w - tw) / 2, y + (h - 8) / 2, down ? 0xFF0E0A16 : valueColor.argb(), !down);
        }
    }

    /** Helmet, chest, legs, boots and held item with remaining durability (items need the vanilla item renderer). */
    static final class Armor extends Panel {
        static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        BooleanSetting held;
        ChoiceSetting durability;
        private final List<ItemStack> items = new ArrayList<>();

        Armor() {
            super("armor");
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return WidgetPlacement.at(0.995f, 0.6f);
        }

        @Override
        public String category() {
            return WidgetCategory.COMBAT;
        }

        @Override
        void extra(SettingsGroup s) {
            held = s.toggle("held", true);
            durability = s.choice("durability", "value", "value", "percent");
        }

        @Override
        public void tick() {
            items.clear();
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) {
                return;
            }
            for (EquipmentSlot s : ARMOR) {
                ItemStack st = p.getStack(s);
                if (!st.isEmpty()) {
                    items.add(st);
                }
            }
            if (held.get() && !p.getStack(EquipmentSlot.MAINHAND).isEmpty()) {
                items.add(p.getStack(EquipmentSlot.MAINHAND));
            }
        }

        @Override
        public int width(RenderContext ctx) {
            return 18 + PAD * 2 + 40;
        }

        @Override
        public int height(RenderContext ctx) {
            int n = MinecraftClient.getInstance().player == null ? 4 : Math.max(1, items.size());
            return n * 17 + PAD * 2;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            panel(g, width(ctx), height(ctx));
            if (items.isEmpty()) {
                g.text(MinecraftClient.getInstance().player == null ? t("lumora.hud.armor.preview") : "—", PAD + 4, PAD + 4, LumoraUi.MUTED, true);
                return;
            }
            GuiGraphics native2d = g.unwrap(GuiGraphics.class).orElse(null);
            int y = PAD;
            for (ItemStack st : items) {
                if (native2d != null) {
                    native2d.renderItem(st, PAD + 2, y);
                }
                if (st.isDamageable()) {
                    int left = st.getMaxDamage() - st.getDamage();
                    float f = (float) left / st.getMaxDamage();
                    String text = "percent".equals(durability.get()) ? Math.round(f * 100) + "%" : Integer.toString(left);
                    g.text(text, PAD + 22, y + 5, f > 0.5f ? LumoraUi.OK : f > 0.2f ? LumoraUi.WARN : 0xFFF2555A, true);
                } else if (st.getCount() > 1) {
                    g.text("×" + st.getCount(), PAD + 22, y + 5, valueColor.argb(), true);
                }
                y += 17;
            }
        }
    }
}
