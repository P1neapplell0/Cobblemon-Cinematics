package com.p1nero.cceib.mixin.client;

import com.p1nero.cceib.client.battle.BattleCameraController;
import com.p1nero.cceib.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void move(float zoom, float verticalOffset, float horizontalOffset);

    @Invoker("getMaxZoom")
    protected abstract float cobblemoncinematics$getMaxZoom(float distance);

    @Inject(method = "setup", at = @At("TAIL"))
    private void cobblemoncinematics$applyBattleCamera(
        BlockGetter level,
        Entity cameraEntity,
        boolean detached,
        boolean thirdPersonReverse,
        float partialTick,
        CallbackInfo callback
    ) {
        Camera camera = (Camera) (Object) this;
        BattleCameraController.CameraTransform transform = BattleCameraController.updateForFrame(
            partialTick,
            camera.getPosition(),
            camera.getYRot(),
            camera.getXRot()
        );
        if (transform == null) {
            return;
        }

        setPosition(transform.getPivot());
        setRotation(transform.getYaw(), transform.getPitch());
        if (ClientConfig.INSTANCE.getFadeOccludingBlocks().get()) {
            // The boom keeps its framing; whatever stands in the way is faded by the occlusion
            // fader instead of pushing the camera around.
            move(-transform.getDistance(), 0.0F, 0.0F);
        } else {
            // Fading is off, so fall back to pulling the boom in against walls.
            float safeDistance = cobblemoncinematics$getMaxZoom(transform.getDistance());
            move(-BattleCameraController.resolveCollisionDistance(safeDistance), 0.0F, 0.0F);
        }
    }
}
