package dev.lumora.core.camera;

/**
 * Free camera of the Freelook module, read by the camera and mouse mixins of every Minecraft version. While active,
 * mouse movement turns only this camera ({@link #turn}) and the camera setup uses its yaw and pitch instead of the
 * player's ({@link #yaw()}, {@link #pitch()}); the player keeps looking and walking where it did. The same idea as
 * LabyMod's camera lock (player turn event cancelled, camera rotation replaced) - own code.
 * <p>
 * Called on the render thread; the fields are volatile because the module switches it on the client tick.
 */
public final class Freelook {
    /** Degrees per unit of mouse movement, the factor of Minecraft's {@code Entity#turn} in every version. */
    static final double DEGREES_PER_UNIT = 0.15;

    private static volatile boolean active;
    private static volatile float yaw;
    private static volatile float pitch;

    private Freelook() {
    }

    /** {@code true} while the free camera replaces the player's view. */
    public static boolean active() {
        return active;
    }

    /** Yaw of the free camera in degrees. */
    public static float yaw() {
        return yaw;
    }

    /** Pitch of the free camera in degrees, -90 (up) to 90 (down). */
    public static float pitch() {
        return pitch;
    }

    /**
     * Mouse movement as Minecraft passes it to {@code Entity#turn(double, double)}: {@code dx} to the right,
     * {@code dy} down. Turns the free camera instead of the player while active.
     *
     * @return {@code true} if the movement was used (the player must not turn)
     */
    public static boolean turn(double dx, double dy) {
        if (!active) {
            return false;
        }
        yaw = (float) (yaw + dx * DEGREES_PER_UNIT);
        pitch = clampPitch((float) (pitch + dy * DEGREES_PER_UNIT));
        return true;
    }

    /** Starts the free camera looking where the player looks. */
    public static void start(float playerYaw, float playerPitch) {
        yaw = playerYaw;
        pitch = clampPitch(playerPitch);
        active = true;
    }

    public static void stop() {
        active = false;
    }

    static float clampPitch(float p) {
        return Math.max(-90f, Math.min(90f, p));
    }
}
