package com.crabtime;

import com.crabtime.crab.Crab;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Crab Time: crabs that dig up glimmer sand and driftwood, their nests, and the Big Crab Claw. */
@Mod(CrabTime.MODID)
public class CrabTime {
    public static final String MODID = "crabtime";

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }

    public CrabTime(IEventBus bus) {
        ModRegistry.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::spawnPlacements);
    }

    private void attributes(EntityAttributeCreationEvent e) { e.put(ModRegistry.CRAB.get(), Crab.createAttributes().build()); }

    private void spawnPlacements(RegisterSpawnPlacementsEvent e) {
        e.register(ModRegistry.CRAB.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Crab::checkCrabSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
