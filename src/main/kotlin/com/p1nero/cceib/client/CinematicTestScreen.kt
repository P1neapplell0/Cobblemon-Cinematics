package com.p1nero.cceib.client

import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class CinematicTestScreen : Screen(Component.literal("Cobblemon Cinematics Test")) {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = ClientConfig.pauseDuringCinematics.get()

    override fun onClose() {
        CinematicTestController.cancel()
        super.onClose()
    }
}
