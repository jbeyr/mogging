package me.jameesyy.mogging.client

import me.jameesyy.mogging.MoggingEntities
import me.jameesyy.mogging.client.renderer.BullishCowRenderer
import me.jameesyy.mogging.client.renderer.HackermanRenderer
import me.jameesyy.mogging.client.renderer.PiranhaRenderer
import me.jameesyy.mogging.client.renderer.StaticSheepRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

@Environment(EnvType.CLIENT)
class MoggingClient : ClientModInitializer {
    override fun onInitializeClient() {
        // if we don't initialize each entity here, if the client encounters one broadcasted by the server, it'll just crash out
        EntityRendererRegistry.register(MoggingEntities.HACKERMAN) { ctx -> HackermanRenderer(ctx) }
        EntityRendererRegistry.register(MoggingEntities.PIRANHA) { ctx -> PiranhaRenderer(ctx) }
        EntityRendererRegistry.register(MoggingEntities.STATIC_SHEEP) { ctx -> StaticSheepRenderer(ctx) }
        EntityRendererRegistry.register(MoggingEntities.BULLISH_COW) { ctx -> BullishCowRenderer(ctx) }
    }
}