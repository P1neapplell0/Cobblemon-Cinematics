package com.p1nero.cceib.client.battle

import com.p1nero.cceib.CobblemonCinematicsMod
import com.p1nero.cceib.config.ClientConfig
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.phys.Vec3
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Fades the blocks that hide the battle from the camera.
 *
 * One beam is cast per subject - every active Pokemon and every trainer in the battle - so a Pokemon
 * standing inside a house is still cleared when the camera is out in the open behind it. Each beam is
 * shaped like a torch: a cone opening from the camera that stops at its subject, with a soft rim so
 * the opening has no hard edge. Whatever a beam covers is emitted into the translucent render layer
 * by the chunk mesher, fading out with the distance from the beam's centre and disappearing entirely
 * in its core. Nothing below its subject's feet is ever touched, so the ground stays intact.
 *
 * The mesher is what makes the opening readable: a faded block is reported as air to its unfaded
 * neighbours so they emit the faces that were culled against it, and faded blocks still cull against
 * each other so a fading wall stays a single surface. Because the alpha is baked into the section
 * mesh rather than drawn by a separate pass it cannot silently fail to appear.
 */
object CameraOcclusionFader {
    private const val SMOOTHING_RATE = 8.0
    private const val DIRTY_INTERVAL_TICKS = 2
    private const val MAX_RANGE = 32.0
    private const val MIN_SUBJECT_DISTANCE = 2.0
    private const val MAX_VOLUME = 400_000L

    private val LOGGER = CobblemonCinematicsMod.LOGGER
    private val meshingPos = ThreadLocal.withInitial { BlockPos.MutableBlockPos() }

    /** A point the shot has to keep visible, and the ground level that must not be cut into. */
    data class Subject(val focus: Vec3, val floorY: Double)

    @Volatile
    private var beams: List<Beam> = emptyList()

    /** Main thread copy of the blocks the beams currently cover, used to dirty the right sections. */
    private val regionBlocks = LongOpenHashSet()
    private var ramp = 0.0
    private var dirtyTicks = 0
    private var disabled = false

    /** Immutable snapshot read by the meshing workers. */
    private data class Beam(
        val camera: Vec3,
        val direction: Vec3,
        val range: Double,
        val fadeWidth: Double,
        val coneSlope: Double,
        val softness: Double,
        val floorY: Double,
    )

    /** Alpha of a block: 0 in the core of a beam, 1 outside every beam. Read by the mesher. */
    @JvmStatic
    fun fadeAt(pos: BlockPos): Float {
        val beams = beams
        if (beams.isEmpty()) return 1.0f
        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z + 0.5
        var alpha = 1.0f
        for (beam in beams) {
            val value = alphaOf(beam, x, y, z)
            if (value < alpha) {
                alpha = value
                if (alpha <= 0.0f) return 0.0f
            }
        }
        return alpha
    }

    private fun alphaOf(beam: Beam, x: Double, y: Double, z: Double): Float {
        // Never the ground: blocks whose top is at (or just above) the subject's feet stay solid.
        if (y + 0.5 <= beam.floorY) return 1.0f
        val offsetX = x - beam.camera.x
        val offsetY = y - beam.camera.y
        val offsetZ = z - beam.camera.z
        val along = offsetX * beam.direction.x + offsetY * beam.direction.y + offsetZ * beam.direction.z
        if (along <= 0.5 || along >= beam.range + beam.fadeWidth) return 1.0f
        val distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ
        val radialSquared = max(0.0, distanceSquared - along * along)
        val coneRadius = along * beam.coneSlope
        val outer = coneRadius * (1.0 + beam.softness)
        if (radialSquared >= outer * outer) return 1.0f
        // Soft rim, so the opening reads like the spill of a torch rather than a hard cone.
        val inner = coneRadius * (1.0 - beam.softness)
        val edge = if (radialSquared <= inner * inner) {
            1.0
        } else {
            1.0 - (sqrt(radialSquared) - inner) / max(1.0E-6, outer - inner)
        }
        // The beam stops at its subject - everything past it renders normally.
        val depth = if (along <= beam.range) {
            1.0
        } else {
            ((beam.range + beam.fadeWidth - along) / beam.fadeWidth).coerceIn(0.0, 1.0)
        }
        return (1.0 - (edge * depth).coerceIn(0.0, 1.0)).toFloat()
    }

    @JvmStatic
    fun isFaded(pos: BlockPos): Boolean = fadeAt(pos) < 1.0f

    /** Called by the mesher for the block it is about to compile. */
    @JvmStatic
    fun setMeshingPos(pos: BlockPos) {
        meshingPos.get().set(pos)
    }

    /** True while the mesher is compiling the block at [pos] itself rather than a neighbour. */
    @JvmStatic
    fun isMeshing(pos: BlockPos): Boolean = meshingPos.get().equals(pos)

    @JvmStatic
    fun isMeshingFaded(): Boolean = isFaded(meshingPos.get())

    /** True once the block being meshed is fully faded and can be dropped from the mesh. */
    @JvmStatic
    fun isMeshingInvisible(): Boolean = fadeAt(meshingPos.get()) <= 0.0f

    /**
     * Runs on the client thread while the battle camera is active. [camera] is where the shot is
     * taken from and [subjects] is everything the shot has to keep visible.
     */
    fun tick(camera: Vec3?, subjects: List<Subject>, deltaSeconds: Double) {
        if (disabled) return
        if (!ClientConfig.fadeOccludingBlocks.get()) {
            if (beams.isNotEmpty()) clear()
            return
        }
        if (camera == null || subjects.isEmpty()) {
            if (beams.isNotEmpty()) clear()
            return
        }
        try {
            val blend = (1.0 - exp(-SMOOTHING_RATE * deltaSeconds)).coerceIn(0.0, 1.0)
            // The beams open up from the camera instead of appearing whole the first time.
            ramp += (1.0 - ramp) * blend

            val coneSlope = tan(Math.toRadians(ClientConfig.occlusionConeAngle.get()))
            val softness = ClientConfig.occlusionEdgeSoftness.get()
            val fadeWidth = ClientConfig.occlusionFadeWidth.get()
            val floorMargin = ClientConfig.occlusionFloorMargin.get()
            val built = ArrayList<Beam>(subjects.size)
            for (subject in subjects) {
                val direction = subject.focus.subtract(camera)
                val length = direction.length()
                // Something right next to the camera cannot be hidden by the world.
                if (length < MIN_SUBJECT_DISTANCE) continue
                built += Beam(
                    camera,
                    direction.scale(1.0 / length),
                    min(length * ramp, MAX_RANGE),
                    fadeWidth,
                    coneSlope,
                    softness,
                    subject.floorY + floorMargin,
                )
            }
            beams = built

            if (--dirtyTicks <= 0) {
                dirtyTicks = DIRTY_INTERVAL_TICKS
                refreshRegion()
            }
        } catch (throwable: Throwable) {
            disabled = true
            LOGGER.error("Battle camera block fading failed and has been disabled", throwable)
            try {
                clear()
            } catch (ignored: Throwable) {
                // Nothing left to restore.
            }
        }
    }

    /** Removes the beams, so every block renders normally again. */
    fun clear() {
        beams = emptyList()
        ramp = 0.0
        dirtyTicks = 0
        if (regionBlocks.isEmpty()) return
        dirtySections(regionBlocks)
        regionBlocks.clear()
    }

    /**
     * Recomputes the blocks the beams cover and rebuilds their sections. The alpha depends on where
     * the camera is, so every section a beam touches is meshed again rather than only the ones whose
     * contents changed.
     */
    private fun refreshRegion() {
        val levelRenderer = Minecraft.getInstance().levelRenderer ?: return
        val sections = LongOpenHashSet()
        val current = LongOpenHashSet()
        for (beam in beams) {
            val outerRadius = beam.range * beam.coneSlope * (1.0 + beam.softness) + 1.0
            val minX = floor(beam.camera.x - outerRadius).toInt()
            val maxX = floor(beam.camera.x + outerRadius).toInt()
            val minY = max(floor(beam.camera.y - outerRadius).toInt(), floor(beam.floorY).toInt())
            val maxY = floor(beam.camera.y + outerRadius).toInt()
            val minZ = floor(beam.camera.z - outerRadius).toInt()
            val maxZ = floor(beam.camera.z + outerRadius).toInt()
            val volume = (maxX - minX + 1).toLong() * (maxY - minY + 1) * (maxZ - minZ + 1)
            if (volume > MAX_VOLUME) continue
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val packed = BlockPos.asLong(x, y, z)
                        if (current.contains(packed)) continue
                        if (alphaOf(beam, x + 0.5, y + 0.5, z + 0.5) >= 1.0f) continue
                        current.add(packed)
                        sections.add(SectionPos.asLong(x shr 4, y shr 4, z shr 4))
                    }
                }
            }
        }
        // Sections the beams have just left still hold the faded mesh and must be rebuilt too.
        for (pos in regionBlocks.toLongArray()) {
            if (current.contains(pos)) continue
            val section = SectionPos.asLong(BlockPos.getX(pos) shr 4, BlockPos.getY(pos) shr 4, BlockPos.getZ(pos) shr 4)
            levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section))
        }
        for (section in sections.toLongArray()) {
            levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section))
        }
        regionBlocks.clear()
        regionBlocks.addAll(current)
    }

    private fun dirtySections(blocks: LongOpenHashSet) {
        val levelRenderer = Minecraft.getInstance().levelRenderer ?: return
        val sections = LongOpenHashSet()
        for (pos in blocks.toLongArray()) {
            sections.add(SectionPos.asLong(BlockPos.getX(pos) shr 4, BlockPos.getY(pos) shr 4, BlockPos.getZ(pos) shr 4))
        }
        for (section in sections.toLongArray()) {
            levelRenderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section))
        }
    }
}
