package me.jameesyy.mogging.client.render

import me.jameesyy.mogging.Mogging
import me.jameesyy.mogging.PiranhaEntity
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.MobEntityRenderer
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.LargeTropicalFishEntityModel
import net.minecraft.client.render.entity.state.TropicalFishEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.passive.TropicalFishEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis

class PiranhaRenderer(context: EntityRendererFactory.Context) :
    MobEntityRenderer<PiranhaEntity, TropicalFishEntityRenderState, LargeTropicalFishEntityModel>(
        context,
        LargeTropicalFishEntityModel(context.getPart(EntityModelLayers.TROPICAL_FISH_LARGE)),
        0.4f // shadow
    ) {
    private val baseTexture = Identifier.of(Mogging.MOD_ID, "textures/entity/piranha_base.png")
    private val patternTexture = Identifier.of(Mogging.MOD_ID, "textures/entity/piranha_pattern.png")

    override fun createRenderState(): TropicalFishEntityRenderState {
        val rstate = TropicalFishEntityRenderState()
        rstate.variety = TropicalFishEntity.Pattern.SUNSTREAK

        // Set piranha-specific colors
        // Primary color: silvery gray for the main body
        rstate.baseColor = 0x9C9C9C // Silvery gray

        // Secondary color: reddish-orange for belly and fins
        rstate.patternColor = 0xC13C28 // Reddish-orange

        return rstate
    }

    override fun getTexture(state: TropicalFishEntityRenderState?): Identifier {
        return baseTexture
    }

    override fun render(
        state: TropicalFishEntityRenderState?,
        matrices: MatrixStack?,
        vertexConsumers: VertexConsumerProvider?,
        light: Int
    ) {
        if (state == null || matrices == null || vertexConsumers == null) return

        matrices.push()
        val offset = getPositionOffset(state)
        matrices.translate(offset.x, offset.y, offset.z)

        // rotate
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - state.bodyYaw))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.pitch))

        // funny swimming wobble
        if (state.touchingWater) {
            val wobbleAmount = 4.5f
            val wobbleSpeed = 1.5f
            val wobble = MathHelper.sin(state.age * wobbleSpeed) * wobbleAmount
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wobble))
        }

        matrices.scale(1.5f, 1.5f, 1.5f) // so its bigger than other fish
        matrices.translate(0.0, -0.7, 0.0) // shirt model down to match hitbox
        model.setAngles(state)

        val baseColor = state.baseColor
        val patternColor = state.patternColor

        // render base + pattern textures
        val baseConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(baseTexture))
        model.render(matrices, baseConsumer, light, getOverlay(state, 0.0f), baseColor)
        val patternConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(patternTexture))
        model.render(matrices, patternConsumer, light, getOverlay(state, 0.0f), patternColor)

        matrices.pop()
    }
}