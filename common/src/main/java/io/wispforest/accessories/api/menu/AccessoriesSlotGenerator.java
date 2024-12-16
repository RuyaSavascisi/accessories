package io.wispforest.accessories.api.menu;

import com.mojang.logging.LogUtils;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.SlotTypeReference;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.data.EntitySlotLoader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Slot Generator to be used for slots generator from {@link UniqueSlotHandling} event hooks
 * to have easier time to add Accessories slots to a given owner of the unique slots
 */
public class AccessoriesSlotGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final AccessoriesCapability capability;

    private final Consumer<Slot> slotConsumer;

    private List<SlotType> slotTypes = List.of();

    private int horizontalPadding = 0, verticalPadding = 0;
    private int startX, startY;

    private AccessoriesSlotGenerator(Consumer<Slot> slotConsumer, int startX, int startY, AccessoriesCapability capability) {
        this.slotConsumer = slotConsumer;

        this.startX = startX;
        this.startY = startY;

        this.capability = capability;
    }

    @Nullable
    public static AccessoriesSlotGenerator of(Consumer<Slot> slotConsumer, int startX, int startY, LivingEntity livingEntity, SlotTypeReference... references) {
        var capability = livingEntity.accessoriesCapability();

        if(capability == null) return null;

        return new AccessoriesSlotGenerator(slotConsumer, startX, startY, capability).adjustTypes(references);
    }

    @Nullable
    public static AccessoriesSlotGenerator of(Consumer<Slot> slotConsumer, int startX, int startY, LivingEntity livingEntity, SlotType... slotTypes) {
        var capability = livingEntity.accessoriesCapability();

        if(capability == null) return null;

        return new AccessoriesSlotGenerator(slotConsumer, startX, startY, capability).adjustTypes(slotTypes);
    }

    /**
     * Attempts to create a given {@link AccessoriesSlotGenerator} using the given start position and the given {@link SlotTypeReference}'s passed.
     * The passed consumer will most likely be the {@code addSlot} within the given Menu. The returned {@link AccessoriesSlotGenerator} will only be null
     * if the entity was found not to have an {@link AccessoriesCapability} bound to it.
     */
    @Nullable
    public static AccessoriesSlotGenerator of(Consumer<Slot> slotConsumer, int startX, int startY, LivingEntity livingEntity) {
        var capability = livingEntity.accessoriesCapability();

        if(capability == null) return null;

        return new AccessoriesSlotGenerator(slotConsumer, startX, startY, capability);
    }

    /**
     * Adjust the given padding of the generator for all sides of a generated slot
     */
    public AccessoriesSlotGenerator padding(int value) {
        this.horizontalPadding(value);
        this.verticalPadding(value);

        return this;
    }

    /**
     * Adjust the given padding of the generator for the left and right of a generated slot
     */
    public AccessoriesSlotGenerator horizontalPadding(int value) {
        this.horizontalPadding = value;

        return this;
    }

    /**
     * Adjust the given padding of the generator for the top and bottom of a generated slot
     */
    public AccessoriesSlotGenerator verticalPadding(int value) {
        this.verticalPadding = value;

        return this;
    }

    public AccessoriesSlotGenerator setX(int x) {
        this.startX = x;

        return this;
    }

    public AccessoriesSlotGenerator setY(int y) {
        this.startY = y;

        return this;
    }

    public AccessoriesSlotGenerator moveX(int x) {
        this.startX += x;

        return this;
    }

    public AccessoriesSlotGenerator moveY(int y) {
        this.startY += y;

        return this;
    }

    public AccessoriesSlotGenerator adjustTypes(SlotTypeReference... references) {
        var level = capability.entity().level();

        var slotTypes = Arrays.stream(references).map(ref -> {
            var slotType = ref.get(level);

            if(slotType == null) {
                LOGGER.error("Unable to find the SlotType based on the passed reference! [SlotName: {}]", ref.slotName());
            }

            return slotType;
        }).filter(Objects::nonNull).toArray(SlotType[]::new);

        return adjustTypes(slotTypes);
    }

    public AccessoriesSlotGenerator adjustTypes(SlotType... slotTypes) {
        return adjustTypes(List.of(slotTypes));
    }

    public AccessoriesSlotGenerator adjustTypes(List<SlotType> slotTypes) {
        var livingEntity = capability.entity();

        var validEntitySlotTypes = EntitySlotLoader.getEntitySlots(livingEntity).values();

        var validSlotTypes = new ArrayList<SlotType>();

        for (var slotType : slotTypes) {
            if(!validEntitySlotTypes.contains(slotType)){
                LOGGER.error("Unable to create Accessory Slot due to the given LivingEntity not having the given SlotType bound to it! [EntityType: {}, SlotType: {}]", livingEntity.getType(), slotType.name());

                continue;
            }

            validSlotTypes.add(slotType);
        }

        this.slotTypes = validSlotTypes;

        return this;
    }

    /**
     * Layout the given slots based as a row from the given starting position
     */
    public int row() {
        return layoutSlots(LayoutType.ROW);
    }

    /**
     * Layout the given slots based as a column from the given starting position
     */
    public int column() {
        return layoutSlots(LayoutType.COLUMN);
    }

    private int layoutSlots(LayoutType type) {
        int xOffset = this.horizontalPadding;
        int yOffset = this.verticalPadding;

        int slotAddedAmount = 0;

        var allContainers = capability.getContainers();

        var containers = slotTypes.stream()
                .map(slotType -> {
                    var container = allContainers.getOrDefault(slotType.name(), null);

                    if(container == null){
                        LOGGER.error("Unable to locate the given container for the passed slotType. [Type: {}]", slotType.name());
                    }

                    return container;
                }).filter(Objects::nonNull).toList();

        for (var container : containers) {
            for (int i = 0; i < container.getSize(); i++) {
                slotConsumer.accept(new AccessoriesBasedSlot(container, container.getAccessories(), i, this.startX + xOffset, this.startY + yOffset));

                if(type == LayoutType.COLUMN) {
                    yOffset += (this.verticalPadding) + 18;
                } else  {
                    xOffset += (this.horizontalPadding) + 18;
                }

                slotAddedAmount++;
            }
        }

        return slotAddedAmount;
    }

    public enum LayoutType {
        COLUMN,
        ROW;
    }
}
