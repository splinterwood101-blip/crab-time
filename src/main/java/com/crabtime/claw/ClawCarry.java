package com.crabtime.claw;

import com.crabtime.CrabTime;
import com.crabtime.item.BigCrabClawItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Big Crab Claw's carrying: the grabbed mob is held in front of you (no AI, no gravity, can't be hurt) until you set it
 * down, throw it, or put the claw away. The mob remembers its old settings in its own data, so nothing gets stuck if the
 * world is closed while you're carrying it.
 */
@EventBusSubscriber(modid = CrabTime.MODID)
public final class ClawCarry {
    private static final String KEY = "crabtime_carried";
    private static final Map<UUID, UUID> CARRY = new HashMap<>();

    public static boolean carrying(ServerPlayer p) { return held(p) != null; }

    private static Mob held(ServerPlayer p) {
        UUID id = CARRY.get(p.getUUID());
        if (id == null) return null;
        Entity e = p.serverLevel().getEntity(id);
        if (e instanceof Mob m && m.isAlive()) return m;
        CARRY.remove(p.getUUID());
        if (e instanceof Mob m) restore(m);
        return null;
    }

    private static boolean holdingClaw(Player p) {
        return p.getMainHandItem().getItem() instanceof BigCrabClawItem || p.getOffhandItem().getItem() instanceof BigCrabClawItem;
    }

    public static boolean canGrab(Entity e) {
        return e instanceof Mob m && m.isAlive() && !m.getType().is(Tags.EntityTypes.BOSSES) && !m.getPersistentData().contains(KEY);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        ItemStack stack = e.getItemStack();
        if (!(stack.getItem() instanceof BigCrabClawItem) || !canGrab(e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(e.getEntity() instanceof ServerPlayer sp) || carrying(sp)) return;
        Mob m = (Mob) e.getTarget();
        m.stopRiding();
        m.ejectPassengers();
        if (m.isLeashed()) m.dropLeash(true, true);
        CompoundTag t = new CompoundTag();
        t.putBoolean("NoAI", m.isNoAi());
        t.putBoolean("NoGravity", m.isNoGravity());
        t.putBoolean("Invulnerable", m.isInvulnerable());
        m.getPersistentData().put(KEY, t);
        m.setNoAi(true);
        m.setNoGravity(true);
        m.setInvulnerable(true);
        m.setTarget(null);
        CARRY.put(sp.getUUID(), m.getUUID());
        stack.hurtAndBreak(1, sp, LivingEntity.getSlotForHand(e.getHand()));
        sp.level().playSound(null, m.blockPosition(), SoundEvents.ARMOR_EQUIP_TURTLE.value(), SoundSource.PLAYERS, 1f, 1.3f);
        sp.swing(e.getHand(), true);
    }

    private static void restore(Mob m) {
        CompoundTag t = m.getPersistentData().getCompound(KEY);
        m.setNoAi(t.getBoolean("NoAI"));
        m.setNoGravity(t.getBoolean("NoGravity"));
        m.setInvulnerable(t.getBoolean("Invulnerable"));
        m.getPersistentData().remove(KEY);
        m.fallDistance = 0;
    }

    private static Mob release(ServerPlayer p) {
        Mob m = held(p);
        CARRY.remove(p.getUUID());
        if (m != null) restore(m);
        return m;
    }

    public static void setDown(ServerPlayer p, Vec3 at) {
        Mob m = release(p);
        if (m == null) return;
        m.moveTo(at.x, at.y, at.z, p.getYRot() + 180f, 0);
        m.setDeltaMovement(Vec3.ZERO);
        p.level().playSound(null, m.blockPosition(), SoundEvents.ARMOR_EQUIP_TURTLE.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    public static void throwMob(ServerPlayer p) {
        Mob m = release(p);
        if (m == null) return;
        Vec3 v = p.getLookAngle().scale(1.4).add(0, 0.35, 0);
        m.setDeltaMovement(v);
        m.hurtMarked = true;
        p.level().playSound(null, m.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 1.3f);
        p.swing(p.getMainHandItem().getItem() instanceof BigCrabClawItem ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !CARRY.containsKey(p.getUUID())) return;
        Mob m = held(p);
        if (m == null) return;
        if (!holdingClaw(p) || m.level() != p.level() || !p.isAlive()) { release(p); return; }
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        double dist = 1.7 + m.getBbWidth() / 2;
        Vec3 at = p.getEyePosition().add(flat.scale(dist)).add(0, Math.max(-0.6, Math.min(0.3, look.y)) * 0.8 - m.getBbHeight() * 0.5 - 0.7, 0);
        m.moveTo(at.x, at.y, at.z, p.getYRot() + 180f, 0);
        m.setYHeadRot(p.getYRot() + 180f);
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) release(p); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) release(p); }

    /** A mob saved while being carried gets its settings back when it loads again. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m) || !m.getPersistentData().contains(KEY)) return;
        if (!CARRY.containsValue(m.getUUID())) restore(m);
    }

    private ClawCarry() {}
}
