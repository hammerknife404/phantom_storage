package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.storage.PhantomTanks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fill-drawn like {@link PhantomChestScreen}; gauges show each tank's fluid texture at its fill level. */
public class PhantomTankScreen extends AbstractContainerScreen<PhantomTankMenu> {
    private static final int GAUGE_EDGE = 0xFF373737;
    private static final int GAUGE_BACK = 0xFF5E586E;
    private static final int GAUGE_TICK = 0x60FFFFFF;
    private static final int GAUGE_HOVER = 0x40FFFFFF;
    private static final int TILE = 16;

    private static final List<Component> TRASH_TOOLTIP = PhantomChestScreen.tooltip("tank_trash");

    public PhantomTankScreen(PhantomTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PhantomTankMenu.WIDTH;
        this.imageHeight = PhantomTankMenu.HEIGHT;
        this.titleLabelX = PhantomLayout.TITLE_X;
        this.titleLabelY = PhantomLayout.TITLE_Y;
        this.inventoryLabelY = PhantomTankMenu.INV_LABEL_Y;
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
        PhantomChestScreen.drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            PhantomChestScreen.drawSlot(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, PhantomChestScreen.SLOT_FILL);
        }
        int hoveredGauge = this.gaugeAt(mouseX, mouseY);
        int hoveredTrash = this.trashAt(mouseX, mouseY);
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            this.drawGauge(graphics, i, i == hoveredGauge);
            boolean hover = i == hoveredTrash;
            PhantomChestScreen.drawTrash(graphics, x + PhantomTankMenu.trashX(i), y + PhantomTankMenu.TRASH_Y,
                    hover ? PhantomChestScreen.ICON_BODY_HOVER : PhantomChestScreen.ICON_BODY,
                    hover ? PhantomChestScreen.ICON_DETAIL_HOVER : PhantomChestScreen.ICON_DETAIL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, PhantomChestScreen.LABEL, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                PhantomChestScreen.LABEL, false);
    }

    private void drawGauge(GuiGraphics g, int tank, boolean hovered) {
        int x0 = this.leftPos + PhantomTankMenu.gaugeX(tank);
        int y0 = this.topPos + PhantomTankMenu.GAUGE_Y;
        int x1 = x0 + PhantomTankMenu.GAUGE_W;
        int y1 = y0 + PhantomTankMenu.GAUGE_H;
        g.fill(x0, y0, x1, y1, GAUGE_EDGE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GAUGE_BACK);

        FluidStack fluid = this.menu.getFluid(tank);
        int inner = PhantomTankMenu.GAUGE_H - 2;
        int height = fluid.isEmpty() ? 0 : Math.max(1, Math.round(inner * fluid.getAmount() / (float) PhantomTanks.CAPACITY));
        if (height > 0) {
            drawFluid(g, fluid, x0 + 1, y1 - 1 - height, x1 - 1, y1 - 1);
        }
        // Quarter marks, so levels can be read at a glance.
        for (int q = 1; q < 4; q++) {
            int ty = y1 - 1 - inner * q / 4;
            g.fill(x0 + 1, ty, x0 + 5, ty + 1, GAUGE_TICK);
        }
        if (hovered) {
            g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GAUGE_HOVER);
        }
    }

    /** Tiles the fluid's still texture from the bottom up, clipped to the box. */
    private void drawFluid(GuiGraphics g, FluidStack fluid, int x0, int y0, int x1, int y1) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        float a = (tint >>> 24 & 0xFF) / 255.0F;
        float r = (tint >> 16 & 0xFF) / 255.0F;
        float gr = (tint >> 8 & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        g.enableScissor(x0, y0, x1, y1);
        for (int ty = y1 - TILE; ty > y0 - TILE; ty -= TILE) {
            for (int tx = x0; tx < x1; tx += TILE) {
                g.blit(tx, ty, 0, TILE, TILE, sprite, r, gr, b, a);
            }
        }
        g.disableScissor();
        RenderSystem.disableBlend();
    }

    // ---- tooltips ------------------------------------------------------------------------------

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int gauge = this.gaugeAt(mouseX, mouseY);
        if (gauge >= 0) {
            graphics.renderComponentTooltip(this.font, this.gaugeTooltip(gauge), mouseX, mouseY);
            return;
        }
        if (this.menu.getCarried().isEmpty() && this.trashAt(mouseX, mouseY) >= 0) {
            graphics.renderComponentTooltip(this.font, TRASH_TOOLTIP, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private List<Component> gaugeTooltip(int tank) {
        FluidStack fluid = this.menu.getFluid(tank);
        Component name = fluid.isEmpty()
                ? Component.translatable("tooltip.phantomstorage.tank_empty")
                : fluid.getHoverName();
        return List.of(
                name,
                Component.translatable("tooltip.phantomstorage.tank_amount",
                        String.format("%,d", fluid.getAmount()), String.format("%,d", PhantomTanks.CAPACITY))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.phantomstorage.tank_pour").withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("tooltip.phantomstorage.tank_take").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int menuButton = -1;
        int gauge = this.gaugeAt(mouseX, mouseY);
        int trash = this.trashAt(mouseX, mouseY);
        if (gauge >= 0 && (button == 0 || button == 1)) {
            if (this.menu.getCarried().isEmpty()) {
                return true; // nothing to pour or fill; don't fall through to slot handling
            }
            menuButton = gauge * PhantomTankMenu.ACTIONS + (button == 0 ? PhantomTankMenu.ACTION_POUR : PhantomTankMenu.ACTION_TAKE);
        } else if (trash >= 0 && button == 0) {
            menuButton = trash * PhantomTankMenu.ACTIONS + PhantomTankMenu.ACTION_VOID;
        }
        if (menuButton >= 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            // The server validates the menu and does the work; see PhantomTankMenu#clickMenuButton.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, menuButton);
            if (trash >= 0) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int gaugeAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            int x = PhantomTankMenu.gaugeX(i);
            if (gx >= x && gx < x + PhantomTankMenu.GAUGE_W && gy >= PhantomTankMenu.GAUGE_Y
                    && gy < PhantomTankMenu.GAUGE_Y + PhantomTankMenu.GAUGE_H) {
                return i;
            }
        }
        return -1;
    }

    private int trashAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            int x = PhantomTankMenu.trashX(i);
            if (gx >= x && gx < x + PhantomLayout.ICON_SIZE && gy >= PhantomTankMenu.TRASH_Y
                    && gy < PhantomTankMenu.TRASH_Y + PhantomLayout.ICON_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
