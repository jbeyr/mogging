package me.jameesyy.mogging

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.World

class HackermanZombieEntity(entityType: EntityType<out ZombieEntity>, world: World) : ZombieEntity(entityType, world) {

    companion object {
        // Custom reach distance for HackermanZombie
        const val ATTACK_REACH = 5.0f

        fun createAttributes(): DefaultAttributeContainer.Builder {
            return ZombieEntity.createZombieAttributes()
        }
    }

    override fun initEquipment(random: Random?, localDifficulty: LocalDifficulty?) {
        super.initEquipment(random, localDifficulty)
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.WOODEN_HOE))
    }

    // Override to add our custom attack goal with extended reach
    override fun initCustomGoals() {
        super.initCustomGoals()

        // Remove the default melee attack goal
        goalSelector.goals.removeIf { goal ->
            goal.goal is MeleeAttackGoal
        }

        // Add our custom melee attack goal with extended reach
        goalSelector.add(2, LongRangeAttackGoal(this, 1.0, false))
    }

    // Custom attack goal that extends reach
    // Custom attack goal that extends reach
    private class LongRangeAttackGoal(
        private val zombie: HackermanZombieEntity,
        speed: Double,
        pauseWhenMobIdle: Boolean
    ) : MeleeAttackGoal(zombie, speed, pauseWhenMobIdle) {

        // Modified to use vanilla logic for continuation, which handles pursuit properly
        override fun shouldContinue(): Boolean {
            val target = this.mob.target ?: return false
            if (!target.isAlive) {
                return false
            }

            return this.mob.canTarget(target) && this.mob.isOnGround
        }


        override fun canAttack(target: LivingEntity?): Boolean {
            return this.mob.squaredDistanceTo(target) <= (ATTACK_REACH * ATTACK_REACH)
        }

        override fun attack(target: LivingEntity?) {
            if (target == null) return
            if (canAttack(target)) {
                this.mob.swingHand(this.mob.activeHand)
                zombie.tryAttack(this.mob.world as ServerWorld, target)
            }
        }
    }

    // Implement the server-specific tryAttack method
    override fun tryAttack(world: ServerWorld, target: Entity): Boolean {
        if (this.squaredDistanceTo(target) <= ATTACK_REACH* ATTACK_REACH) {
            val damage = this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE).toFloat()

            if (target.damage(world, this.damageSources.mobAttack(this), damage)) {
                if (target is LivingEntity) {
                    val knockbackStrength = this.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE).toFloat()

                    if (knockbackStrength > 0.0f) {
                        val knockbackMultiplier = 0.5f * knockbackStrength
                        val dx = this.x - target.x
                        val dz = this.z - target.z

                        // Normalize the vector and apply knockback
                        val length = Math.sqrt(dx * dx + dz * dz)
                        if (length > 0.0) {
                            target.addVelocity(
                                dx / length * knockbackMultiplier,
                                0.1,
                                dz / length * knockbackMultiplier
                            )
                        }
                        // Update target velocity
                        target.velocityModified = true
                    }
                }
                return true
            }
        }
        return false
    }
}