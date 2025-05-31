package me.jameesyy.mogging

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.entity.projectile.PersistentProjectileEntity

class Mogging : ModInitializer {

    companion object {
        const val MOD_ID = "mogging"
    }

    override fun onInitialize() {
        MoggingEntities.registerAll()
    }
}