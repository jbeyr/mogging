// File: me.jameesyy.mogging.client.SkinManager.kt
package me.jameesyy.mogging.client

import me.jameesyy.mogging.Mogging
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

/**
 * Manages skin textures and their properties for entities
 */
object SkinManager {
    // All available skin textures
    private val TEXTURES by lazy {
        scanForSkins()
    }

    // Cache for slim skin detection results
    private val slimSkinCache = ConcurrentHashMap<Identifier, Boolean>()

    // Get a texture by its ID (with wrapping)
    fun getTextureById(id: Int): Identifier {
        // Handle invalid IDs gracefully
        val safeId = if (id >= 0) id else 0

        // Make sure we have textures
        if (TEXTURES.isEmpty()) {
            return Identifier.of(Mogging.MOD_ID, "textures/entity/player_skins/alex.png")
        }

        // Get the texture with proper wrapping
        return TEXTURES[safeId.absoluteValue % TEXTURES.size]
    }

    // Get the default texture
    fun getDefaultTexture(): Identifier {
        return TEXTURES.firstOrNull() ?: Identifier.of(Mogging.MOD_ID, "textures/entity/player_skins/alex.png")
    }

    // Detect if a skin uses the slim model by checking pixel transparency
    fun isSlimSkin(textureId: Identifier): Boolean {
        // Check cache first
        if (slimSkinCache.containsKey(textureId)) {
            return slimSkinCache[textureId]!!
        }

        try {
            // Get the resource
            val resourceManager = MinecraftClient.getInstance().resourceManager
            val resource = resourceManager.getResource(textureId).orElse(null) ?: return false

            // Load the image
            resource.inputStream.use { stream ->
                val image = NativeImage.read(stream)

                // Check the specific pixel at (55, 31) - zero-indexed
                val pixelColor = image.getColorArgb(55, 31)
                val alpha = (pixelColor shr 24) and 0xFF

                // If alpha is 0, it's transparent, indicating a slim skin
                val isSlim = alpha == 0

                // Clean up
                image.close()

                // Cache the result
                slimSkinCache[textureId] = isSlim

                return isSlim
            }
        } catch (e: Exception) {
            // Log error and default to classic skin
            println("Error detecting skin type for $textureId: ${e.message}")
            slimSkinCache[textureId] = false
            return false
        }
    }

    // Scan for all skin textures in the mod's resources
    private fun scanForSkins(): List<Identifier> {
        val skins = mutableListOf<Identifier>()

        try {
            // Get the resource manager
            val resourceManager = MinecraftClient.getInstance().resourceManager

            // Define the starting path and predicate
            val startingPath = "textures/entity"
            val pathPredicate = { id: Identifier ->
                id.namespace == Mogging.MOD_ID && id.path.startsWith(startingPath) && id.path.endsWith(".png")
            }

            // Find all resources matching the predicate
            val resources = resourceManager.findResources(startingPath, pathPredicate)

            // Add all found resources to our list
            for ((identifier, _) in resources) {
                skins.add(identifier)
                println("Found skin texture: $identifier")
            }

            println("Found ${skins.size} skin textures")
        } catch (e: Exception) {
            println("Error scanning for skin textures: ${e.message}")
            e.printStackTrace()
        }

        // If no skins were found, add at least one default skin
        if (skins.isEmpty()) {
            skins.add(Identifier.of(Mogging.MOD_ID, "textures/entity/player_skins/alex.png"))
        }

        return skins
    }
}