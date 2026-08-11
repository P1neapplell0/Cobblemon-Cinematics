package com.p1nero.cceib.mixin.client;

import com.p1nero.cceib.client.battle.BattleCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

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
        float safeDistance = cobblemoncinematics$getMaxZoom(transform.getDistance());
        float resolvedDistance = BattleCameraController.resolveCollisionDistance(safeDistance);
        move(-resolvedDistance, 0.0F, 0.0F);
    }
}
