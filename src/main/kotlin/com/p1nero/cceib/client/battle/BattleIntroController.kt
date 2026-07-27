package com.p1nero.cceib.client.battle

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattleActor
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.p1nero.cceib.client.render.CinematicEffects
import com.p1nero.cceib.client.render.ProceduralDraw
import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent
import java.util.UUID
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object BattleIntroController {
    private const val DURATION_MS = 2_500L
    private val delayedSounds = mutableListOf<SoundInstance>()
    private var active: Intro? = null
    private var lastBattleId: UUID? = null

    @SubscribeEvent
    fun onScreenOpening(event: ScreenEvent.Opening) {
        if (event.newScreen !is BattleGUI || !ClientConfig.battleIntros.get()) return

        val battle = CobblemonClient.battle ?: return
        if (battle.battleId == lastBattleId || battle.isPvW) return
        lastBattleId = battle.battleId

        findOpponent(battle)?.let { opponent ->
            val opponentName = findEntity(opponent.uuid)?.displayName?.copy()
                ?: opponent.displayName.copy()
            active = Intro(
                opponent.uuid,
                opponentName,
                selectPalette(opponentName.string),
                System.nanoTime(),
            )
            delayedSounds.clear()
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if (event.screen !is BattleGUI) return
        val intro = active ?: return
        val elapsed = intro.elapsedMs()
        if (elapsed >= DURATION_MS) {
            finish()
            return
        }
        render(event.guiGraphics, intro, elapsed)
    }

    @SubscribeEvent
    fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.screen is BattleGUI && active != null) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onKeyPressed(event: ScreenEvent.KeyPressed.Pre) {
        if (event.screen is BattleGUI && active != null) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onScreenClosing(event: ScreenEvent.Closing) {
        if (event.screen is BattleGUI && active != null) {
            finish()
        }
    }

    @SubscribeEvent
    fun onSound(event: PlaySoundEvent) {
        if (active == null || !ClientConfig.delayBattleIntroSounds.get()) return
        val path = event.originalSound.location.path.lowercase()
        if (SOUND_MARKERS.any(path::contains)) {
            delayedSounds += event.originalSound
            event.sound = null
        }
    }

    private fun render(graphics: GuiGraphics, intro: Intro, elapsed: Long) {
        val minecraft = Minecraft.getInstance()
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val enter = ProceduralDraw.easeOutCubic((elapsed / 560.0f).coerceAtMost(1.0f))
        val exit = ((DURATION_MS - elapsed) / 500.0f).coerceIn(0.0f, 1.0f)
        val alpha = min(enter, exit)
        val palette = intro.palette
        val dividerY = height / 2

        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, 0.0f, 700.0f)
        CinematicEffects.drawBattleBackdrop(
            graphics,
            width,
            height,
            elapsed,
            alpha,
            palette.primary,
            palette.secondary,
            palette.accent,
        )

        val bandEntrance = ProceduralDraw.easeOutCubic((elapsed / 420.0f).coerceAtMost(1.0f))
        val bandWidth = (width * bandEntrance).roundToInt()
        graphics.fill(0, dividerY - 4, bandWidth, dividerY + 4, ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), alpha * 0.88f))
        graphics.fill(0, dividerY - 2, bandWidth, dividerY + 2, ProceduralDraw.withAlpha(palette.accent, alpha))
        graphics.fill(
            0,
            dividerY - 36,
            (width * 0.53f * bandEntrance).roundToInt(),
            dividerY + 35,
            ProceduralDraw.withAlpha(0xFF05070B.toInt(), alpha * 0.38f),
        )

        val textEntrance = ProceduralDraw.easeOutCubic(((elapsed - 120L) / 520.0f).coerceIn(0.0f, 1.0f))
        val panelOffset = ((1.0f - textEntrance) * -width * 0.55f).toInt()
        val textX = width / 4 + panelOffset
        ProceduralDraw.drawCenteredScaledString(
            graphics,
            minecraft.font,
            intro.name,
            textX,
            dividerY - 17,
            ProceduralDraw.withAlpha(palette.accent, alpha * textEntrance),
            (width * 0.42f).roundToInt(),
            1.54f,
        )

        findEntity(intro.opponentId)?.let { opponent ->
            if (opponent is NPCEntity && !intro.ballAnimationStarted) {
                opponent.playAnimation(
                    NPCEntity.SEND_OUT_ANIMATION,
                    listOf("v.actioning_pokemon_ball='cobblemon:poke_ball';"),
                )
                intro.ballAnimationStarted = true
            }

            val trainerEntrance = ProceduralDraw.easeOutBack(((elapsed - 80L) / 680.0f).coerceIn(0.0f, 1.0f))
            val slide = ((1.0f - trainerEntrance) * width * 0.56f).roundToInt()
            val poseBeat = if (elapsed in 1_000L..1_800L) {
                sin((elapsed - 1_000L) / 800.0 * PI).toFloat()
            } else {
                0.0f
            }
            CinematicEffects.drawExpandingRing(
                graphics,
                width * 3 / 4,
                dividerY,
                (elapsed - 180L) / 720.0f,
                min(width, height) / 2,
                palette.accent,
                alpha * 0.64f,
            )
            renderTrainer(graphics, width, height, slide, poseBeat, elapsed, opponent)
        }

        val flashAlpha = when {
            elapsed < 420L && (elapsed / 50L).toInt() % 4 < 2 -> (1.0f - elapsed / 520.0f) * 0.9f
            elapsed > DURATION_MS - 500L -> 1.0f - (DURATION_MS - elapsed) / 500.0f
            else -> 0.0f
        }
        if (flashAlpha > 0.0f) {
            graphics.fill(0, 0, width, height, ProceduralDraw.withAlpha(0xFFFFFFFF.toInt(), flashAlpha))
        }
        graphics.pose().popPose()
    }

    private fun findOpponent(battle: ClientBattle): ClientBattleActor? {
        val playerId = Minecraft.getInstance().player?.uuid ?: return null
        val playerSide = battle.sides.firstOrNull { side -> side.actors.any { it.uuid == playerId } }
        return battle.sides.firstOrNull { it !== playerSide }?.actors?.firstOrNull()
    }

    private fun findEntity(uuid: UUID): LivingEntity? = Minecraft.getInstance().level
        ?.entitiesForRendering()
        ?.firstOrNull { it.uuid == uuid && it is LivingEntity } as? LivingEntity

    private fun renderTrainer(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        slide: Int,
        poseBeat: Float,
        elapsed: Long,
        trainer: LivingEntity,
    ) {
        val originalHand = trainer.mainHandItem
        val originalAttackAnim = trainer.attackAnim
        val originalOldAttackAnim = trainer.oAttackAnim
        try {
            if (trainer !is NPCEntity && elapsed <= 1_450L) {
                trainer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(POKE_BALL_ITEM))
                trainer.attackAnim = poseBeat
                trainer.oAttackAnim = poseBeat
            }
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics,
                width / 2 + slide,
                0,
                width + slide,
                height,
                (min(width, height) * (0.47f + poseBeat * 0.04f)).roundToInt(),
                -0.04f + poseBeat * 0.03f,
                0.28f,
                -0.04f,
                trainer,
            )
        } finally {
            if (trainer !is NPCEntity) {
                trainer.setItemInHand(InteractionHand.MAIN_HAND, originalHand)
                trainer.attackAnim = originalAttackAnim
                trainer.oAttackAnim = originalOldAttackAnim
            }
        }
    }

    private fun selectPalette(name: String): Palette {
        val normalized = name.lowercase()
        return when {
            BLUE_MARKERS.any(normalized::contains) -> Palette(0xFF145DA0.toInt(), 0xFF0C2D48.toInt(), 0xFFB8E3FF.toInt())
            ORANGE_MARKERS.any(normalized::contains) -> Palette(0xFFD6532D.toInt(), 0xFF42210B.toInt(), 0xFFFFD166.toInt())
            GREEN_MARKERS.any(normalized::contains) -> Palette(0xFF238B57.toInt(), 0xFF102F24.toInt(), 0xFFEAF4D3.toInt())
            else -> DEFAULT_PALETTES[Mth.positiveModulo(name.hashCode(), DEFAULT_PALETTES.size)]
        }
    }

    private fun finish() {
        active = null
        val sounds = delayedSounds.toList()
        delayedSounds.clear()
        sounds.forEach(Minecraft.getInstance().soundManager::play)
    }

    private data class Intro(
        val opponentId: UUID,
        val name: Component,
        val palette: Palette,
        val startTime: Long,
        var ballAnimationStarted: Boolean = false,
    ) {
        fun elapsedMs(): Long = (System.nanoTime() - startTime) / 1_000_000L
    }

    private data class Palette(val primary: Int, val secondary: Int, val accent: Int)

    private val DEFAULT_PALETTES = listOf(
        Palette(0xFFB9364B.toInt(), 0xFF17324D.toInt(), 0xFFFFD166.toInt()),
        Palette(0xFF2D7D72.toInt(), 0xFF4A2031.toInt(), 0xFFF4F1DE.toInt()),
        Palette(0xFFB75D25.toInt(), 0xFF1E4E59.toInt(), 0xFFFFFFFF.toInt()),
    )
    private val BLUE_MARKERS = listOf("water", "aqua", "swimmer", "sea")
    private val ORANGE_MARKERS = listOf("fire", "magma", "flame")
    private val GREEN_MARKERS = listOf("grass", "leaf", "bug")
    private val SOUND_MARKERS = listOf("pokeball", "poke_ball", "cry", "send", "spawn")
    private val POKE_BALL_ITEM by lazy {
        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"))
    }
}
