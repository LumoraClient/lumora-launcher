package dev.lumora.client.diagnostics;

import dev.lumora.client.render.CosmeticsRender;
import dev.lumora.core.cosmetics.Cosmetics;
import dev.lumora.core.cosmetics.CosmeticsModule;
import dev.lumora.core.cosmetics.EmoteDefinition;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Smoke test of the module "Cosmetics & Emotes": wears a few cosmetics, plays emotes, opens the menus and checks that
 * meshes were drawn and the model was posed. Logs one line {@code LUMORA_COSMETICS}. Puts everything back afterwards.
 */
final class CosmeticsSmoke {
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");
    private static final List<String> WEAR = List.of("angel_wings", "halo", "shoulder_fox", "flame_aura", "spirit_orb");
    private static Set<String> before = Set.of();
    private static CameraType camera;
    private static int verticesAtStart;
    private static int posedAtStart;

    private CosmeticsSmoke() {
    }

    static boolean start(Minecraft mc, List<String> problems) {
        Cosmetics c = Cosmetics.get();
        if (c == null) {
            problems.add("cosmetics: module not running");
            return false;
        }
        before = c.state().equipped();
        camera = mc.options.getCameraType();
        c.setEquipped(WEAR);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        verticesAtStart = CosmeticsRender.vertices;
        posedAtStart = CosmeticsRender.posed;
        return true;
    }

    static void front(Minecraft mc) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    static void emote(String id, List<String> problems) {
        Cosmetics c = Cosmetics.get();
        EmoteDefinition e = c == null ? null : c.registry().emote(id);
        if (e == null || !c.playEmote(e)) {
            problems.add("cosmetics: emote " + id + " could not be played");
        }
    }

    static void checkDrawn(List<String> problems) {
        int vertices = CosmeticsRender.vertices - verticesAtStart;
        if (vertices <= 0) {
            problems.add("cosmetics: no cosmetic vertices drawn");
        }
    }

    static void checkPosed(List<String> problems) {
        Cosmetics c = Cosmetics.get();
        if (CosmeticsRender.posed - posedAtStart <= 0) {
            problems.add("cosmetics: emote did not pose the player model");
        }
        if (c != null && c.currentEmote() == null) {
            problems.add("cosmetics: emote ended too early");
        }
    }

    static void openMenu(List<String> problems) {
        CosmeticsModule m = CosmeticsModule.active();
        if (m == null) {
            problems.add("cosmetics: module not running");
            return;
        }
        m.openMenu();
    }

    static void openWheel(Minecraft mc) {
        mc.gui.setScreen(null);
        CosmeticsModule m = CosmeticsModule.active();
        if (m != null) {
            m.openWheel();
        }
    }

    static void openEmotes(Minecraft mc) {
        mc.gui.setScreen(null);
        CosmeticsModule m = CosmeticsModule.active();
        if (m != null) {
            m.openEmotes();
        }
    }

    static void openWheelEditor(Minecraft mc) {
        mc.gui.setScreen(null);
        CosmeticsModule m = CosmeticsModule.active();
        if (m != null) {
            m.openWheelEditor();
        }
    }

    static void openPicker(Minecraft mc) {
        mc.gui.setScreen(null);
        CosmeticsModule m = CosmeticsModule.active();
        if (m != null) {
            m.openEmotePicker(0, 0);
        }
    }

    /** A variant and an offset on two worn cosmetics (shown in the front screenshot). */
    static void options() {
        Cosmetics c = Cosmetics.get();
        if (c != null) {
            c.state().setOptionsQuiet("halo", dev.lumora.core.cosmetics.CosmeticsState.Options.DEFAULT.withVariant("pink").withOffset(0, 2, 0));
            c.state().setOptionsQuiet("angel_wings", dev.lumora.core.cosmetics.CosmeticsState.Options.DEFAULT.withVariant("gold"));
        }
    }

    static void finish(Minecraft mc, List<String> problems) {
        mc.gui.setScreen(null);
        Cosmetics c = Cosmetics.get();
        if (c != null) {
            c.stopEmote();
            c.setEquipped(before);
            c.state().setOptionsQuiet("halo", null);
            c.state().setOptionsQuiet("angel_wings", null);
            c.state().flush();
            LOG.info("LUMORA_COSMETICS cosmetics={} emotes={} frames={} vertices={} posed={}", c.registry().cosmetics().size(),
                    c.registry().emotes().size(), c.frames(), CosmeticsRender.vertices - verticesAtStart,
                    CosmeticsRender.posed - posedAtStart);
        }
        if (camera != null) {
            mc.options.setCameraType(camera);
        }
        if (c != null && c.registry().cosmetics().size() < 14) {
            problems.add("cosmetics: only " + c.registry().cosmetics().size() + " cosmetics loaded");
        }
    }
}
