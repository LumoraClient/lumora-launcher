package dev.lumora.client.hud;

import dev.lumora.core.hud.WidgetDesign;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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

    /** Shows a widget's own look settings only while "Widget-Design" is off. */
    static final java.util.function.BooleanSupplier OWN_LOOK = () -> !WidgetDesign.active();

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
            // the widget's own settings first ("Anzeigen"), then the shared look under its own heading
            int before = s.all().size();
            extra(s);
            String look = dev.lumora.core.hud.WidgetDesign.ownSettingsFirst(s, before);
            // hidden while "Widget-Design" sets the look of all widgets
            background = (BooleanSetting) s.toggle("background", true).name("lumora.widget.setting.background").section(look).visibleWhen(OWN_LOOK);
            accent = (BooleanSetting) s.toggle("accent", true).name("lumora.widget.setting.accent").visibleWhen(OWN_LOOK);
            labelColor = (ColorSetting) s.color("label_color", LumoraUi.ACCENT_HOVER, false).name("lumora.widget.setting.label_color").advanced().visibleWhen(OWN_LOOK);
            valueColor = (ColorSetting) s.color("value_color", LumoraUi.TEXT, false).name("lumora.widget.setting.value_color").advanced().visibleWhen(OWN_LOOK);
        }

        void extra(SettingsGroup s) {
        }

        void panel(HudGraphics g, int w, int h) {
            if (WidgetDesign.panel(g, w, h)) {
                return;
            }
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

        abstract List<String[]> lines(Minecraft mc, boolean preview);

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
            Minecraft mc = Minecraft.getInstance();
            List<String[]> l = lines(mc, mc.player == null);
            int w = 0;
            for (String[] line : l) {
                w = Math.max(w, WidgetDesign.lineWidth(str -> mc.font.width(str), line[0], line[1]));
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
                WidgetDesign.line(g, PAD + 2, y, line[0], line[1], labelColor.argb(), valueColor.argb(), true, false);
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
        List<String[]> lines(Minecraft mc, boolean preview) {
            return List.<String[]>of(new String[]{"FPS", Integer.toString(preview && mc.level == null ? 144 : mc.getFps())});
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
        List<String[]> lines(Minecraft mc, boolean preview) {
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
        List<String[]> lines(Minecraft mc, boolean preview) {
            LocalPlayer p = mc.player;
            List<String[]> out = new ArrayList<>(2);
            if (p == null) {
                out.add(new String[]{"XYZ", "128 / 64 / -340"});
                if (facing.get()) {
                    out.add(new String[]{t("lumora.hud.facing"), t("lumora.hud.dir.north") + " (-Z)"});
                }
                return out;
            }
            float yaw = ((p.getYRot() % 360) + 360) % 360;
            String[] dirs = {"south", "west", "north", "east"};
            String[] axis = {"+Z", "-X", "-Z", "+X"};
            int i = Math.round(yaw / 90f) % 4;
            out.add(new String[]{"XYZ", String.format(Locale.ROOT, "%d / %d / %d", p.getBlockX(), p.getBlockY(), p.getBlockZ())});
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
        List<String[]> lines(Minecraft mc, boolean preview) {
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
        List<String[]> lines(Minecraft mc, boolean preview) {
            int ping = 42;
            if (mc.player != null && mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                ping = info != null ? info.getLatency() : 0;
            }
            return List.<String[]>of(new String[]{"Ping", ping + " ms"});
        }

        @Override
        public boolean isVisible(RenderContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null || !mc.hasSingleplayerServer();
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
        List<String[]> lines(Minecraft mc, boolean preview) {
            double bps = 4.3;
            if (mc.player != null) {
                Vec3 v = mc.player.getDeltaMovement();
                bps = Math.sqrt(v.x * v.x + v.z * v.z) * 20;
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
        List<String[]> lines(Minecraft mc, boolean preview) {
            var server = mc.getCurrentServer();
            return List.<String[]>of(new String[]{"", server != null ? server.ip : (mc.hasSingleplayerServer() ? t("lumora.hud.singleplayer") : "play.example.net")});
        }
    }

    /** W A S D, mouse buttons, jump and sneak - lit while pressed, with clicks per second (Badlion-style options). */
    static final class Keystrokes extends Panel {
        /** Drawing and settings (Badlion-style options), shared by all versions. */
        final dev.lumora.core.hud.KeystrokesView view = new dev.lumora.core.hud.KeystrokesView(
                Colors.withAlpha(LumoraUi.BG, 0x9C), LumoraUi.ACCENT, LumoraUi.TEXT);
        private final String[] labels = {"W", "A", "S", "D"};
        private final dev.lumora.core.hud.KeystrokesView.Input input = new dev.lumora.core.hud.KeystrokesView.Input() {
            @Override
            public boolean down(dev.lumora.core.hud.KeystrokesView.Key key) {
                var o = Minecraft.getInstance().options;
                return switch (key) {
                    case UP -> o.keyUp.isDown();
                    case LEFT -> o.keyLeft.isDown();
                    case DOWN -> o.keyDown.isDown();
                    case RIGHT -> o.keyRight.isDown();
                    case ATTACK -> o.keyAttack.isDown();
                    case USE -> o.keyUse.isDown();
                    case JUMP -> o.keyJump.isDown();
                    case SNEAK -> o.keyShift.isDown();
                };
            }

            @Override
            public String label(dev.lumora.core.hud.KeystrokesView.Key key) {
                return switch (key) {
                    case UP -> labels[0];
                    case LEFT -> labels[1];
                    case DOWN -> labels[2];
                    case RIGHT -> labels[3];
                    default -> "";
                };
            }

            @Override
            public int cps(boolean left) {
                return left ? ClickTracker.left() : ClickTracker.right();
            }
        };

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
            view.configure(s);
        }

        @Override
        public void tick() {
            var o = Minecraft.getInstance().options;
            labels[0] = label(o.keyUp);
            labels[1] = label(o.keyLeft);
            labels[2] = label(o.keyDown);
            labels[3] = label(o.keyRight);
        }

        @Override
        public int width(RenderContext ctx) {
            return view.width();
        }

        @Override
        public int height(RenderContext ctx) {
            return view.height();
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            view.render(g, input, background.get(), WidgetDesign.valueColor(valueColor.argb()), System.currentTimeMillis());
        }

        private static String label(KeyMapping k) {
            String s = k.getTranslatedKeyMessage().getString();
            return s.length() > 3 ? s.substring(0, 1) : s.toUpperCase(Locale.ROOT);
        }

    }

    /** Helmet, chest, legs, boots and held item with remaining durability (items need the 26.1 renderer). */
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
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) {
                return;
            }
            for (EquipmentSlot s : ARMOR) {
                ItemStack st = p.getItemBySlot(s);
                if (!st.isEmpty()) {
                    items.add(st);
                }
            }
            if (held.get() && !p.getMainHandItem().isEmpty()) {
                items.add(p.getMainHandItem());
            }
        }

        @Override
        public int width(RenderContext ctx) {
            return 18 + PAD * 2 + 40;
        }

        @Override
        public int height(RenderContext ctx) {
            int n = Minecraft.getInstance().player == null ? 4 : Math.max(1, items.size());
            return n * 17 + PAD * 2;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            panel(g, width(ctx), height(ctx));
            if (items.isEmpty()) {
                g.text(Minecraft.getInstance().player == null ? t("lumora.hud.armor.preview") : "—", PAD + 4, PAD + 4, LumoraUi.MUTED, true);
                return;
            }
            GuiGraphics native2d = g.unwrap(GuiGraphics.class).orElse(null);
            int y = PAD;
            for (ItemStack st : items) {
                if (native2d != null) {
                    native2d.renderItem(st, PAD + 2, y);
                }
                if (st.isDamageableItem()) {
                    int left = st.getMaxDamage() - st.getDamageValue();
                    float f = (float) left / st.getMaxDamage();
                    String text = "percent".equals(durability.get()) ? Math.round(f * 100) + "%" : Integer.toString(left);
                    g.text(text, PAD + 22, y + 5, f > 0.5f ? LumoraUi.OK : f > 0.2f ? LumoraUi.WARN : 0xFFF2555A, true);
                } else if (st.getCount() > 1) {
                    g.text("×" + st.getCount(), PAD + 22, y + 5, WidgetDesign.valueColor(valueColor.argb()), true);
                }
                y += 17;
            }
        }
    }
}
