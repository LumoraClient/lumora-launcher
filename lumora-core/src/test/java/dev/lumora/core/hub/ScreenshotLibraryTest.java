package dev.lumora.core.hub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotLibraryTest {
    @TempDir
    Path tmp;

    private Path shot(String name, int bytes, long modified) throws IOException {
        Path dir = tmp.resolve("screenshots");
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.write(f, new byte[bytes]);
        Files.setLastModifiedTime(f, FileTime.fromMillis(modified));
        return f;
    }

    private ScreenshotLibrary lib() {
        ScreenshotLibrary l = new ScreenshotLibrary(tmp.resolve("screenshots"), tmp.resolve("config/lumora/screenshots.json"));
        l.refresh();
        return l;
    }

    private static List<String> names(List<ScreenshotLibrary.Shot> shots) {
        return shots.stream().map(ScreenshotLibrary.Shot::name).toList();
    }

    @Test
    void ownAlbumsCollectScreenshotsAndKeepThemThroughRenameDeleteAndRestart() throws IOException {
        shot("a.png", 10, 1_000);
        shot("b.png", 10, 2_000);
        shot("c.png", 10, 3_000);
        ScreenshotLibrary l = lib();
        assertEquals("Basis", l.createAlbum("  Basis "));
        assertNull(l.createAlbum("basis"), "names are unique, ignoring case");
        assertNull(l.createAlbum(""));
        assertEquals("exists", l.checkAlbumName("BASIS", null));
        List<ScreenshotLibrary.Shot> ab = l.view("", false, ScreenshotLibrary.Sort.NAME).subList(0, 2);
        assertEquals(2, l.addToAlbum("Basis", ab));
        assertEquals(0, l.addToAlbum("Basis", ab), "already in it");
        assertEquals(List.of("b.png", "a.png"), names(l.album("Basis").shots()), "newest first: the cover is b");
        assertEquals(List.of("b.png", "a.png"), names(l.view("", false, ScreenshotLibrary.Sort.NEWEST, l.album("Basis"))));

        // a renamed screenshot stays in the album, a deleted one leaves it
        ScreenshotLibrary.Shot a = l.all().stream().filter(s -> s.name().equals("a.png")).findFirst().orElseThrow();
        l.rename(a, "Burg");
        assertEquals(List.of("b.png", "Burg.png"), names(l.album("Basis").shots()));
        ScreenshotLibrary.Shot b = l.all().stream().filter(s -> s.name().equals("b.png")).findFirst().orElseThrow();
        l.delete(b, null);
        assertEquals(List.of("Burg.png"), names(l.album("Basis").shots()));

        assertTrue(l.renameAlbum("Basis", "Meine Burg"));
        assertNull(l.album("Basis"));
        ScreenshotLibrary again = lib(); // read back from the file
        assertEquals(List.of("Meine Burg"), again.albumNames());
        assertEquals(List.of("Burg.png"), names(again.album("Meine Burg").shots()));
        assertEquals(1, again.removeFromAlbum("Meine Burg", again.album("Meine Burg").shots()));
        again.deleteAlbum("Meine Burg");
        assertTrue(again.albumNames().isEmpty());
        assertEquals(2, again.all().size(), "deleting an album keeps the images");
    }

    @Test
    void automaticAlbumsGroupByServerPlayerAndAccount() throws IOException {
        shot("1.png", 10, 1_000);
        shot("2.png", 10, 2_000);
        shot("3.png", 10, 3_000);
        shot("old.png", 10, 500);
        ScreenshotLibrary l = lib();
        l.record("1.png", new ScreenshotLibrary.Meta("play.example.net", "Steve", List.of("Alex", "Notch")));
        l.record("2.png", new ScreenshotLibrary.Meta("play.example.net", "Steve", List.of("Alex")));
        l.record("3.png", new ScreenshotLibrary.Meta("", "Herobrine", List.of()));
        List<ScreenshotLibrary.Album> servers = l.autoAlbums(ScreenshotLibrary.Kind.SERVER);
        assertEquals(List.of("play.example.net", ""), servers.stream().map(ScreenshotLibrary.Album::name).toList(), "largest first");
        assertEquals(List.of("2.png", "1.png"), names(servers.get(0).shots()));
        List<ScreenshotLibrary.Album> players = l.autoAlbums(ScreenshotLibrary.Kind.PLAYER);
        assertEquals(List.of("Alex", "Notch"), players.stream().map(ScreenshotLibrary.Album::name).toList());
        assertEquals(2, players.get(0).shots().size());
        assertEquals(List.of("Steve", "Herobrine"), l.autoAlbums(ScreenshotLibrary.Kind.ACCOUNT).stream().map(ScreenshotLibrary.Album::name).toList());
        assertNull(l.meta(l.all().stream().filter(s -> s.name().equals("old.png")).findFirst().orElseThrow()), "taken before Lumora watched");
        assertEquals(List.of("2.png", "1.png"), names(l.view("alex", false, ScreenshotLibrary.Sort.NEWEST)), "the search finds players");
        assertEquals(List.of("3.png"), names(lib().view("herobrine", false, ScreenshotLibrary.Sort.NEWEST)), "kept in the file");
    }

    @Test
    void listsImagesOnlyAndSortsAndFilters() throws IOException {
        shot("2026-11-01_10.00.00.png", 300, 1_000);
        shot("2026-11-03_20.41.07.png", 100, 3_000);
        shot("Burg.jpg", 200, 2_000);
        shot("notes.txt", 50, 4_000);
        Files.createDirectories(tmp.resolve("screenshots/sub.png"));
        ScreenshotLibrary l = lib();
        assertEquals(3, l.all().size());
        assertEquals(600, l.totalBytes());
        assertEquals(List.of("2026-11-03_20.41.07.png", "Burg.jpg", "2026-11-01_10.00.00.png"),
                names(l.view("", false, ScreenshotLibrary.Sort.NEWEST)));
        assertEquals(List.of("2026-11-01_10.00.00.png", "Burg.jpg", "2026-11-03_20.41.07.png"),
                names(l.view("", false, ScreenshotLibrary.Sort.OLDEST)));
        assertEquals("2026-11-01_10.00.00.png", l.view("", false, ScreenshotLibrary.Sort.SIZE).get(0).name());
        assertEquals("Burg.jpg", l.view("", false, ScreenshotLibrary.Sort.NAME).get(2).name());
        assertEquals(List.of("Burg.jpg"), names(l.view("BURG", false, ScreenshotLibrary.Sort.NEWEST)));
    }

    @Test
    void favouritesAreKeptAcrossRestartsAndFollowARename() throws IOException {
        shot("a.png", 10, 1);
        shot("b.png", 10, 2);
        ScreenshotLibrary l = lib();
        ScreenshotLibrary.Shot a = l.view("a", false, ScreenshotLibrary.Sort.NAME).get(0);
        l.setFavorite(a, true);
        assertEquals(List.of("a.png"), names(l.view("", true, ScreenshotLibrary.Sort.NAME)));
        ScreenshotLibrary again = lib();
        assertEquals(1, again.favoriteCount());
        ScreenshotLibrary.Shot renamed = again.rename(again.all().stream().filter(s -> s.name().equals("a.png")).findFirst().orElseThrow(), "Burg am See");
        assertEquals("Burg am See.png", renamed.name());
        assertTrue(Files.exists(tmp.resolve("screenshots/Burg am See.png")));
        assertFalse(Files.exists(tmp.resolve("screenshots/a.png")));
        assertTrue(lib().isFavorite(renamed));
    }

    @Test
    void refusesBadOrTakenNamesAndPathsOutsideTheFolder() throws IOException {
        shot("a.png", 10, 1);
        shot("b.png", 10, 2);
        ScreenshotLibrary l = lib();
        ScreenshotLibrary.Shot a = l.all().stream().filter(s -> s.name().equals("a.png")).findFirst().orElseThrow();
        assertEquals("exists", l.checkName(a, "b"));
        assertEquals("name", l.checkName(a, "../evil"));
        assertEquals("name", l.checkName(a, "a/b"));
        assertEquals("name", l.checkName(a, ""));
        assertEquals("name", l.checkName(a, ".hidden"));
        assertNull(l.checkName(a, "Schöne Aussicht (2)"));
        assertThrows(IOException.class, () -> l.rename(a, "..\\..\\x"));
        assertThrows(IOException.class, () -> l.inside("../config/lumora/screenshots.json"));
        assertNotNull(l.inside("a.png"));
    }

    @Test
    void deleteUsesTheRecycleBinWhenOfferedAndDropsTheFavourite() throws IOException {
        shot("a.png", 10, 1);
        shot("b.png", 10, 2);
        ScreenshotLibrary l = lib();
        ScreenshotLibrary.Shot a = l.all().stream().filter(s -> s.name().equals("a.png")).findFirst().orElseThrow();
        l.setFavorite(a, true);
        Path[] trashed = new Path[1];
        l.delete(a, f -> {
            trashed[0] = f;
            try {
                Files.delete(f);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return true;
        });
        assertEquals(tmp.resolve("screenshots/a.png").normalize(), trashed[0]);
        assertEquals(1, l.all().size());
        assertEquals(0, l.favoriteCount());
        // no recycle bin: deleted for real
        l.delete(l.all().get(0), f -> false);
        assertTrue(l.all().isEmpty());
    }

    @Test
    void aBrokenFavouritesFileIsIgnored() throws IOException {
        shot("a.png", 10, 1);
        Files.createDirectories(tmp.resolve("config/lumora"));
        Files.writeString(tmp.resolve("config/lumora/screenshots.json"), "{ kaputt");
        ScreenshotLibrary l = lib();
        assertEquals(0, l.favoriteCount());
        l.setFavorite(l.all().get(0), true);
        assertEquals(1, lib().favoriteCount());
    }
}
