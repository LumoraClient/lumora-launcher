package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;
import dev.lumora.core.extras.anim.BodyTurn;
import dev.lumora.core.extras.anim.HandEquip;
import dev.lumora.core.extras.anim.ItemPose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Module "Animations": two options per part, presets, switching to Custom, the swing style. */
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
    void everyPartHasDefaultAndOneLegacy() {
        AnimationsModule m = module();
        for (Feature f : Feature.values()) {
            assertEquals(List.of("default", "legacy"), m.features.get(f).options(), f.key);
            assertEquals(Style.DEFAULT, AnimationsModule.style(f), f + " starts unchanged");
        }
        assertEquals(Style.LEGACY_1_7, Feature.SWORD.legacy, "legacy is 1.7 where 1.7 has its own look");
        assertEquals(Style.LEGACY, Feature.HEAD_ROTATION.legacy, "1.7 and 1.8 are the same");
        assertEquals(Style.LEGACY_1_8, Feature.CREATIVE_LAYOUT.legacy, "1.7 had the 1.8 tabs");
        assertEquals("default", m.preset.get());
    }

    @Test
    void presetsSetEveryPartAndAHandChangeSwitchesToCustom() {
        AnimationsModule m = module();
        m.preset.set("legacy");
        for (Feature f : Feature.values()) {
            assertEquals(f.legacy, AnimationsModule.style(f));
        }
        assertEquals("legacy", m.preset.get(), "the preset's own changes keep it");

        m.features.get(Feature.BOW).set("default");
        assertEquals("custom", m.preset.get(), "a change by hand switches to Custom");
        assertEquals(Style.LEGACY_1_7, AnimationsModule.style(Feature.SWORD), "Custom keeps the other parts");

        m.preset.set("default");
        for (Feature f : Feature.values()) {
            assertEquals(Style.DEFAULT, AnimationsModule.style(f));
        }
    }

    @Test
    void oldBlockAnimationIsItsOwnSwitchInThePresets() {
        AnimationsModule m = module();
        assertFalse(AnimationsModule.oldBlocking(), "off by default");
        // blocking with a sword (use key or shield) while "Sword / Blocking" is Default: Minecraft draws it
        assertEquals(null, ItemPose.select(ItemPose.Use.NONE, true, false, true));
        m.swordBlock.set(true);
        assertEquals("custom", m.preset.get(), "a change by hand switches to Custom");
        assertTrue(AnimationsModule.oldBlocking());
        assertEquals(Style.LEGACY_1_7, ItemPose.select(ItemPose.Use.NONE, true, false, true), "the 1.7 block pose");
        assertEquals(null, ItemPose.select(ItemPose.Use.NONE, true, false, false), "not blocking: the posture stays Default");

        m.preset.set("default");
        assertFalse(AnimationsModule.oldBlocking());
        m.preset.set("legacy");
        assertTrue(AnimationsModule.oldBlocking(), "part of Legacy (everything)");
        assertEquals("legacy", m.preset.get());

        m.setActive(false);
        assertFalse(AnimationsModule.oldBlocking(), "module off");
        module = new AnimationsModule(java.util.EnumSet.of(Feature.HEAD_ROTATION));
        module.settings(new SettingsGroup("lumora-animations."));
        module.setActive(true);
        module.swordBlock.set(true);
        assertFalse(AnimationsModule.oldBlocking(), "not built into this version");
        assertFalse(module.swordBlock.isVisible());
    }

    @Test
    void headRotationAndBackwardsWalkingFollowTheOriginalFormulas() {
        AnimationsModule m = module();
        // default: Minecraft's own code runs, on a modern and on a 1.8-like version
        assertTrue(Float.isNaN(BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, true)));
        assertTrue(Float.isNaN(BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, false)));
        m.features.get(Feature.HEAD_ROTATION).set("legacy");
        // standing, head 70° to the right: 1.7/1.8 keep the head at 70° (limit 75°) and pull the body by 0.2 * 70
        float legacy = BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, true);
        assertEquals(0 * 0.7F + 70 - 70 + 70 * 0.2F, legacy, 1e-4F);
        assertTrue(Float.isNaN(BodyTurn.tick(0, 70, 0, false, 0, 0, 50, true, false)), "a version with the 1.8 head limit already");

        // walking backwards (view 0°, moving to -z = 180°): modern turned the target round to 0°, legacy uses 180°
        m.features.get(Feature.HEAD_ROTATION).set("default");
        m.features.get(Feature.BACKWARDS_WALKING).set("legacy");
        float body = BodyTurn.tick(0, 0, 0, false, 0, -0.2, 50, true, true);
        // the body turns towards the walking direction (180° * 0.3 = 54°), the modern head limit then holds it at 50°
        assertEquals(50F, Math.abs(body), 1e-3F);
        m.features.get(Feature.HEAD_ROTATION).set("legacy");
        BodyTurn.tick(0, 120, 0, false, 0, 0, 50, true, true);
        assertEquals(-3F, BodyTurn.distance(3F), "older versions negate the walked distance when the head is 90° or more off");
    }

    @Test
    void itemEquipAndBlockBuildingDecideTheDip() {
        AnimationsModule m = module();
        assertEquals(HandEquip.Mode.VANILLA, HandEquip.mode());
        assertTrue(HandEquip.blockPlaced(false), "default: Minecraft's dip");
        m.features.get(Feature.BLOCK_BUILD).set("legacy");
        assertFalse(HandEquip.blockPlaced(false), "1.7 never dips when placing");
        assertFalse(HandEquip.countReequips());
        assertFalse(HandEquip.buildDip());
        m.features.get(Feature.ITEM_EQUIP).set("legacy");
        assertEquals(HandEquip.Mode.LEGACY_1_7, HandEquip.mode());
    }

    @Test
    void versionsWithoutThePartsOnlyOfferTheSwingStyle() {
        module = new AnimationsModule(java.util.EnumSet.of(Feature.HEAD_ROTATION));
        module.settings(new SettingsGroup("lumora-animations."));
        module.setActive(true);
        module.features.get(Feature.SWORD).set("legacy");
        assertEquals(Style.DEFAULT, AnimationsModule.style(Feature.SWORD), "not built into this version yet");
        assertFalse(module.features.get(Feature.SWORD).isVisible(), "hidden in the settings");
        assertTrue(module.features.get(Feature.HEAD_ROTATION).isVisible());
        assertTrue(module.swing.isVisible());
        assertEquals("smooth", AnimationsModule.swing());
    }

    @Test
    void offMeansUnchangedAndTheOldSwingStyleIsTakenOverOnce() {
        AnimationsModule m = module();
        m.features.get(Feature.POSTURE).set("legacy");
        assertEquals(Style.LEGACY_1_7, AnimationsModule.style(Feature.POSTURE));
        assertTrue(Style.LEGACY_1_7.legacy() && Style.LEGACY.is17() && !Style.DEFAULT.legacy());

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
