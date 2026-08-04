package com.p1nero.cceib.mixin.client;

import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.p1nero.cceib.client.battle.BattleIntroController;
import com.p1nero.cceib.client.compat.megashowdown.MegaShowdownCompat;
import com.p1nero.cceib.config.ClientConfig;
import com.p1nero.cceib.client.battle.BattleCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleGUI.class, remap = false)
public abstract class BattleGuiMixin {
    @Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void cobblemoncinematics$pauseDuringCinematic(CallbackInfoReturnable<Boolean> callback) {
        if (ClientConfig.INSTANCE.getPauseDuringCinematics().get() &&
            (BattleIntroController.INSTANCE.isPlaying() || MegaShowdownCompat.INSTANCE.isPlaying())) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "selectAction", at = @At("HEAD"), remap = false)
    private void cobblemoncinematics$onActionSelected(
        SingleActionRequest request,
        ShowdownActionResponse response,
        CallbackInfo callback
    ) {
        BattleCameraController.onActionSelected();
    }
}
