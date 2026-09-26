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
        rects.add(new Rect("sort", l.sortX(), l.sortY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE));
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

    /** Scaled GUI sizes from Minecraft's auto GUI scale at common resolutions. */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "1920x1080 scale 4, 480, 270, TALL",
            "2560x1440 scale 5, 512, 288, TALL",
            "1440x900 scale 3,  480, 300, TALL",
            "1366x768 scale 3,  455, 256, WIDE",
            "1280x720 scale 3,  426, 240, WIDE",
            "2560x1440 scale 6, 426, 240, WIDE",
    })
    void choosesLayoutThatFits(String name, int width, int height, Layout expected) {
        PhantomLayout chosen = PhantomLayout.choose(width, height);
        assertEquals(expected.value, chosen, name);
        assertTrue(chosen.width() <= width && chosen.height() <= height, name + ": chosen layout overflows");
    }
}
