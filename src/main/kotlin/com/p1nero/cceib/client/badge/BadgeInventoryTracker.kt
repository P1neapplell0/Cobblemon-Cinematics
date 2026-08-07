package com.p1nero.cceib.client.badge

import com.p1nero.cceib.config.ClientConfig
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

object BadgeInventoryTracker {
    private const val SCAN_INTERVAL_TICKS = 5

    private var playerId: UUID? = null
    private var snapshots: Map<ResourceLocation, BadgeSnapshot> = emptyMap()
    private var scanCooldown = 0

    fun tick() {
        val player = Minecraft.getInstance().player
        if (player == null || !ClientConfig.badgeCinematics.get()) {
            reset()
            return
        }

        if (playerId != player.uuid) {
            playerId = player.uuid
            snapshots = collectBadgeSnapshots()
            scanCooldown = SCAN_INTERVAL_TICKS - 1
            return
        }
        if (scanCooldown > 0) {
            scanCooldown--
            return
        }
        scanCooldown = SCAN_INTERVAL_TICKS - 1

        val current = collectBadgeSnapshots()
        current.forEach { (id, snapshot) ->
            if (snapshot.count > (snapshots[id]?.count ?: 0)) {
                onBadgeObtained(id, snapshot.stack)
            }
        }
        snapshots = current
    }

    private fun onBadgeObtained(id: ResourceLocation, stack: ItemStack) {
        if (!ClientConfig.badgeCinematics.get()) return
        if (ClientConfig.badgeOncePerType.get() && BadgeProgressStore.hasSeen(id.toString())) return

        if (ClientConfig.badgeOncePerType.get()) {
            BadgeProgressStore.markSeen(id.toString())
        }
        BadgeCinematicManager.enqueue(id, stack.hoverName.copy(), stack)
    }

    private fun collectBadgeSnapshots(): Map<ResourceLocation, BadgeSnapshot> = buildMap {
        val inventory = Minecraft.getInstance().player?.inventory ?: return@buildMap
        repeat(inventory.containerSize) { slot ->
            val stack = inventory.getItem(slot)
            if (!isBadge(stack)) return@repeat
            val id = BuiltInRegistries.ITEM.getKey(stack.item)
            val previous = get(id)
            put(
                id,
                BadgeSnapshot(
                    count = previous?.count?.plus(stack.count) ?: stack.count,
                    stack = previous?.stack ?: stack.copyWithCount(1),
                ),
            )
        }
    }

    private fun isBadge(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        return BadgeMatcherRules.matches(stack, id)
    }

    private fun reset() {
        playerId = null
        snapshots = emptyMap()
        scanCooldown = 0
    }

    private data class BadgeSnapshot(val count: Int, val stack: ItemStack)
}
