package dev.lumora.client.diagnostics;

import dev.lumora.core.extras.light.LightOverlayModule;

import java.util.List;

/**
 * Smoke step "light-overlay" (module "Light Block Overlay"): the module is switched on, finds spawn positions around
 * the test player within its time budget and draws them (screenshot with crosses, then with numbers).
 */
final class LightOverlaySmoke {
    private LightOverlaySmoke() {
    }

    static void start() {
        ApiSmoke.core().addonManager().setWanted(LightOverlayModule.ID, true);
        mode("spawn"); // spawn positions first (the light level mode is the default)
        style("cross"); // a previous run may have left the numbers on
    }

    /** "spawn" or "light" (light level of every block). */
    static void mode(String value) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m != null) {
            m.setMode(value);
        }
    }

    static void check(List<String> problems) {
        LightOverlayModule m = LightOverlayModule.active();
        String stats = m == null ? "inactive" : m.stats();
        dev.lumora.client.LumoraClient.LOG.info("LUMORA_LIGHT {}", stats);
        if (m == null) {
            problems.add("light overlay: not active");
        } else if (m.meshes().length == 0) {
            problems.add("light overlay: no spawn positions around the player (" + stats + ")");
        }
    }

    static void style(String value) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m != null) {
            m.setStyle(value);
        }
    }

    /** Style of the light level mode: "number", "tile" or "marker". */
    static void lightStyle(String value) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m != null) {
            m.setLightStyle(value);
        }
    }

    static void stop() {
        LightOverlayModule m = LightOverlayModule.active();
        if (m != null) {
            dev.lumora.client.LumoraClient.LOG.info("LUMORA_LIGHT number {}", m.stats());
        }
        ApiSmoke.core().addonManager().setWanted(LightOverlayModule.ID, false);
    }
}
