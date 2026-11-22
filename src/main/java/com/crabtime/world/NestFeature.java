package com.crabtime.world;

import com.crabtime.ModRegistry;
import com.crabtime.crab.Crab;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A crab nest on the beach sand, with 2 to 4 crabs living around it. */
public class NestFeature extends Feature<NoneFeatureConfiguration> {
    public NestFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos pos = ctx.origin();
        RandomSource r = ctx.random();
        if (!level.getBlockState(pos.below()).is(BlockTags.SAND) || !level.isEmptyBlock(pos) || !level.getFluidState(pos).isEmpty()) return false;
        level.setBlock(pos, ModRegistry.CRAB_NEST.get().defaultBlockState(), 2);
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            Crab crab = ModRegistry.CRAB.get().create(level.getLevel());
            if (crab == null) continue;
            int x = pos.getX() + r.nextInt(5) - 2, z = pos.getZ() + r.nextInt(5) - 2;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            crab.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0);
            crab.setNest(pos);
            crab.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
            crab.setPersistenceRequired();
            level.addFreshEntityWithPassengers(crab);
        }
        return true;
    }
}
