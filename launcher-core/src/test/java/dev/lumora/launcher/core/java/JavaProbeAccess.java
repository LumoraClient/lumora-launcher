package dev.lumora.launcher.core.java;

import java.io.IOException;

/** Test bridge to the package-private parser. */
public final class JavaProbeAccess {
    private JavaProbeAccess() {
    }

    public static int parse(String output) throws IOException {
        return JavaProbe.parseMajor(output);
    }
}
