package com.phantomstorage.summon;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.CollectorSettings;
import com.phantomstorage.storage.PhantomInventory;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the Phantom Collector does every few ticks. Following, it works within its range of its owner;
 * anchored, within its range of itself, wherever its owner is.
 * <ul>
 *   <li>Items its filter allows are taken: those matching the void filter are destroyed, the rest go
 *       into the Phantom Chest storage, then the owner's inventory; whatever fits nowhere stays put.</li>
 *   <li>Experience: following, orbs are brought to the owner, so vanilla's pickup (Mending included)
 *       takes them; anchored, they go into its bank.</li>
 * </ul>
 * It never takes items still on their pickup delay, meant for someone else, or thrown by the owner
 * (what you throw away stays away), nor any an item-transport mod has marked {@code PreventRemoteMovement}.
 */
public final class CollectorWork {
    public static final int INTERVAL = 5;
    /** Item entities handled per pass, so a huge pile spreads over a few passes. */
    private static final int MAX_ITEMS = 32;
    private static final String PREVENT_REMOTE_MOVEMENT = "PreventRemoteMovement";
    /** ExperienceOrb#count: orbs spawned together merge, each worth value x count. */
    private static final Field ORB_COUNT = orbCountField();

    private CollectorWork() {}

    public static void collect(PhantomPetEntity collector, ServerPlayer owner) {
        if (!(collector.level() instanceof ServerLevel level)) {
            return;
        }
        boolean anchored = collector.isAnchored();
        if (!anchored && owner.level() != level) {
            return;
        }
        Vec3 center = anchored ? collector.position() : owner.position();
        CollectorSettings settings = owner.getData(ModRegistries.COLLECTOR);
        double range = settings.range();
        double rangeSqr = range * range;
        AABB area = AABB.ofSize(center, 2 * range, 2 * range, 2 * range);
        PhantomInventory storage = owner.getData(ModRegistries.STORAGE);

        int budget = MAX_ITEMS;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                item -> item.distanceToSqr(center) <= rangeSqr && isCollectable(item, owner))) {
            if (budget-- <= 0) {
                break;
            }
            ItemStack stack = item.getItem();
            if (!settings.allows(stack)) {
                continue;
            }
            int count = stack.getCount();
            if (storage.isVoided(stack)) {
                collector.take(item, count);
                item.discard();
                continue;
            }
            ItemStack rest = storage.addItem(stack);
            if (!rest.isEmpty()) {
                owner.getInventory().add(rest);
            }
            int taken = count - rest.getCount();
            if (taken <= 0) {
                continue; // storage and inventory are full: leave it where it is
            }
            collector.take(item, taken);
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
            }
        }

        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area,
                orb -> orb.isAlive() && orb.distanceToSqr(center) <= rangeSqr)) {
            if (anchored) {
                settings.bank(orbXp(orb));
                collector.take(orb, 1);
                orb.discard();
            } else if (orb.distanceToSqr(owner) > 1.0) {
                orb.moveTo(owner.getX(), owner.getY() + 0.5, owner.getZ());
            }
        }
    }

    private static boolean isCollectable(ItemEntity item, ServerPlayer owner) {
        return item.isAlive()
                && !item.hasPickUpDelay()
                && (item.getTarget() == null || item.getTarget().equals(owner.getUUID()))
                && item.getOwner() != owner
                && !item.getPersistentData().getBoolean(PREVENT_REMOTE_MOVEMENT);
    }

    /** The orb's whole worth, merged copies included. */
    private static int orbXp(ExperienceOrb orb) {
        int count = 1;
        if (ORB_COUNT != null) {
            try {
                count = Math.max(1, ORB_COUNT.getInt(orb));
            } catch (IllegalAccessException e) {
                count = 1;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) orb.getValue() * count);
    }

    private static Field orbCountField() {
        try {
            Field field = ExperienceOrb.class.getDeclaredField("count");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PhantomStorage.LOGGER.warn("Phantom Collector: can't read merged experience orbs; each counts once", e);
            return null;
        }
    }
}
