package com.crabtime.recipe;

import com.crabtime.ModRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Driftwood + a brush: where the brush sits next to the driftwood picks the log you get. The brush isn't used up.
 * <pre>
 *  Oak      Spruce    Birch
 *  Jungle   DRIFTWOOD Acacia
 *  Dark Oak Mangrove  Cherry
 * </pre>
 */
public class DriftwoodRecipe extends CustomRecipe {
    private static final Item[][] LOGS = {
            {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG},
            {Items.JUNGLE_LOG, null, Items.ACACIA_LOG},
            {Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG}};

    public DriftwoodRecipe(CraftingBookCategory cat) { super(cat); }

    @Nullable
    private static Item result(CraftingInput in) {
        int dx = -9, dy = 0, bx = -9, by = 0, n = 0;
        for (int y = 0; y < in.height(); y++)
            for (int x = 0; x < in.width(); x++) {
                ItemStack s = in.getItem(x, y);
                if (s.isEmpty()) continue;
                n++;
                if (s.is(ModRegistry.DRIFTWOOD_LOG_ITEM.get())) { dx = x; dy = y; }
                else if (s.is(Items.BRUSH)) { bx = x; by = y; }
                else return null;
            }
        if (n != 2 || dx == -9 || bx == -9) return null;
        int ox = bx - dx, oy = by - dy;
        if (Math.abs(ox) > 1 || Math.abs(oy) > 1) return null;
        return LOGS[oy + 1][ox + 1];
    }

    @Override public boolean matches(CraftingInput in, Level level) { return result(in) != null; }
    @Override public ItemStack assemble(CraftingInput in, HolderLookup.Provider reg) { Item r = result(in); return r == null ? ItemStack.EMPTY : new ItemStack(r); }
    @Override public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ModRegistry.DRIFTWOOD_RECIPE.get(); }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput in) {
        NonNullList<ItemStack> out = NonNullList.withSize(in.size(), ItemStack.EMPTY);
        for (int i = 0; i < in.size(); i++) if (in.getItem(i).is(Items.BRUSH)) out.set(i, in.getItem(i).copyWithCount(1));
        return out;
    }
}
