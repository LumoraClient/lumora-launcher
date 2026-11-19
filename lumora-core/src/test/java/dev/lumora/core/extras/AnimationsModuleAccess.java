package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;

/** Test access to the package-private parts of {@link AnimationsModule} from other test packages. */
public final class AnimationsModuleAccess {
    private AnimationsModuleAccess() {
    }

    public static void settings(AnimationsModule m, SettingsGroup s) {
        m.settings(s);
    }

    public static void setActive(AnimationsModule m, boolean on) {
        m.setActive(on);
    }

    /** Sets a part ({@code Feature.key}) to a style key. */
    public static void set(AnimationsModule m, String feature, String style) {
        for (AnimationsModule.Feature f : AnimationsModule.Feature.values()) {
            if (f.key.equals(feature)) {
                m.features.get(f).set(style);
                return;
            }
        }
        throw new IllegalArgumentException(feature);
    }
}
