package com.p1nero.cceib.client.battle

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import kotlin.math.exp
import kotlin.math.max

object BattleCameraController {
    private const val SMOOTHING_RATE = 8.0
    private const val ORBIT_SPEED_DEGREES = 9.0f

    private var enabledByKey = true
    private var phase = CameraPhase.ORBIT
    private var phaseTicks = 0
    private var attackerEntity: PokemonEntity? = null
    private var targetEntity: PokemonEntity? = null
    private var current: CameraTransform? = null
    private var orbitYaw = 0.0f
    private var lastFrameNanos = 0L
    private var previousCameraType: CameraType? = null

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
    }

    @SubscribeEvent
    fun onCameraAngles(event: ViewportEvent.ComputeCameraAngles) {
        currentTransform()?.let { transform ->
            event.yaw = transform.yaw
            event.pitch = transform.pitch
            event.roll = 0.0f
        }
    }

    @JvmStatic
    fun updateForFrame(
        partialTick: Float,
        fallbackPivot: Vec3,
        fallbackYaw: Float,
        fallbackPitch: Float,
    ): CameraTransform? {
        if (!isBattleCameraActive()) return null

        val now = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((now - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        }
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
        val attacker = attackerFocus(partialTick) ?: return null
        val target = targetFocus(partialTick) ?: attacker
        val phaseTime = phaseTicks + partialTick
        return when (phase) {
            CameraPhase.ORBIT -> {
                val separation = attacker.distanceTo(target).toFloat()
                CameraTransform(
                    attacker.add(target).scale(0.5),
                    orbitYaw,
                    16.0f,
                    Mth.clamp(separation + 3.5f, 5.0f, 10.0f),
                )
            }
            CameraPhase.ATTACKER -> CameraTransform(
                attacker,
                lookYaw(attacker, target) - 32.0f + phaseTime * 0.35f,
                8.0f,
                3.0f,
            )
            CameraPhase.TARGET -> CameraTransform(
                target,
                lookYaw(target, attacker) + 32.0f + phaseTime * 0.35f,
                8.0f,
                3.0f,
            )
        }
    }

    private fun updateBattleEntities() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return
        val battle = CobblemonClient.battle ?: return
        val playerSide = battle.sides.firstOrNull { side -> side.actors.any { it.uuid == player.uuid } }
        val opponentSide = battle.sides.firstOrNull { it !== playerSide }
        val pokemon = level.entitiesForRendering().filterIsInstance<PokemonEntity>().toList()

        val attacker = findActivePokemon(playerSide, pokemon)
            ?: pokemon.minByOrNull { it.distanceToSqr(player) }
        val target = findActivePokemon(opponentSide, pokemon)
            ?: pokemon.filter { it !== attacker }.minByOrNull { it.distanceToSqr(player) }

        attackerEntity = attacker
        targetEntity = target ?: attacker
    }

    private fun findActivePokemon(side: ClientBattleSide?, entities: List<PokemonEntity>): PokemonEntity? {
        val activeIds = side?.actors
            ?.flatMap { it.activePokemon }
            ?.mapNotNull { it.battlePokemon?.uuid }
            ?.toSet()
            .orEmpty()
        return entities.firstOrNull { it.pokemon.uuid in activeIds }
    }

    private fun attackerFocus(partialTick: Float): Vec3? = attackerEntity?.let { cameraFocus(it, partialTick) }

    private fun targetFocus(partialTick: Float): Vec3? = targetEntity?.let { cameraFocus(it, partialTick) }

    private fun cameraFocus(entity: PokemonEntity, partialTick: Float): Vec3 =
        entity.getPosition(partialTick).add(0.0, max(0.8, entity.bbHeight * 0.55), 0.0)

    private fun lookYaw(from: Vec3, to: Vec3): Float =
        (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG).toFloat() - 90.0f

    private fun isBattleCameraActive(): Boolean =
        enabledByKey &&
            ClientConfig.battleCamera.get() &&
            Minecraft.getInstance().screen is BattleGUI &&
            CobblemonClient.battle != null

    private fun changePhase(next: CameraPhase) {
        phase = next
        phaseTicks = 0
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
        current = null
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
    fun currentTransform(): CameraTransform? = current.takeIf { isBattleCameraActive() }

    data class CameraTransform(
        val pivot: Vec3,
        val yaw: Float,
        val pitch: Float,
        val distance: Float,
    )

    private enum class CameraPhase {
        ORBIT,
        ATTACKER,
        TARGET,
    }
}
