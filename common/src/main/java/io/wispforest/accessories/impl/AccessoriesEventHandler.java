package io.wispforest.accessories.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.api.*;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.components.AccessoryItemAttributeModifiers;
import io.wispforest.accessories.api.components.AccessoryNestContainerContents;
import io.wispforest.accessories.api.events.*;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.client.AccessoriesMenu;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.data.SlotTypeLoader;
import io.wispforest.accessories.endec.NbtMapCarrier;
import io.wispforest.accessories.networking.client.SyncEntireContainer;
import io.wispforest.accessories.networking.client.SyncContainerData;
import io.wispforest.accessories.networking.client.SyncData;
import io.wispforest.accessories.utils.AttributeUtils;
import io.wispforest.endec.SerializationContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static io.wispforest.accessories.Accessories.ACCESSORY_EQUIPPED;
import static io.wispforest.accessories.Accessories.ACCESSORY_UNEQUIPPED;

@ApiStatus.Internal
public class AccessoriesEventHandler {

    public static boolean dataReloadOccurred = false;

    public static void onWorldTick(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        revalidatePlayersOnReload(serverLevel.getServer().getPlayerList());
    }

    public static void revalidatePlayersOnReload(PlayerList playerList) {
        if(!dataReloadOccurred) return;

        for (var player : playerList.getPlayers()) revalidatePlayer(player);

        dataReloadOccurred = false;
    }

    public static void revalidatePlayer(ServerPlayer player) {
        var capability = AccessoriesCapability.get(player);

        if (capability == null) return;

        var validSlotTypes = EntitySlotLoader.getEntitySlots(player).values();

        for (var container : capability.getContainers().values()) {
            var slotType = container.slotType();

            if (slotType != null && validSlotTypes.contains(slotType)) {
                var baseSize = ((AccessoriesContainerImpl) container).getBaseSize();

                if (baseSize == null || baseSize != slotType.amount()) {
                    container.markChanged();
                    container.update();
                }

                var stacks = container.getAccessories();
                var cosmeticStacks = container.getCosmeticAccessories();

                for (int i = 0; i < container.getSize(); i++) {
                    var reference = container.createReference(i);

                    handleInvalidStacks(stacks, reference, player);
                    handleInvalidStacks(cosmeticStacks, reference, player);
                }
            } else {
                // TODO: DROP CONTAINER ?!
                var stacks = container.getAccessories();
                var cosmeticStacks = container.getCosmeticAccessories();

                for (int i = 0; i < container.getSize(); i++) {
                    var reference = container.createReference(i);

                    dropAndRemoveStack(stacks, reference, player);
                    dropAndRemoveStack(cosmeticStacks, reference, player);
                }
            }
        }
    }

    private static void handleInvalidStacks(Container container, SlotReference reference, ServerPlayer player) {
        var bl = !AccessoriesAPI.canInsertIntoSlot(container.getItem(reference.slot()), reference);

        if (bl) dropAndRemoveStack(container, reference, player);
    }

    private static void dropAndRemoveStack(Container container, SlotReference reference, ServerPlayer player) {
        var stack = container.getItem(reference.slot());

        container.setItem(reference.slot(), ItemStack.EMPTY);

        AccessoriesInternals.giveItemToPlayer(player, stack);
    }

    public static void entityLoad(LivingEntity entity, Level level) {
        if (!level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) return;

        var capability = AccessoriesCapability.get(serverPlayer);

        if(capability == null) return;

        var carrier = NbtMapCarrier.of();

        ((AccessoriesHolderImpl) capability.getHolder()).write(carrier, SerializationContext.empty());

        AccessoriesInternals.getNetworkHandler().sendToTrackingAndSelf(serverPlayer, new SyncEntireContainer(capability.entity().getId(), carrier));
    }

    public static void onTracking(LivingEntity entity, ServerPlayer serverPlayer) {
        var capability = AccessoriesCapability.get(entity);

        if(capability == null) return;

        var carrier = NbtMapCarrier.of();

        ((AccessoriesHolderImpl) capability.getHolder()).write(carrier, SerializationContext.empty());

        AccessoriesInternals.getNetworkHandler().sendToPlayer(serverPlayer, new SyncEntireContainer(capability.entity().getId(), carrier));
    }

    public static void dataSync(@Nullable PlayerList list, @Nullable ServerPlayer player) {
        var networkHandler = AccessoriesInternals.getNetworkHandler();
        var syncPacket = SyncData.create();

        if (list != null && !list.getPlayers().isEmpty()) {
            revalidatePlayersOnReload(list);

            // TODO: OPTIMIZE THIS?
            for (var playerEntry : list.getPlayers()) {
                networkHandler.sendToPlayer(playerEntry, syncPacket);

                var capability = AccessoriesCapability.get(playerEntry);

                if(capability == null) return;

                var carrier = NbtMapCarrier.of();

                ((AccessoriesHolderImpl) capability.getHolder()).write(carrier, SerializationContext.empty());

                networkHandler.sendToTrackingAndSelf(playerEntry, new SyncEntireContainer(capability.entity().getId(), carrier));

                if(playerEntry.containerMenu instanceof AccessoriesMenu accessoriesMenu) {
                    Accessories.openAccessoriesMenu(playerEntry, accessoriesMenu.targetEntity());
                }
            }
        } else if (player != null) {
            networkHandler.sendToPlayer(player, syncPacket);

            revalidatePlayer(player);

            var capability = AccessoriesCapability.get(player);

            if(capability == null) return;

            var carrier = NbtMapCarrier.of();

            ((AccessoriesHolderImpl) capability.getHolder()).write(carrier, SerializationContext.empty());

            networkHandler.sendToPlayer(player, new SyncEntireContainer(capability.entity().getId(), carrier));

            if(player.containerMenu instanceof AccessoriesMenu accessoriesMenu) {
                Accessories.openAccessoriesMenu(player, accessoriesMenu.targetEntity());
            }
        }
    }

    public static void onLivingEntityTick(LivingEntity entity) {
        if(entity.isRemoved()) return;

        var capability = AccessoriesCapability.get(entity);

        if (capability != null) {
            var dirtyStacks = new HashMap<String, ItemStack>();
            var dirtyCosmeticStacks = new HashMap<String, ItemStack>();

            var removedAttributesBuilder = new AccessoryAttributeBuilder();
            var addedAttributesBuilder = new AccessoryAttributeBuilder();

            for (var containerEntry : capability.getContainers().entrySet()) {
                var container = containerEntry.getValue();
                var slotType = container.slotType();

                var accessories = (ExpandedSimpleContainer) container.getAccessories();
                var cosmetics = container.getCosmeticAccessories();

                for (int i = 0; i < accessories.getContainerSize(); i++) {
                    var slotReference = container.createReference(i);

                    var slotId = slotType.name() + "/" + i;

                    var currentStack = accessories.getItem(i);

                    // TODO: Move ticking below checks?
                    if (!currentStack.isEmpty()) {
                        // TODO: Document this behavior to prevent double ticking maybe!!!
                        currentStack.inventoryTick(entity.level(), entity, -1, false);

                        var accessory = AccessoriesAPI.getAccessory(currentStack);

                        if (accessory != null) accessory.tick(currentStack, slotReference);
                    }

                    var lastStack = accessories.getPreviousItem(i);

                    if (entity.level().isClientSide()) continue;

                    if (!ItemStack.matches(currentStack, lastStack)) {
                        container.getAccessories().setPreviousItem(i, currentStack.copy());
                        dirtyStacks.put(slotId, currentStack.copy());

                        if (!lastStack.isEmpty()) {
                            removedAttributesBuilder.addFrom(AccessoriesAPI.getAttributeModifiers(lastStack, slotReference));
                        }

                        if (!currentStack.isEmpty()) {
                            addedAttributesBuilder.addFrom(AccessoriesAPI.getAttributeModifiers(currentStack, slotReference));
                        }

                        boolean equipmentChange = false;

                        /*
                         * TODO: Does item check need to exist anymore?
                         */
                        if (!ItemStack.isSameItem(currentStack, lastStack) || accessories.isSlotFlagged(i)) {
                            AccessoriesAPI.getOrDefaultAccessory(lastStack.getItem()).onUnequip(lastStack, slotReference);
                            AccessoriesAPI.getOrDefaultAccessory(currentStack.getItem()).onEquip(currentStack, slotReference);

                            if (entity instanceof ServerPlayer serverPlayer) {
                                if (!currentStack.isEmpty()) {
                                    ACCESSORY_EQUIPPED.trigger(serverPlayer, currentStack, slotReference, false);
                                }

                                if (!lastStack.isEmpty()) {
                                    ACCESSORY_UNEQUIPPED.trigger(serverPlayer, lastStack, slotReference, false);
                                }
                            }

                            equipmentChange = true;
                        }

                        AccessoryChangeCallback.EVENT.invoker().onChange(lastStack, currentStack, slotReference, equipmentChange ? SlotStateChange.REPLACEMENT : SlotStateChange.MUTATION);

                        recursiveStackChange(slotReference, AccessoryNestUtils.getData(lastStack), AccessoryNestUtils.getData(currentStack));
                    }

                    var currentCosmeticStack = cosmetics.getItem(i);
                    var lastCosmeticStack = container.getCosmeticAccessories().getPreviousItem(i);

                    if (!ItemStack.matches(currentCosmeticStack, lastCosmeticStack)) {
                        cosmetics.setPreviousItem(i, currentCosmeticStack.copy());
                        dirtyCosmeticStacks.put(slotId, currentCosmeticStack.copy());

                        if (entity instanceof ServerPlayer serverPlayer) {
                            if (!currentStack.isEmpty()) {
                                ACCESSORY_EQUIPPED.trigger(serverPlayer, currentStack, slotReference, true);
                            }
                            if (!lastStack.isEmpty()) {
                                ACCESSORY_UNEQUIPPED.trigger(serverPlayer, lastStack, slotReference, true);
                            }
                        }
                    }
                }
            }

            if (entity.level().isClientSide()) return;

            AttributeUtils.removeTransientAttributeModifiers(entity, removedAttributesBuilder);
            AttributeUtils.addTransientAttributeModifiers(entity, addedAttributesBuilder);

            //--

            var updatedContainers = ((AccessoriesCapabilityImpl) capability).getUpdatingInventories();

            capability.updateContainers();

            ContainersChangeCallback.EVENT.invoker().onChange(entity, capability, ImmutableMap.copyOf(updatedContainers));

            if (!dirtyStacks.isEmpty() || !dirtyCosmeticStacks.isEmpty() || !updatedContainers.isEmpty()) {
                var packet = SyncContainerData.of(entity, updatedContainers.keySet(), dirtyStacks, dirtyCosmeticStacks);

                var networkHandler = AccessoriesInternals.getNetworkHandler();

                networkHandler.sendToTrackingAndSelf(entity, packet);
            }

            updatedContainers.clear();
        }

        //--

        var holder = ((AccessoriesHolderImpl) AccessoriesInternals.getHolder(entity));

        if(holder.loadedFromTag && capability == null) {
            var tempCapability = new AccessoriesCapabilityImpl(entity);
        }

        var invalidStacks = (holder).invalidStacks;

        if (!invalidStacks.isEmpty()) {
            for (ItemStack invalidStack : invalidStacks) {
                if (entity instanceof ServerPlayer serverPlayer) {
                    AccessoriesInternals.giveItemToPlayer(serverPlayer, invalidStack);
                } else {
                    entity.spawnAtLocation(invalidStack);
                }
            }

            invalidStacks.clear();
        }
    }

    private static void recursiveStackChange(SlotReference slotReference, @Nullable AccessoryNestContainerContents lastNestData, @Nullable AccessoryNestContainerContents currentNestData) {
        var lastInnerStacks = lastNestData != null ? List.copyOf(lastNestData.getMap(slotReference).entrySet()) : List.<Map.Entry<SlotEntryReference, Accessory>>of();
        var currentInnerStacks = currentNestData != null ? List.copyOf(currentNestData.getMap(slotReference).entrySet()) : List.<Map.Entry<SlotEntryReference, Accessory>>of();

        var maxIterationLength = Math.max(lastInnerStacks.size(), currentInnerStacks.size());

        for (int i = 0; i < maxIterationLength; i++) {
            var lastInnerEntry = (i < lastInnerStacks.size()) ? lastInnerStacks.get(i) : null;
            var currentInnerEntry = (i < currentInnerStacks.size()) ? currentInnerStacks.get(i) : null;

            if(lastInnerEntry == null && currentInnerEntry != null) {
                var currentRef = currentInnerEntry.getKey();

                AccessoryChangeCallback.EVENT.invoker().onChange(ItemStack.EMPTY, currentRef.stack(), currentRef.reference(), SlotStateChange.REPLACEMENT);
            } else if(currentInnerEntry == null && lastInnerEntry != null) {
                var lastRef = lastInnerEntry.getKey();

                AccessoryChangeCallback.EVENT.invoker().onChange(lastRef.stack(), ItemStack.EMPTY, lastRef.reference(), SlotStateChange.REPLACEMENT);
            } else if(lastInnerEntry != null && currentInnerEntry != null) {
                var currentRef = currentInnerEntry.getKey();
                var lastRef = lastInnerEntry.getKey();

                var innerRef = lastRef.reference();

                var currentInnerStack = currentRef.stack();
                var lastInnerStack = lastRef.stack();

                AccessoryChangeCallback.EVENT.invoker().onChange(lastInnerStack, currentInnerStack, innerRef, SlotStateChange.REPLACEMENT);

                recursiveStackChange(slotReference, AccessoryNestUtils.getData(lastInnerStack), AccessoryNestUtils.getData(currentInnerStack));
            }
        }
    }

    public static void getTooltipData(@Nullable LivingEntity entity, ItemStack stack, List<Component> tooltip, TooltipFlag tooltipType) {
        var accessory = AccessoriesAPI.getOrDefaultAccessory(stack);

        if (accessory != null) {
            if(entity != null && AccessoriesCapability.get(entity) != null) addEntityBasedTooltipData(entity, accessory, stack, tooltip, tooltipType);

            accessory.getExtraTooltip(stack, tooltip, tooltipType);
        }
    }

    // TODO: Rewrite for better handling of various odd cases
    private static void addEntityBasedTooltipData(LivingEntity entity, Accessory accessory, ItemStack stack, List<Component> tooltip, TooltipFlag tooltipType) {
        // TODO: MAYBE DEPENDING ON ENTITY OR SOMETHING SHOW ALL VALID SLOTS BUT COLOR CODE THEM IF NOT VALID FOR ENTITY?
        // TODO: ADD BETTER HANDLING FOR POSSIBLE SLOTS THAT ARE EQUIPABLE IN BUT IS AT ZERO SIZE
        var validSlotTypes = new HashSet<>(AccessoriesAPI.getValidSlotTypes(entity, stack));

        var validUniqueSlots = validSlotTypes.stream()
                .filter(slotType -> UniqueSlotHandling.isUniqueSlot(slotType.name()))
                .collect(Collectors.toSet());

        if (validSlotTypes.isEmpty()) return;

        validSlotTypes.removeAll(validUniqueSlots);

        var sharedSlotTypes = SlotTypeLoader.getSlotTypes(entity.level()).values()
                .stream()
                .filter(slotType -> /*slotType.amount() > 0 &&*/ !UniqueSlotHandling.isUniqueSlot(slotType.name()))
                .collect(Collectors.toSet());

        var slotInfoComponent = Component.literal("");

        var slotsComponent = Component.literal("");
        boolean allSlots = false;


        if (validSlotTypes.containsAll(sharedSlotTypes)) {
            slotsComponent.append(Component.translatable(Accessories.translation("slot.any")));
            allSlots = true;
        } else {
            var entitySlotTypes = Set.copyOf(EntitySlotLoader.getEntitySlots(entity).values());

            var differenceSlotTypes = Sets.difference(entitySlotTypes, validSlotTypes);

            if(differenceSlotTypes.size() < validSlotTypes.size()) {
                slotsComponent.append(Component.translatable(Accessories.translation("slot.any")));
                slotsComponent.append(Component.literal(" except ").withStyle(ChatFormatting.GRAY));

                var slotTypesList = List.copyOf(differenceSlotTypes);

                for (int i = 0; i < slotTypesList.size(); i++) {
                    var type = slotTypesList.get(i);

                    slotsComponent.append(Component.translatable(type.translation()).withStyle(ChatFormatting.RED));

                    if (i + 1 != slotTypesList.size()) {
                        slotsComponent.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                    }
                }
            } else {
                var slotTypesList = List.copyOf(validSlotTypes);

                for (int i = 0; i < slotTypesList.size(); i++) {
                    var type = slotTypesList.get(i);

                    slotsComponent.append(Component.translatable(type.translation()));

                    if (i + 1 != slotTypesList.size()) {
                        slotsComponent.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }

        if(!validUniqueSlots.isEmpty()) {
            var uniqueSlotTypes = List.copyOf(validUniqueSlots);

            for (int i = 0; i < uniqueSlotTypes.size(); i++) {
                var type = uniqueSlotTypes.get(i);

                slotsComponent.append(Component.translatable(type.translation()));

                if (i + 1 != uniqueSlotTypes.size()) {
                    slotsComponent.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
            }

            validSlotTypes.addAll(validUniqueSlots);
        }

        var slotTranslationKey = "slot.tooltip." + ((validSlotTypes.size() > 1 && !allSlots) ? "plural" : "singular");

        slotInfoComponent.append(
                Component.translatable(Accessories.translation(slotTranslationKey))
                        .withStyle(ChatFormatting.GRAY)
                        .append(slotsComponent.withStyle(ChatFormatting.BLUE))
        );

        tooltip.add(slotInfoComponent);

        var slotSpecificModifiers = new HashMap<SlotType, AccessoryAttributeBuilder>();
        AccessoryAttributeBuilder defaultModifiers = null;

        boolean allDuplicates = true;

        for (var slotType : validSlotTypes) {
            var reference = SlotReference.of(entity, slotType.name(), 0);

            var builder = AccessoriesAPI.getAttributeModifiers(stack, reference, true);

            slotSpecificModifiers.put(slotType, builder);

            if (defaultModifiers == null) {
                defaultModifiers = builder;
            } else if (allDuplicates) {
                // TODO: ! WARNING ! THIS MAY NOT WORK?
                allDuplicates = defaultModifiers.equals(builder);
            }
        }

        var slotTypeToTooltipInfo = new HashMap<SlotType, List<Component>>();

        if (allDuplicates) {
            if (!defaultModifiers.isEmpty()) {
                var attributeTooltip = new ArrayList<Component>();

                addAttributeTooltip(defaultModifiers.getAttributeModifiers(false), attributeTooltip);

                slotTypeToTooltipInfo.put(null, attributeTooltip);
            }
        } else {
            for (var slotModifiers : slotSpecificModifiers.entrySet()) {
                var slotType = slotModifiers.getKey();
                var modifiers = slotModifiers.getValue();

                if (modifiers.isEmpty()) continue;

                var attributeTooltip = new ArrayList<Component>();

                addAttributeTooltip(modifiers.getAttributeModifiers(false), attributeTooltip);

                slotTypeToTooltipInfo.put(slotType, attributeTooltip);
            }
        }

        var extraAttributeTooltips = new HashMap<SlotType, List<Component>>();
        List<Component> defaultExtraAttributeTooltip = null;

        boolean allDuplicatesExtras = true;

        for (var slotType : validSlotTypes) {
            var extraAttributeTooltip = new ArrayList<Component>();
            accessory.getAttributesTooltip(stack, slotType, extraAttributeTooltip, tooltipType);

            extraAttributeTooltips.put(slotType, extraAttributeTooltip);

            if (defaultExtraAttributeTooltip == null) {
                defaultExtraAttributeTooltip = extraAttributeTooltip;
            } else if (allDuplicatesExtras) {
                allDuplicatesExtras = extraAttributeTooltip.equals(defaultExtraAttributeTooltip);
            }
        }

        if (allDuplicatesExtras) {
            slotTypeToTooltipInfo.computeIfAbsent(null, s -> new ArrayList<>())
                    .addAll(defaultExtraAttributeTooltip);
        } else {
            extraAttributeTooltips.forEach((slotType, components) -> {
                slotTypeToTooltipInfo.computeIfAbsent(slotType, s -> new ArrayList<>())
                        .addAll(components);
            });
        }

        if(slotTypeToTooltipInfo.containsKey(null)) {
            var anyTooltipInfo = slotTypeToTooltipInfo.get(null);

            if (anyTooltipInfo.size() > 0) {
                tooltip.add(CommonComponents.EMPTY);

                tooltip.add(
                        Component.translatable(Accessories.translation("tooltip.attributes.any"))
                                .withStyle(ChatFormatting.GRAY)
                );

                tooltip.addAll(anyTooltipInfo);
            }

            slotTypeToTooltipInfo.remove(null);
        }

        if(!slotTypeToTooltipInfo.isEmpty()) {
            for (var entry : slotTypeToTooltipInfo.entrySet()) {
                var tooltipData = entry.getValue();

                if(tooltipData.size() == 0) continue;

                tooltip.add(CommonComponents.EMPTY);

                tooltip.add(
                        Component.translatable(
                                Accessories.translation("tooltip.attributes.slot"),
                                Component.translatable(entry.getKey().translation()).withStyle(ChatFormatting.BLUE)
                        ).withStyle(ChatFormatting.GRAY)
                );

                tooltip.addAll(entry.getValue());
            }
        }
    }

    private static void addAttributeTooltip(Multimap<Attribute, AttributeModifier> multimap, List<Component> tooltip) {
        if (multimap.isEmpty()) return;

        for (Map.Entry<Attribute, AttributeModifier> entry : multimap.entries()) {
            AttributeModifier attributeModifier = entry.getValue();
            double d = attributeModifier.getAmount();

            if (attributeModifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE
                    || attributeModifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                d *= 100.0;
            } else if (entry.getKey().equals(Attributes.KNOCKBACK_RESISTANCE)) {
                d *= 10.0;
            }

            var key = entry.getKey();

            if (d > 0.0) {
                tooltip.add(
                        Component.translatable(
                                        "attribute.modifier.plus." + attributeModifier.getOperation().toValue(),
                                        ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d),
                                        Component.translatable(key.getDescriptionId())
                                )
                                .withStyle(ChatFormatting.BLUE)
                );
            } else if (d < 0.0) {
                d *= -1.0;
                tooltip.add(
                        Component.translatable(
                                        "attribute.modifier.take." + attributeModifier.getOperation().toValue(),
                                        ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d),
                                        Component.translatable(key.getDescriptionId())
                                )
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    public static List<ItemStack> onDeath(LivingEntity entity, DamageSource source) {
        var capability = AccessoriesCapability.get(entity);

        var droppedStacks = new ArrayList<ItemStack>();

        if (capability != null) {
            for (var containerEntry : capability.getContainers().entrySet()) {
                var slotType = containerEntry.getValue().slotType();

                var slotDropRule = slotType != null ? slotType.dropRule() : DropRule.DEFAULT;

                var container = containerEntry.getValue();

                var stacks = container.getAccessories();
                var cosmeticStacks = container.getCosmeticAccessories();

                for (int i = 0; i < container.getSize(); i++) {
                    var reference = SlotReference.of(entity, container.getSlotName(), i);

                    var stack = dropStack(slotDropRule, entity, stacks, reference, source);
                    if (stack != null) droppedStacks.add(stack);

                    var cosmeticStack = dropStack(slotDropRule, entity, cosmeticStacks, reference, source);
                    if (cosmeticStack != null) droppedStacks.add(cosmeticStack);
                }
            }

            var result = OnDeathCallback.EVENT.invoker().shouldDrop(TriState.DEFAULT, entity, capability, source, droppedStacks);

            if (!result.orElse(true)) return List.of();
        }

        return droppedStacks;
    }

    @Nullable
    private static ItemStack dropStack(DropRule dropRule, LivingEntity entity, Container container, SlotReference reference, DamageSource source) {
        var stack = container.getItem(reference.slot());
        var accessory = AccessoriesAPI.getAccessory(stack);

        if (accessory != null && dropRule == DropRule.DEFAULT) {
            dropRule = accessory.getDropRule(stack, reference, source);
        }

        if (accessory instanceof AccessoryNest holdable) {
            var dropRuleToStacks = holdable.getDropRules(stack, reference, source);

            for (int i = 0; i < dropRuleToStacks.size(); i++) {
                var rulePair = dropRuleToStacks.get(i);

                var innerStack = rulePair.right();

                var result = OnDropCallback.getAlternativeRule(rulePair.left(), innerStack, reference, source);

                var breakInnerStack = (result == DropRule.DEFAULT && EnchantmentHelper.hasVanishingCurse(innerStack))
                        || (result == DropRule.DESTROY);

                if (breakInnerStack) {
                    holdable.setInnerStack(stack, i, ItemStack.EMPTY);
                    // TODO: Do we call break here for the accessory?

                    container.setItem(reference.slot(), stack);
                }
            }
        }

        var result = OnDropCallback.getAlternativeRule(dropRule, stack, reference, source);

        boolean dropStack = true;

        if (result == DropRule.DESTROY) {
            container.setItem(reference.slot(), ItemStack.EMPTY);
            dropStack = false;
            // TODO: Do we call break here for the accessory?
        } else if (result == DropRule.KEEP) {
            dropStack = false;
        } else if (result == DropRule.DEFAULT) {
            if (entity.level().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).get()) {
                dropStack = false;
            } else if (EnchantmentHelper.hasVanishingCurse(stack)) {
                container.setItem(reference.slot(), ItemStack.EMPTY);
                dropStack = false;
                // TODO: Do we call break here for the accessory?
            }
        }

        if (!dropStack) return null;

        container.setItem(reference.slot(), ItemStack.EMPTY);

        return stack;
    }

    public static InteractionResultHolder<ItemStack> attemptEquipFromUse(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        var capability = AccessoriesCapability.get(player);

        if(capability != null && !player.isSpectator() && !stack.isEmpty()) {
            var equipControl = capability.getHolder().equipControl();

            var shouldAttemptEquip = false;

            if(equipControl == PlayerEquipControl.MUST_CROUCH && player.isShiftKeyDown()) {
                shouldAttemptEquip = true;
            } else if(equipControl == PlayerEquipControl.MUST_NOT_CROUCH && !player.isShiftKeyDown()) {
                shouldAttemptEquip = true;
            }

            if (shouldAttemptEquip) {
                var accessory = AccessoriesAPI.getOrDefaultAccessory(stack);

                var equipReference = capability.canEquipAccessory(stack, true);

                if (equipReference != null && accessory.canEquipFromUse(stack)) {
                    accessory.onEquipFromUse(stack, equipReference.left());

                    var newHandStack = stack.copy();

                    var possibleSwappedStack = equipReference.second().equipStack(newHandStack);

                    if(possibleSwappedStack.isPresent()) {
                        var swappedStack = possibleSwappedStack.get();

                        if (newHandStack.isEmpty()) {
                            newHandStack = swappedStack;
                        } else if(ItemStack.isSameItemSameTags(newHandStack, swappedStack) && (newHandStack.getCount() + swappedStack.getCount()) <= newHandStack.getMaxStackSize()) {
                            newHandStack.grow(swappedStack.getCount());
                        } else {
                            player.addItem(swappedStack);
                        }
                    }

                    return InteractionResultHolder.success(newHandStack);
                }
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    public static final TagKey<EntityType<?>> EQUIPMENT_MANAGEABLE = TagKey.create(Registries.ENTITY_TYPE, Accessories.of("equipment_manageable"));

    public static InteractionResult attemptEquipOnEntity(Player player, InteractionHand hand, Entity entity) {
        var stack = player.getItemInHand(hand);

        if(!(entity instanceof LivingEntity targetEntity) || !entity.getType().is(EQUIPMENT_MANAGEABLE)) return InteractionResult.PASS;

        var targetCapability = AccessoriesCapability.get(targetEntity);

        var canModify = AllowEntityModificationCallback.EVENT.invoker().allowModifications(targetEntity, player, null).orElse(false);

        if (canModify && targetCapability != null && !player.isSpectator()) {
            if (player.isShiftKeyDown()) {
                var accessory = AccessoriesAPI.getOrDefaultAccessory(stack);

                var equipReference = targetCapability.canEquipAccessory(stack, true);

                if (equipReference != null && accessory.canEquipFromUse(stack)) {
                    if(!stack.isEmpty()) accessory.onEquipFromUse(stack, equipReference.left());

                    var newHandStack = stack.copy();

                    var possibleSwappedStack = equipReference.second().equipStack(newHandStack);

                    if(possibleSwappedStack.isPresent()) {
                        var swappedStack = possibleSwappedStack.get();

                        if (newHandStack.isEmpty()) {
                            newHandStack = swappedStack;
                        } else if(ItemStack.isSameItemSameTags(newHandStack, swappedStack) && (newHandStack.getCount() + swappedStack.getCount()) <= newHandStack.getMaxStackSize()) {
                            newHandStack.grow(swappedStack.getCount());
                        } else {
                            player.addItem(swappedStack);
                        }
                    }

                    player.setItemInHand(hand, newHandStack);

                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }

//    public static void setupItems(AddDataComponentCallback callback) {
//        AccessoriesAPI.getAllAccessories().forEach((item, accessory) -> {
//            var builder = AccessoryItemAttributeModifiers.builder();
//
//            accessory.getStaticModifiers(item, builder);
//
//            if(!builder.isEmpty()) {
//                callback.addTo(item, AccessoriesDataComponents.ATTRIBUTES, builder.build());
//            }
//        });
//    }

//    public interface AddDataComponentCallback {
//        <T> void addTo(Item item, DataComponentType<T> componentType, T component);
//    }
}
