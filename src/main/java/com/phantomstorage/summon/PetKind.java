package com.phantomstorage.summon;

import com.mojang.serialization.Codec;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import io.netty.buffer.ByteBuf;
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
 * A pet with an unlock advancement can't be summoned until the player has earned it; that is the
 * only progression gate, so new pets need no recipes of their own.
 */
public enum PetKind implements StringRepresentable {
    CHEST("chest", ModRegistries.PHANTOM_CHEST, PhantomChestMenu.class, null),
    /** "Ice Bucket Challenge": obtain obsidian, i.e. the player has handled water and lava. */
    TANK("tank", ModRegistries.PHANTOM_TANK, PhantomTankMenu.class,
            ResourceLocation.withDefaultNamespace("story/form_obsidian"));

    public static final Codec<PetKind> CODEC = StringRepresentable.fromEnum(PetKind::values);
    public static final StreamCodec<ByteBuf, PetKind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], PetKind::ordinal);

    private final String key;
    private final Supplier<? extends EntityType<? extends PhantomPetEntity>> type;
    private final Class<? extends AbstractContainerMenu> menuClass;
    @Nullable
    private final ResourceLocation unlock;

    PetKind(String key, Supplier<? extends EntityType<? extends PhantomPetEntity>> type,
            Class<? extends AbstractContainerMenu> menuClass, @Nullable ResourceLocation unlock) {
        this.key = key;
        this.type = type;
        this.menuClass = menuClass;
        this.unlock = unlock;
    }

    /** The advancement that unlocks this pet, if any and if it exists (a datapack may remove it). */
    @Nullable
    public AdvancementHolder unlockAdvancement(ServerPlayer player) {
        return this.unlock == null ? null : player.server.getAdvancements().get(this.unlock);
    }

    public boolean isUnlockedBy(ResourceLocation advancement) {
        return advancement.equals(this.unlock);
    }

    /** Creative players and missing advancements never lock a pet. */
    public boolean isUnlocked(ServerPlayer player) {
        AdvancementHolder advancement = this.unlockAdvancement(player);
        return advancement == null || player.isCreative() || player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** "Tank is locked. Earn the advancement "Ice Bucket Challenge" to unlock it." */
    public Component lockedMessage(ServerPlayer player) {
        AdvancementHolder advancement = this.unlockAdvancement(player);
        Component title = advancement == null ? Component.empty()
                : advancement.value().display().map(DisplayInfo::getTitle).orElse(Component.literal(advancement.id().toString()));
        return Component.translatable("message.phantomstorage.locked", this.displayName(), title);
    }

    public EntityType<? extends PhantomPetEntity> entityType() {
        return this.type.get();
    }

    /** Whether {@code menu} is this kind's menu (it must close before the pet goes). */
    public boolean isMenu(AbstractContainerMenu menu) {
        return this.menuClass.isInstance(menu);
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    /** "Chest" / "Tank", from {@code pet.phantomstorage.<kind>}. */
    public Component displayName() {
        return Component.translatable("pet.phantomstorage." + this.key);
    }

    /** {@code message.phantomstorage.<kind>.<what>} */
    public String message(String what) {
        return "message.phantomstorage." + this.key + "." + what;
    }
}
