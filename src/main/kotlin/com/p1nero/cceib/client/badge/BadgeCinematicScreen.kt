package com.p1nero.cceib.client.badge

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.p1nero.cceib.config.ClientConfig

class BadgeCinematicScreen : Screen(Component.translatable("screen.cobblemoncinematics.badge_cinematic")) {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        BadgeCinematicManager.render(graphics)
    }

    override fun isPauseScreen(): Boolean = ClientConfig.pauseDuringCinematics.get()

    override fun shouldCloseOnEsc(): Boolean = false

    override fun onClose() = Unit
}
