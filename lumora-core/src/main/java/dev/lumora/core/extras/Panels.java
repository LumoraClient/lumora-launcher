package dev.lumora.core.extras;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;

import java.util.List;

/**
 * Look of the Lumora HUD panels (same as the built-in widgets of the version modules): translucent night background,
 * accent bar on the left, label in the accent colour, value in the text colour.
 */
final class Panels {
    static final int PAD = 4;
    static final int LINE = 10;

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
            background = s.toggle("background", true).name("lumora.widget.setting.background");
            accent = s.toggle("accent", true).name("lumora.widget.setting.accent");
            labelColor = s.color("label_color", Colors.ACCENT_HOVER, false).name("lumora.widget.setting.label_color").advanced();
            valueColor = s.color("value_color", Colors.TEXT, false).name("lumora.widget.setting.value_color").advanced();
            extra(s);
        }

        void extra(SettingsGroup s) {
        }

        void panel(HudGraphics g, int w, int h) {
            if (background.get()) {
                g.fill(0, 0, w, h, Colors.withAlpha(Colors.BACKGROUND, 0x9C));
                g.fill(0, 0, w, 1, Colors.withAlpha(0xFFFFFFFF, 0x10));
            }
            if (accent.get()) {
                g.fill(0, 0, 2, h, Colors.ACCENT);
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
            int w = 0;
            for (String[] line : l) {
                w = Math.max(w, g.textWidth(line[0]) + (line[0].isEmpty() ? 0 : 4) + g.textWidth(line[1]));
            }
            width = Math.max(20, w + PAD * 2 + 2);
            panel(g, width, height(ctx));
            int y = PAD;
            for (String[] line : l) {
                int x = PAD + 2;
                if (!line[0].isEmpty()) {
                    g.text(line[0], x, y, labelColor.argb(), true);
                    x += g.textWidth(line[0]) + 4;
                }
                int color = line.length > 2 && line[2] != null ? (int) Long.parseLong(line[2].substring(1), 16) : valueColor.argb();
                g.text(line[1], x, y, color, true);
                y += LINE;
            }
        }
    }

    /** "#AARRGGBB" for {@link Text} line colours. */
    static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
