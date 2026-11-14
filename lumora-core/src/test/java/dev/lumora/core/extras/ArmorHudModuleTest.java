package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Module "Armor HUD": every armor piece and the held item is its own movable widget. */
class ArmorHudModuleTest {
    @TempDir
    Path dir;
    LumoraCore core;

    /** What the player wears (piece index → equipment) and which items were drawn ("piece:sample"). */
    final Map<Integer, GameExtras.Equipment> worn = new HashMap<>();
    final List<String> drawn = new ArrayList<>();
    final AtomicBoolean inWorld = new AtomicBoolean(true);
    final GameExtras extras = (GameExtras) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GameExtras.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equipment" -> worn.getOrDefault((Integer) args[0], GameExtras.Equipment.NONE);
                case "drawEquipment" -> {
                    drawn.add(args[1] + ":" + args[2]);
                    yield null;
                }
                default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
            });

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    private static RenderContext context(boolean editing) {
        return (RenderContext) Proxy.newProxyInstance(ArmorHudModuleTest.class.getClassLoader(), new Class<?>[]{RenderContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "editing" -> editing;
                    case "preview" -> false;
                    case "partialTick" -> 0f;
                    case "screenWidth", "screenHeight", "guiScale" -> 1;
                    default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
                });
    }

    private List<ArmorHudModule.Piece> pieces() {
        List<ArmorHudModule.Piece> out = new ArmorHudModule(() -> extras, inWorld::get).pieces();
        for (ArmorHudModule.Piece p : out) {
            p.configure(new SettingsGroup("lumora-armor.widget." + p.id() + "."));
        }
        return out;
    }

    @Test
    void fiveWidgetsEachWithItsOwnPlaceAndName() {
        List<ArmorHudModule.Piece> pieces = pieces();
        assertEquals(List.of("helmet", "chestplate", "leggings", "boots", "mainhand"), pieces.stream().map(HudWidget::id).toList());
        Set<Integer> offsets = new HashSet<>();
        for (ArmorHudModule.Piece p : pieces) {
            assertEquals("lumora-armor.widget." + p.id(), p.nameKey(), "name from the language files");
            WidgetPlacement place = p.defaultPlacement();
            assertTrue(place.enabled() && place.anchoredPlacement(), p.id());
            offsets.add(place.offsetY());
        }
        assertEquals(5, offsets.size(), "stacked, not on top of each other");
    }

    @Test
    void textOfDurabilityAndCount() {
        GameExtras.Equipment helmet = new GameExtras.Equipment(true, 120, 363, 1);
        assertEquals("120", ArmorHudModule.text(helmet, "value"));
        assertEquals("120/363", ArmorHudModule.text(helmet, "value_max"));
        assertEquals("33%", ArmorHudModule.text(helmet, "percent"));
        assertEquals("×64", ArmorHudModule.text(new GameExtras.Equipment(true, 0, 0, 64), "value"), "blocks show the count");
        assertEquals("", ArmorHudModule.text(new GameExtras.Equipment(true, 0, 0, 1), "value"));
        assertEquals("—", ArmorHudModule.text(GameExtras.Equipment.NONE, "value"));
        assertEquals(dev.lumora.api.render.Colors.OK, ArmorHudModule.durabilityColor(new GameExtras.Equipment(true, 300, 363, 1)));
        assertEquals(ArmorHudModule.RED, ArmorHudModule.durabilityColor(new GameExtras.Equipment(true, 20, 363, 1)));
    }

    @Test
    void outsideTheWorldEveryPieceShowsTheDiamondSample() {
        inWorld.set(false);
        for (ArmorHudModule.Piece p : pieces()) {
            p.tick();
            FakeGraphics g = new FakeGraphics();
            p.render(g, context(false));
            int max = ArmorHudModule.SAMPLE_MAX[p.index];
            assertEquals(List.of(Integer.toString(max)), g.texts, p.id());
            assertTrue(p.width(context(false)) > ArmorHudModule.ICON, "width follows the text");
        }
        assertEquals(List.of("0:true", "1:true", "2:true", "3:true", "4:true"), drawn);
    }

    @Test
    void inTheWorldOwnItemsAndEmptySlotsHidden() {
        worn.put(0, new GameExtras.Equipment(true, 275, 363, 1));
        worn.put(4, new GameExtras.Equipment(true, 0, 0, 32));
        List<ArmorHudModule.Piece> pieces = pieces();
        pieces.forEach(ArmorHudModule.Piece::tick);
        ArmorHudModule.Piece helmet = pieces.get(0);
        ArmorHudModule.Piece boots = pieces.get(3);
        ArmorHudModule.Piece held = pieces.get(4);

        FakeGraphics g = new FakeGraphics();
        helmet.render(g, context(false));
        assertEquals(List.of("275"), g.texts);
        assertEquals("0:false", drawn.getLast(), "the player's own helmet");

        g = new FakeGraphics();
        held.render(g, context(false));
        assertEquals(List.of("×32"), g.texts);

        assertFalse(boots.isVisible(context(false)), "no boots: hidden");
        assertTrue(boots.isVisible(context(true)), "but movable in the HUD editor");
        g = new FakeGraphics();
        boots.render(g, context(true));
        assertEquals(List.of("—"), g.texts);
        assertEquals("3:true", drawn.getLast(), "empty slot: diamond boots as placeholder");

        boots.hideEmpty.set(false);
        assertTrue(boots.isVisible(context(false)), "hide when empty off");
    }

    @Test
    void builtInModuleStartsOffAndRegistersTheFivePieces() {
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        Map<String, AddonState> states = core.addons().stream().collect(Collectors.toMap(AddonInfo::id, AddonInfo::state));
        assertTrue(states.containsKey("lumora-armor"));
        assertTrue(states.get("lumora-armor") != AddonState.ENABLED, "off until the player switches it on");
        assertTrue(core.addonManager().setWanted("lumora-armor", true).isEmpty());
        for (String piece : ArmorHudModule.PIECES) {
            assertTrue(core.widgetRegistry().entry("lumora-armor:" + piece).isPresent(), piece);
        }
    }
}
