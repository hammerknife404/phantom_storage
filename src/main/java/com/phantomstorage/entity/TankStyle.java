package com.phantomstorage.entity;

/**
 * Frame looks for the Phantom Tank: a base slab and top rim around the tinted glass. The tank moves
 * to the next style each time it's summoned. The renderer owns the textures; this is only the index,
 * so styles can be added or cut down to one without touching anything else.
 */
public enum TankStyle {
    /** Polished deepslate slab and rim. */
    DEEPSLATE,
    /** Ghostly ender chest base and lid, flapping phantom wings, glowing phantom eyes on the front. */
    PHANTOM,
    /** {@link #PHANTOM} with the ender chest's latch between the eyes. */
    PHANTOM_LATCH;

    public static TankStyle byIndex(int index) {
        TankStyle[] styles = values();
        return styles[Math.floorMod(index, styles.length)];
    }
}
