package io.wispforest.accessories.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.AccessoriesLoaderInternals;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.compat.GeckoLibCompat;
import io.wispforest.accessories.mixin.client.HumanoidArmorLayerAccessor;
import io.wispforest.accessories.mixin.client.LivingEntityRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

public class BuiltinAccessoryRenderers {

    public static final AccessoryRenderer ARMOR_RENDERER = new AccessoryRenderer() {
        @Override
        public <STATE extends LivingEntityRenderState> void render(ItemStack stack, SlotReference reference, PoseStack matrices, EntityModel<STATE> model, STATE renderState, MultiBufferSource multiBufferSource, int light, float partialTicks) {
            var entityRender = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(reference.entity());

            if (!(entityRender instanceof LivingEntityRendererAccessor<?, ?, ?> accessor)) return;
            if (!(renderState instanceof HumanoidRenderState humanoidRenderState)) return;
            if (!(stack.has(DataComponents.EQUIPPABLE))) return;

            var equipmentSlot = stack.get(DataComponents.EQUIPPABLE).slot();

            var possibleLayer = accessor.getLayers().stream()
                    .filter(renderLayer -> renderLayer instanceof HumanoidArmorLayer<?,?,?>)
                    .findFirst();

            possibleLayer.ifPresent(layer -> {
                rendererArmor((HumanoidArmorLayer<HumanoidRenderState,?,?>) layer, stack, matrices, multiBufferSource, humanoidRenderState, equipmentSlot, light, partialTicks);
            });
        }
    };

    private static <S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> void rendererArmor(HumanoidArmorLayer<S, M, A> armorLayer, ItemStack stack, PoseStack poseStack, MultiBufferSource multiBufferSource, S renderState, EquipmentSlot equipmentSlot, int light, float partialTicks) {
        var armorLayerAccessor = (HumanoidArmorLayerAccessor<S, A>) armorLayer;

        var armorModel = armorLayerAccessor.accessories$getArmorModel(renderState, equipmentSlot);

        if (!attemptGeckoRender(stack, poseStack, multiBufferSource, renderState, equipmentSlot, light, partialTicks, armorLayer.getParentModel(), armorModel, armorLayerAccessor::accessories$setPartVisibility)) {
            armorLayerAccessor.accessories$renderArmorPiece(poseStack, multiBufferSource, stack, equipmentSlot, light, armorModel);
        }
    }

    private static <S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> boolean attemptGeckoRender(ItemStack stack, PoseStack poseStack, MultiBufferSource multiBufferSource, S renderState, EquipmentSlot equipmentSlot, int light, float partialTicks, M parentModel, A armorModel, BiConsumer<A, EquipmentSlot> partVisibilitySetter) {
        if (!AccessoriesLoaderInternals.isModLoaded("geckolib")) return false;

        return GeckoLibCompat.renderGeckoArmor(poseStack, multiBufferSource, renderState, stack, equipmentSlot, parentModel, armorModel, partialTicks, light, partVisibilitySetter);
    }

}
