package me.jameesyy.mogging.client

import me.jameesyy.mogging.MoggingEntities
import me.jameesyy.mogging.client.render.*
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

@Environment(EnvType.CLIENT)
class MoggingClient : ClientModInitializer {
    override fun onInitializeClient() {
        // if we don't initialize each entity here, if the client encounters one broadcasted by the server, it'll just crash out
        EntityRendererRegistry.register(MoggingEntities.HACKERMAN) { HackermanRenderer(it) }
        EntityRendererRegistry.register(MoggingEntities.PIRANHA) { PiranhaRenderer(it) }
        EntityRendererRegistry.register(MoggingEntities.STATIC_SHEEP) { StaticSheepRenderer(it) }
        EntityRendererRegistry.register(MoggingEntities.BULLISH_COW) { BullishCowRenderer(it) }
        EntityRendererRegistry.register(MoggingEntities.CAMO_CREEPER) { CamoCreeperRenderer(it) }
        EntityRendererRegistry.register(MoggingEntities.PALADIN) { PaladinEntityRenderer(it) }
    }
}