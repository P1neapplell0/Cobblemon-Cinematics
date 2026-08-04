package com.p1nero.cceib.client.compat.megashowdown

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.p1nero.cceib.client.render.CinematicEffects
import com.p1nero.cceib.client.render.ProceduralDraw
import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object MegaShowdownCinematicManager {
    private const val DURATION_MS = 3_350L
    private const val ENTER_MS = 420L
    private const val EXIT_MS = 560L
    private const val DEDUPE_MS = 4_000L

    private val queue = ArrayDeque<Presentation>()
    private val recentlyQueued = mutableMapOf<String, Long>()
    private var active: Presentation? = null
    private var startedAt = 0L

    fun onBattleMessages(messages: List<Component>) {
        messages.forEach { message ->
            val contents = message.contents as? TranslatableContents ?: return@forEach
            val type = GimmickType.fromMessageKey(contents.key) ?: return@forEach
            if (!type.isEnabled()) return@forEach

            val pokemonName = contents.args.firstOrNull().toComponent()
            val pokemonId = findActivePokemonId(pokemonName.string)
            enqueue(type, pokemonName, pokemonId)
        }
    }

    fun tick() {
        recentlyQueued.entries.removeIf { elapsedSince(it.value) > DEDUPE_MS }
        if (Minecraft.getInstance().screen !is BattleGUI) {
            clear()
            return
        }

        if (active == null && queue.isNotEmpty()) startNext()
        if (active != null && elapsedMs() >= DURATION_MS) finishCurrent()
    }

    fun isPlaying(): Boolean = active != null

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if (event.screen is BattleGUI && active != null) render(event.guiGraphics)
    }

    @SubscribeEvent
    fun onScreenClosing(event: ScreenEvent.Closing) {
        if (event.screen is BattleGUI) clear()
    }

    private fun enqueue(type: GimmickType, pokemonName: Component, pokemonId: UUID?) {
        val key = "${type.name}:${pokemonId ?: pokemonName.string.lowercase()}"
        val now = System.nanoTime()
        if (recentlyQueued[key]?.let { elapsedSince(it) <= DEDUPE_MS } == true) return

        recentlyQueued[key] = now
        queue.addLast(Presentation(type, pokemonName.copy(), pokemonId))
        if (active == null) startNext()
    }

    private fun render(graphics: GuiGraphics) {
        val presentation = active ?: return
        val elapsed = elapsedMs()
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val centerX = width / 2
        val centerY = height / 2 - max(6, height / 32)
        val enter = ProceduralDraw.easeOutCubic((elapsed.toFloat() / ENTER_MS).coerceIn(0.0f, 1.0f))
        val exit = ((DURATION_MS - elapsed).toFloat() / EXIT_MS).coerceIn(0.0f, 1.0f)
        val alpha = min(enter, exit)
        val reveal = ProceduralDraw.easeOutBack(((elapsed - 180L) / 720.0f).coerceIn(0.0f, 1.0f))
        val palette = presentation.type.palette

        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, 0.0f, 880.0f)
        drawBackdrop(graphics, width, height, elapsed, alpha, palette)
        when (presentation.type) {
            GimmickType.MEGA_EVOLUTION -> drawMega(graphics, width, height, centerX, centerY, elapsed, alpha)
            GimmickType.DYNAMAX -> drawDynamax(graphics, width, height, centerX, centerY, elapsed, alpha)
            GimmickType.Z_MOVE -> drawZMove(graphics, width, height, centerX, centerY, elapsed, alpha)
            GimmickType.TERASTALIZATION -> drawTerastal(graphics, width, height, centerX, centerY, elapsed, alpha)
        }

        findPokemonEntity(presentation)?.let { entity ->
            renderPokemon(graphics, width, height, centerY, elapsed, reveal, exit, presentation.type, entity)
        }
        drawTitles(graphics, width, height, elapsed, alpha, presentation)
        drawRevealFlash(graphics, width, height, elapsed)
        graphics.pose().popPose()
    }

    private fun drawBackdrop(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        elapsed: Long,
        alpha: Float,
        palette: Palette,
    ) {
        val top = ProceduralDraw.withAlpha(ProceduralDraw.shade(palette.secondary, 0.28f), alpha * 0.98f)
        val middle = ProceduralDraw.withAlpha(ProceduralDraw.shade(palette.primary, 0.62f), alpha * 0.97f)
        val bottom = ProceduralDraw.withAlpha(0xFF030308.toInt(), alpha * 0.98f)
        ProceduralDraw.verticalGradient(graphics, 0, 0, width, height / 2, top, middle, 20)
        ProceduralDraw.verticalGradient(graphics, 0, height / 2, width, height, middle, bottom, 20)

        val scanOffset = (elapsed / 50L).toInt() % 6
        var y = scanOffset
        while (y < height) {
            graphics.fill(0, y, width, y + 1, ProceduralDraw.withAlpha(0xFF000000.toInt(), alpha * 0.13f))
            y += 6
        }
    }

    private fun drawMega(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        elapsed: Long,
        alpha: Float,
    ) {
        val frame = (elapsed / 50L).toInt()
        val radius = max(width, height)
        CinematicEffects.drawBurstRays(graphics, centerX, centerY, radius, frame, 0xFFFF4FD8.toInt(), alpha * 0.32f, 22)
        CinematicEffects.drawBurstRays(graphics, centerX, centerY, radius, -frame, 0xFF57D9FF.toInt(), alpha * 0.24f, 14)
        repeat(4) { index ->
            CinematicEffects.drawExpandingRing(
                graphics,
                centerX,
                centerY,
                (elapsed - 240L - index * 180L) / 1_150.0f,
                min(width, height) * (2 + index) / 5,
                if (index % 2 == 0) 0xFFFF5CD6.toInt() else 0xFF70E6FF.toInt(),
                alpha * 0.9f,
            )
        }

        val helixWidth = min(width, height) * 0.34
        val helixHeight = min(width, height) * 0.46
        repeat(28) { index ->
            val progress = index / 27.0
            val angle = progress * PI * 4.0 + elapsed / 360.0
            val y = centerY + ((progress - 0.5) * helixHeight).roundToInt()
            val xOffset = (sin(angle) * helixWidth * 0.42).roundToInt()
            val size = if (index % 4 == 0) 3 else 2
            ProceduralDraw.diamond(graphics, centerX + xOffset, y, size, ProceduralDraw.withAlpha(0xFFFF68DF.toInt(), alpha * 0.78f))
            ProceduralDraw.diamond(graphics, centerX - xOffset, y, size, ProceduralDraw.withAlpha(0xFF6BE7FF.toInt(), alpha * 0.78f))
        }
    }

    private fun drawDynamax(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        elapsed: Long,
        alpha: Float,
    ) {
        val frame = (elapsed / 50L).toInt()
        graphics.fill(0, 0, width, height, ProceduralDraw.withAlpha(0xFF8D092C.toInt(), alpha * 0.2f))
        repeat(11) { index ->
            val cloudX = width * index / 10 + ((frame + index * 7) % 19 - 9)
            val cloudY = height / 7 + (index % 3) * 9
            val cloudRadius = max(18, min(width, height) / 11 + index % 4 * 5)
            ProceduralDraw.circle(
                graphics,
                cloudX,
                cloudY,
                cloudRadius,
                ProceduralDraw.withAlpha(if (index % 2 == 0) 0xFF3A0717.toInt() else 0xFF6F0B2B.toInt(), alpha * 0.72f),
            )
        }

        repeat(5) { index ->
            CinematicEffects.drawExpandingRing(
                graphics,
                centerX,
                centerY + height / 5,
                (elapsed - 180L - index * 150L) / 1_180.0f,
                min(width, height) * (3 + index) / 7,
                0xFFFF315F.toInt(),
                alpha * 0.86f,
            )
        }
        repeat(7) { index ->
            val x = width * (index + 1) / 8
            val startY = height / 6 + (index % 2) * 14
            val endY = height * 3 / 5
            val bend = ((sin(elapsed / 170.0 + index) * 18.0).roundToInt())
            ProceduralDraw.sampledLine(graphics, x, startY, x + bend, (startY + endY) / 2, 2, ProceduralDraw.withAlpha(0xFFFF829E.toInt(), alpha * 0.36f), 30)
            ProceduralDraw.sampledLine(graphics, x + bend, (startY + endY) / 2, x - bend / 2, endY, 1, ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.54f), 30)
        }
    }

    private fun drawZMove(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        elapsed: Long,
        alpha: Float,
    ) {
        val frame = (elapsed / 50L).toInt()
        CinematicEffects.drawBurstRays(graphics, centerX, centerY, max(width, height), frame, 0xFFFFE34D.toInt(), alpha * 0.42f, 28)
        val pulseRadius = (min(width, height) * (0.22f + sin(elapsed / 180.0).toFloat() * 0.018f)).roundToInt()
        ProceduralDraw.ring(
            graphics,
            centerX,
            centerY,
            pulseRadius,
            (pulseRadius - 3).coerceAtLeast(0),
            ProceduralDraw.withAlpha(0xFFFFF29A.toInt(), alpha * 0.78f),
            0x00000000,
        )
        repeat(12) { index ->
            val angle = index * PI / 6.0 + elapsed / 520.0
            val orbit = pulseRadius * (1.18 + (index % 3) * 0.08)
            val x = centerX + (cos(angle) * orbit).roundToInt()
            val y = centerY + (sin(angle) * orbit * 0.74).roundToInt()
            ProceduralDraw.diamond(
                graphics,
                x,
                y,
                if (index % 3 == 0) 5 else 3,
                ProceduralDraw.withAlpha(if (index % 2 == 0) 0xFFFFD52E.toInt() else 0xFFFFFFFF.toInt(), alpha * 0.9f),
            )
        }

        val chevronWidth = min(width, height) / 5
        repeat(3) { index ->
            val y = centerY - chevronWidth / 2 + index * chevronWidth / 2
            ProceduralDraw.sampledLine(graphics, centerX - chevronWidth, y, centerX, y + chevronWidth / 3, 3, ProceduralDraw.withAlpha(0xFFFFCB22.toInt(), alpha * 0.3f), 36)
            ProceduralDraw.sampledLine(graphics, centerX, y + chevronWidth / 3, centerX + chevronWidth, y, 3, ProceduralDraw.withAlpha(0xFFFFCB22.toInt(), alpha * 0.3f), 36)
        }
    }

    private fun drawTerastal(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        elapsed: Long,
        alpha: Float,
    ) {
        val colors = intArrayOf(
            0xFFFF6BCB.toInt(),
            0xFFB77CFF.toInt(),
            0xFF6FD9FF.toInt(),
            0xFF72FFD2.toInt(),
            0xFFFFE77A.toInt(),
        )
        colors.forEachIndexed { index, color ->
            CinematicEffects.drawBurstRays(
                graphics,
                centerX,
                centerY,
                max(width, height),
                (elapsed / 50L).toInt() + index * 3,
                color,
                alpha * 0.13f,
                9,
            )
        }
        repeat(54) { index ->
            val seed = index * 1_103 + 37
            val drift = (elapsed / 45L).toInt()
            val x = Math.floorMod(seed * 17 + drift * (1 + index % 3), max(1, width))
            val y = Math.floorMod(seed * 29 - drift * (2 + index % 4), max(1, height + 30)) - 15
            val color = colors[index % colors.size]
            ProceduralDraw.diamond(
                graphics,
                x,
                y,
                1 + index % 4,
                ProceduralDraw.withAlpha(color, alpha * (0.35f + index % 5 * 0.1f)),
            )
        }
        repeat(5) { index ->
            val x = centerX + (index - 2) * max(12, min(width, height) / 15)
            val y = centerY - min(width, height) / 4 + abs(index - 2) * 9
            ProceduralDraw.diamond(
                graphics,
                x,
                y,
                max(7, min(width, height) / 34),
                ProceduralDraw.withAlpha(colors[index], alpha * 0.65f),
            )
            ProceduralDraw.sparkle(graphics, x, y, 3, ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.92f))
        }
        repeat(3) { index ->
            CinematicEffects.drawExpandingRing(
                graphics,
                centerX,
                centerY,
                (elapsed - 260L - index * 210L) / 1_180.0f,
                min(width, height) * (3 + index) / 7,
                colors[index],
                alpha * 0.78f,
            )
        }
    }

    private fun renderPokemon(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        centerY: Int,
        elapsed: Long,
        reveal: Float,
        exit: Float,
        type: GimmickType,
        entity: PokemonEntity,
    ) {
        val available = min(width, height).toFloat()
        val entityHeight = entity.bbHeight.coerceAtLeast(1.0f)
        val typeScale = if (type == GimmickType.DYNAMAX) 0.34f else 0.4f
        val pulse = if (type == GimmickType.DYNAMAX) 1.0f + sin(elapsed / 170.0).toFloat() * 0.035f else 1.0f
        val scale = (available * typeScale / max(1.0f, entityHeight * 0.62f) * reveal * exit * pulse)
            .roundToInt()
            .coerceAtLeast(1)
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            graphics,
            width / 4,
            max(0, centerY - height / 2),
            width * 3 / 4,
            min(height, centerY + height / 2),
            scale,
            -0.08f,
            (elapsed / 1_900.0).toFloat(),
            0.04f,
            entity,
        )
    }

    private fun drawTitles(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        elapsed: Long,
        alpha: Float,
        presentation: Presentation,
    ) {
        val minecraft = Minecraft.getInstance()
        val entrance = ProceduralDraw.easeOutCubic(((elapsed - 520L) / 520.0f).coerceIn(0.0f, 1.0f))
        val textAlpha = alpha * entrance
        val titleY = height - max(56, height / 5)
        val ruleWidth = (min(width * 0.68f, 380.0f) * entrance).roundToInt()
        graphics.fill(
            width / 2 - ruleWidth / 2,
            titleY - 8,
            width / 2 + ruleWidth / 2,
            titleY - 6,
            ProceduralDraw.withAlpha(presentation.type.palette.accent, textAlpha * 0.84f),
        )
        ProceduralDraw.drawCenteredScaledString(
            graphics,
            minecraft.font,
            Component.translatable(presentation.type.translationKey),
            width / 2,
            titleY,
            ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), textAlpha),
            (width * 0.82f).roundToInt(),
            1.62f,
        )
        ProceduralDraw.drawCenteredScaledString(
            graphics,
            minecraft.font,
            presentation.pokemonName,
            width / 2,
            titleY + 19,
            ProceduralDraw.withAlpha(presentation.type.palette.accent, textAlpha),
            (width * 0.72f).roundToInt(),
            1.05f,
        )
    }

    private fun drawRevealFlash(graphics: GuiGraphics, width: Int, height: Int, elapsed: Long) {
        val revealFlash = (1.0f - abs(elapsed - 520L) / 180.0f).coerceIn(0.0f, 1.0f)
        val exitFlash = ((elapsed - (DURATION_MS - 280L)) / 280.0f).coerceIn(0.0f, 1.0f)
        val flash = max(revealFlash * revealFlash * 0.52f, exitFlash * 0.72f)
        if (flash > 0.0f) {
            graphics.fill(0, 0, width, height, ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), flash))
        }
    }

    private fun findActivePokemonId(name: String): UUID? {
        val normalizedName = name.trim().lowercase()
        val candidates = CobblemonClient.battle
            ?.sides
            ?.flatMap { it.actors }
            ?.flatMap { it.activePokemon }
            ?.mapNotNull { it.battlePokemon }
            .orEmpty()
        return candidates.firstOrNull { candidate ->
            val candidateName = candidate.displayName.string.trim().lowercase()
            candidateName == normalizedName || normalizedName.contains(candidateName)
        }?.uuid
    }

    private fun findPokemonEntity(presentation: Presentation): PokemonEntity? {
        val entities = Minecraft.getInstance().level
            ?.entitiesForRendering()
            ?.filterIsInstance<PokemonEntity>()
            .orEmpty()
        presentation.pokemonId?.let { pokemonId ->
            entities.firstOrNull { it.pokemon.uuid == pokemonId }?.let { return it }
        }
        val normalizedName = presentation.pokemonName.string.trim().lowercase()
        return entities.firstOrNull { entity ->
            val entityName = entity.name.string.trim().lowercase()
            entityName == normalizedName || normalizedName.contains(entityName)
        }
    }

    private fun Any?.toComponent(): Component = when (this) {
        is Component -> copy()
        null -> Component.empty()
        else -> Component.literal(toString())
    }

    private fun startNext() {
        active = queue.pollFirst() ?: return
        startedAt = System.nanoTime()
    }

    private fun finishCurrent() {
        active = null
        if (queue.isNotEmpty()) startNext()
    }

    private fun clear() {
        queue.clear()
        active = null
        recentlyQueued.clear()
    }

    private fun elapsedMs(): Long = (System.nanoTime() - startedAt) / 1_000_000L

    private fun elapsedSince(timestamp: Long): Long = (System.nanoTime() - timestamp) / 1_000_000L

    private data class Presentation(
        val type: GimmickType,
        val pokemonName: Component,
        val pokemonId: UUID?,
    )

    private data class Palette(val primary: Int, val secondary: Int, val accent: Int)

    private enum class GimmickType(
        val translationKey: String,
        val palette: Palette,
    ) {
        MEGA_EVOLUTION(
            "cinematic.cobblemoncinematics.mega_evolution",
            Palette(0xFF8C2BCB.toInt(), 0xFF142A68.toInt(), 0xFFFF68DF.toInt()),
        ),
        DYNAMAX(
            "cinematic.cobblemoncinematics.dynamax",
            Palette(0xFFB3133B.toInt(), 0xFF2A0714.toInt(), 0xFFFF668A.toInt()),
        ),
        Z_MOVE(
            "cinematic.cobblemoncinematics.z_move",
            Palette(0xFFD69B05.toInt(), 0xFF2F2305.toInt(), 0xFFFFE45C.toInt()),
        ),
        TERASTALIZATION(
            "cinematic.cobblemoncinematics.terastalization",
            Palette(0xFFB64CC8.toInt(), 0xFF164A67.toInt(), 0xFF8FF5FF.toInt()),
        ),
        ;

        fun isEnabled(): Boolean = when (this) {
            MEGA_EVOLUTION -> ClientConfig.megaEvolutionCinematic.get()
            DYNAMAX -> ClientConfig.dynamaxCinematic.get()
            Z_MOVE -> ClientConfig.zMoveCinematic.get()
            TERASTALIZATION -> ClientConfig.terastalizationCinematic.get()
        }

        companion object {
            fun fromMessageKey(key: String): GimmickType? = when (key) {
                "cobblemon.battle.mega" -> MEGA_EVOLUTION
                "cobblemon.battle.start.dynamax" -> DYNAMAX
                "cobblemon.battle.zpower" -> Z_MOVE
                "cobblemon.battle.terastallize" -> TERASTALIZATION
                else -> null
            }
        }
    }
}
