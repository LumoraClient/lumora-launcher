package dev.lumora.core.cosmetics;

import dev.lumora.core.cosmetics.anim.Animation;

/**
 * One emote: a whole-body (or partial) player animation in the GeckoLib format. Bones {@code head}, {@code body},
 * {@code right_arm}, {@code left_arm}, {@code right_leg}, {@code left_leg} move the model parts, {@code root} the whole
 * player (sitting, lying, spinning); parts the animation does not move keep Minecraft's own motion (walking legs
 * while waving). {@code body} bends at the hip and carries head and arms, like the waist of the Bedrock player rig.
 *
 * @param stopOnMove the emote ends when the player walks or jumps (whole-body emotes)
 */
public record EmoteDefinition(String id, String name, String description, boolean translated, EmoteCategory category,
                              Rarity rarity, boolean unlocked, Animation animation, boolean stopOnMove, String icon,
                              boolean custom) {
}
