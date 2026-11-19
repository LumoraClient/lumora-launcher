package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Module "Animations": options per part, presets, switching to Custom, the swing style. */
class AnimationsModuleTest {
    private AnimationsModule module;

    private AnimationsModule module() {
        module = new AnimationsModule();
        module.settings(new SettingsGroup("lumora-animations."));
        module.setActive(true);
        return module;
    }

    @AfterEach
    void off() {
        if (module != null) {
            module.setActive(false);
        }
    }

    @Test
    void everyPartOffersOnlyTheStylesTheOriginalCodeHas() {
        AnimationsModule m = module();
        assertEquals(List.of("default", "legacy_1_7", "legacy_1_8", "modern"), m.features.get(Feature.SNEAK).options());
        assertEquals(List.of("default", "legacy_1_7", "modern"), m.features.get(Feature.HEART).options(), "1.8 hearts blink like today");
        assertEquals(List.of("default", "legacy", "modern"), m.features.get(Feature.HEAD_ROTATION).options(), "1.7 and 1.8 are the same");
        assertEquals(List.of("default", "legacy_1_8", "modern"), m.features.get(Feature.CREATIVE_LAYOUT).options());
        for (Feature f : Feature.values()) {
            assertEquals(Style.DEFAULT, AnimationsModule.style(f), f + " starts unchanged");
        }
        assertEquals("custom", m.preset.get());
    }

    @Test
    void presetsSetEveryPartAndAHandChangeSwitchesToCustom() {
        AnimationsModule m = module();
        m.preset.set("classic_1_7");
        assertEquals(Style.LEGACY_1_7, AnimationsModule.style(Feature.SWORD));
        assertEquals(Style.LEGACY_1_7, AnimationsModule.style(Feature.HEART));
        assertEquals(Style.LEGACY, AnimationsModule.style(Feature.BACKWARDS_WALKING), "the shared legacy option");
        assertEquals(Style.MODERN, AnimationsModule.style(Feature.CREATIVE_LAYOUT), "no 1.7 tabs: modern");
        assertEquals("classic_1_7", m.preset.get(), "the preset's own changes keep it");

        m.preset.set("classic_1_8");
        assertEquals(Style.LEGACY_1_8, AnimationsModule.style(Feature.SWORD));
        assertEquals(Style.MODERN, AnimationsModule.style(Feature.HEART), "1.8 hearts are the modern ones");
        assertEquals(Style.LEGACY_1_8, AnimationsModule.style(Feature.CREATIVE_LAYOUT));

        m.features.get(Feature.BOW).set("modern");
        assertEquals("custom", m.preset.get(), "a change by hand switches to Custom");
        assertEquals(Style.LEGACY_1_8, AnimationsModule.style(Feature.SWORD), "Custom keeps the other parts");

        m.preset.set("modern");
        for (Feature f : Feature.values()) {
            assertEquals(Style.MODERN, AnimationsModule.style(f));
        }
    }

    @Test
    void headRotationAndBackwardsWalkingFollowTheOriginalFormulas() {
        AnimationsModule m = module();
        // default on a modern version: Minecraft's own code runs
        assertTrue(Float.isNaN(dev.lumora.core.extras.anim.BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, true)));
        m.features.get(Feature.HEAD_ROTATION).set("legacy");
        // standing, head 70° to the right: 1.7/1.8 keep the head at 70° (limit 75°) and pull the body by 0.2 * 70
        float legacy = dev.lumora.core.extras.anim.BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, true);
        assertEquals(0 * 0.7F + 70 - 70 + 70 * 0.2F, legacy, 1e-4F);
        m.features.get(Feature.HEAD_ROTATION).set("modern");
        assertTrue(Float.isNaN(dev.lumora.core.extras.anim.BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, true)), "modern = this version");
        // modern head limit on a 1.8-like version: at most 50°
        assertEquals(20F, dev.lumora.core.extras.anim.BodyTurn.tick(0, 70, 0, false, 0, 0, 50, false, false), 1e-4F);

        // walking backwards (view 0°, moving to -z = 180°): modern turned the target round to 0°, legacy uses 180°
        m.features.get(Feature.HEAD_ROTATION).set("default");
        m.features.get(Feature.BACKWARDS_WALKING).set("legacy");
        float body = dev.lumora.core.extras.anim.BodyTurn.tick(0, 0, 0, false, 0, -0.2, 50, true, true);
        // the body turns towards the walking direction (180° * 0.3 = 54°), the modern head limit then holds it at 50°
        assertEquals(50F, Math.abs(body), 1e-3F);
        m.features.get(Feature.BACKWARDS_WALKING).set("modern");
        assertTrue(Float.isNaN(dev.lumora.core.extras.anim.BodyTurn.tick(0, 0, 0, false, 0, -0.2, 50, true, true)));
    }

    @Test
    void itemEquipAndBlockBuildingDecideTheDip() {
        AnimationsModule m = module();
        assertEquals(dev.lumora.core.extras.anim.HandEquip.Mode.VANILLA, dev.lumora.core.extras.anim.HandEquip.mode());
        assertTrue(dev.lumora.core.extras.anim.HandEquip.blockPlaced(false), "default: Minecraft's dip");
        m.features.get(Feature.BLOCK_BUILD).set("legacy_1_7");
        assertFalse(dev.lumora.core.extras.anim.HandEquip.blockPlaced(false), "1.7 never dips when placing");
        assertFalse(dev.lumora.core.extras.anim.HandEquip.countReequips());
        m.features.get(Feature.BLOCK_BUILD).set("legacy_1_8");
        assertFalse(dev.lumora.core.extras.anim.HandEquip.blockPlaced(true), "1.8 creative: the stack stays, no dip");
        assertFalse(dev.lumora.core.extras.anim.HandEquip.buildDip());
        assertFalse(dev.lumora.core.extras.anim.HandEquip.blockPlaced(false));
        assertTrue(dev.lumora.core.extras.anim.HandEquip.buildDip(), "1.8 survival: the stack got smaller, a 1.8 dip");
        assertEquals(dev.lumora.core.extras.anim.HandEquip.Mode.MODERN_WITH_DIP, dev.lumora.core.extras.anim.HandEquip.mode());
        dev.lumora.core.extras.anim.HandEquip.handDown();
        assertEquals(dev.lumora.core.extras.anim.HandEquip.Mode.VANILLA, dev.lumora.core.extras.anim.HandEquip.mode());
        m.features.get(Feature.ITEM_EQUIP).set("legacy_1_8");
        assertFalse(dev.lumora.core.extras.anim.HandEquip.blockPlaced(false));
        assertFalse(dev.lumora.core.extras.anim.HandEquip.buildDip(), "1.8 item equip compares the stack itself");
        assertEquals(dev.lumora.core.extras.anim.HandEquip.Mode.LEGACY_1_8, dev.lumora.core.extras.anim.HandEquip.mode());
    }

    @Test
    void versionsWithoutThePartsOnlyOfferTheSwingStyle() {
        module = new AnimationsModule("1.21.11");
        module.settings(new SettingsGroup("lumora-animations."));
        module.setActive(true);
        module.features.get(Feature.SWORD).set("legacy_1_8");
        assertEquals(Style.DEFAULT, AnimationsModule.style(Feature.SWORD), "not built into 1.21.11 yet");
        assertFalse(module.features.get(Feature.SWORD).isVisible(), "hidden in the settings");
        assertTrue(module.swing.isVisible());
        assertEquals("smooth", AnimationsModule.swing());
    }

    @Test
    void offMeansUnchangedAndTheOldSwingStyleIsTakenOverOnce() {
        AnimationsModule m = module();
        m.features.get(Feature.POSTURE).set("legacy_1_8");
        assertEquals(Style.LEGACY_1_8, AnimationsModule.style(Feature.POSTURE));
        assertTrue(Style.LEGACY_1_8.legacy() && Style.LEGACY.is17() && Style.LEGACY.is18() && !Style.MODERN.legacy());

        assertEquals("smooth", AnimationsModule.swing(), "same default as the Themes page had");
        AnimationsModule.migrateSwing("Spin");
        assertEquals("spin", AnimationsModule.swing());
        AnimationsModule.migrateSwing("slap");
        assertEquals("spin", AnimationsModule.swing(), "only the first time");

        m.setActive(false);
        assertEquals(Style.DEFAULT, AnimationsModule.style(Feature.POSTURE));
        assertEquals("vanilla", AnimationsModule.swing());
        assertFalse(AnimationsModule.removeRecipeBook());
    }
}
