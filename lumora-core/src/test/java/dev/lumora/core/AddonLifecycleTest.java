package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.AddonPermissionException;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.event.addon.AddonDisabledEvent;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.input.Keys;
import dev.lumora.core.addon.AddonCandidate;
import dev.lumora.core.addon.AddonDescriptorParser;
import dev.lumora.core.addon.AddonDescriptorParser.InvalidDescriptorException;
import dev.lumora.core.addon.AddonRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonLifecycleTest {
    @TempDir
    Path dir;
    private LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    static String json(String id, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"version\":\"1.0.0\",\"api\":\"1.0.0\",\"entrypoint\":\"test.Addon\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    /** Addon whose onEnable is a lambda; counts calls. */
    static final class TestAddon implements LumoraAddon {
        final Consumer<AddonContext> enable;
        int inits;
        int enables;
        int disables;
        boolean failDisable;

        TestAddon(Consumer<AddonContext> enable) {
            this.enable = enable;
        }

        @Override
        public void onInitialize(AddonContext context) {
            inits++;
            if (context.configuration().settings().get("flag").isEmpty()) {
                context.configuration().settings().toggle("flag", false);
            }
        }

        @Override
        public void onEnable(AddonContext context) {
            enables++;
            enable.accept(context);
        }

        @Override
        public void onDisable(AddonContext context) {
            disables++;
            if (failDisable) {
                throw new IllegalStateException("cannot stop");
            }
        }
    }

    AddonCandidate candidate(String json, LumoraAddon instance) throws InvalidDescriptorException {
        AddonDescriptor d = AddonDescriptorParser.parse(json, "test.jar");
        return AddonCandidate.of(d, () -> instance);
    }

    AddonRecord record(String id) {
        return core.addonManager().record(id).orElseThrow();
    }

    @Test
    void descriptorValidation() {
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse("{", "x"));
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse(json("Bad Id", ""), "x"));
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse(json("a1", "\"permissions\":[\"root\"]"), "x"));
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse(json("a1", "\"depends\":{\"a1\":\"*\"}"), "x"));
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse(json("a1", "\"depends\":{\"b1\":\"newest\"}"), "x"));
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse(
                "{\"id\":\"a1\",\"version\":\"1\",\"api\":\"1\"}", "x"), "entrypoint is required");
        assertThrows(InvalidDescriptorException.class, () -> AddonDescriptorParser.parse(json("a1", "\"schema\":7"), "x"));
    }

    @Test
    void fullLifecycleRemovesEverythingOnDisable() throws Exception {
        FakePlatform p = new FakePlatform(dir);
        core = new LumoraCore(p);
        int[] ticks = {0};
        int[] timer = {0};
        TestContexts.Box box = new TestContexts.Box("box");
        TestAddon addon = new TestAddon(ctx -> {
            ctx.events().subscribe(ClientTickEvent.class, e -> ticks[0]++);
            ctx.widgets().register(box);
            ctx.keybinds().register("act", "test.key", Keys.K, () -> { });
            ctx.tasks().repeat(1, () -> timer[0]++);
        });
        core.loadAddons(List.of(candidate(json("demo", "\"permissions\":[\"hud\",\"keybinds\",\"tasks\"]"), addon)));
        AddonRecord r = record("demo");
        assertEquals(AddonState.ENABLED, r.state());
        assertEquals(1, addon.inits);
        assertEquals(4, r.registrations());
        assertTrue(core.widgetRegistry().entry("demo:box").isPresent());
        core.tick();
        core.tick();
        assertEquals(2, ticks[0]);
        assertTrue(timer[0] >= 1);

        List<String> disabledEvents = new ArrayList<>();
        core.events().subscribe(AddonDisabledEvent.class, e -> disabledEvents.add(e.addon().id()));
        assertTrue(core.addonManager().setWanted("demo", false).isEmpty());
        assertEquals(AddonState.DISABLED, r.state());
        assertEquals(0, r.registrations());
        assertEquals(0, core.eventBus().listenerCount("demo"));
        assertTrue(core.widgetRegistry().entry("demo:box").isEmpty(), "widget removed");
        assertEquals(1, box.disposed);
        assertTrue(core.keybindManager().of("demo").isEmpty());
        assertEquals(List.of("demo"), disabledEvents);
        int before = ticks[0];
        int timerBefore = timer[0];
        core.tick();
        core.tick();
        assertEquals(before, ticks[0], "disabled addon does not keep working");
        assertEquals(timerBefore, timer[0]);

        // the choice is stored
        core.addonManager().tick();
        core.stopping();
        assertTrue(Files.readString(dir.resolve("lumora/addons.json")).contains("demo"));
        core = null;
    }

    @Test
    void enableAgainAfterUnloadCreatesNewRegistrations() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        List<TestContexts.Box> boxes = new ArrayList<>();
        TestAddon addon = new TestAddon(ctx -> {
            TestContexts.Box b = new TestContexts.Box("box");
            boxes.add(b);
            ctx.widgets().register(b);
        });
        core.loadAddons(List.of(candidate(json("demo", "\"permissions\":[\"hud\"]"), addon)));
        core.addonManager().unload("demo");
        assertEquals(AddonState.UNLOADED, record("demo").state());
        assertTrue(core.addonManager().setWanted("demo", true).isEmpty());
        assertEquals(AddonState.ENABLED, record("demo").state());
        assertEquals(2, addon.inits, "initialized again after unload");
        assertEquals(2, boxes.size());
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        List<Throwable> errors = new ArrayList<>();
        TestAddon addon = new TestAddon(ctx -> {
            try {
                ctx.widgets();
            } catch (AddonPermissionException e) {
                errors.add(e);
            }
            assertFalse(ctx.hasPermission(dev.lumora.api.addon.AddonPermission.HUD));
        });
        core.loadAddons(List.of(candidate(json("plain", ""), addon)));
        assertEquals(1, errors.size());
        assertEquals(AddonState.ENABLED, record("plain").state());
    }

    @Test
    void registeringOutsideEnableIsRejected() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        AddonContext[] saved = new AddonContext[1];
        TestAddon addon = new TestAddon(ctx -> saved[0] = ctx);
        core.loadAddons(List.of(candidate(json("late", "\"permissions\":[\"hud\"]"), addon)));
        core.addonManager().setWanted("late", false);
        assertThrows(IllegalStateException.class, () -> saved[0].widgets().register(new TestContexts.Box("x")));
        assertThrows(IllegalStateException.class, () -> saved[0].events().subscribe(ClientTickEvent.class, e -> { }));
    }

    @Test
    void failingOnEnableUndoesRegistrations() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        TestAddon addon = new TestAddon(ctx -> {
            ctx.events().subscribe(ClientTickEvent.class, e -> { });
            ctx.widgets().register(new TestContexts.Box("half"));
            throw new IllegalStateException("broken addon");
        });
        TestAddon good = new TestAddon(ctx -> { });
        core.loadAddons(List.of(candidate(json("bad", "\"permissions\":[\"hud\"]"), addon), candidate(json("good", ""), good)));
        AddonRecord r = record("bad");
        assertEquals(AddonState.FAILED, r.state());
        assertTrue(r.error().orElseThrow().contains("onEnable"));
        assertEquals(0, core.eventBus().listenerCount("bad"));
        assertTrue(core.widgetRegistry().entry("bad:half").isEmpty());
        assertEquals(AddonState.ENABLED, record("good").state(), "other addons are not affected");
    }

    @Test
    void errorInOnDisableStillCleansUp() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        TestAddon addon = new TestAddon(ctx -> ctx.events().subscribe(ClientTickEvent.class, e -> { }));
        addon.failDisable = true;
        core.loadAddons(List.of(candidate(json("sticky", ""), addon)));
        core.addonManager().setWanted("sticky", false);
        assertEquals(AddonState.DISABLED, record("sticky").state());
        assertEquals(0, core.eventBus().listenerCount("sticky"));
    }

    @Test
    void dependenciesMissingConflictingAndCyclic() throws Exception {
        FakePlatform p = new FakePlatform(dir);
        core = new LumoraCore(p);
        core.loadAddons(List.of(
                candidate(json("needs-missing", "\"depends\":{\"ghost\":\"*\"}"), new TestAddon(c -> { })),
                candidate(json("old-api", "\"api\":\"2.0.0\"").replace("\"api\":\"1.0.0\",", ""), new TestAddon(c -> { })),
                candidate(json("needs-new", "\"depends\":{\"base\":\">=2.0\"}"), new TestAddon(c -> { })),
                candidate(json("base", ""), new TestAddon(c -> { })),
                candidate(json("cyc-a", "\"depends\":{\"cyc-b\":\"*\"}"), new TestAddon(c -> { })),
                candidate(json("cyc-b", "\"depends\":{\"cyc-a\":\"*\"}"), new TestAddon(c -> { })),
                candidate(json("on-cycle", "\"depends\":{\"cyc-a\":\"*\"}"), new TestAddon(c -> { })),
                candidate(json("wrong-mc", "\"minecraft\":[\"1.20.x\"]"), new TestAddon(c -> { })),
                candidate(json("base", ""), new TestAddon(c -> { })),
                AddonCandidate.broken("broken.jar", "lumora-addon.json ist kein gültiges JSON")));
        assertEquals(AddonState.FAILED, record("needs-missing").state());
        assertTrue(record("needs-missing").error().orElseThrow().contains("ghost"));
        assertEquals(AddonState.FAILED, record("old-api").state());
        assertEquals(AddonState.FAILED, record("needs-new").state());
        assertTrue(record("needs-new").error().orElseThrow().contains(">=2.0"));
        assertEquals(AddonState.ENABLED, record("base").state(), "first 'base' wins");
        assertEquals(AddonState.FAILED, record("cyc-a").state());
        assertTrue(record("cyc-a").error().orElseThrow().contains("Zyklische"));
        assertEquals(AddonState.FAILED, record("cyc-b").state());
        assertEquals(AddonState.FAILED, record("on-cycle").state(), "depends on a failed addon");
        assertEquals(AddonState.FAILED, record("wrong-mc").state());
        long failed = core.addons().stream().filter(a -> a.state() == AddonState.FAILED).count();
        assertEquals(9, failed, "duplicate and broken candidates are listed as failed too");
    }

    @Test
    void dependencyOrderAndCascade() throws Exception {
        core = new LumoraCore(new FakePlatform(dir));
        List<String> order = new ArrayList<>();
        core.loadAddons(List.of(
                candidate(json("top", "\"depends\":{\"mid\":\"^1.0\",\"lumora\":\">=1.0\"}"), new TestAddon(c -> order.add("top"))),
                candidate(json("mid", "\"depends\":{\"low\":\"1.x\"}"), new TestAddon(c -> order.add("mid"))),
                candidate(json("low", "\"optional\":{\"not-installed\":\"*\"}"), new TestAddon(c -> order.add("low")))));
        assertEquals(List.of("low", "mid", "top"), order, "dependencies first");

        core.addonManager().setWanted("low", false);
        assertEquals(AddonState.DISABLED, record("mid").state(), "dependents follow");
        assertEquals(AddonState.DISABLED, record("top").state());
        assertTrue(record("top").wanted(), "user choice of dependents is kept");

        core.addonManager().setWanted("low", true);
        assertEquals(AddonState.ENABLED, record("mid").state(), "and come back");
        assertEquals(AddonState.ENABLED, record("top").state());

        core.addonManager().setWanted("mid", false);
        assertTrue(core.addonManager().setWanted("top", true).isPresent(), "cannot enable while its dependency is off");
    }

    @Test
    void addonSettingsArePersistedAndRestored() throws Exception {
        FakePlatform p = new FakePlatform(dir);
        core = new LumoraCore(p);
        TestAddon a1 = new TestAddon(c -> { });
        core.loadAddons(List.of(candidate(json("cfg", ""), a1)));
        BooleanSetting flag = (BooleanSetting) record("cfg").configuration().settings().get("flag").orElseThrow();
        flag.set(true);
        core.stopping();

        core = new LumoraCore(new FakePlatform(dir));
        TestAddon a2 = new TestAddon(c -> { });
        core.loadAddons(List.of(candidate(json("cfg", ""), a2)));
        assertTrue((Boolean) record("cfg").configuration().settings().get("flag").orElseThrow().get());
    }
}
