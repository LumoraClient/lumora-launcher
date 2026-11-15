package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffHandModuleTest {
    OffHandModule module;

    @BeforeEach
    void setUp() {
        module = new OffHandModule();
        module.settings(new SettingsGroup("test."));
        module.setActive(true);
    }

    @AfterEach
    void tearDown() {
        module.setActive(false);
    }

    @Test
    void defaultsDrawEverything() {
        for (boolean off : new boolean[]{true, false}) {
            assertFalse(OffHandModule.hide(off, false, false));
            assertFalse(OffHandModule.hide(off, true, false));
            assertFalse(OffHandModule.hide(off, false, true));
        }
        assertEquals(0, module.hiddenCount());
    }

    @Test
    void hiddenHandHidesEveryItemAndTheEmptyHand() {
        module.set("offhand_show", false);
        assertTrue(OffHandModule.hide(true, false, false));
        assertTrue(OffHandModule.hide(true, true, false));
        assertTrue(OffHandModule.hide(true, false, true));
        assertFalse(OffHandModule.hide(false, false, false), "main hand stays");
        module.set("mainhand_show", false);
        assertTrue(OffHandModule.hide(false, false, false));
        assertEquals(4, module.hiddenCount());
    }

    @Test
    void shieldAndTotemAreSeparatePerHand() {
        module.set("offhand_shield", true);
        assertTrue(OffHandModule.hide(true, true, false));
        assertFalse(OffHandModule.hide(true, false, true), "totem still drawn");
        assertFalse(OffHandModule.hide(true, false, false), "other items still drawn");
        assertFalse(OffHandModule.hide(false, true, false), "shield in the main hand still drawn");
        module.set("mainhand_totem", true);
        assertTrue(OffHandModule.hide(false, false, true));
        assertFalse(OffHandModule.hide(false, true, false));
    }

    @Test
    void itemSettingsOnlyShownWhileTheHandIsDrawn() {
        assertTrue(module.offShield.isVisible());
        assertTrue(module.offTotem.isVisible());
        module.set("offhand_show", false);
        assertFalse(module.offShield.isVisible());
        assertFalse(module.offTotem.isVisible());
        assertTrue(module.mainShield.isVisible(), "main hand settings are independent");
        module.set("mainhand_show", false);
        assertFalse(module.mainTotem.isVisible());
    }

    @Test
    void emptyOffHandDrawnWithBothHandsOnly() {
        assertTrue(module.bothHands.get(), "both hands is the default");
        assertTrue(OffHandModule.showEmptyOffHand());
        module.set("both_hands", false);
        assertFalse(OffHandModule.showEmptyOffHand());
        module.set("both_hands", true);
        module.set("offhand_show", false);
        assertFalse(OffHandModule.showEmptyOffHand(), "a hidden off hand is not drawn empty either");
        assertFalse(module.bothHands.isVisible());
        assertEquals(1, module.emptyDrawnCount());
        module.setActive(false);
        module.set("offhand_show", true);
        assertFalse(OffHandModule.showEmptyOffHand(), "module off: vanilla");
    }

    @Test
    void offModuleHidesNothing() {
        module.set("offhand_show", false);
        module.setActive(false);
        assertNull(OffHandModule.active());
        assertFalse(OffHandModule.hide(true, true, true));
    }
}
