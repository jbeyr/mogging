package me.jameesyy.mogging

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.entity.ai.goal.RevengeGoal
import net.minecraft.entity.ai.goal.WanderAroundFarGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
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
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import java.util.EnumSet

class BullishCowEntity(entityType: EntityType<out CowEntity>, world: World) : CowEntity(entityType, world) {

    private var chargeTarget: LivingEntity? = null
    private var isCharging = false
    private var chargeCooldown = 0
    private var chargeTime = 0
    private var isPreparingCharge = false
    private var preparationTime = 0
    private var buckedEntities = mutableSetOf<LivingEntity>()
    private var hasBucked = false
    private var lastBuckTime = 0
    private val pendingBucks = mutableMapOf<LivingEntity, Int>()


    companion object {
        private val CHARGING_STATE: TrackedData<Boolean> = DataTracker.registerData(
            BullishCowEntity::class.java,
            TrackedDataHandlerRegistry.BOOLEAN
        )

        private const val DETECTION_RANGE = 16.0
        private const val CHARGE_PREPARATION_TIME = 15
        private const val CHARGE_DURATION = 40
        private const val CHARGE_COOLDOWN = 100
        private const val CHARGE_DAMAGE = 6.0f
        private const val BUCK_FORCE_HORIZONTAL = 1.2  // Increased horizontal force
        private const val BUCK_FORCE_VERTICAL = 0.8    // Increased vertical force (4-5 blocks height)
        private const val BUCK_RANGE = 2.5
        private const val BUCK_COOLDOWN = 10

        fun createAttributes(): DefaultAttributeContainer.Builder {
            return createCowAttributes()
                .add(EntityAttributes.MAX_HEALTH, 15.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.ATTACK_KNOCKBACK, 1.5)
                .add(EntityAttributes.ARMOR, 8.0)
        }
    }

    override fun initDataTracker(builder: DataTracker.Builder?) {
        super.initDataTracker(builder)
        builder?.add(CHARGING_STATE, false)
    }

    override fun initGoals() {
        goalSelector.clear { true }
        targetSelector.clear { true }

        goalSelector.add(1, ChargeAttackGoal())
        goalSelector.add(2, MeleeAttackGoal(this, 1.2, true))  // Faster melee attack movement
        goalSelector.add(3, WanderAroundFarGoal(this, 0.8))  // Faster wandering

        goalSelector.add(4, LookAtEntityGoal(this, PlayerEntity::class.java, DETECTION_RANGE.toFloat()))
        goalSelector.add(4, LookAtEntityGoal(this, SheepEntity::class.java, DETECTION_RANGE.toFloat()))

        targetSelector.add(1, RevengeGoal(this))
        targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true) { it: LivingEntity, _: ServerWorld ->
            it is PlayerEntity && (it.gameMode == GameMode.SURVIVAL || it.gameMode == GameMode.ADVENTURE)
        })
        targetSelector.add(3, ActiveTargetGoal(this, SheepEntity::class.java, true))
    }

    // Custom goal for charging attack
    private inner class ChargeAttackGoal : Goal() {
        private var path: Vec3d? = null
        private var originalPos: Vec3d? = null

        init {
            setControls(EnumSet.of(Control.MOVE, Control.LOOK))
        }

        override fun canStart(): Boolean {
            val target = this@BullishCowEntity.target
            return target != null &&
                    target.isAlive &&
                    !isCharging &&
                    !isPreparingCharge &&
                    chargeCooldown <= 0 &&
                    canSeeTarget(target) &&
                    target.squaredDistanceTo(this@BullishCowEntity) < 144.0 // 12 blocks squared
        }

        override fun start() {
            val target = this@BullishCowEntity.target ?: return

            chargeTarget = target
            isPreparingCharge = true
            preparationTime = 0
            originalPos = pos
            buckedEntities.clear()
            hasBucked = false
            lastBuckTime = 0

            // Play preparation sound
            world.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_RAVAGER_ROAR,
                SoundCategory.HOSTILE,
                1.0f,
                1.5f // Higher pitch
            )
        }

        override fun shouldContinue(): Boolean {
            val target = chargeTarget
            return (isPreparingCharge || isCharging) &&
                    target != null &&
                    target.isAlive &&
                    !(hasBucked && chargeTime > 10) // Stop shortly after bucking
        }

        override fun tick() {
            val target = chargeTarget ?: return

            if (isPreparingCharge) {
                // Face the target
                lookAtEntity(target, 30.0f, 30.0f)

                // Stomp and prepare animation
                preparationTime++

                // Create dust particles under feet
                if (preparationTime % 4 == 0 && world is ServerWorld) {
                    (world as ServerWorld).spawnParticles(
                        ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.x, pos.y, pos.z,
                        3,
                        0.2, 0.0, 0.2,
                        0.01
                    )
                }

                // After preparation time, start charging
                if (preparationTime >= CHARGE_PREPARATION_TIME) {
                    startCharging()
                }
            } else if (isCharging) {
                chargeTime++

                // Always face the target during charge
                faceTarget(target)

                // Calculate charge direction
                val targetPos = target.pos
                val direction = targetPos.subtract(pos).normalize()

                // Move faster during charge
                val speed = 0.8  // Much faster charge speed
                velocity = Vec3d(
                    direction.x * speed,
                    velocity.y,
                    direction.z * speed
                )

                // Create particles behind
                if (world is ServerWorld && random.nextFloat() < 0.3) {
                    (world as ServerWorld).spawnParticles(
                        ParticleTypes.CLOUD,
                        pos.x - direction.x * 0.5,
                        pos.y + 0.5,
                        pos.z - direction.z * 0.5,
                        1,
                        0.1, 0.1, 0.1,
                        0.0
                    )
                }

                // Check for entities to buck
                if (!hasBucked || (chargeTime - lastBuckTime > BUCK_COOLDOWN)) {
                    checkForBuckTargets()
                }

                // End charge after duration or if we've already bucked
                if (chargeTime >= CHARGE_DURATION || (hasBucked && chargeTime > lastBuckTime + 10)) {
                    stopCharging()
                }

                // Check if we hit a wall
                if (horizontalCollision) {
                    // Play collision sound
                    world.playSound(
                        null,
                        pos.x, pos.y, pos.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE,
                        SoundCategory.BLOCKS,
                        0.5f,
                        1.0f
                    )

                    // Spawn impact particles
                    if (world is ServerWorld) {
                        (world as ServerWorld).spawnParticles(
                            ParticleTypes.EXPLOSION,
                            pos.x, pos.y + height / 2, pos.z,
                            5,
                            0.5, 0.5, 0.5,
                            0.0
                        )
                    }

                    // Take some damage from hitting the wall
                    damage(world as ServerWorld, world.damageSources.generic(), 2.0f)

                    // End charge
                    stopCharging()
                }
            }
        }

        override fun stop() {
            isPreparingCharge = false
            if (isCharging) {
                stopCharging()
            }
        }

        private fun canSeeTarget(target: LivingEntity): Boolean {
            val raycastResult = performRaycast(eyePos, target.eyePos)
            return raycastResult.type != HitResult.Type.BLOCK
        }
    }

    private fun faceTarget(target: LivingEntity) {
        // Calculate direction to target
        val dx = target.x - this.x
        val dz = target.z - this.z

        // Calculate yaw to face target
        val yaw = (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90.0f

        // Set yaw and body yaw
        this.yaw = yaw
        this.bodyYaw = yaw
        this.headYaw = yaw
    }

    private fun startCharging() {
        isPreparingCharge = false
        isCharging = true
        chargeTime = 0
        hasBucked = false
        buckedEntities.clear()
        lastBuckTime = 0
        dataTracker.set(CHARGING_STATE, true)

        // Play charge sound
        world.playSound(
            null,
            pos.x, pos.y, pos.z,
            SoundEvents.ENTITY_RAVAGER_ATTACK,
            SoundCategory.HOSTILE,
            1.0f,
            1.0f
        )
    }

    private fun stopCharging() {
        isCharging = false
        chargeTime = 0
        dataTracker.set(CHARGING_STATE, false)
        chargeCooldown = CHARGE_COOLDOWN
        buckedEntities.clear()
        hasBucked = false
    }

    private fun checkForBuckTargets() {
        // Find entities in buck range
        val nearbyEntities = world.getEntitiesByClass(
            LivingEntity::class.java,
            Box.from(pos).expand(BUCK_RANGE)
        ) { it != this && it.isAlive && !buckedEntities.contains(it) }

        if (nearbyEntities.isNotEmpty()) {
            hasBucked = true
            lastBuckTime = chargeTime

            // Play buck sound
            world.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
                SoundCategory.HOSTILE,
                1.0f,
                1.2f
            )

            // Buck all entities in range
            for (entity in nearbyEntities) {
                buckEntity(entity)
                buckedEntities.add(entity)
            }
        }
    }

    // In your tick method
    override fun tick() {
        super.tick()

        if (!world.isClient) {
            // Handle cooldown
            if (chargeCooldown > 0) {
                chargeCooldown--
            }

            // Handle pending bucks
            val iterator = pendingBucks.entries.iterator()
            while (iterator.hasNext()) {
                val (entity, ticksLeft) = iterator.next()
                if (ticksLeft <= 0) {
                    // Apply the actual velocity now
                    applyBuckVelocity(entity)
                    iterator.remove()
                } else {
                    pendingBucks[entity] = ticksLeft - 1
                }
            }
        }
    }

    private fun buckEntity(entity: LivingEntity) {
        if (world is ServerWorld) {
            // Apply damage immediately
            entity.damage(world as ServerWorld, world.damageSources.mobAttack(this), CHARGE_DAMAGE)

            // Schedule the velocity change for the next tick
            pendingBucks[entity] = 1

            // Spawn particles
            (world as ServerWorld).spawnParticles(
                ParticleTypes.CRIT,
                entity.x, entity.y + entity.height / 2, entity.z,
                10, 0.5, 0.5, 0.5, 0.1
            )
        }
    }

    private fun applyBuckVelocity(entity: LivingEntity) {
        // Calculate direction away from us
        val directionAway = entity.pos.subtract(pos).normalize()

        // Set the velocity with high vertical component
        entity.setVelocity(
            directionAway.x * BUCK_FORCE_HORIZONTAL,
            BUCK_FORCE_VERTICAL,
            directionAway.z * BUCK_FORCE_HORIZONTAL
        )

        entity.velocityModified = true
        entity.fallDistance = 0.0
    }

    private fun performRaycast(start: Vec3d, end: Vec3d): HitResult {
        val context = RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            this
        )
        return world.raycast(context)
    }

    // For client rendering
    fun isCharging(): Boolean {
        return dataTracker.get(CHARGING_STATE)
    }

    // Override to prevent milk bucket usage
    override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
        return ActionResult.PASS
    }
}