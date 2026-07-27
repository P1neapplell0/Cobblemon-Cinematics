package com.p1nero.cceib

import com.p1nero.cceib.config.ClientConfig
import com.p1nero.cceib.registry.ModSounds
import net.neoforged.bus.api.IEventBus
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonCinematicsMod.ID)
class CobblemonCinematicsMod(modBus: IEventBus, modContainer: ModContainer, dist: Dist) {
    init {
        ModSounds.register(modBus)
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)
        if (dist == Dist.CLIENT) {
            com.p1nero.cceib.client.ClientBootstrap.initialize(modBus)
        }
        LOGGER.info("Cobblemon Cinematics initialized")
    }

    companion object {
        const val ID = "cobblemoncinematics"
        val LOGGER: Logger = LoggerFactory.getLogger(ID)
    }
}
