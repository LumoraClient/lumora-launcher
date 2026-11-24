package dev.lumora.core.cosmetics;

import java.util.Locale;

/**
 * Where a cosmetic bone sits on the player: one of the six model parts (it follows that part's motion, also during
 * emotes) or the model root (moves with the body turn only, e.g. auras and floating companions). The pivots are the
 * standard player rig in Blockbench (Bedrock pixels, y up), the same points as Minecraft's model parts.
 */
public enum AttachPoint {
    HEAD(0, 24, 0, "head", "bipedhead", "armorhead", "helmet", "hat"),
    BODY(0, 24, 0, "body", "torso", "waist", "bipedbody", "armorbody", "chest", "jacket"),
    RIGHT_ARM(-5, 22, 0, "rightarm", "right_arm", "bipedrightarm", "armorrightarm"),
    LEFT_ARM(5, 22, 0, "leftarm", "left_arm", "bipedleftarm", "armorleftarm"),
    RIGHT_LEG(-1.9f, 12, 0, "rightleg", "right_leg", "bipedrightleg", "armorrightleg"),
    LEFT_LEG(1.9f, 12, 0, "leftleg", "left_leg", "bipedleftleg", "armorleftleg"),
    ROOT(0, 24, 0, "root", "player", "everything");

    /** Pivot in Bedrock pixels (x right of the model's own right side as Blockbench shows it, y up). */
    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;
    private final String[] names;

    AttachPoint(float x, float y, float z, String... names) {
        this.pivotX = x;
        this.pivotY = y;
        this.pivotZ = z;
        this.names = names;
    }

    /** The point for a bone name of a player rig ({@code head}, {@code rightArm}, {@code armorBody} ...), or {@code null}. */
    public static AttachPoint byBone(String bone) {
        if (bone == null) {
            return null;
        }
        String n = bone.toLowerCase(Locale.ROOT).replace("_", "");
        for (AttachPoint p : values()) {
            for (String name : p.names) {
                if (name.replace("_", "").equals(n)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** The point named in a cosmetic definition ({@code "attach": "body"}), {@code fallback} if unknown. */
    public static AttachPoint of(String name, AttachPoint fallback) {
        AttachPoint p = byBone(name);
        return p == null ? fallback : p;
    }

    /** The bone name an emote animation uses for this part. */
    public String boneName() {
        return switch (this) {
            case HEAD -> "head";
            case BODY -> "body";
            case RIGHT_ARM -> "right_arm";
            case LEFT_ARM -> "left_arm";
            case RIGHT_LEG -> "right_leg";
            case LEFT_LEG -> "left_leg";
            case ROOT -> "root";
        };
    }
}
