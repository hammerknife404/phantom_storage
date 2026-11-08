package com.phantomstorage.menu;

/**
 * Pixel layout of the Phantom Tank screen, kept free of game classes like {@link PhantomLayout} so the
 * unit tests can check it.
 *
 * <pre>
 * +------------------------------+
 * | icon title                   |
 * | +--------------------------+ |
 * | | [##]  [##]  [##]  [##]   | |  gauges   } one well
 * | |  T     T     T     T     | |  trash    }
 * | +--------------------------+ |
 * | [ ] Overfill   [ ] Drain all |  options
 * | Inventory                    |
 * | | player inventory, hotbar | |  a second well
 * +------------------------------+
 * </pre>
 */
public final class TankLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 200;
    public static final int GAUGE_X = 17;
    public static final int GAUGE_STEP = 40;
    public static final int GAUGE_Y = 18;
    public static final int GAUGE_W = 22;
    public static final int GAUGE_H = 52;
    public static final int TRASH_Y = GAUGE_Y + GAUGE_H + 3;
    public static final int OPTION_Y = TRASH_Y + 18;
    public static final int OPTION_SIZE = 9;
    /** Checkbox hit area: the box plus its label. */
    public static final int OPTION_W = 76;
    public static final int OVERFILL_X = 8;
    public static final int DRAIN_ALL_X = 92;
    public static final int INV_X = 8;
    public static final int INV_Y = 118;
    public static final int HOTBAR_Y = INV_Y + 58;
    public static final int INV_LABEL_Y = INV_Y - 11;
    /** Room the title may take: the panel's width less the header icon and a matching right margin. */
    public static final int MAX_TITLE_WIDTH = WIDTH - PhantomLayout.TITLE_X - 8;

    private TankLayout() {}

    /** The well behind the gauges and their trash buttons, as wide as the inventory's. */
    public static PhantomLayout.Box gaugeWell() {
        return new PhantomLayout.Box(INV_X - 1, GAUGE_Y - 3, 9 * 18, TRASH_Y + PhantomLayout.ICON_SIZE + 2 - (GAUGE_Y - 3))
                .padded(PhantomLayout.WELL_PAD, 0, PhantomLayout.WELL_PAD);
    }

    /** The well behind the player inventory and hotbar, as on the chest screen. */
    public static PhantomLayout.Box inventoryWell() {
        return new PhantomLayout.Box(INV_X - 1, INV_Y - 1, 9 * 18, HOTBAR_Y + 17 - INV_Y)
                .padded(PhantomLayout.WELL_PAD, 0, PhantomLayout.WELL_PAD);
    }

    public static int gaugeX(int tank) {
        return GAUGE_X + tank * GAUGE_STEP;
    }

    public static int trashX(int tank) {
        return gaugeX(tank) + (GAUGE_W - PhantomLayout.ICON_SIZE) / 2;
    }
}
