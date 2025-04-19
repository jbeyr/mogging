package me.jameesyy.mogging

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.World
import java.util.*
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sqrt

class HackermanEntity(entityType: EntityType<out HackermanEntity>, world: World) : HostileEntity(entityType, world) {

    private var prevHandSwingProgress = 0f
    private var attackCooldown = 0
    private var skinIndex: Int = -1


    companion object {
        const val ATTACK_REACH = 4.0f

        // Animation constants
        private const val ATTACK_COOLDOWN = 5
        private const val SWING_DURATION = 6
        private const val PATH_UPDATE_INTERVAL = 5

        fun createAttributes(): DefaultAttributeContainer.Builder {
            return createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.FOLLOW_RANGE, 50.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.ARMOR, 0.0)
                .add(EntityAttributes.ATTACK_KNOCKBACK, 1.4)
        }
    }

    init {
        this.setPathfindingPenalty(PathNodeType.LAVA, -1.0f)
        this.setPathfindingPenalty(PathNodeType.WATER, -1.0f)
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))


        if (skinIndex == -1) {
            // Use entity UUID for consistent skin assignment
            skinIndex = (uuid.leastSignificantBits % 100).toInt().absoluteValue
            println("Entity ${this.id} initialized with skin index $skinIndex from UUID $uuid")
        }
    }

    override fun initGoals() {
        this.goalSelector.add(0, SwimGoal(this))
        this.goalSelector.add(1, HackermanAttackGoal(this, 1.2))
        this.goalSelector.add(7, WanderAroundFarGoal(this, 1.0))
        this.goalSelector.add(7, LookAroundGoal(this))
        this.goalSelector.add(8, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))

        this.targetSelector.add(1, RevengeGoal(this))
        this.targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        this.targetSelector.add(3, ActiveTargetGoal(this, MobEntity::class.java, true))
    }

    // FIXME for some reason this entity doesnt show equipment..
    override fun initEquipment(random: Random?, localDifficulty: LocalDifficulty?) {
        super.initEquipment(random, localDifficulty)
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f) // Don't drop the weapon
    }

    // TODO consider if needed
    override fun isUsingItem(): Boolean {
        return false
    }

    // TODO consider if needed
    override fun getActiveHand(): Hand {
        return Hand.MAIN_HAND
    }

//    override fun getEquippedStack(slot: EquipmentSlot): ItemStack {
//        if (slot == EquipmentSlot.MAINHAND) {
//            return ItemStack(Items.IRON_SWORD)
//        }
//        return super.getEquippedStack(slot)
//    }


    // uses LivingEntity's vanilla implementation for smooth animation
    override fun getHandSwingProgress(tickDelta: Float): Float {
        var progress = handSwingProgress
        if (handSwinging) {
            progress = (handSwingTicks.toFloat() + tickDelta) / SWING_DURATION // progress is based on current ticks
        }
        return MathHelper.clamp(progress, 0.0f, 1.0f)
    }

    private var jumpCooldown = 0
    private val JUMP_COOLDOWN_MAX = 1 // lol
    private val JUMP_CHANCE = 0.4f

    private var strafingTime = 0
    private var strafingClockwise = false
    private var strafeSpeed = 0.0
    private val STRAFE_DURATION_MIN = 4
    private val STRAFE_DURATION_MAX = 14
    private val STRAFE_DISTANCE_SQ_MIN = 0 // Min distance squared to start strafing
    private val STRAFE_DISTANCE_SQ_MAX = 4 * 4 // Max distance squared to stop strafing
    private val STRAFE_WALK_BACK_DISTANCE_SQ = 3 * 3

    // Add this method to handle strafing movement
    private fun updateStrafing() {
        // Decrease strafing time
        if (strafingTime > 0) {
            strafingTime--
        }

        val target = this.target ?: return
        val distanceSquared = this.squaredDistanceTo(target)

        // Check if we're in the strafing distance range
        val inStrafingRange = distanceSquared >= STRAFE_DISTANCE_SQ_MIN && distanceSquared <= STRAFE_DISTANCE_SQ_MAX
        val targetBboxSideLenSq = target.boundingBox.averageSideLength * target.boundingBox.averageSideLength;
        val tooClose = distanceSquared < STRAFE_WALK_BACK_DISTANCE_SQ + targetBboxSideLenSq;

        // Start or continue strafing
        if (inStrafingRange || tooClose) {
            // If not currently strafing, start a new strafe
            if (strafingTime <= 0) {
                strafingTime = random.nextBetween(STRAFE_DURATION_MIN, STRAFE_DURATION_MAX) // random time
                strafingClockwise = random.nextBoolean() /// random direction

                strafeSpeed = 0.5 + random.nextFloat() * 0.3
            }

            // Apply strafing movement
            if (strafingTime > 0) {
                // Calculate forward and strafe components
                // Forward is negative when too close to back away
                val forwardAmount = if (tooClose) -0.6f else 0.6f
                val strafeAmount = if (strafingClockwise) 0.5f else -0.5f

                // Set movement input
                this.movementSpeed = this.getAttributeValue(EntityAttributes.MOVEMENT_SPEED).toFloat() * strafeSpeed.toFloat()

                // Apply the movement
                this.setForwardSpeed(forwardAmount)
                this.setSidewaysSpeed(strafeAmount)

                // Make sure we're still looking at the target while strafing
                this.lookAtEntity(target, 30.0f, 30.0f)
            }
        } else {
            // Outside strafing range, reset strafing
            strafingTime = 0
            this.setSidewaysSpeed(0.0f)
        }
    }

    private var criticalHitCooldown = 0
    private val CRITICAL_COOLDOWN_MIN = 20 // Minimum ticks between crit attempts (2 seconds)
    private val CRITICAL_COOLDOWN_MAX = 100 // Maximum ticks between crit attempts (5 seconds)
    private val CRITICAL_CHANCE = 0.3f // Chance to attempt a critical hit when conditions are right
    private var isTryingCritical = false // Whether the entity is currently trying to perform a critical hit

    // Add this method to check if a critical hit is possible
    private fun canAttemptCriticalHit(target: LivingEntity): Boolean {
        // Check if cooldown has expired
        if (criticalHitCooldown > 0) return false

        // Check if we're in attack range
        val distanceSquared = this.squaredDistanceTo(target)
        if (distanceSquared > ATTACK_REACH * ATTACK_REACH) return false

        // Check if we're on the ground (need to jump to perform a crit)
        if (!this.isOnGround) return false

        // Check if we have a clear line of sight to the target
        if (!this.canSee(target)) return false

        // Check if we're not already trying a critical
        if (isTryingCritical) return false

        // All conditions met, random chance to attempt a crit
        return random.nextFloat() < CRITICAL_CHANCE
    }

    // Add this method to execute a critical hit
    private fun attemptCriticalHit(target: LivingEntity) {
        // Start the critical hit sequence
        isTryingCritical = true

        // Jump to start falling
        this.jump()

        // Reset cooldown for next critical attempt
        criticalHitCooldown = random.nextBetween(CRITICAL_COOLDOWN_MIN, CRITICAL_COOLDOWN_MAX)
    }

    /**
     * Actually perform (not try) the attack.
     */
    override fun tryAttack(world: ServerWorld, target: Entity): Boolean {
        // Get base damage
        var damage = this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE).toFloat()

        // Check if this is a critical hit (falling, not in water, not blind)
        // These are the vanilla conditions for a critical hit
        val isCriticalHit = !this.isOnGround && !this.isTouchingWater && !this.hasStatusEffect(StatusEffects.BLINDNESS)

        // Apply critical hit multiplier (50% more damage, same as vanilla)
        if (isCriticalHit) {
            damage *= 1.5f
        }

        if (target.damage(world, this.damageSources.mobAttack(this), damage)) {
            // Spawn critical hit particles if it was a critical hit
            if (isCriticalHit && target is LivingEntity) {
                // Spawn the critical hit particles
                world.spawnParticles(
                    ParticleTypes.CRIT,
                    target.x,
                    target.y + target.height * 0.5,
                    target.z,
                    10, // Number of particles
                    target.boundingBox.averageSideLength / 2, // Spread X
                    target.boundingBox.lengthY / 2, // Spread Y
                    target.boundingBox.averageSideLength / 2, // Spread Z
                    0.2 // Speed
                )

                // Play critical hit sound
                this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f)
            }

            // Apply knockback
            if (target is LivingEntity) {
                val knockbackStrength = this.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE).toFloat()

                if (knockbackStrength > 0.0f) {
                    val knockbackMultiplier = 0.5f * knockbackStrength
                    val dx = this.x - target.x
                    val dz = this.z - target.z

                    // Normalize the vector and apply knockback
                    val length = sqrt(dx * dx + dz * dz)
                    if (length > 0.0) {
                        target.addVelocity(
                            dx / length * knockbackMultiplier,
                            0.6,
                            dz / length * knockbackMultiplier
                        )
                    }
                    // Update target velocity
                    target.velocityModified = true
                }
            }

            // Reset the critical hit attempt flag
            if (isCriticalHit) {
                isTryingCritical = false
            }

            return true
        }
        return false
    }

    // Also modify the tick method to make attacking more opportunistic
    override fun tick() {
        prevHandSwingProgress = handSwingProgress
        super.tick()

        if (attackCooldown > 0) {
            attackCooldown--
        }

        if (jumpCooldown > 0) {
            jumpCooldown--
        }

        if (criticalHitCooldown > 0) {
            criticalHitCooldown--
        }

        // Handle target tracking and movement
        val target = this.target
        if (target != null) {
            // Calculate distance to target
            val distanceSquared = this.squaredDistanceTo(target)

            // Update strafing behavior
            updateStrafing()

            // Check if we should attempt a deliberate critical hit
            if (canAttemptCriticalHit(target)) {
                attemptCriticalHit(target)
            }

            // Sprint-jump behavior (only when not strafing and not trying a critical)
            if (strafingTime <= 0 && !isTryingCritical) {
                if (distanceSquared > ATTACK_REACH * ATTACK_REACH) {
                    // Enable sprinting when not in attack range
                    this.isSprinting = true

                    // Check for jumping when on ground and cooldown expired
                    if (this.isOnGround && jumpCooldown <= 0) {
                        // Check if we're actually moving
                        val motion = this.velocity
                        val speed = sqrt(motion.x * motion.x + motion.z * motion.z)

                        // Jump if moving and either:
                        // 1. Far from target (always jump when sprinting far away)
                        // 2. Random chance for medium distance
                        val shouldJump = speed > 0.1 && (
                                distanceSquared > ATTACK_REACH * ATTACK_REACH * 4 ||
                                        random.nextFloat() < JUMP_CHANCE
                                )

                        if (shouldJump) {
                            // Vanilla jump
                            this.jump()
                            jumpCooldown = JUMP_COOLDOWN_MAX
                        }
                    }
                } else {
                    // In attack range - stop sprinting
                    this.isSprinting = false
                }
            } else {
                // When strafing or trying a critical hit, don't sprint
                this.isSprinting = false
            }

            // Attack logic:
            // 1. If in attack range and attack cooldown is ready
            // 2. AND either we're deliberately trying a crit and in the air, OR we're not trying a deliberate crit
            // 3. OR we're in the air (for opportunistic crits)

            val targetBboxSideLenSq = target.boundingBox.averageSideLength * target.boundingBox.averageSideLength
            val randomBboxDistance = random.nextFloat()*targetBboxSideLenSq
            if (distanceSquared <= ATTACK_REACH * ATTACK_REACH - randomBboxDistance*randomBboxDistance && attackCooldown <= 0) {
                // Always attack if we're in the air (opportunistic crit)
                val shouldAttack = !this.isOnGround ||
                        // Or if we're either not trying a crit, or we are and we're in the air
                        (!isTryingCritical || (isTryingCritical && !this.isOnGround))

                if (shouldAttack) {
                    this.tryAttackWithCooldown(target)
                }
            }
        } else {
            // No target - reset all combat states
            this.isSprinting = false
            strafingTime = 0
            isTryingCritical = false
            this.setSidewaysSpeed(0.0f)
        }

        if (this.age % 200 == 0) {
            debugSkinInfo()
        }
    }

    // Modify the tryAttackWithCooldown method to use our enhanced tryAttack
    private fun tryAttackWithCooldown(target: LivingEntity): Boolean {
        if (attackCooldown <= 0) {
            attackCooldown = ATTACK_COOLDOWN
            this.swingHand(Hand.MAIN_HAND)
            return this.tryAttack(this.world as ServerWorld, target)
        }
        return false
    }

    /**
     * Custom attack goal for `HackermanEntity`.
     */
    private class HackermanAttackGoal(
        private val hackerman: HackermanEntity,
        private val moveSpeed: Double
    ) : Goal() {
        private var pathUpdateTimer = 0
        private var lastPathX = 0.0

        private var lastPathZ = 0.0

        init {
            this.controls = EnumSet.of(Control.MOVE, Control.LOOK)
        }

        override fun canStart(): Boolean {
            return hackerman.target != null && hackerman.target!!.isAlive && hackerman.canTarget(hackerman.target!!)
        }

        override fun shouldContinue(): Boolean {
            return canStart()
        }

        override fun start() {
            val target = hackerman.target ?: return

            // Force immediate path update on start
            hackerman.navigation.stop()
            hackerman.navigation.startMovingTo(target, moveSpeed * 1.3)
            hackerman.setAttacking(true)
            pathUpdateTimer = 0

            // Store target position
            lastPathX = target.x
            lastPathZ = target.z
        }

        override fun tick() {
            val target = hackerman.target ?: return

            // Always look at target
            hackerman.lookAtEntity(target, 30.0f, 30.0f)

            // Calculate distance to target
            val distanceSquared = hackerman.squaredDistanceTo(target)

            // Only update path if not currently strafing
            if (hackerman.strafingTime <= 0) {
                // Check if target has moved significantly
                val targetMovedSignificantly =
                    abs(target.x - lastPathX) > 1.0 ||
                            abs(target.z - lastPathZ) > 1.0

                // Update path more frequently when:
                // 1. Timer expires
                // 2. Target moves significantly
                // 3. Path is stuck or invalid
                if (--pathUpdateTimer <= 0 || targetMovedSignificantly || !hackerman.navigation.isFollowingPath) {
                    pathUpdateTimer = PATH_UPDATE_INTERVAL

                    // Adjust speed based on distance
                    val pathSpeed = when {
                        distanceSquared > ATTACK_REACH * ATTACK_REACH * 4 -> moveSpeed * 1.3
                        distanceSquared > ATTACK_REACH * ATTACK_REACH * 2 -> moveSpeed * 1.2
                        else -> moveSpeed
                    }

                    // Force a new path
                    hackerman.navigation.stop()
                    hackerman.navigation.startMovingTo(target, pathSpeed)

                    // Update last path position
                    lastPathX = target.x
                    lastPathZ = target.z
                }

                // If very close to target but not in attack range, move directly toward target.
                // for some reason removing this makes them not wanna attack skeletons, only strafe them,
                // so im keeping this in
                if (distanceSquared < 4.0 && distanceSquared > ATTACK_REACH * ATTACK_REACH) {
                    // Direct movement logic (keep as is)
                    val dx = target.x - hackerman.x
                    val dy = target.y - hackerman.y
                    val dz = target.z - hackerman.z

                    val length = sqrt(dx * dx + dz * dz)
                    if (length > 0.0) {
                        hackerman.moveControl.moveTo(
                            hackerman.x + dx / length * 0.5,
                            hackerman.y + (if (dy > 0) 0.5 else 0.0),
                            hackerman.z + dz / length * 0.5,
                            moveSpeed * 1.2
                        )
                    }
                }
            } else {
                // When strafing, stop pathfinding to allow manual movement control
                hackerman.navigation.stop()
            }
        }
        override fun stop() {
            hackerman.setAttacking(false)
            hackerman.isSprinting = false
            hackerman.strafingTime = 0
            hackerman.setSidewaysSpeed(0.0f)
            hackerman.navigation.stop()
        }
    }

    override fun getAmbientSound(): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_BREATH
    }

    override fun getHurtSound(source: DamageSource): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_HURT
    }

    override fun getDeathSound(): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_DEATH
    }

    // Save/load for persistence
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        // Always save the current skin index
        nbt.putInt("SkinIndex", skinIndex)
    }

    // Load from NBT
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)

        if (nbt.contains("SkinIndex")) {
            skinIndex = nbt.getInt("SkinIndex").orElse(-1)
            println("Entity ${this.id} loaded skin index $skinIndex from NBT")
        }

        // If for some reason we still don't have a valid skin index, initialize it
        if (skinIndex == -1) {
            skinIndex = (uuid.leastSignificantBits % 100).toInt().absoluteValue
            println("Entity ${this.id} re-initialized skin index to $skinIndex after NBT load")
        }
    }

    // Getter for the renderer to use
    fun getSkinIndex(): Int {
        // Ensure we always have a valid skin index
        if (skinIndex == -1) {
            skinIndex = (uuid.leastSignificantBits % 100).toInt().absoluteValue
            println("Entity ${this.id} late-initialized skin index to $skinIndex")
        }
        return skinIndex
    }

    fun debugSkinInfo() {
        println("Entity ${this.id} with UUID $uuid has skin index $skinIndex")
    }
}