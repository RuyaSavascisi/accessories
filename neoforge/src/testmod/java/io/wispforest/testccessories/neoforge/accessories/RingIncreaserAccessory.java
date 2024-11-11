package io.wispforest.testccessories.neoforge.accessories;

import com.google.common.collect.HashMultimap;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoryRegistry;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RingIncreaserAccessory implements Accessory {

    public static void init(){
        AccessoryRegistry.register(Items.BEACON, new RingIncreaserAccessory());
    }

    private static final ResourceLocation ringLocation = Accessories.of("additional_rings_uuid");

    @Override
    public void onEquip(ItemStack stack, SlotReference reference) {
        var map = HashMultimap.<String, AttributeModifier>create();

        map.put("ring", new AttributeModifier(ringLocation, 100, AttributeModifier.Operation.ADD_VALUE));
        
        reference.capability().addPersistentSlotModifiers(map);
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference reference) {
        var map = HashMultimap.<String, AttributeModifier>create();

        map.put("ring", new AttributeModifier(ringLocation, 100, AttributeModifier.Operation.ADD_VALUE));

       reference.capability().removeSlotModifiers(map);
    }
}
