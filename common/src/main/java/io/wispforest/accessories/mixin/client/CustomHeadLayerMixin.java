package io.wispforest.accessories.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel> {

    // TODO: REALLY WISH TO USE WRAPMETHOD FOR SOMETHING LIKE THIS
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD")
    )
    private void accessories$adjustHeadItem(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, S livingEntityRenderState, float f, float g, CallbackInfo ci, @Share(value = "previousStack") LocalRef<ItemStack> prevStack, @Share(value = "previousModel") LocalRef<BakedModel> prevModel) {
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

                    prevStack.set(livingEntityRenderState.headItem);
                    prevModel.set(livingEntityRenderState.headItemModel);

                    livingEntityRenderState.headItem = stack;
                    livingEntityRenderState.headItemModel = Minecraft.getInstance().getItemRenderer().resolveItemModel(stack, livingEntity, ItemDisplayContext.HEAD);

                    return;
                }
            }
        }

        prevStack.set(null);
        prevModel.set(null);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("TAIL")
    )
    private void accessories$resetStackAndModel(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, S livingEntityRenderState, float f, float g, CallbackInfo ci, @Share(value = "previousStack") LocalRef<ItemStack> prevStack, @Share(value = "previousModel") LocalRef<BakedModel> prevModel){
        if (prevStack.get() != null) livingEntityRenderState.headItem = prevStack.get();
        if (prevModel.get() != null) livingEntityRenderState.headItemModel = prevModel.get();
    }
}
