package dev.lumora.core.render;

import dev.lumora.api.Registration;
import dev.lumora.api.render.NameTags;
import dev.lumora.core.CoreLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameTagRegistryTest {
    private static final CoreLogger QUIET = new CoreLogger() {
        @Override public void info(String message) { }
        @Override public void warn(String message) { }
        @Override public void warn(String message, Throwable error) { }
        @Override public void error(String message, Throwable error) { }
    };

    @Test
    void leadingIconComesBeforeTheNameAndDoesNotReplaceAddonIcons() {
        NameTagRegistry r = new NameTagRegistry(QUIET);
        UUID max = UUID.randomUUID();
        var badge = new NameTags.Icon("lumora:badge", "", 0xFFFFFF, "*", "");
        var speaker = new NameTags.Icon("voice:nametags", "", 0xFFFFFF, "~", "");
        assertFalse(r.active());

        Registration lead = r.registerLeading((id, place) -> id.equals(max) ? Optional.of(badge) : Optional.empty());
        assertTrue(r.active(), "a leading decorator alone makes the mixins draw");
        assertEquals(badge, r.leading(max, NameTags.Place.WORLD).orElseThrow());
        assertTrue(r.icon(max, NameTags.Place.WORLD).isEmpty(), "not an icon after the name");

        r.scoped("voice", new dev.lumora.core.RegistrationTracker(), () -> { })
                .register((id, place) -> Optional.of(speaker));
        assertEquals(speaker, r.icon(max, NameTags.Place.TAB_LIST).orElseThrow(), "addon icon after the name");
        assertEquals(badge, r.leading(max, NameTags.Place.TAB_LIST).orElseThrow(), "badge before the name at the same time");
        assertTrue(r.leading(UUID.randomUUID(), NameTags.Place.WORLD).isEmpty());

        lead.unregister();
        assertTrue(r.leading(max, NameTags.Place.WORLD).isEmpty());
        assertTrue(r.active(), "the addon decorator is still there");
    }

    @Test
    void brokenLeadingDecoratorIsSkipped() {
        NameTagRegistry r = new NameTagRegistry(QUIET);
        int[] calls = {0};
        r.registerLeading((id, place) -> {
            calls[0]++;
            throw new IllegalStateException("boom");
        });
        UUID p = UUID.randomUUID();
        assertTrue(r.leading(p, NameTags.Place.WORLD).isEmpty());
        assertTrue(r.leading(p, NameTags.Place.WORLD).isEmpty());
        assertEquals(1, calls[0], "asked once, then skipped");
    }
}
