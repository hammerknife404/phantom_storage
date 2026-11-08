package com.phantomstorage.summon;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

/**
 * The Phantom Charm's modes: one per pet, plus modes that use a pet rather than being one (the saddle
 * rides the chest, the gateway reaches the anchor). A mode with unlock advancements is skipped until
 * the player has earned one of them; that is the only progression gate, so new modes need no recipes
 * of their own. The bell calls every pet at once. New modes go at the end: the charm stores its mode by name on disk and by ordinal over
 * the network, and the item model picks its art by ordinal.
 */
public enum CharmMode implements StringRepresentable {
    CHEST("chest", PetKind.CHEST),
    /**
     * "Local Brewery": brew a potion (blaze rods, so a Nether trip). "Not Quite "Nine" Lives" (charge a
     * respawn anchor) was the unlock before 2.9.0 and still counts, so nobody who had the tank loses it.
     */
    TANK("tank", PetKind.TANK,
            ResourceLocation.withDefaultNamespace("nether/brew_potion"),
            ResourceLocation.withDefaultNamespace("nether/charge_respawn_anchor")),
    /** "Remote Getaway": enter an End gateway, i.e. after the dragon. */
    ANCHOR("anchor", PetKind.ANCHOR,
            ResourceLocation.withDefaultNamespace("end/enter_end_gateway")),
    /** "You've Got a Friend in Me": have an Allay deliver you an item, vanilla's own collector. */
    COLLECTOR("collector", PetKind.COLLECTOR,
            ResourceLocation.withDefaultNamespace("husbandry/allay_deliver_item_to_player")),
    /** "Great View From Up Here": levitate 50 blocks on a shulker's bullets. Rides the Phantom Chest. */
    SADDLE("saddle", null,
            ResourceLocation.withDefaultNamespace("end/levitate")),
    /** "The End... Again...": respawn the Ender Dragon. Opens a portal to the anchored Phantom Anchor. */
    GATEWAY("gateway", null,
            ResourceLocation.withDefaultNamespace("end/respawn_dragon")),
    /** "Hot Tourist Destinations": explore every Nether biome. Summons or dismisses every pet at once. */
    BELL("bell", null,
            ResourceLocation.withDefaultNamespace("nether/explore_nether"));

    public static final Codec<CharmMode> CODEC = StringRepresentable.fromEnum(CharmMode::values);
    public static final StreamCodec<ByteBuf, CharmMode> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], CharmMode::ordinal);

    private final String key;
    @Nullable
    private final PetKind pet;
    /** Any one of these unlocks the mode; the first is the one named while it's locked. */
    private final List<ResourceLocation> unlocks;

    CharmMode(String key, @Nullable PetKind pet, ResourceLocation... unlocks) {
        this.key = key;
        this.pet = pet;
        this.unlocks = List.of(unlocks);
    }

    /** The pet this mode summons, or null for a mode that isn't a pet of its own. */
    @Nullable
    public PetKind pet() {
        return this.pet;
    }

    /** The mode that summons {@code kind}. */
    public static CharmMode of(PetKind kind) {
        for (CharmMode mode : values()) {
            if (mode.pet == kind) {
                return mode;
            }
        }
        throw new IllegalArgumentException("No charm mode for " + kind);
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

    /** Creative players and missing advancements never lock a mode. */
    public boolean isUnlocked(ServerPlayer player) {
        return player.isCreative() || this.isEarned(player);
    }

    /** Unlocked by advancement, whatever the game mode: nothing to earn, or one of them earned. */
    public boolean isEarned(ServerPlayer player) {
        List<AdvancementHolder> advancements = this.unlockAdvancements(player);
        return advancements.isEmpty() || advancements.stream().anyMatch(a -> isDone(player, a));
    }

    /** Whether this mode starts locked at all (the chest doesn't). */
    public boolean hasUnlock() {
        return !this.unlocks.isEmpty();
    }

    /** The advancement to earn for it: the first that exists; null if none do. */
    @Nullable
    public AdvancementHolder primaryUnlock(ServerPlayer player) {
        List<AdvancementHolder> advancements = this.unlockAdvancements(player);
        return advancements.isEmpty() ? null : advancements.getFirst();
    }

    /** {@code earned} unlocks this mode and no other unlock advancement already had: say so once, not per advancement. */
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

    @Override
    public String getSerializedName() {
        return this.key;
    }

    /** "Chest" / "Saddle" / "Gateway", from {@code pet.phantomstorage.<mode>}. */
    public Component displayName() {
        return Component.translatable("pet.phantomstorage." + this.key);
    }

    /** "Phantom Tank" / "Phantom Saddle": the pet's entity name, or the mode's own. */
    public Component fullName() {
        return this.pet != null ? this.pet.fullName() : Component.translatable("charm.phantomstorage." + this.key);
    }
}
