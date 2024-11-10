package io.wispforest.accessories.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.caching.ItemStackBasedPredicate;
import io.wispforest.accessories.pond.LivingEntityRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel> {

    @WrapMethod(method = {
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            "method_17159(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;ILnet/minecraft/class_10042;FF)V" //TODO: FIGURE OUT WHY ARCH LOOM DON'T REMAP WRAP METHOD
    })
    private void accessories$adjustHeadItem(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, S livingEntityRenderState, float f, float g, Operation<Void> original) {
        ItemStack prevStack = null;
        BakedModel prevModel = null;

        if (livingEntityRenderState instanceof LivingEntityRenderStateExtension extension) {
            var livingEntity = extension.getEntity();
            var capability = livingEntity.accessoriesCapability();

            if (capability != null) {
                var ref = capability.getEquipped(ItemStackBasedPredicate.ofClass(BannerItem.class))
                        .stream()
                        .filter(slotEntryReference -> slotEntryReference.reference().slotName().equals("hat"))
                        .findFirst()
                        .orElse(null);

                if (ref != null) {
                    var stack = ref.stack();

                    prevStack = livingEntityRenderState.headItem;
                    prevModel = livingEntityRenderState.headItemModel;

                    livingEntityRenderState.headItem = stack;
                    livingEntityRenderState.headItemModel = Minecraft.getInstance().getItemRenderer().resolveItemModel(stack, livingEntity, ItemDisplayContext.HEAD);
                }
            }
        }

        original.call(poseStack, multiBufferSource, i, livingEntityRenderState, f, g);

        if (prevStack != null) livingEntityRenderState.headItem = prevStack;
        if (prevModel != null) livingEntityRenderState.headItemModel = prevModel;
    }
}
