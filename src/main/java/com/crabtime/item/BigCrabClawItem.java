package com.crabtime.item;

import com.crabtime.claw.ClawCarry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Right-click a mob to grab it (one durability per grab). Right-click a block to set it down there; sneak + right-click to
 * throw it. Grabbing itself is handled in {@link ClawCarry}, before the mob gets a chance to react to the click.
 */
public class BigCrabClawItem extends Item {
    public BigCrabClawItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !ClawCarry.carrying(sp)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) ClawCarry.throwMob(sp);
        else ClawCarry.setDown(sp, Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace())));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && ClawCarry.carrying(sp) && player.isShiftKeyDown()) {
            ClawCarry.throwMob(sp);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        for (int i = 1; i <= 3; i++) tip.add(Component.translatable("tooltip.crabtime.big_crab_claw" + i).withStyle(ChatFormatting.GRAY));
    }
}
