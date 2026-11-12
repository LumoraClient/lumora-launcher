package dev.lumora.core.hub;

import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotViewTest {
    @TempDir
    Path tmp;

    private FakePlatform platform() throws IOException {
        Path config = tmp.resolve("game").resolve("config");
        Files.createDirectories(config);
        return new FakePlatform(config);
    }

    private void png(String name, int w, int h, long modified) throws IOException {
        Path dir = tmp.resolve("game").resolve("screenshots");
        Files.createDirectories(dir);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, (x * 255 / w) << 16 | (y * 255 / h) << 8);
            }
        }
        Path f = dir.resolve(name);
        ImageIO.write(img, "png", f.toFile());
        Files.setLastModifiedTime(f, FileTime.fromMillis(modified));
    }

    /** Renders until the background decoding uploaded {@code count} textures (or gives up after a while). */
    private static FakeGraphics renderUntil(ScreenshotView v, FakePlatform p, int count) throws InterruptedException {
        for (int i = 0; i < 200 && p.textures.size() < count; i++) {
            v.render(new FakeGraphics(), 854, 480, 0, 0);
            Thread.sleep(10);
        }
        FakeGraphics g = new FakeGraphics();
        v.render(g, 854, 480, 0, 0);
        return g;
    }

    @Test
    void newScreenshotsRememberServerAccountAndPlayersNearby() throws Exception {
        FakePlatform p = platform();
        p.inWorld = true;
        p.connection = new dev.lumora.api.game.ConnectionInfo(dev.lumora.api.game.ConnectionInfo.Kind.MULTIPLAYER, "Play.Example.net:25565", 30);
        java.util.UUID me = java.util.UUID.randomUUID();
        p.player = new dev.lumora.api.game.PlayerSnapshot(me, "Steve", 0, 64, 0, 0, 0, 20, 20, 20, 0, 0, true, false, false, 0);
        p.worldAccess = new dev.lumora.api.game.WorldAccess() {
            @Override public java.util.Optional<ViewPoint> viewPoint() { return java.util.Optional.empty(); }
            @Override public java.util.List<PlayerInfo> players(int limit) {
                return java.util.List.of(new PlayerInfo(me, "Steve", 0, 64, 0, false, true, java.util.OptionalInt.empty()),
                        new PlayerInfo(java.util.UUID.randomUUID(), "Far", 300, 64, 0, false, true, java.util.OptionalInt.empty()),
                        new PlayerInfo(java.util.UUID.randomUUID(), "Alex", 5, 64, 0, false, true, java.util.OptionalInt.empty()));
            }
            @Override public java.util.List<TabEntry> tabList() { return java.util.List.of(); }
            @Override public java.util.Optional<TabEntry> crosshairPlayer() { return java.util.Optional.empty(); }
            @Override public boolean lineOfSightBlocked(double a, double b, double c, double d, double e, double f) { return false; }
            @Override public int seaLevel() { return 63; }
            @Override public String remoteIp() { return ""; }
            @Override public void actionBar(String message) { }
            @Override public void playClickSound() { }
        };
        png("before.png", 32, 18, System.currentTimeMillis() - 60_000);
        LumoraCore core = new LumoraCore(p);
        ScreenshotWatcher w = new ScreenshotWatcher(core);
        png("taken.png", 32, 18, System.currentTimeMillis() + 2_000);
        w.check();
        ScreenshotLibrary lib = ScreenshotView.library(core);
        lib.refresh();
        ScreenshotLibrary.Shot taken = lib.all().stream().filter(s -> s.name().equals("taken.png")).findFirst().orElseThrow();
        ScreenshotLibrary.Meta m = lib.meta(taken);
        assertEquals("play.example.net", m.server(), "without the default port");
        assertEquals("Steve", m.account());
        assertEquals(java.util.List.of("Alex"), m.players(), "only players nearby, not yourself");
        assertTrue(lib.meta(lib.all().stream().filter(s -> s.name().equals("before.png")).findFirst().orElseThrow()) == null);

        // the albums tab shows the automatic albums; an album opens its photos
        ScreenshotView v = new ScreenshotView(core);
        v.tab(true);
        FakeGraphics g = renderUntil(v, p, 1);
        assertTrue(g.texts.contains("My albums") || g.texts.contains("Meine Alben"), g.texts.toString());
        assertTrue(g.texts.contains("New album") || g.texts.contains("Neues Album"));
        assertTrue(g.texts.contains("play.example.net"));
        assertTrue(g.texts.contains("Alex"));
        v.openAlbum(ScreenshotLibrary.Kind.SERVER, "play.example.net");
        assertEquals(1, v.shown().size());
        assertTrue(v.closeRequested() == false, "Escape: back to the albums");

        // photos: select all, make a new album with them
        v.tab(false);
        v.render(new FakeGraphics(), 854, 480, 0, 0);
        Thread.sleep(220); // typing right after opening is ignored
        v.keyPressed(dev.lumora.api.input.Keys.A, true, false);
        v.startNewAlbum(true);
        for (char c : "Ausflug".toCharArray()) {
            v.charTyped(c);
        }
        v.keyPressed(dev.lumora.api.input.Keys.ENTER, false, false);
        assertEquals(java.util.List.of("Ausflug"), lib.albumNames());
        assertEquals(2, lib.album("Ausflug").shots().size());
        v.removed();
        core.stopping();
    }

    @Test
    void showsThumbnailsAsTexturesAndFreesThemOnClose() throws Exception {
        png("a.png", 640, 360, 1_000);
        png("b.png", 320, 180, 2_000);
        FakePlatform p = platform();
        ScreenshotView v = new ScreenshotView(new LumoraCore(p));
        assertEquals(2, v.shown().size());
        FakeGraphics g = renderUntil(v, p, 2);
        assertEquals(2, p.textures.size(), "two thumbnails uploaded");
        // thumbnails are scaled down to at most 256 x 144
        for (int[] px : p.textures.values()) {
            assertTrue(px.length <= 256 * 144);
        }
        assertEquals(2, g.textures.stream().filter(t -> t.startsWith("lumora:screenshots/")).count());
        assertTrue(g.texts.stream().anyMatch(t -> t.startsWith("2 images") || t.startsWith("2 Bilder")), g.texts.toString());
        assertEquals(0, g.depth);
        v.removed();
        assertTrue(p.textures.isEmpty(), "all textures freed");
    }

    @Test
    void withoutTextureSupportThePageStillWorks() throws Exception {
        png("a.png", 64, 36, 1_000);
        FakePlatform p = platform();
        p.textureSupport = false;
        ScreenshotView v = new ScreenshotView(new LumoraCore(p));
        FakeGraphics g = new FakeGraphics();
        for (int i = 0; i < 30; i++) {
            g = new FakeGraphics();
            v.render(g, 854, 480, 0, 0);
            Thread.sleep(10);
        }
        assertTrue(g.textures.stream().noneMatch(t -> t.startsWith("lumora:screenshots/")), g.textures.toString());
        assertTrue(p.textures.isEmpty());
        assertEquals(0, g.depth);
    }

    @Test
    void typingSearchesAndEscapeLeavesThePreviewBeforeClosing() throws Exception {
        png("Burg.png", 64, 36, 1_000);
        png("Strand.png", 64, 36, 2_000);
        FakePlatform p = platform();
        ScreenshotView v = new ScreenshotView(new LumoraCore(p));
        renderUntil(v, p, 2);
        Thread.sleep(220);
        for (char c : "burg".toCharArray()) {
            v.charTyped(c);
        }
        assertEquals(1, v.shown().size());
        assertFalse(v.closeRequested(), "Esc first leaves the search field");
        assertEquals(1, v.shown().size(), "the search stays");
        // two quick clicks on the card open the preview
        boolean opened = false;
        for (int y = 60; y < 200 && !opened; y += 4) {
            for (int x = 40; x < 300 && !opened; x += 8) {
                v.render(new FakeGraphics(), 854, 480, x, y);
                v.mouseClicked(x, y, 0);
                v.render(new FakeGraphics(), 854, 480, x, y);
                v.mouseClicked(x, y, 0);
                FakeGraphics g = new FakeGraphics();
                v.render(g, 854, 480, x, y);
                opened = g.texts.stream().anyMatch(t -> t.startsWith("Burg.png  ·  "));
            }
        }
        assertTrue(opened, "a double click opens the preview");
        assertFalse(v.closeRequested(), "Esc leaves the preview first");
        assertTrue(v.closeRequested(), "then Esc closes the view");
    }
}
