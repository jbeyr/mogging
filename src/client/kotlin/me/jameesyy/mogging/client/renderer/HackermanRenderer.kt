package me.jameesyy.mogging.client.renderer

import me.jameesyy.mogging.HackermanEntity
import me.jameesyy.mogging.Mogging
import me.jameesyy.mogging.SkinManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.MobEntityRenderer
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier

class HackermanRenderer(context: EntityRendererFactory.Context) :
    MobEntityRenderer<HackermanEntity, PlayerEntityRenderState, PlayerEntityModel>(
        context,
        PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false),
        0.5f
    ) {

    // The models to use (slim and classic)
    // Initialize both model types
    private val slimModel: PlayerEntityModel = PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true)
    private val classicModel: PlayerEntityModel = PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false)

    // Thread-local storage for current entity being rendered
    private val currentEntity = ThreadLocal<HackermanEntity>()

    companion object {
        // Scan for skins at initialization time (client-side only)
        private val TEXTURES by lazy {
            val skins = mutableListOf<Identifier>()

            try {
                // Get resource manager (client-side only)
                val resourceManager = MinecraftClient.getInstance().resourceManager

                // Find all skin textures
                val startingPath = "textures/entity/player_skins"
                val pathPredicate = { id: Identifier ->
                    id.namespace == Mogging.MOD_ID &&
                            id.path.startsWith(startingPath) &&
                            id.path.endsWith(".png")
                }

                val resources = resourceManager.findResources(startingPath, pathPredicate)

                for ((identifier, _) in resources) {
                    skins.add(identifier)
                    println("Found skin texture: $identifier")
                }

                println("Found ${skins.size} skin textures")
            } catch (e: Exception) {
                // Fallback
                println("Error scanning for skin textures: ${e.message}")
                skins.add(Identifier.of(Mogging.MOD_ID, "textures/entity/player_skins/alex.png"))
            }

            if (skins.isEmpty()) {
                skins.add(Identifier.of(Mogging.MOD_ID, "textures/entity/player_skins/alex.png"))
            }

            skins
        }
    }

    init {
        // Add held item renderer
        this.addFeature(HeldItemFeatureRenderer(this))
    }

    override fun render(
        entity: PlayerEntityRenderState?,
        matrices: MatrixStack?,
        vertexConsumers: VertexConsumerProvider?,
        light: Int
    ) {
        try {
            if (entity == null) return

            // Get the associated Hackerman entity
            val hackermanEntity = currentEntity.get() ?: return

            // Get texture for this entity
            val texture = SkinManager.getTextureById(hackermanEntity.getSkinIndex())

            // Detect if it's a slim skin and set the appropriate model
            model = if (SkinManager.isSlimSkin(texture)) slimModel else classicModel

            super.render(entity, matrices, vertexConsumers, light)
        } finally {
            // Clear thread-local storage when done
            currentEntity.remove()
        }
    }

    override fun getTexture(state: PlayerEntityRenderState?): Identifier {
        // Get the current entity from thread-local storage
        val entity = currentEntity.get()

        if (entity != null) {
            // Use SkinManager to get the texture
            return SkinManager.getTextureById(entity.getSkinIndex())
        }

        // Fallback
        return SkinManager.getDefaultTexture()
    }



    override fun createRenderState(): PlayerEntityRenderState {
        return PlayerEntityRenderState()
    }

    override fun updateRenderState(
        entity: HackermanEntity?,
        state: PlayerEntityRenderState?,
        tickDelta: Float
    ) {
        super.updateRenderState(entity, state, tickDelta)

        if (entity != null && state != null) {
            // Store the entity reference for use in render method
            currentEntity.set(entity)

            // Set animation properties
            state.handSwingProgress = entity.getHandSwingProgress(tickDelta)
            state.activeHand = entity.activeHand
            state.isUsingItem = false
        }
    }
}