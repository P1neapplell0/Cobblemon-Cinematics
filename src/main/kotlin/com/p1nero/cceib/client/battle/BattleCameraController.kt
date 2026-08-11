package com.p1nero.cceib.client.battle

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.p1nero.cceib.client.ClientBootstrap
import com.p1nero.cceib.client.render.ProceduralDraw
import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

object BattleCameraController {
    private const val SMOOTHING_RATE = 8.0
    private const val COLLISION_RELEASE_RATE = 5.0
    private const val ORBIT_SPEED_DEGREES = 9.0f
    private const val ENVIRONMENT_UPDATE_INTERVAL = 3

    private var enabledByKey = true
    private var phase = CameraPhase.ORBIT
    private var phaseTicks = 0
    private var attackerEntity: PokemonEntity? = null
    private var targetEntity: PokemonEntity? = null
    private var activeEntities: List<PokemonEntity> = emptyList()
    private var current: CameraTransform? = null
    private var orbitYaw = 0.0f
    private var lastFrameNanos = 0L
    private var previousCameraType: CameraType? = null
    private var trackingAvailable = false
    private var environmentChoice: EnvironmentChoice? = null
    private var environmentUpdateTicks = 0
    private var collisionDistance: Float? = null
    private var frameDeltaSeconds = 1.0 / 60.0

    fun toggle() {
        enabledByKey = !enabledByKey
        if (!enabledByKey) reset()
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable(
                if (enabledByKey) "message.cobblemoncinematics.battle_camera.enabled"
                else "message.cobblemoncinematics.battle_camera.disabled",
            ),
            true,
        )
    }

    @SubscribeEvent
    fun onScreenOpening(event: ScreenEvent.Opening) {
        if (
            event.newScreen is BattleGUI &&
            enabledByKey &&
            ClientConfig.battleCamera.get() &&
            CobblemonClient.battle != null
        ) {
            ensureCinematicPerspective()
        }
    }

    @SubscribeEvent
    fun onScreenClosing(event: ScreenEvent.Closing) {
        if (event.screen is BattleGUI) reset()
    }

    fun tick() {
        if (!isBattleCameraActive()) {
            reset()
            return
        }

        ensureCinematicPerspective()
        updateBattleEntities()
        phaseTicks++
        when (phase) {
            CameraPhase.ATTACKER -> if (phaseTicks >= 32) changePhase(CameraPhase.TARGET)
            CameraPhase.TARGET -> if (phaseTicks >= 32) changePhase(CameraPhase.ORBIT)
            CameraPhase.ORBIT -> Unit
        }
        if (trackingAvailable && (--environmentUpdateTicks <= 0 || environmentChoice?.phase != phase)) {
            environmentChoice = selectEnvironmentChoice()
            environmentUpdateTicks = ENVIRONMENT_UPDATE_INTERVAL
        }
    }

    @SubscribeEvent
    fun onCameraAngles(event: ViewportEvent.ComputeCameraAngles) {
        currentTransform()?.let { transform ->
            event.yaw = transform.yaw
            event.pitch = transform.pitch
            event.roll = 0.0f
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if (
            event.screen !is BattleGUI ||
            !ClientConfig.battleCameraHint.get() ||
            !canToggle() ||
            BattleIntroController.isPlaying()
        ) {
            return
        }

        val graphics = event.guiGraphics
        val minecraft = Minecraft.getInstance()
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val text = Component.translatable(
            "message.cobblemoncinematics.battle_camera.hint",
            ClientBootstrap.battleCameraKeyName(),
        )
        val textWidth = minecraft.font.width(text)
        val centerX = width / 2
        val y = height - max(28, height / 9)
        val padding = 8

        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, 0.0f, 760.0f)
        graphics.fill(
            centerX - textWidth / 2 - padding,
            y - 5,
            centerX + textWidth / 2 + padding,
            y + 14,
            0xB80A0D12.toInt(),
        )
        graphics.fill(
            centerX - textWidth / 2 - padding,
            y - 5,
            centerX + textWidth / 2 + padding,
            y - 3,
            0xFFE9C94A.toInt(),
        )
        ProceduralDraw.drawCenteredScaledString(
            graphics,
            minecraft.font,
            text,
            centerX,
            y,
            0xFFFFFFFF.toInt(),
            (width * 0.82f).roundToInt(),
            1.0f,
        )
        graphics.pose().popPose()
    }

    @JvmStatic
    fun updateForFrame(
        partialTick: Float,
        fallbackPivot: Vec3,
        fallbackYaw: Float,
        fallbackPitch: Float,
    ): CameraTransform? {
        if (!isBattleCameraActive() || !trackingAvailable) return null

        val now = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((now - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        }
        frameDeltaSeconds = deltaSeconds
        lastFrameNanos = now

        if (current == null) {
            val attacker = attackerFocus(partialTick) ?: return null
            orbitYaw = Mth.wrapDegrees(lookYaw(attacker, targetFocus(partialTick) ?: attacker) - 45.0f)
        }
        orbitYaw = Mth.wrapDegrees(orbitYaw + ORBIT_SPEED_DEGREES * deltaSeconds.toFloat())
        val desired = desiredTransform(partialTick) ?: return null

        val previous = current ?: CameraTransform(fallbackPivot, fallbackYaw, fallbackPitch, 0.0f)
        val blend = (1.0 - exp(-SMOOTHING_RATE * deltaSeconds)).toFloat().coerceIn(0.0f, 1.0f)
        current = CameraTransform(
            previous.pivot.lerp(desired.pivot, blend.toDouble()),
            Mth.rotLerp(blend, previous.yaw, desired.yaw),
            Mth.lerp(blend, previous.pitch, desired.pitch),
            Mth.lerp(blend, previous.distance, desired.distance),
        )
        return current
    }

    private fun desiredTransform(partialTick: Float): CameraTransform? {
        val base = baseTransform(partialTick) ?: return null
        val environment = environmentChoice?.takeIf { it.phase == phase } ?: return base
        return base.copy(
            yaw = base.yaw + environment.yawOffset,
            pitch = (base.pitch + environment.pitchOffset).coerceIn(-12.0f, 42.0f),
            distance = min(base.distance, environment.safeDistance),
        )
    }

    private fun baseTransform(partialTick: Float): CameraTransform? {
        val attacker = attackerFocus(partialTick) ?: return null
        val target = targetFocus(partialTick) ?: return null
        val phaseTime = phaseTicks + partialTick
        return when (phase) {
            CameraPhase.ORBIT -> {
                val subjects = activeEntities.ifEmpty { listOfNotNull(attackerEntity, targetEntity) }
                val pivot = averageFocus(subjects, partialTick)
                CameraTransform(
                    pivot,
                    orbitYaw,
                    16.0f,
                    framingDistance(subjects, pivot, 4.5f, 24.0f),
                )
            }
            CameraPhase.ATTACKER -> CameraTransform(
                attacker,
                lookYaw(attacker, target) - 32.0f + phaseTime * 0.35f,
                8.0f,
                framingDistance(listOfNotNull(attackerEntity), attacker, 2.75f, 16.0f),
            )
            CameraPhase.TARGET -> CameraTransform(
                target,
                lookYaw(target, attacker) + 32.0f + phaseTime * 0.35f,
                8.0f,
                framingDistance(listOfNotNull(targetEntity), target, 2.75f, 16.0f),
            )
        }
    }

    private fun framingDistance(
        entities: List<PokemonEntity>,
        pivot: Vec3,
        minimum: Float,
        maximum: Float,
    ): Float {
        if (entities.isEmpty()) return minimum
        val minX = entities.minOf { it.boundingBox.minX }
        val minY = entities.minOf { it.boundingBox.minY }
        val minZ = entities.minOf { it.boundingBox.minZ }
        val maxX = entities.maxOf { it.boundingBox.maxX }
        val maxY = entities.maxOf { it.boundingBox.maxY }
        val maxZ = entities.maxOf { it.boundingBox.maxZ }
        val extentX = max(kotlin.math.abs(maxX - pivot.x), kotlin.math.abs(pivot.x - minX))
        val extentY = max(kotlin.math.abs(maxY - pivot.y), kotlin.math.abs(pivot.y - minY))
        val extentZ = max(kotlin.math.abs(maxZ - pivot.z), kotlin.math.abs(pivot.z - minZ))
        val radius = sqrt(
            extentX * extentX + extentY * extentY + extentZ * extentZ,
        )
        val minecraft = Minecraft.getInstance()
        val verticalFov = minecraft.options.fov().get().toDouble().coerceIn(35.0, 110.0)
        val aspect = (minecraft.window.width.toDouble() / minecraft.window.height.coerceAtLeast(1)).coerceAtLeast(1.0)
        val limitingHalfFov = Math.toRadians(verticalFov * 0.5) * min(1.0, aspect / 1.35)
        return (radius / tan(limitingHalfFov) * 1.18 + 1.15).toFloat().coerceIn(minimum, maximum)
    }

    private fun averageFocus(entities: List<PokemonEntity>, partialTick: Float): Vec3 {
        val sum = entities.fold(Vec3.ZERO) { total, entity -> total.add(cameraFocus(entity, partialTick)) }
        return sum.scale(1.0 / entities.size.coerceAtLeast(1))
    }

    private fun selectEnvironmentChoice(): EnvironmentChoice? {
        val base = baseTransform(1.0f) ?: return null
        val subjects = when (phase) {
            CameraPhase.ORBIT -> activeEntities.map { cameraFocus(it, 1.0f) }
            CameraPhase.ATTACKER, CameraPhase.TARGET -> listOfNotNull(attackerFocus(1.0f), targetFocus(1.0f))
        }
        if (subjects.isEmpty()) return null
        val previousChoice = environmentChoice?.takeIf { it.phase == phase }
        val yawOffsets = when (phase) {
            CameraPhase.ORBIT -> floatArrayOf(0.0f, -35.0f, 35.0f, -70.0f, 70.0f, -140.0f, 140.0f)
            CameraPhase.ATTACKER, CameraPhase.TARGET -> floatArrayOf(0.0f, -28.0f, 28.0f, -56.0f, 56.0f)
        }
        val pitchOffsets = floatArrayOf(0.0f, -8.0f, 8.0f)
        val candidates = yawOffsets.flatMap { yawOffset ->
            pitchOffsets.map { pitchOffset ->
                val pitch = (base.pitch + pitchOffset).coerceIn(-12.0f, 42.0f)
                val clearance = cameraClearance(base.pivot, base.yaw + yawOffset, pitch, base.distance)
                EnvironmentCandidate(yawOffset, pitchOffset, clearance)
            }
        }
        return candidates
            .sortedByDescending { candidate ->
                candidate.clearance / base.distance -
                    kotlin.math.abs(candidate.yawOffset) / 720.0f -
                    kotlin.math.abs(candidate.pitchOffset) / 160.0f
            }
            .take(5)
            .maxByOrNull { candidate ->
                val distance = min(base.distance, (candidate.clearance - 0.18f).coerceAtLeast(0.6f))
                val cameraPosition = cameraPosition(
                    base.pivot,
                    base.yaw + candidate.yawOffset,
                    (base.pitch + candidate.pitchOffset).coerceIn(-12.0f, 42.0f),
                    distance,
                )
                val visibleSubjects = subjects.count { hasLineOfSight(cameraPosition, it) }
                val stabilityBonus = if (
                    previousChoice != null &&
                    kotlin.math.abs(previousChoice.yawOffset - candidate.yawOffset) < 1.0f &&
                    kotlin.math.abs(previousChoice.pitchOffset - candidate.pitchOffset) < 1.0f
                ) 0.3f else 0.0f
                candidate.clearance / base.distance * 4.0f +
                    visibleSubjects * 2.0f -
                    kotlin.math.abs(candidate.yawOffset) / 180.0f -
                    kotlin.math.abs(candidate.pitchOffset) / 48.0f +
                    stabilityBonus
            }
            ?.let { candidate ->
                EnvironmentChoice(
                    phase,
                    candidate.yawOffset,
                    candidate.pitchOffset,
                    min(base.distance, (candidate.clearance - 0.18f).coerceAtLeast(0.6f)),
                )
            }
    }

    private fun cameraClearance(pivot: Vec3, yaw: Float, pitch: Float, desiredDistance: Float): Float {
        val cameraPosition = cameraPosition(pivot, yaw, pitch, desiredDistance)
        val hit = clip(pivot, cameraPosition)
        return if (hit.type == HitResult.Type.MISS) desiredDistance else pivot.distanceTo(hit.location).toFloat()
    }

    private fun hasLineOfSight(cameraPosition: Vec3, subject: Vec3): Boolean {
        val hit = clip(cameraPosition, subject)
        return hit.type == HitResult.Type.MISS || hit.location.distanceTo(subject) < 0.35
    }

    private fun clip(from: Vec3, to: Vec3): BlockHitResult {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val source = minecraft.cameraEntity ?: minecraft.player
        if (level == null || source == null) {
            return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to))
        }
        return level.clip(
            ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, source),
        )
    }

    private fun cameraPosition(pivot: Vec3, yaw: Float, pitch: Float, distance: Float): Vec3 =
        pivot.subtract(Vec3.directionFromRotation(pitch, yaw).scale(distance.toDouble()))

    private fun updateBattleEntities() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        val level = minecraft.level
        val battle = CobblemonClient.battle
        if (player == null || level == null || battle == null) {
            trackingAvailable = false
            if (current != null) reset()
            return
        }
        val playerSide = battle.sides.firstOrNull { side -> side.actors.any { it.uuid == player.uuid } }
        val opponentSide = battle.sides.firstOrNull { it !== playerSide }
        val pokemon = level.entitiesForRendering().filterIsInstance<PokemonEntity>().toList()

        // Only follow the entities explicitly marked active by the battle state. A nearest-entity
        // fallback can select an unrelated Pokemon after a faint and send the camera flying.
        val activeAttackers = findActivePokemon(playerSide, pokemon)
        val activeTargets = findActivePokemon(opponentSide, pokemon)
        val attacker = activeAttackers.firstOrNull() ?: retainRenderedEntity(attackerEntity, pokemon)
        val target = activeTargets.firstOrNull() ?: retainRenderedEntity(targetEntity, pokemon)
        val attackers = activeAttackers.ifEmpty { listOfNotNull(attacker) }
        val targets = activeTargets.ifEmpty { listOfNotNull(target) }

        attackerEntity = attacker
        targetEntity = target
        activeEntities = (attackers + targets).distinctBy { it.pokemon.uuid }
        trackingAvailable = attacker != null && target != null
        if (!trackingAvailable && current != null) {
            reset()
        }
    }

    private fun findActivePokemon(side: ClientBattleSide?, entities: List<PokemonEntity>): List<PokemonEntity> {
        val activeIds = side?.actors
            ?.flatMap { it.activePokemon }
            ?.mapNotNull { it.battlePokemon?.uuid }
            ?.toSet()
            .orEmpty()
        return entities.filter {
            it.pokemon.uuid in activeIds
        }
    }

    private fun retainRenderedEntity(
        previous: PokemonEntity?,
        renderedEntities: List<PokemonEntity>,
    ): PokemonEntity? = previous?.takeIf { tracked ->
        renderedEntities.any { it.pokemon.uuid == tracked.pokemon.uuid }
    }

    private fun attackerFocus(partialTick: Float): Vec3? = attackerEntity?.let { cameraFocus(it, partialTick) }

    private fun targetFocus(partialTick: Float): Vec3? = targetEntity?.let { cameraFocus(it, partialTick) }

    private fun cameraFocus(entity: PokemonEntity, partialTick: Float): Vec3 =
        entity.getPosition(partialTick).add(0.0, max(0.8, entity.bbHeight * 0.55), 0.0)

    private fun lookYaw(from: Vec3, to: Vec3): Float =
        (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG).toFloat() - 90.0f

    private fun isBattleCameraActive(): Boolean =
        enabledByKey &&
            canToggle()

    /** Only the active Cobblemon battle screen may consume the camera toggle key. */
    fun canToggle(): Boolean =
        ClientConfig.battleCamera.get() &&
            Minecraft.getInstance().screen is BattleGUI &&
            CobblemonClient.battle != null

    private fun changePhase(next: CameraPhase) {
        phase = next
        phaseTicks = 0
        environmentUpdateTicks = 0
    }

    private fun ensureCinematicPerspective() {
        val options = Minecraft.getInstance().options
        if (previousCameraType == null && options.cameraType.isFirstPerson) {
            previousCameraType = options.cameraType
        }
        if (previousCameraType != null && options.cameraType != CameraType.THIRD_PERSON_BACK) {
            options.cameraType = CameraType.THIRD_PERSON_BACK
        }
    }

    private fun reset() {
        previousCameraType?.let { Minecraft.getInstance().options.cameraType = it }
        previousCameraType = null
        phase = CameraPhase.ORBIT
        phaseTicks = 0
        attackerEntity = null
        targetEntity = null
        activeEntities = emptyList()
        trackingAvailable = false
        current = null
        environmentChoice = null
        environmentUpdateTicks = 0
        collisionDistance = null
        orbitYaw = 0.0f
        lastFrameNanos = 0L
    }

    @JvmStatic
    fun onActionSelected() {
        if (ClientConfig.attackCamera.get() && isBattleCameraActive()) {
            changePhase(CameraPhase.ATTACKER)
        }
    }

    @JvmStatic
    fun currentTransform(): CameraTransform? = current.takeIf { isBattleCameraActive() && trackingAvailable }

    @JvmStatic
    fun resolveCollisionDistance(safeDistance: Float): Float {
        val previous = collisionDistance
        val resolved = when {
            previous == null || safeDistance <= previous -> safeDistance
            else -> {
                val blend = (1.0 - exp(-COLLISION_RELEASE_RATE * frameDeltaSeconds)).toFloat()
                Mth.lerp(blend, previous, safeDistance)
            }
        }
        collisionDistance = resolved
        return resolved.coerceAtMost(safeDistance)
    }

    data class CameraTransform(
        val pivot: Vec3,
        val yaw: Float,
        val pitch: Float,
        val distance: Float,
    )

    private data class EnvironmentCandidate(
        val yawOffset: Float,
        val pitchOffset: Float,
        val clearance: Float,
    )

    private data class EnvironmentChoice(
        val phase: CameraPhase,
        val yawOffset: Float,
        val pitchOffset: Float,
        val safeDistance: Float,
    )

    private enum class CameraPhase {
        ORBIT,
        ATTACKER,
        TARGET,
    }
}
