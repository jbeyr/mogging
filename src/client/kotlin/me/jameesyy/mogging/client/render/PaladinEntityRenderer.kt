package me.jameesyy.mogging.client.render

import me.jameesyy.mogging.PaladinEntity
import me.jameesyy.mogging.client.model.PaladinEntityModel
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.state.BipedEntityRenderState
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Items
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.Identifier

class PaladinEntityRenderer(
    context: EntityRendererFactory.Context
) : BipedEntityRenderer<PaladinEntity, BipedEntityRenderState, PaladinEntityModel>(
    context,
    PaladinEntityModel(context.getPart(EntityModelLayers.SKELETON)),
    0.5f // shadow radius
) {

    init {
        // Add held item rendering for both hands
        addFeature(HeldItemFeatureRenderer(this))

        // Add armor rendering
        val innerModel = PaladinEntityModel(context.getPart(EntityModelLayers.SKELETON_INNER_ARMOR))
        val outerModel = PaladinEntityModel(context.getPart(EntityModelLayers.SKELETON_OUTER_ARMOR))
        addFeature(ArmorFeatureRenderer(
            this,
            innerModel,
            outerModel,
            context.equipmentRenderer
        ))
    }

    override fun createRenderState(): BipedEntityRenderState {
        return BipedEntityRenderState()
    }

    override fun updateRenderState(
        entity: PaladinEntity,
        state: BipedEntityRenderState,
        tickDelta: Float
    ) {
        super.updateRenderState(entity, state, tickDelta)

        // Get the items from the entity
        val mainHandStack = entity.getStackInHand(Hand.MAIN_HAND)
        val offHandStack = entity.getStackInHand(Hand.OFF_HAND)

        // Update arm poses based on item usage
        state.rightArmPose = when {
            entity.isUsingItem() && entity.getActiveHand() == Hand.MAIN_HAND -> {
                // Crossbow charging pose
                BipedEntityModel.ArmPose.CROSSBOW_CHARGE
            }
            CrossbowItem.isCharged(mainHandStack) -> {
                // Crossbow ready to fire pose
                BipedEntityModel.ArmPose.CROSSBOW_HOLD
            }
            !mainHandStack.isEmpty -> {
                BipedEntityModel.ArmPose.ITEM
            }
            else -> BipedEntityModel.ArmPose.EMPTY
        }

        state.leftArmPose = when {
            entity.isBlocking() -> {
                // Shield blocking pose
                BipedEntityModel.ArmPose.BLOCK
            }
            offHandStack.item == Items.SHIELD -> {
                // Just holding shield
                BipedEntityModel.ArmPose.ITEM
            }
            else -> BipedEntityModel.ArmPose.EMPTY
        }
    }

    override fun getTexture(state: BipedEntityRenderState): Identifier {
        return Identifier.of("textures/entity/skeleton/skeleton.png")
        // For a custom texture:
        // return Identifier("mogging", "textures/entity/paladin.png")
    }

    override fun getArmPose(entity: PaladinEntity, arm: Arm): BipedEntityModel.ArmPose {
        val stack = entity.getStackInHand(if (arm == Arm.RIGHT) Hand.MAIN_HAND else Hand.OFF_HAND)

        return when {
            arm == Arm.RIGHT && entity.isUsingItem() && entity.getActiveHand() == Hand.MAIN_HAND -> {
                BipedEntityModel.ArmPose.CROSSBOW_CHARGE
            }
            arm == Arm.RIGHT && CrossbowItem.isCharged(stack) -> {
                BipedEntityModel.ArmPose.CROSSBOW_HOLD
            }
            arm == Arm.LEFT && entity.isBlocking() -> {
                BipedEntityModel.ArmPose.BLOCK
            }
            !stack.isEmpty -> {
                BipedEntityModel.ArmPose.ITEM
            }
            else -> BipedEntityModel.ArmPose.EMPTY
        }
    }
}