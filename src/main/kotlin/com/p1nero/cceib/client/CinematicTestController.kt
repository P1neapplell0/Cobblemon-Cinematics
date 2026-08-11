package com.p1nero.cceib.client

import com.p1nero.cceib.client.badge.BadgeCinematicManager
import com.p1nero.cceib.client.battle.BattleIntroController
import com.p1nero.cceib.client.compat.megashowdown.MegaShowdownCompat
import net.minecraft.client.Minecraft
import java.util.ArrayDeque

object CinematicTestController {
    enum class Kind {
        BATTLE_INTRO,
        BADGE,
        MEGA,
        DYNAMAX,
        Z_MOVE,
        TERASTALIZATION,
    }

    private val queue = ArrayDeque<Kind>()
    private var active: Kind? = null
    private var openRequested = false

    fun request(vararg kinds: Kind): Boolean {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.level == null) return false
        queue.addAll(kinds)
        openRequested = true
        return true
    }

    fun tick() {
        val minecraft = Minecraft.getInstance()
        if (openRequested) {
            openRequested = false
            if (minecraft.screen !is CinematicTestScreen) {
                minecraft.setScreen(CinematicTestScreen())
            }
            return
        }

        if (minecraft.screen !is CinematicTestScreen) {
            if (active != null || queue.isNotEmpty()) cancel()
            return
        }

        active?.let { kind ->
            if (isPlaying(kind)) return
            active = null
        }

        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (start(next)) {
                active = next
                return
            }
        }

        minecraft.setScreen(null)
    }

    fun cancel() {
        queue.clear()
        active = null
        openRequested = false
        BattleIntroController.stopTest()
        BadgeCinematicManager.stopTest()
        MegaShowdownCompat.stopTest()
    }

    private fun start(kind: Kind): Boolean = when (kind) {
        Kind.BATTLE_INTRO -> BattleIntroController.debugStandalone()
        Kind.BADGE -> BadgeCinematicManager.debugPlay()
        Kind.MEGA -> MegaShowdownCompat.debugPlay("mega")
        Kind.DYNAMAX -> MegaShowdownCompat.debugPlay("dynamax")
        Kind.Z_MOVE -> MegaShowdownCompat.debugPlay("zmove")
        Kind.TERASTALIZATION -> MegaShowdownCompat.debugPlay("terastalization")
    }

    private fun isPlaying(kind: Kind): Boolean = when (kind) {
        Kind.BATTLE_INTRO -> BattleIntroController.isPlaying()
        Kind.BADGE -> BadgeCinematicManager.isPlaying()
        Kind.MEGA,
        Kind.DYNAMAX,
        Kind.Z_MOVE,
        Kind.TERASTALIZATION,
        -> MegaShowdownCompat.isPlaying()
    }
}
