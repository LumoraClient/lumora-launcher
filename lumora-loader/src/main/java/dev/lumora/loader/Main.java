package dev.lumora.loader;

import dev.lumora.loader.impl.Boot;

/** Main class the launcher starts instead of Minecraft's; see {@link Boot}. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Throwable {
        Boot.main(args);
    }
}
