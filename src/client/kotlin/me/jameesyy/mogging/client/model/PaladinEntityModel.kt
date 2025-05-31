package me.jameesyy.mogging.client.model

import net.minecraft.client.model.*
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.state.BipedEntityRenderState

class PaladinEntityModel(
    root: ModelPart
) : BipedEntityModel<BipedEntityRenderState>(root) {

    override fun setAngles(state: BipedEntityRenderState) {
        super.setAngles(state)
        // The parent class handles all the animation based on arm poses
    }

    companion object {
        fun getTexturedModelData(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation.NONE, 0.0f)
            return TexturedModelData.of(modelData, 64, 32)
        }
    }
}