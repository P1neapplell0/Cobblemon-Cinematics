package com.p1nero.cceib.client

import com.mojang.blaze3d.platform.InputConstants
import com.p1nero.cceib.client.badge.BadgeCinematicManager
import com.p1nero.cceib.client.badge.BadgeInventoryTracker
import com.p1nero.cceib.client.battle.BattleCameraController
import com.p1nero.cceib.client.battle.BattleIntroController
import com.p1nero.cceib.client.compat.megashowdown.MegaShowdownCompat
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW

object ClientBootstrap {
    private val toggleBattleCamera = KeyMapping(
        "key.cobblemoncinematics.toggle_battle_camera",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.cobblemoncinematics",
    )

    fun initialize(modBus: IEventBus) {
        modBus.addListener(::registerKeyMappings)
        NeoForge.EVENT_BUS.register(this)
        NeoForge.EVENT_BUS.register(BadgeCinematicManager)
        NeoForge.EVENT_BUS.register(BattleIntroController)
        NeoForge.EVENT_BUS.register(BattleCameraController)
        MegaShowdownCompat.initialize()
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(toggleBattleCamera)
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        BadgeInventoryTracker.tick()
        BadgeCinematicManager.tick()
        BattleCameraController.tick()
        MegaShowdownCompat.tick()
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.Key) {
        if (event.action == GLFW.GLFW_PRESS && toggleBattleCamera.matches(event.key, event.scanCode)) {
            BattleCameraController.toggle()
        }
    }

    fun battleCameraKeyName(): Component = toggleBattleCamera.translatedKeyMessage
}
