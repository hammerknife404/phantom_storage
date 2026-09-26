package com.phantomstorage.menu;

import java.util.function.Supplier;

/**
 * Pixel layout of the Phantom Chest screen. Slot positions only matter on the client (rendering
 * and hit-testing), so the client picks whichever layout fits the window when the menu opens.
 *
 * <pre>
 * TALL 292x266 (preferred, leaves JEI room)      WIDE 400x186 (fits 720p/768p at GUI scale 3)
 * +--------------+-----+                         +--------------+----------------------+
 * | storage 12x9 |craft|                         | storage 12x9 | craft->out  void [T] |
 * |              | out |                         |              |                      |
 * |              |void |                         |              | player inventory     |
 * +--------------+-----+                         |              | hotbar               |
 * | player inv + hotbar|                         +--------------+----------------------+
 * +--------------------+
 * </pre>
 *
 * @param invLabelX -1 hides the "Inventory" label (no room for it in the tall layout)
 * @param trashX top-left of the void filter's trash-can button
 * @param sortX top-left of the storage sort button
 * @param recipesX top-left of the "+" show-recipes button (JEI/REI)
 */
public record PhantomLayout(
        boolean wide, int width, int height,
        int storageX, int storageY,
        int craftX, int craftY,
        int resultX, int resultY,
        int voidX, int voidY,
        int invX, int invY, int hotbarY,
        int craftLabelX, int craftLabelY,
        int voidLabelX, int voidLabelY,
        int invLabelX, int invLabelY,
        int trashX, int trashY,
        int sortX, int sortY,
        int recipesX, int recipesY) {

    /** Size of the square icon buttons (trash, sort, recipes). */
    public static final int ICON_SIZE = 12;

    public static final PhantomLayout TALL = new PhantomLayout(false, 292, 266,
            8, 16, 232, 16, 250, 84, 232, 122, 35, 184, 242,
            232, 5, 232, 111, -1, -1,
            274, 108, 212, 3, 275, 86);

    public static final PhantomLayout WIDE = new PhantomLayout(true, 400, 186,
            8, 16, 231, 16, 305, 34, 339, 16, 231, 102, 160,
            231, 5, 339, 5, 231, 91,
            380, 3, 212, 3, 307, 58);

    /** Screen-edge margin kept free around the GUI when deciding what fits. */
    private static final int MARGIN = 4;

    private static Supplier<PhantomLayout> clientSelector = () -> TALL;

    /** Installed by the client mod class; keeps client-only classes out of common code. */
    public static void setClientSelector(Supplier<PhantomLayout> selector) {
        clientSelector = selector;
    }

    static PhantomLayout forClient() {
        return clientSelector.get();
    }

    /** Tall when it fits (more side room for JEI), otherwise wide. */
    public static PhantomLayout choose(int guiScaledWidth, int guiScaledHeight) {
        if (guiScaledHeight >= TALL.height + MARGIN && guiScaledWidth >= TALL.width + MARGIN) {
            return TALL;
        }
        return WIDE;
    }
}
