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
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.World
import java.util.*
import kotlin.math.absoluteValue

class HackermanEntity(entityType: EntityType<out HackermanEntity>, world: World) : HostileEntity(entityType, world) {

    private var prevHandSwingProgress = 0f
    private var attackCooldown = 0
    private var lastTargetX = 0.0
    private var lastTargetY = 0.0
    private var lastTargetZ = 0.0
    private var stuckTicks = 0

    companion object {
        const val ATTACK_REACH = 5.0f

        // Animation constants
        private const val ATTACK_COOLDOWN = 5
        private const val SWING_DURATION = 6
        private const val PATH_UPDATE_INTERVAL = 10

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

    // uses LivingEntity's vanilla implementation for smooth animation
    override fun getHandSwingProgress(tickDelta: Float): Float {
        var progress = handSwingProgress
        if (handSwinging) {
            progress = (handSwingTicks.toFloat() + tickDelta) / SWING_DURATION // progress is based on current ticks
        }
        return MathHelper.clamp(progress, 0.0f, 1.0f)
    }

    override fun tick() {
        prevHandSwingProgress = handSwingProgress

        super.tick()

        if (attackCooldown > 0) {
            attackCooldown--
        }

        // stuck check
        val target = this.target
        if (target != null) {
            // if we haven't moved in a while and have a target
            if (this.lastTargetX == target.x &&
                this.lastTargetY == target.y &&
                this.lastTargetZ == target.z) {
                stuckTicks++

                // if stuck for too long, force a navigation update
                if (stuckTicks > 40) { // 2 seconds
                    this.navigation.stop()
                    this.navigation.startMovingTo(target, 1.2)
                    stuckTicks = 0
                }
            } else {
                this.lastTargetX = target.x
                this.lastTargetY = target.y
                this.lastTargetZ = target.z
                stuckTicks = 0
            }

            // always look at target even if pathfinding is failing
            this.lookAtEntity(target, 30.0f, 30.0f)
        }
    }

    // simple attack method with cooldown
    fun tryAttackWithCooldown(target: LivingEntity): Boolean {
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

        init {
            // Set the controls this goal uses
            this.controls = EnumSet.of(Control.MOVE, Control.LOOK)
        }

        override fun canStart(): Boolean {
            val target = hackerman.target
            return target != null && target.isAlive && hackerman.canTarget(target)
        }

        override fun shouldContinue(): Boolean {
            val target = hackerman.target
            return target != null && target.isAlive && hackerman.canTarget(target)
        }

        override fun start() {
            val target = hackerman.target ?: return
            hackerman.navigation.startMovingTo(target, moveSpeed)
            hackerman.setAttacking(true)
            pathUpdateTimer = 0
        }

        override fun stop() {
            hackerman.setAttacking(false)
            hackerman.navigation.stop()
        }

        override fun tick() {
            val target = hackerman.target ?: return
            hackerman.lookAtEntity(target, 30.0f, 30.0f) // always look at target

            // update path to target periodically
            if (--pathUpdateTimer <= 0) {
                pathUpdateTimer = PATH_UPDATE_INTERVAL
                hackerman.navigation.startMovingTo(target, moveSpeed)
            }

            val distanceSquared = hackerman.squaredDistanceTo(target)
            if (distanceSquared <= ATTACK_REACH * ATTACK_REACH) {
                // try to attack - the method handles its own cooldown
                hackerman.tryAttackWithCooldown(target)
            }
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
                                0.5,
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

    override fun getAmbientSound(): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_BREATH
    }

    override fun getHurtSound(source: DamageSource): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_HURT
    }

    override fun getDeathSound(): SoundEvent {
        return SoundEvents.ENTITY_PLAYER_DEATH
    }



    private var skinIndex: Int = -1

    init {
        // Only set once when entity is first created
        if (skinIndex == -1) {
            // Random but deterministic skin assignment
            skinIndex = (uuid.leastSignificantBits % 100).toInt().absoluteValue
        }
    }

    // Save/load for persistence
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putInt("SkinIndex", skinIndex)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        if (nbt.contains("SkinIndex")) {
            skinIndex = nbt.getInt("SkinIndex").orElse(0)
        }
    }

    // Getter for the renderer to use
    fun getSkinIndex(): Int {
        return skinIndex
    }
}