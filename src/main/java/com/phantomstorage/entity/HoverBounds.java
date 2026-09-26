package com.phantomstorage.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Idle hover limits: the chest's base may sit at most {@link #MAX_SINK} into the ground block
 * beneath it, and at most {@link #MAX_HEIGHT} above it. Fluid surfaces count as ground.
 * Bounded scan (at most SCAN_UP + SCAN_DOWN block lookups) and never loads chunks.
 */
public final class HoverBounds {
    public static final double MAX_SINK = 0.4;
    public static final double MAX_HEIGHT = 4.0;
    private static final int SCAN_UP = 4;
    private static final int SCAN_DOWN = 8;

    private HoverBounds() {}

    public static double clampY(Level level, double x, double y, double z) {
        double ground = groundTop(level, x, y, z);
        if (Double.isNaN(ground)) {
            return y;
        }
        return Mth.clamp(y, ground - MAX_SINK, ground + MAX_HEIGHT);
    }

    /** Top surface of the ground under (x, y, z), or NaN if none within scan range. */
    private static double groundTop(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        double surface = surfaceAt(level, pos);
        if (!Double.isNaN(surface)) {
            // Inside terrain: climb to the first open block.
            for (int i = 0; i < SCAN_UP; i++) {
                pos.move(Direction.UP);
                double above = surfaceAt(level, pos);
                if (Double.isNaN(above)) {
                    return surface;
                }
                surface = above;
            }
            return surface;
        }
        for (int i = 0; i < SCAN_DOWN; i++) {
            pos.move(Direction.DOWN);
            surface = surfaceAt(level, pos);
            if (!Double.isNaN(surface)) {
                return surface;
            }
        }
        return Double.NaN;
    }

    private static double surfaceAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Double.NaN;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        double top = Double.NaN;
        if (!fluid.isEmpty()) {
            top = pos.getY() + fluid.getHeight(level, pos);
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (!shape.isEmpty()) {
            double solidTop = pos.getY() + shape.max(Direction.Axis.Y);
            top = Double.isNaN(top) ? solidTop : Math.max(top, solidTop);
        }
        return top;
    }
}
