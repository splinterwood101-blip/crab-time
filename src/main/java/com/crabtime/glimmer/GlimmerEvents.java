package com.crabtime.glimmer;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.EnumSet;
import java.util.Optional;

/** Glimmer Sand keeps hostile mobs away: none spawn within 8 blocks, and they back away from it if they wander close. */
@EventBusSubscriber(modid = CrabTime.MODID)
public final class GlimmerEvents {
    public static final int SPAWN_RANGE = 8, AVOID_RANGE = 5;

    static Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int range) {
        return level.getPoiManager().findClosest(h -> h.is(ModRegistry.GLIMMER_POI.getKey()), pos, range, PoiManager.Occupancy.ANY);
    }

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.PositionCheck e) {
        MobSpawnType t = e.getSpawnType();
        if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION && t != MobSpawnType.PATROL && t != MobSpawnType.EVENT) return;
        if (e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (nearest(e.getLevel().getLevel(), e.getEntity().blockPosition(), SPAWN_RANGE).isPresent())
            e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof PathfinderMob m) || !(m instanceof Enemy)) return;
        if (m.getType().is(Tags.EntityTypes.BOSSES)) return;
        m.goalSelector.addGoal(0, new Avoid(m));
    }

    /** Walks away from glimmer sand. */
    static class Avoid extends Goal {
        final PathfinderMob mob;
        int check;
        Vec3 away;
        Avoid(PathfinderMob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() {
            if (--check > 0 || !(mob.level() instanceof ServerLevel sl)) return false;
            check = 20 + mob.getRandom().nextInt(10);
            Optional<BlockPos> g = nearest(sl, mob.blockPosition(), AVOID_RANGE);
            if (g.isEmpty()) return false;
            away = DefaultRandomPos.getPosAway(mob, 12, 6, Vec3.atCenterOf(g.get()));
            return away != null;
        }
        @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }
        @Override public void start() { mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2); }
    }

    private GlimmerEvents() {}
}
