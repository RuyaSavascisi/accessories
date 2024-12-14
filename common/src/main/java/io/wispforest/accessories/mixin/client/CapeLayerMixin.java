package io.wispforest.accessories.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wispforest.accessories.api.equip.EquipmentChecking;
import io.wispforest.accessories.pond.LivingEntityRenderStateExtension;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Shadow @Final private EquipmentAssetManager equipmentAssets;

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/CapeLayer;hasLayer(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;)Z"))
    private boolean accessories$adjustGliderStackCheck(CapeLayer instance, ItemStack itemStack, EquipmentClientInfo.LayerType layerType, Operation<Boolean> original, @Local(argsOnly = true) PlayerRenderState playerRenderState) {
        if (playerRenderState instanceof LivingEntityRenderStateExtension extension) {
            var capability = extension.getEntity().accessoriesCapability();

            if (capability != null) {
                var gliderItem = capability.getFirstEquipped(stack1 -> {
                    var equippable = stack1.get(DataComponents.EQUIPPABLE);

                    if (equippable != null && equippable.assetId().isPresent()) {
                        var list = equipmentAssets.get(equippable.assetId().get())
                                .getLayers(EquipmentClientInfo.LayerType.WINGS);

                        return !list.isEmpty();
                    }

                    return false;
                }, EquipmentChecking.COSMETICALLY_OVERRIDABLE);

                if (gliderItem != null) {
                    itemStack = gliderItem.stack();
                }
            }
        }

        return original.call(instance, itemStack, layerType);
    }
}
