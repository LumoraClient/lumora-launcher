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
 * (one in the list, two in a category), the category opened with the wheel on its first entry, then closed.
 */
final class PlayerMenuSmoke {
    private static RegistrationTracker tracker;

    private PlayerMenuSmoke() {
    }

    /**
     * The player may have died in an earlier step (fall damage): respawn and close the death screen first. Chat lines
     * and toasts of the earlier steps are cleared, so the screenshots show the menu (also used for the news).
     */
    static void prepare(Minecraft mc) {
        if (mc.player != null && mc.player.isDeadOrDying()) {
            mc.player.respawn();
        }
        mc.gui.setScreen(null);
        mc.gui.hud.getChat().clearMessages(false);
        mc.gui.toastManager().clear();
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

    /** Opens the test category and steps the wheel onto its first entry. */
    static void openCategory(Minecraft mc) {
        ApiSmoke.core().interaction().openCategory("Moderation");
        var s = mc.gui.screen();
        if (s != null) {
            s.mouseScrolled(0, 0, 0, -1);
            s.mouseScrolled(0, 0, 0, -1);
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
