package com.p1nero.cceib.client.render

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ProceduralDraw {
    fun withAlpha(color: Int, alpha: Float): Int {
        val sourceAlpha = color ushr 24 and 0xFF
        val resolvedAlpha = (sourceAlpha * Mth.clamp(alpha, 0.0f, 1.0f)).toInt()
        return color and 0x00FFFFFF or (resolvedAlpha shl 24)
    }

    fun circle(graphics: GuiGraphics, centerX: Int, centerY: Int, radius: Int, color: Int) {
        if (radius <= 0 || color ushr 24 == 0) return
        for (dy in -radius..radius) {
            val halfWidth = sqrt((radius * radius - dy * dy).toDouble()).toInt()
            graphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color)
        }
    }

    fun diamond(graphics: GuiGraphics, centerX: Int, centerY: Int, radius: Int, color: Int) {
        if (radius <= 0 || color ushr 24 == 0) return
        for (dy in -radius..radius) {
            val halfWidth = radius - abs(dy)
            graphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color)
        }
    }

    fun ring(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        outerRadius: Int,
        innerRadius: Int,
        outerColor: Int,
        innerColor: Int,
    ) {
        if (outerRadius <= 0 || outerColor ushr 24 == 0 && innerColor ushr 24 == 0) return
        for (dy in -outerRadius..outerRadius) {
            val outerHalfWidth = sqrt((outerRadius * outerRadius - dy * dy).toDouble()).toInt()
            if (dy in -innerRadius..innerRadius) {
                val innerHalfWidth = sqrt((innerRadius * innerRadius - dy * dy).toDouble()).toInt()
                graphics.fill(
                    centerX - outerHalfWidth,
                    centerY + dy,
                    centerX - innerHalfWidth,
                    centerY + dy + 1,
                    outerColor,
                )
                graphics.fill(
                    centerX + innerHalfWidth + 1,
                    centerY + dy,
                    centerX + outerHalfWidth + 1,
                    centerY + dy + 1,
                    outerColor,
                )
            } else {
                graphics.fill(
                    centerX - outerHalfWidth,
                    centerY + dy,
                    centerX + outerHalfWidth + 1,
                    centerY + dy + 1,
                    outerColor,
                )
            }
        }
        if (innerColor ushr 24 != 0) {
            circle(graphics, centerX, centerY, innerRadius, innerColor)
        }
    }

    fun easeOutCubic(value: Float): Float {
        val inverse = 1.0f - Mth.clamp(value, 0.0f, 1.0f)
        return 1.0f - inverse * inverse * inverse
    }

    fun easeOutBack(value: Float): Float {
        val x = Mth.clamp(value, 0.0f, 1.0f) - 1.0f
        return 1.0f + 2.70158f * x * x * x + 1.70158f * x * x
    }

    fun easeInOutCubic(value: Float): Float {
        val x = Mth.clamp(value, 0.0f, 1.0f)
        return if (x < 0.5f) 4.0f * x * x * x else 1.0f - (-2.0f * x + 2.0f).let { it * it * it } / 2.0f
    }

    fun lerpColor(from: Int, to: Int, progress: Float): Int {
        val amount = Mth.clamp(progress, 0.0f, 1.0f)
        val alpha = Mth.lerp(amount, (from ushr 24 and 0xFF).toFloat(), (to ushr 24 and 0xFF).toFloat()).roundToInt()
        val red = Mth.lerp(amount, (from ushr 16 and 0xFF).toFloat(), (to ushr 16 and 0xFF).toFloat()).roundToInt()
        val green = Mth.lerp(amount, (from ushr 8 and 0xFF).toFloat(), (to ushr 8 and 0xFF).toFloat()).roundToInt()
        val blue = Mth.lerp(amount, (from and 0xFF).toFloat(), (to and 0xFF).toFloat()).roundToInt()
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }

    fun shade(color: Int, factor: Float): Int {
        val resolved = factor.coerceAtLeast(0.0f)
        val alpha = color ushr 24 and 0xFF
        val red = ((color ushr 16 and 0xFF) * resolved).roundToInt().coerceAtMost(255)
        val green = ((color ushr 8 and 0xFF) * resolved).roundToInt().coerceAtMost(255)
        val blue = ((color and 0xFF) * resolved).roundToInt().coerceAtMost(255)
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }

    fun verticalGradient(
        graphics: GuiGraphics,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        topColor: Int,
        bottomColor: Int,
        steps: Int = 24,
    ) {
        val height = y2 - y1
        if (height <= 0) return
        val bands = steps.coerceIn(1, height)
        repeat(bands) { index ->
            val startY = y1 + height * index / bands
            val endY = y1 + height * (index + 1) / bands
            val progress = if (bands == 1) 0.0f else index.toFloat() / (bands - 1)
            graphics.fill(x1, startY, x2, endY, lerpColor(topColor, bottomColor, progress))
        }
    }

    fun sampledLine(
        graphics: GuiGraphics,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        thickness: Int,
        color: Int,
        samples: Int = 48,
    ) {
        if (color ushr 24 == 0) return
        val distance = max(abs(endX - startX), abs(endY - startY))
        val count = samples.coerceIn(1, max(1, distance))
        val half = max(1, thickness) / 2
        for (index in 0..count) {
            val progress = index.toFloat() / count
            val x = Mth.lerp(progress, startX.toFloat(), endX.toFloat()).roundToInt()
            val y = Mth.lerp(progress, startY.toFloat(), endY.toFloat()).roundToInt()
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color)
        }
    }

    fun sparkle(graphics: GuiGraphics, centerX: Int, centerY: Int, radius: Int, color: Int) {
        if (radius <= 0 || color ushr 24 == 0) return
        val core = withAlpha(0xFFFFFFFF.toInt(), (color ushr 24 and 0xFF) / 255.0f)
        graphics.fill(centerX - radius * 2, centerY, centerX + radius * 2 + 1, centerY + 1, color)
        graphics.fill(centerX, centerY - radius * 2, centerX + 1, centerY + radius * 2 + 1, color)
        diamond(graphics, centerX, centerY, max(1, radius / 2), core)
    }

    fun drawCenteredScaledString(
        graphics: GuiGraphics,
        font: Font,
        text: Component,
        centerX: Int,
        y: Int,
        color: Int,
        maxWidth: Int,
        preferredScale: Float,
    ) {
        // Font treats colors with an almost-zero alpha as legacy opaque RGB colors.
        if (color ushr 24 <= 3) return
        val textWidth = max(1, font.width(text))
        val scale = minOf(preferredScale, maxWidth.toFloat() / textWidth).coerceAtLeast(0.5f)
        graphics.pose().pushPose()
        graphics.pose().translate(centerX.toFloat(), y.toFloat(), 0.0f)
        graphics.pose().scale(scale, scale, 1.0f)
        graphics.drawString(font, text, -textWidth / 2, 0, color, true)
        graphics.pose().popPose()
    }
}
