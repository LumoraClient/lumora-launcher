package dev.lumora.core.hub;

import dev.lumora.api.i18n.Translations;
import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.theme.LumoraTheme;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Screenshot manager in the hub's design (main menu and pause menu, camera button): the game's screenshots as a grid
 * of thumbnails with search, favourites filter and sorting; a large preview with arrow keys; open in the system
 * viewer, show in the folder, favourite, rename and delete (with a question first). Thumbnails are decoded off the
 * render thread and uploaded as textures through {@link dev.lumora.core.Platform#uploadTexture}; only the visible ones
 * are kept (least recently used are freed), all are freed when the view closes.
 */
public final class ScreenshotView implements AddonView {
    static final int TEXT = HubView.TEXT;
    static final int MUTED = HubView.MUTED;
    static final int DIM = HubView.DIM;
    private static final int THUMB_W = 256;
    private static final int THUMB_H = 144;
    private static final int PREVIEW_MAX_W = 1600;
    private static final int PREVIEW_MAX_H = 900;
    private static final int MAX_THUMBS = 48;
    private static final int UPLOADS_PER_FRAME = 3;
    private static final long MAX_FILE_BYTES = 64L << 20;
    private static final ExecutorService DECODER = decoder();
    private static int textureSeq;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    /** A decoded image and, once uploaded, its texture. */
    private static final class Tex {
        final String texPath;
        volatile int[] pixels;
        volatile int width;
        volatile int height;
        /** Size of the image file in pixels (before scaling). */
        volatile int sourceWidth;
        volatile int sourceHeight;
        volatile boolean failed;
        boolean uploaded;
        long used;

        Tex(String texPath) {
            this.texPath = texPath;
        }
    }

    private final LumoraCore core;
    private final ScreenshotLibrary lib;
    private final boolean de;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Tex> thumbs = new LinkedHashMap<>();
    private final Set<String> selected = new HashSet<>();
    private List<ScreenshotLibrary.Shot> shown = List.of();
    private String search = "";
    private boolean favoritesOnly;
    private ScreenshotLibrary.Sort sort = ScreenshotLibrary.Sort.NEWEST;
    /** Focused text field: "search" or "rename"; null if none. */
    private String focus;
    private final StringBuilder buffer = new StringBuilder();
    private ScreenshotLibrary.Shot preview;
    private Tex previewTex;
    private String previewKey;
    private boolean confirmDelete;
    private boolean renaming;
    private int anchor = -1;
    private long openedAt;
    private float scroll;
    private float scrollTarget;
    private int maxScroll;
    private int contentTop;
    private int contentBottom;
    private boolean clipHits;
    private String lastClick;
    private long lastClickAt;
    private String toast = "";
    private long toastAt;
    private boolean textures = true;

    public ScreenshotView(LumoraCore core) {
        this.core = core;
        Path game = core.platform().configDir().getParent();
        this.lib = new ScreenshotLibrary(game.resolve("screenshots"), core.platform().configDir().resolve("lumora").resolve("screenshots.json"));
        Translations tr = core.translations();
        String lang = tr == null ? "" : tr.language();
        this.de = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
        reload();
    }

    /** Opens the screenshot manager on top of the current screen (main menu, pause menu, hub). */
    public static void open(LumoraCore core) {
        core.platform().views().show(new ScreenshotView(core));
    }

    private static ExecutorService decoder() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Lumora screenshots");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    private String t(String german, String english) {
        return de ? german : english;
    }

    // ------------------------------------------------------------------ data

    private void reload() {
        lib.refresh();
        update();
    }

    private void update() {
        shown = lib.view(search, favoritesOnly, sort);
        Set<String> names = new HashSet<>();
        for (ScreenshotLibrary.Shot s : shown) {
            names.add(s.name());
        }
        selected.retainAll(names);
    }

    List<ScreenshotLibrary.Shot> shown() {
        return shown;
    }

    private List<ScreenshotLibrary.Shot> selection() {
        if (preview != null) {
            return List.of(preview);
        }
        List<ScreenshotLibrary.Shot> out = new ArrayList<>();
        for (ScreenshotLibrary.Shot s : shown) {
            if (selected.contains(s.name())) {
                out.add(s);
            }
        }
        return out;
    }

    private static String key(ScreenshotLibrary.Shot s) {
        return s.name() + "@" + s.modified() + ":" + s.size();
    }

    /** The thumbnail texture of a shot, starting to decode it on first use; null until it can be drawn. */
    private Tex thumb(ScreenshotLibrary.Shot s) {
        String k = key(s);
        Tex t = thumbs.get(k);
        if (t == null) {
            t = new Tex("screenshots/t" + (++textureSeq));
            thumbs.put(k, t);
            Tex job = t;
            Path file = s.file();
            DECODER.execute(() -> decode(job, file, THUMB_W, THUMB_H));
            evict();
        }
        t.used = System.nanoTime();
        return t;
    }

    private void evict() {
        while (thumbs.size() > MAX_THUMBS) {
            String oldest = null;
            long best = Long.MAX_VALUE;
            for (Map.Entry<String, Tex> e : thumbs.entrySet()) {
                if (e.getValue().used < best) {
                    best = e.getValue().used;
                    oldest = e.getKey();
                }
            }
            Tex gone = thumbs.remove(oldest);
            free(gone);
        }
    }

    private void free(Tex t) {
        if (t != null && t.uploaded) {
            core.platform().releaseTexture(t.texPath);
            t.uploaded = false;
        }
    }

    /** Decodes and scales the image to fit {@code maxW x maxH} (keeping its shape). Background thread. */
    static void decode(Tex t, Path file, int maxW, int maxH) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                t.failed = true;
                return;
            }
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                t.failed = true;
                return;
            }
            t.sourceWidth = img.getWidth();
            t.sourceHeight = img.getHeight();
            double f = Math.min(1.0, Math.min(maxW / (double) img.getWidth(), maxH / (double) img.getHeight()));
            int w = Math.max(1, (int) Math.round(img.getWidth() * f));
            int h = Math.max(1, (int) Math.round(img.getHeight() * f));
            BufferedImage src = img;
            // halve in steps for a smooth result, then the last step
            while (src.getWidth() / 2 >= w && src.getHeight() / 2 >= h) {
                src = scaled(src, src.getWidth() / 2, src.getHeight() / 2);
            }
            BufferedImage out = src.getWidth() == w && src.getHeight() == h ? src : scaled(src, w, h);
            int[] px = new int[w * h];
            out.getRGB(0, 0, w, h, px, 0, w);
            for (int i = 0; i < px.length; i++) {
                px[i] |= 0xFF000000;
            }
            t.width = w;
            t.height = h;
            t.pixels = px;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            t.failed = true;
        }
    }

    private static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    /** Uploads decoded images (a few per frame, so opening the view never stalls). Render thread. */
    private void upload() {
        int budget = UPLOADS_PER_FRAME;
        if (previewTex != null && !previewTex.uploaded && previewTex.pixels != null) {
            uploadOne(previewTex);
            budget--;
        }
        for (Tex t : thumbs.values()) {
            if (budget <= 0) {
                break;
            }
            if (!t.uploaded && t.pixels != null && !t.failed) {
                uploadOne(t);
                budget--;
            }
        }
    }

    private void uploadOne(Tex t) {
        if (textures && core.platform().uploadTexture(t.texPath, t.width, t.height, t.pixels)) {
            t.uploaded = true;
            t.pixels = null;
        } else {
            textures = false;
            t.failed = true;
        }
    }

    private void showPreview(ScreenshotLibrary.Shot s) {
        preview = s;
        confirmDelete = false;
        renaming = false;
        focus = null;
        String k = key(s);
        if (!k.equals(previewKey)) {
            free(previewTex);
            previewKey = k;
            Tex t = new Tex("screenshots/p" + (++textureSeq));
            previewTex = t;
            DECODER.execute(() -> decode(t, s.file(), PREVIEW_MAX_W, PREVIEW_MAX_H));
        }
    }

    private void closePreview() {
        preview = null;
        confirmDelete = false;
        renaming = false;
        focus = null;
    }

    private void step(int dir) {
        if (preview == null || shown.isEmpty()) {
            return;
        }
        int i = indexOf(preview);
        int n = Math.floorMod((i < 0 ? 0 : i) + dir, shown.size());
        selected.clear();
        selected.add(shown.get(n).name());
        showPreview(shown.get(n));
    }

    private int indexOf(ScreenshotLibrary.Shot s) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).name().equals(s.name())) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ actions

    private void toast(String message) {
        toast = message == null ? "" : message;
        toastAt = System.currentTimeMillis();
    }

    private void toggleFavorite() {
        List<ScreenshotLibrary.Shot> sel = selection();
        if (sel.isEmpty()) {
            return;
        }
        boolean on = sel.stream().anyMatch(s -> !lib.isFavorite(s));
        for (ScreenshotLibrary.Shot s : sel) {
            lib.setFavorite(s, on);
        }
        update();
        toast(on ? t("Als Favorit markiert", "Marked as favourite") : t("Favorit entfernt", "Favourite removed"));
    }

    private void startRename() {
        List<ScreenshotLibrary.Shot> sel = selection();
        if (sel.size() != 1) {
            return;
        }
        renaming = true;
        confirmDelete = false;
        focus = "rename";
        buffer.setLength(0);
        buffer.append(ScreenshotLibrary.baseName(sel.get(0).name()));
    }

    private void commitRename() {
        List<ScreenshotLibrary.Shot> sel = selection();
        String name = buffer.toString();
        renaming = false;
        focus = null;
        if (sel.size() != 1) {
            return;
        }
        ScreenshotLibrary.Shot s = sel.get(0);
        String problem = lib.checkName(s, name);
        if (problem != null) {
            toast(problem.equals("exists") ? t("Diesen Namen gibt es schon", "That name already exists")
                    : t("Ungültiger Name (Buchstaben, Ziffern, Leerzeichen, - _ . ( ))", "Invalid name (letters, digits, space, - _ . ( ))"));
            return;
        }
        try {
            ScreenshotLibrary.Shot renamed = lib.rename(s, name);
            update();
            selected.clear();
            selected.add(renamed.name());
            if (preview != null) {
                preview = renamed;
                previewKey = key(renamed);
            }
            toast(t("Umbenannt in ", "Renamed to ") + renamed.name());
        } catch (IOException e) {
            toast(t("Umbenennen fehlgeschlagen", "Rename failed"));
        }
    }

    private void askDelete() {
        if (!selection().isEmpty()) {
            confirmDelete = true;
            renaming = false;
            focus = null;
        }
    }

    private void delete() {
        List<ScreenshotLibrary.Shot> sel = selection();
        confirmDelete = false;
        int done = 0;
        int next = preview != null ? indexOf(preview) : -1;
        for (ScreenshotLibrary.Shot s : sel) {
            try {
                lib.delete(s, OsFiles::moveToTrash);
                done++;
            } catch (IOException e) {
                toast(t("Löschen fehlgeschlagen: ", "Delete failed: ") + s.name());
            }
        }
        update();
        selected.clear();
        if (preview != null) {
            if (shown.isEmpty()) {
                closePreview();
            } else {
                ScreenshotLibrary.Shot n = shown.get(Math.min(Math.max(0, next), shown.size() - 1));
                selected.add(n.name());
                showPreview(n);
            }
        }
        if (done > 0) {
            toast(done == 1 ? t("Screenshot gelöscht", "Screenshot deleted") : done + t(" Screenshots gelöscht", " screenshots deleted"));
        }
    }

    private void openExternal() {
        List<ScreenshotLibrary.Shot> sel = selection();
        if (sel.size() == 1) {
            OsFiles.open(sel.get(0).file());
        }
    }

    private void showInFolder() {
        List<ScreenshotLibrary.Shot> sel = selection();
        if (sel.size() == 1) {
            OsFiles.reveal(sel.get(0).file());
        } else {
            try {
                Files.createDirectories(lib.directory());
            } catch (IOException e) {
                // the folder opens anyway if it exists
            }
            OsFiles.open(lib.directory());
        }
    }

    /** Click on a card: select (Ctrl adds, Shift a range), a second click soon after opens the preview. */
    private void clickCard(int index, boolean ctrl, boolean shift) {
        ScreenshotLibrary.Shot s = shown.get(index);
        long now = System.currentTimeMillis();
        boolean twice = s.name().equals(lastClick) && now - lastClickAt < 350;
        lastClick = s.name();
        lastClickAt = now;
        confirmDelete = false;
        renaming = false;
        if (twice && !ctrl && !shift) {
            showPreview(s);
            return;
        }
        if (shift && anchor >= 0 && anchor < shown.size()) {
            selected.clear();
            for (int i = Math.min(anchor, index); i <= Math.max(anchor, index); i++) {
                selected.add(shown.get(i).name());
            }
        } else if (ctrl) {
            if (!selected.remove(s.name())) {
                selected.add(s.name());
            }
            anchor = index;
        } else {
            selected.clear();
            selected.add(s.name());
            anchor = index;
        }
    }

    // ------------------------------------------------------------------ drawing

    private void hit(int x, int y, int w, int h, Runnable action) {
        if (clipHits && (y + h <= contentTop || y >= contentBottom)) {
            return;
        }
        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mx, int my) {
        hits.clear();
        long now = System.currentTimeMillis();
        if (openedAt == 0) {
            openedAt = now;
        }
        upload();
        float p = Math.min(1f, (now - openedAt) / 200f);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        HubView.background(g, width, height, e, core.platform().game().inWorld());

        int margin = width >= 560 ? 16 : 6;
        int pw = Math.min(width - 2 * margin, 820);
        int ph = Math.min(height - 2 * margin, 480);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        int accent = LumoraTheme.accent();
        g.push();
        float s = 0.97f + 0.03f * e;
        g.translate(width / 2f, height / 2f + (1f - e) * 5f);
        g.scale(s);
        g.translate(-width / 2f, -height / 2f);
        for (int i = 3; i >= 1; i--) {
            g.roundedRect(px - i * 2, py - i * 2, pw + i * 4, ph + i * 4, 8 + i * 2, LumoraTheme.alpha(accent, (int) (10 * e / i)));
        }
        g.roundedRect(px - 1, py - 1, pw + 2, ph + 2, 8, HubView.BORDER);
        g.roundedRect(px, py, pw, ph, 7, HubView.MAIN);

        int pad = 12;
        int left = px + pad;
        int right = px + pw - pad;
        int y = py + 10;
        header(g, left, right, y, mx, my);
        y += 22;
        toolbar(g, left, right, y, mx, my);
        y += 18;
        g.fill(px, y, px + pw, y + 1, LumoraTheme.alpha(accent, 0xB0));
        contentTop = y + 6;
        int footerH = 22;
        contentBottom = py + ph - footerH - 4;
        if (preview != null) {
            previewArea(g, px + 6, contentTop, pw - 12, contentBottom - contentTop, mx, my);
        } else {
            grid(g, left, right, mx, my);
        }
        footer(g, left, right, py + ph - footerH, footerH, mx, my);
        if (!toast.isEmpty() && now - toastAt < 2500) {
            int tw = g.textWidth(toast) + 16;
            g.card(px + (pw - tw) / 2, contentBottom - 22, tw, 16, 8, 0xF0201B2E, accent);
            g.centeredText(toast, px + pw / 2, contentBottom - 18, TEXT, false);
        }
        g.pop();
    }

    private void header(HudGraphics g, int left, int right, int y, int mx, int my) {
        int accent = LumoraTheme.accent();
        boolean backHover = HubView.in(mx, my, left, y, 16, 16);
        g.roundedRect(left, y, 16, 16, 8, backHover ? accent : 0x26FFFFFF);
        HubGlyphs.draw(g, "back", left + 4, y + 4, backHover ? 0xFFFFFFFF : TEXT);
        hit(left, y, 16, 16, () -> {
            if (preview != null) {
                closePreview();
            } else {
                close();
            }
        });
        HubGlyphs.draw(g, "camera", left + 24, y + 4, LumoraTheme.accentHover());
        g.push();
        g.translate(left + 38, y + 2);
        g.scale(1.5f);
        g.text("§l" + t("Screenshots", "Screenshots"), 0, 0, TEXT, false);
        g.pop();
        // folder and refresh on the right, search left of them
        int ix = right - 16;
        iconButton(g, ix, y, "folder", t("Ordner öffnen", "Open folder"), mx, my, this::showInFolder);
        ix -= 20;
        iconButton(g, ix, y, "reset", t("Neu laden", "Refresh"), mx, my, () -> {
            reload();
            toast(t("Neu geladen", "Refreshed"));
        });
        int sbw = Math.max(80, Math.min(150, (right - left) / 4));
        searchBox(g, ix - 6 - sbw, y, sbw, 16, mx, my);
    }

    private void close() {
        core.platform().views().close(this);
    }

    private void iconButton(HudGraphics g, int x, int y, String glyph, String hint, int mx, int my, Runnable action) {
        boolean hover = HubView.in(mx, my, x, y, 16, 16);
        if (hover) {
            g.roundedRect(x, y, 16, 16, 4, 0x26FFFFFF);
        }
        HubGlyphs.draw(g, glyph, x + 4, y + 4, hover ? TEXT : MUTED);
        hit(x, y, 16, 16, action);
    }

    private void searchBox(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean focused = "search".equals(focus);
        boolean hover = HubView.in(mx, my, x, y, w, h);
        g.card(x, y, w, h, h / 2, HubView.FIELD, focused ? LumoraTheme.accent() : hover ? 0xFF3A3450 : HubView.BORDER);
        HubGlyphs.draw(g, "search", x + 5, y + (h - HubGlyphs.SIZE) / 2, focused ? LumoraTheme.accentHover() : DIM);
        String text = focused ? buffer.toString() : search;
        int max = w - 24;
        while (g.textWidth(text) > max && !text.isEmpty()) {
            text = text.substring(1);
        }
        if (text.isEmpty() && !focused) {
            g.text(HubView.trim(g, t("Suchen…", "Search…"), max), x + 17, y + 4, DIM, false);
        } else {
            g.text(text, x + 17, y + 4, TEXT, false);
            caret(g, focused, x + 17 + g.textWidth(text), y + 3, y + h - 3);
        }
        hit(x, y, w, h, () -> focus("search", search));
    }

    private static void caret(HudGraphics g, boolean focused, int x, int y1, int y2) {
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            g.fill(x, y1, x + 1, y2, TEXT);
        }
    }

    private void focus(String field, String value) {
        if (renaming && !"rename".equals(field)) {
            renaming = false;
        }
        focus = field;
        buffer.setLength(0);
        buffer.append(value);
    }

    /** Counts on the left, filter and sort chips on the right. */
    private void toolbar(HudGraphics g, int left, int right, int y, int mx, int my) {
        String info = lib.all().size() + (lib.all().size() == 1 ? t(" Bild", " image") : t(" Bilder", " images"))
                + "  ·  " + size(lib.totalBytes()) + "  ·  " + lib.favoriteCount() + " ";
        g.text(info, left, y + 3, MUTED, false);
        HubGlyphs.draw(g, "star", left + g.textWidth(info), y + 2, LumoraTheme.accentHover());
        int x = right;
        String sortLabel = switch (sort) {
            case NEWEST -> t("Neueste zuerst", "Newest first");
            case OLDEST -> t("Älteste zuerst", "Oldest first");
            case NAME -> t("Name", "Name");
            case SIZE -> t("Größte zuerst", "Largest first");
        };
        x = chip(g, x, y, t("Sortierung: ", "Sort: ") + sortLabel, false, mx, my, () -> {
            sort = ScreenshotLibrary.Sort.values()[(sort.ordinal() + 1) % ScreenshotLibrary.Sort.values().length];
            update();
        });
        x = chip(g, x - 6, y, t("Favoriten", "Favourites"), favoritesOnly, mx, my, () -> {
            favoritesOnly = true;
            update();
        });
        chip(g, x - 4, y, t("Alle", "All"), !favoritesOnly, mx, my, () -> {
            favoritesOnly = false;
            update();
        });
    }

    /** A chip ending at {@code right}; returns its left edge. */
    private int chip(HudGraphics g, int right, int y, String label, boolean on, int mx, int my, Runnable action) {
        int w = g.textWidth(label) + 14;
        int x = right - w;
        boolean hover = HubView.in(mx, my, x, y, w, 14);
        int accent = LumoraTheme.accent();
        g.card(x, y, w, 14, 7, on ? LumoraTheme.alpha(accent, 0x60) : hover ? 0x22FFFFFF : 0x12FFFFFF, on ? accent : HubView.BORDER);
        g.text(label, x + 7, y + 3, on || hover ? TEXT : MUTED, false);
        hit(x, y, w, 14, action);
        return x;
    }

    private void grid(HudGraphics g, int left, int right, int mx, int my) {
        int w = right - left - 6;
        if (shown.isEmpty()) {
            String msg = lib.all().isEmpty() ? t("Noch keine Screenshots. Drück im Spiel F2, um einen aufzunehmen.",
                    "No screenshots yet. Press F2 in game to take one.")
                    : favoritesOnly ? t("Keine Favoriten. Markiere Bilder mit dem Stern.", "No favourites. Mark images with the star.")
                    : t("Nichts gefunden", "Nothing found");
            g.centeredText(msg, left + w / 2, contentTop + 30, MUTED, false);
            maxScroll = 0;
            return;
        }
        int gap = 8;
        int cols = Math.max(2, Math.min(6, (w + gap) / (128 + gap)));
        int cw = (w - (cols - 1) * gap) / cols;
        int thumbH = cw * 9 / 16;
        int ch = thumbH + 26;
        int rows = (shown.size() + cols - 1) / cols;
        int used = rows * ch + (rows - 1) * gap;
        int view = contentBottom - contentTop;
        maxScroll = Math.max(0, used - view);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * 0.4f;
        if (Math.abs(scrollTarget - scroll) < 0.5f) {
            scroll = scrollTarget;
        }
        g.enableScissor(left - 2, contentTop - 1, right, contentBottom);
        clipHits = true;
        int accent = LumoraTheme.accent();
        int top = contentTop - Math.round(scroll);
        for (int i = 0; i < shown.size(); i++) {
            int cx = left + (i % cols) * (cw + gap);
            int cy = top + (i / cols) * (ch + gap);
            if (cy + ch < contentTop || cy > contentBottom) {
                continue;
            }
            ScreenshotLibrary.Shot shot = shown.get(i);
            boolean sel = selected.contains(shot.name());
            boolean hover = my >= contentTop && my < contentBottom && HubView.in(mx, my, cx, cy, cw, ch);
            g.card(cx, cy, cw, ch, 6, hover ? HubView.CARD_HOVER : HubView.CARD, sel ? accent : hover ? 0xFF3A3450 : HubView.BORDER);
            if (sel) {
                g.outline(cx + 1, cy + 1, cw - 2, ch - 2, LumoraTheme.alpha(accent, 0x90));
            }
            image(g, thumb(shot), cx + 4, cy + 4, cw - 8, thumbH - 4);
            g.text(HubView.trim(g, ScreenshotLibrary.baseName(shot.name()), cw - (lib.isFavorite(shot) ? 22 : 10)), cx + 5, cy + thumbH + 4,
                    sel || hover ? TEXT : 0xFFCFC9DC, false);
            g.text(date(shot.modified()) + "  ·  " + size(shot.size()), cx + 5, cy + thumbH + 14, DIM, false);
            if (lib.isFavorite(shot)) {
                HubGlyphs.draw(g, "star", cx + cw - 14, cy + thumbH + 4, LumoraTheme.accentHover());
            }
            int index = i;
            hit(cx, cy, cw, ch, () -> clickCard(index, down(Keys.LEFT_CONTROL) || down(Keys.RIGHT_CONTROL),
                    down(Keys.LEFT_SHIFT) || down(Keys.RIGHT_SHIFT)));
        }
        clipHits = false;
        g.disableScissor();
        if (maxScroll > 0) {
            int trackH = view;
            int barH = Math.max(16, trackH * trackH / Math.max(1, used));
            int barY = contentTop + Math.round((trackH - barH) * (scroll / maxScroll));
            g.roundedRect(right - 2, contentTop, 2, trackH, 1, 0x18FFFFFF);
            g.roundedRect(right - 2, barY, 2, barH, 1, LumoraTheme.alpha(accent, 0xC0));
        }
    }

    /** Draws a texture fitted into the box (centred, shape kept), or a placeholder while it loads. */
    private void image(HudGraphics g, Tex t, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0B0A10);
        if (t != null && t.uploaded && t.width > 0) {
            double f = Math.min(w / (double) t.width, h / (double) t.height);
            int dw = Math.max(1, (int) Math.round(t.width * f));
            int dh = Math.max(1, (int) Math.round(t.height * f));
            g.texture(new TextureRef("lumora", t.texPath), x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 0, 0, t.width, t.height, t.width, t.height);
            return;
        }
        boolean broken = t != null && t.failed;
        HubGlyphs.draw(g, "camera", x + (w - HubGlyphs.SIZE) / 2, y + (h - HubGlyphs.SIZE) / 2 - (broken ? 5 : 0), broken ? 0xFF8A4050 : DIM);
        if (broken) {
            String m = textures ? t("Nicht lesbar", "Unreadable") : t("Keine Vorschau", "No preview");
            g.centeredText(HubView.trim(g, m, w - 4), x + w / 2, y + h / 2 + 4, DIM, false);
        }
    }

    private void previewArea(HudGraphics g, int x, int y, int w, int h, int mx, int my) {
        ScreenshotLibrary.Shot s = preview;
        String res = previewTex != null && previewTex.sourceWidth > 0 ? previewTex.sourceWidth + " × " + previewTex.sourceHeight + "  ·  " : "";
        String info = s.name() + "  ·  " + res
                + date(s.modified()) + "  ·  " + size(s.size());
        g.centeredText(HubView.trim(g, info, w - 20), x + w / 2, y, MUTED, false);
        int iy = y + 12;
        int ih = h - 14;
        int arrows = 22;
        image(g, previewTex, x + arrows, iy, w - 2 * arrows, ih);
        if (lib.isFavorite(s)) {
            HubGlyphs.draw(g, "star", x + w - arrows - 14, iy + 5, LumoraTheme.accentHover());
        }
        if (shown.size() > 1) {
            arrow(g, x + 2, iy + ih / 2 - 10, "back", mx, my, () -> step(-1));
            arrow(g, x + w - 20, iy + ih / 2 - 10, "next", mx, my, () -> step(1));
        }
        int i = indexOf(s);
        if (i >= 0) {
            String pos = (i + 1) + " / " + shown.size();
            g.rightText(pos, x + w - arrows, y, DIM, false);
        }
    }

    private void arrow(HudGraphics g, int x, int y, String glyph, int mx, int my, Runnable action) {
        boolean hover = HubView.in(mx, my, x, y, 18, 20);
        g.roundedRect(x, y, 18, 20, 5, hover ? LumoraTheme.accent() : 0x26FFFFFF);
        HubGlyphs.draw(g, glyph, x + 5, y + 6, hover ? 0xFFFFFFFF : TEXT);
        hit(x, y, 18, 20, action);
    }

    private void footer(HudGraphics g, int left, int right, int y, int h, int mx, int my) {
        g.fill(left - 12, y - 3, right + 12, y - 2, HubView.BORDER);
        int by = y + 2;
        List<ScreenshotLibrary.Shot> sel = selection();
        if (confirmDelete) {
            String q = sel.size() == 1 ? t("„", "\"") + sel.get(0).name() + t("“ löschen?", "\" delete?")
                    : sel.size() + t(" Screenshots löschen?", " screenshots - delete?");
            g.text(HubView.trim(g, q + " " + (OsFiles.trashAvailable() ? t("(in den Papierkorb)", "(to the recycle bin)")
                    : t("(endgültig)", "(permanently)")), right - left - 170), left, by + 4, TEXT, false);
            int x = button(g, right, by, t("Abbrechen", "Cancel"), false, false, mx, my, () -> confirmDelete = false);
            button(g, x - 6, by, t("Löschen", "Delete"), true, false, mx, my, this::delete);
            return;
        }
        if (renaming) {
            int fw = Math.max(80, right - left - 190);
            boolean focused = "rename".equals(focus);
            g.card(left, by, fw, 16, 8, HubView.FIELD, focused ? LumoraTheme.accent() : HubView.BORDER);
            String text = buffer.toString();
            String ext = sel.size() == 1 ? ScreenshotLibrary.extension(sel.get(0).name()) : "";
            int max = fw - 16 - g.textWidth(ext);
            String visible = text;
            while (g.textWidth(visible) > max && !visible.isEmpty()) {
                visible = visible.substring(1);
            }
            g.text(visible, left + 8, by + 4, TEXT, false);
            caret(g, focused, left + 8 + g.textWidth(visible), by + 3, by + 13);
            g.text(ext, left + 9 + g.textWidth(visible), by + 4, DIM, false);
            hit(left, by, fw, 16, () -> focus = "rename");
            int x = button(g, right, by, t("Abbrechen", "Cancel"), false, false, mx, my, () -> {
                renaming = false;
                focus = null;
            });
            button(g, x - 6, by, t("Speichern", "Save"), true, false, mx, my, this::commitRename);
            return;
        }
        boolean any = !sel.isEmpty();
        boolean one = sel.size() == 1;
        int x = right;
        x = button(g, x, by, t("Löschen", "Delete"), false, !any, mx, my, this::askDelete) - 5;
        x = button(g, x, by, t("Umbenennen", "Rename"), false, !one, mx, my, this::startRename) - 5;
        boolean allFav = any && sel.stream().allMatch(lib::isFavorite);
        x = button(g, x, by, allFav ? t("Kein Favorit", "Unfavourite") : t("Favorit", "Favourite"), false, !any, mx, my, this::toggleFavorite) - 5;
        x = button(g, x, by, t("Im Ordner zeigen", "Show in folder"), false, false, mx, my, this::showInFolder) - 5;
        x = button(g, x, by, t("Extern öffnen", "Open externally"), false, !one, mx, my, this::openExternal) - 5;
        if (preview == null) {
            button(g, x, by, t("Ansehen", "View"), true, !one, mx, my, () -> showPreview(sel.get(0)));
        }
        String hint = preview != null ? t("← → blättern  ·  Esc zurück", "← → browse  ·  Esc back")
                : any ? sel.size() + t(" ausgewählt", " selected")
                : t("Klick: auswählen  ·  Strg/Umschalt: mehrere  ·  Doppelklick: ansehen", "Click: select  ·  Ctrl/Shift: several  ·  Double-click: view");
        int room = x - left - (preview == null ? 70 : 8);
        if (room > 40) {
            g.text(HubView.trim(g, hint, room), left, by + 4, DIM, false);
        }
    }

    /** A footer button ending at {@code right}; returns its left edge. */
    private int button(HudGraphics g, int right, int y, String label, boolean primary, boolean disabled, int mx, int my, Runnable action) {
        int w = g.textWidth(label) + 14;
        int x = right - w;
        boolean hover = !disabled && HubView.in(mx, my, x, y, w, 16);
        int accent = LumoraTheme.accent();
        int fill = disabled ? 0x0CFFFFFF : primary ? (hover ? LumoraTheme.accentHover() : accent) : hover ? 0x2AFFFFFF : 0x16FFFFFF;
        g.card(x, y, w, 16, 5, fill, disabled ? 0x18FFFFFF : primary ? accent : HubView.BORDER);
        g.text(label, x + 7, y + 4, disabled ? DIM : primary ? 0xFFFFFFFF : hover ? TEXT : 0xFFCFC9DC, false);
        if (!disabled) {
            hit(x, y, w, 16, action);
        }
        return x;
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.GERMANY, "%.0f KB", Math.max(1, bytes / 1024.0));
        }
        return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String date(long millis) {
        return new SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY).format(new Date(millis));
    }

    // ------------------------------------------------------------------ input

    private boolean down(int key) {
        try {
            return core.platform().keys().isDown(key);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) {
            return false;
        }
        if ("search".equals(focus)) {
            commitSearch();
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                hits.get(i).action().run();
                return true;
            }
        }
        if (!renaming) {
            focus = null;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (preview != null) {
            step(delta > 0 ? -1 : 1);
            return true;
        }
        scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - delta * 30));
        return true;
    }

    private void commitSearch() {
        search = buffer.toString();
        focus = null;
        update();
    }

    @Override
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        if (key == Keys.LEFT_CONTROL || key == Keys.RIGHT_CONTROL || key == Keys.LEFT_SHIFT || key == Keys.RIGHT_SHIFT) {
            return false;
        }
        if (focus != null) {
            if (key == Keys.BACKSPACE) {
                if (ctrl) {
                    buffer.setLength(0);
                } else if (!buffer.isEmpty()) {
                    buffer.setLength(buffer.length() - 1);
                }
                if ("search".equals(focus)) {
                    search = buffer.toString();
                    update();
                }
                return true;
            }
            if (key == Keys.ENTER) {
                if ("rename".equals(focus)) {
                    commitRename();
                } else {
                    commitSearch();
                }
                return true;
            }
            if (key == Keys.ESCAPE) {
                return false;
            }
            return true;
        }
        if (ctrl && key == Keys.F) {
            focus("search", search);
            return true;
        }
        if (ctrl && key == Keys.A && preview == null) {
            for (ScreenshotLibrary.Shot s : shown) {
                selected.add(s.name());
            }
            return true;
        }
        if (confirmDelete && key == Keys.ENTER) {
            delete();
            return true;
        }
        if (key == Keys.DELETE) {
            askDelete();
            return true;
        }
        if (preview != null) {
            if (key == Keys.LEFT) {
                step(-1);
                return true;
            }
            if (key == Keys.RIGHT) {
                step(1);
                return true;
            }
        } else if (key == Keys.ENTER && selection().size() == 1) {
            showPreview(selection().get(0));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch) {
        if (ch < 32 || ch == 127 || Character.isISOControl(ch) || System.currentTimeMillis() - openedAt < 200) {
            return focus != null;
        }
        if (focus == null) {
            if (ch == ' ' || preview != null || renaming || confirmDelete) {
                return false;
            }
            // typing starts the search
            focus("search", search);
        }
        if (buffer.length() < 100) {
            buffer.append(ch);
        }
        if ("search".equals(focus)) {
            search = buffer.toString();
            update();
        }
        return true;
    }

    /** Escape: first leaves a field, the question, the rename or the preview, then closes the view. */
    @Override
    public boolean closeRequested() {
        if (focus != null) {
            if ("search".equals(focus)) {
                commitSearch();
            } else {
                focus = null;
                renaming = false;
            }
            return false;
        }
        if (confirmDelete || renaming) {
            confirmDelete = false;
            renaming = false;
            return false;
        }
        if (preview != null) {
            closePreview();
            return false;
        }
        return true;
    }

    @Override
    public void removed() {
        for (Tex t : thumbs.values()) {
            free(t);
        }
        thumbs.clear();
        free(previewTex);
        previewTex = null;
    }
}
