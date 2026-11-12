package dev.lumora.core.interaction;

import dev.lumora.api.game.WorldAccess;
import dev.lumora.api.interaction.PlayerMenu;
import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.extras.GameExtras;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMenuTest {
    static final UUID STEVE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    Path dir;
    FakePlatform platform;
    LumoraCore core;
    boolean middle;
    int permission;
    final List<String> chat = new ArrayList<>();

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        // GameExtras.NONE with the mouse wheel button, the operator level and the chat of the test
        platform.extras = (GameExtras) java.lang.reflect.Proxy.newProxyInstance(GameExtras.class.getClassLoader(),
                new Class<?>[] {GameExtras.class}, (proxy, m, args) -> switch (m.getName()) {
                    case "mouseMiddleDown" -> middle;
                    case "permissionLevel" -> permission;
                    case "openChat" -> {
                        chat.add((String) args[0]);
                        yield null;
                    }
                    default -> m.invoke(GameExtras.NONE, args);
                });
        platform.worldAccess = new WorldAccess() {
            @Override public Optional<ViewPoint> viewPoint() { return Optional.empty(); }
            @Override public List<PlayerInfo> players(int limit) { return List.of(); }
            @Override public List<TabEntry> tabList() { return List.of(); }
            @Override public Optional<TabEntry> crosshairPlayer() { return Optional.of(new TabEntry(STEVE, "Steve")); }
            @Override public boolean lineOfSightBlocked(double x1, double y1, double z1, double x2, double y2, double z2) { return false; }
            @Override public int seaLevel() { return 63; }
            @Override public String remoteIp() { return ""; }
            @Override public void actionBar(String message) { }
            @Override public void playClickSound() { }
        };
        core = new LumoraCore(platform);
    }

    @AfterEach
    void tearDown() {
        core.stopping();
    }

    static PlayerMenuLayout<String> layout(String... entries) {
        List<String> l = List.of(entries);
        return PlayerMenuLayout.of(l, new PlayerMenuLayout.Grouping<>() {
            @Override
            public String category(String e) {
                return e.contains("/") ? e.substring(0, e.indexOf('/')) : null;
            }

            @Override
            public String categoryIcon(String e) {
                return null;
            }
        });
    }

    @Test
    void categoriesSitWhereTheirFirstEntryIs() {
        PlayerMenuLayout<String> l = layout("a", "mod/kick", "b", "mod/ban", "c", "mod/mute");
        assertEquals(4, l.size(), "a, the category mod (where its first entry is), b, c");
        assertTrue(l.slots().get(1).isCategory());
        assertEquals(List.of("mod/kick", "mod/ban", "mod/mute"), l.slots().get(1).entries);
        assertEquals(l.slots().get(1), l.category("mod"));
        assertNull(l.category("gone"));
        assertNull(l.category(null));
    }

    PlayerMenuView shown() {
        return assertInstanceOf(PlayerMenuView.class, platform.screenStack.peek());
    }

    /** Moves the mouse onto the row whose label contains {@code label} and draws a frame; returns the mouse position. */
    int[] pointAt(PlayerMenuView v, String label) {
        v.render(new FakeGraphics(), 400, 300, 0, 0); // where the mouse rests when the menu opens
        for (int y = 0; y < 300; y += 3) {
            for (int x = 200; x < 400; x += 20) {
                v.render(new FakeGraphics(), 400, 300, x, y);
                String c = v.chosen();
                if (c != null && c.contains(label)) {
                    return new int[] {x, y};
                }
            }
        }
        throw new AssertionError("no row " + label);
    }

    @Test
    void holdingTheMiddleButtonOpensReleasingRunsTheEntry() throws InterruptedException {
        middle = true;
        core.tick();
        PlayerMenuView v = shown();
        assertTrue(core.interaction().isOpen());
        FakeGraphics card = new FakeGraphics();
        v.render(card, 400, 300, 0, 0);
        assertTrue(card.texts.contains("Steve"), "the player card next to the list");
        assertTrue(card.texts.contains("Player interactions"));
        // in singleplayer without the Lumora server: only "Copy name" (English test platform)
        int[] at = pointAt(v, "Copy name");
        Thread.sleep(400); // held longer than a click
        middle = false;
        v.render(new FakeGraphics(), 400, 300, at[0], at[1]); // notices the release
        assertEquals("Steve", platform.clipboard);
        assertTrue(platform.screenStack.isEmpty(), "closed");
        assertFalse(core.interaction().isOpen());
    }

    @Test
    void aShortClickKeepsTheMenuOpenForAClickOrTheWheel() {
        middle = true;
        core.tick();
        PlayerMenuView v = shown();
        v.render(new FakeGraphics(), 400, 300, 200, 150);
        middle = false;
        v.mouseReleased(200, 150, 2); // released right away, even over an action
        assertFalse(platform.screenStack.isEmpty(), "stays open");
        assertEquals(null, v.chosen(), "the resting mouse chooses nothing");
        v.mouseScrolled(200, 150, -1); // the wheel chooses the first entry
        v.render(new FakeGraphics(), 400, 300, 200, 150);
        assertEquals("Copy name", v.chosen());
        v.keyPressed(dev.lumora.api.input.Keys.ENTER, false, false);
        assertEquals("Steve", platform.clipboard);
        assertTrue(platform.screenStack.isEmpty());
    }

    @Test
    void partyInviteIsTheServersPartyCommand() {
        platform.connection = new dev.lumora.api.game.ConnectionInfo(dev.lumora.api.game.ConnectionInfo.Kind.MULTIPLAYER, "mc.example.net", 20);
        platform.inWorld = true;
        middle = true;
        core.tick();
        PlayerMenuView v = shown();
        int[] at = pointAt(v, "Invite to party");
        v.mouseClicked(at[0], at[1], 0);
        // GameExtras.NONE cannot send commands: the chat opens with the command instead
        assertEquals(List.of("/party invite Steve"), chat);
        assertTrue(platform.screenStack.isEmpty());

        assertTrue(PlayerInteraction.commandName("Steve_99"));
        assertFalse(PlayerInteraction.commandName("§6Shop NPC"));
        assertFalse(PlayerInteraction.commandName("a b"));
    }

    @Test
    void theFriendEntryIsGreyedOutWithTheReasonWhileTheSocialServerIsUnreachable() {
        // Lumora Social set up, but nothing listens on the server address
        System.setProperty("lumora.social.server", "ws://127.0.0.1:1/");
        try {
            platform.gameSession = Optional.of(new dev.lumora.core.social.GameSession("Me", "99999999999999999999999999999999", () -> "x"));
            core.clientStarted();
            middle = true;
            core.tick();
            PlayerMenuView v = shown();
            int[] at = pointAt(v, "Add friend · not connected");
            v.mouseClicked(at[0], at[1], 0); // a click on a greyed out entry does nothing
            assertFalse(platform.screenStack.isEmpty());
            FakeGraphics g = new FakeGraphics();
            v.render(g, 400, 300, at[0], at[1]);
            assertTrue(g.texts.contains("offline"), "the card says why");
            assertFalse(g.texts.stream().anyMatch(t -> t.contains("party")), "no party entry in singleplayer");
            v.close();
        } finally {
            System.clearProperty("lumora.social.server");
        }
    }

    @Test
    void addonEntriesWithCategoryPermissionAndAFailingOne() {
        RegistrationTracker tracker = new RegistrationTracker();
        PlayerMenu menu = core.playerMenu().scoped("demo", tracker, () -> { });
        List<String> ran = new ArrayList<>();
        menu.add(PlayerMenuEntry.builder("kick", "Kicken").category("Moderation", "⚒").requiresPermissionLevel(2)
                .onSelect(t -> ran.add("kick " + t.name())).build());
        menu.add(PlayerMenuEntry.builder("wave", "Winken").icon("☺").onSelect(t -> ran.add("wave")).build());
        menu.add(PlayerMenuEntry.builder("broken", "Kaputt").visibleWhen(t -> {
            throw new IllegalStateException("boom");
        }).build());
        assertEquals(3, menu.entries().size());

        middle = true;
        core.tick();
        PlayerMenuView v = shown();
        int[] at = pointAt(v, "Winken");
        v.mouseClicked(at[0], at[1], 0);
        middle = false;
        assertEquals(List.of("wave"), ran);
        assertTrue(platform.screenStack.isEmpty());
        assertTrue(platform.logs.stream().anyMatch(l -> l.contains("demo:broken")), "the failing entry is logged and left out");

        core.tick(); // the button is up again
        // operator level 2: the category appears and opens its own list in the same menu
        permission = 2;
        middle = true;
        core.tick();
        v = shown();
        at = pointAt(v, "Moderation");
        v.mouseClicked(at[0], at[1], 0);
        int[] kick = pointAt(v, "Kicken");
        pointAt(v, "< Moderation");
        v.mouseClicked(0, 0, 1); // right click: back to the main list
        v.render(new FakeGraphics(), 400, 300, kick[0], kick[1]);
        assertFalse("Kicken".equals(v.chosen()));
        v.mouseClicked(at[0], at[1], 0);
        v.render(new FakeGraphics(), 400, 300, kick[0], kick[1]);
        v.mouseClicked(kick[0], kick[1], 0);
        assertEquals(List.of("wave", "kick Steve"), ran);
        assertTrue(platform.screenStack.isEmpty());

        tracker.closeAll(platform.logger(), "demo");
        assertTrue(menu.entries().isEmpty(), "removed with the addon");
    }
}
