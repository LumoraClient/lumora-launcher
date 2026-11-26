package dev.lumora.core.social;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.NameTags;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LumoraBadgeModuleTest {
    @Test
    void ownBadgeAndBothPlacesAreOnByDefault() {
        LumoraBadgeModule m = new LumoraBadgeModule(() -> null, () -> null);
        m.settings(new SettingsGroup("test."));
        assertTrue(m.own.get(), "players see their own badge without looking for the setting");
        assertTrue(m.aboveHead.get());
        assertTrue(m.tabList.get());
        assertTrue(m.share.get());
        // nobody known yet (not connected): no badge for a stranger
        assertTrue(m.icon(UUID.randomUUID(), NameTags.Place.TAB_LIST).isEmpty());
    }
}
