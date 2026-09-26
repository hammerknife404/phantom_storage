package com.phantomstorage.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class StackCompactorTest {
    /** Stand-in for ItemStack: item id, components tag, count, per-item max. */
    record Stack(String id, String tag, int count, int max) {}

    private static final StackCompactor.Ops<Stack> OPS = new StackCompactor.Ops<>() {
        @Override
        public boolean isEmpty(Stack s) {
            return s.count() <= 0;
        }

        @Override
        public boolean canMerge(Stack a, Stack b) {
            return a.id().equals(b.id()) && a.tag().equals(b.tag());
        }

        @Override
        public int count(Stack s) {
            return s.count();
        }

        @Override
        public int maxCount(Stack s) {
            return s.max();
        }

        @Override
        public Stack withCount(Stack s, int count) {
            return new Stack(s.id(), s.tag(), count, s.max());
        }
    };

    private static final Comparator<Stack> BY_ID = Comparator.comparing(Stack::id).thenComparing(Stack::tag);

    private static Map<String, Integer> totals(List<Stack> stacks) {
        Map<String, Integer> totals = new HashMap<>();
        for (Stack s : stacks) {
            if (s != null) {
                totals.merge(s.id() + "|" + s.tag(), Math.max(0, s.count()), Integer::sum);
            }
        }
        totals.values().removeIf(v -> v == 0);
        return totals;
    }

    @Test
    void mergesPartialStacksAndSorts() {
        List<Stack> in = List.of(
                new Stack("stone", "", 40, 64),
                new Stack("apple", "", 3, 64),
                new Stack("stone", "", 40, 64),
                new Stack("sword", "sharp", 1, 1),
                new Stack("sword", "sharp", 1, 1));
        List<Stack> out = StackCompactor.compact(in, OPS, BY_ID);
        assertEquals(List.of(
                new Stack("apple", "", 3, 64),
                new Stack("stone", "", 64, 64),
                new Stack("stone", "", 16, 64),
                new Stack("sword", "sharp", 1, 1),
                new Stack("sword", "sharp", 1, 1)), out);
    }

    @Test
    void neverMergesDifferentComponents() {
        List<Stack> in = List.of(new Stack("book", "a", 1, 64), new Stack("book", "b", 1, 64));
        assertEquals(2, StackCompactor.compact(in, OPS, BY_ID).size());
    }

    @Test
    void dropsEmptiesAndNulls() {
        List<Stack> in = new ArrayList<>();
        in.add(null);
        in.add(new Stack("air", "", 0, 64));
        in.add(new Stack("dirt", "", 5, 64));
        assertEquals(List.of(new Stack("dirt", "", 5, 64)), StackCompactor.compact(in, OPS, BY_ID));
    }

    @Test
    void splitsOverfullInputsToMaxSize() {
        List<Stack> out = StackCompactor.compact(List.of(new Stack("pearl", "", 40, 16)), OPS, BY_ID);
        assertEquals(List.of(new Stack("pearl", "", 16, 16), new Stack("pearl", "", 16, 16), new Stack("pearl", "", 8, 16)), out);
    }

    /** The dupe/loss guard: random full storages always keep exact per-item totals and fit. */
    @RepeatedTest(200)
    void conservesEveryItemAndNeverGrows() {
        Random random = new Random();
        String[] ids = {"stone", "dirt", "pearl", "sword", "book"};
        int[] maxes = {64, 64, 16, 1, 64};
        List<Stack> in = new ArrayList<>();
        for (int slot = 0; slot < 108; slot++) { // one full storage
            if (random.nextInt(4) == 0) {
                in.add(null);
                continue;
            }
            int kind = random.nextInt(ids.length);
            in.add(new Stack(ids[kind], random.nextBoolean() ? "" : "t" + random.nextInt(2),
                    1 + random.nextInt(maxes[kind]), maxes[kind]));
        }
        List<Stack> out = StackCompactor.compact(in, OPS, BY_ID);
        assertEquals(totals(in), totals(out));
        assertTrue(out.size() <= in.stream().filter(s -> s != null && s.count() > 0).count());
        assertTrue(out.stream().allMatch(s -> s.count() > 0 && s.count() <= s.max()));
    }
}
