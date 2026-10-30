package dev.lumora.core;

import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.render.HudGraphics;

/** Shared test helpers: a render context and a simple widget. */
public final class TestContexts {
    private TestContexts() {
    }

    public static RenderContext ctx(FakePlatform p, int w, int h, boolean editing) {
        return new RenderContext() {
            @Override
            public boolean preview() {
                return editing;
            }

            @Override
            public boolean editing() {
                return editing;
            }

            @Override
            public float partialTick() {
                return 0;
            }

            @Override
            public int screenWidth() {
                return w;
            }

            @Override
            public int screenHeight() {
                return h;
            }

            @Override
            public int guiScale() {
                return 2;
            }

            @Override
            public long timeMillis() {
                return 0;
            }

            @Override
            public GameContext game() {
                return p.game();
            }

            @Override
            public Translations translations() {
                return p.translations();
            }
        };
    }

    /** 40x10 widget that counts lifecycle calls. */
    public static class Box extends HudWidget {
        public int enabled;
        public int disabled;
        public int ticks;
        public int renders;
        public int disposed;
        public int w = 40;
        public int h = 10;
        public WidgetPlacement placement = WidgetPlacement.at(0f, 0f).enabledByDefault();
        public boolean throwInRender;

        public Box(String id) {
            super(id);
        }

        @Override
        public WidgetPlacement defaultPlacement() {
            return placement;
        }

        @Override
        public void onEnable() {
            enabled++;
        }

        @Override
        public void onDisable() {
            disabled++;
        }

        @Override
        public void tick() {
            ticks++;
        }

        @Override
        public void dispose() {
            disposed++;
        }

        @Override
        public int width(RenderContext context) {
            return w;
        }

        @Override
        public int height(RenderContext context) {
            return h;
        }

        @Override
        public void render(HudGraphics graphics, RenderContext context) {
            renders++;
            if (throwInRender) {
                throw new IllegalStateException("boom");
            }
            graphics.text("box", 0, 0, -1);
        }
    }
}
