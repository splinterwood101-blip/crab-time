package com.crabtime.block;

import com.crabtime.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Nine slots of whatever the crabs bring home. */
public class CrabNestBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

    public CrabNestBlockEntity(BlockPos pos, BlockState state) { super(ModRegistry.NEST_BE.get(), pos, state); }

    @Override protected Component getDefaultName() { return Component.translatable("container.crabtime.crab_nest"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return new DispenserMenu(id, inv, this); }
    @Override public int getContainerSize() { return 9; }

    /** Puts a stack in; returns what didn't fit. */
    public ItemStack insert(ItemStack stack) {
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n); stack.shrink(n);
            }
        }
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++)
            if (items.get(i).isEmpty()) { items.set(i, stack.copy()); stack.setCount(0); }
        setChanged();
        return stack;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, reg);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
    }
}
