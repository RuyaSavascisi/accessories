package io.wispforest.accessories.api.events.extra;

import io.wispforest.accessories.api.slot.SlotReference;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/**
 * Event callback used to adjust the given {@link LivingEntity#isLookingAtMe}s method calls return
 * either indicating the user is masked or not
 * <p/>
 * This is called within {@link ExtraEventHandler#isGazedBlocked(LivingEntity, LivingEntity)} (LivingEntity)}
 * if any given Accessory was found to implement this interface and/or any registered callback
 * to the {@link IsGazeDisguised#EVENT} returns an adjustment
 */
@ApiStatus.Experimental
public interface IsGazeDisguised {

    Event<IsGazeDisguised> EVENT = EventFactory.createArrayBacked(IsGazeDisguised.class, invokers -> (lookingEntity, stack, reference) -> {
        for (var invoker : invokers) {
            var state = invoker.isWearDisguise(lookingEntity, stack, reference);

            if(state != TriState.DEFAULT) return state;
        }

        return TriState.DEFAULT;
    });

    /**
     * @param lookingEntity       Currently looking entity
     * @param stack               The specific stack being evaluated
     * @param reference           The reference to the specific location within the Accessories Inventory
     * @return If the given looking entity sees the given entity as disguised
     */
    TriState isWearDisguise(LivingEntity lookingEntity, ItemStack stack, SlotReference reference);
}
