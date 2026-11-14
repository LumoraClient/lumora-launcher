package dev.lumora.client.diagnostics;

import dev.lumora.core.extras.OffHandModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Smoke step "off-hand" (module "Off Hand"): a sword in the main hand and a shield in the off hand; with the default
 * settings nothing is hidden, then the shield in the off hand and then the whole main hand are skipped by the first
 * person hand hook (screenshot each time).
 */
final class OffHandSmoke {
    private static int before;

    private OffHandSmoke() {
    }

    static void start(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server != null && mc.player != null) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                }
            });
        }
        ApiSmoke.core().addonManager().setWanted(OffHandModule.ID, true);
        OffHandModule m = OffHandModule.active();
        before = m == null ? 0 : m.hiddenCount();
    }

    /** Both hands drawn so far; now the shield in the off hand is hidden. */
    static void hideShield(List<String> problems) {
        OffHandModule m = OffHandModule.active();
        if (m == null) {
            problems.add("off hand: module not active");
            return;
        }
        if (m.hiddenCount() != before) {
            problems.add("off hand: a hand was hidden with the default settings");
        }
        m.set("offhand_shield", true);
        before = m.hiddenCount();
    }

    /** The shield was skipped; now the whole main hand is hidden instead. */
    static void hideMainHand(List<String> problems) {
        OffHandModule m = OffHandModule.active();
        if (m == null) {
            return;
        }
        int shield = m.hiddenCount() - before;
        dev.lumora.client.LumoraClient.LOG.info("LUMORA_OFFHAND shield hidden {} times", shield);
        if (shield <= 0) {
            problems.add("off hand: the shield in the off hand was still drawn");
        }
        m.set("offhand_shield", false);
        m.set("mainhand_show", false);
        before = m.hiddenCount();
    }

    static void stop(List<String> problems) {
        OffHandModule m = OffHandModule.active();
        if (m == null) {
            return;
        }
        int main = m.hiddenCount() - before;
        dev.lumora.client.LumoraClient.LOG.info("LUMORA_OFFHAND main hand hidden {} times", main);
        if (main <= 0) {
            problems.add("off hand: the main hand was still drawn");
        }
        m.set("mainhand_show", true);
        ApiSmoke.core().addonManager().setWanted(OffHandModule.ID, false);
    }
}
