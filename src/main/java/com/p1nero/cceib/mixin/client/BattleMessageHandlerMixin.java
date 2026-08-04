package com.p1nero.cceib.mixin.client;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.p1nero.cceib.client.compat.megashowdown.MegaShowdownCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleMessageHandler.class, remap = false)
public abstract class BattleMessageHandlerMixin {
    @Inject(
        method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMessagePacket;Lnet/minecraft/client/Minecraft;)V",
        at = @At("TAIL"),
        remap = false
    )
    private void cobblemoncinematics$onBattleMessages(
        BattleMessagePacket packet,
        Minecraft minecraft,
        CallbackInfo callback
    ) {
        MegaShowdownCompat.onBattleMessages(packet.getMessages());
    }
}
