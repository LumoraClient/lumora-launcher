package dev.lumora.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** In-game settings (config/lumora-client.json). Invalid files are backed up and replaced by defaults. */
public final class LumoraConfig {
    private static final Logger LOG = LogManager.getLogger("Lumora");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Lumora main menu instead of the vanilla title screen. */
    public boolean customMainMenu = true;
    /** Lumora landscape instead of the vanilla panorama behind menus. */
    public boolean menuBackground = true;
    /** GLSL menu effects: "auto"/"on" (enabled) or "off". */
    public String menuShaders = "auto";
    /** Look of Lumora's menus: "lumora" or "vanilla" (Minecraft's own menus, buttons and font; the hub stays Lumora). */
    public String theme = "lumora";
    /** Accent colour of Lumora's menus (id of dev.lumora.core.theme.LumoraTheme.ACCENTS, e.g. "purple", "blue"). */
    public String accentColor = "purple";
    /** Cross-fade when switching menus: "auto"/"on" (enabled) or "off". */
    public String menuTransitions = "auto";
    /** "fade", "slide" or "off". */
    public String transitionStyle = "fade";
    /** Duration of a menu transition (80..1000 ms). */
    public int transitionMillis = 220;
    /** Also fade when a menu opens over the game or closes back into it (pause menu, inventory, chat). */
    public boolean transitionInGame = true;
    /** 1 = menus fade in and out over the game by default (older configs had it off). */
    public int transitionsVersion = 0;
    /** Lumora loading screen instead of the Mojang Studios screen. */
    public boolean customLoadingScreen = true;
    /** Lumora font (Inter) instead of the Minecraft font - a built-in resource pack. */
    public boolean lumoraFont = false;
    /** Set once the "Lumora Theme" pack was switched on at the first start with Lumora: older Fabric API versions,
     *  and instances whose options.txt predates Lumora, do not enable built-in packs by default. Switching the pack
     *  off later (Options > Resource Packs) is respected. */
    public boolean themePackApplied = false;
    /** Zoom (key C): on/off, factor 1.5-50, "hold" or "toggle", smooth animation, scroll wheel, lower sensitivity,
     *  cinematic camera and dark border while zoomed. */
    public boolean zoomEnabled = true;
    public double zoomFactor = 4.0;
    public String zoomMode = "hold";
    public boolean zoomSmooth = true;
    public boolean zoomScroll = true;
    public boolean zoomSensitivity = true;
    public boolean zoomCinematic = false;
    public boolean zoomVignette = true;
    /** First-person swing animation (cosmetic): vanilla, smooth, spin, slap. */
    public String swingAnimation = "smooth";
    public boolean titleScreenButton = true;
    /** "Lumora" button in the pause (ESC) menu that opens the Lumora hub. */
    public boolean pauseMenuButton = true;
    public boolean multiplayerProtocolLabel = true;
    public boolean writeDiagnosticsOnStart = true;
    public int benchmarkSeconds = 30;

    /** Clamps values; returns true if something was changed. */
    public boolean validate() {
        int b = Math.clamp(benchmarkSeconds, 5, 300);
        boolean changed = b != benchmarkSeconds;
        if (!"auto".equals(menuShaders) && !"on".equals(menuShaders) && !"off".equals(menuShaders)) {
            menuShaders = "auto";
            changed = true;
        }
        if (transitionsVersion < 1) {
            // every menu fades out like it fades in, also back into the game
            transitionInGame = true;
            transitionsVersion = 1;
            changed = true;
        }
        if (!"auto".equals(menuTransitions) && !"on".equals(menuTransitions) && !"off".equals(menuTransitions)) {
            menuTransitions = "auto";
            changed = true;
        }
        benchmarkSeconds = b;
        if (!"fade".equals(transitionStyle) && !"slide".equals(transitionStyle) && !"off".equals(transitionStyle)) {
            transitionStyle = "fade";
            changed = true;
        }
        if (!"hold".equals(zoomMode) && !"toggle".equals(zoomMode)) {
            zoomMode = "hold";
            changed = true;
        }
        double zf = Math.clamp(zoomFactor, 1.5, 50.0);
        if (zf != zoomFactor) {
            zoomFactor = zf;
            changed = true;
        }
        int tm = Math.clamp(transitionMillis, 80, 1000);
        if (tm != transitionMillis) {
            transitionMillis = tm;
            changed = true;
        }
        String anim = dev.lumora.client.anim.SwingAnimation.Mode.parse(swingAnimation).name().toLowerCase(java.util.Locale.ROOT);
        if (!anim.equals(swingAnimation)) {
            swingAnimation = anim;
            changed = true;
        }
        return changed;
    }

    public static LumoraConfig load(Path file) {
        LumoraConfig cfg = null;
        if (Files.isRegularFile(file)) {
            try {
                cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LumoraConfig.class);
                if (cfg == null) {
                    throw new JsonParseException("empty");
                }
            } catch (IOException | JsonParseException e) {
                Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
                LOG.warn("Invalid {} ({}), restoring defaults; backup: {}", file, e.getMessage(), backup);
                try {
                    Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) {
                    LOG.warn("Could not back up invalid config", moveError);
                }
                cfg = null;
            }
        }
        boolean save = cfg == null;
        if (cfg == null) {
            cfg = new LumoraConfig();
        }
        save |= cfg.validate();
        if (save) {
            cfg.save(file);
        }
        return cfg;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("Could not save {}", file, e);
        }
    }
}
