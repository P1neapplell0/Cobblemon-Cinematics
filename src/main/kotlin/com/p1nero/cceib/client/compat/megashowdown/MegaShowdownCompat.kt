package com.p1nero.cceib.client.compat.megashowdown

import com.p1nero.cceib.CobblemonCinematicsMod
import net.minecraft.network.chat.Component
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.NeoForge

object MegaShowdownCompat {
    private const val MOD_ID = "mega_showdown"
    private var loaded = false

    fun initialize() {
        loaded = ModList.get().isLoaded(MOD_ID)
        if (!loaded) return

        NeoForge.EVENT_BUS.register(MegaShowdownCinematicManager)
        CobblemonCinematicsMod.LOGGER.info("Mega Showdown cinematic compatibility enabled")
    }

    fun tick() {
        if (loaded) MegaShowdownCinematicManager.tick()
    }

    fun isPlaying(): Boolean = loaded && MegaShowdownCinematicManager.isPlaying()

    @JvmStatic
    fun onBattleMessages(messages: List<Component>) {
        if (loaded) MegaShowdownCinematicManager.onBattleMessages(messages)
    }
}
