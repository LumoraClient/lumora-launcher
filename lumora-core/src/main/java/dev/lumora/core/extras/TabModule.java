package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;

/**
 * Built-in module "Tab-Liste & Scoreboard": ping in milliseconds instead of the signal bars in the tab list, and the
 * sidebar scoreboard moved, scaled, without the red numbers (1.20.3+) or hidden. The version adapters read the
 * settings in Minecraft's tab list and sidebar methods.
 */
public final class TabModule implements LumoraAddon {
    private static volatile TabModule active;

    BooleanSetting pingNumbers;
    BooleanSetting scoreboard;
    IntegerSetting scale;
    IntegerSetting offsetX;
    IntegerSetting offsetY;
    BooleanSetting hideNumbers;

    public static TabModule active() {
        return active;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        String k = "lumora.extras.tab.";
        pingNumbers = s.toggle("ping_numbers", true).name(k + "ping_numbers").section(k + "section.tab");
        scoreboard = s.toggle("scoreboard", true).name(k + "scoreboard").section(k + "section.scoreboard");
        hideNumbers = s.checkbox("hide_numbers", false).name(k + "hide_numbers").requires(scoreboard);
        scale = s.slider("scoreboard_scale", 100, 50, 150).step(5).unit("%").name(k + "scoreboard_scale").requires(scoreboard);
        offsetX = s.slider("scoreboard_x", 0, -400, 0).unit(" px").name(k + "scoreboard_x").requires(scoreboard);
        offsetY = s.slider("scoreboard_y", 0, -200, 200).unit(" px").name(k + "scoreboard_y").requires(scoreboard);
        s.button("scoreboard_reset", () -> {
            scale.reset();
            offsetX.reset();
            offsetY.reset();
        }).name(k + "scoreboard_reset").requires(scoreboard);
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    public boolean pingNumbers() {
        return pingNumbers.get();
    }

    /** False: the sidebar is not drawn at all. */
    public boolean showScoreboard() {
        return scoreboard.get();
    }

    public boolean hideNumbers() {
        return hideNumbers.get();
    }

    /** Scoreboard transform: scale around the right edge / vertical centre, then the offset (GUI pixels). */
    public float scoreboardScale() {
        return scale.get() / 100f;
    }

    public int scoreboardX() {
        return offsetX.get();
    }

    public int scoreboardY() {
        return offsetY.get();
    }

    /** True if the sidebar is drawn exactly as vanilla (the adapter can skip the transform). */
    public boolean scoreboardUnchanged() {
        return scale.get() == 100 && offsetX.get() == 0 && offsetY.get() == 0;
    }

    /** Ping text right-aligned at {@code right} (the slot of vanilla's 10 px signal icon). */
    public static void drawPing(HudGraphics g, int latency, int right, int y) {
        String text = latency < 0 ? "?" : Integer.toString(latency);
        int w = g.textWidth(text);
        g.text(text, right - w, y, pingColor(latency), true);
    }

    /** Green under 80 ms, yellow under 150, orange under 300, red above (and grey while unknown). */
    public static int pingColor(int latency) {
        if (latency < 0) {
            return Colors.MUTED;
        }
        if (latency < 80) {
            return Colors.OK;
        }
        if (latency < 150) {
            return 0xFFD7E05A;
        }
        if (latency < 300) {
            return Colors.WARNING;
        }
        return Colors.ERROR;
    }
}
