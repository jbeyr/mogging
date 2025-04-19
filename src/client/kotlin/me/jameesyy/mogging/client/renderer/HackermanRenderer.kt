package me.jameesyy.mogging.client.renderer

import me.jameesyy.mogging.HackermanEntity
import me.jameesyy.mogging.Mogging
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.MobEntityRenderer
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.util.Identifier

class HackermanRenderer(context: EntityRendererFactory.Context) :
    MobEntityRenderer<HackermanEntity, PlayerEntityRenderState, PlayerEntityModel>(
        context,
        PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), true),
        0.5f // shadow radius
    ) {

    companion object {
        private val TEXTURE = Identifier.of(Mogging.MOD_ID, "textures/entity/alex.png")
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
            state.handSwingProgress = entity.handSwingProgress
            state.activeHand = entity.activeHand
            state.isUsingItem = entity.isUsingItem
        }
    }
}