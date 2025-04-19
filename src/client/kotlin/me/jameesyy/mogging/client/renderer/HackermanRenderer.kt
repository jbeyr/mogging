package me.jameesyy.mogging.client.renderer

import me.jameesyy.mogging.HackermanEntity
import me.jameesyy.mogging.Mogging
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.MobEntityRenderer
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.util.Identifier

class HackermanRenderer(context: EntityRendererFactory.Context) :
    MobEntityRenderer<HackermanEntity, PlayerEntityRenderState, PlayerEntityModel>(
        context,
        PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), // Use Steve model
        0.5f // shadow radius
    ) {

    companion object {
        private val TEXTURE = Identifier.of(Mogging.MOD_ID, "textures/entity/alex.png")
    }

    init {
        // Add the held item feature renderer
        this.addFeature(HeldItemFeatureRenderer(this))
    }

    override fun getTexture(state: PlayerEntityRenderState?): Identifier {
        return TEXTURE
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
            // Use vanilla's hand swing progress calculation
            state.handSwingProgress = entity.getHandSwingProgress(tickDelta)
            state.activeHand = entity.activeHand
            state.isUsingItem = false
        }
    }
}