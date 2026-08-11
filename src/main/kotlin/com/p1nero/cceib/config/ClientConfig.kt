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

    private val DEFAULT_LEGENDARY_POKEMON = listOf(
        "cobblemon:articuno", "cobblemon:zapdos", "cobblemon:moltres", "cobblemon:mewtwo", "cobblemon:mew",
        "cobblemon:raikou", "cobblemon:entei", "cobblemon:suicune", "cobblemon:lugia", "cobblemon:hooh",
        "cobblemon:celebi", "cobblemon:regirock", "cobblemon:regice", "cobblemon:registeel", "cobblemon:latias",
        "cobblemon:latios", "cobblemon:kyogre", "cobblemon:groudon", "cobblemon:rayquaza", "cobblemon:jirachi",
        "cobblemon:deoxys", "cobblemon:uxie", "cobblemon:mesprit", "cobblemon:azelf", "cobblemon:dialga",
        "cobblemon:palkia", "cobblemon:heatran", "cobblemon:regigigas", "cobblemon:giratina", "cobblemon:cresselia",
        "cobblemon:phione", "cobblemon:manaphy", "cobblemon:darkrai", "cobblemon:shaymin", "cobblemon:arceus",
        "cobblemon:victini", "cobblemon:cobalion", "cobblemon:terrakion", "cobblemon:virizion", "cobblemon:tornadus",
        "cobblemon:thundurus", "cobblemon:landorus", "cobblemon:reshiram", "cobblemon:zekrom", "cobblemon:kyurem",
        "cobblemon:keldeo", "cobblemon:meloetta", "cobblemon:genesect", "cobblemon:xerneas", "cobblemon:yveltal",
        "cobblemon:zygarde", "cobblemon:diancie", "cobblemon:hoopa", "cobblemon:volcanion", "cobblemon:cosmog",
        "cobblemon:cosmoem", "cobblemon:solgaleo", "cobblemon:lunala", "cobblemon:necrozma", "cobblemon:tapukoko",
        "cobblemon:tapulele", "cobblemon:tapubulu", "cobblemon:tapufini", "cobblemon:marshadow", "cobblemon:zeraora",
        "cobblemon:meltan", "cobblemon:melmetal", "cobblemon:magearna", "cobblemon:silvally", "cobblemon:typenull",
        "cobblemon:zacian", "cobblemon:zamazenta", "cobblemon:eternatus", "cobblemon:kubfu", "cobblemon:urshifu",
        "cobblemon:zarude", "cobblemon:regieleki", "cobblemon:regidrago", "cobblemon:glastrier", "cobblemon:spectrier",
        "cobblemon:calyrex", "cobblemon:enamorus", "cobblemon:koraidon", "cobblemon:miraidon", "cobblemon:okidogi",
        "cobblemon:munkidori", "cobblemon:fezandipiti", "cobblemon:terapagos", "cobblemon:chienpao", "cobblemon:chiyu",
    )

    private val builder = ModConfigSpec.Builder()

    init {
        builder.push("general")
    }

    val pauseDuringCinematics: ModConfigSpec.BooleanValue = builder
        .comment("Pause the game world while a cinematic is playing when the active screen supports pausing.")
        .define("pauseDuringCinematics", true)

    init {
        builder.pop()
        builder.push("battleIntro")
    }

    val battleIntros: ModConfigSpec.BooleanValue = builder
        .comment("Play a procedural introduction when a trainer battle screen opens.")
        .define("trainerEnabled", true)

    val trainerIntroNpcWhitelist: ModConfigSpec.ConfigValue<List<String>> = builder
        .comment("NPC resource IDs allowed to play Trainer battle intros. Empty or '*' allows every NPC.")
        .defineListAllowEmpty("trainerNpcWhitelist", listOf("*"), { "cobblemon:example_npc" }, ::isValidIdFilter)

    val trainerIntroNpcBlacklist: ModConfigSpec.ConfigValue<List<String>> = builder
        .comment("NPC resource IDs blocked from Trainer battle intros. Blacklist entries take priority.")
        .defineListAllowEmpty("trainerNpcBlacklist", emptyList(), { "cobblemon:example_npc" }, ::isValidIdFilter)

    val wildIntroPokemonWhitelist: ModConfigSpec.ConfigValue<List<String>> = builder
        .comment("Pokemon species IDs allowed to play wild battle intros. Defaults to legendary and mythical Pokemon.")
        .defineListAllowEmpty("wildPokemonWhitelist", DEFAULT_LEGENDARY_POKEMON, { "cobblemon:mew" }, ::isValidIdFilter)

    val wildIntroPokemonBlacklist: ModConfigSpec.ConfigValue<List<String>> = builder
        .comment("Pokemon species IDs blocked from wild battle intros. Blacklist entries take priority.")
        .defineListAllowEmpty("wildPokemonBlacklist", emptyList(), { "cobblemon:mew" }, ::isValidIdFilter)

    val delayBattleIntroSounds: ModConfigSpec.BooleanValue = builder
        .comment("Delay Pokemon send-out sounds until the battle introduction finishes.")
        .define("delayPokemonSounds", true)

    init {
        builder.pop()
        builder.push("battleCamera")
    }

    val battleCamera: ModConfigSpec.BooleanValue = builder
        .comment("Use the directed orbit camera while a Cobblemon battle screen is open.")
        .define("enabled", true)

    val attackCamera: ModConfigSpec.BooleanValue = builder
        .comment("Temporarily focus the attacker and target after choosing a battle action.")
        .define("attackFocus", true)

    val battleCameraHint: ModConfigSpec.BooleanValue = builder
        .comment("Show the current battle-camera key binding near the bottom of the battle screen.")
        .define("hint", true)

    init {
        builder.pop()
        builder.push("megaShowdown")
    }

    val megaEvolutionCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon Mega Evolves.")
        .define("megaEvolution", true)

    val dynamaxCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon Dynamaxes.")
        .define("dynamax", true)

    val zMoveCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon uses Z-Power.")
        .define("zMove", true)

    val terastalizationCinematic: ModConfigSpec.BooleanValue = builder
        .comment("Play the optional Mega Showdown cinematic when a Pokemon Terastallizes.")
        .define("terastalization", true)

    init {
        builder.pop()
        builder.push("badges")
    }

    val badgeCinematics: ModConfigSpec.BooleanValue = builder
        .comment("Play a procedural cinematic when a supported optional badge-mod item is obtained.")
        .define("enabled", true)

    val badgeOncePerType: ModConfigSpec.BooleanValue = builder
        .comment("Only play each badge cinematic once per save/server and player.")
        .define("oncePerType", true)

    val badgeSound: ModConfigSpec.BooleanValue = builder
        .comment("Play the badge obtain sound with badge cinematics.")
        .define("sound", true)

    val badgeItemMatchers: ModConfigSpec.ConfigValue<List<String>> = builder
        .comment(
            "Rules that identify badge items. Supported forms:",
            "  item:namespace:path  - exact item ID",
            "  regex:<pattern>     - regular expression matched against the complete item ID",
            "  tag:namespace:path  - item tag",
        )
        .defineListAllowEmpty(
            "itemMatchers",
            DEFAULT_BADGE_MATCHERS,
            { "item:minecraft:diamond" },
            ::isValidBadgeMatcher,
        )

    val SPEC: ModConfigSpec = builder.pop().build()

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

    private fun isValidIdFilter(value: Any): Boolean =
        value is String && (value == "*" || ResourceLocation.tryParse(value) != null)

}
