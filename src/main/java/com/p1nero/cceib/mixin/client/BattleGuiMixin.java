package com.p1nero.cceib.mixin.client;

import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.p1nero.cceib.client.battle.BattleCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleGUI.class, remap = false)
public abstract class BattleGuiMixin {
    @Inject(method = "selectAction", at = @At("HEAD"), remap = false)
    private void cobblemoncinematics$onActionSelected(
        SingleActionRequest request,
        ShowdownActionResponse response,
        CallbackInfo callback
    ) {
        BattleCameraController.onActionSelected();
    }
}
