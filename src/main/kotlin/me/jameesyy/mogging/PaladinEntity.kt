package me.jameesyy.mogging

import net.minecraft.entity.*
import net.minecraft.entity.ai.control.MoveControl
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.passive.SnowGolemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import java.util.*
import kotlin.math.*

class PaladinEntity(
    type: EntityType<out PaladinEntity>,
    world: World
) : SkeletonEntity(type, world), CrossbowUser {

    private var shielding = false

    // Aim prediction setting
    private var useAimPrediction = true

    private val MELEE_RANGE_SQ = 4.0 * 4.0
    private val COMFORT_RANGE_SQ = 10.0 * 10.0
    private val MAX_SHOOT_RANGE_SQ = 50.0 * 50.0
    private var lastShotTime = 0L
    private val SHOT_COOLDOWN = 60L // 3 second between shots

    companion object {
        fun createAttributes(): DefaultAttributeContainer.Builder =
            HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 34.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 50.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.35)
    }

    init {
        // Replace the default move control with our custom one
        moveControl = PaladinMoveControl(this)
    }

    override fun initGoals() {
        goalSelector.add(1, SwimGoal(this))
        goalSelector.add(2, PaladinCombatGoal(this))
        goalSelector.add(3, WanderAroundFarGoal(this, 0.8))
        goalSelector.add(4, LookAtEntityGoal(this, PlayerEntity::class.java, 8f))
        goalSelector.add(5, LookAroundGoal(this))

        targetSelector.add(1, RevengeGoal(this))
        targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(2, ActiveTargetGoal(this, IronGolemEntity::class.java, true))
        targetSelector.add(2, ActiveTargetGoal(this, SnowGolemEntity::class.java, true))
    }

    override fun isBlocking(): Boolean {
        return shielding && !getStackInHand(Hand.OFF_HAND).isEmpty
    }

    override fun initialize(
        world: ServerWorldAccess?,
        difficulty: LocalDifficulty?,
        spawnReason: SpawnReason?,
        entityData: EntityData?
    ): EntityData? {
        val data = super.initialize(world, difficulty, spawnReason, entityData)
        equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.CROSSBOW))
        equipStack(EquipmentSlot.OFFHAND, ItemStack(Items.SHIELD))
        return data
    }

    private fun shouldShield(): Boolean {
        // Check for melee threat
        val target = target
        if (target != null && squaredDistanceTo(target) < MELEE_RANGE_SQ) {
            return true
        }

        // Check for incoming projectiles
        val incomingProjectiles = world.getEntitiesByClass(
            PersistentProjectileEntity::class.java,
            boundingBox.expand(8.0)
        ) { arrow ->
            if (arrow.velocity.lengthSquared() < 0.01) return@getEntitiesByClass false

            val toThis = eyePos.subtract(arrow.pos).normalize()
            val arrowDir = arrow.velocity.normalize()
            arrowDir.dotProduct(toThis) > 0.7
        }

        return incomingProjectiles.isNotEmpty()
    }

    private fun isTargetBlocking(target: LivingEntity): Boolean {
        return target.isBlocking
    }

    private fun predictTargetPosition(target: LivingEntity, projectileSpeed: Float): Vec3d {
        if (!useAimPrediction) {
            return Vec3d(target.x, target.eyeY, target.z)
        }

        val mvmt = target.movement

        // For players, also consider their intended movement direction
        val effectiveMovement = if (target is PlayerEntity) {
            // Get the player's movement input and transform it to world coordinates
            val movementInput = getPlayerMovementInput(target)

            // If we have movement input, use it; otherwise fall back to actual movement
            if (movementInput.horizontalLengthSquared() > 0.001) {
                movementInput
            } else {
                mvmt
            }
        } else {
            mvmt
        }

        val dx = target.x - x
        val dy = target.eyeY - eyeY  // Use eye height for better accuracy
        val dz = target.z - z
        val distance = sqrt(dx*dx + dy*dy + dz*dz)

        // Estimate flight time based on total distance
        val estimatedFlightTime = distance / projectileSpeed
        val predictionMultiplier = 6.0

        // Scale prediction based on target speed - faster targets get more prediction
        val targetSpeed = sqrt(effectiveMovement.x*effectiveMovement.x + effectiveMovement.z*effectiveMovement.z) // Only horizontal speed
        val speedBasedMultiplier = predictionMultiplier * targetSpeed

        // Predict movement - reduce Y prediction since vertical movement is less predictable
        val predictedX = target.x + effectiveMovement.x * estimatedFlightTime * speedBasedMultiplier
        val predictedY = target.eyeY + (effectiveMovement.y * estimatedFlightTime * speedBasedMultiplier * 0.3) // Reduced Y prediction
        val predictedZ = target.z + effectiveMovement.z * estimatedFlightTime * speedBasedMultiplier

        // One refinement iteration
        val newDx = predictedX - x
        val newDy = predictedY - eyeY
        val newDz = predictedZ - z
        val newDistance = sqrt(newDx*newDx + newDy*newDy + newDz*newDz)
        val refinedFlightTime = newDistance / projectileSpeed

        val finalX = target.x + effectiveMovement.x * refinedFlightTime * speedBasedMultiplier
        val finalY = target.eyeY + (effectiveMovement.y * refinedFlightTime * speedBasedMultiplier * 0.3)
        val finalZ = target.z + effectiveMovement.z * refinedFlightTime * speedBasedMultiplier

        return Vec3d(finalX, finalY, finalZ)
    }

    // Helper function to get player movement input
    private fun getPlayerMovementInput(player: PlayerEntity): Vec3d {
        // Try to get the player's intended movement direction
        // This is more complex and might require accessing player input directly

        // For now, we can estimate based on the player's facing direction and movement
        val yaw = Math.toRadians(player.yaw.toDouble())
        val movement = player.movement

        // If the player is moving, try to determine their intended direction
        if (movement.horizontalLengthSquared() > 0.001) {
            // Calculate the angle of movement relative to facing direction
            val movementAngle = atan2(movement.z, movement.x)
            val facingAngle = yaw + Math.PI/2 // Adjust for Minecraft's coordinate system

            // If movement is roughly in the facing direction, use enhanced prediction
            val angleDiff = abs(MathHelper.wrapDegrees((movementAngle - facingAngle) * 180.0 / Math.PI))

            if (angleDiff < 45.0) { // Moving forward
                val speed = if (player.isSprinting) 1.3 else 1.0
                return Vec3d(
                    -sin(yaw) * speed * 0.1,
                    movement.y,
                    cos(yaw) * speed * 0.1
                )
            } else if (angleDiff > 135.0) { // Moving backward
                val speed = 0.8 // Backward movement is slower
                return Vec3d(
                    sin(yaw) * speed * 0.1,
                    movement.y,
                    -cos(yaw) * speed * 0.1
                )
            }
        }

        // Fall back to actual movement
        return movement
    }

    fun shootCrossbow(target: LivingEntity) {
        val crossbowStack = getStackInHand(Hand.MAIN_HAND)
        if (!CrossbowItem.isCharged(crossbowStack)) return

        // Prevent machine gun firing
        if (world.time - lastShotTime < SHOT_COOLDOWN) return

        val speed = 3.15f
        val divergence = 0.0f

        // Get predicted target position
        val targetPos = predictTargetPosition(target, speed)

        // Calculate direction to predicted position FROM CURRENT POSITION
        // Don't rotate the entity, just calculate the shot direction
        val dx = targetPos.x - x
        val dy = targetPos.y - eyeY
        val dz = targetPos.z - z
        val horizontalDistance = sqrt(dx * dx + dz * dz)

        // Simple gravity compensation
        val gravity = 0.05f
        val timeToTarget = horizontalDistance / speed
        val gravityDrop = 0.5 * gravity * timeToTarget * timeToTarget

        // Calculate launch angle
        val launchAngle = atan2(dy + gravityDrop, horizontalDistance)

        // Calculate velocity components
        val horizontalVelocity = speed * cos(launchAngle)
        val verticalVelocity = speed * sin(launchAngle)

        // Normalize horizontal direction
        val horizontalNorm = 1.0 / horizontalDistance
        val vx = dx * horizontalNorm * horizontalVelocity
        val vy = verticalVelocity
        val vz = dz * horizontalNorm * horizontalVelocity

        // Create and shoot projectile
        val projectile = createArrowProjectile(
            ItemStack(Items.ARROW),
            1f,
            crossbowStack,
        )
        projectile.isCritical = true
        projectile.setVelocity(vx, vy, vz, speed, divergence)

        // Spawn and play sound
        world.spawnEntity(projectile)
        world.playSound(
            null, x, y, z,
            SoundEvents.ITEM_CROSSBOW_SHOOT, soundCategory,
            1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f)
        )

        // Clear the crossbow properly
        crossbowStack.set(
            net.minecraft.component.DataComponentTypes.CHARGED_PROJECTILES,
            net.minecraft.component.type.ChargedProjectilesComponent.DEFAULT
        )

        // Update last shot time
        lastShotTime = world.time

        // Force the paladin to recognize the crossbow is now empty
        stopUsingItem()
    }

    // CrossbowUser implementation
    private var charging = false
    override fun setCharging(charging: Boolean) {
        this.charging = charging
    }

    override fun shoot(target: LivingEntity, pullProgress: Float) {
        shootCrossbow(target)
    }

    override fun postShoot() {}

    // Custom move control that maintains facing while moving
    class PaladinMoveControl(private val paladin: PaladinEntity) : MoveControl(paladin) {
        override fun tick() {
            val crossbow = paladin.getStackInHand(Hand.MAIN_HAND)
            val isLoaded = CrossbowItem.isCharged(crossbow)
            val target = paladin.target

            // Face the target's CURRENT position for visual purposes, not the predicted position
            target?.let {
                val dx = it.x - paladin.x
                val dz = it.z - paladin.z
                val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()

                // Smoothly rotate to face target's current position
                val yawDiff = MathHelper.wrapDegrees(targetYaw - paladin.yaw)
                val rotationSpeed = 10f
                paladin.yaw += MathHelper.clamp(yawDiff, -rotationSpeed, rotationSpeed)
                paladin.bodyYaw = paladin.yaw
                paladin.headYaw = paladin.yaw

                // Look at current position, not predicted
                paladin.lookControl.lookAt(it.x, it.eyeY, it.z, 30f, 30f)
            }

            // Rest of the combat logic remains the same...
            // [Keep all the existing shield, movement, and shooting logic]
        }

        private fun rotateTowards(from: Float, to: Float, maxDelta: Float): Float {
            val delta = MathHelper.wrapDegrees(to - from)
            return from + MathHelper.clamp(delta, -maxDelta, maxDelta)
        }
    }

    override fun damage(world: ServerWorld?, source: DamageSource?, amount: Float): Boolean {
        // Check if we're blocking and if the damage can be blocked
        if (isBlocking && source?.let { canBlockDamageSourceWithShield(it) } == true) {
            // Get the attacker's position
            val attacker = source.attacker
            if (attacker != null) {
                // Check if the attack is coming from the front
                val dx = attacker.x - x
                val dz = attacker.z - z
                val attackAngle = Math.toDegrees(atan2(-dx, dz)).toFloat()
                val facingAngle = bodyYaw

                // Calculate the difference in angles
                val angleDiff = MathHelper.wrapDegrees(attackAngle - facingAngle)

                // Shield blocks attacks from roughly the front 180 degrees
                if (abs(angleDiff) <= 90) {
                    // Play shield block sound
                    world?.playSound(
                        null, x, y, z,
                        SoundEvents.ITEM_SHIELD_BLOCK, soundCategory,
                        1.0f, 0.8f + world.random.nextFloat() * 0.4f
                    )

                    // Damage the shield (optional)
                    val shield = getStackInHand(Hand.OFF_HAND)
                    if (!shield.isEmpty && shield.isOf(Items.SHIELD)) {
                        shield.damage(1, this, EquipmentSlot.OFFHAND)
                    }

                    // Knockback the attacker slightly
                    if (attacker is LivingEntity) {
                        attacker.takeKnockback(0.5, x - attacker.x, z - attacker.z)
                    }

                    return false // Block the damage
                }
            }
        }

        return super.damage(world, source, amount)
    }

    private fun canBlockDamageSourceWithShield(source: DamageSource): Boolean {
        val typeKey = source.typeRegistryEntry.key.orElse(null) ?: return false

        // List of damage types that can be blocked by shields
        return typeKey == DamageTypes.MOB_ATTACK ||
                typeKey == DamageTypes.MOB_ATTACK_NO_AGGRO ||
                typeKey == DamageTypes.PLAYER_ATTACK ||
                typeKey == DamageTypes.ARROW ||
                typeKey == DamageTypes.TRIDENT ||
                typeKey == DamageTypes.MOB_PROJECTILE ||
                typeKey == DamageTypes.SPIT ||
                typeKey == DamageTypes.FIREBALL ||
                typeKey == DamageTypes.WITHER_SKULL ||
                typeKey == DamageTypes.THROWN
    }

    inner class PaladinCombatGoal(private val paladin: PaladinEntity) : Goal() {
        private var chargeStartTime = 0L
        private var strafeTimer = 0
        private var strafeDirection = 0
        private var targetX = 0.0
        private var targetZ = 0.0

        init {
            controls = EnumSet.of(Control.MOVE, Control.LOOK)
        }

        override fun canStart() = true
        override fun shouldContinue() = true

        override fun tick() {
            val crossbow = paladin.getStackInHand(Hand.MAIN_HAND)
            val isLoaded = CrossbowItem.isCharged(crossbow)
            val target = paladin.target

            // Always face the target if we have one
            target?.let {
                // Use predicted position for aiming if enabled
                val aimPos = if (paladin.useAimPrediction) {
                    paladin.predictTargetPosition(it, 3.15f)
                } else {
                    Vec3d(it.x, it.eyeY, it.z)
                }

                // Calculate the angle to face the target/predicted position
                val dx = aimPos.x - paladin.x
                val dz = aimPos.z - paladin.z
                val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()

                // Smoothly rotate to face target
                val yawDiff = MathHelper.wrapDegrees(targetYaw - paladin.yaw)
                val rotationSpeed = 10f
                paladin.yaw += MathHelper.clamp(yawDiff, -rotationSpeed, rotationSpeed)
                paladin.bodyYaw = paladin.yaw
                paladin.headYaw = paladin.yaw

                // Update look control to maintain facing
                paladin.lookControl.lookAt(aimPos.x, aimPos.y, aimPos.z, 30f, 30f)
            }

            // Priority 1: Shield if needed
            if (paladin.shouldShield()) {
                if (!paladin.isBlocking) {
                    paladin.stopUsingItem()
                    paladin.setCurrentHand(Hand.OFF_HAND)
                    shielding = true
                }
            } else if (paladin.isBlocking) {
                paladin.stopUsingItem()
                shielding = false
            }

            // Priority 2: Maintain distance
            if (target != null) {
                val distanceSq = paladin.squaredDistanceTo(target)

                if (distanceSq < COMFORT_RANGE_SQ) {
                    // Calculate retreat direction
                    val dx = paladin.x - target.x
                    val dz = paladin.z - target.z
                    val distance = sqrt(dx * dx + dz * dz)

                    if (distance > 0) {
                        // Set movement target behind the paladin
                        val retreatDistance = 5.0
                        targetX = paladin.x + (dx / distance) * retreatDistance
                        targetZ = paladin.z + (dz / distance) * retreatDistance

                        // Use move control instead of navigation
                        val speed = if (paladin.isBlocking) 0.4 else 0.6
                        (paladin.moveControl as PaladinMoveControl).moveTo(targetX, paladin.y, targetZ, speed)

                        // Add strafing only when not blocking
                        if (!paladin.isBlocking) {
                            if (--strafeTimer <= 0) {
                                strafeDirection = if (random.nextBoolean()) 1 else -1
                                strafeTimer = 20 + random.nextInt(20)
                            }

                            val strafeAngle = atan2(dz, dx) + (Math.PI / 2) * strafeDirection
                            val strafeOffset = 0.5
                            targetX += cos(strafeAngle) * strafeOffset
                            targetZ += sin(strafeAngle) * strafeOffset

                            (paladin.moveControl as PaladinMoveControl).moveTo(targetX, paladin.y, targetZ, speed)
                        }
                    }
                } else if (distanceSq > MAX_SHOOT_RANGE_SQ * 0.8 && !paladin.isBlocking) {
                    // Move closer if too far
                    paladin.navigation.startMovingTo(target, 1.0)
                } else {
                    // Good distance - stop moving
                    paladin.moveControl.moveTo(paladin.x, paladin.y, paladin.z, 0.0)
                }
            }

            // Priority 3: Combat actions (only if not shielding)
            if (!paladin.isBlocking) {
                when {
                    // Shoot if loaded and target is valid
                    isLoaded && target != null &&
                            !isTargetBlocking(target) &&
                            paladin.squaredDistanceTo(target) < MAX_SHOOT_RANGE_SQ &&
                            world.time - lastShotTime >= SHOT_COOLDOWN -> {
                        paladin.shootCrossbow(target)
                        chargeStartTime = 0L
                    }

                    // Charge crossbow if not loaded
                    !isLoaded && !paladin.isUsingItem -> {
                        paladin.setCurrentHand(Hand.MAIN_HAND)
                        chargeStartTime = world.time
                    }

                    // Handle charging
                    paladin.isUsingItem && paladin.activeHand == Hand.MAIN_HAND -> {
                        val useTime = paladin.itemUseTimeLeft
                        val maxUseTime = crossbow.getMaxUseTime(paladin)
                        val pullTime = CrossbowItem.getPullTime(crossbow, paladin)

                        if (maxUseTime - useTime >= pullTime) {
                            (crossbow.item as CrossbowItem).onStoppedUsing(crossbow, world, paladin, useTime)
                            paladin.stopUsingItem()
                        }
                    }
                }
            }
        }

        override fun stop() {
            paladin.stopUsingItem()
            shielding = false
            paladin.navigation.stop()
        }
    }
}