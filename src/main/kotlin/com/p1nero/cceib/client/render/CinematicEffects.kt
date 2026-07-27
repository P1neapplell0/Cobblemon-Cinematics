package com.p1nero.cceib.client.render

import net.minecraft.client.gui.GuiGraphics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Shared, texture-free effects quantized to 20 FPS to retain a hand-authored frame-sequence cadence. */
object CinematicEffects {
    private const val FRAME_MS = 50L

    fun drawBattleBackdrop(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        elapsed: Long,
        alpha: Float,
        primary: Int,
        secondary: Int,
        accent: Int,
    ) {
        val frame = (elapsed / FRAME_MS).toInt()
        val centerY = height / 2
        val top = ProceduralDraw.withAlpha(ProceduralDraw.shade(secondary, 0.48f), alpha)
        val middle = ProceduralDraw.withAlpha(ProceduralDraw.lerpColor(primary, accent, 0.24f), alpha)
        val bottom = ProceduralDraw.withAlpha(ProceduralDraw.shade(secondary, 0.34f), alpha)

        ProceduralDraw.verticalGradient(graphics, 0, 0, width, centerY, top, middle, 20)
        ProceduralDraw.verticalGradient(graphics, 0, centerY, width, height, middle, bottom, 20)
        drawHorizontalGlow(graphics, width, centerY, max(18, height / 7), accent, alpha * 0.26f)
        drawHorizontalStreaks(graphics, width, height, frame, primary, accent, alpha)
        drawScanTexture(graphics, width, height, alpha * 0.12f)
        drawVignette(graphics, width, height, alpha * 0.62f)
    }

    fun drawAwardBackdrop(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        elapsed: Long,
        alpha: Float,
        accent: Int,
    ) {
        val frame = (elapsed / FRAME_MS).toInt()
        val centerX = width / 2
        val centerY = height / 2 - 12
        val darkGold = ProceduralDraw.withAlpha(0xFF321604.toInt(), alpha * 0.96f)
        val warmGold = ProceduralDraw.withAlpha(0xFFD58A05.toInt(), alpha * 0.94f)
        val brightGold = ProceduralDraw.withAlpha(0xFFFFD84A.toInt(), alpha * 0.94f)

        ProceduralDraw.verticalGradient(graphics, 0, 0, width, height / 2, darkGold, warmGold, 24)
        ProceduralDraw.verticalGradient(graphics, 0, height / 2, width, height, warmGold, brightGold, 24)

        val glowRadius = max(54, minOf(width, height) / 3)
        for (layer in 5 downTo 1) {
            val radius = glowRadius * layer / 5
            ProceduralDraw.circle(
                graphics,
                centerX,
                centerY,
                radius,
                ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), alpha * (0.018f + (6 - layer) * 0.012f)),
            )
        }

        drawBurstRays(graphics, centerX, centerY, max(width, height), frame, accent, alpha * 0.2f, 18)
        drawVerticalBeams(graphics, width, height, frame, alpha)
        drawRisingParticles(graphics, width, height, frame, accent, alpha)
        drawVignette(graphics, width, height, alpha * 0.54f)
    }

    fun drawBurstRays(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        radius: Int,
        frame: Int,
        color: Int,
        alpha: Float,
        rayCount: Int = 16,
    ) {
        repeat(rayCount) { index ->
            val wobble = ((frame + index * 3) % 9 - 4) * 0.0025
            val angle = index * (PI * 2.0 / rayCount) + wobble
            val innerRadius = 30 + range(index * 71 + 11, 18)
            val length = radius * (78 + range(index * 43 + 7, 28)) / 100
            val startX = centerX + (cos(angle) * innerRadius).roundToInt()
            val startY = centerY + (sin(angle) * innerRadius).roundToInt()
            val endX = centerX + (cos(angle) * length).roundToInt()
            val endY = centerY + (sin(angle) * length).roundToInt()
            val rayAlpha = alpha * (0.45f + range(index * 31 + frame, 45) / 100.0f)
            ProceduralDraw.sampledLine(
                graphics,
                startX,
                startY,
                endX,
                endY,
                if (index % 5 == 0) 3 else 1,
                ProceduralDraw.withAlpha(color, rayAlpha),
                72,
            )
        }
    }

    fun drawExpandingRing(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        progress: Float,
        maxRadius: Int,
        color: Int,
        alpha: Float,
    ) {
        if (progress !in 0.0f..1.0f) return
        val radius = (maxRadius * ProceduralDraw.easeOutCubic(progress)).roundToInt().coerceAtLeast(2)
        val thickness = max(1, 5 - (progress * 4.0f).roundToInt())
        ProceduralDraw.ring(
            graphics,
            centerX,
            centerY,
            radius,
            (radius - thickness).coerceAtLeast(0),
            ProceduralDraw.withAlpha(color, alpha * (1.0f - progress)),
            0x00000000,
        )
    }

    private fun drawHorizontalGlow(
        graphics: GuiGraphics,
        width: Int,
        centerY: Int,
        radius: Int,
        color: Int,
        alpha: Float,
    ) {
        val bands = 12
        repeat(bands) { index ->
            val inner = radius * index / bands
            val outer = radius * (index + 1) / bands
            val falloff = 1.0f - index.toFloat() / bands
            val bandColor = ProceduralDraw.withAlpha(color, alpha * falloff * falloff)
            graphics.fill(0, centerY - outer, width, centerY - inner, bandColor)
            graphics.fill(0, centerY + inner, width, centerY + outer, bandColor)
        }
    }

    private fun drawHorizontalStreaks(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        frame: Int,
        primary: Int,
        accent: Int,
        alpha: Float,
    ) {
        repeat(46) { index ->
            val seed = index * 1_741 + 97
            val length = max(18, width * (8 + range(seed + 5, 27)) / 100)
            val speed = 7 + range(seed + 9, 17)
            val cycle = width + length * 2
            val travel = Math.floorMod(range(seed + 13, cycle) + frame * speed, cycle) - length
            val direction = if (index % 6 == 0) -1 else 1
            val x = if (direction > 0) travel else width - travel - length
            val baseY = range(seed + 17, max(1, height))
            val jitterY = range(seed + frame * 29, 5) - 2
            val y = baseY + jitterY
            val thickness = if (index % 11 == 0) 3 else if (index % 4 == 0) 2 else 1
            val brightness = 0.16f + range(seed + frame * 7, 56) / 100.0f
            val lineColor = if (index % 5 == 0) accent else ProceduralDraw.lerpColor(primary, 0xFFFFFFFF.toInt(), 0.72f)

            drawSegmentedStreak(graphics, width, height, x, y, length, thickness, direction, lineColor, alpha * brightness)
            if (index % 9 == 0) {
                drawSegmentedStreak(
                    graphics,
                    width,
                    height,
                    x - direction * 5,
                    y + 2,
                    length,
                    1,
                    direction,
                    accent,
                    alpha * 0.18f,
                )
            }
        }
    }

    private fun drawSegmentedStreak(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        length: Int,
        thickness: Int,
        direction: Int,
        color: Int,
        alpha: Float,
    ) {
        repeat(5) { segment ->
            val segmentStart = x + length * segment / 5
            val segmentEnd = x + length * (segment + 1) / 5
            val intensity = if (direction > 0) (segment + 1) / 5.0f else (5 - segment) / 5.0f
            fillClipped(
                graphics,
                width,
                height,
                segmentStart,
                y,
                segmentEnd + 1,
                y + thickness,
                ProceduralDraw.withAlpha(color, alpha * intensity * intensity),
            )
        }
    }

    private fun drawVerticalBeams(graphics: GuiGraphics, width: Int, height: Int, frame: Int, alpha: Float) {
        repeat(28) { index ->
            val seed = index * 2_137 + 41
            val length = max(20, height * (12 + range(seed + 3, 42)) / 100)
            val cycle = height + length * 2
            val y = Math.floorMod(range(seed + 7, cycle) - frame * (4 + range(seed + 9, 9)), cycle) - length
            val x = range(seed + 11, max(1, width))
            val strength = 0.12f + range(seed + frame * 3, 46) / 100.0f
            fillClipped(
                graphics,
                width,
                height,
                x - 2,
                y,
                x + 3,
                y + length,
                ProceduralDraw.withAlpha(0xFFFFE477.toInt(), alpha * strength * 0.22f),
            )
            fillClipped(
                graphics,
                width,
                height,
                x,
                y,
                x + 1,
                y + length,
                ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), alpha * strength),
            )
        }
    }

    private fun drawRisingParticles(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        frame: Int,
        accent: Int,
        alpha: Float,
    ) {
        repeat(92) { index ->
            val seed = index * 3_571 + 73
            val x = range(seed + 3, max(1, width)) + range(seed + frame, 5) - 2
            val cycle = height + 36
            val y = Math.floorMod(range(seed + 5, cycle) - frame * (1 + range(seed + 7, 4)), cycle) - 12
            val pulse = 0.35f + range(seed + frame * 17, 66) / 100.0f
            val color = if (index % 8 == 0) accent else 0xFFFFFFFF.toInt()
            when {
                index % 13 == 0 -> ProceduralDraw.sparkle(
                    graphics,
                    x,
                    y,
                    2 + range(seed + 9, 2),
                    ProceduralDraw.withAlpha(color, alpha * pulse),
                )

                index % 4 == 0 -> ProceduralDraw.diamond(
                    graphics,
                    x,
                    y,
                    1 + range(seed + 11, 2),
                    ProceduralDraw.withAlpha(color, alpha * pulse * 0.82f),
                )

                else -> graphics.fill(x, y, x + 1, y + 1, ProceduralDraw.withAlpha(color, alpha * pulse))
            }
        }
    }

    private fun drawScanTexture(graphics: GuiGraphics, width: Int, height: Int, alpha: Float) {
        var y = 1
        while (y < height) {
            graphics.fill(0, y, width, y + 1, ProceduralDraw.withAlpha(0xFF000000.toInt(), alpha))
            y += 4
        }
    }

    private fun drawVignette(graphics: GuiGraphics, width: Int, height: Int, alpha: Float) {
        val edge = max(12, height / 5)
        ProceduralDraw.verticalGradient(
            graphics,
            0,
            0,
            width,
            edge,
            ProceduralDraw.withAlpha(0xFF000000.toInt(), alpha),
            0x00000000,
            12,
        )
        ProceduralDraw.verticalGradient(
            graphics,
            0,
            height - edge,
            width,
            height,
            0x00000000,
            ProceduralDraw.withAlpha(0xFF000000.toInt(), alpha),
            12,
        )
    }

    private fun fillClipped(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
    ) {
        val left = x1.coerceIn(0, width)
        val right = x2.coerceIn(0, width)
        val top = y1.coerceIn(0, height)
        val bottom = y2.coerceIn(0, height)
        if (left < right && top < bottom) {
            graphics.fill(left, top, right, bottom, color)
        }
    }

    private fun range(seed: Int, bound: Int): Int {
        if (bound <= 1) return 0
        return (mix(seed) and Int.MAX_VALUE) % bound
    }

    private fun mix(seed: Int): Int {
        var value = seed
        value = (value xor (value ushr 16)) * -2_048_143_789
        value = (value xor (value ushr 13)) * -1_028_477_389
        return value xor (value ushr 16)
    }
}
