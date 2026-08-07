package com.p1nero.cceib.client.badge

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Stores one-time badge presentations separately for each save/server and local player. */
object BadgeProgressStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val mapType = object : TypeToken<MutableMap<String, MutableSet<String>>>() {}.type
    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve("cobblemoncinematics-badges.json")

    private var loaded = false
    private var progress: MutableMap<String, MutableSet<String>> = mutableMapOf()

    fun hasSeen(id: String): Boolean {
        val worldKey = currentWorldKey() ?: return false
        ensureLoaded()
        return progress[worldKey]?.contains(id) == true
    }

    fun markSeen(id: String) {
        val worldKey = currentWorldKey() ?: return
        ensureLoaded()
        val badges = progress.getOrPut(worldKey) { mutableSetOf() }
        if (!badges.add(id)) return
        save()
    }

    private fun currentWorldKey(): String? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        if (minecraft.level == null) return null

        val identity = minecraft.singleplayerServer?.worldData?.levelName?.let { levelName ->
            "singleplayer:$levelName"
        } ?: minecraft.currentServer?.let { server ->
            "multiplayer:${server.ip}"
        } ?: return null

        return "$identity:${player.uuid}"
    }

    private fun ensureLoaded() {
        if (loaded) return
        progress = runCatching {
            if (!Files.isRegularFile(file)) {
                mutableMapOf()
            } else {
                gson.fromJson<MutableMap<String, MutableSet<String>>>(Files.readString(file), mapType)
                    ?: mutableMapOf()
            }
        }.getOrElse { mutableMapOf() }
        loaded = true
    }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(
                file,
                gson.toJson(progress, mapType),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
    }
}
