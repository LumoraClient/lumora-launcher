package dev.lumora.core.extras;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.hud.WidgetDesign;
import dev.lumora.core.perf.PerformanceEngine;
import dev.lumora.core.perf.TextWidths;

import java.util.List;

/**
 * Look of the Lumora HUD panels (same as the built-in widgets of the version modules): translucent night background,
 * accent bar on the left, label in the accent colour, value in the text colour.
 */
final class Panels {
    static final int PAD = 4;
    static final int LINE = 10;

    /** Shows a widget's own look settings only while "Widget-Design" is off. */
    static final java.util.function.BooleanSupplier OWN_LOOK = () -> !WidgetDesign.active();

    private Panels() {
    }

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
            background = s.toggle("background", true).name("lumora.widget.setting.background").section(look).visibleWhen(OWN_LOOK);
            accent = s.toggle("accent", true).name("lumora.widget.setting.accent").visibleWhen(OWN_LOOK);
            labelColor = s.color("label_color", Colors.ACCENT_HOVER, false).name("lumora.widget.setting.label_color").advanced().visibleWhen(OWN_LOOK);
            valueColor = s.color("value_color", Colors.TEXT, false).name("lumora.widget.setting.value_color").advanced().visibleWhen(OWN_LOOK);
        }

        void extra(SettingsGroup s) {
        }

        void panel(HudGraphics g, int w, int h) {
            if (WidgetDesign.panel(g, w, h)) {
                return;
            }
            boolean batch = dev.lumora.core.perf.FillBatch.begin(g);
            try {
                if (background.get()) {
                    g.fill(0, 0, w, h, Colors.withAlpha(Colors.BACKGROUND, 0x9C));
                    g.fill(0, 0, w, 1, Colors.withAlpha(0xFFFFFFFF, 0x10));
                }
                if (accent.get()) {
                    g.fill(0, 0, 2, h, Colors.ACCENT);
                }
            } finally {
                dev.lumora.core.perf.FillBatch.end(g, batch);
            }
        }
    }

    /**
     * Panel with label/value lines, collected in {@link #tick()} (20x per second). Line colours: a third array entry
     * "#AARRGGBB" overrides the value colour of that line.
     */
    abstract static class Text extends Panel {
        private List<String[]> lines = List.of();
        private int width = 60;
        // Smart HUD: what the width and the line colours were computed for (lines change 20x per second, frames more often)
        private List<String[]> measured;
        private WidgetDesign.Look measuredLook;
        private int measuredEpoch;
        private int[] fixedColors = new int[0];

        Text(String id) {
            super(id);
        }

        /** @param preview no world (menu, HUD editor in the main menu): sample values */
        abstract List<String[]> lines(boolean preview);

        boolean inWorld() {
            return true;
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
            List<String[]> l = lines(!inWorld());
            lines = l == null ? List.of() : l;
        }

        @Override
        public int width(RenderContext ctx) {
            return width;
        }

        @Override
        public int height(RenderContext ctx) {
            return Math.max(1, lines.size()) * LINE + PAD * 2 - 1;
        }

        @Override
        public void render(HudGraphics g, RenderContext ctx) {
            List<String[]> l = lines;
            WidgetDesign.Look look = WidgetDesign.look();
            int epoch = TextWidths.epoch();
            boolean smart = PerformanceEngine.smartHud;
            if (!smart || l != measured || look != measuredLook || epoch != measuredEpoch) {
                measure(g, l);
                measured = smart ? l : null;
                measuredLook = look;
                measuredEpoch = epoch;
            }
            panel(g, width, height(ctx));
            int y = PAD;
            for (int i = 0; i < l.size(); i++) {
                String[] line = l.get(i);
                boolean fixed = line.length > 2 && line[2] != null;
                int color = fixed ? fixedColors[i] : valueColor.argb();
                WidgetDesign.line(g, PAD + 2, y, line[0], line[1], labelColor.argb(), color, true, fixed);
                y += LINE;
            }
        }

        /** Width of the panel and the fixed line colours for these lines. */
        private void measure(HudGraphics g, List<String[]> l) {
            int w = 0;
            int[] colors = fixedColors.length == l.size() ? fixedColors : new int[l.size()];
            for (int i = 0; i < l.size(); i++) {
                String[] line = l.get(i);
                w = Math.max(w, WidgetDesign.lineWidth(g::textWidth, line[0], line[1]));
                colors[i] = line.length > 2 && line[2] != null ? (int) Long.parseLong(line[2].substring(1), 16) : 0;
            }
            fixedColors = colors;
            width = Math.max(20, w + PAD * 2 + 2);
        }
    }

    /** "#AARRGGBB" for {@link Text} line colours. */
    static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
