package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.event.client.ItemTooltipEvent;
import dev.lumora.api.render.NameTags;
import dev.lumora.core.render.ItemTooltips;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora API 1.7: lines above and below name tags, item tooltip lines. */
class Api17Test {
    @TempDir
    Path dir;
    private LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    private void start(String id, String permissions, Consumer<AddonContext> enable) throws Exception {
        if (core == null) {
            core = new LumoraCore(new FakePlatform(dir));
        }
        var addon = new AddonLifecycleTest.TestAddon(enable::accept);
        var json = AddonLifecycleTest.json(id, "\"permissions\":[" + permissions + "]").replace("\"api\":\"1.0.0\"", "\"api\":\"1.7.0\"");
        var d = dev.lumora.core.addon.AddonDescriptorParser.parse(json, "test.jar");
        core.loadAddons(List.of(dev.lumora.core.addon.AddonCandidate.of(d, () -> addon)));
        assertEquals(AddonState.ENABLED, core.addonManager().record(id).orElseThrow().state(), "addon " + id + " enabled");
    }

    @Test
    void nameTagLinesComeFromTheFirstDecoratorThatHasSome() throws Exception {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        // an icon-only decorator (a lambda, as before 1.7) has no lines
        start("voice", "\"nametags\"", ctx -> ctx.nameTags().register((id, place) -> Optional.empty()));
        start("money", "\"nametags\"", ctx -> ctx.nameTags().register(new NameTags.Decorator() {
            @Override
            public Optional<NameTags.Icon> icon(UUID id, NameTags.Place place) {
                return Optional.empty();
            }

            @Override
            public Optional<NameTags.Lines> lines(UUID id) {
                return id.equals(steve) ? Optional.of(new NameTags.Lines("1.234 €", null)) : Optional.of(new NameTags.Lines("", ""));
            }
        }));
        NameTags.Lines lines = core.nameTags().lines(steve).orElseThrow();
        assertEquals("1.234 €", lines.above());
        assertEquals("", lines.below());
        assertTrue(core.nameTags().lines(alex).isEmpty(), "empty lines count as none");
        assertTrue(core.nameTags().icon(steve, NameTags.Place.WORLD).isEmpty());
    }

    @Test
    void addonsAppendTooltipLines() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        assertFalse(ItemTooltips.active());
        assertEquals(List.of(), ItemTooltips.extra("minecraft:diamond", 3));

        start("prices", "", ctx -> ctx.events().subscribe(ItemTooltipEvent.class, e -> {
            if (e.item().equals("minecraft:diamond")) {
                e.add("");
                e.add("Sell: " + 10 * e.count());
                e.add(null);
            }
        }));
        assertTrue(ItemTooltips.active());
        assertEquals(List.of("", "Sell: 30"), ItemTooltips.extra("minecraft:diamond", 3));
        assertEquals(List.of(), ItemTooltips.extra("minecraft:dirt", 1));

        core.addonManager().unload("prices");
        assertFalse(ItemTooltips.active(), "listener removed with the addon");
    }
}
