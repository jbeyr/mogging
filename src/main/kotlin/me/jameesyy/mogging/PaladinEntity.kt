package me.jameesyy.mogging

import net.minecraft.entity.*
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import java.util.*
import kotlin.math.*

class PaladinEntity(
    type: EntityType<out PaladinEntity>,
    world: World
) : SkeletonEntity(type, world), CrossbowUser {

    private val MELEE_RANGE_SQ = 4.0 * 4.0
    private val COMFORT_RANGE_SQ = 10.0 * 10.0
    private val MAX_SHOOT_RANGE_SQ = 24.0 * 24.0
    private var lastShotTime = 0L
    private val SHOT_COOLDOWN = 60L // 3 second between shots

    companion object {
        fun createAttributes(): DefaultAttributeContainer.Builder =
            HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 34.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.35)
    }

    override fun initGoals() {
        goalSelector.add(1, SwimGoal(this))
        goalSelector.add(2, PaladinCombatGoal(this))
        goalSelector.add(3, WanderAroundFarGoal(this, 0.8))
        goalSelector.add(4, LookAtEntityGoal(this, PlayerEntity::class.java, 8f))
        goalSelector.add(5, LookAroundGoal(this))

        targetSelector.add(1, RevengeGoal(this))
        targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true))
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

    fun shootCrossbow(target: LivingEntity) {
        val crossbowStack = getStackInHand(Hand.MAIN_HAND)
        if (!CrossbowItem.isCharged(crossbowStack)) return

        // Prevent machine gun firing
        if (world.time - lastShotTime < SHOT_COOLDOWN) return

        val speed = 3.15f
        val divergence = 1.0f

        // Calculate distance to target
        val dx = target.x - x
        val dy = target.eyeY - eyeY
        val dz = target.z - z
        val horizontalDistance = sqrt(dx * dx + dz * dz)

        // For close to medium range, use a direct shot with minimal arc
        val gravity = 0.05f
        val ticksToTarget = horizontalDistance / speed

        // Simple trajectory calculation
        val gravityDrop = 0.5 * gravity * ticksToTarget * ticksToTarget

        // Direct shot angle - just enough to compensate for gravity
        val directAngle = atan2(dy + gravityDrop, horizontalDistance)

        // Limit angle to keep shots direct-looking
        val maxAngle = Math.toRadians(30.0)
        val launchAngle = directAngle.coerceIn(-maxAngle, maxAngle)

        // If we need too steep an angle, wait for better opportunity
        if (abs(directAngle) > maxAngle && dy > 2) {
            return
        }

        // Calculate velocity components
        val totalVelocity = speed
        val horizontalVelocity = totalVelocity * cos(launchAngle)
        val verticalVelocity = totalVelocity * sin(launchAngle)

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
        projectile.setVelocity(vx, vy, vz, 3.15f, divergence)

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

    inner class PaladinCombatGoal(private val paladin: PaladinEntity) : Goal() {
        private var chargeStartTime = 0L

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
                paladin.lookControl.lookAt(it, 30f, 30f)
            }

            // Priority 1: Shield if needed
            if (paladin.shouldShield()) {
                if (!paladin.isBlocking) {
                    paladin.stopUsingItem()
                    paladin.setCurrentHand(Hand.OFF_HAND)
                }
            } else if (paladin.isBlocking) {
                paladin.stopUsingItem()
            }

            // Priority 2: Maintain distance
            if (target != null) {
                val distanceSq = paladin.squaredDistanceTo(target)
                if (distanceSq < COMFORT_RANGE_SQ) {
                    val dx = paladin.x - target.x
                    val dz = paladin.z - target.z
                    val distance = sqrt(dx * dx + dz * dz)

                    if (distance > 0) {
                        val retreatX = paladin.x + (dx / distance) * 3
                        val retreatZ = paladin.z + (dz / distance) * 3
                        val speed = if (paladin.isBlocking) 0.8 else 1.2
                        paladin.navigation.startMovingTo(retreatX, paladin.y, retreatZ, speed)
                    }
                } else {
                    paladin.navigation.stop()
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
                        // Immediately recognize we need to reload
                        chargeStartTime = 0L
                    }

                    // Charge crossbow if not loaded
                    !isLoaded && !paladin.isUsingItem -> {
                        paladin.setCurrentHand(Hand.MAIN_HAND)
                        chargeStartTime = world.time
                    }

                    // Handle charging - let vanilla handle the loading
                    paladin.isUsingItem && paladin.activeHand == Hand.MAIN_HAND -> {
                        val useTime = paladin.itemUseTimeLeft
                        val maxUseTime = crossbow.getMaxUseTime(paladin)
                        val pullTime = CrossbowItem.getPullTime(crossbow, paladin)

                        // Check if we've been using long enough
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
            paladin.navigation.stop()
        }
    }
}