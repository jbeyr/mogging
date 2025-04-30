package me.jameesyy.mogging.client.renderer

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.entity.CreeperEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.feature.CreeperChargeFeatureRenderer
import net.minecraft.client.render.entity.state.CreeperEntityRenderState
import net.minecraft.util.Identifier

const val DEFAULT = 0xFF_FFFFFF.toInt()

class CamoCreeperRenderer(ctx: EntityRendererFactory.Context) : CreeperEntityRenderer(ctx) {

    // swap this for your own texture if you like
    private val tex = Identifier.of("minecraft", "textures/entity/creeper/creeper.png")

    init {
        addFeature(CreeperChargeFeatureRenderer(this, ctx.entityModels))
    }

    /* --------  texture --------------------------------------------------- */

    override fun getTexture(state: CreeperEntityRenderState?): Identifier = tex

    /* --------  make the creeper translucent ------------------------------ */

    override fun getRenderLayer(
        state: CreeperEntityRenderState,
        showBody: Boolean,
        translucent: Boolean,
        outline: Boolean
    ): RenderLayer = RenderLayer.getEntityTranslucent(tex)

    /* --------  per-vertex colour (ARGB): we only touch the alpha --------- */

    override fun getMixColor(state: CreeperEntityRenderState?): Int {
        // fully-opaque white as a fallback
        if (state == null) return DEFAULT

        val client = net.minecraft.client.MinecraftClient.getInstance()
        val player = client.player ?: return DEFAULT

        val dx = player.x - state.x
        val dy = player.y - state.y
        val dz = player.z - state.z
        val distSq = dx*dx + dy*dy + dz*dz

        /* visibility curve:
           ≤3 blocks  -> α = 1
           ≥5 blocks  -> α = 0
           linear fade in between */
        val alpha = when {
            distSq <= 9.0  -> 1.0f
            distSq >= 25.0 -> 0.0f
            else -> 1.0f - ((distSq - 9.0f) / 16.0f) // (25-9) = 16
        }

        val a = (alpha.toFloat() * 255f).toInt() shl 24
        return a or 0x00_FFFFFF // rgb stays white
    }

    override fun createRenderState(): CreeperEntityRenderState = CreeperEntityRenderState()
}