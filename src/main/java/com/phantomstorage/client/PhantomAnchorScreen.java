package com.phantomstorage.client;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.AnchorLayout;
import com.phantomstorage.menu.PhantomAnchorMenu;
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

/**
 * The anchor's panel in the shared {@link PhantomTheme}: its charm art and where it is in a well,
 * with whether it's anchored in the title gold, then the "Stay here" checkbox and a dismiss button.
 */
public class PhantomAnchorScreen extends AbstractContainerScreen<PhantomAnchorMenu> {
    static final ResourceLocation CHARM = PhantomStorage.id("textures/item/phantom_charm_anchor.png");
    private static final int DOT = 4;

    private static final Component ANCHORED = Component.translatable("container.phantomstorage.anchor.anchored");
    private static final Component FOLLOWING = Component.translatable("container.phantomstorage.anchor.following");
    private static final Component CHUNK_LOADED = Component.translatable("container.phantomstorage.anchor.chunk_loaded");
    private static final Component STAY = Component.translatable("container.phantomstorage.anchor.stay");
    private static final List<Component> STAY_TOOLTIP = PhantomTheme.tooltip("anchor_stay");
    private static final List<Component> DISMISS_TOOLTIP = PhantomTheme.tooltip("anchor_dismiss");

    private enum Control { STAY, DISMISS }

    public PhantomAnchorScreen(PhantomAnchorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AnchorLayout.WIDTH;
        this.imageHeight = AnchorLayout.HEIGHT;
        this.titleLabelX = PhantomLayout.TITLE_X;
        this.titleLabelY = PhantomLayout.TITLE_Y;
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
        PhantomLayout.Box well = AnchorLayout.infoWell();
        PhantomTheme.drawWell(graphics, x + well.x(), y + well.y(), x + well.right(), y + well.bottom(), false);
        PhantomTheme.drawCharm(graphics, x + AnchorLayout.ART_X, y + AnchorLayout.ART_Y, AnchorLayout.ART_SIZE, CHARM, 1.0F);
        if (this.menu.isAnchored()) {
            int dotY = y + AnchorLayout.TEXT_Y + 3 * AnchorLayout.LINE_H + 2;
            int dotX = x + AnchorLayout.TEXT_X;
            graphics.fill(dotX, dotY, dotX + DOT, dotY + DOT, PhantomTheme.ACCENT);
        }
        Control hovered = this.controlAt(mouseX, mouseY);
        PhantomTheme.drawCheckbox(graphics, x + AnchorLayout.OPTION_X, y + AnchorLayout.OPTION_Y,
                AnchorLayout.OPTION_SIZE, this.menu.isAnchored());
        PhantomTheme.drawTrash(graphics, x + AnchorLayout.DISMISS_X, y + AnchorLayout.DISMISS_Y, hovered == Control.DISMISS);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        PhantomTheme.drawTitle(graphics, this.font, this.title, AnchorLayout.MAX_TITLE_WIDTH);
        int x = AnchorLayout.TEXT_X;
        int y = AnchorLayout.TEXT_Y;
        int line = AnchorLayout.LINE_H;
        boolean anchored = this.menu.isAnchored();
        graphics.drawString(this.font, anchored ? ANCHORED : FOLLOWING, x, y, PhantomTheme.TITLE, false);
        BlockPos pos = this.menu.getPos();
        PhantomTheme.drawLabel(graphics, this.font,
                Component.translatable("container.phantomstorage.anchor.position", pos.getX(), pos.getY(), pos.getZ()), x, y + line);
        PhantomTheme.drawLabel(graphics, this.font, dimensionName(this.menu.getDimension()), x, y + 2 * line);
        if (anchored) {
            PhantomTheme.drawLabel(graphics, this.font, CHUNK_LOADED, x + DOT + 3, y + 3 * line);
        }
        PhantomTheme.drawLabel(graphics, this.font, STAY,
                AnchorLayout.OPTION_X + AnchorLayout.OPTION_SIZE + 3, AnchorLayout.OPTION_Y + 1);
    }

    /** "Overworld", "The Nether", "The End"; another mod's dimension by its id unless it's translated. */
    static Component dimensionName(ResourceLocation id) {
        return Component.translatableWithFallback("dimension.phantomstorage." + id.getNamespace() + "." + id.getPath(), id.toString());
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Control control = this.controlAt(mouseX, mouseY);
        if (control != null) {
            graphics.renderComponentTooltip(this.font, control == Control.STAY ? STAY_TOOLTIP : DISMISS_TOOLTIP, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Control control = this.controlAt(mouseX, mouseY);
        if (control != null && button == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            // The server validates the menu and does the work; see PhantomAnchorMenu#clickMenuButton.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    control == Control.STAY ? PhantomAnchorMenu.BUTTON_STAY : PhantomAnchorMenu.BUTTON_DISMISS);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private Control controlAt(double mouseX, double mouseY) {
        double gx = mouseX - this.leftPos;
        double gy = mouseY - this.topPos;
        if (gy >= AnchorLayout.OPTION_Y && gy < AnchorLayout.OPTION_Y + AnchorLayout.OPTION_SIZE
                && gx >= AnchorLayout.OPTION_X && gx < AnchorLayout.OPTION_X + AnchorLayout.OPTION_W) {
            return Control.STAY;
        }
        if (gx >= AnchorLayout.DISMISS_X && gx < AnchorLayout.DISMISS_X + PhantomLayout.ICON_SIZE
                && gy >= AnchorLayout.DISMISS_Y && gy < AnchorLayout.DISMISS_Y + PhantomLayout.ICON_SIZE) {
            return Control.DISMISS;
        }
        return null;
    }
}
