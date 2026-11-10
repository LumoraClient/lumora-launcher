package dev.lumora.core.extras;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.TestContexts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusBarsModuleTest {
    @TempDir
    Path dir;

    /** Game values and slots as a version adapter would deliver them. */
    static final class Extras implements GameExtras {
        GameExtras.Status status = new GameExtras.Status(true, true, false, 7, 20, 4, 0, 13, 300, 300, false, 30, 0.5f,
                false, "normal", false, 4);
        String offhand = "";
        final List<Integer> drawn = new ArrayList<>();

        @Override public Status status() { return status; }
        @Override public void drawSlot(HudGraphics g, int slot, int x, int y) { drawn.add(slot); }
        @Override public String slotItem(int slot) { return slot == 9 ? offhand : ""; }
        @Override public List<Effect> effects() { return List.of(); }
        @Override public int count(String itemId) { return 0; }
        @Override public String heldItemId() { return ""; }
        @Override public String heldItemName() { return ""; }
        @Override public long dayTime() { return 0; }
        @Override public List<String> resourcePacks() { return List.of(); }
        @Override public int targetEntityId() { return -1; }
        @Override public int hurtTime(int entityId) { return -1; }
        @Override public int ownHurtTime() { return 0; }
        @Override public double gamma() { return 0.5; }
        @Override public void setGamma(double value) { }
        @Override public int sprintKey() { return -1; }
        @Override public int sneakKey() { return -1; }
        @Override public void holdSprint(boolean down) { }
        @Override public void holdSneak(boolean down) { }
    }

    private FakePlatform platform(String mc, Extras extras) {
        FakePlatform p = new FakePlatform(dir);
        p.mc = mc;
        p.extras = extras;
        p.inWorld = true;
        return p;
    }

    private static StatusBarsModule module(FakePlatform p) {
        StatusBarsModule m = new StatusBarsModule(p);
        m.sprites = new VanillaSprites(p.minecraftVersion());
        return m;
    }

    private static <W extends HudWidget> W configured(W w) {
        w.configure(new SettingsGroup("lumora-statusbars.widget." + w.id() + "."));
        return w;
    }

    private static RenderContext world(FakePlatform p) {
        return TestContexts.ctx(p, 480, 270, false);
    }

    @Test
    void valuesAreShownAsNumberWithMaximumOrInPercent() {
        assertEquals("17", StatusBarsModule.format("value", 17, 20, false));
        assertEquals("17/20", StatusBarsModule.format("value_max", 17, 20, false));
        assertEquals("85%", StatusBarsModule.format("percent", 17, 20, false));
        assertEquals("16.5", StatusBarsModule.format("value", 16.5, 20, true), "half hearts with decimals");
        assertEquals("17", StatusBarsModule.format("value", 16.5, 20, false), "rounded up like the hearts");
    }

    @Test
    void heartsAreDrawnLikeMinecraftWithAbsorptionInASecondRow() {
        Extras x = new Extras();
        FakePlatform p = platform("26.3", x);
        StatusBarsModule.Health h = configured(new StatusBarsModule.Health(module(p)));
        RenderContext ctx = world(p);
        // 20 max health + 4 absorption: 10 + 2 hearts, two rows 10 px apart
        assertEquals(81, h.width(ctx));
        assertEquals(19, h.height(ctx));
        FakeGraphics g = new FakeGraphics();
        h.render(g, ctx);
        assertEquals(12, g.textures.stream().filter(t -> t.endsWith("heart/container.png")).count());
        assertEquals(3, g.textures.stream().filter(t -> t.endsWith("heart/full.png")).count(), "7 health: 3 full hearts");
        assertEquals(1, g.textures.stream().filter(t -> t.endsWith("heart/half.png")).count(), "and a half one");
        assertEquals(2, g.textures.stream().filter(t -> t.endsWith("heart/absorbing_full.png")).count());
        // before 1.20.2 the same hearts come from icons.png
        FakePlatform old = platform("1.16.5", x);
        StatusBarsModule.Health oh = configured(new StatusBarsModule.Health(module(old)));
        FakeGraphics og = new FakeGraphics();
        oh.render(og, world(old));
        assertTrue(og.textures.stream().allMatch(t -> t.equals("minecraft:textures/gui/icons.png")), og.textures.toString());
    }

    @Test
    void theOtherLooksShowIconAndNumberABarOrTheNumber() {
        Extras x = new Extras();
        FakePlatform p = platform("26.3", x);
        StatusBarsModule.Food f = configured(new StatusBarsModule.Food(module(p)));
        RenderContext ctx = world(p);
        f.style.set("compact");
        f.format.set("value_max");
        FakeGraphics g = new FakeGraphics();
        f.render(g, ctx);
        assertTrue(g.texts.contains("13/20"), g.texts.toString());
        assertTrue(g.textures.contains("minecraft:textures/gui/sprites/hud/food_full.png"), "the food icon in front");
        f.style.set("bar");
        f.format.set("percent");
        g = new FakeGraphics();
        f.render(g, ctx);
        assertTrue(g.texts.contains("65%"));
        assertEquals(80, f.width(ctx));
        f.style.set("number");
        f.format.set("value");
        g = new FakeGraphics();
        f.render(g, ctx);
        assertEquals(List.of("13"), g.texts);
        assertTrue(g.textures.isEmpty());
    }

    @Test
    void armorAndAirOnlyShowWhenTheyMatterAndNothingInCreative() {
        Extras x = new Extras();
        FakePlatform p = platform("26.3", x);
        StatusBarsModule m = module(p);
        StatusBarsModule.Armor a = configured(new StatusBarsModule.Armor(m));
        StatusBarsModule.Air air = configured(new StatusBarsModule.Air(m));
        StatusBarsModule.Health h = configured(new StatusBarsModule.Health(m));
        RenderContext ctx = world(p);
        assertFalse(a.isVisible(ctx), "no armor");
        assertFalse(air.isVisible(ctx), "full air above water");
        a.autoHide.set(false);
        assertTrue(a.isVisible(ctx));
        assertTrue(h.isVisible(ctx));
        x.status = new GameExtras.Status(true, false, false, 20, 20, 0, 0, 20, 300, 300, false, 0, 0, false, "normal", false, 0);
        assertFalse(module(platform("26.3", x)).status(world(p)).survival());
        StatusBarsModule.Health creative = configured(new StatusBarsModule.Health(module(p)));
        assertFalse(creative.isVisible(world(p)), "creative: no hearts");
        assertTrue(creative.isVisible(TestContexts.ctx(p, 480, 270, true)), "in the editor always");
    }

    @Test
    void theHotbarHasAllLooksAndTheOffhandRules() {
        Extras x = new Extras();
        FakePlatform p = platform("26.3", x);
        StatusBarsModule.Hotbar hb = configured(new StatusBarsModule.Hotbar(module(p)));
        RenderContext ctx = world(p);
        assertEquals(182 + 2 * 29, hb.width(ctx), "Minecraft's strip with room for the offhand on both sides (centred)");
        assertEquals(24, hb.height(ctx));
        FakeGraphics g = new FakeGraphics();
        hb.render(g, ctx);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), x.drawn, "nine items, the empty offhand is not drawn");
        assertTrue(g.textures.contains("minecraft:textures/gui/sprites/hud/hotbar.png"));
        assertTrue(g.textures.contains("minecraft:textures/gui/sprites/hud/hotbar_selection.png"));

        x.offhand = "minecraft:totem_of_undying";
        assertTrue(hb.offhandShown(ctx));
        hb.offhandSpecial.set(false);
        assertFalse(hb.offhandShown(ctx), "shield and totem can be hidden");
        x.offhand = "minecraft:torch";
        assertTrue(hb.offhandShown(ctx), "other items still show");

        hb.offhand.set("off");
        assertEquals(182, hb.width(ctx));
        hb.vertical.set(true);
        assertEquals(24, hb.width(ctx));
        assertEquals(183, hb.height(ctx));
        hb.style.set("lumora");
        hb.vertical.set(false);
        hb.numbers.set(true);
        assertEquals(9 * 22 - 2, hb.width(ctx));
        g = new FakeGraphics();
        hb.render(g, ctx);
        assertTrue(g.texts.containsAll(List.of("1", "5", "9")), "slot numbers");
        assertTrue(g.textures.isEmpty(), "Lumora's slots are drawn, not textured");

        // 1.8.9 has no offhand
        StatusBarsModule.Hotbar legacy = configured(new StatusBarsModule.Hotbar(module(platform("1.8.9", x))));
        assertEquals(182, legacy.width(ctx));
    }

    @Test
    void experienceShowsLevelAndProgress() {
        Extras x = new Extras();
        FakePlatform p = platform("26.3", x);
        StatusBarsModule.Experience e = configured(new StatusBarsModule.Experience(module(p)));
        RenderContext ctx = world(p);
        FakeGraphics g = new FakeGraphics();
        e.render(g, ctx);
        assertTrue(g.texts.contains("30"), "level above the bar");
        assertTrue(g.textures.contains("minecraft:textures/gui/sprites/hud/experience_bar_progress.png"));
        e.style.set("number");
        e.format.set("value_max");
        g = new FakeGraphics();
        e.render(g, ctx);
        assertEquals(List.of("30 (50%)"), g.texts);
    }
}
