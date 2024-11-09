package io.wispforest.accessories.mixin.client;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentModelSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EquipmentLayerRenderer.class)
public interface EquipmentLayerRendererAccessor {

    @Accessor("equipmentModels")
    EquipmentModelSet accessories$equipmentModels();
}
