package me.jameesyy.mogging

import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.passive.TropicalFishEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PiranhaEntity(entityType: EntityType<out PiranhaEntity>, world: World) : TropicalFishEntity(entityType, world) {

    // Track how long since last leap attempt
    private var leapCooldown = 0

    init {
        setPathfindingPenalty(PathNodeType.WATER, 0.0f)
        setPathfindingPenalty(PathNodeType.WALKABLE, 0.0f)
    }

    override fun initGoals() {
        // Higher priority for attack goals
        goalSelector.add(0, MeleeAttackGoal(this, 1.0, true))
        goalSelector.add(1, SwimAroundGoal(this, 1.0, 10))
        goalSelector.add(2, PounceAtTargetGoal(this, 0.8f))

        // Target players aggressively
        targetSelector.add(0, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(1, RevengeGoal(this))
    }

    override fun tick() {
        super.tick()

        // Handle leap cooldown
        if (leapCooldown > 0) {
            leapCooldown--
        }

        // Check if we have a target
        val t = target
        if (t != null && t.isAlive) {
            if (isOnGround && random.nextFloat() < 0.7f) {
                flopTowardTarget(t)
            } else if (touchingWater && !t.isTouchingWater && leapCooldown <= 0 && canSee(target)) {
                tryLeapAtTarget(t)
            }
        }
    }

    // Make the piranha flop toward its target when on land
    private fun flopTowardTarget(target: net.minecraft.entity.Entity) {
        val dx = target.x - x
        val dz = target.z - z
        val angle = atan2(dz, dx)

        // More aggressive flopping - higher chance and stronger impulse
        velocity = velocity.add(
            cos(angle) * 0.4,
            0.2,
            sin(angle) * 0.4
        )
    }

    // Make the piranha leap out of water to chase players
    private fun tryLeapAtTarget(target: net.minecraft.entity.Entity) {
            // Calculate leap vector
            val dx = target.x - x
            val dy = target.y - y
            val dz = target.z - z
            val horizDistSq = dx * dx + dz * dz

            // Only leap if we're close enough horizontally
            if (horizDistSq < 64.0) {
                // Calculate leap trajectory
                // Higher vertical component to clear the water surface
                val leapStrength = 0.5 + random.nextFloat()*0.5
                val leapY = 0.4 + if (dy > 0) dy * 0.1 else 0.0

                // Apply leap velocity
                velocity = Vec3d(
                    dx*dx/horizDistSq * leapStrength,
                    leapY,
                    dz*dz/horizDistSq * leapStrength
                )

                leapCooldown = random.nextInt(25)
            }
    }

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean {
        return distanceSquared > 4096.0
    }

    companion object {
        fun createAttributes(): DefaultAttributeContainer.Builder {
            return createFishAttributes()
                .add(EntityAttributes.MAX_HEALTH, 12.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 3.0)
                .add(EntityAttributes.FOLLOW_RANGE, 24.0)
                .add(EntityAttributes.JUMP_STRENGTH, 0.5)
                .add(EntityAttributes.ARMOR, 4.0)
        }
    }
}