package me.jameesyy.mogging

import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.world.World


class CamoCreeperEntity(entityType: EntityType<out CreeperEntity>, world: World) : CreeperEntity(entityType, world) {

    companion object {
        fun createAttributes(): DefaultAttributeContainer.Builder =
            HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
    }
}