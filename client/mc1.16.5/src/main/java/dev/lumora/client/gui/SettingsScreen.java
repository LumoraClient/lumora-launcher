package dev.lumora.client.gui;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.KeybindSetting;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingType;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.config.TextSetting;
import dev.lumora.api.input.Keybind;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.Colors;
import dev.lumora.client.api.LumoraRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.compat.Tooltips;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Generic Lumora settings screen: one row per setting of a {@link SettingsGroup} (switch, number with +/-,
 * slider, text field, choice, color, key) plus Lumora keybinds. Built only from the setting descriptions, so every
 * widget and addon gets it for free. Advanced settings are behind "Erweitert"; values are validated by the
 * settings themselves and saved by their owner.
 */
public final class SettingsScreen extends Screen {
    /** Minecraft 1.16.5 has no widget Tooltip class, see {@link Tooltips}. */
    private final Tooltips tooltips = new Tooltips();
    private static final int[] PALETTE = {0xFF8B5CF6, 0xFF3DD68C, 0xFFF5B342, 0xFFF2555A, 0xFFB57BFF, 0xFFECE8F4, 0xFF0F0C17};
    private static boolean advanced;
    private final Screen parent;
    private final SettingsGroup group;
    private final List<Keybind> keybinds;
    private final Runnable onClose;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private Component status = TextComponent.EMPTY;
    /** Setter of the key row waiting for a key press, or {@code null}. */
    private IntConsumer capture;

    private record Row(Component label, int y, int swatch) {
    }

    /**
     * @param group    settings to show (may be {@code null} if there are only keybinds)
     * @param keybinds Lumora keybinds shown below the settings
     * @param onClose  run when leaving (e.g. save), may be {@code null}
     */
    public SettingsScreen(Screen parent, Component title, SettingsGroup group, List<Keybind> keybinds, Runnable onClose) {
        super(title);
        this.parent = parent;
        this.group = group;
        this.keybinds = keybinds;
        this.onClose = onClose;
    }

    private int panelWidth() {
        return Math.min(400, width - 24);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int perPage() {
        return Math.max(3, (height - 36 - 44 - 20) / 24);
    }

    private List<Object> visibleItems() {
        List<Object> items = new ArrayList<>();
        if (group != null) {
            for (Setting<?> s : group.all()) {
                if (advanced || !s.isAdvanced()) {
                    items.add(s);
                }
            }
        }
        items.addAll(keybinds);
        return items;
    }

    private boolean hasAdvanced() {
        if (group == null) {
            return false;
        }
        for (Setting<?> s : group.all()) {
            if (s.isAdvanced()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void init() {
        tooltips.clear();
        rows.clear();
        capture = null;
        List<Object> items = visibleItems();
        int pages = Math.max(1, (items.size() + perPage() - 1) / perPage());
        page = Math.min(page, pages - 1);
        int cw = Math.min(160, panelWidth() / 2 - 8);
        int cx = left() + panelWidth() - 12 - cw;
        int y = 44;
        for (int i = page * perPage(); i < Math.min(items.size(), (page + 1) * perPage()); i++) {
            Object item = items.get(i);
            if (item instanceof Setting<?> s) {
                int swatch = control(s, cx, y, cw);
                rows.add(new Row(new TranslatableComponent(s.nameKey()), y, swatch));
                if (net.minecraft.client.resources.language.I18n.exists(s.descriptionKey())) {
                    tooltip(new TranslatableComponent(s.descriptionKey()));
                }
            } else if (item instanceof Keybind k) {
                keyButton(cx, y, cw, k.key(), key -> {
                    if (!k.setKey(key)) {
                        status = new TranslatableComponent("lumora.settings.key.taken", k.conflictWith(key).orElse("?"));
                    } else {
                        status = TextComponent.EMPTY;
                    }
                });
                rows.add(new Row(new TranslatableComponent(k.nameKey()), y, 0));
            }
            y += 24;
        }
        int by = height - 30;
        int x = left() + 12;
        if (pages > 1) {
            LumoraButton prev = new LumoraButton(x, by, 20, 20, new TextComponent("‹"), LumoraButton.Style.SECONDARY, null, () -> {
                page--;
                init(minecraft, width, height);
            });
            prev.active = page > 0;
            addButton(prev);
            LumoraButton next = new LumoraButton(x + 24, by, 20, 20, new TextComponent("›"), LumoraButton.Style.SECONDARY, null, () -> {
                page++;
                init(minecraft, width, height);
            });
            next.active = page < pages - 1;
            addButton(next);
            x += 50;
        }
        if (hasAdvanced()) {
            addButton(new LumoraButton(x, by, 96, 20, new TranslatableComponent(advanced ? "lumora.settings.advanced.hide" : "lumora.settings.advanced.show"),
                    LumoraButton.Style.SECONDARY, null, () -> {
                        advanced = !advanced;
                        init(minecraft, width, height);
                    }));
        }
        int right = left() + panelWidth() - 12;
        addButton(new LumoraButton(right - 96, by, 96, 20, new TranslatableComponent("gui.done"), LumoraButton.Style.PRIMARY, null, this::onClose));
        addButton(new LumoraButton(right - 96 - 6 - 96, by, 96, 20, new TranslatableComponent("lumora.settings.defaults"), LumoraButton.Style.SECONDARY, null, () -> {
            if (group != null) {
                group.resetAll();
            }
            for (Keybind k : keybinds) {
                k.setKey(k.defaultKey());
            }
            init(minecraft, width, height);
        }));
    }

    private void tooltip(Component text) {
        var children = children();
        if (!children.isEmpty() && children.get(children.size() - 1) instanceof net.minecraft.client.gui.components.AbstractWidget w) {
            tooltips.set(w, text);
        }
    }

    /** Adds the control for a setting; returns a color for the swatch (0 = none). */
    private int control(Setting<?> s, int x, int y, int w) {
        SettingType type = s.type();
        if (s instanceof BooleanSetting b) {
            addButton(new LumoraButton(x, y, w, 20, new TranslatableComponent(b.get() ? "options.on" : "options.off"),
                    b.get() ? LumoraButton.Style.PRIMARY : LumoraButton.Style.SECONDARY, null, () -> {
                        b.toggle();
                        init(minecraft, width, height);
                    }));
        } else if (s instanceof ChoiceSetting c) {
            String key = c.optionKey(c.get());
            Component label = net.minecraft.client.resources.language.I18n.exists(key) ? new TranslatableComponent(key) : new TextComponent(c.get());
            addButton(new LumoraButton(x, y, w, 20, label, LumoraButton.Style.SECONDARY, "⇄", () -> {
                c.cycle(1);
                init(minecraft, width, height);
            }));
        } else if (type == SettingType.SLIDER) {
            addButton(new Slider(x, y, w, s));
        } else if (s instanceof IntegerSetting i) {
            stepper(x, y, w, Integer.toString(i.get()), () -> i.set(i.get() - 1), () -> i.set(i.get() + 1));
        } else if (s instanceof DecimalSetting d) {
            double step = d.step() > 0 ? d.step() : (d.max() - d.min()) / 20;
            stepper(x, y, w, format(d.get()), () -> d.set(d.get() - step), () -> d.set(d.get() + step));
        } else if (s instanceof TextSetting t) {
            EditBox box = new EditBox(font, x, y, w, 20, new TranslatableComponent(s.nameKey()));
            box.setMaxLength(t.maxLength());
            box.setValue(t.get());
            box.setResponder(t::set);
            addButton(box);
        } else if (s instanceof ColorSetting c) {
            EditBox box = new EditBox(font, x + 22, y, w - 22 - 24, 20, new TranslatableComponent(s.nameKey()));
            box.setMaxLength(9);
            box.setValue(c.hasAlpha() ? Colors.toHex(c.get()) : String.format("#%06X", c.get() & 0xFFFFFF));
            box.setResponder(v -> {
                Integer parsed = Colors.parseHex(v);
                if (parsed != null) {
                    c.set(parsed);
                }
            });
            addButton(box);
            addButton(new LumoraButton(x + w - 20, y, 20, 20, new TextComponent("◐"), LumoraButton.Style.SECONDARY, null, () -> {
                int next = PALETTE[0];
                for (int i = 0; i < PALETTE.length; i++) {
                    if ((PALETTE[i] & 0xFFFFFF) == (c.get() & 0xFFFFFF)) {
                        next = PALETTE[(i + 1) % PALETTE.length];
                        break;
                    }
                }
                c.set(c.hasAlpha() ? (c.get() & 0xFF000000) | (next & 0xFFFFFF) : next);
                init(minecraft, width, height);
            }));
            return c.get();
        } else if (s instanceof KeybindSetting k) {
            keyButton(x, y, w, k.get(), k::set);
        }
        return 0;
    }

    private void stepper(int x, int y, int w, String value, Runnable minus, Runnable plus) {
        addButton(new LumoraButton(x, y, 20, 20, new TextComponent("−"), LumoraButton.Style.SECONDARY, null, () -> {
            minus.run();
            init(minecraft, width, height);
        }));
        LumoraButton v = new LumoraButton(x + 24, y, w - 48, 20, new TextComponent(value), LumoraButton.Style.SECONDARY, null, () -> { });
        v.active = false;
        addButton(v);
        addButton(new LumoraButton(x + w - 20, y, 20, 20, new TextComponent("+"), LumoraButton.Style.SECONDARY, null, () -> {
            plus.run();
            init(minecraft, width, height);
        }));
    }

    private void keyButton(int x, int y, int w, int key, IntConsumer setter) {
        String name = LumoraRuntime.core().keybindManager().keyName(key);
        LumoraButton[] self = new LumoraButton[1];
        self[0] = new LumoraButton(x, y, w, 20, new TextComponent(name), LumoraButton.Style.SECONDARY, "⌨", () -> {
            capture = k -> {
                setter.accept(k);
                init(minecraft, width, height);
            };
            self[0].setMessage(new TranslatableComponent("lumora.settings.key.press"));
        });
        addButton(self[0]);
    }

    private static String format(double v) {
        return String.format(Locale.GERMANY, v == Math.rint(v) ? "%.0f" : "%.2f", v);
    }

    /** Slider over the setting's range; the setting clamps and rounds the value. */
    private static final class Slider extends AbstractSliderButton {
        private final Setting<?> setting;

        Slider(int x, int y, int w, Setting<?> setting) {
            super(x, y, w, 20, TextComponent.EMPTY, fraction(setting));
            this.setting = setting;
            updateMessage();
        }

        private static double fraction(Setting<?> s) {
            if (s instanceof IntegerSetting i) {
                return i.max() == i.min() ? 0 : (i.get() - i.min()) / (double) (i.max() - i.min());
            }
            DecimalSetting d = (DecimalSetting) s;
            return d.max() == d.min() ? 0 : (d.get() - d.min()) / (d.max() - d.min());
        }

        @Override
        protected void updateMessage() {
            String text = setting instanceof IntegerSetting i ? Integer.toString(i.get()) : format(((DecimalSetting) setting).get());
            setMessage(new TextComponent(text));
        }

        @Override
        protected void applyValue() {
            if (setting instanceof IntegerSetting i) {
                i.set((int) Math.round(i.min() + value * (i.max() - i.min())));
            } else {
                DecimalSetting d = (DecimalSetting) setting;
                d.set(d.min() + value * (d.max() - d.min()));
            }
        }
    }

    // --- drawing and input

    /** Minecraft 1.16.5: Screen#render does not draw the background yet (1.20.2+ does), so the screen draws it first. */
    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics g = GuiGraphics.of(pose);
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g.pose(), mouseX, mouseY, delta);
        tooltips.render(this, pose, mouseX, mouseY);
    }

    /** Background of this screen; Minecraft 1.16.5 has no Screen#renderBackground(g, x, y, delta) yet, see render. */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
        }
        g.fill(0, 0, width, height, LumoraUi.alpha(LumoraUi.BG, minecraft.level == null ? 0x80 : 0xB8));
        int x = left();
        int pw = panelWidth();
        LumoraShaders.glass(g, x, 8, pw, height - 16, 0xEE15111D);
        LumoraUi.bigText(g, title, x + 12, 16, 1.25f, LumoraUi.TEXT);
        for (Row r : rows) {
            g.drawString(font, r.label(), x + 12, r.y() + 6, LumoraUi.TEXT, false);
            if (r.swatch() != 0) {
                int cx = x + pw - 12 - Math.min(160, pw / 2 - 8);
                LumoraUi.card(g, cx, r.y(), 20, 20, r.swatch() | 0xFF000000, LumoraUi.BORDER);
            }
        }
        if (rows.isEmpty()) {
            g.drawString(font, new TranslatableComponent("lumora.settings.none"), x + 12, 48, LumoraUi.MUTED, false);
        }
        if (!status.getString().isEmpty()) {
            g.drawString(font, status, x + 12, height - 44, 0xFFF5B342, false);
        }
    }

    boolean key(int key) {
        if (capture == null) {
            return false;
        }
        IntConsumer c = capture;
        capture = null;
        if (key == Keys.ESCAPE) {
            init(minecraft, width, height);
        } else if (key == Keys.BACKSPACE || key == Keys.DELETE) {
            c.accept(Keys.UNBOUND);
        } else {
            c.accept(key);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (onClose != null) {
            onClose.run();
        }
        minecraft.setScreen(parent);
    }
}
