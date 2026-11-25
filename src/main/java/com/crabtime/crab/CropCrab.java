package com.crabtime.crab;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The crop crab: a farmer. It never fights; it runs from monsters and from anyone who hurts a crop crab (iron golems
 * take its side). On grass it digs up seeds; next to crops it digs up vegetables, hefty crop blocks and, rarely, ultra crop
 * meal. Near its nest and close to water it tills the ground and plants what it dug up. Breeds with seeds.
 */
public class CropCrab extends Crab {
    public static final ResourceKey<LootTable> DIG_GRASS = ResourceKey.create(Registries.LOOT_TABLE, CrabTime.id("gameplay/crop_crab_dig_grass"));
    public static final ResourceKey<LootTable> DIG_CROPS = ResourceKey.create(Registries.LOOT_TABLE, CrabTime.id("gameplay/crop_crab_dig_crops"));
    private static final EntityDataAccessor<Boolean> FLEEING = SynchedEntityData.defineId(CropCrab.class, EntityDataSerializers.BOOLEAN);
    /** Players this crab is scared of, and until when (game time). */
    private final Map<UUID, Long> scaredOf = new HashMap<>();

    public CropCrab(EntityType<? extends Crab> type, Level level) { super(type, level); }

    /** Plains: grass, dirt or farmland underfoot. Twice as likely near a village. */
    public static boolean checkCropCrabSpawn(EntityType<CropCrab> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource r) {
        BlockState below = level.getBlockState(pos.below());
        if (!(below.is(Blocks.GRASS_BLOCK) || below.is(BlockTags.DIRT) || below.is(Blocks.FARMLAND))) return false;
        if (reason == MobSpawnType.CHUNK_GENERATION) return r.nextInt(3) == 0;
        if (reason != MobSpawnType.NATURAL) return true;
        boolean village = level.getLevel().getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), pos, 64, PoiManager.Occupancy.ANY).isPresent();
        return village || r.nextBoolean();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(FLEEING, false);
    }

    @Override public boolean isFleeing() { return entityData.get(FLEEING); }

    @Override
    protected void crabGoals() {
        goalSelector.addGoal(0, new Busy());
        goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, LivingEntity.class, 10f, 1.2, 1.5, e -> e instanceof Enemy));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 12f, 1.2, 1.5, e -> isScaredOf(e)));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1, Ingredient.of(Tags.Items.SEEDS), false));
        goalSelector.addGoal(2, new Farm());
        goalSelector.addGoal(3, new Deliver());
        goalSelector.addGoal(3, new FetchEgg());
        goalSelector.addGoal(4, new Dig());
        goalSelector.addGoal(4, new Shed());
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.9, 80));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override protected boolean warns() { return false; }
    @Override protected float duration(int anim) { return anim == SHED ? 2f : anim == SNIP ? 0.6f : super.duration(anim); }
    @Override protected Block nestBlock() { return ModRegistry.CROP_NEST.get(); }
    @Override protected ResourceKey<PoiType> nestPoi() { return ModRegistry.CROP_NEST_POI.getKey(); }
    @Override protected boolean canNestOn(BlockState b) { return b.is(Blocks.GRASS_BLOCK) || b.is(BlockTags.DIRT) || b.is(Blocks.FARMLAND); }
    @Override protected boolean canDigOn(BlockState b) { return canNestOn(b); }
    @Override public Item eggItem() { return ModRegistry.CROP_CRAB_EGG.get(); }
    @Override public boolean isFood(ItemStack stack) { return stack.is(Tags.Items.SEEDS); }
    @Override @javax.annotation.Nullable public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel level, net.minecraft.world.entity.AgeableMob other) {
        return ModRegistry.CROP_CRAB.get().create(level);
    }

    /** Next to crops: vegetables, hefty blocks, ultra crop meal. On plain grass: seeds. */
    @Override
    protected ResourceKey<LootTable> digLoot() {
        BlockPos at = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2)))
            if (level().getBlockState(p).is(BlockTags.CROPS)) return DIG_CROPS;
        return DIG_GRASS;
    }

    // ---------- running away ----------
    private boolean isScaredOf(LivingEntity e) {
        Long until = scaredOf.get(e.getUUID());
        return until != null && until > level().getGameTime() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    public void scare(Player p) { scaredOf.put(p.getUUID(), level().getGameTime() + 2400); }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean hit = super.hurt(src, amount);
        if (hit && !level().isClientSide && src.getEntity() instanceof Player p && !p.isCreative()) {
            // every crop crab nearby remembers who did it, and the village's golems come for them
            for (CropCrab c : level().getEntitiesOfClass(CropCrab.class, getBoundingBox().inflate(16))) c.scare(p);
            for (IronGolem g : level().getEntitiesOfClass(IronGolem.class, getBoundingBox().inflate(24))) {
                if (g.isPlayerCreated()) continue;
                g.setPersistentAngerTarget(p.getUUID());
                g.startPersistentAngerTimer();
                g.setTarget(p);
            }
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 5 == 0) {
            boolean running = goalSelector.getAvailableGoals().stream().anyMatch(w -> w.isRunning()
                    && (w.getGoal() instanceof AvoidEntityGoal<?> || w.getGoal() instanceof PanicGoal));
            if (running != entityData.get(FLEEING)) entityData.set(FLEEING, running);
        }
    }

    // ---------- farming ----------
    private static Block cropFor(ItemStack s) {
        if (s.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
        if (s.is(Items.CARROT)) return Blocks.CARROTS;
        if (s.is(Items.POTATO)) return Blocks.POTATOES;
        if (s.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
        return null;
    }

    private boolean nearWater(BlockPos ground) {
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-4, -1, -4), ground.offset(4, 0, 4)))
            if (level().getFluidState(p).is(FluidTags.WATER)) return true;
        return false;
    }

    /** With something plantable in its claws: till a patch of dirt or grass near the nest (within 4 of water) and plant it. */
    class Farm extends Goal {
        BlockPos ground;
        int cooldown, t;
        Farm() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() {
            if (--cooldown > 0 || isBaby() || isFleeing() || busy() || cropFor(held()) == null || nest() == null || !validNest()) return false;
            cooldown = 100;
            if (!EventHooks.canEntityGrief((ServerLevel) level(), CropCrab.this)) return false;
            RandomSource r = getRandom();
            for (int i = 0; i < 24; i++) {
                BlockPos g = nest().offset(r.nextInt(11) - 5, r.nextInt(3) - 1, r.nextInt(11) - 5);
                BlockState s = level().getBlockState(g);
                if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT)) && level().isEmptyBlock(g.above()) && !g.equals(nest().below()) && nearWater(g)) {
                    ground = g;
                    return true;
                }
            }
            return false;
        }

        @Override public boolean canContinueToUse() { return ground != null && t < 400 && cropFor(held()) != null && !isFleeing(); }
        @Override public void start() { t = 0; getNavigation().moveTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 1.0); }

        @Override public void tick() {
            t++;
            if (distanceToSqr(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5) > 2.5) {
                if (t % 20 == 0) getNavigation().moveTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 1.0);
                return;
            }
            getNavigation().stop();
            BlockState s = level().getBlockState(ground);
            Block crop = cropFor(held());
            if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT)) && level().isEmptyBlock(ground.above()) && crop != null) {
                playAnim(DIG);
                level().setBlock(ground, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
                level().setBlock(ground.above(), crop.defaultBlockState(), 3);
                playSound(SoundEvents.HOE_TILL, 0.8f, 1.2f);
                ItemStack h = held().copy(); h.shrink(1); setHeld(h);
                dust(6);
            }
            ground = null;
        }
    }
}
