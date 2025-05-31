package me.jameesyy.mogging

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.*
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.passive.CowEntity
import net.minecraft.entity.passive.FishEntity
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.util.Identifier
import net.minecraft.world.Heightmap

fun entityTypeRegistryKey(identifierName: String): RegistryKey<EntityType<*>> {
    return RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Mogging.MOD_ID, identifierName))
}

object MoggingEntities {
    // Create registry keys for our entities
    private val HACKERMAN_KEY = entityTypeRegistryKey("hackerman")
    private val PIRANHA_KEY = entityTypeRegistryKey("piranha")
    private val STATIC_SHEEP_KEY = entityTypeRegistryKey("static_sheep")
    private val BULLISH_COW_KEY = entityTypeRegistryKey("bullish_cow")
    private val CAMO_CREEPER_KEY = entityTypeRegistryKey("camo_creeper")
    private val PALADIN_KEY = entityTypeRegistryKey("paladin")


    // Register the entity types
    val HACKERMAN: EntityType<HackermanEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        HACKERMAN_KEY.value,
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER) { entityType, world ->
            HackermanEntity(entityType, world)
        }.dimensions(EntityDimensions.fixed(0.6f, 1.95f)).build(HACKERMAN_KEY)
    )

    val PIRANHA: EntityType<PiranhaEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        PIRANHA_KEY.value,
        FabricEntityTypeBuilder.create(SpawnGroup.WATER_AMBIENT) { entityType, world ->
            PiranhaEntity(entityType, world)
        }.dimensions(EntityDimensions.fixed(1f, 0.6f)).build(PIRANHA_KEY)
    )

    val STATIC_SHEEP: EntityType<StaticSheepEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        STATIC_SHEEP_KEY.value,
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE) { entityType, world ->
            StaticSheepEntity(entityType as EntityType<SheepEntity>, world)
        }.dimensions(EntityDimensions.fixed(0.9f, 1.3f)).build(STATIC_SHEEP_KEY)
    )

    val BULLISH_COW: EntityType<BullishCowEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        BULLISH_COW_KEY.value,
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE) { entityType, world ->
            BullishCowEntity(entityType as EntityType<BullishCowEntity>, world)
        }.dimensions(EntityDimensions.fixed(0.9f, 1.3f)).build(BULLISH_COW_KEY)
    )

    val CAMO_CREEPER: EntityType<CamoCreeperEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        CAMO_CREEPER_KEY.value,
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER) { entityType, world ->
            CamoCreeperEntity(entityType as EntityType<CamoCreeperEntity>, world)
        }.trackRangeBlocks(6)
        .dimensions(EntityDimensions.fixed(0.6f, 1.7f)).build(CAMO_CREEPER_KEY)
    )

    val PALADIN: EntityType<PaladinEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        PALADIN_KEY.value,
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER) { type, world ->
            PaladinEntity(type, world)
        }.dimensions(EntityDimensions.fixed(0.6f, 1.99f)).build(PALADIN_KEY)
    )

    private fun registerDefaultAttributeRegistry() {
        FabricDefaultAttributeRegistry.register(HACKERMAN, HackermanEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(PIRANHA, PiranhaEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(STATIC_SHEEP, StaticSheepEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(BULLISH_COW, BullishCowEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(CAMO_CREEPER, CamoCreeperEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(PALADIN, PaladinEntity.createAttributes())
    }

    fun registerAll() {
        registerDefaultAttributeRegistry()

        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            HACKERMAN,
            30,
            1,
            3
        )
        SpawnRestriction.register(
            HACKERMAN,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            ZombieEntity::canSpawnInDark
        )

        BiomeModifications.addSpawn(
            BiomeSelectors.tag(BiomeTags.IS_OCEAN),
            SpawnGroup.WATER_AMBIENT,
            PIRANHA,
            50,
            3,
            6
        )
        SpawnRestriction.register(
            PIRANHA,
            SpawnLocationTypes.IN_WATER,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            FishEntity::canSpawn
        )

        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(
                net.minecraft.world.biome.BiomeKeys.PLAINS,
                net.minecraft.world.biome.BiomeKeys.MEADOW
            ),
            SpawnGroup.CREATURE,
            STATIC_SHEEP,
            10, // lower weight than regular sheep
            1,
            2
        )
        SpawnRestriction.register(
            STATIC_SHEEP,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            SheepEntity::canMobSpawn
        )

        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(
                net.minecraft.world.biome.BiomeKeys.PLAINS,
                net.minecraft.world.biome.BiomeKeys.MEADOW,
                net.minecraft.world.biome.BiomeKeys.FOREST,
                net.minecraft.world.biome.BiomeKeys.FLOWER_FOREST,
                net.minecraft.world.biome.BiomeKeys.TAIGA,
                net.minecraft.world.biome.BiomeKeys.SNOWY_TAIGA,
                net.minecraft.world.biome.BiomeKeys.OLD_GROWTH_PINE_TAIGA,
                net.minecraft.world.biome.BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA,
                net.minecraft.world.biome.BiomeKeys.SAVANNA,
                net.minecraft.world.biome.BiomeKeys.SAVANNA_PLATEAU,
                net.minecraft.world.biome.BiomeKeys.WINDSWEPT_SAVANNA,
                net.minecraft.world.biome.BiomeKeys.SUNFLOWER_PLAINS
            ),
            SpawnGroup.CREATURE,
            BULLISH_COW,
            5, // lower weight than regular cows (typically 8-10)
            3,
            5
        )
        SpawnRestriction.register(
            BULLISH_COW,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            CowEntity::canMobSpawn
        )


        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            CAMO_CREEPER,
            20,
            1,
            1
        )
        SpawnRestriction.register(
            CAMO_CREEPER,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            CreeperEntity::canSpawnInDark
        )


        SpawnRestriction.register(
            PALADIN,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            net.minecraft.entity.mob.SkeletonEntity::canSpawnInDark
        )
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            PALADIN,
            25, 1, 2
        )
    }
}