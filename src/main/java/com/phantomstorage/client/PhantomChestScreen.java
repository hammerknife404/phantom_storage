package com.phantomstorage.client;

import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.VoidSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Drawn entirely with fills (no background texture to keep in sync with the layout constants).
 * Height is kept at 266px so it fits a 1080p screen at GUI scale 4.
 */
public class PhantomChestScreen extends AbstractContainerScreen<PhantomChestMenu> {
    private static final int PANEL = 0xFFC8C3D4;
    private static final int OUTLINE = 0xFF000000;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF55506A;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int VOID_FILL = 0xFF4A3563;
    private static final int LABEL = 0x404040;
    private static final int VOID_LABEL = 0x4A2A70;

    private static final Component CRAFTING = Component.translatable("container.phantomstorage.crafting");
    private static final Component VOID = Component.translatable("container.phantomstorage.void");
    private static final Component VOID_TOOLTIP = Component.translatable("tooltip.phantomstorage.void_slot");

    public PhantomChestScreen(PhantomChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PhantomChestMenu.GUI_WIDTH;
        this.imageHeight = PhantomChestMenu.GUI_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);

        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            if (i == PhantomChestMenu.RESULT_SLOT) {
                drawSlot(graphics, sx - 4, sy - 4, 26, 26, SLOT_FILL);
            } else {
                drawSlot(graphics, sx, sy, 18, 18, slot instanceof VoidSlot ? VOID_FILL : SLOT_FILL);
            }
        }
        drawDownArrow(graphics, x + PhantomChestMenu.RESULT_X + 8, y + PhantomChestMenu.CRAFT_Y + 3 * 18 + 1);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL, false);
        graphics.drawString(this.font, CRAFTING, PhantomChestMenu.CRAFT_X, this.titleLabelY, LABEL, false);
        graphics.drawString(this.font, VOID, PhantomChestMenu.VOID_X, PhantomChestMenu.VOID_Y - 11, VOID_LABEL, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot instanceof VoidSlot && !this.hoveredSlot.hasItem()) {
            graphics.renderTooltip(this.font, VOID_TOOLTIP, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HIGHLIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
    }

    private static void drawSlot(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, SLOT_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** A small downward arrow centred on {@code cx}, from the crafting grid to the result slot. */
    private static void drawDownArrow(GuiGraphics g, int cx, int top) {
        g.fill(cx - 1, top, cx + 1, top + 4, SHADOW);
        for (int i = 0; i < 4; i++) {
            g.fill(cx - 4 + i, top + 4 + i, cx + 4 - i, top + 5 + i, SHADOW);
        }
    }
}
