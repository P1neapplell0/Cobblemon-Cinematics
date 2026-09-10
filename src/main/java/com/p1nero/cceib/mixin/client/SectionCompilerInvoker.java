package com.p1nero.cceib.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/** Exposes the private layer buffer lookup so faded blocks can be emitted into the translucent layer. */
@Mixin(SectionCompiler.class)
public interface SectionCompilerInvoker {
    @Invoker("getOrBeginLayer")
    BufferBuilder cobblemoncinematics$getOrBeginLayer(
        Map<RenderType, BufferBuilder> layers,
        SectionBufferBuilderPack pack,
        RenderType renderType
    );
}
