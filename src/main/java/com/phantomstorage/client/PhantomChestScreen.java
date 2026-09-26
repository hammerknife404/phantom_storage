package com.phantomstorage.client;

import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.menu.VoidSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Drawn entirely with fills, so it follows whichever {@link PhantomLayout} the menu was opened with
 * (no background textures to keep in sync).
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
    private static final Component TRASH_TOOLTIP = Component.translatable("tooltip.phantomstorage.trash");
    private static final int TRASH_OUTLINE = 0xFF2A2536;
    private static final int TRASH_BODY = 0xFF8A8499;
    private static final int TRASH_RIB = 0xFF5E586E;
    private static final int TRASH_BODY_HOVER = 0xFFB07CE8;
    private static final int TRASH_RIB_HOVER = 0xFF7A48B0;

    private final PhantomLayout layout;

    public PhantomChestScreen(PhantomChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.layout = menu.getLayout();
        this.imageWidth = this.layout.width();
        this.imageHeight = this.layout.height();
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
        if (this.layout.wide()) {
            drawRightArrow(graphics, x + this.layout.craftX() + 3 * 18 + 1, y + this.layout.resultY() + 8);
        } else {
            drawDownArrow(graphics, x + this.layout.resultX() + 8, y + this.layout.craftY() + 3 * 18 + 1);
        }
        drawTrash(graphics, x + this.layout.trashX(), y + this.layout.trashY(), this.isOverTrash(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isOverTrash(mouseX, mouseY)
                && this.minecraft != null && this.minecraft.gameMode != null) {
            // The server validates the menu and does the deleting; see PhantomChestMenu#clickMenuButton.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, PhantomChestMenu.BUTTON_TRASH);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverTrash(double mouseX, double mouseY) {
        double tx = this.leftPos + this.layout.trashX();
        double ty = this.topPos + this.layout.trashY();
        return mouseX >= tx && mouseX < tx + PhantomLayout.TRASH_SIZE
                && mouseY >= ty && mouseY < ty + PhantomLayout.TRASH_SIZE;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL, false);
        graphics.drawString(this.font, CRAFTING, this.layout.craftLabelX(), this.layout.craftLabelY(), LABEL, false);
        graphics.drawString(this.font, VOID, this.layout.voidLabelX(), this.layout.voidLabelY(), VOID_LABEL, false);
        if (this.layout.invLabelX() >= 0) {
            graphics.drawString(this.font, this.playerInventoryTitle, this.layout.invLabelX(), this.layout.invLabelY(), LABEL, false);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.isOverTrash(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, TRASH_TOOLTIP, mouseX, mouseY);
            return;
        }
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

    /** 12x12 pixel trash can; glows purple on hover. */
    private static void drawTrash(GuiGraphics g, int x, int y, boolean hovered) {
        int body = hovered ? TRASH_BODY_HOVER : TRASH_BODY;
        int rib = hovered ? TRASH_RIB_HOVER : TRASH_RIB;
        // handle
        g.fill(x + 4, y, x + 8, y + 1, TRASH_OUTLINE);
        g.fill(x + 4, y + 1, x + 5, y + 2, TRASH_OUTLINE);
        g.fill(x + 7, y + 1, x + 8, y + 2, TRASH_OUTLINE);
        // lid
        g.fill(x, y + 2, x + 12, y + 4, TRASH_OUTLINE);
        g.fill(x + 1, y + 2, x + 11, y + 3, body);
        // can
        g.fill(x + 1, y + 4, x + 11, y + 12, TRASH_OUTLINE);
        g.fill(x + 2, y + 4, x + 10, y + 11, body);
        g.fill(x + 4, y + 5, x + 5, y + 10, rib);
        g.fill(x + 7, y + 5, x + 8, y + 10, rib);
    }

    /** A small right-pointing arrow centred on {@code cy}, from the crafting grid to the result slot. */
    private static void drawRightArrow(GuiGraphics g, int left, int cy) {
        g.fill(left + 1, cy - 1, left + 7, cy + 1, SHADOW);
        for (int i = 0; i < 4; i++) {
            g.fill(left + 7 + i, cy - 4 + i, left + 8 + i, cy + 4 - i, SHADOW);
        }
    }
}
