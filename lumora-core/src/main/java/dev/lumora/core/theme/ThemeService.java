package dev.lumora.core.theme;

import dev.lumora.api.Registration;
import dev.lumora.api.ui.Theme;
import dev.lumora.core.RegistrationTracker;

import java.util.ArrayList;
import java.util.List;

/** The player's theme for addons ({@code AddonContext.theme()}); addons' accent colours go into {@link LumoraTheme}. */
public final class ThemeService {
    private final boolean german;

    public ThemeService(boolean german) {
        this.german = german;
    }

    /** {@code designGuard} checks the permission {@code design} (only for adding accents). */
    public Theme scoped(String addon, RegistrationTracker tracker, Runnable activeGuard, Runnable designGuard) {
        return new Theme() {
            @Override
            public String style() {
                return ThemeLayers.themeActive() ? LumoraTheme.LUMORA : LumoraTheme.VANILLA;
            }

            @Override
            public String accentId() {
                return LumoraTheme.current().id();
            }

            @Override
            public int accent() {
                return LumoraTheme.accent();
            }

            @Override
            public int accentHover() {
                return LumoraTheme.accentHover();
            }

            @Override
            public int accentDark() {
                return LumoraTheme.accentDark();
            }

            @Override
            public dev.lumora.api.ui.WidgetStyle widgetStyle() {
                return dev.lumora.core.hud.WidgetStyleView.INSTANCE;
            }

            @Override
            public List<Accent> accents() {
                List<Accent> l = new ArrayList<>();
                for (LumoraTheme.Accent a : LumoraTheme.ACCENTS) {
                    l.add(new Accent(a.id(), german ? a.german() : a.english(), a.color()));
                }
                return l;
            }

            @Override
            public Registration addAccent(String id, String name, int color) {
                activeGuard.run();
                designGuard.run();
                if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
                    throw new IllegalArgumentException("id: " + id);
                }
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("name");
                }
                String full = addon + ":" + id;
                String n = name.strip().length() > 24 ? name.strip().substring(0, 24) : name.strip();
                LumoraTheme.addAccent(new LumoraTheme.Accent(full, n, n, 0xFF000000 | color));
                return tracker.track(RegistrationTracker.of(() -> LumoraTheme.removeAccent(full)));
            }
        };
    }
}
