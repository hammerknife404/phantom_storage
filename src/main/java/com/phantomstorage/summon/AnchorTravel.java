package com.phantomstorage.summon;

import com.phantomstorage.entity.HoverBounds;
import com.phantomstorage.entity.PhantomPetEntity;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The charm's anchor mode once the anchor is out. Looking at it (within {@link #SWAP_RANGE} blocks,
 * walls or not) swaps places with it; otherwise the owner goes to it: anywhere, even another
 * dimension, while it's anchored, or a short hop while it follows. Either way they land on the
 * nearest safe spot within {@link #LANDING_RADIUS} blocks of it, or the charm does nothing.
 */
public final class AnchorTravel {
    public static final double SWAP_RANGE = 32.0;
    /** Forgiveness around the anchor's box for "looking at it" at range. */
    private static final double LOOK_MARGIN = 0.3;
    /** Landing spots: at most this far from the anchor, horizontally... */
    private static final double LANDING_RADIUS = 3.0;
    /** ...and from this far above its block down to the ground under it (it hovers up to {@link HoverBounds#MAX_HEIGHT} up). */
    private static final int LANDING_UP = 3;
    private static final int LANDING_DOWN = 6;
    /** Dry ground beats swimming: a dry spot a few blocks further away still wins (squared blocks). */
    private static final double WATER_PENALTY = 16.0;
    /** A swapped anchor floats at the owner's old chest height. */
    private static final double SWAP_HOVER = 0.5;

    private record Landing(Vec3 feet, double score) {}

    private AnchorTravel() {}

    /** Charm use with the anchor out: swap or teleport. False (no cooldown) if there's nowhere to land. */
    public static boolean use(ServerPlayer player, PhantomPetEntity anchor) {
        boolean sameLevel = anchor.level() == player.level();
        if (!sameLevel && !anchor.isAnchored()) {
            return false;
        }
        boolean swap = sameLevel && isLookingAt(player, anchor);
        Vec3 landing = findLanding(player.getDimensions(Pose.STANDING), anchor);
        if (landing == null || !(anchor.level() instanceof ServerLevel to)) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.anchor.no_landing"), true);
            return false;
        }
        if (swap) {
            Vec3 from = player.position();
            anchor.relocate(from.x, HoverBounds.clampY(to, from.x, from.y + SWAP_HOVER, from.z), from.z);
            if (anchor.isAnchored()) {
                AnchorManager.anchor(player, anchor);
            }
        }
        teleport(player, to, landing);
        player.displayClientMessage(Component.translatable(
                swap ? "message.phantomstorage.anchor.swapped" : "message.phantomstorage.anchor.teleported"), true);
        return true;
    }

    /** The crosshair is on {@code target} within {@link #SWAP_RANGE}, ignoring blocks in between. */
    private static boolean isLookingAt(ServerPlayer player, Entity target) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1.0F).scale(SWAP_RANGE));
        AABB box = target.getBoundingBox().inflate(LOOK_MARGIN);
        return box.contains(eye) || box.clip(eye, reach).isPresent();
    }

    private static void teleport(ServerPlayer player, ServerLevel to, Vec3 landing) {
        effects(player.serverLevel(), player.position(), 0.8F);
        player.teleportTo(to, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), player.getXRot());
        player.resetFallDistance();
        effects(to, landing, 1.2F);
    }

    /** The teleport puff and sound, at someone's feet. */
    static void effects(ServerLevel level, Vec3 feet, float pitch) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 32, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, pitch);
    }

    /**
     * Where the feet go: the nearest spot to the anchor, within {@link #LANDING_RADIUS} blocks
     * of it horizontally, where the arrival fits, on solid ground that vanilla's pathfinding
     * would walk (no fire, lava, cactus, berry bushes, powder snow, trapdoors, or anything harmful
     * beside it) and away from portals and cobwebs. Surface water counts as a last resort. Never
     * loads chunks; null if there's no such spot. {@code size} is whoever is landing: a player
     * standing, or anything else coming through a gateway.
     */
    @Nullable
    static Vec3 findLanding(EntityDimensions size, PhantomPetEntity anchor) {
        Level level = anchor.level();
        Vec3 center = anchor.position();
        BlockPos base = anchor.blockPosition();
        PathfindingContext context = new PathfindingContext(level, anchor);
        int reach = Mth.ceil(LANDING_RADIUS);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Landing best = null;
        for (int dy = -LANDING_DOWN; dy <= LANDING_UP; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    Landing landing = landingAt(level, context, pos, size, center);
                    if (landing != null && (best == null || landing.score() < best.score())) {
                        best = landing;
                    }
                }
            }
        }
        return best == null ? null : best.feet();
    }

    @Nullable
    private static Landing landingAt(Level level, PathfindingContext context, BlockPos pos, EntityDimensions size, Vec3 center) {
        double dx = pos.getX() + 0.5 - center.x;
        double dz = pos.getZ() + 0.5 - center.z;
        if (dx * dx + dz * dz > LANDING_RADIUS * LANDING_RADIUS
                || !level.getWorldBorder().isWithinBounds(pos)
                || !isAreaLoaded(level, pos)
                || isUnpleasant(level.getBlockState(pos))
                || isUnpleasant(level.getBlockState(pos.above()))) {
            return null;
        }
        PathType type = WalkNodeEvaluator.getPathTypeStatic(context, pos.mutable());
        boolean ground = type == PathType.WALKABLE || type == PathType.WATER_BORDER;
        boolean water = !ground && isSurfaceWater(level, pos);
        if (!ground && !water) {
            return null;
        }
        double y = ground ? standingY(level, pos) : pos.getY();
        if (Double.isNaN(y)) {
            return null;
        }
        Vec3 feet = new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
        AABB box = size.makeBoundingBox(feet);
        if (!level.noCollision(box) || (ground && level.containsAnyLiquid(box))) {
            return null;
        }
        return new Landing(feet, feet.distanceToSqr(center) + (water ? WATER_PENALTY : 0.0));
    }

    /** The pathfinding check also reads the blocks around {@code pos}; never load a chunk for it. */
    private static boolean isAreaLoaded(Level level, BlockPos pos) {
        return level.isLoaded(pos.offset(-1, 0, -1)) && level.isLoaded(pos.offset(1, 0, -1))
                && level.isLoaded(pos.offset(-1, 0, 1)) && level.isLoaded(pos.offset(1, 0, 1));
    }

    /** Safe to stand in, but not where anyone wants to arrive: portals whisk you off, cobwebs hold you. */
    private static boolean isUnpleasant(BlockState state) {
        return state.is(BlockTags.PORTALS) || state.is(Blocks.COBWEB);
    }

    /** Swimming at the surface: water at the feet, open air above. */
    private static boolean isSurfaceWater(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).isEmpty();
    }

    /**
     * Where feet rest at {@code pos}: on a carpet or snow layer in it, else on the floor below (lower
     * for a slab, higher for a fence). NaN if that's more than half a block off.
     */
    private static double standingY(Level level, BlockPos pos) {
        VoxelShape own = level.getBlockState(pos).getCollisionShape(level, pos);
        double y;
        if (!own.isEmpty()) {
            y = pos.getY() + own.max(Direction.Axis.Y);
        } else {
            BlockPos below = pos.below();
            VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
            y = floor.isEmpty() ? pos.getY() : below.getY() + floor.max(Direction.Axis.Y);
        }
        return Math.abs(y - pos.getY()) <= 0.5 ? y : Double.NaN;
    }
}
