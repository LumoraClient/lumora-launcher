package dev.lumora.core.addon;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared with the test addons loaded from jars (this class lives in the parent loader). */
public final class TestSink {
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private TestSink() {
    }
}
