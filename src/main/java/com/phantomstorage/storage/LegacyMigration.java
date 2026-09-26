package com.phantomstorage.storage;

import com.phantomstorage.registry.ModRegistries;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One-way import of Phantom Storage 1.x data (same mod id, same Minecraft version, same item format).
 * <p>
 * 1.x kept the 54-slot chest inventory in the player's NeoForge persistent data under
 * {@value #KEY_INVENTORY}. Items move into the v2 storage; whatever doesn't fit, or can't be read
 * (e.g. an item from a mod that has since been removed), stays in that legacy list untouched and is
 * retried on the next login or chest open. Nothing is ever deleted that wasn't moved.
 * <p>
 * 1.x filter/refill slots were ghost slots holding template copies, not real items, so they are
 * discarded, never imported (importing them would create items). Legacy chest entities still saved
 * in chunks carry their own inventory copy; v2 never reads it and the entity removes itself.
 * <p>
 * The legacy list and the v2 storage both live in the same player data file, so a move is saved
 * atomically: an item is never in both or neither.
 */
public final class LegacyMigration {
    public static final String KEY_INVENTORY = "PhantomChestInventory";
    /** 1.x keys that hold no real items: filter/refill templates, links, entity id, tier. */
    private static final String[] NON_ITEM_KEYS = {
            "PhantomChestFilter", "PhantomChestRefill", "PhantomChestLinkedStorages",
            "PhantomChest.EntityId", "PhantomChest.Tier"};

    private LegacyMigration() {}

    public static void run(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        for (String key : NON_ITEM_KEYS) {
            data.remove(key);
        }
        if (!data.contains(KEY_INVENTORY, Tag.TAG_LIST)) {
            return;
        }

        HolderLookup.Provider provider = player.registryAccess();
        PhantomInventory storage = player.getData(ModRegistries.STORAGE);
        ListTag legacy = data.getList(KEY_INVENTORY, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        int moved = 0;
        int waiting = 0;
        int unreadable = 0;

        for (int i = 0; i < legacy.size(); i++) {
            CompoundTag entry = legacy.getCompound(i);
            Optional<ItemStack> parsed = ItemStack.parse(provider, entry);
            if (parsed.isEmpty() || parsed.get().isEmpty()) {
                remaining.add(entry); // keep exactly as it was
                unreadable++;
                continue;
            }
            ItemStack stack = parsed.get();
            ItemStack leftover = storage.addItem(stack);
            if (leftover.getCount() < stack.getCount()) {
                moved++;
            }
            if (!leftover.isEmpty()) {
                CompoundTag kept = (CompoundTag) leftover.save(provider, new CompoundTag());
                kept.putByte("Slot", entry.getByte("Slot"));
                remaining.add(kept);
                waiting++;
            }
        }

        if (remaining.isEmpty()) {
            data.remove(KEY_INVENTORY);
        } else {
            data.put(KEY_INVENTORY, remaining);
        }

        if (moved > 0) {
            player.sendSystemMessage(Component.translatable("message.phantomstorage.legacy_moved", moved)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (waiting > 0) {
            player.sendSystemMessage(Component.translatable("message.phantomstorage.legacy_waiting", waiting)
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (unreadable > 0 && moved + waiting > 0) {
            // Only mention unreadable stacks alongside other activity, not on every login.
            player.sendSystemMessage(Component.translatable("message.phantomstorage.legacy_unreadable", unreadable)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Legacy data isn't copied to the respawned player by default; carry anything still waiting. */
    public static void copyOnClone(ServerPlayer original, ServerPlayer clone) {
        CompoundTag from = original.getPersistentData();
        if (from.contains(KEY_INVENTORY, Tag.TAG_LIST)) {
            clone.getPersistentData().put(KEY_INVENTORY, from.getList(KEY_INVENTORY, Tag.TAG_COMPOUND).copy());
        }
    }
}
