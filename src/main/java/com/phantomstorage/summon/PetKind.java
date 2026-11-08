package com.phantomstorage.summon;

import com.mojang.serialization.Codec;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.menu.PhantomAnchorMenu;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The pets a player can have out at once (at most one of each), and the Phantom Charm's modes.
 * A pet with unlock advancements can't be summoned until the player has earned one of them; that is
 * the only progression gate, so new pets need no recipes of their own. New kinds go at the end: the
 * charm stores its mode by ordinal over the network.
 */
public enum PetKind implements StringRepresentable {
    CHEST("chest", ModRegistries.PHANTOM_CHEST, PhantomChestMenu.class),
    /**
     * "Local Brewery": brew a potion (blaze rods, so a Nether trip). "Not Quite "Nine" Lives" (charge a
     * respawn anchor) was the unlock before 2.9.0 and still counts, so nobody who had the tank loses it.
     */
    TANK("tank", ModRegistries.PHANTOM_TANK, PhantomTankMenu.class,
            ResourceLocation.withDefaultNamespace("nether/brew_potion"),
            ResourceLocation.withDefaultNamespace("nether/charge_respawn_anchor")),
    /** "Remote Getaway": enter an End gateway, i.e. after the dragon. Its panel shows where it is. */
    ANCHOR("anchor", ModRegistries.PHANTOM_ANCHOR, PhantomAnchorMenu.class,
            ResourceLocation.withDefaultNamespace("end/enter_end_gateway"));

    public static final Codec<PetKind> CODEC = StringRepresentable.fromEnum(PetKind::values);
    public static final StreamCodec<ByteBuf, PetKind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], PetKind::ordinal);

    private final String key;
    private final Supplier<? extends EntityType<? extends PhantomPetEntity>> type;
    @Nullable
    private final Class<? extends AbstractContainerMenu> menuClass;
    /** Any one of these unlocks the pet; the first is the one named while it's locked. */
    private final List<ResourceLocation> unlocks;

    PetKind(String key, Supplier<? extends EntityType<? extends PhantomPetEntity>> type,
            @Nullable Class<? extends AbstractContainerMenu> menuClass, ResourceLocation... unlocks) {
        this.key = key;
        this.type = type;
        this.menuClass = menuClass;
        this.unlocks = List.of(unlocks);
    }

    /** The unlock advancements that exist (a datapack may remove any of them). */
    private List<AdvancementHolder> unlockAdvancements(ServerPlayer player) {
        List<AdvancementHolder> found = new ArrayList<>(this.unlocks.size());
        for (ResourceLocation id : this.unlocks) {
            AdvancementHolder advancement = player.server.getAdvancements().get(id);
            if (advancement != null) {
                found.add(advancement);
            }
        }
        return found;
    }

    public boolean isUnlockedBy(ResourceLocation advancement) {
        return this.unlocks.contains(advancement);
    }

    /** Creative players and missing advancements never lock a pet. */
    public boolean isUnlocked(ServerPlayer player) {
        return player.isCreative() || this.isEarned(player);
    }

    /** Unlocked by advancement, whatever the game mode: nothing to earn, or one of them earned. */
    public boolean isEarned(ServerPlayer player) {
        List<AdvancementHolder> advancements = this.unlockAdvancements(player);
        return advancements.isEmpty() || advancements.stream().anyMatch(a -> isDone(player, a));
    }

    /** Whether this pet starts locked at all (the chest doesn't). */
    public boolean hasUnlock() {
        return !this.unlocks.isEmpty();
    }

    /** The advancement to earn for it: the first that exists; null if none do. */
    @Nullable
    public AdvancementHolder primaryUnlock(ServerPlayer player) {
        List<AdvancementHolder> advancements = this.unlockAdvancements(player);
        return advancements.isEmpty() ? null : advancements.getFirst();
    }

    /** {@code earned} unlocks this pet and no other unlock advancement already had: say so once, not per advancement. */
    public boolean isNewlyUnlockedBy(ServerPlayer player, ResourceLocation earned) {
        return this.isUnlockedBy(earned)
                && this.unlockAdvancements(player).stream().noneMatch(a -> !a.id().equals(earned) && isDone(player, a));
    }

    private static boolean isDone(ServerPlayer player, AdvancementHolder advancement) {
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** "Tank is locked. Earn the advancement "Local Brewery" to unlock it." */
    public Component lockedMessage(ServerPlayer player) {
        AdvancementHolder advancement = this.primaryUnlock(player);
        Component title = advancement == null ? Component.empty() : advancementTitle(advancement);
        return Component.translatable("message.phantomstorage.locked", this.displayName(), title);
    }

    /** "Local Brewery", or the id of an advancement with no display. */
    public static Component advancementTitle(AdvancementHolder advancement) {
        return advancement.value().display().map(DisplayInfo::getTitle).orElse(Component.literal(advancement.id().toString()));
    }

    public EntityType<? extends PhantomPetEntity> entityType() {
        return this.type.get();
    }

    /** Whether {@code menu} is this kind's menu (it must close before the pet goes). */
    public boolean isMenu(AbstractContainerMenu menu) {
        return this.menuClass != null && this.menuClass.isInstance(menu);
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    /** "Phantom Tank": the pet's entity name. */
    public Component fullName() {
        return this.entityType().getDescription();
    }

    /** "Chest" / "Tank" / "Anchor", from {@code pet.phantomstorage.<kind>}. */
    public Component displayName() {
        return Component.translatable("pet.phantomstorage." + this.key);
    }

    /** {@code message.phantomstorage.<kind>.<what>} */
    public String message(String what) {
        return "message.phantomstorage." + this.key + "." + what;
    }
}
