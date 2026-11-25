package dev.lumora.launcher.app.ui;

import dev.lumora.launcher.core.download.Downloader;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Images from content platforms (icons, gallery pictures), loaded lazily in the background and cached in memory.
 * Only hosts of the launcher's download allow-list are fetched (HTTPS); anything else, a failure or an image that cannot
 * be decoded leaves the caller's placeholder in place. WebP comes through the TwelveMonkeys ImageIO plugin.
 */
public final class RemoteImages {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ENTRIES = 600;

    private final Downloader downloader;
    private final ExecutorService io;
    private final Map<String, ImageIcon> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private final Map<String, List<Consumer<ImageIcon>>> waiting = new LinkedHashMap<>();
    private final java.util.Set<String> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public RemoteImages(Downloader downloader, ExecutorService io) {
        this.downloader = downloader;
        this.io = io;
    }

    /** The cached image, or null. Swing thread. */
    public ImageIcon cached(String url, int w, int h, boolean contain) {
        synchronized (cache) {
            return cache.get(key(url, w, h, contain));
        }
    }

    /**
     * Calls {@code done} on the Swing thread with the image scaled to w x h ({@code contain} letterboxes, otherwise it is
     * cropped to fill); immediately if cached, never if it cannot be loaded.
     */
    public void load(String url, int w, int h, boolean contain, Consumer<ImageIcon> done) {
        if (url == null || url.isBlank()) {
            return;
        }
        String key = key(url, w, h, contain);
        ImageIcon hit;
        synchronized (cache) {
            hit = cache.get(key);
            if (hit == null) {
                if (failed.contains(url)) {
                    return;
                }
                List<Consumer<ImageIcon>> list = waiting.get(key);
                if (list != null) {
                    list.add(done);
                    return;
                }
                list = new ArrayList<>();
                list.add(done);
                waiting.put(key, list);
            }
        }
        if (hit != null) {
            done.accept(hit);
            return;
        }
        io.submit(() -> {
            ImageIcon icon = null;
            try {
                URI uri = URI.create(url);
                downloader.checkTrusted(uri);
                byte[] data = downloader.fetchBytes(uri, MAX_BYTES);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img != null) {
                    icon = new ImageIcon(Ui.fit(img, w, h, contain));
                }
            } catch (IOException | RuntimeException e) {
                // placeholder stays
            }
            ImageIcon result = icon;
            List<Consumer<ImageIcon>> callbacks;
            synchronized (cache) {
                if (result != null) {
                    cache.put(key, result);
                } else {
                    failed.add(url);
                }
                callbacks = waiting.remove(key);
            }
            if (result != null && callbacks != null) {
                SwingUtilities.invokeLater(() -> callbacks.forEach(c -> c.accept(result)));
            }
        });
    }

    private static String key(String url, int w, int h, boolean contain) {
        return w + "x" + h + (contain ? "c" : "f") + "|" + url;
    }
}
