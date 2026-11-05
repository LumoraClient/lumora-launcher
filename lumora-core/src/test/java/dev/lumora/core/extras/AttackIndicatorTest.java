package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonState;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.i18n.Translations;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackIndicatorTest {
    @TempDir
    Path dir;
    LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    /** Game extras with a progress the test sets and the sounds played. */
    static final class Game implements GameExtras {
        Progress progress = Progress.NONE;
        final List<String> sounds = new ArrayList<>();

        @Override public Progress progress(float partialTick) { return progress; }
        @Override public void playSound(String kind, float volume) { sounds.add(kind); }
        @Override public List<Effect> effects() { return List.of(); }
        @Override public int count(String itemId) { return 0; }
        @Override public String heldItemId() { return ""; }
        @Override public String heldItemName() { return ""; }
        @Override public long dayTime() { return -1; }
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

    static GameExtras.Progress attack(float a, boolean target) {
        return new GameExtras.Progress(a, target, true, List.of(), "", 0, -1, -1, 1);
    }

    static RenderContext ctx(boolean editing) {
        return new RenderContext() {
            @Override public boolean preview() { return false; }
            @Override public boolean editing() { return editing; }
            @Override public float partialTick() { return 0; }
            @Override public int screenWidth() { return 427; }
            @Override public int screenHeight() { return 240; }
            @Override public int guiScale() { return 2; }
            @Override public long timeMillis() { return 0; }
            @Override public GameContext game() { return null; }
            @Override public Translations translations() { return null; }
        };
    }

    private AttackIndicatorModule start(Game game) {
        FakePlatform p = new FakePlatform(dir);
        p.extras = game;
        core = new LumoraCore(p);
        core.loadAddons(List.of());
        assertNull(AttackIndicatorModule.current(), "starts switched off");
        assertFalse(AttackIndicatorModule.hidesVanilla());
        assertTrue(core.addonManager().setWanted("lumora-attackindicator", true).isEmpty());
        AttackIndicatorModule m = AttackIndicatorModule.current();
        assertTrue(m != null && AttackIndicatorModule.hidesVanilla(), "on: Minecraft's own indicator is hidden");
        return m;
    }

    @Test
    void attackIsShownWhileChargingAndWhenAimingAtAMob() {
        AttackIndicatorModule m = start(new Game());
        List<AttackIndicatorModule.Shown> s = m.shown(attack(0.4f, false));
        assertEquals(1, s.size());
        assertEquals("attack", s.get(0).kind());
        assertEquals(0.4f, s.get(0).value(), 1e-6);
        assertTrue(m.shown(attack(1f, false)).isEmpty(), "hidden when ready, like Minecraft");
        assertEquals(1, m.shown(attack(1f, true)).size(), "full while aiming at a mob");
        assertEquals(m.readyColor.argb(), m.shown(attack(1f, true)).get(0).color());
        m.hideWhenReady.set(false);
        assertEquals(1, m.shown(attack(1f, false)).size());
        // 1.8.9: no attack cooldown
        assertTrue(m.shown(attack(-1, true)).isEmpty());
        core.addonManager().setWanted("lumora-attackindicator", false);
        assertFalse(AttackIndicatorModule.hidesVanilla());
    }

    @Test
    void usingAnItemWinsAndCooldownsFollow() {
        AttackIndicatorModule m = start(new Game());
        GameExtras.Progress bow = new GameExtras.Progress(0.3f, false, true,
                List.of(new GameExtras.Cooldown("minecraft:ender_pearl", "Enderperle", 0.75f)), "bow", 0.5f, 0.2f, -1, 1);
        List<AttackIndicatorModule.Shown> s = m.shown(bow);
        assertEquals(List.of("bow", "cooldown"), s.stream().map(AttackIndicatorModule.Shown::kind).toList());
        assertEquals(0.25f, s.get(1).value(), 1e-6, "a cooldown fills up until the item is ready");
        assertEquals("Enderperle", s.get(1).label());
        m.charging.set(false);
        assertEquals("breaking", m.shown(bow).get(0).kind(), "without charging: block breaking");
        m.breaking.set(false);
        assertEquals("attack", m.shown(bow).get(0).kind());
        m.cooldowns.set(false);
        assertEquals(1, m.shown(bow).size());
        GameExtras.Progress sleep = new GameExtras.Progress(1f, false, true, List.of(), "eat", 0.5f, -1, 0.6f, 1);
        assertEquals("eat", m.shown(sleep).get(0).kind());
        m.using.set(false);
        assertEquals("sleep", m.shown(sleep).get(0).kind());
    }

    @Test
    void soundsWhenTheAttackGetsReadyAndACooldownEnds() {
        Game game = new Game();
        AttackIndicatorModule m = start(game);
        game.progress = attack(1f, false);
        m.tick();
        assertTrue(game.sounds.isEmpty(), "no sound without a swing before");
        m.soundReady.set(true);
        game.progress = attack(0.2f, false);
        m.tick();
        game.progress = attack(0.95f, false);
        m.tick();
        game.progress = attack(1f, false);
        m.tick();
        m.tick();
        assertEquals(List.of("ready"), game.sounds, "once per recharge");
        assertTrue(m.pulseLeft() > 0, "the indicator pops when ready");

        game.sounds.clear();
        game.progress = new GameExtras.Progress(1f, false, true,
                List.of(new GameExtras.Cooldown("minecraft:ender_pearl", "Enderperle", 0.1f)), "", 0, -1, -1, 1);
        m.tick();
        assertTrue(game.sounds.isEmpty());
        game.progress = attack(1f, false);
        m.tick();
        assertEquals(List.of("cooldown"), game.sounds);
        m.volume.set(0);
        game.progress = new GameExtras.Progress(1f, false, true,
                List.of(new GameExtras.Cooldown("minecraft:chorus_fruit", "Chorusfrucht", 0.1f)), "", 0, -1, -1, 1);
        m.tick();
        game.progress = attack(1f, false);
        m.tick();
        assertEquals(1, game.sounds.size(), "volume 0: silent");
    }

    @Test
    void everyStyleAndPositionDrawsAndKeepsTheStackBalanced() {
        Game game = new Game();
        AttackIndicatorModule m = start(game);
        game.progress = new GameExtras.Progress(0.5f, false, true,
                List.of(new GameExtras.Cooldown("minecraft:ender_pearl", "Enderperle", 0.5f),
                        new GameExtras.Cooldown("minecraft:wind_charge", "Windkugel", 0.2f)), "", 0, -1, -1, 2);
        m.text.set(true);
        m.gradient.set(true);
        for (String st : AttackIndicatorModule.STYLES) {
            for (String pos : AttackIndicatorModule.POSITIONS) {
                m.style.set(st);
                m.position.set(pos);
                for (int size : new int[]{50, 100, 200}) {
                    m.size.set(size);
                    FakeGraphics g = new FakeGraphics();
                    m.render(g, ctx(false));
                    assertEquals(0, g.depth, st + "/" + pos);
                    assertTrue(g.fills > 3, st + "/" + pos + " draws something");
                    assertTrue(g.texts.contains("Enderperle"), "names with text on");
                }
            }
        }
        m.style.set("ring"); // the level and boss bar styles are checked on their own below
        assertTrue(m.atHotbar(), "the last position of the loop");
        m.position.set("crosshair");
        assertFalse(m.atHotbar());
        m.position.decode("vanilla"); // removed option: the stored value is ignored
        assertEquals("crosshair", m.position.get());
        // third person: nothing at the crosshair; HUD editor: nothing
        game.progress = new GameExtras.Progress(0.5f, false, false, List.of(), "", 0, -1, -1, 1);
        FakeGraphics g = new FakeGraphics();
        m.render(g, ctx(false));
        assertEquals(0, g.fills);
        m.position.set("hotbar");
        g = new FakeGraphics();
        m.render(g, ctx(true));
        assertEquals(0, g.fills, "not drawn while the HUD editor is open");
        assertEquals(AddonState.ENABLED, core.addons().stream().filter(a -> a.id().equals("lumora-attackindicator"))
                .findFirst().orElseThrow().state());
    }

    @Test
    void levelAndBossBarStylesUseMinecraftsBarPlaces() {
        Game game = new Game();
        AttackIndicatorModule m = start(game);
        // third person on purpose: these bars do not depend on the crosshair
        game.progress = new GameExtras.Progress(0.5f, false, false,
                List.of(new GameExtras.Cooldown("minecraft:ender_pearl", "Enderperle", 0.75f)), "", 0, -1, -1, 1);
        m.text.set(true);
        m.style.set("xpbar");
        assertFalse(m.position.isVisible(), "position does not apply to the level bar");
        assertFalse(m.size.isVisible());
        FakeGraphics g = new FakeGraphics();
        m.render(g, ctx(false));
        // 427 x 240: Minecraft's experience bar runs from x 122 to 304 at y 211 to 216; half charged = up to x 213
        assertTrue(g.rects.stream().anyMatch(r -> r[0] == 122 && r[1] == 211 && r[2] == 304 && r[3] == 216), "track over the experience bar");
        assertTrue(g.rects.stream().anyMatch(r -> r[0] == 122 && r[1] == 211 && r[2] == 213 && r[3] == 216), "filled to the half");
        assertTrue(g.texts.contains("50%"), "percent where Minecraft shows the level");
        assertTrue(g.texts.contains("Enderperle"), "the cooldown right of the hotbar, with its name");
        assertEquals(0, g.depth);

        m.style.set("bossbar");
        g = new FakeGraphics();
        m.render(g, ctx(false));
        assertTrue(g.rects.stream().anyMatch(r -> r[0] == 122 && r[1] == 12 && r[2] == 304 && r[3] == 17), "boss bar at the top");
        assertTrue(g.rects.stream().anyMatch(r -> r[0] == 122 && r[1] == 31 && r[2] == 168 && r[3] == 36), "cooldown as the next boss bar, a quarter done");
        assertTrue(g.texts.contains("50%") && g.texts.contains("Enderperle"), "titles above the bars");
        assertEquals(0, g.depth);

        m.style.set("ring");
        assertTrue(m.position.isVisible() && m.size.isVisible());
    }
}
