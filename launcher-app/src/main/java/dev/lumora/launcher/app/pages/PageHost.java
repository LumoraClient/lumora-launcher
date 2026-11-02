package dev.lumora.launcher.app.pages;

import dev.lumora.launcher.app.AppContext;

import javax.swing.JFrame;
import java.nio.file.Path;

/** What the launcher window provides to its pages. All methods are called on the Swing thread. */
public interface PageHost {
    AppContext ctx();

    JFrame frame();

    /** Makes a profile the selected one everywhere (home tiles, play bar, pages). */
    void selectProfile(String id);

    void openFolder(Path dir);

    /** Opens an https link in the default browser. */
    void browse(String url);

    void error(String title, String message);

    void saveConfig();

    /** Runs the interactive component update check (asks before applying anything). */
    void checkUpdates();

    void extensionsChanged();

    /** Starts the selected profile; {@code quickPlay} (optional) jumps straight into a world or onto a server. */
    void launch(dev.lumora.launcher.core.launch.LaunchCommandBuilder.QuickPlay quickPlay, boolean repair);

    boolean gameRunning();

    void showPage(String id);

    /** Interactive Microsoft sign-in; adds the account (or updates it) and makes it active. */
    void signIn();

    /** The active account changed (switch / remove). */
    void accountsChanged();

    /** Makes a start type (Lumora, Vanilla or a mod loader, see Flavor ids) the selected one. */
    void selectFlavor(String flavorId);
}
