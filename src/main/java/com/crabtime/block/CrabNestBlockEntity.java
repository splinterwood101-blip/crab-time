package com.crabtime.block;

import com.crabtime.ModRegistry;
import com.crabtime.crab.Crab;
import com.crabtime.menu.CrabNestMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 18 slots of finds, plus 6 egg slots that hatch into baby crabs. */
public class CrabNestBlockEntity extends BaseContainerBlockEntity {
    public static final int FINDS = CrabNestMenu.FINDS, EGGS = CrabNestMenu.EGGS, SIZE = FINDS + EGGS, HATCH = CrabNestMenu.HATCH;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final int[] progress = new int[EGGS];
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return progress[i]; }
        @Override public void set(int i, int v) { progress[i] = v; }
        @Override public int getCount() { return EGGS; }
    };

    public CrabNestBlockEntity(BlockPos pos, BlockState state) { super(ModRegistry.NEST_BE.get(), pos, state); }

    @Override protected Component getDefaultName() { return Component.translatable("container.crabtime.crab_nest"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return new CrabNestMenu(id, inv, this, data); }
    @Override public int getContainerSize() { return SIZE; }

    /** Egg slots only take crab eggs (so hoppers can't fill them with anything else). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < FINDS || (stack.is(ModRegistry.CRAB_EGG.get()) && items.get(slot).isEmpty());
    }

    /** Puts a find in the 18 find slots; returns what didn't fit. */
    public ItemStack insert(ItemStack stack) { return insert(stack, 0, FINDS); }

    /** Puts an egg in a free egg slot; returns it if there's no room. */
    public ItemStack insertEgg(ItemStack stack) {
        for (int i = FINDS; i < SIZE && !stack.isEmpty(); i++)
            if (items.get(i).isEmpty()) { items.set(i, stack.split(1)); progress[i - FINDS] = 0; }
        setChanged();
        return stack;
    }

    private ItemStack insert(ItemStack stack, int from, int to) {
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n); stack.shrink(n);
            }
        }
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (items.get(i).isEmpty()) { items.set(i, stack.copy()); stack.setCount(0); }
        setChanged();
        return stack;
    }

    /** Eggs grow; when one is ready a baby crab hatches next to the nest. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrabNestBlockEntity be) {
        for (int e = 0; e < EGGS; e++) {
            ItemStack egg = be.items.get(FINDS + e);
            if (egg.isEmpty() || !egg.is(ModRegistry.CRAB_EGG.get())) { be.progress[e] = 0; continue; }
            if (++be.progress[e] >= HATCH) {
                be.progress[e] = 0;
                egg.shrink(1);
                be.setChanged();
                if (level instanceof ServerLevel sl) hatch(sl, pos);
            }
        }
    }

    private static void hatch(ServerLevel level, BlockPos pos) {
        Crab baby = ModRegistry.CRAB.get().create(level);
        if (baby == null) return;
        baby.setAge(-24000);
        baby.moveTo(pos.getX() + 0.5 + (level.random.nextDouble() - 0.5), pos.getY() + 0.25, pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5),
                level.random.nextFloat() * 360f, 0);
        baby.setNest(pos);
        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.BREEDING, null);
        level.addFreshEntity(baby);
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.NEUTRAL, 0.8f, 1.3f);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, reg);
        int[] p = tag.getIntArray("Hatch");
        for (int i = 0; i < EGGS && i < p.length; i++) progress[i] = p[i];
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
        tag.putIntArray("Hatch", progress);
    }
}
