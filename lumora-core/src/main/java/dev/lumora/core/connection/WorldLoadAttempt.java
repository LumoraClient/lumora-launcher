package dev.lumora.core.connection;

/**
 * A singleplayer world being opened, seen through the version adapter. Minecraft does all the work (reading the
 * save, starting the integrated server, preparing the spawn area, loading the terrain); this interface only reads how
 * far it is. All methods run on the client thread.
 */
public interface WorldLoadAttempt {
    /** The steps of opening a world, in order. A step Minecraft skips (e.g. no data packs to load) has no time. */
    enum Stage {
        /** Reading the world's save data (existing world). */
        READ,
        /** Preparing a new world (new world). */
        CREATE,
        /** Loading the world's data packs and resources. */
        RESOURCES,
        /** Starting the integrated server. */
        SERVER,
        /** The server prepares the spawn area (chunks around the spawn point). */
        SPAWN,
        /** The client joins and loads the terrain around the player. */
        TERRAIN
    }

    /** Name of the world; "" while it is not known yet. */
    String worldName();

    /** Whether the world is being created right now. */
    boolean newWorld();

    /** Where Minecraft is. */
    Stage stage();

    /** Progress of the current stage in percent (0-100), or -1 if Minecraft does not report any. */
    int progress();
}
