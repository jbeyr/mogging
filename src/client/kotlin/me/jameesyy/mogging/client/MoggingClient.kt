package me.jameesyy.mogging.client

import me.jameesyy.mogging.Mogging
import me.jameesyy.mogging.client.renderer.HackermanRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

@Environment(EnvType.CLIENT)
class MoggingClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Register the custom entity renderer
        EntityRendererRegistry.register(Mogging.HACKERMAN) { context ->
            HackermanRenderer(context)
        }
    }
}