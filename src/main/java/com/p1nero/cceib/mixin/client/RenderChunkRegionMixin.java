package com.p1nero.cceib.mixin.client;

import com.p1nero.cceib.client.battle.CameraOcclusionFader;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports blocks that {@link CameraOcclusionFader} is fading as air to their unfaded neighbours, so
 * the faces that were culled against them are emitted and the wall can be seen through. The faded
 * block itself still reports its real state because it is drawn, into the translucent layer, and a
 * faded block still reports its real state to another faded block so the inside of a fading wall
 * stays culled instead of turning into a mess of unlit inner faces.
 */
@Mixin(RenderChunkRegion.class)
public class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cobblemoncinematics$cullAgainstFaded(BlockPos pos, CallbackInfoReturnable<BlockState> callback) {
        if (!CameraOcclusionFader.isFaded(pos)) {
            return;
        }
        if (CameraOcclusionFader.isMeshing(pos)) {
            // Fully transparent: leave it out of the mesh altogether, so it cannot write depth and
            // hide whatever is drawn behind it later.
            if (CameraOcclusionFader.isMeshingInvisible()) {
                callback.setReturnValue(Blocks.AIR.defaultBlockState());
            }
            return;
        }
        // A neighbour lookup: report a faded block as air so an unfaded neighbour emits the faces
        // that were culled against it, but keep it solid for other faded blocks so the inside of a
        // fading wall stays culled.
        if (!CameraOcclusionFader.isMeshingFaded()) {
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
