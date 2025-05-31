package me.jameesyy.mogging.goals

import me.jameesyy.mogging.PaladinEntity
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Items
import net.minecraft.util.Hand
import java.util.EnumSet

class ChargeCrossbowIdleGoal(private val paladin: PaladinEntity) : Goal() {

    init { controls = EnumSet.of(Control.MOVE) }

    override fun canStart(): Boolean {
        if (paladin.target != null || paladin.isBlocking) return false
        val stack = paladin.getStackInHand(Hand.MAIN_HAND)
        return stack.item == Items.CROSSBOW && !CrossbowItem.isCharged(stack)
    }

    override fun shouldContinue() = paladin.isUsingItem || canStart()

    override fun tick() {
        if (!paladin.isUsingItem) paladin.setCurrentHand(Hand.MAIN_HAND) // keep charging
    }
}