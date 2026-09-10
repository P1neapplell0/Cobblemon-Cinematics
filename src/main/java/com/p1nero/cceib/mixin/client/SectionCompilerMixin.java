package com.p1nero.cceib.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.p1nero.cceib.client.battle.CameraOcclusionFader;
import com.p1nero.cceib.client.render.FadingVertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Emits a block that {@link CameraOcclusionFader} is fading into the translucent render layer with a
 * reduced vertex alpha, so the wall really becomes see-through instead of being drawn by a separate
 * pass that can silently fail to appear.
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;"
        + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
        + "Lcom/mojang/blaze3d/vertex/VertexSorting;"
        + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;"
        + "Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Redirect(
        require = 0,
        method = COMPILE,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState cobblemoncinematics$captureMeshingPos(RenderChunkRegion region, BlockPos pos) {
        CameraOcclusionFader.setMeshingPos(pos);
        return region.getBlockState(pos);
    }

    @Redirect(
        require = 0,
        method = COMPILE,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;getOrBeginLayer(Ljava/util/Map;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"
        )
    )
    private BufferBuilder cobblemoncinematics$fadeLayer(
        SectionCompiler compiler,
        Map<RenderType, BufferBuilder> layers,
        SectionBufferBuilderPack pack,
        RenderType renderType
    ) {
        RenderType layer = CameraOcclusionFader.isMeshingFaded() ? RenderType.translucent() : renderType;
        return ((SectionCompilerInvoker) (Object) compiler).cobblemoncinematics$getOrBeginLayer(layers, pack, layer);
    }

    @Redirect(
        require = 0,
        method = COMPILE,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"
        )
    )
    private void cobblemoncinematics$fadeBatched(
        BlockRenderDispatcher dispatcher,
        BlockState state,
        BlockPos pos,
        BlockAndTintGetter level,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        ModelData modelData,
        RenderType renderType
    ) {
        float alpha = CameraOcclusionFader.fadeAt(pos);
        if (alpha < 1.0F) {
            dispatcher.renderBatched(
                state, pos, level, poseStack, new FadingVertexConsumer(consumer, alpha),
                checkSides, random, modelData, RenderType.translucent()
            );
        } else {
            dispatcher.renderBatched(state, pos, level, poseStack, consumer, checkSides, random, modelData, renderType);
        }
    }
}
