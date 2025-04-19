package me.jameesyy.mogging.client

import me.jameesyy.mogging.Mogging
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.entity.ZombieEntityRenderer

@Environment(EnvType.CLIENT)
class MoggingClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Register the entity renderer
        EntityRendererRegistry.register(Mogging.HACKERMAN_ZOMBIE) { context ->
            ZombieEntityRenderer(context)
        }
    }
}