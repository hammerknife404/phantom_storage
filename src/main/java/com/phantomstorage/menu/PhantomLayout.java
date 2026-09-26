package com.phantomstorage.menu;

import java.util.List;
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
 * @param sortX rightmost position of the sort button; it normally sits just after the title
 * @param recipesX top-left of the "+" show-recipes button (JEI/REI)
 * @param emblemX top-left of the faint charm emblem in spare space; -1 = none
 * @param webX top-left of the faint corner cobweb; -1 = none
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
        int recipesX, int recipesY,
        int emblemX, int emblemY, int emblemSize,
        int webX, int webY) {

    /** Size of the square icon buttons (trash, sort, recipes). */
    public static final int ICON_SIZE = 12;
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 5;
    /** Padding between the end of the title and the sort icon. */
    public static final int SORT_GAP = 6;

    public static final PhantomLayout TALL = new PhantomLayout(false, 292, 266,
            8, 16, 232, 16, 250, 84, 232, 122, 35, 184, 242,
            232, 5, 232, 111, -1, -1,
            274, 108, 212, 3, 275, 86,
            220, 196, 48, 3, 247);

    public static final PhantomLayout WIDE = new PhantomLayout(true, 400, 186,
            8, 16, 231, 16, 305, 34, 339, 16, 231, 102, 160,
            231, 5, 339, 5, 231, 91,
            380, 3, 212, 3, 307, 58,
            -1, -1, 0, -1, -1);

    /** Vanilla cobweb texture size. */
    public static final int WEB_SIZE = 16;
    /** Well padding around a slot group (left/right/bottom; flush at the top so labels and icons stay clear). */
    private static final int WELL_PAD = 2;

    /** Pixel rectangle relative to the GUI's top-left. */
    public record Box(int x, int y, int w, int h) {
        public int right() {
            return this.x + this.w;
        }

        public int bottom() {
            return this.y + this.h;
        }

        public boolean overlaps(Box o) {
            return this.x < o.right() && o.x < this.right() && this.y < o.bottom() && o.y < this.bottom();
        }

        public boolean contains(Box o) {
            return o.x >= this.x && o.y >= this.y && o.right() <= this.right() && o.bottom() <= this.bottom();
        }

        static Box union(Box... boxes) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (Box b : boxes) {
                x0 = Math.min(x0, b.x);
                y0 = Math.min(y0, b.y);
                x1 = Math.max(x1, b.right());
                y1 = Math.max(y1, b.bottom());
            }
            return new Box(x0, y0, x1 - x0, y1 - y0);
        }

        Box padded(int side, int top, int bottom) {
            return new Box(this.x - side, this.y - top, this.w + 2 * side, this.h + top + bottom);
        }
    }

    /** A soft background behind one slot group; the void well is tinted. */
    public record Well(Box box, boolean isVoid) {}

    // Slot-group bounds (slot frames: 18px per slot starting 1px before the slot's item position).
    public Box storageBox() {
        return new Box(this.storageX - 1, this.storageY - 1, 12 * 18, 9 * 18);
    }

    /** Grid, result frame and "+" icon together. */
    public Box craftingBox() {
        return Box.union(
                new Box(this.craftX - 1, this.craftY - 1, 3 * 18, 3 * 18),
                new Box(this.resultX - 5, this.resultY - 5, 26, 26),
                new Box(this.recipesX, this.recipesY, ICON_SIZE, ICON_SIZE));
    }

    public Box voidBox() {
        return new Box(this.voidX - 1, this.voidY - 1, 3 * 18, 3 * 18);
    }

    /** Main inventory and hotbar together. */
    public Box inventoryBox() {
        return new Box(this.invX - 1, this.invY - 1, 9 * 18, this.hotbarY + 17 - this.invY);
    }

    public List<Well> wells() {
        return List.of(
                new Well(this.storageBox().padded(WELL_PAD, 0, WELL_PAD), false),
                new Well(this.craftingBox().padded(WELL_PAD, 0, WELL_PAD), false),
                new Well(this.voidBox().padded(WELL_PAD, 0, WELL_PAD), true),
                new Well(this.inventoryBox().padded(WELL_PAD, 0, WELL_PAD), false));
    }

    /** Decorations drawn only in spare space; empty when the layout has none. */
    public List<Box> decorations() {
        List<Box> boxes = new java.util.ArrayList<>(2);
        if (this.emblemX >= 0) {
            boxes.add(new Box(this.emblemX, this.emblemY, this.emblemSize, this.emblemSize));
        }
        if (this.webX >= 0) {
            boxes.add(new Box(this.webX, this.webY, WEB_SIZE, WEB_SIZE));
        }
        return boxes;
    }

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

    /** Widest the title may draw so the sort icon still gets its padding. */
    public int maxTitleWidth() {
        return this.sortX - SORT_GAP - TITLE_X;
    }

    /** Sort icon x: {@link #SORT_GAP} after a title of this pixel width, never past {@link #sortX}. */
    public int sortXAfterTitle(int titleWidth) {
        return Math.min(TITLE_X + Math.max(0, titleWidth) + SORT_GAP, this.sortX);
    }

    /** Tall when it fits (more side room for JEI), otherwise wide. */
    public static PhantomLayout choose(int guiScaledWidth, int guiScaledHeight) {
        if (guiScaledHeight >= TALL.height + MARGIN && guiScaledWidth >= TALL.width + MARGIN) {
            return TALL;
        }
        return WIDE;
    }
}
