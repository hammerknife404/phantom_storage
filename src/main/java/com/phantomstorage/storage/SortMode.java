package com.phantomstorage.storage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * How the chest's sort button orders storage. Every mode falls back to name, then id, then damage,
 * then fullest stack first, so the result is always stable. Descending reverses the whole order.
 */
public enum SortMode {
    /** A to Z by displayed name. */
    NAME("name"),
    /** Most of an item (counted over the whole storage) first. */
    AMOUNT("amount"),
    /** Grouped by the mod that adds the item, mods A to Z by display name. */
    MOD("mod");

    private final String key;

    SortMode(String key) {
        this.key = key;
    }

    public SortMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    /** "Name" / "Amount" / "Mod", from {@code sort.phantomstorage.<mode>}. */
    public Component displayName() {
        return Component.translatable("sort.phantomstorage." + this.key);
    }

    /** The order for {@code contents}; AMOUNT needs the totals, so it is built per sort. */
    public Comparator<ItemStack> comparator(List<ItemStack> contents, boolean descending) {
        Comparator<ItemStack> primary = switch (this) {
            case NAME -> Comparator.comparingInt(stack -> 0);
            case AMOUNT -> {
                Map<Item, Integer> totals = new HashMap<>();
                for (ItemStack stack : contents) {
                    if (!stack.isEmpty()) {
                        totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    }
                }
                yield Comparator.comparingInt((ItemStack stack) -> totals.getOrDefault(stack.getItem(), 0)).reversed();
            }
            case MOD -> Comparator.comparing(SortMode::modName, String.CASE_INSENSITIVE_ORDER);
        };
        Comparator<ItemStack> order = primary.thenComparing(TIE_BREAK);
        return descending ? order.reversed() : order;
    }

    /** Name, then id (separates same-named items), then damage, fullest first. */
    private static final Comparator<ItemStack> TIE_BREAK = Comparator
            .comparing((ItemStack stack) -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .thenComparingInt(ItemStack::getDamageValue)
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

    private static String modName(ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }
}
