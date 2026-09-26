package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.menu.VoidSlot;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;

/**
 * Drawn entirely with fills, so it follows whichever {@link PhantomLayout} the menu was opened with
 * (no background textures to keep in sync).
 */
public class PhantomChestScreen extends AbstractContainerScreen<PhantomChestMenu> {
    private static final int PANEL_TOP = 0xFFCDC8DA;
    private static final int PANEL_BOTTOM = 0xFFBCB5CC;
    private static final int WELL_EDGE = 0xFF978DAE;
    private static final int WELL_FILL = 0xFFB3ABC6;
    private static final int VOID_WELL_EDGE = 0xFF5E4680;
    private static final int VOID_WELL_FILL = 0xFF8F7AB0;
    /** Decorations are faint so they read as texture, not content. */
    private static final float EMBLEM_ALPHA = 0.28F;
    private static final float WEB_ALPHA = 0.35F;
    private static final ResourceLocation EMBLEM = PhantomStorage.id("textures/item/phantom_charm.png");
    private static final int EMBLEM_TEXTURE_SIZE = 64;
    private static final ResourceLocation COBWEB = ResourceLocation.withDefaultNamespace("textures/block/cobweb.png");
    private static final int OUTLINE = 0xFF000000;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF55506A;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int VOID_FILL = 0xFF4A3563;
    private static final int LABEL = 0x404040;
    private static final int VOID_LABEL = 0x4A2A70;

    private static final int ICON_OUTLINE = 0xFF2A2536;
    private static final int ICON_BODY = 0xFF8A8499;
    private static final int ICON_DETAIL = 0xFF5E586E;
    private static final int ICON_BODY_HOVER = 0xFFB07CE8;
    private static final int ICON_DETAIL_HOVER = 0xFF7A48B0;

    private static final Component CRAFTING = Component.translatable("container.phantomstorage.crafting");
    private static final Component VOID = Component.translatable("container.phantomstorage.void");
    private static final List<Component> SORT_TOOLTIP = tooltip("sort");
    private static final List<Component> TRASH_TOOLTIP = tooltip("trash");
    private static final List<Component> VOID_TOOLTIP = tooltip("void_slot");
    private static final List<Component> RESULT_TOOLTIP = tooltip("result");

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

    /** Title line plus a grey description line, from {@code tooltip.phantomstorage.<key>[.desc]}. */
    private static List<Component> tooltip(String key) {
        return List.of(
                Component.translatable("tooltip.phantomstorage." + key),
                Component.translatable("tooltip.phantomstorage." + key + ".desc").withStyle(ChatFormatting.GRAY));
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
        drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);
        this.drawDecorations(graphics, x, y);
        for (PhantomLayout.Well well : this.layout.wells()) {
            drawWell(graphics, x, y, well);
        }

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

        Icon hovered = this.iconAt(mouseX, mouseY);
        for (Icon icon : Icon.values()) {
            if (this.isShown(icon)) {
                this.drawIcon(graphics, icon, icon == hovered);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Long custom names are cut short rather than running under the sort button.
        graphics.drawString(this.font, Language.getInstance().getVisualOrder(this.font.substrByWidth(this.title, this.layout.maxTitleWidth())),
                this.titleLabelX, this.titleLabelY, LABEL, false);
        graphics.drawString(this.font, CRAFTING, this.layout.craftLabelX(), this.layout.craftLabelY(), LABEL, false);
        graphics.drawString(this.font, VOID, this.layout.voidLabelX(), this.layout.voidLabelY(), VOID_LABEL, false);
        if (this.layout.invLabelX() >= 0) {
            graphics.drawString(this.font, this.playerInventoryTitle, this.layout.invLabelX(), this.layout.invLabelY(), LABEL, false);
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
            return SORT_TOOLTIP;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Icon icon = button == 0 ? this.iconAt(mouseX, mouseY) : null;
        int menuButton = icon == Icon.SORT ? PhantomChestMenu.BUTTON_SORT
                : icon == Icon.TRASH ? PhantomChestMenu.BUTTON_TRASH
                : -1;
        if (menuButton >= 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            // The server validates the menu and does the work; see PhantomChestMenu#clickMenuButton.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, menuButton);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        int body = hovered ? ICON_BODY_HOVER : ICON_BODY;
        int detail = hovered ? ICON_DETAIL_HOVER : ICON_DETAIL;
        switch (icon) {
            case SORT -> drawSort(g, x, y, body);
            case TRASH -> drawTrash(g, x, y, body, detail);
            case RECIPES -> drawPlus(g, x, y, body);
        }
    }

    // ---- drawing -------------------------------------------------------------------------------

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HIGHLIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, PANEL_TOP, PANEL_BOTTOM);
    }

    /** A soft recess behind a slot group: edge on the sides and bottom, flush at the top. */
    private static void drawWell(GuiGraphics g, int ox, int oy, PhantomLayout.Well well) {
        PhantomLayout.Box b = well.box();
        int x0 = ox + b.x();
        int y0 = oy + b.y();
        int x1 = ox + b.right();
        int y1 = oy + b.bottom();
        g.fill(x0, y0, x1, y1, well.isVoid() ? VOID_WELL_EDGE : WELL_EDGE);
        g.fill(x0 + 1, y0, x1 - 1, y1 - 1, well.isVoid() ? VOID_WELL_FILL : WELL_FILL);
    }

    /** Faint charm emblem and corner cobweb, only where the layout has spare space. */
    private void drawDecorations(GuiGraphics g, int ox, int oy) {
        if (this.layout.emblemX() < 0 && this.layout.webX() < 0) {
            return;
        }
        RenderSystem.enableBlend();
        if (this.layout.emblemX() >= 0) {
            int size = this.layout.emblemSize();
            g.setColor(1.0F, 1.0F, 1.0F, EMBLEM_ALPHA);
            g.blit(EMBLEM, ox + this.layout.emblemX(), oy + this.layout.emblemY(), size, size,
                    0.0F, 0.0F, EMBLEM_TEXTURE_SIZE, EMBLEM_TEXTURE_SIZE, EMBLEM_TEXTURE_SIZE, EMBLEM_TEXTURE_SIZE);
        }
        if (this.layout.webX() >= 0) {
            int size = PhantomLayout.WEB_SIZE;
            g.setColor(1.0F, 1.0F, 1.0F, WEB_ALPHA);
            g.blit(COBWEB, ox + this.layout.webX(), oy + this.layout.webY(), 0.0F, 0.0F, size, size, size, size);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
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

    /** A small right-pointing arrow centred on {@code cy}, from the crafting grid to the result slot. */
    private static void drawRightArrow(GuiGraphics g, int left, int cy) {
        g.fill(left + 1, cy - 1, left + 7, cy + 1, SHADOW);
        for (int i = 0; i < 4; i++) {
            g.fill(left + 7 + i, cy - 4 + i, left + 8 + i, cy + 4 - i, SHADOW);
        }
    }

    /** 12x12: three bars, longest to shortest. */
    private static void drawSort(GuiGraphics g, int x, int y, int body) {
        int[] widths = {10, 7, 4};
        for (int i = 0; i < widths.length; i++) {
            int top = y + i * 4;
            g.fill(x, top, x + widths[i] + 2, top + 4, ICON_OUTLINE);
            g.fill(x + 1, top + 1, x + widths[i] + 1, top + 3, body);
        }
    }

    /** 12x12 trash can. */
    private static void drawTrash(GuiGraphics g, int x, int y, int body, int rib) {
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
    private static void drawPlus(GuiGraphics g, int x, int y, int body) {
        g.fill(x + 4, y, x + 8, y + 12, ICON_OUTLINE);
        g.fill(x, y + 4, x + 12, y + 8, ICON_OUTLINE);
        g.fill(x + 5, y + 1, x + 7, y + 11, body);
        g.fill(x + 1, y + 5, x + 11, y + 7, body);
    }
}
