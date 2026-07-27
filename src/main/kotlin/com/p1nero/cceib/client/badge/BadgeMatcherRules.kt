package com.p1nero.cceib.client.badge

import com.p1nero.cceib.config.ClientConfig
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

object BadgeMatcherRules {
    private var cachedSource: List<String> = emptyList()
    private var cachedRules: List<Rule> = emptyList()

    fun matches(stack: ItemStack, itemId: ResourceLocation): Boolean {
        val configured = ClientConfig.badgeItemMatchers.get()
        if (configured != cachedSource) {
            cachedSource = configured.toList()
            cachedRules = configured.mapNotNull(::parse)
        }
        return cachedRules.any { it.matches(stack, itemId) }
    }

    private fun parse(source: String): Rule? = when {
        source.startsWith("item:") -> ResourceLocation.tryParse(source.removePrefix("item:"))?.let(::ExactItemRule)
        source.startsWith("tag:") -> ResourceLocation.tryParse(source.removePrefix("tag:"))?.let {
            TagRule(TagKey.create(Registries.ITEM, it))
        }

        source.startsWith("regex:") -> runCatching {
            RegexRule(Pattern.compile(source.removePrefix("regex:")))
        }.getOrNull()

        else -> null
    }

    private fun interface Rule {
        fun matches(stack: ItemStack, itemId: ResourceLocation): Boolean
    }

    private data class ExactItemRule(val expected: ResourceLocation) : Rule {
        override fun matches(stack: ItemStack, itemId: ResourceLocation): Boolean = itemId == expected
    }

    private data class TagRule(val tag: TagKey<net.minecraft.world.item.Item>) : Rule {
        override fun matches(stack: ItemStack, itemId: ResourceLocation): Boolean = stack.`is`(tag)
    }

    private data class RegexRule(val pattern: Pattern) : Rule {
        override fun matches(stack: ItemStack, itemId: ResourceLocation): Boolean = pattern.matcher(itemId.toString()).matches()
    }
}
