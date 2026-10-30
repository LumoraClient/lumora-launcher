package dev.lumora.api.i18n;

/** Minecraft's language system: keys from {@code assets/<namespace>/lang/<language>.json} of all packs and mods. */
public interface Translations {
    /** Translated text with {@code %s} arguments; the key itself if there is no translation. */
    String translate(String key, Object... args);

    boolean has(String key);

    /** Selected language, e.g. "de_de". */
    String language();

    /** Translation if present, otherwise {@code fallback}. */
    default String translateOr(String key, String fallback) {
        return has(key) ? translate(key) : fallback;
    }
}
