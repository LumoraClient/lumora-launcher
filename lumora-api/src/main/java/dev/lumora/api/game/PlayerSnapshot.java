package dev.lumora.api.game;

import java.util.UUID;

/** The local player as of the last client tick. {@code speed} is horizontal blocks per second. */
public record PlayerSnapshot(
        UUID id,
        String name,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float health,
        float maxHealth,
        int food,
        int armor,
        int experienceLevel,
        boolean onGround,
        boolean sprinting,
        boolean sneaking,
        double speed) {

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** Compass direction the player looks at: "south", "west", "north" or "east". */
    public String facing() {
        float yawNorm = ((yaw % 360) + 360) % 360;
        String[] dirs = {"south", "west", "north", "east"};
        return dirs[Math.round(yawNorm / 90f) % 4];
    }
}
