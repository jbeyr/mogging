package me.jameesyy.mogging

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.*
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.passive.FishEntity
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.util.Identifier
import net.minecraft.world.Heightmap

object MoggingEntities {
    // Create registry keys for our entities
    private val HACKERMAN_KEY = RegistryKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Mogging.MOD_ID, "hackerman")
    )

    private val PIRANHA_KEY = RegistryKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Mogging.MOD_ID, "piranha")
    )

    private val STATIC_SHEEP_KEY = RegistryKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Mogging.MOD_ID, "static_sheep")
    )

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

    fun registerAll() {
        FabricDefaultAttributeRegistry.register(HACKERMAN, HackermanEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(PIRANHA, PiranhaEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(STATIC_SHEEP, StaticSheepEntity.createAttributes())

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

        // Set up natural spawning for Piranha
        BiomeModifications.addSpawn(
            BiomeSelectors.tag(BiomeTags.IS_OCEAN),
            SpawnGroup.WATER_AMBIENT,
            PIRANHA,
            50,
            3,
            6
        )

        // Set spawn restrictions for Piranha
        SpawnRestriction.register(
            PIRANHA,
            SpawnLocationTypes.IN_WATER,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            FishEntity::canSpawn
        )

        // Static Sheep spawning - less common, in plains and meadows
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(
                net.minecraft.world.biome.BiomeKeys.PLAINS,
                net.minecraft.world.biome.BiomeKeys.MEADOW
            ),
            SpawnGroup.CREATURE,
            STATIC_SHEEP,
            10, // Lower weight than regular sheep
            1,
            2
        )

        SpawnRestriction.register(
            STATIC_SHEEP,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            SheepEntity::canMobSpawn
        )
    }
}