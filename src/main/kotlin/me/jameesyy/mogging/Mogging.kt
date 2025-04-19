package me.jameesyy.mogging

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.*
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.world.Heightmap

class Mogging : ModInitializer {

    companion object {
        const val MOD_ID = "mogging"

        // Create a registry key for our entity
        private val HACKERMAN_KEY = RegistryKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(MOD_ID, "hackerman")
        )

        val HACKERMAN: EntityType<HackermanEntity> = Registry.register(
            Registries.ENTITY_TYPE,
            HACKERMAN_KEY.value,
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER) { entityType, world ->
                HackermanEntity(entityType, world)
            }.dimensions(EntityDimensions.fixed(0.6f, 1.95f)).build(HACKERMAN_KEY)
        )
    }

    override fun onInitialize() {
        // Register entity attributes
        FabricDefaultAttributeRegistry.register(HACKERMAN, HackermanEntity.createAttributes())

        // Set up natural spawning
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            HACKERMAN,
            30, // Weight
            1,  // Min in group
            3   // Max in group
        )

        // Set spawn restrictions
        SpawnRestriction.register(
            HACKERMAN,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            ZombieEntity::canSpawnInDark
        )
    }
}