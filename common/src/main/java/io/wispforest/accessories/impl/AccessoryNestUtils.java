package io.wispforest.accessories.impl;

import io.wispforest.accessories.api.AccessoryNest;
import io.wispforest.accessories.api.AccessoryRegistry;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.components.AccessoryNestContainerContents;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.slot.NestedSlotReferenceImpl;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class AccessoryNestUtils {

    @Nullable
    public static AccessoryNestContainerContents getData(ItemStack stack){
        var accessory = AccessoryRegistry.getAccessoryOrDefault(stack);

        if(!(accessory instanceof AccessoryNest)) return null;

        return stack.get(AccessoriesDataComponents.NESTED_ACCESSORIES);
    }

    public static <T> @Nullable T recursiveStackHandling(ItemStack stack, SlotReference reference, BiFunction<ItemStack, SlotReference, @Nullable T> function) {
        var accessory = AccessoryRegistry.getAccessoryOrDefault(stack);

        var value = function.apply(stack, reference);

        if (accessory instanceof AccessoryNest holdable && value == null) {
            var innerStacks = holdable.getInnerStacks(stack);

            for (int i = 0; i < innerStacks.size(); i++) {
                var innerStack = innerStacks.get(i);

                if (innerStack.isEmpty()) continue;

                value = recursiveStackHandling(innerStack, create(reference, i), function);

                if(value != null) return value;
            }
        }

        return value;
    }

    public static void recursiveStackConsumption(ItemStack stack, SlotReference reference, BiConsumer<ItemStack, SlotReference> consumer) {
        var accessory = AccessoryRegistry.getAccessoryOrDefault(stack);

        consumer.accept(stack, reference);

        if (!(accessory instanceof AccessoryNest holdable)) return;

        var innerStacks = holdable.getInnerStacks(stack);

        for (int i = 0; i < innerStacks.size(); i++) {
            var innerStack = innerStacks.get(i);

            if (innerStack.isEmpty()) continue;

            recursiveStackConsumption(innerStack, create(reference, i), consumer);
        }
    }

    public static SlotReference create(SlotReference reference, int innerIndex) {
        var innerSlotIndices = new ArrayList<Integer>();

        if(reference instanceof NestedSlotReferenceImpl nestedSlotReference) {
            innerSlotIndices.addAll(nestedSlotReference.innerSlotIndices());
        }

        innerSlotIndices.add(innerIndex);

        return SlotReference.ofNest(reference.entity(), reference.slotName(), reference.slot(), innerSlotIndices);
    }
}
