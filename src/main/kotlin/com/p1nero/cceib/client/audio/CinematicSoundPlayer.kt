package com.p1nero.cceib.client.audio

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

/** Plays existing Minecraft/Cobblemon sound events without bundling duplicate audio assets. */
object CinematicSoundPlayer {
    data class Cue(
        val delayMs: Long,
        val soundId: String,
        val volume: Float = 1.0f,
        val pitch: Float = 1.0f,
    )

    private data class ScheduledCue(
        val group: String,
        val playAt: Long,
        val cue: Cue,
    )

    private val scheduled = mutableListOf<ScheduledCue>()
    private var dispatching = false

    fun play(soundId: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
        val location = ResourceLocation.tryParse(soundId) ?: return
        BuiltInRegistries.SOUND_EVENT.getOptional(location).ifPresent { sound ->
            dispatching = true
            try {
                Minecraft.getInstance().soundManager.play(
                    SimpleSoundInstance.forUI(sound, pitch, volume),
                )
            } finally {
                dispatching = false
            }
        }
    }

    fun playSequence(group: String, cues: List<Cue>) {
        cancel(group)
        val now = System.nanoTime()
        cues.forEach { cue ->
            scheduled += ScheduledCue(group, now + cue.delayMs * 1_000_000L, cue)
        }
    }

    fun tick() {
        val now = System.nanoTime()
        val iterator = scheduled.iterator()
        while (iterator.hasNext()) {
            val scheduledCue = iterator.next()
            if (scheduledCue.playAt > now) continue
            iterator.remove()
            scheduledCue.cue.let { play(it.soundId, it.volume, it.pitch) }
        }
    }

    fun cancel(group: String) {
        scheduled.removeIf { it.group == group }
    }

    fun isDispatching(): Boolean = dispatching
}
