package io.wispforest.testccessories.neoforge.accessories;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoryRegistry;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.client.SimpleAccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class PotatoAccessory implements Accessory {

    @OnlyIn(Dist.CLIENT)
    public static void clientInit(){
        AccessoriesRendererRegistry.registerRenderer(Items.POTATO, Renderer::new);
    }

    public static void init(){
        AccessoryRegistry.register(Items.POTATO, new PotatoAccessory());
    }

    @OnlyIn(Dist.CLIENT)
    public static class Renderer implements SimpleAccessoryRenderer {
        @Override
        public <S extends LivingEntityRenderState> void align(ItemStack stack, SlotReference reference, EntityModel<S> model, S renderState, PoseStack matrices) {
            if(!(model instanceof HumanoidModel<? extends HumanoidRenderState> humanoidModel)) return;

            AccessoryRenderer.transformToModelPart(matrices, humanoidModel.body, 0, 0, -1);
        }
    }
}