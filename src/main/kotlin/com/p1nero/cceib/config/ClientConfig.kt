package com.p1nero.cceib.config

import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.ModConfigSpec
import java.util.regex.Pattern

object ClientConfig {
    private val DEFAULT_BADGE_MATCHERS = listOf(
        "tag:badgebox:badges",
        "regex:^badgebox:[a-z0-9_]+_badge(?:_[a-z0-9_]+)?$",
        "regex:^cobblemonpokemonbadges:[a-z0-9_]+_badge(?:_[a-z0-9_]+)?$",
    )

    private val builder = ModConfigSpec.Builder()

    val battleIntros: ModConfigSpec.BooleanValue = builder
        .comment("Play a procedural introduction when a trainer battle screen opens.")
        .define("battleIntros", true)

    val pauseDuringCinematics: ModConfigSpec.BooleanValue = builder
        .comment("Pause the game world while a cinematic is playing when the active screen supports pausing.")
        .define("pauseDuringCinematics", true)

    val delayBattleIntroSounds: ModConfigSpec.BooleanValue = builder
        .comment("Delay Pokemon send-out sounds until the battle introduction finishes.")
        .define("delayBattleIntroSounds", true)

    val battleCamera: ModConfigSpec.BooleanValue = builder
        .comment("Use the directed orbit camera while a Cobblemon battle screen is open.")
        .define("battleCamera", true)

    val attackCamera: ModConfigSpec.BooleanValue = builder
        .comment("Temporarily focus the attacker and target after choosing a battle action.")
        .define("attackCamera", true)

    val battleCameraHint: ModConfigSpec.BooleanValue = builder
        .comment("Show the current battle-camera key binding near the bottom of the battle screen.")
        .define("battleCameraHint", true)

    val megaEvolutionCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon Mega Evolves.")
        .define("megaEvolutionCinematic", true)

    val dynamaxCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon Dynamaxes.")
        .define("dynamaxCinematic", true)

    val zMoveCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon uses Z-Power.")
        .define("zMoveCinematic", true)

    val terastalizationCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon Terastallizes.")
        .define("terastalizationCinematic", true)

    val badgeCinematics: ModConfigSpec.BooleanValue = builder
        .comment("Play a procedural cinematic when a supported optional badge-mod item is obtained.")
        .define("badgeCinematics", true)

    val badgeOncePerType: ModConfigSpec.BooleanValue = builder
        .comment("Only play each badge cinematic once per save/server and player.")
        .define("badgeOncePerType", true)

    val badgeSound: ModConfigSpec.BooleanValue = builder
        .comment("Play the badge obtain sound with badge cinematics.")
        .define("badgeSound", true)

    val badgeItemMatchers: ModConfigSpec.ConfigValue<List<String>> = builder
        .comment(
            "Rules that identify badge items. Supported forms:",
            "  item:namespace:path  - exact item ID",
            "  regex:<pattern>     - regular expression matched against the complete item ID",
            "  tag:namespace:path  - item tag",
        )
        .defineListAllowEmpty(
            "badgeItemMatchers",
            DEFAULT_BADGE_MATCHERS,
            { "item:minecraft:diamond" },
            ::isValidBadgeMatcher,
        )

    val SPEC: ModConfigSpec = builder.build()

    private fun isValidBadgeMatcher(value: Any): Boolean {
        if (value !is String) return false
        return when {
            value.startsWith("item:") -> ResourceLocation.tryParse(value.removePrefix("item:")) != null
            value.startsWith("tag:") -> ResourceLocation.tryParse(value.removePrefix("tag:")) != null
            value.startsWith("regex:") -> value.removePrefix("regex:").isNotEmpty() &&
                runCatching { Pattern.compile(value.removePrefix("regex:")) }.isSuccess

            else -> false
        }
    }

}
