package dev.lumora.client.gui;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.input.Keybind;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.hud.HudEditorScreen;
import dev.lumora.core.hub.SettingsView;

import java.util.List;

/**
 * Lumora settings on Minecraft 26.1.2: the view itself is {@link SettingsView} in the core (the hub's design,
 * same on every version), built only from the setting descriptions, so every widget and addon gets it for free.
 * This class shows it in a screen of this version.
 */
public final class SettingsScreen {
    private SettingsScreen() {
    }

    /**
     * @param group    settings to show (may be {@code null} if there are only keybinds)
     * @param keybinds Lumora keybinds shown below the settings
     * @param onClose  run when leaving (e.g. save), may be {@code null}
     */
    public static net.minecraft.client.gui.screens.Screen create(net.minecraft.client.gui.screens.Screen parent, net.minecraft.network.chat.Component title, SettingsGroup group, List<Keybind> keybinds, Runnable onClose) {
        return create(parent, title.getString(), "", group, keybinds, onClose, false);
    }

    /** Settings with a description line; {@code hudEditor} adds the button that opens the HUD editor. */
    public static net.minecraft.client.gui.screens.Screen create(net.minecraft.client.gui.screens.Screen parent, String title, String description, SettingsGroup group, List<Keybind> keybinds,
                                Runnable onClose, boolean hudEditor) {
        SettingsView view = new SettingsView(LumoraRuntime.core(), new Host(), title, description, group, keybinds, onClose, hudEditor);
        return AddonViewScreen.create(parent, view);
    }

    private static final class Host implements SettingsView.Host {
        private static net.minecraft.client.Minecraft mc() {
            return net.minecraft.client.Minecraft.getInstance();
        }

        @Override
        public void close(SettingsView view) {
            AddonViewScreen.close(view);
        }

        @Override
        public boolean inWorld() {
            return mc().level != null;
        }

        @Override
        public void openHudEditor() {
            AddonViewScreen.keepCurrent();
            mc().setScreen(new HudEditorScreen(mc().screen));
        }
    }
}
