package com.phantomstorage.menu;

/**
 * Pixel layout of the Phantom Collector panel, kept free of game classes like {@link PhantomLayout} so
 * the unit tests can check it.
 *
 * <pre>
 * +----------------------------------+
 * | icon title                       |
 * | +--------+ +-------------------+ |
 * | | filter | | Following you     | |
 * | |  3x3   | | 120, 64, -30      | |  info well
 * | |        | | Overworld         | |
 * | | [ ]Skip| | Range  [-] 8 [+]  | |
 * | +--------+ | XP 1,234      (o) | |
 * |            +-------------------+ |
 * | [x] Stay here               [T]  |
 * | Inventory                        |
 * | | player inventory, hotbar     | |
 * +----------------------------------+
 * </pre>
 */
public final class CollectorLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 196;
    public static final int MAX_TITLE_WIDTH = WIDTH - PhantomLayout.TITLE_X - 8;

    /** Filter grid: 3x3 ghost slots (item positions). */
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 16;
    public static final int OPTION_SIZE = 9;
    public static final int SKIP_X = FILTER_X;
    public static final int SKIP_Y = FILTER_Y + 3 * 18 + 4;
    /** Checkbox hit areas: the box plus its label. */
    public static final int SKIP_W = 40;
    public static final int GROUP_H = SKIP_Y + OPTION_SIZE + 2 - (FILTER_Y - 1);

    /** Info well, right of the filter, as tall. */
    public static final int INFO_X = 68;
    public static final int INFO_W = WIDTH - 7 - INFO_X;
    public static final int TEXT_X = INFO_X + 3;
    public static final int STATUS_Y = FILTER_Y + 2;
    public static final int LINE_H = 11;
    public static final int RANGE_Y = STATUS_Y + 3 * LINE_H + 2;
    public static final int XP_Y = RANGE_Y + LINE_H + 5;
    /** Range buttons and the take-XP button: 12 px icons at the info well's right. */
    public static final int PLUS_X = INFO_X + INFO_W - 3 - PhantomLayout.ICON_SIZE;
    public static final int MINUS_X = PLUS_X - 32;
    public static final int RANGE_ICON_Y = RANGE_Y - 2;
    public static final int TAKE_X = PLUS_X;
    public static final int TAKE_Y = XP_Y - 2;

    public static final int STAY_X = 8;
    public static final int STAY_Y = FILTER_Y - 1 + GROUP_H + 6;
    public static final int STAY_W = 76;
    public static final int DISMISS_X = WIDTH - 8 - PhantomLayout.ICON_SIZE;
    public static final int DISMISS_Y = STAY_Y - 1;

    public static final int INV_X = 8;
    public static final int INV_Y = STAY_Y + 24;
    public static final int HOTBAR_Y = INV_Y + 58;
    public static final int INV_LABEL_Y = INV_Y - 11;

    private CollectorLayout() {}

    /** Banked experience as shown: exact below a million ("12,345"), then short ("2.1M"), so it fits beside its button. */
    public static String formatXp(int xp) {
        return xp < 1_000_000 ? String.format("%,d", xp) : String.format("%.1fM", xp / 1_000_000.0);
    }

    /** The well behind the filter grid and its Skip checkbox. */
    public static PhantomLayout.Box filterWell() {
        return new PhantomLayout.Box(FILTER_X - 1, FILTER_Y - 1, 3 * 18, GROUP_H)
                .padded(PhantomLayout.WELL_PAD, 0, PhantomLayout.WELL_PAD);
    }

    /** The info well, as tall as the filter's. */
    public static PhantomLayout.Box infoWell() {
        return new PhantomLayout.Box(INFO_X, FILTER_Y - 1, INFO_W, GROUP_H)
                .padded(PhantomLayout.WELL_PAD, 0, PhantomLayout.WELL_PAD);
    }

    public static PhantomLayout.Box inventoryWell() {
        return new PhantomLayout.Box(INV_X - 1, INV_Y - 1, 9 * 18, HOTBAR_Y + 17 - INV_Y)
                .padded(PhantomLayout.WELL_PAD, 0, PhantomLayout.WELL_PAD);
    }
}
