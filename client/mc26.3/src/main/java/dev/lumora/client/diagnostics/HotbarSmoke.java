package dev.lumora.client.diagnostics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Smoke test of the module "Hotbar & Leisten": blocks and items in the hotbar (flat and 3D models, a stack count,
 * a damaged tool), a shield in the offhand, the vanilla and the Lumora look and Minecraft's own hotbar to compare.
 */
final class HotbarSmoke {
    private HotbarSmoke() {
    }

    static void fill(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(uuid);
            if (p == null) {
                return;
            }
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            pick.setDamageValue(120);
            ItemStack[] items = {new ItemStack(Items.STONE, 37), new ItemStack(Items.OAK_LOG), new ItemStack(Items.GOLD_BLOCK, 5),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 12), pick, new ItemStack(Items.OAK_FENCE), new ItemStack(Items.CHEST),
                    new ItemStack(Items.EMERALD), new ItemStack(Items.TORCH, 64)};
            for (int i = 0; i < items.length; i++) {
                p.getInventory().setItem(i, items[i]);
            }
            p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            p.getInventory().setSelectedSlot(2);
        });
        mc.player.getInventory().setSelectedSlot(2);
    }

    /** Module on or off. */
    static void module(boolean on) {
        ApiSmoke.core().addonManager().setWanted("lumora-statusbars", on);
    }

    /** Look of the hotbar widget ("vanilla" or "lumora"). */
    static void style(String style) {
        ApiSmoke.core().widgetRegistry().entry("lumora-statusbars:hotbar")
                .flatMap(e -> e.widget().settings().get("style")).ifPresent(s -> s.decode(style));
    }
}
