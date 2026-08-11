package com.p1nero.cceib.client.battle

import com.p1nero.cceib.config.ClientConfig
import net.minecraft.resources.ResourceLocation

object BattleIntroFilters {
    fun allowsNpc(identifier: ResourceLocation?): Boolean = allows(
        identifier,
        ClientConfig.trainerIntroNpcWhitelist.get(),
        ClientConfig.trainerIntroNpcBlacklist.get(),
    )

    fun allowsPokemon(identifier: ResourceLocation): Boolean = allows(
        identifier,
        ClientConfig.wildIntroPokemonWhitelist.get(),
        ClientConfig.wildIntroPokemonBlacklist.get(),
    )

    private fun allows(
        identifier: ResourceLocation?,
        whitelist: List<String>,
        blacklist: List<String>,
    ): Boolean {
        val normalizedBlacklist = blacklist.map { it.trim().lowercase() }
        if ("*" in normalizedBlacklist) return false

        val id = identifier?.toString()?.lowercase()
        if (id != null && id in normalizedBlacklist) return false

        val normalizedWhitelist = whitelist.map { it.trim().lowercase() }
        if (normalizedWhitelist.isEmpty() || "*" in normalizedWhitelist) return true
        return id != null && id in normalizedWhitelist
    }
}
