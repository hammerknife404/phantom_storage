package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.CollectorLayout;
import com.phantomstorage.menu.GhostSlot;
import com.phantomstorage.menu.PhantomCollectorMenu;
import com.phantomstorage.menu.PhantomLayout;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The collector's panel in the shared {@link PhantomTheme}: its filter in one well (ghost entries,
 * veiled like the chest's void filter, with a Skip checkbox), where it is, its range and banked
 * experience in another, then Stay here, dismiss, and the player inventory.
 */
public class PhantomCollectorScreen extends AbstractContainerScreen<PhantomCollectorMenu> {
    static final ResourceLocation CHARM = PhantomStorage.id("textures/item/phantom_charm_collector.png");
    /** Vanilla's experience orb sheet: 4x4 frames of 16 px, smallest orb first; this one reads at icon size. */
    private static final ResourceLocation ORB = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final float ORB_U = 32.0F;
    private static final float ORB_V = 16.0F;
    private static final int ORB_TINT = 0xFF80FF20;

    private static final Component FOLLOWING = Component.translatable("container.phantomstorage.collector.following");
    private static final Component ANCHORED = Component.translatable("container.phantomstorage.collector.anchored");
    private static final Component RANGE = Component.translatable("container.phantomstorage.collector.range");
    private static final Component SKIP = Component.translatable("container.phantomstorage.collector.skip");
    private static final Component STAY = Component.translatable("container.phantomstorage.anchor.stay");
    private static final List<Component> FILTER_TOOLTIP = PhantomTheme.tooltip("collector_filter");
    private static final List<Component> SKIP_TOOLTIP = PhantomTheme.tooltip("collector_skip");
    private static final List<Component> RANGE_TOOLTIP = PhantomTheme.tooltip("collector_range");
    private static final List<Component> TAKE_TOOLTIP = PhantomTheme.tooltip("collector_take");
    private static final List<Component> STAY_TOOLTIP = PhantomTheme.tooltip("collector_stay");
    private static final List<Component> DISMISS_TOOLTIP = PhantomTheme.tooltip("collector_dismiss");

    private enum Control { RANGE_DOWN, RANGE_UP, SKIP, TAKE, STAY, DISMISS }

    public PhantomCollectorScreen(PhantomCollectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = CollectorLayout.WIDTH;
        this.imageHeight = CollectorLayout.HEIGHT;
        this.titleLabelX = PhantomLayout.TITLE_X;
        this.titleLabelY = PhantomLayout.TITLE_Y;
        this.inventoryLabelY = CollectorLayout.INV_LABEL_Y;
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
        for (PhantomLayout.Box well : List.of(CollectorLayout.filterWell(), CollectorLayout.infoWell(), CollectorLayout.inventoryWell())) {
            PhantomTheme.drawWell(graphics, x + well.x(), y + well.y(), x + well.right(), y + well.bottom(), false);
        }
        for (Slot slot : this.menu.slots) {
            PhantomTheme.drawSlot(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, PhantomTheme.SLOT_FILL);
        }
        Control hovered = this.controlAt(mouseX, mouseY);
        PhantomTheme.drawCheckbox(graphics, x + CollectorLayout.SKIP_X, y + CollectorLayout.SKIP_Y,
                CollectorLayout.OPTION_SIZE, this.menu.isSkip());
        PhantomTheme.drawCheckbox(graphics, x + CollectorLayout.STAY_X, y + CollectorLayout.STAY_Y,
                CollectorLayout.OPTION_SIZE, this.menu.isAnchored());
        PhantomTheme.drawMinus(graphics, x + CollectorLayout.MINUS_X, y + CollectorLayout.RANGE_ICON_Y, hovered == Control.RANGE_DOWN);
        PhantomTheme.drawPlus(graphics, x + CollectorLayout.PLUS_X, y + CollectorLayout.RANGE_ICON_Y, hovered == Control.RANGE_UP);
        this.drawOrb(graphics, x + CollectorLayout.TAKE_X, y + CollectorLayout.TAKE_Y, hovered == Control.TAKE);
        PhantomTheme.drawTrash(graphics, x + CollectorLayout.DISMISS_X, y + CollectorLayout.DISMISS_Y, hovered == Control.DISMISS);
    }

    /** The take-experience button: vanilla's smallest experience orb, brighter on hover, dim when the bank is empty. */
    private void drawOrb(GuiGraphics g, int x, int y, boolean hovered) {
        float k = this.menu.getBankedXp() == 0 ? 0.45F : hovered ? 1.0F : 0.8F;
        RenderSystem.enableBlend();
        g.setColor((ORB_TINT >> 16 & 0xFF) / 255.0F * k, (ORB_TINT >> 8 & 0xFF) / 255.0F * k, (ORB_TINT & 0xFF) / 255.0F * k, 1.0F);
        g.blit(ORB, x - 2, y - 2, 16, 16, ORB_U, ORB_V, 16, 16, 64, 64);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /** Filter entries are ghosts: veiled so they never read as stock. */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        if (slot instanceof GhostSlot) {
            PhantomTheme.drawGhostVeil(graphics, slot, PhantomTheme.SLOT_FILL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        PhantomTheme.drawTitle(graphics, this.font, this.title, CollectorLayout.MAX_TITLE_WIDTH);
        PhantomTheme.drawLabel(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY);
        PhantomTheme.drawLabel(graphics, this.font, SKIP, CollectorLayout.SKIP_X + CollectorLayout.OPTION_SIZE + 3, CollectorLayout.SKIP_Y + 1);
        PhantomTheme.drawLabel(graphics, this.font, STAY, CollectorLayout.STAY_X + CollectorLayout.OPTION_SIZE + 3, CollectorLayout.STAY_Y + 1);

        int x = CollectorLayout.TEXT_X;
        int y = CollectorLayout.STATUS_Y;
        graphics.drawString(this.font, this.menu.isAnchored() ? ANCHORED : FOLLOWING, x, y, PhantomTheme.TITLE, false);
        BlockPos pos = this.menu.getPos();
        PhantomTheme.drawLabel(graphics, this.font, Component.translatable("container.phantomstorage.collector.position",
                pos.getX(), pos.getY(), pos.getZ()), x, y + CollectorLayout.LINE_H);
        PhantomTheme.drawLabel(graphics, this.font, PhantomAnchorScreen.dimensionName(this.menu.getDimension()), x, y + 2 * CollectorLayout.LINE_H);

        PhantomTheme.drawLabel(graphics, this.font, RANGE, x, CollectorLayout.RANGE_Y);
        String range = Integer.toString(this.menu.getRange());
        int mid = (CollectorLayout.MINUS_X + PhantomLayout.ICON_SIZE + CollectorLayout.PLUS_X) / 2;
        graphics.drawString(this.font, range, mid - this.font.width(range) / 2, CollectorLayout.RANGE_Y, PhantomTheme.TITLE, false);

        PhantomTheme.drawLabel(graphics, this.font, Component.translatable("container.phantomstorage.collector.xp",
                CollectorLayout.formatXp(this.menu.getBankedXp())), x, CollectorLayout.XP_Y);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty()) {
            List<Component> lines = this.extraTooltip(mouseX, mouseY);
            if (lines != null) {
                graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Nullable
    private List<Component> extraTooltip(int mouseX, int mouseY) {
        Control control = this.controlAt(mouseX, mouseY);
        if (control != null) {
            return switch (control) {
                case RANGE_DOWN, RANGE_UP -> RANGE_TOOLTIP;
                case SKIP -> SKIP_TOOLTIP;
                case TAKE -> TAKE_TOOLTIP;
                case STAY -> STAY_TOOLTIP;
                case DISMISS -> DISMISS_TOOLTIP;
            };
        }
        if (this.hoveredSlot instanceof GhostSlot && !this.hoveredSlot.hasItem()) {
            return FILTER_TOOLTIP;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Control control = this.controlAt(mouseX, mouseY);
        if (control != null && button == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            int id = switch (control) {
                case RANGE_DOWN -> PhantomCollectorMenu.BUTTON_RANGE_DOWN;
                case RANGE_UP -> PhantomCollectorMenu.BUTTON_RANGE_UP;
                case SKIP -> PhantomCollectorMenu.BUTTON_SKIP;
                case TAKE -> PhantomCollectorMenu.BUTTON_TAKE_XP;
                case STAY -> PhantomCollectorMenu.BUTTON_STAY;
                case DISMISS -> PhantomCollectorMenu.BUTTON_DISMISS;
            };
            // The server validates the menu and does the work; see PhantomCollectorMenu#clickMenuButton.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Scrolling over the range adjusts it, like the chest's sort mode. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        if (scrollY != 0.0 && gy >= CollectorLayout.RANGE_ICON_Y && gy < CollectorLayout.RANGE_ICON_Y + PhantomLayout.ICON_SIZE
                && gx >= CollectorLayout.TEXT_X && gx < CollectorLayout.PLUS_X + PhantomLayout.ICON_SIZE
                && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    scrollY > 0.0 ? PhantomCollectorMenu.BUTTON_RANGE_UP : PhantomCollectorMenu.BUTTON_RANGE_DOWN);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Nullable
    private Control controlAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        if (inIcon(gx, gy, CollectorLayout.MINUS_X, CollectorLayout.RANGE_ICON_Y)) {
            return Control.RANGE_DOWN;
        }
        if (inIcon(gx, gy, CollectorLayout.PLUS_X, CollectorLayout.RANGE_ICON_Y)) {
            return Control.RANGE_UP;
        }
        if (inIcon(gx, gy, CollectorLayout.TAKE_X, CollectorLayout.TAKE_Y)) {
            return Control.TAKE;
        }
        if (inIcon(gx, gy, CollectorLayout.DISMISS_X, CollectorLayout.DISMISS_Y)) {
            return Control.DISMISS;
        }
        if (gy >= CollectorLayout.SKIP_Y && gy < CollectorLayout.SKIP_Y + CollectorLayout.OPTION_SIZE
                && gx >= CollectorLayout.SKIP_X && gx < CollectorLayout.SKIP_X + CollectorLayout.SKIP_W) {
            return Control.SKIP;
        }
        if (gy >= CollectorLayout.STAY_Y && gy < CollectorLayout.STAY_Y + CollectorLayout.OPTION_SIZE
                && gx >= CollectorLayout.STAY_X && gx < CollectorLayout.STAY_X + CollectorLayout.STAY_W) {
            return Control.STAY;
        }
        return null;
    }

    private static boolean inIcon(double gx, double gy, int x, int y) {
        return gx >= x && gx < x + PhantomLayout.ICON_SIZE && gy >= y && gy < y + PhantomLayout.ICON_SIZE;
    }
}
