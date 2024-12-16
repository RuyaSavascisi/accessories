package io.wispforest.accessories.menu;

import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.api.slot.SlotPredicateRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AccessoriesInternalSlot extends AccessoriesBasedSlot {

    public final boolean isCosmetic;
    public boolean useCosmeticIcon = true;

    private Function<AccessoriesInternalSlot, Boolean> isActive = (slot) -> true;
    private Function<AccessoriesInternalSlot, Boolean> isAccessible = (slot) -> true;

    public AccessoriesInternalSlot(AccessoriesContainer container, boolean isCosmetic, int slot, int x, int y) {
        super(container, isCosmetic ? container.getCosmeticAccessories() : container.getAccessories(), slot, x, y);

        this.isCosmetic = isCosmetic;
    }

    public AccessoriesInternalSlot isActive(Function<AccessoriesInternalSlot, Boolean> isActive){
        this.isActive = isActive;

        return this;
    }

    public AccessoriesInternalSlot isAccessible(Function<AccessoriesInternalSlot, Boolean> isAccessible){
        this.isAccessible = isAccessible;

        return this;
    }

    public AccessoriesInternalSlot useCosmeticIcon(boolean value) {
        this.useCosmeticIcon = value;

        return this;
    }

    @Override
    public boolean isCosmeticSlot() {
        return this.isCosmetic;
    }

    @Override
    public ResourceLocation getNoItemIcon() {
        return (this.isCosmetic && useCosmeticIcon) ? Accessories.of("gui/slot/cosmetic") : super.getNoItemIcon();
    }

    public List<Component> getTooltipData() {
        List<Component> tooltipData = new ArrayList<>();

        var key = this.isCosmetic ? "cosmetic_" : "";

        var slotType = this.accessoriesContainer.slotType();

        tooltipData.add(Component.translatable(Accessories.translationKey(key + "slot.tooltip.singular"))
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(slotType.translation()).withStyle(ChatFormatting.BLUE)));

        return tooltipData;
    }

    @Override
    public void set(ItemStack stack) {
        var prevStack = this.getItem();

        super.set(stack);

        // TODO: SHOULD THIS BE HERE?
//        if(isCosmetic) {
//            var reference = new SlotReference(container.getSlotName(), entity, getContainerSlot());
//
//            AccessoriesAPI.getAccessory(prevStack)
//                    .ifPresent(prevAccessory1 -> prevAccessory1.onUnequip(prevStack, reference));
//
//            AccessoriesAPI.getAccessory(stack)
//                    .ifPresent(accessory1 -> accessory1.onEquip(stack, reference));
//        }
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (!this.isAccessible.apply(this)) return false;

        if (!this.isCosmeticSlot()) return super.mayPlace(stack);

        var slotType = this.accessoriesContainer.slotType();

        return SlotPredicateRegistry.getPredicateResults(slotType.validators(), this.entity.level(), this.entity, slotType, 0, stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return this.isAccessible.apply(this) && (isCosmetic || super.mayPickup(player));
    }

    @Override
    public boolean allowModification(Player player) {
        return this.isAccessible.apply(this) && super.allowModification(player);
    }

    @Override
    public boolean isActive() {
        return this.isActive.apply(this) && super.isActive();
    }
}
