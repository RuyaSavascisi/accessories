package io.wispforest.accessories.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.wispforest.accessories.api.events.extra.ExtraEventHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ApplyBonusCount.class)
public abstract class ApplyBonusCountMixin {

    @Shadow @Final private Holder<Enchantment> enchantment;

    @ModifyArg(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/functions/ApplyBonusCount$Formula;calculateNewCount(Lnet/minecraft/util/RandomSource;II)I"), index = 2)
    private int test(int value, @Local(argsOnly = true) LootContext context){
        return (this.enchantment.value() == context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getValueOrThrow(Enchantments.FORTUNE))
                ? ExtraEventHandler.fortuneAdjustment(context, value)
                : value;
    }
}
