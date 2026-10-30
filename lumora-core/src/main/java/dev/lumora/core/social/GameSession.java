package dev.lumora.core.social;

import java.util.function.Supplier;

/**
 * The game's signed-in account as the version adapter sees it. {@code accessToken} is read only right before the
 * official Mojang session join and never stored, logged or sent elsewhere; {@link #toString()} leaves it out.
 */
public record GameSession(String name, String uuid, Supplier<String> accessToken) {
    @Override
    public String toString() {
        return "GameSession[" + name + "]";
    }
}
