package com.crabtime.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The farming claw. Right-click dirt or grass: till a 3x3. Right-click a grown crop: harvest the grown crops in a 3x3
 * and replant them. Hold right-click anywhere else: the claw summons its grow orb, and crops in a 5x5 in front of you
 * grow while you hold it (one durability a second while something is growing).
 */
public class FarmingClawItem extends Item {
    public FarmingClawItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        BlockState s = level.getBlockState(pos);
        if (s.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).getBlock() instanceof CropBlock) { pos = pos.above(); s = level.getBlockState(pos); }

        if (s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) {
            if (level instanceof ServerLevel sl) {
                int n = 0;
                for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) n += harvest(sl, p.immutable(), player) ? 1 : 0;
                if (n > 0) {
                    sl.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
                    damage(ctx.getItemInHand(), player, ctx.getHand());
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (tillable(level, pos)) {
            if (level instanceof ServerLevel sl) {
                for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1)))
                    if (tillable(sl, p)) {
                        sl.setBlock(p, Blocks.FARMLAND.defaultBlockState(), 11);
                        sl.gameEvent(GameEvent.BLOCK_CHANGE, p, GameEvent.Context.of(player, Blocks.FARMLAND.defaultBlockState()));
                    }
                sl.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1f, 1f);
                damage(ctx.getItemInHand(), player, ctx.getHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS; // falls through to use(): the grow orb
    }

    private static boolean tillable(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH)) && level.getBlockState(p.above()).isAir();
    }

    /** Breaks a grown crop, keeps one seed back to replant it at age 0, and drops the rest. */
    private static boolean harvest(ServerLevel level, BlockPos p, Player player) {
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) return false;
        List<ItemStack> drops = Block.getDrops(s, level, p, null, player, player != null ? player.getMainHandItem() : ItemStack.EMPTY);
        Item seed = s.getBlock().asItem();
        for (ItemStack d : drops) if (d.is(seed)) { d.shrink(1); break; }
        level.setBlock(p, crop.getStateForAge(0), 3);
        for (ItemStack d : drops) if (!d.isEmpty()) Block.popResource(level, p, d);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0);
        return true;
    }

    private static void damage(ItemStack stack, Player player, InteractionHand hand) {
        if (player != null) stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
    }

    // ---------- the grow orb ----------
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (!level.isClientSide) level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity e) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl)) return;
        int held = getUseDuration(stack, user) - remaining;
        if (held < 5 || held % 5 != 0) return; // the orb takes a moment to appear
        Vec3 look = user.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos centre = BlockPos.containing(user.getX() + look.x * 3.5, user.getY(), user.getZ() + look.z * 3.5);
        boolean grew = false;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 1, 2))) {
            BlockState s = sl.getBlockState(p);
            if (!s.is(BlockTags.CROPS) || !s.isRandomlyTicking()) continue;
            if (s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) continue;
            s.randomTick(sl, p.immutable(), sl.random);
            grew = true;
            if (sl.random.nextInt(3) == 0)
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 1, 0.3, 0.2, 0.3, 0);
        }
        // sparkles from the orb itself
        Vec3 hand = user.getEyePosition().add(user.getLookAngle().scale(0.8)).add(0, -0.35, 0);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0);
        if (grew && held % 20 == 0 && user instanceof Player p && !p.getAbilities().instabuild)
            stack.hurtAndBreak(1, p, p.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        if (held % 40 == 0) sl.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        for (int i = 1; i <= 3; i++) tip.add(Component.translatable("tooltip.crabtime.farming_claw" + i).withStyle(ChatFormatting.GRAY));
    }
}
