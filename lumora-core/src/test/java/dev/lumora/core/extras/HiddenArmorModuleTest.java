package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenArmorModuleTest {
    static final long S = 1_000_000_000L;
    HiddenArmorModule module;

    @BeforeEach
    void setUp() {
        module = new HiddenArmorModule();
        module.settings(new SettingsGroup("test."));
        module.setActive(true);
    }

    @AfterEach
    void tearDown() {
        module.setActive(false);
    }

    @Test
    void offModuleAndNonArmorSlotsDrawAsUsual() {
        module.setActive(false);
        assertEquals(1f, HiddenArmorModule.alpha(HiddenArmorModule.HEAD, true, 1, false));
        module.setActive(true);
        assertEquals(1f, module.opacityFor(-1, true, 1, false, 0));
        assertEquals(1f, module.opacityFor(4, true, 1, false, 0));
    }

    @Test
    void defaultsHideTheOwnArmorOnly() {
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(0f, module.opacityFor(slot, true, 1, false, 0));
            assertEquals(1f, module.opacityFor(slot, false, 2, false, 0), "others keep their armor (only me)");
        }
        module.setAppliesTo("everyone");
        assertEquals(0f, module.opacityFor(HiddenArmorModule.CHEST, false, 2, false, 0));
    }

    @Test
    void eachPieceHasItsOwnOpacity() {
        module.setOpacity(HiddenArmorModule.HEAD, 37);
        module.setOpacity(HiddenArmorModule.CHEST, 100);
        module.setOpacity(HiddenArmorModule.LEGS, 0);
        module.setOpacity(HiddenArmorModule.FEET, 40);
        assertEquals(0.37f, module.opacityFor(HiddenArmorModule.HEAD, true, 1, false, 0), 1e-6);
        assertEquals(1f, module.opacityFor(HiddenArmorModule.CHEST, true, 1, false, 0));
        assertEquals(0f, module.opacityFor(HiddenArmorModule.LEGS, true, 1, false, 0));
        assertEquals(0.4f, module.opacityFor(HiddenArmorModule.FEET, true, 1, false, 0), 1e-6);
    }

    @Test
    void aHitShowsTheArmorThenItFadesBack() {
        module.setOpacity(HiddenArmorModule.CHEST, 20);
        int chest = HiddenArmorModule.CHEST;
        assertEquals(0.2f, module.opacityFor(chest, true, 1, false, 0), 1e-6);
        // hit at t = 10 s: fully visible for 3 s (default), then 1 s fade back to 20 %
        assertEquals(1f, module.opacityFor(chest, true, 1, true, 10 * S));
        assertEquals(1f, module.opacityFor(chest, true, 1, false, 12 * S));
        assertEquals(0.6f, module.opacityFor(chest, true, 1, false, 13 * S + S / 2), 1e-3);
        assertEquals(0.2f, module.opacityFor(chest, true, 1, false, 14 * S + 1), 1e-6);
        // another player's hit does not show this player's armor
        module.setAppliesTo("everyone");
        module.opacityFor(chest, false, 2, true, 20 * S);
        assertEquals(0.2f, module.opacityFor(chest, true, 1, false, 20 * S + 1), 1e-6);
        assertEquals(1f, module.opacityFor(chest, false, 2, false, 21 * S));
    }

    @Test
    void withoutFadeTheArmorVanishesAtOnceAndWithoutShowOnHitHitsDoNothing() {
        module.fade.set(false);
        int head = HiddenArmorModule.HEAD;
        module.opacityFor(head, true, 1, true, 0);
        assertEquals(1f, module.opacityFor(head, true, 1, false, 3 * S - 1));
        assertEquals(0f, module.opacityFor(head, true, 1, false, 3 * S));
        module.showOnHit.set(false);
        assertEquals(0f, module.opacityFor(head, true, 1, true, 10 * S));
    }

    @Test
    void sameForAllMovesTheOtherSliders() {
        module.setOpacity(HiddenArmorModule.HEAD, 10);
        module.setOpacity(HiddenArmorModule.FEET, 70);
        module.sameForAll.set(true);
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(10, module.opacity[slot].get(), "switching it on takes the helmet's value");
        }
        module.setOpacity(HiddenArmorModule.LEGS, 55);
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(55, module.opacity[slot].get());
        }
        module.sameForAll.set(false);
        module.setOpacity(HiddenArmorModule.HEAD, 0);
        assertEquals(55, module.opacity[HiddenArmorModule.CHEST].get());
    }

    @Test
    void piecesDrawnOtherThanUsualAreCounted() {
        HiddenArmorModule.alpha(HiddenArmorModule.HEAD, true, 1, false);
        HiddenArmorModule.alpha(HiddenArmorModule.HEAD, false, 2, false);
        assertTrue(module.changedCount() == 1);
    }
}
