package dev.lumora.core.extras.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;

/**
 * "Hearts": how Minecraft's hearts blink. Version independent; the version mixins change the two values Minecraft
 * hands to its heart drawing ({@code oldHealth} for the white "lost" part and {@code blink}).
 * <p>
 * Original code:
 * <ul>
 *     <li>1.7.10 {@code GuiIngame.renderPlayerStats}: the hearts blink while the hurt resistance time is at least 10
 *     ({@code hurtResistantTime / 3 % 2 == 1}), the white part shows the health before the last damage
 *     ({@code prevHealth}); healing does not blink.</li>
 *     <li>1.8.9 and modern: an own counter, 20 ticks after damage and 10 after healing, the white part shows the health
 *     of up to a second ago. The same in both, so there is no own 1.8 option.</li>
 * </ul>
 * Only Minecraft's hearts; Lumora's own hearts (module "Hotbar & Status Bars") do not blink at all.
 */
public final class HeartBlink {
    private static int lastHealth = -1;
    /** 1.7 {@code prevHealth}: the health before the last damage. */
    private static int healthBefore;

    private HeartBlink() {
    }

    /** Whether the 1.7 hearts are on. */
    public static boolean legacy() {
        return AnimationsModule.style(Feature.HEART) == Style.LEGACY_1_7;
    }

    /** 1.7: blinking while the hurt resistance time is 10 or more. */
    public static boolean blink(int hurtResistantTime) {
        return hurtResistantTime >= 10 && hurtResistantTime / 3 % 2 == 1;
    }

    /** 1.7: the health shown white while blinking (the health before the last damage); called once per frame. */
    public static int oldHealth(int currentHealth) {
        if (lastHealth >= 0 && currentHealth < lastHealth) {
            healthBefore = lastHealth;
        }
        lastHealth = currentHealth;
        return Math.max(healthBefore, currentHealth);
    }
}
