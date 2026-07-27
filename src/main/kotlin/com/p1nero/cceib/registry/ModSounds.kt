package com.p1nero.cceib.registry

import com.p1nero.cceib.CobblemonCinematicsMod
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModSounds {
    private val sounds = DeferredRegister.create(Registries.SOUND_EVENT, CobblemonCinematicsMod.ID)

    val BADGE_GET: DeferredHolder<SoundEvent, SoundEvent> = sounds.register("badge_get", Supplier {
        SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(CobblemonCinematicsMod.ID, "badge_get"),
        )
    })

    fun register(modBus: IEventBus) {
        sounds.register(modBus)
    }
}
