package io.wispforest.accessories.api.events;

import io.wispforest.accessories.api.AccessoryRegistry;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.AccessoryNestUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.item.ItemStack;

/**
 * Event callback used to allow or denied the ability to equip a given accessory for the given referenced slot
 * type and entity.
 * <p>
 * Fired in {@link AccessoryRegistry#canEquip(ItemStack, SlotReference)}
 */
public interface CanEquipCallback {

    Event<CanEquipCallback> EVENT = EventFactory.createArrayBacked(CanEquipCallback.class,
            (invokers) -> (stack, reference) -> {
                var result =  AccessoryNestUtils.recursiveStackHandling(stack, reference, (stack1, reference1) -> {
                    TriState finalResult = null;

                    for (var invoker : invokers) {
                        var returnResult = invoker.canEquip(stack1, reference1);

                        if(returnResult.equals(TriState.FALSE)) {
                            finalResult = returnResult;

                            break;
                        }
                    }

                    return finalResult;
                });

                return result != null ? result : TriState.DEFAULT;
            }
    );

    /**
     * @param stack     The specific stack being evaluated
     * @param reference The reference to the specific location within the Accessories Inventory
     * @return If the given stack can be equipped
     */
    TriState canEquip(ItemStack stack, SlotReference reference);
}
