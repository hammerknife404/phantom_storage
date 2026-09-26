package com.phantomstorage.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Merges and orders stacks for the storage sort button. Kept free of Minecraft types so the
 * item-conservation guarantee (total count in == total count out) is unit tested directly.
 */
public final class StackCompactor {
    /** How to read and rebuild a stack type. */
    public interface Ops<S> {
        boolean isEmpty(S stack);

        /** Same item and same components: may share one stack. */
        boolean canMerge(S a, S b);

        int count(S stack);

        /** Largest count one slot may hold for this stack. */
        int maxCount(S stack);

        /** A copy of {@code stack} with a different count. Must not mutate {@code stack}. */
        S withCount(S stack, int count);
    }

    private StackCompactor() {}

    /**
     * Merges mergeable stacks up to their max size, then sorts with {@code order} (stable).
     * Empty inputs are dropped; inputs are never mutated.
     */
    public static <S> List<S> compact(List<S> stacks, Ops<S> ops, Comparator<S> order) {
        List<S> merged = new ArrayList<>();
        for (S stack : stacks) {
            if (stack == null || ops.isEmpty(stack)) {
                continue;
            }
            int remaining = ops.count(stack);
            for (int i = 0; i < merged.size() && remaining > 0; i++) {
                S existing = merged.get(i);
                if (!ops.canMerge(existing, stack)) {
                    continue;
                }
                int room = ops.maxCount(existing) - ops.count(existing);
                if (room <= 0) {
                    continue;
                }
                int moved = Math.min(room, remaining);
                merged.set(i, ops.withCount(existing, ops.count(existing) + moved));
                remaining -= moved;
            }
            while (remaining > 0) {
                int size = Math.min(remaining, Math.max(1, ops.maxCount(stack)));
                merged.add(ops.withCount(stack, size));
                remaining -= size;
            }
        }
        merged.sort(order);
        return merged;
    }
}
