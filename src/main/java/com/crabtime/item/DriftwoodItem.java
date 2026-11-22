package com.crabtime.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** The driftwood log, with the brush chart in its tooltip. */
public class DriftwoodItem extends BlockItem {
    public DriftwoodItem(Block block, Properties p) { super(block, p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.crabtime.driftwood").withStyle(ChatFormatting.GRAY));
        for (int i = 1; i <= 3; i++) tip.add(Component.translatable("tooltip.crabtime.driftwood" + i).withStyle(ChatFormatting.DARK_GRAY));
    }
}
