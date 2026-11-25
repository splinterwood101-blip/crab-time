package com.crabtime;

import com.crabtime.crab.Crab;
import com.crabtime.crab.CropCrab;
import com.crabtime.item.UltraCropMealItem;
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UltraCropMealItem::levelTick);
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(ModRegistry.CRAB.get(), Crab.createAttributes().build());
        e.put(ModRegistry.CROP_CRAB.get(), CropCrab.createAttributes().build());
    }

    private void spawnPlacements(RegisterSpawnPlacementsEvent e) {
        e.register(ModRegistry.CRAB.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Crab::checkCrabSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(ModRegistry.CROP_CRAB.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CropCrab::checkCropCrabSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
