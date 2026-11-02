package dev.lumora.loader.api;

/**
 * Entry point of a client component started by the Lumora Loader (listed under {@code entrypoints} in its
 * {@code lumora.loader.json}). Called once, on the main thread, after the mixins are set up and before Minecraft's
 * own {@code main} runs: register hooks here, do not touch the game instance yet.
 */
public interface ClientEntrypoint {
    void onInitializeClient();
}
