package io.wispforest.accessories.pond;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;

public interface WingsLayerExtension<S extends HumanoidRenderState> {
    void renderStack(ItemStack stack, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, S humanoidRenderState);
}
