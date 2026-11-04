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
