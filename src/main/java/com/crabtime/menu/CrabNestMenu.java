package com.crabtime.menu;

import com.crabtime.ModRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The nest's menu: page 0 = Finds (18 slots), page 1 = Eggs (6 slots, each with a hatching bar). */
public class CrabNestMenu extends AbstractContainerMenu {
    public static final int FINDS = 18, EGGS = 6, HATCH = 18000; // about 15 minutes
    /** Where the slots' top-left corners sit on the menu picture (the item goes 1 pixel in). */
    public static final int[][] EGG_XY = {{10, 18}, {46, 18}, {82, 18}, {10, 46}, {46, 46}, {82, 46}};
    private final Container nest;
    private final ContainerData data;
    public int page;

    public CrabNestMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(FINDS + EGGS), new SimpleContainerData(EGGS + 1)); }

    public CrabNestMenu(int id, Inventory inv, Container nest, ContainerData data) {
        super(ModRegistry.NEST_MENU.get(), id);
        checkContainerSize(nest, FINDS + EGGS);
        checkContainerDataCount(data, EGGS + 1);
        this.nest = nest;
        this.data = data;
        nest.startOpen(inv.player);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 6; c++) addSlot(new PageSlot(nest, r * 6 + c, 8 + c * 18, 18 + r * 18, 0));
        for (int e = 0; e < EGGS; e++) addSlot(new EggSlot(nest, FINDS + e, EGG_XY[e][0] + 1, EGG_XY[e][1] + 1));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
        addDataSlots(data);
    }

    /** True for a crop crab nest: grassy menu art and a crop crab in the pool. */
    public boolean isCrop() { return data.get(EGGS) == 1; }

    public float progress(int egg) { return Math.min(1f, data.get(egg) / (float) HATCH); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 || id == 1) { page = id; return true; }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int nestEnd = FINDS + EGGS, end = slots.size();
        if (index < nestEnd) {
            if (!moveItemStackTo(stack, nestEnd, end, true)) return ItemStack.EMPTY;
        } else if (page == 1) {
            if (!com.crabtime.crab.Crab.isEgg(stack) || !moveItemStackTo(stack, FINDS, nestEnd, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, FINDS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override public boolean stillValid(Player player) { return nest.stillValid(player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        nest.stopOpen(player);
    }

    /** A slot that only shows on one tab. */
    class PageSlot extends Slot {
        final int onPage;
        PageSlot(Container c, int i, int x, int y, int onPage) { super(c, i, x, y); this.onPage = onPage; }
        @Override public boolean isActive() { return page == onPage; }
    }

    class EggSlot extends PageSlot {
        EggSlot(Container c, int i, int x, int y) { super(c, i, x, y, 1); }
        @Override public boolean mayPlace(ItemStack stack) { return stack.is(isCrop() ? ModRegistry.CROP_CRAB_EGG.get() : ModRegistry.CRAB_EGG.get()); }
        @Override public int getMaxStackSize() { return 1; }
    }
}
