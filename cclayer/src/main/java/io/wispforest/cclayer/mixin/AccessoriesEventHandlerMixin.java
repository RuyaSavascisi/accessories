package io.wispforest.cclayer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.compat.WrappedAccessory;
import top.theillusivec4.curios.compat.WrappedCurio;

@Mixin(value = AccessoriesEventHandler.class)
public abstract class AccessoriesEventHandlerMixin {

    @WrapOperation(
            method = "handleInvalidStacks(Lnet/minecraft/world/Container;Lio/wispforest/accessories/api/slot/SlotReference;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lio/wispforest/accessories/api/AccessoriesAPI;canInsertIntoSlot(Lnet/minecraft/world/item/ItemStack;Lio/wispforest/accessories/api/slot/SlotReference;)Z"), remap = false)
    private static boolean cclayer$adjustCheckBehavior(ItemStack stack, SlotReference reference, Operation<Boolean> operation){
        var accessory = AccessoriesAPI.getAccessory(stack);

        if (!(accessory instanceof WrappedCurio)) return operation.call(stack, reference);

        var slotType = reference.type();

        return AccessoriesAPI.getPredicateResults(slotType.validators(), reference.entity().level(), slotType, 0, stack);
    }
}
