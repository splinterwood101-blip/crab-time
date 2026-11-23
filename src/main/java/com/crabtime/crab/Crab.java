package com.crabtime.crab;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import com.crabtime.block.CrabNestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Optional;

/**
 * The crab. Neutral: it wanders beaches and the sea floor, digs up glimmer sand and driftwood and carries them home to
 * its nest, sheds a claw now and then near the nest, snips leads, and buries itself in the sand when it's losing a fight.
 */
public class Crab extends PathfinderMob {
    public static final String[] ANIMS = {"dig", "carry", "warn", "pinch", "snip", "shed", "bury"};
    public static final float[] DUR = {2.6f, 2.6f, 1.8f, 1.0f, 1.2f, 1.2f, 2.4f};
    public static final int DIG = 0, WARN = 2, PINCH = 3, SNIP = 4, SHED = 5, BURY = 6;
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Crab.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> HELD = SynchedEntityData.defineId(Crab.class, EntityDataSerializers.ITEM_STACK);

    public static final ResourceKey<LootTable> DIG_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CrabTime.id("gameplay/crab_dig"));
    @Nullable private BlockPos nest;
    private int seq, animIdx = -1, animStart;
    private int digCooldown, shedCooldown, buryCooldown, buryTicks, warnTicks, carryTicks, leashTicks, nestSearch, nestless;
    // client
    private int clientAnim = -1, clientStart;
    private float side, sideO, carry, carryO;
    /** Client only: which looping animation is showing. */
    public final AnimState animState = new AnimState();

    public Crab(EntityType<? extends Crab> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, 0f);
        RandomSource r = level.getRandom();
        digCooldown = 200 + r.nextInt(1200);
        shedCooldown = 6000 + r.nextInt(6000);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    /** Beaches (sand or gravel underfoot) or anywhere in the sea. */
    public static boolean checkCrabSpawn(EntityType<Crab> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource r) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.SAND) || below.is(Blocks.GRAVEL);
    }

    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }
    @Override protected float getWaterSlowDown() { return 0.9f; }
    @Override public boolean removeWhenFarAway(double dist) { return false; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new Busy());
        goalSelector.addGoal(1, new Pinch());
        goalSelector.addGoal(2, new Deliver());
        goalSelector.addGoal(3, new Dig());
        goalSelector.addGoal(4, new Shed());
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.9, 80));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ANIM, 0);
        b.define(HELD, ItemStack.EMPTY);
    }

    // ---------- animations ----------
    public void playAnim(int idx) {
        seq = (seq + 1) & 0x7FFF;
        entityData.set(ANIM, (seq << 16) | (idx + 1));
        animIdx = idx;
        animStart = tickCount;
    }

    public int currentAnim() { return animIdx >= 0 && tickCount - animStart < DUR[animIdx] * 20f ? animIdx : -1; }
    /** Moves the crab stands still for. */
    public boolean busy() { int a = currentAnim(); return a == DIG || a == WARN || a == SNIP || a == SHED || a == BURY || warnTicks > 0 || buryTicks > 0; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANIM.equals(key)) { clientAnim = (entityData.get(ANIM) & 0xFFFF) - 1; clientStart = tickCount; }
    }

    @Nullable public String animName() {
        if (clientAnim < 0 || clientAnim >= ANIMS.length) return null;
        return (tickCount - clientStart) / 20f <= DUR[clientAnim] ? ANIMS[clientAnim] : null;
    }

    public float animTime(float pt) { return (tickCount + pt - clientStart) / 20f; }
    public float sideways(float pt) { return Mth.lerp(pt, sideO, side); }
    public float carryAmount(float pt) { return Mth.lerp(pt, carryO, carry); }

    public ItemStack held() { return entityData.get(HELD); }
    public boolean hasHeld() { return !held().isEmpty(); }
    private void setHeld(ItemStack s) { entityData.set(HELD, s); carryTicks = 0; }
    public void setNest(@Nullable BlockPos pos) { nest = pos; }

    // ---------- ticking ----------
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            sideO = side; carryO = carry;
            boolean walking = getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 && getTarget() == null && animName() == null;
            side += ((walking ? 90f : 0f) - side) * 0.15f;
            carry += ((hasHeld() ? 1f : 0f) - carry) * 0.2f;
            return;
        }
        if (digCooldown > 0) digCooldown--;
        if (shedCooldown > 0) shedCooldown--;
        if (buryCooldown > 0) buryCooldown--;
        if (warnTicks > 0) warnTicks--;
        if (buryTicks > 0 && --buryTicks == 0) { setTarget(null); setLastHurtByMob(null); }
        if (busy()) getNavigation().stop();
        if (buryTicks > 10 && tickCount % 4 == 0) dust(4);
        // leads: crabs don't stay leashed, they snip them
        if (isLeashed()) {
            if (++leashTicks == 20) playAnim(SNIP);
            if (leashTicks >= 32) {
                dropLeash(true, true); leashTicks = 0;
                level().playSound(null, blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.8f, 1.5f);
            }
        } else leashTicks = 0;
        // no nest: join one nearby, or (standing on beach sand, out of the water) build a new one after about 30 seconds
        if (!validNest()) {
            nestless++;
            if (hasHeld()) carryTicks++;
            if (nestless % 100 == 0) {
                nest = findNest(24).orElse(null);
                if (nest == null && nestless >= 600 && canNestHere()) {
                    BlockPos at = blockPosition();
                    level().setBlock(at, ModRegistry.CRAB_NEST.get().defaultBlockState(), 3);
                    nest = at;
                    moveTo(getX(), at.getY() + 0.5, getZ());
                    playSound(SoundEvents.SAND_PLACE, 1f, 1f);
                }
            }
            if (hasHeld() && carryTicks > 2400) { spawnAtLocation(held()); setHeld(ItemStack.EMPTY); }
        } else nestless = 0;
    }

    /** Nests only go on sand, above the water. */
    private boolean canNestHere() {
        BlockPos at = blockPosition();
        return onGround() && !isInWater() && getTarget() == null && level().getBlockState(at.below()).is(BlockTags.SAND)
                && level().isEmptyBlock(at) && level().getFluidState(at).isEmpty();
    }

    private boolean validNest() { return nest != null && level().getBlockState(nest).is(ModRegistry.CRAB_NEST.get()); }

    private Optional<BlockPos> findNest(int radius) {
        if (!(level() instanceof ServerLevel sl)) return Optional.empty();
        return sl.getPoiManager().findClosest(h -> h.is(ModRegistry.NEST_POI.getKey()), blockPosition(), radius, PoiManager.Occupancy.ANY);
    }

    private void dust(int n) {
        if (level() instanceof ServerLevel sl) {
            BlockState below = level().getBlockState(blockPosition().below());
            if (below.isAir()) below = Blocks.SAND.defaultBlockState();
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.1, getZ(), n, 0.3, 0.05, 0.3, 0.05);
        }
    }

    /** What a dig turns up: the loot table data/crabtime/loot_table/gameplay/crab_dig.json. */
    private ItemStack digUp() {
        if (!(level() instanceof ServerLevel sl)) return ItemStack.EMPTY;
        LootTable table = sl.getServer().reloadableRegistries().getLootTable(DIG_LOOT);
        LootParams params = new LootParams.Builder(sl).withParameter(LootContextParams.ORIGIN, position())
                .withParameter(LootContextParams.THIS_ENTITY, this).create(LootContextParamSets.GIFT);
        for (ItemStack s : table.getRandomItems(params)) if (!s.isEmpty()) return s;
        return ItemStack.EMPTY;
    }

    // ---------- fighting ----------
    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean calm = getTarget() == null;
        boolean hit = super.hurt(src, amount);
        if (!hit || level().isClientSide || !isAlive()) return hit;
        if (calm && src.getEntity() instanceof LivingEntity && buryTicks == 0) {
            warnTicks = 30; playAnim(WARN);
        }
        return hit;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (hasHeld()) { spawnAtLocation(held()); setHeld(ItemStack.EMPTY); }
    }

    @Override @Nullable protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.TURTLE_HURT_BABY; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.TURTLE_DEATH_BABY; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SPIDER_STEP, 0.12f, 1.7f); }

    // ---------- saving ----------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (nest != null) tag.putLong("Nest", nest.asLong());
        if (hasHeld()) tag.put("Held", held().save(registryAccess()));
        tag.putInt("DigCooldown", digCooldown);
        tag.putInt("ShedCooldown", shedCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        nest = tag.contains("Nest") ? BlockPos.of(tag.getLong("Nest")) : null;
        if (tag.contains("Held")) setHeld(ItemStack.parseOptional(registryAccess(), tag.getCompound("Held")));
        if (tag.contains("DigCooldown")) digCooldown = tag.getInt("DigCooldown");
        if (tag.contains("ShedCooldown")) shedCooldown = tag.getInt("ShedCooldown");
    }

    // ---------- goals ----------
    /** Holds still during the moves it does in place. */
    class Busy extends Goal {
        Busy() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        @Override public boolean canUse() { return busy() && currentAnim() != DIG && currentAnim() != SHED; }
        @Override public void tick() { getNavigation().stop(); }
    }

    /** Fights back, with a pinch. */
    class Pinch extends MeleeAttackGoal {
        Pinch() { super(Crab.this, 1.3, false); }
        @Override public boolean canUse() { return !busy() && super.canUse(); }
        @Override public boolean canContinueToUse() { return buryTicks == 0 && super.canContinueToUse(); }
        @Override protected void checkAndPerformAttack(LivingEntity target) {
            if (canPerformAttack(target) && isTimeToAttack()) playAnim(PINCH);
            super.checkAndPerformAttack(target);
        }
    }

    /** Digs in the sand and comes up with glimmer sand or a driftwood log. */
    class Dig extends Goal {
        int t;
        Dig() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() {
            if (hasHeld() || digCooldown > 0 || getTarget() != null || !onGround() || getRandom().nextInt(40) != 0) return false;
            BlockState below = level().getBlockState(blockPosition().below());
            return below.is(BlockTags.SAND) || below.is(Blocks.GRAVEL) || below.is(Blocks.CLAY);
        }
        @Override public boolean canContinueToUse() { return t < 52 && getTarget() == null; }
        @Override public void start() { t = 0; getNavigation().stop(); playAnim(DIG); }
        @Override public void tick() {
            t++;
            getNavigation().stop();
            if (t > 6 && t < 46 && t % 3 == 0) dust(3);
            if (t % 10 == 5) playSound(SoundEvents.SAND_HIT, 0.5f, 1.2f);
            if (t == 50) {
                ItemStack find = digUp();
                if (!find.isEmpty()) { setHeld(find); playSound(SoundEvents.ITEM_PICKUP, 0.5f, 1.4f); }
                digCooldown = 1200 + getRandom().nextInt(1200);
            }
        }
        @Override public void stop() { if (t < 50) digCooldown = 200; }
    }

    /** Carries its find home and drops it in the nest. */
    class Deliver extends Goal {
        int repath;
        Deliver() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (!hasHeld() || busy() || getTarget() != null) return false;
            if (!validNest()) {
                if (--nestSearch > 0) return false;
                nestSearch = 100;
                nest = findNest(32).orElse(null);
            }
            return validNest();
        }
        @Override public boolean canContinueToUse() { return hasHeld() && validNest() && getTarget() == null && !busy(); }
        @Override public void start() { repath = 0; }
        @Override public void tick() {
            if (nest == null) return;
            if (distanceToSqr(nest.getX() + 0.5, nest.getY() + 0.5, nest.getZ() + 0.5) < 3.5) {
                ItemStack left = level().getBlockEntity(nest) instanceof CrabNestBlockEntity be ? be.insert(held().copy()) : held().copy();
                if (!left.isEmpty()) spawnAtLocation(left);
                setHeld(ItemStack.EMPTY);
                playSound(SoundEvents.SAND_PLACE, 0.6f, 1.3f);
                getNavigation().stop();
                return;
            }
            if (--repath <= 0) { repath = 20; getNavigation().moveTo(nest.getX() + 0.5, nest.getY(), nest.getZ() + 0.5, 1.0); }
        }
    }

    /** Near its nest, now and then, it sheds a claw (and grows a new one). */
    class Shed extends Goal {
        int t;
        Shed() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            return shedCooldown == 0 && !hasHeld() && getTarget() == null && validNest() && nest.distSqr(blockPosition()) < 64 && getRandom().nextInt(100) == 0;
        }
        @Override public boolean canContinueToUse() { return t < 26; }
        @Override public void start() { t = 0; getNavigation().stop(); playAnim(SHED); }
        @Override public void tick() {
            t++;
            getNavigation().stop();
            if (t == 18) { spawnAtLocation(new ItemStack(ModRegistry.CRAB_CLAW.get())); dust(5); playSound(SoundEvents.TURTLE_SHAMBLE_BABY, 1f, 1.2f); }
        }
        @Override public void stop() { shedCooldown = 12000 + getRandom().nextInt(12000); }
    }
}
