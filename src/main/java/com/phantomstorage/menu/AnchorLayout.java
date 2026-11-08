package com.phantomstorage.menu;

/**
 * Pixel layout of the Phantom Anchor panel, kept free of game classes like {@link PhantomLayout} so the
 * unit tests can check it.
 *
 * <pre>
 * +------------------------------+
 * | icon title                   |
 * | +--------------------------+ |
 * | | charm  Anchored          | |
 * | |  art   X 120  Y 64  Z -30| |  info well
 * | |        Overworld         | |
 * | |        * Chunk kept loaded | |
 * | +--------------------------+ |
 * | [x] Stay here          [T]   |
 * +------------------------------+
 * </pre>
 */
public final class AnchorLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 88;
    /** The info well's group box; drawn padded like the other screens' wells. */
    public static final int INFO_X = 7;
    public static final int INFO_Y = 15;
    public static final int INFO_W = 9 * 18;
    public static final int INFO_H = 48;
    public static final int ART_SIZE = 32;
    public static final int ART_X = INFO_X + 3;
    public static final int ART_Y = INFO_Y + 4;
    public static final int TEXT_X = ART_X + ART_SIZE + 6;
    public static final int TEXT_Y = INFO_Y + 4;
    public static final int LINE_H = 11;
    public static final int OPTION_X = 8;
    public static final int OPTION_Y = INFO_Y + INFO_H + 8;
    public static final int OPTION_SIZE = 9;
    /** Checkbox hit area: the box plus its label. */
    public static final int OPTION_W = 76;
    public static final int DISMISS_X = WIDTH - 8 - PhantomLayout.ICON_SIZE;
    public static final int DISMISS_Y = OPTION_Y - 1;
    public static final int MAX_TITLE_WIDTH = WIDTH - PhantomLayout.TITLE_X - 8;

    private AnchorLayout() {}

    /** The info well, padded. */
    public static PhantomLayout.Box infoWell() {
        return new PhantomLayout.Box(INFO_X, INFO_Y, INFO_W, INFO_H).padded(PhantomLayout.WELL_PAD, 0, PhantomLayout.WELL_PAD);
    }
}
