package me.jameesyy.mogging.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.entity.CreeperEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.feature.CreeperChargeFeatureRenderer
import net.minecraft.client.render.entity.state.CreeperEntityRenderState
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.math.exp
import kotlin.math.roundToInt

private const val DEFAULT_COLOR = -0x1 // opaque white

class CamoCreeperRenderer(ctx: EntityRendererFactory.Context) : CreeperEntityRenderer(ctx) {

    private val tex = Identifier.of("minecraft", "textures/entity/creeper/creeper.png")

    init {
        addFeature(CreeperChargeFeatureRenderer(this, ctx.entityModels))
    }

    /* --------  texture --------------------------------------------------- */

    override fun getTexture(state: CreeperEntityRenderState?): Identifier = tex

    override fun getRenderLayer(
        state: CreeperEntityRenderState,
        showBody: Boolean,
        translucent: Boolean,
        outline: Boolean
    ): RenderLayer = RenderLayer.getEntityTranslucent(tex)

    /* --------  per-vertex colour (ARGB): we only touch the alpha --------- */

    override fun getMixColor(state: CreeperEntityRenderState?): Int {
        // fully-opaque white as a fallback
        if (state == null) return DEFAULT_COLOR

        val client = net.minecraft.client.MinecraftClient.getInstance()
        val player = client.player ?: return DEFAULT_COLOR

        val dx = player.x - state.x
        val dy = player.y - state.y
        val dz = player.z - state.z
        val distSq = dx * dx + dy * dy + dz * dz

        val appearAtSq = 3.5 * 3.5
        val steepness = 2.0
        val alphaF = (1.0 / (1.0 + exp((distSq - appearAtSq) * steepness))).toFloat()
        val alphaI = (alphaF.coerceIn(0f, 1f) * 255f).toInt() shl 24

        val world = player.world
        val lightF = world.getLightLevel(BlockPos.ofFloored(state.x, state.y, state.z)) / 15f
        val startTint = if (lightF < 0.5f) 0.15f else 1.6f
        val tint = (startTint + alphaF * (1.0f - startTint)).coerceIn(0f, 1f)
        val rgb = (tint * 255f).roundToInt().coerceIn(0, 255)
        val colour = (rgb shl 16) or (rgb shl 8) or rgb

        return alphaI or colour
    }

    override fun createRenderState(): CreeperEntityRenderState = CreeperEntityRenderState()
}