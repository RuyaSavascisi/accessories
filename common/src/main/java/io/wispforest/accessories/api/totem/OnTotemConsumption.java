package io.wispforest.accessories.api.totem;

import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface OnTotemConsumption {

    OnTotemConsumption DEFAULT_BEHAVIOR = new OnTotemConsumption() {
        @Override
        public ItemStack onConsumption(SlotReference slotReference, ItemStack currentStack, DamageSource damageSource) {
            currentStack.shrink(1);

            return currentStack;
        }
    };

    ItemStack onConsumption(SlotReference slotReference, ItemStack currentStack, DamageSource damageSource);
}
