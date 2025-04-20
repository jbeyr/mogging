package me.jameesyy.mogging

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.TargetPredicate
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.RevengeGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.passive.CowEntity
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
import java.util.EnumSet

class StaticSheepEntity(entityType: EntityType<out SheepEntity>, world: World) : SheepEntity(entityType, world) {

    private var attackCooldown = 0
    private var chargeTime = 0
    private var isCharging = false
    private var targetEntity: LivingEntity? = null
    private var shouldFlee = false
    private var fleeTimer = 0
    private var hasLineOfSight = false
    private var laserHoldTime = 0

    companion object {
        private val CHARGING_STATE: TrackedData<Boolean> = DataTracker.registerData(
            StaticSheepEntity::class.java,
            TrackedDataHandlerRegistry.BOOLEAN
        )

        private const val DETECTION_RANGE = 16.0
        private const val MAINTAIN_DISTANCE_RANGE = 20.0
        private const val OPTIMAL_DISTANCE = 12.0
        private const val LASER_DAMAGE = 8.0f
        private const val SPLASH_DAMAGE = 4.0f
        private const val SPLASH_RADIUS = 3.0
        private const val CHARGE_DURATION = 40 // 2 seconds at 20 ticks per second
        private const val MAX_LASER_HOLD_TIME = 60 // 3 seconds at 20 ticks per second
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

        // Maintain optimal distance from target
        goalSelector.add(1, MaintainDistanceGoal())

        // Look at players and cows
        goalSelector.add(2, LookAtEntityGoal(this, PlayerEntity::class.java, DETECTION_RANGE.toFloat()))
        goalSelector.add(2, LookAtEntityGoal(this, CowEntity::class.java, DETECTION_RANGE.toFloat()))

        // Target only non-creative players (higher priority)
        targetSelector.add(1, SurvivalPlayerRevengeGoal(this))
        targetSelector.add(2, SurvivalPlayerTargetGoal(this, PlayerEntity::class.java, true))

        // Target cows (lower priority)
        targetSelector.add(3, ActiveTargetGoal(this, CowEntity::class.java, true))
    }

    // Custom goal to maintain optimal distance from any target
    private inner class MaintainDistanceGoal : Goal() {
        private var moveAwayPosition: Vec3d? = null

        init {
            setControls(EnumSet.of(Goal.Control.MOVE))
        }

        override fun canStart(): Boolean {
            return targetEntity != null &&
                    targetEntity!!.isAlive &&
                    !isCharging &&
                    !shouldFlee
        }

        override fun shouldContinue(): Boolean {
            return targetEntity != null &&
                    targetEntity!!.isAlive &&
                    !isCharging &&
                    !shouldFlee
        }

        override fun tick() {
            targetEntity?.let { target ->
                val distanceToTarget = sqrt(target.squaredDistanceTo(this@StaticSheepEntity))

                if (distanceToTarget < OPTIMAL_DISTANCE - 2.0) {
                    // Too close, move away
                    val directionAway = pos.subtract(target.pos).normalize()
                    moveAwayPosition = pos.add(directionAway.multiply(5.0))
                    navigation.startMovingTo(moveAwayPosition!!.x, moveAwayPosition!!.y, moveAwayPosition!!.z, 1.0)
                } else if (distanceToTarget > OPTIMAL_DISTANCE + 2.0) {
                    // Too far, move closer but maintain line of sight
                    val directionTowards = target.pos.subtract(pos).normalize()
                    moveAwayPosition = pos.add(directionTowards.multiply(2.0))

                    // Check if moving closer would maintain line of sight
                    val potentialEyePos = moveAwayPosition!!.add(0.0, standingEyeHeight.toDouble(), 0.0)
                    val raycast = performRaycast(potentialEyePos, target.eyePos)

                    if (raycast.type != HitResult.Type.BLOCK) {
                        navigation.startMovingTo(moveAwayPosition!!.x, moveAwayPosition!!.y, moveAwayPosition!!.z, 1.0)
                    } else {
                        // Find a position that would give us line of sight
                        moveTowardsTargetForLineOfSight()
                    }
                } else {
                    // At good distance, just check for line of sight
                    val raycast = performRaycast(eyePos, target.eyePos)
                    if (raycast.type == HitResult.Type.BLOCK) {
                        moveTowardsTargetForLineOfSight()
                    } else {
                        // We have line of sight at optimal distance, stop moving
                        navigation.stop()

                        // If we have a clear shot and cooldown is ready, start charging
                        if (attackCooldown <= 0 && !isCharging) {
                            startCharging()
                        }
                    }
                }

                // Always face the target
                faceTarget(target)
            }
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
                return (target.gameMode == GameMode.SURVIVAL || target.gameMode == GameMode.ADVENTURE) && super.canTrack(target, targetPredicate)
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

            // Update target entity from AI target
            if (targetEntity == null || !targetEntity!!.isAlive || !isValidTarget(targetEntity!!)) {
                // Try to get the current target from AI
                val aiTarget = this.target
                if (aiTarget != null && aiTarget.isAlive && isValidTarget(aiTarget)) {
                    targetEntity = aiTarget
                } else {
                    // If no AI target, look for nearest valid target
                    findNearestTarget()
                }
            }

            // If we have a target and aren't charging, check if we should start charging
            if (targetEntity != null && !isCharging && attackCooldown <= 0 && !shouldFlee) {
                // Check if we have line of sight to the target
                val raycastResult = performRaycast(this.eyePos, targetEntity!!.eyePos)
                if (raycastResult.type != HitResult.Type.BLOCK) {
                    // Only start charging if we have line of sight
                    val distance = targetEntity!!.squaredDistanceTo(this)
                    if (distance <= OPTIMAL_DISTANCE * OPTIMAL_DISTANCE * 1.5) { // Allow some flexibility in distance
                        startCharging()
                    }
                }
            }

            // Handle charging state
            if (isCharging) {
                handleCharging()
            }
        }
    }

    private fun moveTowardsTargetForLineOfSight() {
        targetEntity?.let { target ->
            // Calculate a position that might give us line of sight
            val dirToTarget = target.pos.subtract(pos).normalize()

            // Try a few different positions to find one with line of sight
            for (angle in arrayOf(0.0, 45.0, -45.0, 90.0, -90.0)) {
                val radians = angle * Math.PI / 180.0
                val rotatedDir = Vec3d(
                    dirToTarget.x * Math.cos(radians) - dirToTarget.z * Math.sin(radians),
                    dirToTarget.y,
                    dirToTarget.x * Math.sin(radians) + dirToTarget.z * Math.cos(radians)
                ).normalize()

                val potentialPos = pos.add(rotatedDir.multiply(3.0))

                // Check if moving to this position would improve line of sight
                val raycastFromPotential = performRaycast(
                    potentialPos.add(0.0, standingEyeHeight.toDouble(), 0.0),
                    target.eyePos
                )

                if (raycastFromPotential.type != HitResult.Type.BLOCK) {
                    // Move towards this position
                    navigation.startMovingTo(
                        potentialPos.x,
                        potentialPos.y,
                        potentialPos.z,
                        1.0
                    )
                    return
                }
            }

            // If no good position found, just move a bit closer
            val potentialPos = pos.add(dirToTarget.multiply(2.0))
            navigation.startMovingTo(
                potentialPos.x,
                potentialPos.y,
                potentialPos.z,
                1.0
            )
        }
    }

    private fun faceTarget(target: LivingEntity) {
        // Calculate yaw and pitch to face target
        val targetPos = target.eyePos
        val dx = targetPos.x - this.x
        val dz = targetPos.z - this.z
        val yaw = (atan2(dz, dx) * 180 / Math.PI).toFloat() - 90f

        val dy = targetPos.y - (this.y + this.standingEyeHeight)
        val horizontalDistance = sqrt(dx * dx + dz * dz).toFloat()
        val pitch = -(atan2(dy, horizontalDistance.toDouble()) * 180 / Math.PI).toFloat()

        // Set yaw and pitch gradually to make it look more natural
        this.headYaw = yaw
        this.bodyYaw = yaw
    }

    private fun isValidTarget(entity: LivingEntity): Boolean {
        if (entity is PlayerEntity) {
            val gm = entity.gameMode
            return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE
        }
        // Cows and other entities are always valid targets
        return entity is CowEntity || entity.type == EntityType.COW
    }

    // Override damage to set the attacker as a target
    override fun damage(world: ServerWorld?, source: DamageSource?, amount: Float): Boolean {
        val result = super.damage(world, source, amount)

        // If we took damage and the attacker is a valid target, target it
        if (result && source?.attacker is LivingEntity) {
            val attacker = source.attacker as LivingEntity
            if (isValidTarget(attacker)) {
                targetEntity = attacker
                this.target = attacker

                // Start charging immediately if not already charging
                if (!isCharging && attackCooldown <= 0 && !shouldFlee) {
                    startCharging()
                }
            }
        }

        return result
    }

    private fun findNearestTarget() {
        // First check for players (higher priority)
        val nearbyPlayers = world.getEntitiesByClass(
            PlayerEntity::class.java,
            Box.from(pos).expand(DETECTION_RANGE)
        ) { it.isAlive && !it.isSpectator && isValidTarget(it) }

        val nearestPlayer = nearbyPlayers.minByOrNull { it.squaredDistanceTo(this) }

        if (nearestPlayer != null) {
            targetEntity = nearestPlayer
            this.target = nearestPlayer
            return
        }

        // If no players, check for cows
        val nearbyCows = world.getEntitiesByClass(
            CowEntity::class.java,
            Box.from(pos).expand(DETECTION_RANGE)
        ) { it.isAlive }

        val nearestCow = nearbyCows.minByOrNull { it.squaredDistanceTo(this) }

        if (nearestCow != null) {
            targetEntity = nearestCow
            this.target = nearestCow
        }
    }

    private fun startCharging() {
        isCharging = true
        chargeTime = 0
        hasLineOfSight = false
        laserHoldTime = 0
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

        // Color flashing
        if (chargeTime % 2 == 0) {
            if (color == DyeColor.BLACK) {
                setColor(DyeColor.YELLOW)
            } else {
                setColor(DyeColor.BLACK)
            }
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

        // Once fully charged
        if (chargeTime >= CHARGE_DURATION) {
            targetEntity?.let { target ->
                // Check for line of sight
                val raycastResult = performRaycast(this.eyePos, target.eyePos)
                hasLineOfSight = raycastResult.type != HitResult.Type.BLOCK

                // If we have line of sight, fire the laser
                if (hasLineOfSight) {
                    fireLaser()
                    resetChargingState()

                    // Start fleeing after firing
                    shouldFlee = true
                    fleeTimer = FLEE_DURATION
                } else {
                    // No line of sight, increment hold time
                    laserHoldTime++

                    // Face the target
                    faceTarget(target)

                    // Try to move to get line of sight
                    moveToGetLineOfSight(target)

                    // If we've held the charge too long, reset
                    if (laserHoldTime >= MAX_LASER_HOLD_TIME) {
                        // Failed to get line of sight within time limit
                        world.playSound(
                            null,
                            pos.x, pos.y, pos.z,
                            SoundEvents.BLOCK_BEACON_DEACTIVATE,
                            SoundCategory.HOSTILE,
                            1.0f,
                            1.0f
                        )
                        resetChargingState()
                        attackCooldown = ATTACK_COOLDOWN / 2 // Half cooldown for failed attempts
                    }
                }
            } ?: resetChargingState() // Reset if no target
        }
    }

    private fun moveToGetLineOfSight(target: LivingEntity) {
        // Move more aggressively towards target when charged
        val dirToTarget = target.pos.subtract(pos).normalize()
        val distanceToTarget = pos.distanceTo(target.pos)

        // If we're far away, move closer
        if (distanceToTarget > 5.0) {
            val movePos = pos.add(dirToTarget.multiply(2.0))
            navigation.startMovingTo(movePos.x, movePos.y, movePos.z, 1.2) // Move faster
        } else {
            // If we're close, try to find a position with line of sight
            // Try different angles and elevations
            val possiblePositions = mutableListOf<Vec3d>()

            // Try positions at different angles
            for (angle in arrayOf(0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0)) {
                val radians = angle * Math.PI / 180.0
                val rotatedDir = Vec3d(
                    dirToTarget.x * Math.cos(radians) - dirToTarget.z * Math.sin(radians),
                    dirToTarget.y,
                    dirToTarget.x * Math.sin(radians) + dirToTarget.z * Math.cos(radians)
                ).normalize()

                // Try different distances
                for (dist in 2..4) {
                    val potentialPos = pos.add(rotatedDir.multiply(dist.toDouble()))

                    // Check if this position gives line of sight
                    val eyeHeight = standingEyeHeight.toDouble()
                    val raycast = performRaycast(
                        potentialPos.add(0.0, eyeHeight, 0.0),
                        target.eyePos
                    )

                    if (raycast.type != HitResult.Type.BLOCK) {
                        possiblePositions.add(potentialPos)
                    }
                }
            }

            // If we found positions with line of sight, move to the closest one
            if (possiblePositions.isNotEmpty()) {
                val closestPos = possiblePositions.minByOrNull { it.distanceTo(pos) }
                closestPos?.let {
                    navigation.startMovingTo(it.x, it.y, it.z, 1.2)
                }
            } else {
                // If no good position found, just move towards the target
                val movePos = pos.add(dirToTarget.multiply(1.5))
                navigation.startMovingTo(movePos.x, movePos.y, movePos.z, 1.2)
            }
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
        laserHoldTime = 0
        hasLineOfSight = false
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