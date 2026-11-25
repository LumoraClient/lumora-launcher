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

    /** Settings were changed outside the settings page (e.g. a fix from the crash analysis): the page shows them anew. */
    default void settingsChanged() {
    }

    /** Makes an instance the selected one: the play button, the home page and the version menu follow. */
    void selectInstance(dev.lumora.launcher.core.instance.Instance instance);

    /** Shows the instance page with the details of {@code instance}. */
    void openInstance(dev.lumora.launcher.core.instance.Instance instance);

    /** Opens Entdecken for one content type, with filters for {@code target} (null = the selected instance). */
    void discover(dev.lumora.launcher.core.platform.ProjectType type, dev.lumora.launcher.core.instance.Instance target);

    /** The instance list changed (created, copied, renamed, deleted, imported): pages that show instances reload. */
    void instancesChanged();
}
