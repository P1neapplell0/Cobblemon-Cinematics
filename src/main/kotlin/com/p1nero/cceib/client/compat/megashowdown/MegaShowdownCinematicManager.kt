package com.p1nero.cceib.client.compat.megashowdown

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.client.render.models.blockbench.animation.ActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.pokemon.Pokemon
import com.p1nero.cceib.client.CinematicTestScreen
import com.p1nero.cceib.client.audio.CinematicSoundPlayer
import com.p1nero.cceib.client.render.CinematicEffects
import com.p1nero.cceib.client.render.ProceduralDraw
import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.resources.PlayerSkin
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import java.util.ArrayDeque
import java.util.UUID
import net.minecraft.world.entity.LivingEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object MegaShowdownCinematicManager {
    private const val DURATION_MS = 5_600L
    private const val ENTER_MS = 420L
    private const val EXIT_MS = 560L
    private const val DEDUPE_MS = 4_000L
    private const val TRAINER_RISE_MS = 520L
    private const val TRAINER_VICTORY_START_MS = 480L
    private const val TRAINER_RETREAT_START_MS = 1_500L
    private const val TRAINER_RETREAT_MS = 520L
    // The Pokemon appears only after the trainer has finished its victory presentation.
    private const val POKEMON_REVEAL_START_MS = TRAINER_RETREAT_START_MS + TRAINER_RETREAT_MS
    private const val POKEMON_REVEAL_MS = 620L
    // Start the battle cry as soon as the Pokemon enters its reveal phase. The animation's own
    // sound keyframe then fires shortly after the model appears, as it does in a normal battle.
    private const val POKEMON_CRY_START_MS = POKEMON_REVEAL_START_MS

    private val modelField by lazy {
        LivingEntityRenderer::class.java.getDeclaredField("model").apply { isAccessible = true }
    }
    private val victoryModels = mutableMapOf<Boolean, VictoryPosePlayerModel>()

    private fun victoryModel(slim: Boolean): VictoryPosePlayerModel = victoryModels.getOrPut(slim) {
        val layer = if (slim) ModelLayers.PLAYER_SLIM else ModelLayers.PLAYER
        VictoryPosePlayerModel(Minecraft.getInstance().entityModels.bakeLayer(layer), slim)
    }

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
        val screen = Minecraft.getInstance().screen
        if (screen !is BattleGUI && screen !is CinematicTestScreen) {
            clear()
            return
        }

        if (active == null && queue.isNotEmpty()) startNext()
        if (active != null && elapsedMs() >= DURATION_MS) finishCurrent()
    }

    fun isPlaying(): Boolean = active != null

    fun stopTest() = clear()

    fun debugPlay(kind: String, pokemonId: String? = null): Boolean {
        val screen = Minecraft.getInstance().screen
        if (screen !is BattleGUI && screen !is CinematicTestScreen) return false
        val type = GimmickType.fromDebugName(kind) ?: return false

        val debugEntity = createDebugPokemon(pokemonId)
        enqueue(
            type,
            debugEntity?.name ?: Component.literal("Test Pokemon"),
            null,
            debugEntity,
        )
        return true
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if ((event.screen is BattleGUI || event.screen is CinematicTestScreen) && active != null) {
            render(event.guiGraphics)
        }
    }

    @SubscribeEvent
    fun onScreenClosing(event: ScreenEvent.Closing) {
        if (event.screen is BattleGUI || event.screen is CinematicTestScreen) clear()
    }

    private fun enqueue(
        type: GimmickType,
        pokemonName: Component,
        pokemonId: UUID?,
        debugEntity: PokemonEntity? = null,
    ) {
        val key = "${type.name}:${pokemonId ?: pokemonName.string.lowercase()}"
        val now = System.nanoTime()
        if (recentlyQueued[key]?.let { elapsedSince(it) <= DEDUPE_MS } == true) return

        recentlyQueued[key] = now
        queue.addLast(
            Presentation(type, pokemonName.copy(), pokemonId, debugEntity, findTrainerId(pokemonId)),
        )
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
        val reveal = ProceduralDraw.easeOutBack(
            ((elapsed - POKEMON_REVEAL_START_MS) / POKEMON_REVEAL_MS.toFloat()).coerceIn(0.0f, 1.0f),
        )
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
        tickPokemonAnimation(presentation, elapsed)
        updatePokemonAnimation(presentation, elapsed)
        // Draw the trainer after the Pokemon so the player/NPC presentation remains the
        // front-most subject while the Pokemon stays above the procedural backdrop.
        renderTrainer(graphics, width, height, elapsed, presentation)
        updateTrainerAnimation(presentation, elapsed)
        drawTitles(graphics, width, height, elapsed, alpha, presentation)
        drawRevealFlash(graphics, width, height, elapsed)
        graphics.pose().popPose()
    }

    private fun updateTrainerAnimation(presentation: Presentation, elapsed: Long) {
        if (elapsed !in TRAINER_VICTORY_START_MS..TRAINER_RETREAT_START_MS) return
        // The native NPC win animation is stateful and must be queued after the first render
        // has selected the model. The local player has no Cobblemon poser; its victory pose is
        // driven statelessly in renderTrainer instead.
        val trainer = findTrainerEntity(presentation) ?: return
        if (trainer !is NPCEntity) return
        val delegate = trainer.delegate as? NPCClientDelegate
        if (delegate?.currentModel == null) return
        // `win` is a primary animation in Cobblemon's standard poser, so it is not included in
        // `allActiveAnimations`. Re-queuing based on that list would reset the animation every
        // render frame and leave the player in its first pose.
        if (!presentation.trainerAnimationStarted) {
            trainer.playAnimation(NPCEntity.WIN_ANIMATION, emptyList())
            presentation.trainerAnimationStarted = true
        }
    }

    /**
     * Advances the Pokemon's render animation while the cinematic is on screen. During a battle
     * the game deliberately skips [PokemonClientDelegate.tick] for battling Pokemon on the client
     * (PokemonEntity.tick only ticks the delegate when not battling), which is why the model renders
     * statically after mega/dynamax/tera. A client-only test entity never ticks on its own, so both
     * cases need this manual tick at Minecraft's simulation rate.
     */
    private fun tickPokemonAnimation(presentation: Presentation, elapsed: Long) {
        if (elapsed < POKEMON_REVEAL_START_MS) return
        val pokemon = findPokemonEntity(presentation) ?: return
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return
        // Advance the animation (and fire its sound_effects keyframes, e.g. the cry) at Minecraft's
        // simulation rate. The head orientation comes from the selected battle pose; no
        // look-control target is needed because cinematic entities use a fixed inventory angle.
        if (elapsed - presentation.lastPokemonTickMs >= 50L) {
            delegate.tick(pokemon)
            presentation.lastPokemonTickMs = elapsed
        }
    }

    private fun updatePokemonAnimation(presentation: Presentation, elapsed: Long) {
        if (elapsed < POKEMON_CRY_START_MS) return
        val pokemon = findPokemonEntity(presentation) ?: return
        val delegate = (pokemon.delegate as? PokemonClientDelegate)
        // PosableState rejects animations until the renderer has selected a model. The
        // entity is rendered before this method, so this check makes the trigger reliable.
        if (delegate?.currentModel == null) return
        if (!presentation.pokemonAnimationStarted) {
            // Resolve `cry` through the active pose so namedAnimations.cry is selected. This is
            // where Cobblemon's battle-standing poser maps the cry to battle_cry; calling the
            // delegate's generic cry provider bypasses that pose-specific mapping.
            val animation = runCatching {
                delegate.currentModel?.getAnimation(delegate, "cry", delegate.runtime)
            }.getOrNull() ?: return
            when (animation) {
                is PrimaryAnimation -> delegate.addPrimaryAnimation(animation)
                is ActiveAnimation -> delegate.addActiveAnimation(animation) {}
            }
            presentation.pokemonAnimationStarted = true
        }
        // The cry sound is played by the animation itself via its sound_effects keyframe, so it
        // stays in sync with the roar instead of being fired a second time out of sync.
    }

    /**
     * Renders the trainer. Real Cobblemon NPCs go through Cobblemon's own renderer and receive
     * the native `win` animation. The local player is drawn through the vanilla player renderer
     * instead of an NPC proxy, so the cinematic keeps the player's real skin texture and every
     * registered cosmetic layer (Accessories, capes, armour, ...) for free. Players are posed in
     * a fixed victory stance, NPCs keep the Cobblemon `win` animation.
     */
    private fun renderTrainer(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        elapsed: Long,
        presentation: Presentation,
    ) {
        val trainer = findTrainerEntity(presentation) ?: return
        val rise = ProceduralDraw.easeOutCubic((elapsed.toFloat() / TRAINER_RISE_MS).coerceIn(0.0f, 1.0f))
        val retreat = ProceduralDraw.easeInOutCubic(
            ((elapsed - TRAINER_RETREAT_START_MS).toFloat() / TRAINER_RETREAT_MS).coerceIn(0.0f, 1.0f),
        )
        val visibility = (rise * (1.0f - retreat)).coerceIn(0.0f, 1.0f)
        if (visibility <= 0.001f) return

        val offsetY = ((1.0f - rise + retreat) * height * 0.78f).roundToInt()
        val fittedScale = min(
            width * 0.62f / trainer.bbWidth.coerceAtLeast(0.35f),
            height * 0.55f / trainer.bbHeight.coerceAtLeast(0.6f),
        ).roundToInt().coerceAtLeast(1)
        val modelScale = (min(width, height) * 0.34f).roundToInt().coerceAtMost(fittedScale)
        val renderScale = (modelScale * visibility).roundToInt().coerceAtLeast(1)

        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, offsetY.toFloat(), 240.0f)
        RenderSystem.disableDepthTest()
        if (trainer is AbstractClientPlayer) {
            renderVictoryPosePlayer(graphics, width, height, renderScale, trainer)
        } else {
            renderTrainerEntity(graphics, width, height, renderScale, trainer)
        }
        RenderSystem.enableDepthTest()
        graphics.pose().popPose()
    }

    /**
     * Draws the local player through the vanilla player renderer in a fixed victory pose. The
     * renderer's model is temporarily swapped for a [VictoryPosePlayerModel]; skin, held item and
     * every cosmetic layer (Accessories, capes, ...) keep working unchanged because they all read
     * the swapped model from the renderer.
     */
    private fun renderVictoryPosePlayer(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        scale: Int,
        player: AbstractClientPlayer,
    ) {
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(player)
        if (renderer !is LivingEntityRenderer<*, *>) {
            renderTrainerEntity(graphics, width, height, scale, player)
            return
        }
        val victoryModel = victoryModel(player.skin.model == PlayerSkin.Model.SLIM)
        val originalModel = runCatching {
            val current = modelField.get(renderer)
            modelField.set(renderer, victoryModel)
            current
        }.getOrNull()
        try {
            renderTrainerEntity(graphics, width, height, scale, player)
        } finally {
            if (originalModel != null) runCatching { modelField.set(renderer, originalModel) }
        }
    }

    private fun renderTrainerEntity(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        scale: Int,
        entity: LivingEntity,
    ) {
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            graphics,
            width / 4,
            0,
            width * 3 / 4,
            height,
            scale,
            -0.04f,
            0.28f,
            -0.04f,
            entity,
        )
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
        // The Pokemon stays hidden until its own reveal phase begins after the trainer.
        if (reveal <= 0.0f) return
        val available = min(width, height).toFloat()
        val entityHeight = entity.bbHeight.coerceAtLeast(1.0f)
        val typeScale = if (type == GimmickType.DYNAMAX) 0.34f else 0.4f
        val pulse = if (type == GimmickType.DYNAMAX) 1.0f + sin(elapsed / 170.0).toFloat() * 0.035f else 1.0f
        val scale = (available * typeScale / max(1.0f, entityHeight * 0.62f) * reveal * exit * pulse)
            .roundToInt()
            .coerceAtLeast(1)
        // Keep the entity animation in the renderer's normal coordinate system. The direct
        // renderEntityInInventory overload accepts a complete quaternion, which used to add a
        // second 45-degree pitch on top of the inventory renderer's standard Z rotation. That
        // made animated Pokemon lean as the pose changed. The helper below owns both the camera
        // and entity rotations and restores the entity state after rendering.
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            graphics,
            width / 4,
            max(0, centerY - height / 2),
            width * 3 / 4,
            min(height, centerY + height / 2),
            scale,
            -0.08f,
            0.0f,
            0.0f,
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
        val revealPeak = POKEMON_REVEAL_START_MS + POKEMON_REVEAL_MS / 2
        val revealFlash = (1.0f - abs(elapsed - revealPeak) / 180.0f).coerceIn(0.0f, 1.0f)
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
        presentation.debugEntity?.let { return it }
        // A real battle Pokemon carries the battle LookControl, which pitches its head down at
        // the (missing) target. Render through a temporary client-only proxy that mirrors the
        // species/form/aspects instead, so the head stays level and the animation is independent.
        presentation.renderProxy?.let { return it }
        val real = findRealPokemonEntity(presentation) ?: return null
        presentation.renderProxy = createRenderProxy(real) ?: real
        return presentation.renderProxy
    }

    private fun findRealPokemonEntity(presentation: Presentation): PokemonEntity? {
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

    private fun createRenderProxy(realEntity: PokemonEntity): PokemonEntity? {
        val level = Minecraft.getInstance().level ?: return null
        return runCatching {
            val tempPokemon = Pokemon().copyFrom(realEntity.pokemon)
            PokemonEntity(level, tempPokemon, CobblemonEntities.POKEMON).also { entity ->
                entity.entityData.set(PokemonEntity.ASPECTS, realEntity.aspects.toSet())
                (entity.delegate as? PokemonClientDelegate)?.initialize(entity)
                positionCinematicPokemon(entity)
                // Poser conditions use `in_battle` to select battle-standing and its head base
                // correction. This id never resolves to a server battle and is local to the proxy.
                entity.battleId = UUID.randomUUID()
            }
        }.getOrNull()
    }

    private fun findTrainerEntity(presentation: Presentation): LivingEntity? {
        presentation.trainerId?.let { trainerId ->
            Minecraft.getInstance().level
                ?.entitiesForRendering()
                ?.firstOrNull { it.uuid == trainerId && it is LivingEntity }
                ?.let { return it as LivingEntity }
        }
        return Minecraft.getInstance().player
    }

    private fun findTrainerId(pokemonId: UUID?): UUID? {
        if (pokemonId == null) return Minecraft.getInstance().player?.uuid
        return CobblemonClient.battle
            ?.sides
            ?.flatMap { it.actors }
            ?.firstOrNull { actor ->
                actor.activePokemon.any { it.battlePokemon?.uuid == pokemonId } ||
                    actor.pokemon.any { it.uuid == pokemonId }
            }
            ?.uuid
            ?: Minecraft.getInstance().player?.uuid
    }

    private fun createDebugPokemon(pokemonId: String?): PokemonEntity? {
        val level = Minecraft.getInstance().level ?: return null
        val species = pokemonId
            ?.let { ResourceLocation.tryParse(it) }
            ?.let { runCatching { PokemonSpecies.getByIdentifier(it) }.getOrNull() }
            ?: PokemonSpecies.getByName("pikachu")
        if (species == null) return null
        val pokemon = Pokemon().apply {
            this.species = species
            this.level = 50
        }
        return PokemonEntity(level, pokemon, CobblemonEntities.POKEMON).also { entity ->
            (entity.delegate as? PokemonClientDelegate)?.initialize(entity)
            positionCinematicPokemon(entity)
            // Keep debug entities on the same battle pose path as real gimmick presentations.
            entity.battleId = UUID.randomUUID()
        }
    }

    /**
     * Bedrock sound keyframes use the entity's world position for positional playback. Temporary
     * cinematic entities are never added to the level and otherwise remain at (0, 0, 0), which
     * makes the cry inaudible when the player is elsewhere in the world.
     */
    private fun positionCinematicPokemon(entity: PokemonEntity) {
        Minecraft.getInstance().player?.let { player ->
            entity.setPos(player.x, player.y, player.z)
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
        active?.let { presentation ->
            CinematicSoundPlayer.playSequence(GIMMICK_SOUND_GROUP, presentation.type.soundCues())
        }
    }

    private fun finishCurrent() {
        active = null
        if (queue.isNotEmpty()) startNext()
    }

    private fun clear() {
        queue.clear()
        active = null
        recentlyQueued.clear()
        CinematicSoundPlayer.cancel(GIMMICK_SOUND_GROUP)
    }

    private fun elapsedMs(): Long = (System.nanoTime() - startedAt) / 1_000_000L

    private fun elapsedSince(timestamp: Long): Long = (System.nanoTime() - timestamp) / 1_000_000L

    private data class Presentation(
        val type: GimmickType,
        val pokemonName: Component,
        val pokemonId: UUID?,
        val debugEntity: PokemonEntity? = null,
        val trainerId: UUID? = null,
        var trainerAnimationStarted: Boolean = false,
        var pokemonAnimationStarted: Boolean = false,
        var lastPokemonTickMs: Long = -50L,
        var renderProxy: PokemonEntity? = null,
    )

    private data class Palette(val primary: Int, val secondary: Int, val accent: Int)

    private enum class GimmickType(
        val translationKey: String,
        val palette: Palette,
        val soundId: String,
        val soundVolume: Float,
        val soundPitch: Float,
    ) {
        MEGA_EVOLUTION(
            "cinematic.cobblemoncinematics.mega_evolution",
            Palette(0xFF8C2BCB.toInt(), 0xFF142A68.toInt(), 0xFFFF68DF.toInt()),
            "cobblemon:evolution.full",
            0.92f,
            1.08f,
        ),
        DYNAMAX(
            "cinematic.cobblemoncinematics.dynamax",
            Palette(0xFFB3133B.toInt(), 0xFF2A0714.toInt(), 0xFFFF668A.toInt()),
            "minecraft:block.beacon.activate",
            0.9f,
            0.68f,
        ),
        Z_MOVE(
            "cinematic.cobblemoncinematics.z_move",
            Palette(0xFFD69B05.toInt(), 0xFF2F2305.toInt(), 0xFFFFE45C.toInt()),
            "minecraft:block.amethyst_block.chime",
            0.82f,
            1.35f,
        ),
        TERASTALIZATION(
            "cinematic.cobblemoncinematics.terastalization",
            Palette(0xFFB64CC8.toInt(), 0xFF164A67.toInt(), 0xFF8FF5FF.toInt()),
            "cobblemon:evolution.notification",
            0.88f,
            1.18f,
        ),
        ;

        fun isEnabled(): Boolean = when (this) {
            MEGA_EVOLUTION -> ClientConfig.megaEvolutionCinematic.get()
            DYNAMAX -> ClientConfig.dynamaxCinematic.get()
            Z_MOVE -> ClientConfig.zMoveCinematic.get()
            TERASTALIZATION -> ClientConfig.terastalizationCinematic.get()
        }

        fun soundCues(): List<CinematicSoundPlayer.Cue> {
            val opening = CinematicSoundPlayer.Cue(0L, soundId, soundVolume, soundPitch)
            return when (this) {
                MEGA_EVOLUTION -> listOf(
                    opening,
                    CinematicSoundPlayer.Cue(620L, "minecraft:block.amethyst_block.resonate", 0.9f, 1.28f),
                    CinematicSoundPlayer.Cue(1_260L, "cobblemon:impact.psychic", 1.0f, 0.86f),
                )
                DYNAMAX -> listOf(
                    opening,
                    CinematicSoundPlayer.Cue(420L, "minecraft:block.portal.trigger", 0.72f, 0.62f),
                    CinematicSoundPlayer.Cue(1_280L, "cobblemon:impact.dragon", 1.0f, 0.72f),
                )
                Z_MOVE -> listOf(
                    CinematicSoundPlayer.Cue(0L, "cobblemon:evolution.notification", 1.0f, 1.28f),
                    CinematicSoundPlayer.Cue(180L, "cobblemon:move.swordsdance.actor", 1.05f, 0.82f),
                    CinematicSoundPlayer.Cue(640L, "cobblemon:move.thunderwave.actor", 1.0f, 1.12f),
                    CinematicSoundPlayer.Cue(980L, "minecraft:block.amethyst_block.resonate", 1.0f, 1.34f),
                    CinematicSoundPlayer.Cue(1_360L, "cobblemon:impact.fairy", 1.15f, 0.82f),
                )
                TERASTALIZATION -> listOf(
                    opening,
                    CinematicSoundPlayer.Cue(560L, "minecraft:block.amethyst_block.resonate", 1.0f, 1.38f),
                    CinematicSoundPlayer.Cue(1_240L, "cobblemon:impact.ice", 0.9f, 1.12f),
                )
            }
        }

        companion object {
            fun fromDebugName(name: String): GimmickType? = when (name.lowercase()) {
                "mega", "mega_evolution" -> MEGA_EVOLUTION
                "dynamax" -> DYNAMAX
                "zmove", "z_move", "z-power" -> Z_MOVE
                "tera", "terastalization", "terastallization" -> TERASTALIZATION
                else -> null
            }

            fun fromMessageKey(key: String): GimmickType? = when (key) {
                "cobblemon.battle.mega" -> MEGA_EVOLUTION
                "cobblemon.battle.start.dynamax" -> DYNAMAX
                "cobblemon.battle.zpower" -> Z_MOVE
                "cobblemon.battle.terastallize" -> TERASTALIZATION
                else -> null
            }
        }
    }

    private const val GIMMICK_SOUND_GROUP = "mega_showdown_gimmick"
}

/**
 * A vanilla [PlayerModel] that locks the arms into a fixed victory pose after the normal
 * [PlayerModel.setupAnim] pass. Used for the cinematic player so the pose does not depend on the
 * Cobblemon NPC poser (which cannot drive the vanilla model, nor carry the player's cosmetics).
 */
private class VictoryPosePlayerModel(root: ModelPart, slim: Boolean) :
    PlayerModel<AbstractClientPlayer>(root, slim) {

    override fun setupAnim(
        entity: AbstractClientPlayer,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float,
    ) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)
        // Left arm raised ~80° above horizontal (nearly overhead); right arm extended 30°
        // backward and abducted 30° outward.
        leftArm.xRot = LEFT_ARM_RAISED_X
        leftArm.yRot = 0.0f
        leftArm.zRot = 0.0f
        rightArm.xRot = RIGHT_ARM_BACK_X
        rightArm.yRot = 0.0f
        rightArm.zRot = RIGHT_ARM_OUT_Z
        leftSleeve.copyFrom(leftArm)
        rightSleeve.copyFrom(rightArm)
    }

    companion object {
        private val LEFT_ARM_RAISED_X = Math.toRadians(-170.0).toFloat()
        private val RIGHT_ARM_BACK_X = Math.toRadians(30.0).toFloat()
        private val RIGHT_ARM_OUT_Z = Math.toRadians(30.0).toFloat()
    }
}
