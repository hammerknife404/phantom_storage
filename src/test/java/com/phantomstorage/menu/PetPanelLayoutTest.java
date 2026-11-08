package com.phantomstorage.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the hand-computed pixel math of the tank and anchor panels, like {@link PhantomLayoutTest} does the chest's. */
class PetPanelLayoutTest {
    private static final int BORDER = 3;
    private static final int FONT_HEIGHT = 9;
    private static final int CHAR_WIDTH = 6;

    private static PhantomLayout.Box box(int x, int y, int w, int h) {
        return new PhantomLayout.Box(x, y, w, h);
    }

    private static void assertInsidePanel(PhantomLayout.Box b, int width, int height, String what) {
        assertTrue(b.x() >= BORDER && b.y() >= BORDER && b.right() <= width - BORDER && b.bottom() <= height - BORDER,
                what + " " + b + " leaves the panel");
    }

    private static void assertApart(List<PhantomLayout.Box> boxes, String what) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).overlaps(boxes.get(j)), what + ": " + boxes.get(i) + " overlaps " + boxes.get(j));
            }
        }
    }

    @Test
    void tankPanelFits() {
        int w = TankLayout.WIDTH;
        int h = TankLayout.HEIGHT;
        PhantomLayout.Box gauges = TankLayout.gaugeWell();
        PhantomLayout.Box inventory = TankLayout.inventoryWell();
        assertInsidePanel(gauges, w, h, "gauge well");
        assertInsidePanel(inventory, w, h, "inventory well");

        List<PhantomLayout.Box> inGaugeWell = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // The active outline reaches 2 px past the gauge.
            inGaugeWell.add(box(TankLayout.gaugeX(i) - 2, TankLayout.GAUGE_Y - 2,
                    TankLayout.GAUGE_W + 4, TankLayout.GAUGE_H + 4));
            inGaugeWell.add(box(TankLayout.trashX(i), TankLayout.TRASH_Y, PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        }
        for (PhantomLayout.Box b : inGaugeWell) {
            assertTrue(gauges.contains(b), b + " is outside the gauge well");
        }
        PhantomLayout.Box invSlots = box(TankLayout.INV_X - 1, TankLayout.INV_Y - 1, 9 * 18, TankLayout.HOTBAR_Y + 17 - TankLayout.INV_Y);
        assertTrue(inventory.contains(invSlots), "inventory slots stick out of their well");

        List<PhantomLayout.Box> free = List.of(
                PhantomLayout.headerIconBox(),
                box(PhantomLayout.TITLE_X, PhantomLayout.TITLE_Y, TankLayout.MAX_TITLE_WIDTH, FONT_HEIGHT),
                box(TankLayout.OVERFILL_X, TankLayout.OPTION_Y, TankLayout.OPTION_W, TankLayout.OPTION_SIZE),
                box(TankLayout.DRAIN_ALL_X, TankLayout.OPTION_Y, TankLayout.OPTION_W, TankLayout.OPTION_SIZE),
                box(TankLayout.INV_X, TankLayout.INV_LABEL_Y, "Inventory".length() * CHAR_WIDTH, FONT_HEIGHT));
        List<PhantomLayout.Box> all = new ArrayList<>(free);
        all.add(gauges);
        all.add(inventory);
        assertApart(all, "tank");
    }

    @Test
    void anchorPanelFits() {
        int w = AnchorLayout.WIDTH;
        int h = AnchorLayout.HEIGHT;
        PhantomLayout.Box info = AnchorLayout.infoWell();
        assertInsidePanel(info, w, h, "info well");
        PhantomLayout.Box art = box(AnchorLayout.ART_X, AnchorLayout.ART_Y, AnchorLayout.ART_SIZE, AnchorLayout.ART_SIZE);
        // Four lines; the longest ("X -29999999   Y 319   Z -29999999") stays inside the well.
        PhantomLayout.Box text = box(AnchorLayout.TEXT_X, AnchorLayout.TEXT_Y,
                info.right() - 1 - AnchorLayout.TEXT_X, 3 * AnchorLayout.LINE_H + FONT_HEIGHT);
        assertTrue(info.contains(art), "charm art leaves the info well");
        assertTrue(info.contains(text), "info text leaves the info well");
        assertFalse(art.overlaps(text), "charm art overlaps the info text");

        PhantomLayout.Box stay = box(AnchorLayout.OPTION_X, AnchorLayout.OPTION_Y, AnchorLayout.OPTION_W, AnchorLayout.OPTION_SIZE);
        PhantomLayout.Box dismiss = box(AnchorLayout.DISMISS_X, AnchorLayout.DISMISS_Y, PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE);
        assertInsidePanel(stay, w, h, "stay checkbox");
        assertInsidePanel(dismiss, w, h, "dismiss button");
        assertApart(List.of(
                PhantomLayout.headerIconBox(),
                box(PhantomLayout.TITLE_X, PhantomLayout.TITLE_Y, AnchorLayout.MAX_TITLE_WIDTH, FONT_HEIGHT),
                info, stay, dismiss), "anchor");
    }

    @Test
    void collectorPanelFits() {
        int w = CollectorLayout.WIDTH;
        int h = CollectorLayout.HEIGHT;
        PhantomLayout.Box filter = CollectorLayout.filterWell();
        PhantomLayout.Box info = CollectorLayout.infoWell();
        PhantomLayout.Box inventory = CollectorLayout.inventoryWell();
        assertInsidePanel(filter, w, h, "filter well");
        assertInsidePanel(info, w, h, "info well");
        assertInsidePanel(inventory, w, h, "inventory well");

        PhantomLayout.Box grid = box(CollectorLayout.FILTER_X - 1, CollectorLayout.FILTER_Y - 1, 3 * 18, 3 * 18);
        PhantomLayout.Box skip = box(CollectorLayout.SKIP_X, CollectorLayout.SKIP_Y, CollectorLayout.SKIP_W, CollectorLayout.OPTION_SIZE);
        assertTrue(filter.contains(grid), "filter grid sticks out of its well");
        assertTrue(filter.contains(skip), "Skip checkbox sticks out of the filter well");

        int icon = PhantomLayout.ICON_SIZE;
        PhantomLayout.Box minus = box(CollectorLayout.MINUS_X, CollectorLayout.RANGE_ICON_Y, icon, icon);
        PhantomLayout.Box plus = box(CollectorLayout.PLUS_X, CollectorLayout.RANGE_ICON_Y, icon, icon);
        PhantomLayout.Box take = box(CollectorLayout.TAKE_X, CollectorLayout.TAKE_Y, icon, icon);
        // Status, coordinates and dimension lines, from the text column to the well's inner edge.
        PhantomLayout.Box lines = box(CollectorLayout.TEXT_X, CollectorLayout.STATUS_Y,
                info.right() - 1 - CollectorLayout.TEXT_X, 2 * CollectorLayout.LINE_H + FONT_HEIGHT);
        PhantomLayout.Box rangeLabel = box(CollectorLayout.TEXT_X, CollectorLayout.RANGE_Y, "Range".length() * CHAR_WIDTH, FONT_HEIGHT);
        // The longest the total can read: just under a million, or the int limit shortened.
        int xpWidth = Math.max(("XP " + CollectorLayout.formatXp(999_999)).length(),
                ("XP " + CollectorLayout.formatXp(Integer.MAX_VALUE)).length()) * CHAR_WIDTH;
        PhantomLayout.Box xpLabel = box(CollectorLayout.TEXT_X, CollectorLayout.XP_Y, xpWidth, FONT_HEIGHT);
        for (PhantomLayout.Box b : List.of(minus, plus, take, lines, rangeLabel)) {
            assertTrue(info.contains(b), b + " sticks out of the info well");
        }
        assertApart(List.of(minus, plus, take, lines, rangeLabel), "info well");
        assertFalse(xpLabel.overlaps(take), "XP total runs into the take button");

        PhantomLayout.Box invSlots = box(CollectorLayout.INV_X - 1, CollectorLayout.INV_Y - 1, 9 * 18,
                CollectorLayout.HOTBAR_Y + 17 - CollectorLayout.INV_Y);
        assertTrue(inventory.contains(invSlots), "inventory slots stick out of their well");
        PhantomLayout.Box stay = box(CollectorLayout.STAY_X, CollectorLayout.STAY_Y, CollectorLayout.STAY_W, CollectorLayout.OPTION_SIZE);
        PhantomLayout.Box dismiss = box(CollectorLayout.DISMISS_X, CollectorLayout.DISMISS_Y, icon, icon);
        assertApart(List.of(
                PhantomLayout.headerIconBox(),
                box(PhantomLayout.TITLE_X, PhantomLayout.TITLE_Y, CollectorLayout.MAX_TITLE_WIDTH, FONT_HEIGHT),
                filter, info, stay, dismiss,
                box(CollectorLayout.INV_X, CollectorLayout.INV_LABEL_Y, "Inventory".length() * CHAR_WIDTH, FONT_HEIGHT),
                inventory), "collector");
    }
}
