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
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.World

class HackermanEntity(entityType: EntityType<out HackermanEntity>, world: World) : HostileEntity(entityType, world) {

    // Animation properties
    private var prevHandSwingProgress = 0f
    private var attackCooldown = 0

    // Tracking properties for movement
    private var lastTargetX = 0.0
    private var lastTargetY = 0.0
    private var lastTargetZ = 0.0
    private var stuckTicks = 0

    companion object {
        // Custom reach distance for Hackerman
        const val ATTACK_REACH = 5.0f

        // Animation constants
        private const val ATTACK_COOLDOWN = 5
        private const val SWING_DURATION = 6 // Standard vanilla swing duration

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
    }

    override fun initGoals() {
        this.goalSelector.add(0, SwimGoal(this))
        this.goalSelector.add(1, LongRangeAttackGoal(this, 1.2, false))
        this.goalSelector.add(7, WanderAroundFarGoal(this, 1.0))
        this.goalSelector.add(7, LookAroundGoal(this))
        this.goalSelector.add(8, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))

        this.targetSelector.add(1, RevengeGoal(this))
        this.targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        this.targetSelector.add(3, ActiveTargetGoal(this, MobEntity::class.java, true))
    }

    override fun initEquipment(random: Random?, localDifficulty: LocalDifficulty?) {
        super.initEquipment(random, localDifficulty)

        // Give it a visible weapon
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))

        // Set drop chances
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f) // Don't drop the weapon
    }

    // Ensure the entity is holding its weapon
    override fun getMainHandStack(): ItemStack {
        return this.getEquippedStack(EquipmentSlot.MAINHAND)
    }

    // Override to ensure we're never using an item
    override fun isUsingItem(): Boolean {
        return false
    }

    // Set the active hand to MAIN_HAND
    override fun getActiveHand(): Hand {
        return Hand.MAIN_HAND
    }

    // Use LivingEntity's vanilla implementation
    override fun getHandSwingProgress(tickDelta: Float): Float {
        var progress = handSwingProgress
        if (handSwinging) {
            // If currently swinging, progress is based on current ticks
            progress = (handSwingTicks.toFloat() + tickDelta) / SWING_DURATION
        }
        return MathHelper.clamp(progress, 0.0f, 1.0f)
    }

    override fun tick() {
        // Store previous animation state before tick
        prevHandSwingProgress = handSwingProgress

        super.tick()

        // Decrement attack cooldown
        if (attackCooldown > 0) {
            attackCooldown--
        }

        // Check for stuck behavior with target
        val target = this.target
        if (target != null) {
            // If we haven't moved in a while and have a target
            if (this.lastTargetX == target.x &&
                this.lastTargetY == target.y &&
                this.lastTargetZ == target.z) {
                stuckTicks++

                // If stuck for too long, force a navigation update
                if (stuckTicks > 40) { // 2 seconds
                    // Force navigation recalculation
                    this.navigation.stop()
                    this.navigation.startMovingTo(target, 1.2)
                    stuckTicks = 0
                }
            } else {
                // Update last target position
                this.lastTargetX = target.x
                this.lastTargetY = target.y
                this.lastTargetZ = target.z
                stuckTicks = 0
            }

            // Always look at target even if pathfinding is failing
            this.lookAtEntity(target, 30.0f, 30.0f)
        }
    }

    // Override to control when the entity can attack
    fun canAttack(): Boolean {
        return attackCooldown <= 0
    }

    // Custom method to trigger an attack with cooldown
    fun performAttack(target: LivingEntity) {
        if (attackCooldown <= 1) {
            // Set cooldown
            attackCooldown = ATTACK_COOLDOWN

            // Trigger the swing animation
            this.swingHand(Hand.MAIN_HAND)

            // Perform the attack
            this.tryAttack(this.world as ServerWorld, target)
        }
    }

    // Improved attack goal with extended reach
    private class LongRangeAttackGoal(
        private val hackerman: HackermanEntity,
        private val moveSpeed: Double,
        pauseWhenMobIdle: Boolean
    ) : MeleeAttackGoal(hackerman, moveSpeed, pauseWhenMobIdle) {

        private var ticksUntilPathRecalculation = 0
        private var attackTicks = 0

        override fun tick() {
            super.tick()

            // Decrement attack timer
            if (attackTicks > 0) {
                attackTicks--
            }

            // Force more frequent path recalculation
            if (--ticksUntilPathRecalculation <= 0) {
                val target = this.hackerman.target ?: return

                // Update path more frequently when in combat
                this.hackerman.navigation.startMovingTo(target, moveSpeed)
                ticksUntilPathRecalculation = 10 // Recalculate path every half second
            }

            // Try to attack more frequently when in range
            val target = this.hackerman.target
            if (target != null && canAttack(target) && hackerman.canAttack() && attackTicks <= 0) {
                hackerman.performAttack(target)
                attackTicks = 10 // Match the cooldown for consistent attacks
            }
        }

        override fun shouldContinue(): Boolean {
            val target = this.hackerman.target ?: return false
            if (!target.isAlive) {
                return false
            }

            return this.hackerman.canTarget(target)
        }

        override fun canAttack(target: LivingEntity?): Boolean {
            return target != null && this.hackerman.squaredDistanceTo(target) <= (ATTACK_REACH * ATTACK_REACH)
        }

        override fun attack(target: LivingEntity?) {
            // This is handled in the tick method for more frequent attacks
        }

        override fun start() {
            super.start()
            ticksUntilPathRecalculation = 0 // Force immediate path calculation
            attackTicks = 0 // Reset attack timer
        }
    }

    // Implement attack method with extended reach
    override fun tryAttack(world: ServerWorld, target: Entity): Boolean {
        if (this.squaredDistanceTo(target) <= ATTACK_REACH * ATTACK_REACH) {
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
                                0.25,
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

    // Sounds
    override fun getAmbientSound(): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_BREATH
    }

    override fun getHurtSound(source: DamageSource): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_HURT
    }

    override fun getDeathSound(): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_DEATH
    }
}