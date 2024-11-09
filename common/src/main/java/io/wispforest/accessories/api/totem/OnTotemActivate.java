package io.wispforest.accessories.api.totem;

import io.wispforest.accessories.api.slot.SlotReference;
import jdk.jfr.Experimental;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface OnTotemActivate {

    OnTotemActivate DEFAULT_BEHAVIOR = new OnTotemActivate() {
        @Override
        public @Nullable DeathProtection onActivation(DeathProtection currentProtection, SlotReference slotReference, ItemStack triggeredStack, DamageSource damageSource) {
            return currentProtection;
        }
    };

    @Nullable DeathProtection onActivation(DeathProtection currentProtection, SlotReference slotReference, ItemStack triggeredStack, DamageSource damageSource);
}
