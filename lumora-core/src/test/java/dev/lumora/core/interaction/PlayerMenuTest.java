package dev.lumora.core.interaction;

import dev.lumora.api.game.WorldAccess;
import dev.lumora.api.interaction.PlayerMenu;
import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.extras.GameExtras;
import dev.lumora.core.interaction.PlayerMenuLayout.Selection;
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
    void theRingStartsAtTheTopAndAnglesChooseTheSegment() {
        PlayerMenuLayout<String> l = layout("a", "mod/kick", "b", "mod/ban", "c", "mod/mute");
        assertEquals(4, l.size(), "a, the category mod (where its first entry is), b, c");
        assertTrue(l.slots().get(1).isCategory());
        assertEquals(List.of("mod/kick", "mod/ban", "mod/mute"), l.slots().get(1).entries);
        assertEquals(new Selection(0, -1), l.hit(0, -50, -1), "up");
        assertEquals(new Selection(1, -1), l.hit(50, 0, -1), "right");
        assertEquals(new Selection(2, -1), l.hit(0, 50, -1), "down");
        assertEquals(new Selection(3, -1), l.hit(-50, 0, -1), "left");
        assertEquals(new Selection(0, -1), l.hit(0, -300, -1), "far outside still chooses by angle");
        assertTrue(l.hit(3, 4, -1).none(), "near the center nothing");

        // the open category: its outer ring is centered on its segment (right), one tenth of the circle per entry
        double a = l.childCenter(1, 0);
        double r = (PlayerMenuLayout.CHILD_INNER + PlayerMenuLayout.CHILD_OUTER) / 2.0;
        assertEquals(new Selection(1, 0), l.hit(Math.sin(a) * r, -Math.cos(a) * r, 1));
        double c = l.childCenter(1, 2);
        assertEquals(new Selection(1, 2), l.hit(Math.sin(c) * r, -Math.cos(c) * r, 1));
        assertEquals(new Selection(1, -1), l.hit(50, 0, 1), "inside the ring: the category itself");
        assertEquals(new Selection(3, -1), l.hit(-r, 0, 1), "outside the outer ring: by angle");
        assertEquals("mod/ban", l.entry(new Selection(1, 1)));
        assertEquals(null, l.entry(new Selection(1, -1)), "a category is no entry");

        // the wheel steps through the entries, the category's in its place
        assertEquals(List.of(new Selection(0, -1), new Selection(1, 0), new Selection(1, 1), new Selection(1, 2),
                new Selection(2, -1), new Selection(3, -1)), l.order());
    }

    @Test
    void segmentsAreFilledWithGapsBetweenThem() {
        double w = Math.PI / 2;
        int[] runs = PlayerMenuLayout.runs(30, 70, 2, -w / 2, w, 4, 2.0);
        assertTrue(runs.length > 0 && runs.length % 4 == 0);
        // straight up at radius 50 is segment 0; on the diagonal (boundary between 0 and 1) nothing
        assertEquals(0, segmentAt(runs, 0, -50, 2));
        assertEquals(1, segmentAt(runs, 50, 0, 2));
        assertEquals(-1, segmentAt(runs, 35.4, -35.4, 2), "gap on the boundary");
        assertEquals(-1, segmentAt(runs, 0, -10, 2), "the inside stays free for the player");
        assertEquals(-1, segmentAt(runs, 0, -80, 2));
    }

    static int segmentAt(int[] runs, double x, double y, int q) {
        int sx = (int) Math.floor(x * q);
        int sy = (int) Math.floor(y * q);
        for (int i = 0; i < runs.length; i += 4) {
            if (runs[i] == sy && sx >= runs[i + 1] && sx < runs[i + 2]) {
                return runs[i + 3];
            }
        }
        return -1;
    }

    PlayerMenuView shown() {
        return assertInstanceOf(PlayerMenuView.class, platform.screenStack.peek());
    }

    /** Moves the mouse onto the segment whose label is {@code label} and draws a frame. */
    FakeGraphics pointAt(PlayerMenuView v, String label) {
        for (double a = 0; a < 2 * Math.PI; a += Math.PI / 90) {
            int mx = 200 + (int) Math.round(Math.sin(a) * 50);
            int my = 150 - (int) Math.round(Math.cos(a) * 50);
            FakeGraphics g = new FakeGraphics();
            v.render(g, 400, 300, mx, my);
            if (g.texts.stream().anyMatch(t -> t.contains(label))) {
                return g;
            }
        }
        throw new AssertionError("no segment " + label);
    }

    @Test
    void holdingTheMiddleButtonOpensReleasingRunsTheEntry() {
        middle = true;
        core.tick();
        PlayerMenuView v = shown();
        assertTrue(core.interaction().isOpen());
        // in singleplayer without the Lumora server: only "Copy name" (English test platform)
        FakeGraphics g = pointAt(v, "Copy name");
        assertTrue(g.texts.stream().anyMatch(t -> t.contains("Steve")), "the player card above the ring");
        middle = false;
        v.render(new FakeGraphics(), 400, 300, 200, 100); // notices the release
        assertEquals("Steve", platform.clipboard);
        assertTrue(platform.screenStack.isEmpty(), "closed");
        assertFalse(core.interaction().isOpen());
    }

    @Test
    void aShortClickKeepsTheMenuOpenForAClickOrTheWheel() {
        middle = true;
        core.tick();
        PlayerMenuView v = shown();
        middle = false;
        v.mouseReleased(200, 150, 2); // released right away in the middle
        assertFalse(platform.screenStack.isEmpty(), "stays open");
        v.mouseScrolled(200, 150, -1); // the wheel chooses the first entry
        FakeGraphics g = new FakeGraphics();
        v.render(g, 400, 300, 200, 150);
        assertTrue(g.texts.stream().anyMatch(t -> t.contains("Copy name")));
        v.mouseClicked(200, 150, 0);
        assertEquals("Steve", platform.clipboard);
        assertTrue(platform.screenStack.isEmpty());
    }

    @Test
    void partyAndFriendAreOfferedGreyedOutWhileTheSocialServerIsUnreachable() {
        // Lumora Social set up, but nothing listens on the server address
        System.setProperty("lumora.social.server", "ws://127.0.0.1:1/");
        try {
            platform.gameSession = Optional.of(new dev.lumora.core.social.GameSession("Me", "99999999999999999999999999999999", () -> "x"));
            core.clientStarted();
            middle = true;
            core.tick();
            PlayerMenuView v = shown();
            FakeGraphics g = pointAt(v, "Invite to party");
            assertTrue(g.texts.stream().anyMatch(t -> t.contains("Invite to party · not connected")), "with the reason");
            pointAt(v, "Add friend · not connected");
            v.mouseClicked(200, 100, 0); // a click on a greyed out entry does nothing
            assertFalse(platform.screenStack.isEmpty());
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
        pointAt(v, "Winken");
        middle = false;
        v.render(new FakeGraphics(), 400, 300, 200, 150);
        assertEquals(List.of("wave"), ran, "released over the entry chosen in the last frame");
        assertTrue(platform.screenStack.isEmpty());
        assertTrue(platform.logs.stream().anyMatch(l -> l.contains("demo:broken")), "the failing entry is logged and left out");

        core.tick(); // the button is up again
        // operator level 2: the category appears and opens its outer ring in the same menu
        permission = 2;
        middle = true;
        core.tick();
        v = shown();
        FakeGraphics g = pointAt(v, "Moderation");
        assertTrue(g.texts.stream().anyMatch(t -> t.contains("Moderation")));
        v.mouseClicked(0, 0, 1); // right click: back (closes the outer ring)
        v.close();

        tracker.closeAll(platform.logger(), "demo");
        assertTrue(menu.entries().isEmpty(), "removed with the addon");
    }
}
