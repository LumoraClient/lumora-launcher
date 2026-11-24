package dev.lumora.core.cosmetics;

import java.util.UUID;

/**
 * What a version adapter tells the core about a player it is about to draw: identity and motion this frame. One
 * instance is reused for all players (fill it, then call {@link Cosmetics#frame}); values are in blocks per tick,
 * degrees and ticks as Minecraft has them.
 */
public final class PlayerInput {
    public UUID id;
    /** The player of this client. */
    public boolean self;
    /** Drawn in the cosmetics menu preview (only for the own player, never sent anywhere). */
    public boolean preview;
    /** Age in ticks plus the partial tick. */
    public double age;
    public float groundSpeed;
    public float verticalSpeed;
    public boolean onGround = true;
    public boolean sneaking;
    public boolean sprinting;
    public boolean swimming;
    public boolean gliding;
    public boolean invisible;
    public boolean firstPerson;
    /** Head turn relative to the body (degrees). */
    public float headYaw;
    public float headPitch;
    public float bodyYaw;
    public float limbSwing;
    public float limbAmount;

    /** Back to defaults before filling it for the next player. */
    public PlayerInput reset() {
        id = null;
        self = false;
        preview = false;
        age = 0;
        groundSpeed = 0;
        verticalSpeed = 0;
        onGround = true;
        sneaking = false;
        sprinting = false;
        swimming = false;
        gliding = false;
        invisible = false;
        firstPerson = false;
        headYaw = 0;
        headPitch = 0;
        bodyYaw = 0;
        limbSwing = 0;
        limbAmount = 0;
        return this;
    }

    /** Player state name for cosmetic animations. */
    String state() {
        if (swimming) {
            return "swim";
        }
        if (gliding) {
            return "fly";
        }
        if (!onGround) {
            return verticalSpeed > 0.02f ? "jump" : "fall";
        }
        if (sneaking) {
            return "sneak";
        }
        if (groundSpeed > 0.2f || sprinting && groundSpeed > 0.05f) {
            return "run";
        }
        if (groundSpeed > 0.02f) {
            return "walk";
        }
        return "idle";
    }

    /** Fallback chain: run → walk → idle, jump/fall → air → idle, fly → air → idle. */
    static String fallback(String state) {
        return switch (state) {
            case "run" -> "walk";
            case "jump", "fall", "fly" -> "air";
            default -> "idle";
        };
    }
}
