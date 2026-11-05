package dev.lumora.api.config;

import java.util.List;

/**
 * A setting that holds one of a fixed list of options ({@link ChoiceSetting}, {@link EnumSetting}); the settings
 * screen uses it for arrows, drop-down lists and button rows alike. Option ids are lower-case words.
 *
 * @since 1.3.0
 */
public interface OptionSetting {
    /** Ids of all options in display order. */
    List<String> optionIds();

    /** Id of the selected option. */
    String selectedId();

    /** Selects an option by id; false if the id is unknown or already selected. */
    boolean select(String id);

    /** Translation key of an option label (setting name key + "." + id). */
    String optionKey(String id);

    /** Selects the next ({@code direction} 1) or previous (-1) option, wrapping around. */
    default void cycle(int direction) {
        List<String> ids = optionIds();
        int i = ids.indexOf(selectedId());
        int n = ids.size();
        select(ids.get(((i + direction) % n + n) % n));
    }
}
