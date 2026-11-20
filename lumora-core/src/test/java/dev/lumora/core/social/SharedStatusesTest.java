package dev.lumora.core.social;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared status lines: batched lookups, refresh, one lookup per addon at a time, forgetting, errors. */
class SharedStatusesTest {
    static final String A = "11111111111111111111111111111111";
    static final String B = "22222222222222222222222222222222";

    /** Records the lookups; each one is answered by hand. */
    static final class Recorder implements SharedStatuses.Lookup {
        final List<List<String>> asked = new ArrayList<>();
        final List<CompletableFuture<Map<String, String>>> answers = new ArrayList<>();

        @Override
        public CompletableFuture<Map<String, String>> ask(String key, List<String> uuids) {
            asked.add(List.copyOf(uuids));
            CompletableFuture<Map<String, String>> f = new CompletableFuture<>();
            answers.add(f);
            return f;
        }
    }

    @Test
    void asksInBatchesAndRefreshes() {
        SharedStatuses s = new SharedStatuses();
        Recorder r = new Recorder();
        assertEquals(Optional.empty(), s.get("spotify", A, 0), "unknown at first");
        s.get("spotify", B, 0);
        s.tick(r, 0);
        assertEquals(1, r.asked.size());
        assertTrue(r.asked.get(0).containsAll(List.of(A, B)), "one lookup for both");

        s.tick(r, 5_000);
        assertEquals(1, r.asked.size(), "no second lookup while one is open");
        r.answers.get(0).complete(Map.of(A, "♪ Song - Artist"));
        assertEquals(Optional.of("♪ Song - Artist"), s.get("spotify", A, 5_000));
        assertEquals(Optional.empty(), s.get("spotify", B, 5_000), "B shares nothing");

        s.tick(r, 6_000);
        assertEquals(1, r.asked.size(), "answers are fresh for a while");
        s.tick(r, SharedStatuses.REFRESH_MS + 1);
        assertEquals(2, r.asked.size(), "asked again after the refresh time");

        // a failed lookup keeps the old answer
        r.answers.get(1).completeExceptionally(new RuntimeException("offline"));
        assertEquals(Optional.of("♪ Song - Artist"), s.get("spotify", A, SharedStatuses.REFRESH_MS + 2));
    }

    @Test
    void forgetsPlayersNobodyAsksFor() {
        SharedStatuses s = new SharedStatuses();
        Recorder r = new Recorder();
        s.get("spotify", A, 0);
        s.tick(r, 0);
        r.answers.get(0).complete(Map.of(A, "x"));
        s.get("other", B, 0);
        s.tick(r, SharedStatuses.FORGET_MS + SharedStatuses.REFRESH_MS);
        assertEquals(1, r.asked.size(), "A and B were forgotten, nothing to ask");
        assertEquals(Optional.empty(), s.get("spotify", A, SharedStatuses.FORGET_MS + SharedStatuses.REFRESH_MS + 1));
    }
}
