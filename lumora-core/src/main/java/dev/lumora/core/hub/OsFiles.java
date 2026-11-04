package dev.lumora.core.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Opening files and folders with the operating system and moving files to its recycle bin, for the screenshot
 * manager. Processes get the path as one argument (no shell), so file names cannot inject anything. The recycle bin
 * uses AWT's Desktop only on Windows and Linux; on macOS AWT inside the game can block the window, there the file is
 * deleted after the question in the view says so.
 */
final class OsFiles {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static Boolean trash;

    private OsFiles() {
    }

    static boolean windows() {
        return OS.contains("win");
    }

    static boolean mac() {
        return OS.contains("mac");
    }

    /** Opens a file (system image viewer) or a folder (file manager). */
    static void open(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        String abs = p.toAbsolutePath().toString();
        if (windows()) {
            run(List.of("rundll32", "url.dll,FileProtocolHandler", abs));
        } else if (mac()) {
            run(List.of("open", abs));
        } else {
            run(List.of("xdg-open", abs));
        }
    }

    /** Shows the file selected in the file manager (Linux: opens its folder). */
    static void reveal(Path file) {
        if (file == null || !Files.exists(file)) {
            return;
        }
        String abs = file.toAbsolutePath().toString();
        if (windows()) {
            run(List.of("explorer.exe", "/select," + abs));
        } else if (mac()) {
            run(List.of("open", "-R", abs));
        } else {
            open(file.getParent());
        }
    }

    private static void run(List<String> cmd) {
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException e) {
            // nothing to open with: the view stays as it is
        }
    }

    /** Whether files go to the recycle bin (else they are deleted). */
    static synchronized boolean trashAvailable() {
        if (trash == null) {
            boolean ok = false;
            if (!mac() && !Boolean.getBoolean("java.awt.headless")) {
                try {
                    ok = java.awt.Desktop.isDesktopSupported()
                            && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH);
                } catch (RuntimeException | LinkageError e) {
                    ok = false;
                }
            }
            trash = ok;
        }
        return trash;
    }

    /** Moves a file to the recycle bin; false if that is not possible (the caller then deletes it). */
    static boolean moveToTrash(Path file) {
        if (!trashAvailable()) {
            return false;
        }
        try {
            return java.awt.Desktop.getDesktop().moveToTrash(file.toFile());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
