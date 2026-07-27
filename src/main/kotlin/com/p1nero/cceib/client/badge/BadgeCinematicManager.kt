package com.p1nero.cceib.client.badge

import com.mojang.math.Axis
import com.p1nero.cceib.client.render.CinematicEffects
import com.p1nero.cceib.client.render.ProceduralDraw
import com.p1nero.cceib.config.ClientConfig
import com.p1nero.cceib.registry.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object BadgeCinematicManager {
    private const val DURATION_MS = 5_200L
    private const val ENTER_MS = 720L
    private const val EXIT_MS = 820L

    private val queue = ArrayDeque<BadgePresentation>()
    private var active: BadgePresentation? = null
    private var startedAt = 0L

    fun enqueue(id: ResourceLocation, name: Component, stack: ItemStack) {
        queue.addLast(BadgePresentation(id, name, stack.copyWithCount(1)))
        if (active == null) {
            startNext()
        }
    }

    fun tick() {
        if (!ClientConfig.badgeCinematics.get()) {
            stopAll()
            return
        }

        if (active == null && queue.isNotEmpty()) {
            startNext()
        }
        if (active == null) return

        val minecraft = Minecraft.getInstance()
        if (minecraft.screen == null) {
            minecraft.setScreen(BadgeCinematicScreen())
        }
        if (elapsedMs() >= DURATION_MS) {
            finishCurrent()
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if (active != null && event.screen !is BadgeCinematicScreen) {
            render(event.guiGraphics)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHudRender(event: RenderGuiEvent.Post) {
        if (active != null && Minecraft.getInstance().screen == null) {
            render(event.guiGraphics)
        }
    }

    fun render(graphics: GuiGraphics) {
        val presentation = active ?: return
        val minecraft = Minecraft.getInstance()
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val elapsed = elapsedMs()
        val visibility = when {
            elapsed < ENTER_MS -> ProceduralDraw.easeOutCubic(elapsed.toFloat() / ENTER_MS)
            elapsed > DURATION_MS - EXIT_MS -> (DURATION_MS - elapsed).toFloat() / EXIT_MS
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)
        val reveal = ((elapsed - 180L) / 820.0f).coerceIn(0.0f, 1.0f)
        val scale = ProceduralDraw.easeOutBack(reveal).coerceAtLeast(0.0f) *
            (min(width, height) / 290.0f).coerceIn(0.82f, 1.48f)
        val palette = Palette.forId(presentation.id)
        val centerX = width / 2
        val centerY = height / 2 - maxOf(10, height / 28)

        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, 0.0f, 900.0f)
        CinematicEffects.drawAwardBackdrop(graphics, width, height, elapsed, visibility, palette.accent)
        drawHalo(graphics, centerX, centerY, scale, visibility, elapsed, palette)
        drawBadgeItem(graphics, centerX, centerY, scale, visibility, reveal, elapsed, presentation.stack)

        val textEntrance = ProceduralDraw.easeOutCubic(((elapsed - 720L) / 520.0f).coerceIn(0.0f, 1.0f))
        val textAlpha = visibility * textEntrance
        val titleY = centerY + (60.0f * scale.coerceAtLeast(0.82f)).roundToInt() + 10
        val ruleWidth = (min(width * 0.62f, 310.0f) * textEntrance).roundToInt()
        graphics.fill(
            centerX - ruleWidth / 2,
            titleY - 7,
            centerX + ruleWidth / 2,
            titleY - 6,
            ProceduralDraw.withAlpha(palette.accent, textAlpha * 0.72f),
        )
        ProceduralDraw.drawCenteredScaledString(
            graphics,
            minecraft.font,
            Component.translatable("cinematic.cobblemoncinematics.badge_obtained"),
            centerX,
            titleY,
            ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), textAlpha),
            (width * 0.78f).roundToInt(),
            1.48f,
        )
        ProceduralDraw.drawCenteredScaledString(
            graphics,
            minecraft.font,
            presentation.name,
            centerX,
            titleY + 18,
            ProceduralDraw.withAlpha(0xFFFFEBA3.toInt(), textAlpha),
            (width * 0.72f).roundToInt(),
            1.08f,
        )

        val revealFlash = (1.0f - abs(elapsed - 690L) / 190.0f).coerceIn(0.0f, 1.0f)
        if (revealFlash > 0.0f) {
            graphics.fill(
                0,
                0,
                width,
                height,
                ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), revealFlash * revealFlash * 0.46f),
            )
        }
        graphics.pose().popPose()
    }

    private fun drawHalo(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        scale: Float,
        alpha: Float,
        elapsed: Long,
        palette: Palette,
    ) {
        val haloScale = scale.coerceAtLeast(0.72f)
        val outerRadius = (68 * haloScale).roundToInt()
        val frame = (elapsed / 50L).toInt()
        ProceduralDraw.ring(
            graphics,
            centerX,
            centerY,
            outerRadius,
            (outerRadius - 2).coerceAtLeast(0),
            ProceduralDraw.withAlpha(0xFFFFE477.toInt(), alpha * 0.62f),
            0x00000000,
        )
        ProceduralDraw.ring(
            graphics,
            centerX,
            centerY,
            (58 * haloScale).roundToInt(),
            (56 * haloScale).roundToInt(),
            ProceduralDraw.withAlpha(palette.accent, alpha * 0.52f),
            0x00000000,
        )

        val rotation = elapsed / 820.0 * PI
        repeat(12) { index ->
            val angle = rotation + index * PI / 6.0
            val orbit = (74 + if (index % 3 == 0) 8 else 0) * haloScale
            val x = centerX + (cos(angle) * orbit).roundToInt()
            val y = centerY + (sin(angle) * orbit * 0.72).roundToInt()
            ProceduralDraw.sparkle(
                graphics,
                x,
                y,
                if ((frame + index) % 5 == 0) 4 else 2,
                ProceduralDraw.withAlpha(if (index % 4 == 0) palette.accent else 0xFFFFFFFF.toInt(), alpha * 0.9f),
            )
        }

        CinematicEffects.drawExpandingRing(
            graphics,
            centerX,
            centerY,
            (elapsed - 620L) / 850.0f,
            (112 * haloScale).roundToInt(),
            0xFFFFFFFF.toInt(),
            alpha * 0.86f,
        )
        CinematicEffects.drawExpandingRing(
            graphics,
            centerX,
            centerY,
            (elapsed - 1_420L) / 1_050.0f,
            (138 * haloScale).roundToInt(),
            palette.accent,
            alpha * 0.58f,
        )
    }

    private fun drawBadgeItem(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        scale: Float,
        alpha: Float,
        reveal: Float,
        elapsed: Long,
        stack: ItemStack,
    ) {
        graphics.pose().pushPose()
        graphics.pose().translate(centerX.toFloat(), centerY.toFloat(), 0.0f)
        graphics.pose().scale(scale * 5.2f, scale * 5.2f, 1.0f)
        val tilt = (1.0f - reveal) * -16.0f + sin(elapsed / 420.0).toFloat() * 0.8f
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(tilt))
        graphics.setColor(1.0f, 1.0f, 1.0f, alpha)
        graphics.renderItem(stack, -8, -8)
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f)
        graphics.pose().popPose()
    }

    private fun startNext() {
        active = queue.pollFirst() ?: return
        startedAt = System.nanoTime()
        if (ClientConfig.badgeSound.get()) {
            Minecraft.getInstance().soundManager.play(
                SimpleSoundInstance.forUI(ModSounds.BADGE_GET.get(), 1.0f),
            )
        }
    }

    private fun finishCurrent() {
        active = null
        if (queue.isNotEmpty()) {
            startNext()
            return
        }

        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is BadgeCinematicScreen) {
            minecraft.setScreen(null)
        }
    }

    private fun stopAll() {
        queue.clear()
        active = null
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is BadgeCinematicScreen) {
            minecraft.setScreen(null)
        }
    }

    private fun elapsedMs(): Long = (System.nanoTime() - startedAt) / 1_000_000L

    private data class BadgePresentation(val id: ResourceLocation, val name: Component, val stack: ItemStack)

    private data class Palette(val primary: Int, val secondary: Int, val accent: Int) {
        companion object {
            private val palettes = listOf(
                Palette(0xFFE33B4E.toInt(), 0xFF1976A3.toInt(), 0xFFFFD166.toInt()),
                Palette(0xFF2BAE66.toInt(), 0xFFE65336.toInt(), 0xFFF2E8CF.toInt()),
                Palette(0xFF168AAD.toInt(), 0xFFD1495B.toInt(), 0xFFFFC857.toInt()),
                Palette(0xFFE07A2D.toInt(), 0xFF347A73.toInt(), 0xFFFFFFFF.toInt()),
            )

            fun forId(id: ResourceLocation): Palette = palettes[Mth.positiveModulo(id.hashCode(), palettes.size)]
        }
    }
}
