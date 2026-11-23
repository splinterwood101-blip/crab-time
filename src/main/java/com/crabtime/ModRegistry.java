package com.crabtime;

import com.crabtime.block.CrabNestBlock;
import com.crabtime.block.CrabNestBlockEntity;
import com.crabtime.crab.Crab;
import com.crabtime.item.BigCrabClawItem;
import com.crabtime.item.DriftwoodItem;
import com.crabtime.recipe.DriftwoodRecipe;
import com.crabtime.world.NestFeature;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CrabTime.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrabTime.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CrabTime.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CrabTime.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, CrabTime.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, CrabTime.MODID);
    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CrabTime.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrabTime.MODID);

    // blocks
    public static final DeferredBlock<Block> GLIMMER_SAND_BLOCK = BLOCKS.register("glimmer_sand_block", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).strength(0.6f).sound(SoundType.SAND).lightLevel(s -> 12)));
    public static final DeferredBlock<RotatedPillarBlock> DRIFTWOOD_LOG = BLOCKS.register("driftwood_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.BASS).strength(2f).sound(SoundType.WOOD).ignitedByLava()));
    public static final DeferredBlock<CrabNestBlock> CRAB_NEST = BLOCKS.register("crab_nest", () -> new CrabNestBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).strength(0.5f).sound(SoundType.SAND).noOcclusion()));

    // entity
    public static final DeferredHolder<EntityType<?>, EntityType<Crab>> CRAB = ENTITIES.register("crab",
            () -> EntityType.Builder.of(Crab::new, MobCategory.CREATURE).sized(0.75f, 0.5f).clientTrackingRange(8).build("crab"));

    // items
    public static final DeferredItem<BlockItem> GLIMMER_SAND_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("glimmer_sand_block", GLIMMER_SAND_BLOCK);
    public static final DeferredItem<DriftwoodItem> DRIFTWOOD_LOG_ITEM = ITEMS.register("driftwood_log", () -> new DriftwoodItem(DRIFTWOOD_LOG.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CRAB_NEST_ITEM = ITEMS.registerSimpleBlockItem("crab_nest", CRAB_NEST);
    public static final DeferredItem<Item> GLIMMER_SAND = ITEMS.registerSimpleItem("glimmer_sand");
    public static final DeferredItem<Item> CRAB_CLAW = ITEMS.registerSimpleItem("crab_claw");
    public static final DeferredItem<BigCrabClawItem> BIG_CRAB_CLAW = ITEMS.register("big_crab_claw", () -> new BigCrabClawItem(new Item.Properties().durability(250)));
    public static final DeferredItem<DeferredSpawnEggItem> CRAB_SPAWN_EGG = ITEMS.register("crab_spawn_egg", () -> new DeferredSpawnEggItem(CRAB, 0xC43C26, 0xEC8440, new Item.Properties()));

    // other
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrabNestBlockEntity>> NEST_BE = BLOCK_ENTITIES.register("crab_nest",
            () -> BlockEntityType.Builder.of(CrabNestBlockEntity::new, CRAB_NEST.get()).build(null));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<DriftwoodRecipe>> DRIFTWOOD_RECIPE = RECIPES.register("driftwood_brushing",
            () -> new SimpleCraftingRecipeSerializer<>(DriftwoodRecipe::new));
    public static final DeferredHolder<Feature<?>, NestFeature> NEST_FEATURE = FEATURES.register("crab_nest", () -> new NestFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<PoiType, PoiType> GLIMMER_POI = POIS.register("glimmer_sand",
            () -> new PoiType(ImmutableSet.copyOf(GLIMMER_SAND_BLOCK.get().getStateDefinition().getPossibleStates()), 0, 1));
    public static final DeferredHolder<PoiType, PoiType> NEST_POI = POIS.register("crab_nest",
            () -> new PoiType(ImmutableSet.copyOf(CRAB_NEST.get().getStateDefinition().getPossibleStates()), 0, 1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("crabtime", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crabtime")).icon(() -> new ItemStack(CRAB_CLAW.get()))
            .displayItems((p, out) -> {
                out.accept(CRAB_SPAWN_EGG.get()); out.accept(CRAB_CLAW.get()); out.accept(BIG_CRAB_CLAW.get());
                out.accept(GLIMMER_SAND.get()); out.accept(GLIMMER_SAND_BLOCK_ITEM.get()); out.accept(DRIFTWOOD_LOG_ITEM.get()); out.accept(CRAB_NEST_ITEM.get());
            }).build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); BLOCK_ENTITIES.register(bus);
        RECIPES.register(bus); FEATURES.register(bus); POIS.register(bus); TABS.register(bus);
    }

    private ModRegistry() {}
}
