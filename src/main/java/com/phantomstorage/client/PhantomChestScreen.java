package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.menu.VoidSlot;
import com.phantomstorage.storage.SortMode;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;

/**
 * Drawn in the shared {@link PhantomTheme}, entirely with fills, so it follows whichever
 * {@link PhantomLayout} the menu was opened with (no background textures to keep in sync).
 */
public class PhantomChestScreen extends AbstractContainerScreen<PhantomChestMenu> {
    /** Decorations are faint so they read as texture, not content. */
    private static final float EMBLEM_ALPHA = 0.28F;
    private static final float WEB_ALPHA = 0.35F;
    static final ResourceLocation CHARM = PhantomStorage.id("textures/item/phantom_charm.png");
    private static final ResourceLocation COBWEB = ResourceLocation.withDefaultNamespace("textures/block/cobweb.png");
    private static final Component CRAFTING = Component.translatable("container.phantomstorage.crafting");
    private static final Component VOID = Component.translatable("container.phantomstorage.void");
    private static final List<Component> TRASH_TOOLTIP = PhantomTheme.tooltip("trash");
    private static final List<Component> VOID_TOOLTIP = PhantomTheme.tooltip("void_slot");
    private static final List<Component> RESULT_TOOLTIP = PhantomTheme.tooltip("result");

    private enum Icon { SORT, TRASH, RECIPES }

    private final PhantomLayout layout;
    /** Set in {@link #init()} once the font is available: the sort icon follows the title. */
    private int sortIconX;
    /** The "+" only means something when a recipe viewer is there to handle it. */
    private final boolean recipeViewerLoaded;

    public PhantomChestScreen(PhantomChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.layout = menu.getLayout();
        this.imageWidth = this.layout.width();
        this.imageHeight = this.layout.height();
        this.titleLabelX = PhantomLayout.TITLE_X;
        this.titleLabelY = PhantomLayout.TITLE_Y;
        this.recipeViewerLoaded = ModList.get().isLoaded("jei") || ModList.get().isLoaded("roughlyenoughitems");
    }

    @Override
    protected void init() {
        super.init();
        int titleWidth = Math.min(this.font.width(this.title), this.layout.maxTitleWidth());
        this.sortIconX = this.layout.sortXAfterTitle(titleWidth);
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
        this.drawDecorations(graphics, x, y);
        for (PhantomLayout.Well well : this.layout.wells()) {
            PhantomTheme.drawWell(graphics, x, y, well);
        }

        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            if (i == PhantomChestMenu.RESULT_SLOT) {
                PhantomTheme.drawSlot(graphics, sx - 4, sy - 4, 26, 26, PhantomTheme.SLOT_FILL);
            } else {
                PhantomTheme.drawSlot(graphics, sx, sy, 18, 18, slot instanceof VoidSlot ? PhantomTheme.VOID_FILL : PhantomTheme.SLOT_FILL);
            }
        }
        if (this.layout.wide()) {
            PhantomTheme.drawRightArrow(graphics, x + this.layout.craftX() + 3 * 18 + 1, y + this.layout.resultY() + 8);
        } else {
            PhantomTheme.drawDownArrow(graphics, x + this.layout.resultX() + 8, y + this.layout.craftY() + 3 * 18 + 1);
        }

        Icon hovered = this.iconAt(mouseX, mouseY);
        for (Icon icon : Icon.values()) {
            if (this.isShown(icon)) {
                this.drawIcon(graphics, icon, icon == hovered);
            }
        }
    }

    /** Void filter entries are ghosts: veiled (here, after every slot is drawn) so they never read as stock. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        PhantomTheme.drawGhostVeils(graphics, this.menu.slots, PhantomTheme.VOID_FILL);
        if (this.hoveredSlot instanceof VoidSlot) {
            this.renderSlotHighlight(graphics, this.hoveredSlot, mouseX, mouseY, 0.0F);
        }
        // Long custom names are cut short rather than running under the sort button.
        PhantomTheme.drawTitle(graphics, this.font, this.title, this.layout.maxTitleWidth());
        PhantomTheme.drawLabel(graphics, this.font, CRAFTING, this.layout.craftLabelX(), this.layout.craftLabelY());
        graphics.drawString(this.font, VOID, this.layout.voidLabelX(), this.layout.voidLabelY(), PhantomTheme.VOID_LABEL, false);
        if (this.layout.invLabelX() >= 0) {
            PhantomTheme.drawLabel(graphics, this.font, this.playerInventoryTitle, this.layout.invLabelX(), this.layout.invLabelY());
        }
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

    /** Tooltips for our own controls and empty special slots; item tooltips stay vanilla. */
    @Nullable
    private List<Component> extraTooltip(int mouseX, int mouseY) {
        Icon icon = this.iconAt(mouseX, mouseY);
        if (icon == Icon.SORT) {
            return this.sortTooltip();
        }
        if (icon == Icon.TRASH) {
            return TRASH_TOOLTIP;
        }
        // Icon.RECIPES: JEI/REI draw their own "show recipes" tooltip for the click area.
        Slot slot = this.hoveredSlot;
        if (slot != null && !slot.hasItem()) {
            if (slot instanceof VoidSlot) {
                return VOID_TOOLTIP;
            }
            if (slot instanceof ResultSlot) {
                return RESULT_TOOLTIP;
            }
        }
        return null;
    }

    /** "Sort: Name, A → Z" and the controls. */
    private List<Component> sortTooltip() {
        SortMode mode = this.menu.getSortMode();
        String direction = this.menu.isSortDescending() ? "desc" : "asc";
        return List.of(
                Component.translatable("tooltip.phantomstorage.sort.mode", mode.displayName(),
                        Component.translatable("sort.phantomstorage." + mode.name().toLowerCase(Locale.ROOT) + "." + direction)),
                Component.translatable("tooltip.phantomstorage.sort.click").withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.phantomstorage.sort.cycle").withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.phantomstorage.sort.reverse").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Sort icon: left-click sorts, shift + left-click flips the direction, right-click cycles the mode.
     * Trash: left-click.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Icon icon = this.iconAt(mouseX, mouseY);
        int menuButton = -1;
        if (icon == Icon.SORT && button == 0) {
            menuButton = hasShiftDown() ? PhantomChestMenu.BUTTON_SORT_DIRECTION : PhantomChestMenu.BUTTON_SORT;
        } else if (icon == Icon.SORT && button == 1) {
            menuButton = PhantomChestMenu.BUTTON_SORT_NEXT_MODE;
        } else if (icon == Icon.TRASH && button == 0) {
            menuButton = PhantomChestMenu.BUTTON_TRASH;
        }
        if (menuButton >= 0 && this.pressMenuButton(menuButton)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Scrolling over the sort icon cycles its mode. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && this.iconAt(mouseX, mouseY) == Icon.SORT && this.pressMenuButton(
                scrollY < 0.0 ? PhantomChestMenu.BUTTON_SORT_NEXT_MODE : PhantomChestMenu.BUTTON_SORT_PREVIOUS_MODE)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** The server validates the menu and does the work; see PhantomChestMenu#clickMenuButton. */
    private boolean pressMenuButton(int menuButton) {
        if (this.minecraft == null || this.minecraft.gameMode == null) {
            return false;
        }
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, menuButton);
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    private boolean isShown(Icon icon) {
        return icon != Icon.RECIPES || this.recipeViewerLoaded;
    }

    private int iconX(Icon icon) {
        return switch (icon) {
            case SORT -> this.sortIconX;
            case TRASH -> this.layout.trashX();
            case RECIPES -> this.layout.recipesX();
        };
    }

    private int iconY(Icon icon) {
        return switch (icon) {
            case SORT -> this.layout.sortY();
            case TRASH -> this.layout.trashY();
            case RECIPES -> this.layout.recipesY();
        };
    }

    @Nullable
    private Icon iconAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        for (Icon icon : Icon.values()) {
            int ix = this.iconX(icon);
            int iy = this.iconY(icon);
            if (this.isShown(icon) && gx >= ix && gx < ix + PhantomLayout.ICON_SIZE && gy >= iy && gy < iy + PhantomLayout.ICON_SIZE) {
                return icon;
            }
        }
        return null;
    }

    private void drawIcon(GuiGraphics g, Icon icon, boolean hovered) {
        int x = this.leftPos + this.iconX(icon);
        int y = this.topPos + this.iconY(icon);
        switch (icon) {
            case SORT -> PhantomTheme.drawSort(g, x, y, hovered, this.menu.isSortDescending());
            case TRASH -> PhantomTheme.drawTrash(g, x, y, hovered);
            case RECIPES -> PhantomTheme.drawPlus(g, x, y, hovered);
        }
    }

    // ---- drawing -------------------------------------------------------------------------------

    /** Faint charm emblem and corner cobweb, only where the layout has spare space. */
    private void drawDecorations(GuiGraphics g, int ox, int oy) {
        if (this.layout.emblemX() >= 0) {
            PhantomTheme.drawCharm(g, ox + this.layout.emblemX(), oy + this.layout.emblemY(), this.layout.emblemSize(), CHARM, EMBLEM_ALPHA);
        }
        if (this.layout.webX() >= 0) {
            int size = PhantomLayout.WEB_SIZE;
            RenderSystem.enableBlend();
            g.setColor(1.0F, 1.0F, 1.0F, WEB_ALPHA);
            g.blit(COBWEB, ox + this.layout.webX(), oy + this.layout.webY(), 0.0F, 0.0F, size, size, size, size);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }
}
