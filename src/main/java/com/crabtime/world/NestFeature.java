package com.crabtime.world;

import com.crabtime.ModRegistry;
import com.crabtime.crab.Crab;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A crab nest with 2 to 4 crabs living around it. Beach version: on the sand. Crop version: on plains grass,
 * mostly next to villages (it looks for village paths in the chunk) and only now and then out in the open.
 */
public class NestFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean crop;

    public NestFeature(Codec<NoneFeatureConfiguration> codec, boolean crop) { super(codec); this.crop = crop; }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos pos = ctx.origin();
        if (crop) {
            pos = cropSpot(level, pos, r);
            if (pos == null) return false;
        } else if (!level.getBlockState(pos.below()).is(BlockTags.SAND)) return false;
        if (!level.isEmptyBlock(pos) || !level.getFluidState(pos).isEmpty()) return false;
        level.setBlock(pos, (crop ? ModRegistry.CROP_NEST : ModRegistry.CRAB_NEST).get().defaultBlockState(), 2);
        int n = 2 + r.nextInt(crop ? 2 : 3);
        for (int i = 0; i < n; i++) {
            Crab crab = crop ? ModRegistry.CROP_CRAB.get().create(level.getLevel()) : ModRegistry.CRAB.get().create(level.getLevel());
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

    /** Next to a village path: 1 in 3 chunks. Open plains: 1 in 40. Always on top of grass. */
    private static BlockPos cropSpot(WorldGenLevel level, BlockPos origin, RandomSource r) {
        int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
        BlockPos path = null;
        for (int i = 0; i < 48 && path == null; i++) {
            int x = cx + r.nextInt(16), z = cz + r.nextInt(16);
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
            if (level.getBlockState(top).is(Blocks.DIRT_PATH)) path = top;
        }
        if (path == null ? r.nextInt(40) != 0 : r.nextInt(3) != 0) return null;
        BlockPos near = path != null ? path : new BlockPos(cx + r.nextInt(16), 0, cz + r.nextInt(16));
        for (int i = 0; i < 16; i++) {
            int x = near.getX() + r.nextInt(9) - 4, z = near.getZ() + r.nextInt(9) - 4;
            if ((x & ~15) != cx || (z & ~15) != cz) continue;
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && level.isEmptyBlock(at)) return at;
        }
        return null;
    }
}
