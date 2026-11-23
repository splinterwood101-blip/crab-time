package com.crabtime.client;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import com.crabtime.crab.Crab;
import com.crabtime.menu.CrabNestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The nest menu: your Finds and Eggs pages, two tabs on top, and a live crab in the rock pool that watches the mouse. */
public class CrabNestScreen extends AbstractContainerScreen<CrabNestMenu> {
    private static final ResourceLocation FINDS_BG = CrabTime.id("textures/gui/crab_nest_finds.png");
    private static final ResourceLocation EGGS_BG = CrabTime.id("textures/gui/crab_nest_eggs.png");
    private static final ResourceLocation TABS = CrabTime.id("textures/gui/crab_nest_tabs.png");
    private static final Component EGGS_TITLE = Component.translatable("container.crabtime.crab_nest_eggs");
    private static final Component[] TAB_NAMES = {Component.translatable("crabtime.nest.tab.finds"), Component.translatable("crabtime.nest.tab.eggs")};
    /** The rock pool on the menu picture: x, y, width, height. */
    private static final int POOL_X = 120, POOL_Y = 16, POOL_W = 50, POOL_H = 56;
    private Crab crab;

    public CrabNestScreen(CrabNestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    private int tabX(int i) { return leftPos + 4 + i * 30; }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (crab != null) crab.tickCount++;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        for (int i = 0; i < 2; i++)
            if (mx >= tabX(i) && mx < tabX(i) + 28 && my >= topPos - 28 && my < topPos) g.renderTooltip(font, TAB_NAMES[i], mx, my);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int page = menu.page;
        ItemStack[] icons = {new ItemStack(ModRegistry.GLIMMER_SAND.get()), new ItemStack(ModRegistry.CRAB_EGG.get())};
        // the tab that isn't open sits behind the menu
        for (int i = 0; i < 2; i++) if (i != page) {
            g.blit(TABS, tabX(i), topPos - 26, 0, 0, 28, 28, 64, 32);
            g.renderItem(icons[i], tabX(i) + 6, topPos - 20);
        }
        g.blit(page == 0 ? FINDS_BG : EGGS_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        g.blit(TABS, tabX(page), topPos - 28, 28, 0, 28, 32, 64, 32);
        g.renderItem(icons[page], tabX(page) + 6, topPos - 21);
        if (page == 1)
            for (int e = 0; e < CrabNestMenu.EGGS; e++) {
                int w = Math.round(16 * menu.progress(e));
                if (w > 0) {
                    int x = leftPos + CrabNestMenu.EGG_XY[e][0] + 1, y = topPos + CrabNestMenu.EGG_XY[e][1] + 20;
                    g.fill(x, y, x + w, y + 3, 0xFF7FD35A);
                }
            }
        if (crab == null && minecraft != null && minecraft.level != null) crab = ModRegistry.CRAB.get().create(minecraft.level);
        if (crab != null)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + POOL_X + 1, topPos + POOL_Y + 1, leftPos + POOL_X + POOL_W - 1,
                    topPos + POOL_Y + POOL_H - 1, 42, 0.0625f, mx, my, crab);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, menu.page == 0 ? title : EGGS_TITLE, titleLabelX, titleLabelY, 0x3F3428, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x3F3428, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 2; i++)
            if (mx >= tabX(i) && mx < tabX(i) + 28 && my >= topPos - 28 && my < topPos && i != menu.page) {
                menu.page = i;
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        return super.hasClickedOutside(mx, my, left, top, button) && !(my >= top - 28 && my < top && mx >= left && mx < left + 64);
    }
}
