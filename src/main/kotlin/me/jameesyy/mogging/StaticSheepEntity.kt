package me.jameesyy.mogging

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.TargetPredicate
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.EscapeDangerGoal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.RevengeGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.DyeColor
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import kotlin.math.atan2
import kotlin.math.sqrt

class StaticSheepEntity(entityType: EntityType<out SheepEntity>, world: World) : SheepEntity(entityType, world) {

    private var attackCooldown = 0
    private var chargeTime = 0
    private var isCharging = false
    private var targetEntity: LivingEntity? = null
    private var shouldFlee = false
    private var fleeTimer = 0

    companion object {
        private val CHARGING_STATE: TrackedData<Boolean> = DataTracker.registerData(
            StaticSheepEntity::class.java,
            TrackedDataHandlerRegistry.BOOLEAN
        )

        private const val DETECTION_RANGE = 16.0
        private const val LASER_DAMAGE = 8.0f
        private const val SPLASH_DAMAGE = 4.0f
        private const val SPLASH_RADIUS = 3.0
        private const val CHARGE_DURATION = 40 // 2 seconds at 20 ticks per second
        private const val ATTACK_COOLDOWN = 100 // 5 seconds cooldown
        private const val FLEE_DURATION = 60 // 3 seconds of fleeing

        fun createAttributes(): DefaultAttributeContainer.Builder {
            return createSheepAttributes()
                .add(EntityAttributes.MAX_HEALTH, 12.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.ARMOR, 2.0)
        }
    }

    override fun initDataTracker(builder: DataTracker.Builder?) {
        super.initDataTracker(builder)
        builder?.add(CHARGING_STATE, false)
    }

    override fun initGoals() {
        super.initGoals()

        // Add a goal to flee from the target after attacking
        goalSelector.add(1, FleeFromTargetGoal())

        // Add revenge goal to target entities that hit it (but only non-creative players)
        targetSelector.add(1, SurvivalPlayerRevengeGoal(this))

        // Look at players
        goalSelector.add(2, LookAtEntityGoal(this, PlayerEntity::class.java, DETECTION_RANGE.toFloat()))

        // Target only non-creative players
        targetSelector.add(2, SurvivalPlayerTargetGoal(this, PlayerEntity::class.java, true))
    }

    // Custom goal to flee from the target
    private inner class FleeFromTargetGoal : EscapeDangerGoal(this, 1.25) {
        override fun canStart(): Boolean {
            return shouldFlee && targetEntity != null
        }
    }

    // Custom revenge goal that only targets non-creative players
    private class SurvivalPlayerRevengeGoal(entity: StaticSheepEntity) : RevengeGoal(entity) {
        override fun canTrack(target: LivingEntity?, targetPredicate: TargetPredicate?): Boolean {

            if (target is PlayerEntity) {
                // Only target players in survival/adventure
                return (target.gameMode == GameMode.SURVIVAL || target.gameMode == GameMode.ADVENTURE) && super.canTrack(target, targetPredicate)
            }
            return super.canTrack(target, targetPredicate)
        }
    }

    // Custom target goal that only targets non-creative players
    private class SurvivalPlayerTargetGoal<T : LivingEntity>(
        entity: StaticSheepEntity,
        targetClass: Class<T>,
        checkVisibility: Boolean
    ) : ActiveTargetGoal<T>(entity, targetClass, checkVisibility) {
        override fun canTrack(target: LivingEntity?, targetPredicate: TargetPredicate?): Boolean {
            if (target is PlayerEntity) {
                return target.gameMode == GameMode.SURVIVAL || target.gameMode == GameMode.ADVENTURE && super.canTrack(target, targetPredicate)
            }
            return super.canTrack(target, targetPredicate)
        }
    }

    override fun tick() {
        super.tick()

        if (!world.isClient) {
            // Handle attack cooldown
            if (attackCooldown > 0) {
                attackCooldown--
            }

            // Handle flee timer
            if (shouldFlee) {
                fleeTimer--
                if (fleeTimer <= 0) {
                    shouldFlee = false
                }
            }

            // Find closest player if we don't have a target
            if (targetEntity == null || !targetEntity!!.isAlive || !isValidTarget(targetEntity!!)) {
                // Try to get the revenge target first
                val attackTarget = this.target
                if (attackTarget != null && attackTarget.isAlive && isValidTarget(attackTarget)) {
                    targetEntity = attackTarget
                } else {
                    findNearestPlayer()
                }
            }

            // If we have a target and aren't charging, start charging
            if (targetEntity != null && !isCharging && attackCooldown <= 0 && !shouldFlee) {
                startCharging()
            }

            // Handle charging state
            if (isCharging) {
                handleCharging()
            }
        }
    }

    private fun isValidTarget(entity: LivingEntity): Boolean {
        if (entity is PlayerEntity) {
            val gm = entity.gameMode
            return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE
        }
        return true
    }

    // Override damage to set the attacker as a target
    override fun damage(world: ServerWorld?, source: DamageSource?, amount: Float): Boolean {
        val result = super.damage(world, source, amount)

        // If we took damage and the attacker is a valid target, target it
        if (result && source?.attacker is LivingEntity) {
            val attacker = source.attacker as LivingEntity
            if (isValidTarget(attacker)) {
                targetEntity = attacker

                // Start charging immediately if not already charging
                if (!isCharging && attackCooldown <= 0 && !shouldFlee) {
                    startCharging()
                }
            }
        }

        return result
    }

    private fun findNearestPlayer() {
        val nearbyPlayers = world.getEntitiesByClass(
            PlayerEntity::class.java,
            Box.from(pos).expand(DETECTION_RANGE)
        ) { it.isAlive && !it.isSpectator && isValidTarget(it) }

        targetEntity = nearbyPlayers.minByOrNull { it.squaredDistanceTo(this) }
    }

    private fun startCharging() {
        isCharging = true
        chargeTime = 0
        setColor(DyeColor.BLACK)  // Use public method
        dataTracker.set(CHARGING_STATE, true)

        // Play charge start sound
        world.playSound(
            null,
            pos.x, pos.y, pos.z,
            SoundEvents.BLOCK_BEACON_ACTIVATE,
            SoundCategory.HOSTILE,
            1.0f,
            1.0f
        )
    }

    private fun handleCharging() {
        chargeTime++

        // Flash wool color between black and yellow while charging
        if (chargeTime % 2 == 0) {
            if (color == DyeColor.BLACK) {
                setColor(DyeColor.YELLOW)
            } else {
                setColor(DyeColor.BLACK)
            }
        }

        // Face the target when about to fire
        if (chargeTime >= CHARGE_DURATION - 10 && targetEntity != null) {
            // Calculate yaw and pitch to face target
            val targetPos = targetEntity!!.eyePos
            val dx = targetPos.x - this.x
            val dz = targetPos.z - this.z
            val yaw = (atan2(dz, dx) * 180 / Math.PI).toFloat() - 90f

            val dy = targetPos.y - (this.y + this.standingEyeHeight)
            val horizontalDistance = sqrt(dx * dx + dz * dz).toFloat()
            val pitch = -(atan2(dy, horizontalDistance.toDouble()) * 180 / Math.PI).toFloat()

            // Set yaw and pitch directly
            this.yaw = yaw
            this.pitch = pitch
            this.headYaw = yaw
            this.bodyYaw = yaw
        }

        // Spawn particles while charging
        if (chargeTime > 10 && world is ServerWorld) {
            val particleCount = chargeTime / 8
            for (i in 0 until particleCount) {
                (world as ServerWorld).spawnParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    pos.x + random.nextGaussian() * 0.3,
                    pos.y + height / 2 + random.nextGaussian() * 0.3,
                    pos.z + random.nextGaussian() * 0.3,
                    1, // count
                    0.0, 0.0, 0.0, // velocity
                    0.0 // speed
                )
            }
        }

        // Fire laser when charge is complete
        if (chargeTime >= CHARGE_DURATION) {
            fireLaser()
            resetChargingState()

            // Start fleeing after firing
            shouldFlee = true
            fleeTimer = FLEE_DURATION
        }
    }

    private fun fireLaser() {
        targetEntity?.let { target ->
            // Calculate direction to target
            val direction = target.eyePos.subtract(this.eyePos).normalize()

            // Check if there's a clear line of sight to the target (laser can't go through blocks)
            val raycastResult = performRaycast(this.eyePos, target.eyePos)

            // Get the hit position (either the target or a block in the way)
            val hitPos = if (raycastResult.type == HitResult.Type.BLOCK) {
                (raycastResult as BlockHitResult).pos
            } else {
                target.eyePos
            }

            // Spawn laser particles to the hit position
            spawnLaserParticles(this.eyePos, hitPos)

            // Play laser sound
            world.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GUARDIAN_ATTACK,
                SoundCategory.HOSTILE,
                1.0f,
                1.0f
            )

            // Damage target and apply splash damage
            if (world is ServerWorld) {
                val serverWorld = world as ServerWorld
                val damageSource = world.damageSources.indirectMagic(this, this)

                // If we have a direct line to the target, damage it directly
                if (raycastResult.type != HitResult.Type.BLOCK) {
                    target.damage(serverWorld, damageSource, LASER_DAMAGE)
                }

                // Apply splash damage at the impact point
                applySplashDamage(hitPos, damageSource)
            }

            // Set cooldown
            attackCooldown = ATTACK_COOLDOWN
        }
    }

    private fun performRaycast(start: Vec3d, end: Vec3d): HitResult {
        // Create a raycast that can hit blocks
        val context = RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            this
        )

        // Perform the raycast and return the result
        return world.raycast(context)
    }

    private fun applySplashDamage(center: Vec3d, damageSource: DamageSource) {
        if (world !is ServerWorld) return

        val serverWorld = world as ServerWorld

        // Get entities in splash radius
        val affectedEntities = world.getEntitiesByClass(
            LivingEntity::class.java,
            Box.from(center).expand(SPLASH_RADIUS)
        ) { it != this && it.isAlive }

        // Apply splash damage based on distance from center
        for (entity in affectedEntities) {
            val distance = entity.pos.distanceTo(center)
            if (distance <= SPLASH_RADIUS) {
                // Calculate damage falloff based on distance
                val damageMultiplier = 1.0 - (distance / SPLASH_RADIUS)
                val damage = (SPLASH_DAMAGE * damageMultiplier).toFloat()

                // Apply damage if significant
                if (damage > 0.5f) {
                    entity.damage(serverWorld, damageSource, damage)
                }

                // Spark effect on hit entities
                serverWorld.spawnParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    entity.x, entity.y + entity.height / 2, entity.z,
                    5, 0.2, 0.2, 0.2, 0.1
                )
            }
        }

        // Explosion effect at impact location
        serverWorld.spawnParticles(
            ParticleTypes.EXPLOSION,
            center.x, center.y, center.z,
            1, 0.0, 0.0, 0.0, 0.0
        )
    }

    private fun spawnLaserParticles(start: Vec3d, end: Vec3d) {
        if (world !is ServerWorld) return

        val serverWorld = world as ServerWorld
        val distance = start.distanceTo(end)
        val steps = (distance * 2).toInt().coerceAtLeast(5)

        val direction = end.subtract(start).normalize()

        for (i in 0 until steps) {
            val progress = i.toDouble() / steps
            val particlePos = start.add(
                direction.multiply(distance * progress)
            )

            serverWorld.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                particlePos.x, particlePos.y, particlePos.z,
                1, // count
                0.0, 0.0, 0.0, // offset
                0.0 // speed
            )
        }
    }

    private fun resetChargingState() {
        isCharging = false
        chargeTime = 0
        dataTracker.set(CHARGING_STATE, false)
        setColor(DyeColor.WHITE)  // Use public method
    }

    // Getter for client-side rendering
    fun isCharging(): Boolean {
        return dataTracker.get(CHARGING_STATE)
    }

    // Override to prevent normal sheep shearing behavior
    override fun isShearable(): Boolean {
        return false
    }
}