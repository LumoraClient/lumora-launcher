package dev.lumora.launcher.core.platform;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Small thread-safe answer cache for API calls: entries expire after their time to live, the least recently used ones
 * go first when the cache is full. Failed calls are not cached, so the next try asks again.
 */
public final class ApiCache {
    @FunctionalInterface
    public interface Loader {
        String load() throws IOException;
    }

    private record Item(String value, long expires) {
    }

    private final int maxEntries;
    private final LongSupplier clock;
    private final Map<String, Item> entries;

    public ApiCache(int maxEntries) {
        this(maxEntries, System::currentTimeMillis);
    }

    ApiCache(int maxEntries, LongSupplier clock) {
        this.maxEntries = maxEntries;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Item> eldest) {
                return size() > ApiCache.this.maxEntries;
            }
        };
    }

    public String get(String key, Duration ttl, Loader loader) throws IOException {
        long now = clock.getAsLong();
        synchronized (entries) {
            Item e = entries.get(key);
            if (e != null && e.expires > now) {
                return e.value;
            }
        }
        String value = loader.load();
        synchronized (entries) {
            entries.put(key, new Item(value, now + ttl.toMillis()));
        }
        return value;
    }

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    int size() {
        synchronized (entries) {
            return entries.size();
        }
    }
}
