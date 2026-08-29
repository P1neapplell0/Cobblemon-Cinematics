package com.p1nero.cceib.client

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.cobblemon.mod.common.command.argument.SpeciesArgumentType
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent

object ClientCinematicCommands {
    @SubscribeEvent
    fun register(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(
            literal<CommandSourceStack>("cobblemoncinematics")
                .then(
                    literal<CommandSourceStack>("test")
                        .then(testCommand("battle_intro", CinematicTestController.Kind.BATTLE_INTRO))
                        .then(testCommand("badge", CinematicTestController.Kind.BADGE))
                        .then(testCommand("mega", CinematicTestController.Kind.MEGA))
                        .then(testCommand("dynamax", CinematicTestController.Kind.DYNAMAX))
                        .then(testCommand("zmove", CinematicTestController.Kind.Z_MOVE))
                        .then(testCommand("terastalization", CinematicTestController.Kind.TERASTALIZATION))
                        .then(
                            literal<CommandSourceStack>("all").executes {
                                report(
                                    "all",
                                    CinematicTestController.request(*CinematicTestController.Kind.entries.toTypedArray()),
                                )
                            },
                        ),
                ),
        )
    }

    private fun testCommand(
        name: String,
        kind: CinematicTestController.Kind,
    ): LiteralArgumentBuilder<CommandSourceStack> =
        literal<CommandSourceStack>(name)
            .executes { report(name, CinematicTestController.request(kind)) }
            .then(
                argument<CommandSourceStack, Species>("pokemon_id", SpeciesArgumentType.Companion.species()).executes {
                    val species = SpeciesArgumentType.Companion.getPokemon(it, "pokemon_id")
                    report(name, CinematicTestController.request(kind, species.resourceIdentifier.toString()))
                },
            )

    private fun report(name: String, success: Boolean): Int {
        Minecraft.getInstance().player?.displayClientMessage(
            Component.literal(
                if (success) "Cobblemon Cinematics test queued: $name"
                else "Join a world before running cinematic tests.",
            ),
            true,
        )
        return if (success) Command.SINGLE_SUCCESS else 0
    }
}
