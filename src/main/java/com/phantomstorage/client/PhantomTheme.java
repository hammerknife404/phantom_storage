package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phantomstorage.menu.PhantomLayout;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * "Phantom Night": the one look every pet screen shares, taken from the chest and charm art. Night
 * violet panels in a gold-trimmed frame, slot groups sunk into darker wells, cyan (the art's gems) for
 * whatever is active, selected or hovered, and the pet's charm art beside each title. Everything is
 * drawn with fills, so screens can follow their layouts with no background textures to keep in sync.
 */
public final class PhantomTheme {
    // Frame and panel.
    private static final int OUTLINE = 0xFF0B0814;
    private static final int TRIM_LIGHT = 0xFFEDAF60;
    private static final int TRIM_DARK = 0xFF806432;
    private static final int PANEL_TOP = 0xFF30244A;
    private static final int PANEL_BOTTOM = 0xFF231A36;
    // Wells: a recess behind each slot group; the void filter's is tinted.
    private static final int WELL_EDGE = 0xFF120C1E;
    private static final int WELL_FILL = 0xFF1B142A;
    private static final int VOID_WELL_EDGE = 0xFF1E0A24;
    private static final int VOID_WELL_FILL = 0xFF2A0F33;
    // Slots: sunk like vanilla's (dark top-left, light bottom-right), in the night palette.
    private static final int SLOT_DARK = 0xFF0C0816;
    private static final int SLOT_LIGHT = 0xFF5E4A78;
    public static final int SLOT_FILL = 0xFF2A2040;
    public static final int VOID_FILL = 0xFF45185A;
    /** An empty gauge or other read-out, darker than a slot. */
    public static final int READOUT_FILL = 0xFF150F22;
    // Text.
    public static final int TITLE = 0xEDAF60;
    public static final int LABEL = 0xCDBCE8;
    public static final int VOID_LABEL = 0xE07AF0;
    // Icons and accents.
    private static final int ICON_OUTLINE = 0xFF0C0816;
    private static final int ICON_BODY = 0xFF8A78A8;
    private static final int ICON_DETAIL = 0xFF54446E;
    /** Active, selected, hovered, on: the cyan of the art's gems. */
    public static final int ACCENT = 0xFF3FD8E6;
    private static final int ACCENT_DARK = 0xFF1E8C96;
    /** Faint marks on a read-out, like a gauge's quarter lines. */
    public static final int TICK = 0x70CDBCE8;
    /** A soft wash over a hovered read-out. */
    public static final int HOVER = 0x30FFFFFF;
    private static final int ARROW = ICON_BODY;
    // Tooltips on these screens: the frame's gold trim around vanilla's dark body.
    private static final int TOOLTIP_BACKGROUND = 0xF0150F22;
    private static final int TOOLTIP_BORDER_TOP = 0xB0EDAF60;
    private static final int TOOLTIP_BORDER_BOTTOM = 0xB0806432;

    /** Pet charm art beside the title, scaled from its 64x64 sprite. */
    private static final int CHARM_TEXTURE_SIZE = 64;

    private PhantomTheme() {}

    // ---- frame ---------------------------------------------------------------------------------

    /** The panel: dark outline, gold trim lit from the top-left, night-violet gradient inside. */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, TRIM_LIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, TRIM_DARK);
        g.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, PANEL_TOP, PANEL_BOTTOM);
    }

    /** The pet's charm art at the header spot, so every screen says whose it is at a glance. */
    public static void drawHeaderIcon(GuiGraphics g, int x, int y, ResourceLocation charm) {
        drawCharm(g, x + PhantomLayout.HEADER_ICON_X, y + PhantomLayout.HEADER_ICON_Y, PhantomLayout.HEADER_ICON_SIZE, charm, 1.0F);
    }

    /** {@code size} px of charm art at (x, y), at {@code alpha} (decorations are faint). */
    public static void drawCharm(GuiGraphics g, int x, int y, int size, ResourceLocation charm, float alpha) {
        RenderSystem.enableBlend();
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(charm, x, y, size, size, 0.0F, 0.0F, CHARM_TEXTURE_SIZE, CHARM_TEXTURE_SIZE, CHARM_TEXTURE_SIZE, CHARM_TEXTURE_SIZE);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /** The title after the header icon, in the trim's gold; long names are cut to {@code maxWidth}. Label space. */
    public static void drawTitle(GuiGraphics g, Font font, Component title, int maxWidth) {
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(title, maxWidth)),
                PhantomLayout.TITLE_X, PhantomLayout.TITLE_Y, TITLE, false);
    }

    public static void drawLabel(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, LABEL, false);
    }

    // ---- wells and slots -----------------------------------------------------------------------

    /** A recess behind a slot group, (x0, y0) to (x1, y1): edge on the sides and bottom, flush at the top. */
    public static void drawWell(GuiGraphics g, int x0, int y0, int x1, int y1, boolean isVoid) {
        g.fill(x0, y0, x1, y1, isVoid ? VOID_WELL_EDGE : WELL_EDGE);
        g.fill(x0 + 1, y0, x1 - 1, y1 - 1, isVoid ? VOID_WELL_FILL : WELL_FILL);
    }

    public static void drawWell(GuiGraphics g, int ox, int oy, PhantomLayout.Well well) {
        PhantomLayout.Box b = well.box();
        drawWell(g, ox + b.x(), oy + b.y(), ox + b.right(), oy + b.bottom(), well.isVoid());
    }

    /** A sunken frame (slot, result, gauge, checkbox) with {@code fill} inside. */
    public static void drawSlot(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, SLOT_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** A checkbox the size of {@code size}: a small slot, lit with the accent when on. */
    public static void drawCheckbox(GuiGraphics g, int x, int y, int size, boolean on) {
        drawSlot(g, x, y, size, size, SLOT_FILL);
        if (on) {
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, ACCENT);
        }
    }

    /** Frame around whatever is active (the selected gauge). */
    public static void drawActiveOutline(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, ACCENT);
    }

    // ---- arrows and icons ----------------------------------------------------------------------

    /** A small downward arrow centred on {@code cx}. */
    public static void drawDownArrow(GuiGraphics g, int cx, int top) {
        g.fill(cx - 1, top, cx + 1, top + 4, ARROW);
        for (int i = 0; i < 4; i++) {
            g.fill(cx - 4 + i, top + 4 + i, cx + 4 - i, top + 5 + i, ARROW);
        }
    }

    /** A small right-pointing arrow centred on {@code cy}. */
    public static void drawRightArrow(GuiGraphics g, int left, int cy) {
        g.fill(left + 1, cy - 1, left + 7, cy + 1, ARROW);
        for (int i = 0; i < 4; i++) {
            g.fill(left + 7 + i, cy - 4 + i, left + 8 + i, cy + 4 - i, ARROW);
        }
    }

    /** 12x12: three bars, longest to shortest top-down for ascending, shortest to longest for descending. */
    public static void drawSort(GuiGraphics g, int x, int y, boolean hovered, boolean descending) {
        int body = body(hovered);
        int[] widths = descending ? new int[] {4, 7, 10} : new int[] {10, 7, 4};
        for (int i = 0; i < widths.length; i++) {
            int top = y + i * 4;
            g.fill(x, top, x + widths[i] + 2, top + 4, ICON_OUTLINE);
            g.fill(x + 1, top + 1, x + widths[i] + 1, top + 3, body);
        }
    }

    /** 12x12 trash can. */
    public static void drawTrash(GuiGraphics g, int x, int y, boolean hovered) {
        int body = body(hovered);
        int rib = hovered ? ACCENT_DARK : ICON_DETAIL;
        g.fill(x + 4, y, x + 8, y + 1, ICON_OUTLINE);
        g.fill(x + 4, y + 1, x + 5, y + 2, ICON_OUTLINE);
        g.fill(x + 7, y + 1, x + 8, y + 2, ICON_OUTLINE);
        g.fill(x, y + 2, x + 12, y + 4, ICON_OUTLINE);
        g.fill(x + 1, y + 2, x + 11, y + 3, body);
        g.fill(x + 1, y + 4, x + 11, y + 12, ICON_OUTLINE);
        g.fill(x + 2, y + 4, x + 10, y + 11, body);
        g.fill(x + 4, y + 5, x + 5, y + 10, rib);
        g.fill(x + 7, y + 5, x + 8, y + 10, rib);
    }

    /** 12x12 plus sign. */
    public static void drawPlus(GuiGraphics g, int x, int y, boolean hovered) {
        int body = body(hovered);
        g.fill(x + 4, y, x + 8, y + 12, ICON_OUTLINE);
        g.fill(x, y + 4, x + 12, y + 8, ICON_OUTLINE);
        g.fill(x + 5, y + 1, x + 7, y + 11, body);
        g.fill(x + 1, y + 5, x + 11, y + 7, body);
    }

    private static int body(boolean hovered) {
        return hovered ? ACCENT : ICON_BODY;
    }

    // ---- tooltips ------------------------------------------------------------------------------

    /** Title line plus a grey description line, from {@code tooltip.phantomstorage.<key>[.desc]}. */
    public static List<Component> tooltip(String key) {
        return List.of(
                Component.translatable("tooltip.phantomstorage." + key),
                Component.translatable("tooltip.phantomstorage." + key + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** Every tooltip shown over a pet screen, items' included, wears the panel's gold trim. */
    static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (isPetScreen()) {
            event.setBackground(TOOLTIP_BACKGROUND);
            event.setBorderStart(TOOLTIP_BORDER_TOP);
            event.setBorderEnd(TOOLTIP_BORDER_BOTTOM);
        }
    }

    private static boolean isPetScreen() {
        return Minecraft.getInstance().screen instanceof PhantomChestScreen
                || Minecraft.getInstance().screen instanceof PhantomTankScreen
                || Minecraft.getInstance().screen instanceof PhantomAnchorScreen;
    }
}
