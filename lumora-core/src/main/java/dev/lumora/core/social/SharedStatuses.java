package dev.lumora.core.social;

import dev.lumora.social.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Status lines other players share per addon ({@code SocialInfo.sharedStatus}): the render thread reads the cache and
 * marks the players it asks for, the client tick asks Lumora Social for the wanted ones in batches (one lookup per addon
 * at a time, at most every {@link #BATCH_MS}), answers count for {@link #REFRESH_MS}, players nobody asked for in
 * {@link #FORGET_MS} are dropped.
 */
public final class SharedStatuses {
    static final long BATCH_MS = 3_000;
    static final long REFRESH_MS = 10_000;
    static final long FORGET_MS = 60_000;

    /** Asks Lumora Social for the lines of these players (UUIDs without dashes). */
    public interface Lookup {
        CompletableFuture<Map<String, String>> ask(String key, List<String> uuids);
    }

    private static final class Entry {
        volatile String text;
        volatile long fetched = -1;
        volatile long wanted;
    }

    private final Map<String, Map<String, Entry>> byKey = new ConcurrentHashMap<>();
    private final Map<String, Long> lastLookup = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /** The known line of {@code uuid} under {@code key}; asks for it with the next tick if it is missing or old. */
    public Optional<String> get(String key, String uuid, long now) {
        Entry e = byKey.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).computeIfAbsent(uuid, u -> new Entry());
        e.wanted = now;
        return Optional.ofNullable(e.text);
    }

    /** Client thread: forgets players nobody asks for and sends the due lookups. */
    public void tick(Lookup lookup, long now) {
        for (Map.Entry<String, Map<String, Entry>> k : byKey.entrySet()) {
            String key = k.getKey();
            Map<String, Entry> players = k.getValue();
            players.values().removeIf(e -> now - e.wanted > FORGET_MS);
            if (inFlight.contains(key) || now - lastLookup.getOrDefault(key, Long.MIN_VALUE / 2) < BATCH_MS) {
                continue;
            }
            List<String> due = new ArrayList<>();
            for (Map.Entry<String, Entry> p : players.entrySet()) {
                long f = p.getValue().fetched;
                if (f < 0 || now - f >= REFRESH_MS) {
                    due.add(p.getKey());
                    if (due.size() >= Rules.STATUS_LOOKUP_MAX) {
                        break;
                    }
                }
            }
            if (due.isEmpty()) {
                continue;
            }
            inFlight.add(key);
            lastLookup.put(key, now);
            CompletableFuture<Map<String, String>> answer;
            try {
                answer = lookup.ask(key, due);
            } catch (RuntimeException ex) {
                answer = CompletableFuture.failedFuture(ex);
            }
            answer.whenComplete((found, error) -> {
                for (String u : due) {
                    Entry e = players.get(u);
                    if (e != null) {
                        if (error == null) {
                            e.text = found.get(u);
                        }
                        e.fetched = now; // after an error: the old answer stays until the next refresh
                    }
                }
                inFlight.remove(key);
            });
        }
    }
}
