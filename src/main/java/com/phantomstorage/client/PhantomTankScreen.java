package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.menu.TankLayout;
import com.phantomstorage.storage.PhantomTanks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Drawn in the shared {@link PhantomTheme}, like the chest screen: the gauges and the inventory each sit
 * in a well, each gauge is a sunken read-out showing its tank's fluid texture at its fill level, and
 * the active tank is outlined in the accent.
 */
public class PhantomTankScreen extends AbstractContainerScreen<PhantomTankMenu> {
    static final ResourceLocation CHARM = PhantomStorage.id("textures/item/phantom_charm_tank.png");
    private static final int TILE = 16;

    private static final List<Component> TRASH_TOOLTIP = PhantomTheme.tooltip("tank_trash");
    private static final List<Component> OVERFILL_TOOLTIP = PhantomTheme.tooltip("tank_overfill");
    private static final List<Component> DRAIN_ALL_TOOLTIP = PhantomTheme.tooltip("tank_drain_all");
    private static final Component OVERFILL = Component.translatable("container.phantomstorage.overfill");
    private static final Component DRAIN_ALL = Component.translatable("container.phantomstorage.drain_all");

    public PhantomTankScreen(PhantomTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TankLayout.WIDTH;
        this.imageHeight = TankLayout.HEIGHT;
        this.titleLabelX = PhantomLayout.TITLE_X;
        this.titleLabelY = PhantomLayout.TITLE_Y;
        this.inventoryLabelY = TankLayout.INV_LABEL_Y;
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
        PhantomTheme.drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);
        PhantomTheme.drawHeaderIcon(graphics, x, y, CHARM);
        for (PhantomLayout.Box well : List.of(TankLayout.gaugeWell(), TankLayout.inventoryWell())) {
            PhantomTheme.drawWell(graphics, x + well.x(), y + well.y(), x + well.right(), y + well.bottom(), false);
        }
        for (Slot slot : this.menu.slots) {
            PhantomTheme.drawSlot(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, PhantomTheme.SLOT_FILL);
        }
        int hoveredGauge = this.gaugeAt(mouseX, mouseY);
        int hoveredTrash = this.trashAt(mouseX, mouseY);
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            this.drawGauge(graphics, i, i == hoveredGauge);
            PhantomTheme.drawTrash(graphics, x + TankLayout.trashX(i), y + TankLayout.TRASH_Y, i == hoveredTrash);
        }
        int size = TankLayout.OPTION_SIZE;
        PhantomTheme.drawCheckbox(graphics, x + TankLayout.OVERFILL_X, y + TankLayout.OPTION_Y, size, this.menu.isOverfill());
        PhantomTheme.drawCheckbox(graphics, x + TankLayout.DRAIN_ALL_X, y + TankLayout.OPTION_Y, size, this.menu.isDrainAll());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        PhantomTheme.drawTitle(graphics, this.font, this.title, TankLayout.MAX_TITLE_WIDTH);
        PhantomTheme.drawLabel(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY);
        int labelY = TankLayout.OPTION_Y + 1;
        PhantomTheme.drawLabel(graphics, this.font, OVERFILL, TankLayout.OVERFILL_X + TankLayout.OPTION_SIZE + 3, labelY);
        PhantomTheme.drawLabel(graphics, this.font, DRAIN_ALL, TankLayout.DRAIN_ALL_X + TankLayout.OPTION_SIZE + 3, labelY);
    }

    private void drawGauge(GuiGraphics g, int tank, boolean hovered) {
        int x0 = this.leftPos + TankLayout.gaugeX(tank);
        int y0 = this.topPos + TankLayout.GAUGE_Y;
        int x1 = x0 + TankLayout.GAUGE_W;
        int y1 = y0 + TankLayout.GAUGE_H;
        if (tank == this.menu.getActive()) {
            PhantomTheme.drawActiveOutline(g, x0, y0, x1, y1);
        }
        PhantomTheme.drawSlot(g, x0, y0, TankLayout.GAUGE_W, TankLayout.GAUGE_H, PhantomTheme.READOUT_FILL);

        FluidStack fluid = this.menu.getFluid(tank);
        int inner = TankLayout.GAUGE_H - 2;
        int height = fluid.isEmpty() ? 0 : Math.max(1, Math.round(inner * fluid.getAmount() / (float) PhantomTanks.CAPACITY));
        if (height > 0) {
            drawFluid(g, fluid, x0 + 1, y1 - 1 - height, x1 - 1, y1 - 1);
        }
        // Quarter marks, so levels can be read at a glance.
        for (int q = 1; q < 4; q++) {
            int ty = y1 - 1 - inner * q / 4;
            g.fill(x0 + 1, ty, x0 + 5, ty + 1, PhantomTheme.TICK);
        }
        if (hovered) {
            g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PhantomTheme.HOVER);
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
        int option = this.optionAt(mouseX, mouseY);
        if (this.menu.getCarried().isEmpty() && option >= 0) {
            graphics.renderComponentTooltip(this.font,
                    option == PhantomTankMenu.BUTTON_OVERFILL ? OVERFILL_TOOLTIP : DRAIN_ALL_TOOLTIP, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    /** Name, then grey lines like every other tooltip here; the active tank's line in the accent. */
    private List<Component> gaugeTooltip(int tank) {
        FluidStack fluid = this.menu.getFluid(tank);
        Component name = fluid.isEmpty()
                ? Component.translatable("tooltip.phantomstorage.tank_empty")
                : fluid.getHoverName();
        boolean active = tank == this.menu.getActive();
        return List.of(
                name,
                Component.translatable("tooltip.phantomstorage.tank_amount",
                        String.format("%,d", fluid.getAmount()), String.format("%,d", PhantomTanks.CAPACITY))
                        .withStyle(ChatFormatting.GRAY),
                active
                        ? Component.translatable("tooltip.phantomstorage.tank_active").withStyle(ChatFormatting.AQUA)
                        : Component.translatable("tooltip.phantomstorage.tank_select").withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.phantomstorage.tank_pour").withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.phantomstorage.tank_take").withStyle(ChatFormatting.GRAY));
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int menuButton = -1;
        int gauge = this.gaugeAt(mouseX, mouseY);
        int trash = this.trashAt(mouseX, mouseY);
        int option = this.optionAt(mouseX, mouseY);
        if (option >= 0 && button == 0) {
            menuButton = option;
        } else if (gauge >= 0 && (button == 0 || button == 1)) {
            int action = this.menu.getCarried().isEmpty() ? PhantomTankMenu.ACTION_SELECT
                    : button == 0 ? PhantomTankMenu.ACTION_POUR
                    : PhantomTankMenu.ACTION_TAKE;
            menuButton = gauge * PhantomTankMenu.ACTIONS + action;
        } else if (trash >= 0 && button == 0) {
            menuButton = trash * PhantomTankMenu.ACTIONS + PhantomTankMenu.ACTION_VOID;
        }
        if (menuButton >= 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            // The server validates the menu and does the work; see PhantomTankMenu#clickMenuButton.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, menuButton);
            if (trash >= 0 || option >= 0 || this.menu.getCarried().isEmpty()) {
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
            int x = TankLayout.gaugeX(i);
            if (gx >= x && gx < x + TankLayout.GAUGE_W && gy >= TankLayout.GAUGE_Y
                    && gy < TankLayout.GAUGE_Y + TankLayout.GAUGE_H) {
                return i;
            }
        }
        return -1;
    }

    /** The checkbox's button id under the mouse, or -1. */
    private int optionAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        if (gy < TankLayout.OPTION_Y || gy >= TankLayout.OPTION_Y + TankLayout.OPTION_SIZE) {
            return -1;
        }
        if (gx >= TankLayout.OVERFILL_X && gx < TankLayout.OVERFILL_X + TankLayout.OPTION_W) {
            return PhantomTankMenu.BUTTON_OVERFILL;
        }
        if (gx >= TankLayout.DRAIN_ALL_X && gx < TankLayout.DRAIN_ALL_X + TankLayout.OPTION_W) {
            return PhantomTankMenu.BUTTON_DRAIN_ALL;
        }
        return -1;
    }

    private int trashAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            int x = TankLayout.trashX(i);
            if (gx >= x && gx < x + PhantomLayout.ICON_SIZE && gy >= TankLayout.TRASH_Y
                    && gy < TankLayout.TRASH_Y + PhantomLayout.ICON_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
