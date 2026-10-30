package dev.lumora.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    public static final String NAME = "Lumora Client";
    public static final String VERSION = load();

    private BuildInfo() {
    }

    private static String load() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/lumora-build.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("version", "dev");
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "dev";
    }
}
