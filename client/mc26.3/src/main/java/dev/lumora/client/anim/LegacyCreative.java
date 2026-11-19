package dev.lumora.client.anim;

import dev.lumora.core.extras.anim.CreativeTabs18;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lumora "Animations", "Creative layout" Legacy (1.8): the contents of the 1.8 tabs ({@link CreativeTabs18}) made from
 * the items Minecraft puts into its own tabs (so only items enabled in this world appear). 1.8 entries come first in
 * their order, every variant of a plain entry next to it; items that came after 1.8 follow in the matching 1.8 tab.
 * Rebuilt whenever Minecraft rebuilds its tab contents.
 */
public final class LegacyCreative {
    private static Map<CreativeTabs18.Tab, List<ItemStack>> lists;
    private static final Map<CreativeTabs18.Tab, ItemStack> ICONS = new EnumMap<>(CreativeTabs18.Tab.class);

    private LegacyCreative() {
    }

    /** Minecraft rebuilt (a part of) its tab contents. */
    public static void invalidate() {
        lists = null;
    }

    /** The 1.8 tab standing in this Minecraft tab slot, {@code null} if none. */
    public static CreativeTabs18.Tab slot(CreativeModeTab tab) {
        Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id == null ? null : CreativeTabs18.SLOTS.get(id.getPath());
    }

    /** Whether this Minecraft tab slot has no 1.8 counterpart (hotbar, operator items). */
    public static boolean hidden(CreativeModeTab tab) {
        Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id != null && CreativeTabs18.HIDDEN.contains(id.getPath());
    }

    public static Component name(CreativeTabs18.Tab tab) {
        return Component.translatable(tab.nameKey());
    }

    public static ItemStack icon(CreativeTabs18.Tab tab) {
        return ICONS.computeIfAbsent(tab, t -> t == CreativeTabs18.Tab.BREWING
                ? PotionContents.createItemStack(Items.POTION, Potions.WATER)
                : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(t.icon))));
    }

    /**
     * The contents of a 1.8 tab.
     *
     * @param original the contents Minecraft built for its own tab slots: slot id path → items
     */
    public static List<ItemStack> contents(CreativeTabs18.Tab tab, Map<String, Collection<ItemStack>> original) {
        Map<CreativeTabs18.Tab, List<ItemStack>> l = lists;
        if (l == null) {
            l = build(original);
            lists = l;
        }
        return l.getOrDefault(tab, List.of());
    }

    static Map<CreativeTabs18.Tab, List<ItemStack>> build(Map<String, Collection<ItemStack>> original) {
        // every item Minecraft shows, with the tab it is in today
        Map<Item, List<ItemStack>> byItem = new LinkedHashMap<>();
        Map<ItemStack, String> modernTab = new IdentityHashMap<>();
        for (Map.Entry<String, Collection<ItemStack>> e : original.entrySet()) {
            for (ItemStack s : e.getValue()) {
                byItem.computeIfAbsent(s.getItem(), k -> new ArrayList<>()).add(s);
                modernTab.putIfAbsent(s, e.getKey());
            }
        }
        Map<CreativeTabs18.Tab, List<ItemStack>> out = new EnumMap<>(CreativeTabs18.Tab.class);
        for (CreativeTabs18.Tab t : CreativeTabs18.Tab.values()) {
            out.put(t, new ArrayList<>());
        }
        Set<ItemStack> used = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> enchantments18 = new HashSet<>();
        for (CreativeTabs18.Entry e : CreativeTabs18.entries()) {
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.id())).orElse(null);
            List<ItemStack> stacks = item == null ? null : byItem.get(item);
            if (stacks == null) {
                continue;
            }
            if (e.enchantment() != null) {
                enchantments18.add(e.enchantment());
            }
            for (ItemStack s : stacks) {
                if (used.contains(s)) {
                    continue;
                }
                if (e.potion() != null && !e.potion().equals(potion(s))) {
                    continue;
                }
                if (e.enchantment() != null && !matches(s, e.enchantment(), e.level())) {
                    continue;
                }
                out.get(e.tab()).add(s);
                used.add(s);
                if (e.potion() != null || e.enchantment() != null) {
                    break; // one variant per 1.8 entry
                }
            }
        }
        // books of enchantments 1.8 did not have: the highest level only, as 1.8 listed its books
        Map<String, Integer> maxLevel = new HashMap<>();
        for (List<ItemStack> stacks : byItem.values()) {
            for (ItemStack s : stacks) {
                for (Map.Entry<String, Integer> b : books(s).entrySet()) {
                    maxLevel.merge(b.getKey(), b.getValue(), Math::max);
                }
            }
        }
        for (List<ItemStack> stacks : byItem.values()) {
            for (ItemStack s : stacks) {
                if (used.contains(s)) {
                    continue;
                }
                Map<String, Integer> b = books(s);
                if (!b.isEmpty()) {
                    Map.Entry<String, Integer> first = b.entrySet().iterator().next();
                    if (enchantments18.contains(first.getKey()) || !first.getValue().equals(maxLevel.get(first.getKey()))) {
                        continue;
                    }
                }
                CreativeTabs18.Tab t = CreativeTabs18.fallback(modernTab.get(s), BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                if (t != null) {
                    out.get(t).add(s);
                    used.add(s);
                }
            }
        }
        return out;
    }

    private static String potion(ItemStack s) {
        PotionContents p = s.get(DataComponents.POTION_CONTENTS);
        return p == null ? null : p.potion().flatMap(Holder::unwrapKey).map(k -> k.identifier().toString()).orElse(null);
    }

    private static boolean matches(ItemStack s, String enchantment, int level) {
        Integer l = books(s).get(enchantment);
        return l != null && l == level;
    }

    /** Stored enchantments of a book: id → level (empty for other items). */
    private static Map<String, Integer> books(ItemStack s) {
        ItemEnchantments e = s.get(DataComponents.STORED_ENCHANTMENTS);
        if (e == null || e.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var entry : e.entrySet()) {
            Holder<Enchantment> h = entry.getKey();
            h.unwrapKey().ifPresent(k -> out.put(k.identifier().toString(), entry.getIntValue()));
        }
        return out;
    }
}
