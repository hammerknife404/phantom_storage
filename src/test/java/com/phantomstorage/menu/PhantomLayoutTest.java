package com.phantomstorage.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Guards the hand-computed pixel math in {@link PhantomLayout}. */
class PhantomLayoutTest {
    /** Inner edge of the panel's bevelled border. */
    private static final int BORDER = 3;
    private static final int FONT_HEIGHT = 9;
    /** Generous per-character width of Minecraft's default font. */
    private static final int CHAR_WIDTH = 6;

    enum Layout {
        TALL(PhantomLayout.TALL), WIDE(PhantomLayout.WIDE);

        final PhantomLayout value;

        Layout(PhantomLayout value) {
            this.value = value;
        }
    }

    record Rect(String name, int x, int y, int w, int h) {
        boolean overlaps(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    /** Every drawn slot frame plus the icon buttons, matching PhantomChestScreen#renderBg. */
    private static List<Rect> slotFrames(PhantomLayout l) {
        List<Rect> rects = new ArrayList<>();
        grid(rects, "storage", l.storageX(), l.storageY(), 12, 9);
        grid(rects, "craft", l.craftX(), l.craftY(), 3, 3);
        grid(rects, "void", l.voidX(), l.voidY(), 3, 3);
        grid(rects, "inv", l.invX(), l.invY(), 9, 3);
        grid(rects, "hotbar", l.invX(), l.hotbarY(), 9, 1);
        rects.add(new Rect("result", l.resultX() - 5, l.resultY() - 5, 26, 26));
        rects.add(new Rect("trash", l.trashX(), l.trashY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        rects.add(new Rect("recipes", l.recipesX(), l.recipesY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        return rects;
    }

    private static void grid(List<Rect> out, String name, int x, int y, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out.add(new Rect(name + "[" + c + "," + r + "]", x - 1 + c * 18, y - 1 + r * 18, 18, 18));
            }
        }
    }

    private static List<Rect> labels(PhantomLayout l) {
        List<Rect> rects = new ArrayList<>();
        rects.add(label("Phantom Chest", 8, 5));
        rects.add(label("Crafting", l.craftLabelX(), l.craftLabelY()));
        rects.add(label("Void", l.voidLabelX(), l.voidLabelY()));
        if (l.invLabelX() >= 0) {
            rects.add(label("Inventory", l.invLabelX(), l.invLabelY()));
        }
        return rects;
    }

    private static Rect label(String text, int x, int y) {
        return new Rect(text, x, y, text.length() * CHAR_WIDTH, FONT_HEIGHT);
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void slotsStayInsidePanel(Layout layout) {
        PhantomLayout l = layout.value;
        for (Rect r : slotFrames(l)) {
            assertTrue(r.x >= BORDER && r.y >= BORDER
                            && r.x + r.w <= l.width() - BORDER && r.y + r.h <= l.height() - BORDER,
                    layout + ": " + r + " leaves the panel");
        }
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void slotsDoNotOverlap(Layout layout) {
        List<Rect> rects = slotFrames(layout.value);
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(rects.get(i).overlaps(rects.get(j)), layout + ": " + rects.get(i) + " overlaps " + rects.get(j));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void labelsDoNotOverlapSlots(Layout layout) {
        for (Rect label : labels(layout.value)) {
            for (Rect slot : slotFrames(layout.value)) {
                assertFalse(label.overlaps(slot), layout + ": " + label + " overlaps " + slot);
            }
        }
    }

    /** The sort icon follows the title with padding, for any title length, without touching anything. */
    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
            "TALL, Phantom Chest", "WIDE, Phantom Chest",
            "TALL, A Very Long Custom Name For My Chest", "WIDE, A Very Long Custom Name For My Chest",
            "TALL, ''", "WIDE, ''",
    })
    void sortIconFollowsTitle(Layout layout, String title) {
        PhantomLayout l = layout.value;
        int titleWidth = Math.min(title.length() * CHAR_WIDTH, l.maxTitleWidth());
        Rect sort = new Rect("sort", l.sortXAfterTitle(titleWidth), l.sortY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE);
        Rect drawnTitle = new Rect("title", PhantomLayout.TITLE_X, PhantomLayout.TITLE_Y, titleWidth, FONT_HEIGHT);

        assertTrue(sort.x() >= drawnTitle.x() + drawnTitle.w() + PhantomLayout.SORT_GAP, "sort icon lost its padding");
        assertTrue(sort.x() <= l.sortX(), "sort icon went past its limit");
        assertTrue(sort.y() >= BORDER && sort.x() + sort.w() <= l.width() - BORDER, "sort icon leaves the panel");
        for (Rect other : slotFrames(l)) {
            assertFalse(sort.overlaps(other), layout + ": " + sort + " overlaps " + other);
        }
        for (Rect label : labels(l)) {
            if (!label.name().equals("Phantom Chest")) {
                assertFalse(sort.overlaps(label), layout + ": " + sort + " overlaps " + label);
            }
        }
    }

    private static Rect rect(String name, PhantomLayout.Box b) {
        return new Rect(name, b.x(), b.y(), b.w(), b.h());
    }

    private static List<Rect> wellRects(PhantomLayout l) {
        List<Rect> rects = new ArrayList<>();
        for (PhantomLayout.Well w : l.wells()) {
            rects.add(rect(w.isVoid() ? "voidWell" : "well", w.box()));
        }
        return rects;
    }

    private static boolean inside(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.x() + inner.w() <= outer.x() + outer.w() && inner.y() + inner.h() <= outer.y() + outer.h();
    }

    @ParameterizedTest
    @EnumSource(Layout.class)
    void wellsStayInsidePanelAndApart(Layout layout) {
        PhantomLayout l = layout.value;
        List<Rect> wells = wellRects(l);
        for (Rect w : wells) {
            assertTrue(w.x() >= BORDER && w.y() >= BORDER
                    && w.x() + w.w() <= l.width() - BORDER && w.y() + w.h() <= l.height() - BORDER, layout + ": " + w + " leaves the panel");
        }
        for (int i = 0; i < wells.size(); i++) {
            for (int j = i + 1; j < wells.size(); j++) {
                assertFalse(wells.get(i).overlaps(wells.get(j)), layout + ": " + wells.get(i) + " overlaps " + wells.get(j));
            }
        }
    }

    /** Every slot and the "+" sit inside a well; labels, sort and trash stay clear of all wells. */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void wellsFrameTheirGroupsOnly(Layout layout) {
        PhantomLayout l = layout.value;
        List<Rect> wells = wellRects(l);
        for (Rect frame : slotFrames(l)) {
            if (frame.name().equals("trash")) {
                continue;
            }
            assertTrue(wells.stream().anyMatch(w -> inside(w, frame)), layout + ": " + frame + " is outside every well");
        }
        List<Rect> mustStayClear = new ArrayList<>(labels(l));
        mustStayClear.add(new Rect("trash", l.trashX(), l.trashY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        mustStayClear.add(new Rect("sort", l.sortX(), l.sortY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        mustStayClear.add(new Rect("sortAfterTitle", l.sortXAfterTitle(13 * CHAR_WIDTH), l.sortY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        for (Rect clear : mustStayClear) {
            for (Rect w : wells) {
                assertFalse(clear.overlaps(w), layout + ": " + clear + " overlaps " + w);
            }
        }
    }

    /** Decorations only use spare space: inside the panel, touching no well, slot, label or icon. */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void decorationsUseSpareSpaceOnly(Layout layout) {
        PhantomLayout l = layout.value;
        List<Rect> occupied = new ArrayList<>(wellRects(l));
        occupied.addAll(slotFrames(l));
        occupied.addAll(labels(l));
        occupied.add(new Rect("sort", l.sortX(), l.sortY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
        for (PhantomLayout.Box box : l.decorations()) {
            Rect deco = rect("decoration", box);
            assertTrue(deco.x() >= BORDER && deco.y() >= BORDER
                    && deco.x() + deco.w() <= l.width() - BORDER && deco.y() + deco.h() <= l.height() - BORDER, layout + ": " + deco + " leaves the panel");
            for (Rect o : occupied) {
                assertFalse(deco.overlaps(o), layout + ": " + deco + " overlaps " + o);
            }
        }
    }

    /** Scaled GUI sizes from Minecraft's auto GUI scale at common resolutions. */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "1920x1080 scale 4, 480, 270, WIDE",
            "2560x1440 scale 5, 512, 288, WIDE",
            "1440x900 scale 3,  480, 300, WIDE",
            "1366x768 scale 3,  455, 256, WIDE",
            "1280x720 scale 3,  426, 240, WIDE",
            "2560x1440 scale 6, 426, 240, WIDE",
            "800x600 scale 2,   400, 300, TALL",
    })
    void choosesLayoutThatFits(String name, int width, int height, Layout expected) {
        PhantomLayout chosen = PhantomLayout.choose(width, height);
        assertEquals(expected.value, chosen, name);
        assertTrue(chosen.width() <= width && chosen.height() <= height, name + ": chosen layout overflows");
    }
}
