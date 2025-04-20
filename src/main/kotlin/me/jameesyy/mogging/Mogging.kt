package me.jameesyy.mogging

import net.fabricmc.api.ModInitializer

class Mogging : ModInitializer {

    companion object {
        const val MOD_ID = "mogging"
    }

    override fun onInitialize() {
        MoggingEntities.registerAll()
    }
}