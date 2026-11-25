package com.crabtime.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Use on a crop: the 5x5 around it grows about 4x as fast for one Minecraft day, with green sparkles. */
public class UltraCropMealItem extends Item {
    public static final int RADIUS = 2, DURATION = 24000;
    /** Vanilla gives each block a random tick about every 1365 ticks; three more on top of that makes 4x. */
    private static final float EXTRA_TICK_CHANCE = 3f / 1365f;

    public UltraCropMealItem(Properties p) { super(p); }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext c, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("tooltip.crabtime.ultra_crop_meal").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.getBlockState(pos).is(Blocks.FARMLAND)) pos = pos.above();
        if (!level.getBlockState(pos).is(BlockTags.CROPS)) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl) {
            Zones.of(sl).add(pos, sl.getGameTime() + DURATION);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 2.2, 0.4, 2.2, 0.1);
            sl.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1f, 0.8f);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Every server tick: extra growth and sparkles in each live zone. */
    public static void levelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Zones zones = Zones.of(level);
        if (zones.list.isEmpty()) return;
        long now = level.getGameTime();
        RandomSource r = level.random;
        if (zones.list.removeIf(z -> z.end <= now)) zones.setDirty();
        for (Zone z : zones.list) {
            if (!level.isLoaded(z.pos)) continue;
            for (BlockPos p : BlockPos.betweenClosed(z.pos.offset(-RADIUS, -1, -RADIUS), z.pos.offset(RADIUS, 1, RADIUS))) {
                if (r.nextFloat() >= EXTRA_TICK_CHANCE) continue;
                BlockState s = level.getBlockState(p);
                if (s.is(BlockTags.CROPS) && s.isRandomlyTicking()) s.randomTick(level, p.immutable(), r);
            }
            if (now % 8 == 0)
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, z.pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 5, z.pos.getY() + 0.3 + r.nextDouble() * 0.6,
                        z.pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 5, 1, 0, 0, 0, 0);
        }
    }

    record Zone(BlockPos pos, long end) {}

    /** The boosted patches in one world, saved with it. */
    static class Zones extends SavedData {
        final List<Zone> list = new ArrayList<>();

        static Zones of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Zones::new, Zones::load, null), "crabtime_crop_boost");
        }

        void add(BlockPos pos, long end) {
            list.removeIf(z -> z.pos.equals(pos));
            list.add(new Zone(pos.immutable(), end));
            setDirty();
        }

        static Zones load(CompoundTag tag, HolderLookup.Provider reg) {
            Zones z = new Zones();
            for (Tag t : tag.getList("Zones", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                z.list.add(new Zone(BlockPos.of(c.getLong("Pos")), c.getLong("End")));
            }
            return z;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
            ListTag l = new ListTag();
            for (Zone z : list) {
                CompoundTag c = new CompoundTag();
                c.putLong("Pos", z.pos.asLong());
                c.putLong("End", z.end);
                l.add(c);
            }
            tag.put("Zones", l);
            return tag;
        }
    }
}
