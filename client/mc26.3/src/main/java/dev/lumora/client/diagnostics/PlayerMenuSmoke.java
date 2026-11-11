package dev.lumora.client.diagnostics;

import dev.lumora.api.interaction.PlayerMenu;
import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.client.gui.AddonViewScreen;
import dev.lumora.core.RegistrationTracker;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;

/**
 * Smoke test of the player menu: opened on a player (as by the middle mouse button), with test entries of an addon
 * (one in the ring, two in a category), the mouse wheel stepping into the category's outer ring, then closed.
 */
final class PlayerMenuSmoke {
    private static RegistrationTracker tracker;

    private PlayerMenuSmoke() {
    }

    static void open() {
        tracker = new RegistrationTracker();
        PlayerMenu menu = ApiSmoke.core().playerMenu().scoped("smoke", tracker, () -> { });
        menu.add(PlayerMenuEntry.builder("wave", "Winken").icon("☺").build());
        menu.add(PlayerMenuEntry.builder("kick", "Kicken").category("Moderation", "⚒").build());
        menu.add(PlayerMenuEntry.builder("ban", "Bannen").category("Moderation", "⚒").build());
        ApiSmoke.core().interaction().open(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), "jeb_", false);
    }

    static void check(List<String> problems) {
        if (!ApiSmoke.core().interaction().isOpen() || !AddonViewScreen.isShowing()) {
            problems.add("player menu: not shown after opening");
        }
    }

    /** Turns the mouse wheel {@code steps} times (down = next entry). */
    static void wheel(Minecraft mc, int steps) {
        var s = mc.gui.screen();
        if (s == null) {
            return;
        }
        for (int i = 0; i < steps; i++) {
            s.mouseScrolled(s.width / 2.0, s.height / 2.0, 0, -1);
        }
    }

    static void close(Minecraft mc, List<String> problems) {
        mc.gui.setScreen(null);
        if (ApiSmoke.core().interaction().isOpen()) {
            problems.add("player menu: still open after the screen closed");
        }
        if (tracker != null) {
            tracker.closeAll(ApiSmoke.core().platform().logger(), "smoke");
            tracker = null;
        }
    }
}
