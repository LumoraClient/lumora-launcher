package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonPermissionException;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.building.BlockHit;
import dev.lumora.api.building.BlockInfo;
import dev.lumora.api.building.Building;
import dev.lumora.api.building.ItemSpec;
import dev.lumora.api.building.UseHandler;
import dev.lumora.core.building.BuildingPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora API 1.10: building tools (block under the crosshair, break, place, pick, creative slots, click hooks). */
class Api110Test {
    @TempDir
    Path dir;
    private LumoraCore core;
    private final List<String> actions = new ArrayList<>();
    private boolean creative = true;

    static final BlockInfo CHEST = new BlockInfo("minecraft:chest", Map.of("facing", "north"),
            Map.of("facing", List.of("north", "south", "west", "east")), true, true);
    static final BlockHit HIT = new BlockHit(1, 64, 2, "up", 1.5, 65, 2.5, CHEST);

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    /** A creative world with a chest under the crosshair; actions are written to {@link #actions}. */
    private final class World implements BuildingPlatform {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean creative() {
            return creative;
        }

        @Override
        public Optional<BlockHit> target() {
            return Optional.of(HIT);
        }

        @Override
        public boolean breakBlock(BlockHit hit) {
            actions.add("break " + hit.x() + "," + hit.y() + "," + hit.z());
            return creative;
        }

        @Override
        public boolean place(BlockHit hit) {
            actions.add("place " + hit.x() + "," + hit.y() + "," + hit.z());
            return creative;
        }

        @Override
        public boolean setCreativeSlot(int slot, ItemSpec item) {
            actions.add("slot " + slot + " " + item.id());
            return creative;
        }
    }

    private AddonContext start(String id, String permissions) throws Exception {
        FakePlatform platform = new FakePlatform(dir);
        platform.building = new World();
        core = new LumoraCore(platform);
        AddonContext[] ctx = new AddonContext[1];
        var addon = new AddonLifecycleTest.TestAddon(c -> ctx[0] = c);
        var json = AddonLifecycleTest.json(id, "\"permissions\":[" + permissions + "]").replace("\"api\":\"1.0.0\"", "\"api\":\"1.10.0\"");
        var d = dev.lumora.core.addon.AddonDescriptorParser.parse(json, "test.jar");
        core.loadAddons(List.of(dev.lumora.core.addon.AddonCandidate.of(d, () -> addon)));
        assertEquals(AddonState.ENABLED, core.addonManager().record(id).orElseThrow().state());
        return ctx[0];
    }

    @Test
    void theBuildingToolsNeedTheirPermission() throws Exception {
        AddonContext ctx = start("plain", "\"hud\"");
        assertThrows(AddonPermissionException.class, ctx::building);
        assertTrue(dev.lumora.api.Version.parse(dev.lumora.api.LumoraAPI.API_VERSION).compareTo(dev.lumora.api.Version.parse("1.10.0")) >= 0);
    }

    @Test
    void readsAndActsThroughTheVersionAdapter() throws Exception {
        Building b = start("tools", "\"building\"").building();
        assertTrue(b.available());
        assertEquals("minecraft:chest[facing=north]", b.target().orElseThrow().block().state());
        assertTrue(b.breakBlock(HIT));
        assertTrue(b.place(HIT.at(1, 65, 2, BlockInfo.AIR)));
        assertTrue(b.setCreativeSlot(3, ItemSpec.ofBlock("minecraft:stone")));
        assertFalse(b.setCreativeSlot(9, ItemSpec.ofBlock("minecraft:stone")), "only hotbar slots 0..8");
        assertEquals(List.of("break 1,64,2", "place 1,65,2", "slot 3 minecraft:stone"), actions);
    }

    @Test
    void theFirstHandlerThatDecidesWinsAndAFailingOneIsSkipped() throws Exception {
        Building b = start("tools", "\"building\"").building();
        var api = core.building();
        assertEquals(UseHandler.Decision.NORMAL, api.decideUse(HIT, ItemSpec.EMPTY), "no handler: Minecraft decides");
        b.onUse((hit, held) -> {
            throw new IllegalStateException("broken addon");
        });
        b.onUse((hit, held) -> UseHandler.Decision.NORMAL);
        b.onUse((hit, held) -> hit.block().interactive() && held.block() ? UseHandler.Decision.AS_SNEAKING : UseHandler.Decision.NORMAL);
        assertEquals(UseHandler.Decision.AS_SNEAKING, api.decideUse(HIT, ItemSpec.ofBlock("minecraft:stone")));
        assertEquals(UseHandler.Decision.NORMAL, api.decideUse(HIT, ItemSpec.EMPTY));
        assertFalse(api.handlePick(HIT));
        b.onPick(hit -> true);
        assertTrue(api.handlePick(HIT));
    }

    @Test
    void delaysAndHandlersGoAwayWithTheAddon() throws Exception {
        Building b = start("tools", "\"building\"").building();
        var api = core.building();
        assertEquals(-1, api.useDelay(), "Minecraft's own delay");
        b.setUseDelay(0);
        b.setBreakDelay(2);
        b.onUse((hit, held) -> UseHandler.Decision.CANCEL);
        assertEquals(0, api.useDelay());
        assertEquals(2, api.breakDelay());
        assertEquals(UseHandler.Decision.CANCEL, api.decideUse(HIT, ItemSpec.EMPTY));

        core.addonManager().setWanted("tools", false);
        assertEquals(-1, api.useDelay());
        assertEquals(-1, api.breakDelay());
        assertFalse(api.hasUseHandlers());
        assertEquals(UseHandler.Decision.NORMAL, api.decideUse(HIT, ItemSpec.EMPTY));
    }

    @Test
    void highlightsGoAwayWithTheirRegistrationAndTheAddon() throws Exception {
        Building b = start("tools", "\"building\"").building();
        var api = core.building();
        var start = b.highlight(1, 64, 2, 0xFF00FF00);
        b.highlight(5, 64, 9, 0xFFFF0000);
        assertEquals(2, api.highlights().size());
        start.unregister();
        assertEquals(1, api.highlights().size());
        assertEquals(5, api.highlights().get(0)[0]);
        core.addonManager().setWanted("tools", false);
        assertTrue(api.highlights().isEmpty());
        assertTrue(new FakePlatform(dir).building().blockIds().isEmpty(), "versions without building tools: nothing");
    }

    /** API 1.12: NoClip belongs to the addon, the server can switch it off, disabling the addon removes it. */
    @Test
    void noClipIsPerAddonAndGoesOffWhenTheServerRefuses() throws Exception {
        Building b = start("tools", "\"building\"").building();
        var api = core.building();
        assertFalse(api.wantsNoClip());
        b.setNoClip(true);
        assertTrue(b.noClip());
        assertTrue(api.wantsNoClip());
        api.rejectNoClip();
        assertFalse(b.noClip(), "the server put the player back");
        assertFalse(api.wantsNoClip());
        b.setNoClip(true);
        core.addonManager().setWanted("tools", false);
        assertFalse(api.wantsNoClip(), "gone with the addon");
        assertEquals(dev.lumora.api.building.BlockTraits.NONE, new FakePlatform(dir).building().blockTraits("minecraft:stone"));
        assertEquals(0, new FakePlatform(dir).building().itemColor("minecraft:stick"));
    }

    @Test
    void itemsAndBlockStatesAreValues() {
        ItemSpec stairs = new ItemSpec("minecraft:oak_stairs", 1, Map.of("facing", "east"), true, "Oak Stairs");
        assertTrue(stairs.sameAs(new ItemSpec("minecraft:oak_stairs", 64, Map.of("facing", "east"), true, "")));
        assertFalse(stairs.sameAs(ItemSpec.ofBlock("minecraft:oak_stairs")), "another block state");
        assertTrue(ItemSpec.EMPTY.isEmpty());
        assertTrue(BlockInfo.AIR.isAir());
        assertEquals("minecraft:stone", new BlockInfo("minecraft:stone", Map.of(), Map.of(), false, false).state());
        BlockHit above = HIT.at(1, 65, 2, BlockInfo.AIR);
        assertEquals(66.0, above.hitY(), 1e-9, "the hit point moves with the block");
    }
}
