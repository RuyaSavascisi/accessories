package io.wispforest.accessories.impl;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.endec.NbtMapCarrier;
import io.wispforest.accessories.impl.caching.AccessoriesHolderLookupCache;
import io.wispforest.accessories.utils.InstanceEndec;
import io.wispforest.owo.serialization.RegistriesAttribute;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import io.wispforest.accessories.utils.EndecUtils;
import io.wispforest.endec.Endec;
import io.wispforest.endec.SerializationAttribute;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.impl.KeyedEndec;
import io.wispforest.endec.util.MapCarrier;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

@ApiStatus.Internal
public class AccessoriesHolderImpl implements InstanceEndec {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final MapCarrier EMPTY = new NbtMapCarrier(new CompoundTag());

    private final Map<String, AccessoriesContainer> slotContainers = new LinkedHashMap<>();

    public final List<ItemStack> invalidStacks = new ArrayList<>();
    protected final Map<AccessoriesContainer, Boolean> containersRequiringUpdates = new HashMap<>();

    //-- Logical Stuff

    private PlayerEquipControl equipControl = PlayerEquipControl.MUST_NOT_CROUCH;

    //--

    //-- Rendering Stuff

    private boolean showAdvancedOptions = false;

    private boolean showUnusedSlots = false;

    private boolean showCosmetics = false;

    private int columnAmount = 1;
    private int widgetType = 2;
    private boolean showGroupFilter = false;
    private boolean mainWidgetPosition = true;
    private boolean sideWidgetPosition = false;

    private boolean showCraftingGrid = false;

    // --

    private MapCarrier carrier;
    protected boolean loadedFromTag = false;

    public AccessoriesHolderImpl(){}

    public static AccessoriesHolderImpl of(){
        var holder = new AccessoriesHolderImpl();

        holder.loadedFromTag = true;
        holder.carrier = EMPTY;

        return holder;
    }

    @Nullable
    public static AccessoriesHolderImpl getHolder(LivingEntity livingEntity) {
        var capability = livingEntity.accessoriesCapability();

        if (capability == null) return null;

        return getHolder(capability);
    }

    public static AccessoriesHolderImpl getHolder(AccessoriesCapability capability) {
        var entity = capability.entity();

        var holder = ((AccessoriesHolderImpl) AccessoriesInternals.getHolder(entity));

        // Attempts to reset the container when loaded from tag on the server
        if (holder.loadedFromTag) capability.reset(true);

        // Prevents containers from not existing even if a given entity will have such slots but have yet to be synced to the client
        if (holder.getSlotContainers().size() != EntitySlotLoader.getEntitySlots(entity).size()) holder.init(capability);

        return holder;
    }

    @ApiStatus.Internal
    protected Map<String, AccessoriesContainer> getAllSlotContainers() {
        return this.slotContainers;
    }

    @Nullable
    private Map<String, AccessoriesContainer> validSlotContainers = null;

    public void setValidTypes(Set<String> validTypes) {
        var validSlotContainers = ImmutableMap.<String, AccessoriesContainer>builder();

        this.slotContainers.forEach((string, container) -> {
            if (validTypes.contains(container.getSlotName())) validSlotContainers.put(string, container);
        });

        this.validSlotContainers = validSlotContainers.build();

        if (this.lookupCache == null) {
            this.lookupCache = new AccessoriesHolderLookupCache(this);
        }

        this.lookupCache.clearCache();
    }

    @ApiStatus.Internal
    public Map<String, AccessoriesContainer> getSlotContainers() {
        return this.validSlotContainers != null ? this.validSlotContainers : this.getAllSlotContainers();
    }

    @Nullable
    public AccessoriesHolderLookupCache lookupCache = null;

    @Nullable
    public AccessoriesHolderLookupCache getLookupCache() {
        return Accessories.config().useExperimentalCaching() ? this.lookupCache : null;
    }

    //--

    public PlayerEquipControl equipControl() {
        return equipControl;
    }

    public AccessoriesHolderImpl equipControl(PlayerEquipControl value) {
        this.equipControl = value;

        return this;
    }

    //--

    public boolean showUnusedSlots() {
        return this.showUnusedSlots;
    }

    public AccessoriesHolderImpl showUnusedSlots(boolean value) {
        this.showUnusedSlots = value;

        return this;
    }

    public boolean cosmeticsShown() {
        return this.showCosmetics;
    }

    public AccessoriesHolderImpl cosmeticsShown(boolean value) {
        this.showCosmetics = value;

        return this;
    }

    public int columnAmount() {
        return Math.max(columnAmount, 1);
    }

    public AccessoriesHolderImpl columnAmount(int value) {
        this.columnAmount = value;

        return this;
    }

    public int widgetType() {
        return Math.max(widgetType, 1);
    }

    public AccessoriesHolderImpl widgetType(int value) {
        this.widgetType = value;

        return this;
    }

    public boolean mainWidgetPosition() {
        return this.mainWidgetPosition;
    }

    public AccessoriesHolderImpl mainWidgetPosition(boolean value) {
        this.mainWidgetPosition = value;

        return this;
    }

    public boolean showAdvancedOptions() {
        return this.showAdvancedOptions;
    }

    public AccessoriesHolderImpl showAdvancedOptions(boolean value) {
        this.showAdvancedOptions = value;

        return this;
    }

    public boolean showGroupFilter() {
        return this.showGroupFilter;
    }

    public AccessoriesHolderImpl showGroupFilter(boolean value) {
        this.showGroupFilter = value;

        return this;
    }

    private boolean isGroupFiltersOpen = true;

    public boolean isGroupFiltersOpen() {
        return isGroupFiltersOpen;
    }

    public AccessoriesHolderImpl isGroupFiltersOpen(boolean value) {
        this.isGroupFiltersOpen = value;

        return this;
    }

    private Set<String> filteredGroups = Set.of();

    public Set<String> filteredGroups() {
        return filteredGroups;
    }

    public AccessoriesHolderImpl filteredGroups(Set<String> value) {
        this.filteredGroups = value;

        return this;
    }

    public boolean sideWidgetPosition() {
        return this.sideWidgetPosition;
    }

    public AccessoriesHolderImpl sideWidgetPosition(boolean value) {
        this.sideWidgetPosition = value;

        return this;
    }

    public boolean showCraftingGrid() {
        return this.showCraftingGrid;
    }

    public AccessoriesHolderImpl showCraftingGrid(boolean value) {
        this.showCraftingGrid = value;

        return this;
    }

    //--

    public void init(AccessoriesCapability capability) {
        var livingEntity = capability.entity();

        //this.slotContainers.clear();

        var entitySlots = EntitySlotLoader.getEntitySlots(livingEntity);

        //LOGGER.error("Entity Slots for [{}]: {}", livingEntity, entitySlots.keySet());

        if(livingEntity instanceof Player && entitySlots.isEmpty()) {
            LOGGER.warn("It seems the given player has no slots bound to it within a init call, is that desired?");
        }

        this.validSlotContainers = null;

        if (loadedFromTag) {
            entitySlots.forEach((s, slotType) -> {
                this.slotContainers.putIfAbsent(s, new AccessoriesContainerImpl(capability, slotType));
            });

            var ctx = SerializationContext.attributes(
                    new EntityAttribute(livingEntity),
                    RegistriesAttribute.of(livingEntity.registryAccess())
            );

            read(capability, livingEntity, this.carrier, ctx);
        } else {
            entitySlots.forEach((s, slotType) -> {
                this.slotContainers.put(s, new AccessoriesContainerImpl(capability, slotType));
            });
        }

        this.setValidTypes(entitySlots.keySet());
    }

    // TODO: SPLIT DECODING AND VALIDATION SAFETY DOWN THE ROAD
    private static final KeyedEndec<Map<String, AccessoriesContainer>> CONTAINERS_KEY = NbtEndec.COMPOUND.xmapWithContext(
            (ctx, containersMap) -> {
                var entity = ctx.requireAttributeValue(EntityAttribute.ENTITY).livingEntity();
                var slotContainers = ctx.requireAttributeValue(ContainersAttribute.CONTAINERS).slotContainers();
                var invalidStacks = ctx.requireAttributeValue(InvalidStacksAttribute.INVALID_STACKS).invalidStacks();

                var slots = EntitySlotLoader.getEntitySlots(entity);

                for (var key : containersMap.getAllKeys()) {
                    var containerElement = containersMap.getCompound(key);

                    if (containerElement.isEmpty()) continue; // TODO: Handle this case?

                    if (slots.containsKey(key)) {
                        var container = slotContainers.get(key);
                        var prevAccessories = AccessoriesContainerImpl.copyContainerList(container.getAccessories());
                        var prevCosmetics = AccessoriesContainerImpl.copyContainerList(container.getCosmeticAccessories());

                        ((AccessoriesContainerImpl) container).read(new NbtMapCarrier(containerElement), ctx);

                        if (prevAccessories.getContainerSize() > container.getSize()) {
                            for (int i = container.getSize() - 1; i < prevAccessories.getContainerSize(); i++) {
                                var prevStack = prevAccessories.getItem(i);

                                if (!prevStack.isEmpty()) invalidStacks.add(prevStack);

                                var prevCosmetic = prevCosmetics.getItem(i);

                                if (!prevCosmetic.isEmpty()) invalidStacks.add(prevCosmetic);
                            }
                        }
                    } else {
                        var containers = AccessoriesContainerImpl.readContainers(new NbtMapCarrier(containerElement), ctx, AccessoriesContainerImpl.COSMETICS_KEY, AccessoriesContainerImpl.ITEMS_KEY);

                        for (var simpleContainer : containers) {
                            for (int i = 0; i < simpleContainer.getContainerSize(); i++) {
                                var stack = simpleContainer.getItem(i);

                                if (!stack.isEmpty()) invalidStacks.add(stack);
                            }
                        }
                    }
                }

                return slotContainers;
            }, (ctx, containers) -> {
                var containerMap = new CompoundTag();

                containers.forEach((s, container) -> {
                    containerMap.put(s, Util.make(NbtMapCarrier.of(), innerCarrier -> ((AccessoriesContainerImpl) container).write(innerCarrier, ctx)).compoundTag());
                });

                return containerMap;
            }).keyed("accessories_containers", HashMap::new);

    private static final KeyedEndec<PlayerEquipControl> EQUIP_CONTROL_KEY = Endec.forEnum(PlayerEquipControl.class).keyed("equip_control", PlayerEquipControl.MUST_CROUCH);

    private static final KeyedEndec<Boolean> SHOW_UNUSED_SLOTS_KEY = Endec.BOOLEAN.keyed("show_unused_slots", false);
    private static final KeyedEndec<Boolean> SHOW_COSMETICS_KEY = Endec.BOOLEAN.keyed("show_cosmetics", false);

    private static final KeyedEndec<Integer> COLUMN_AMOUNT_KEY = Endec.INT.keyed("column_amount", 1);
    private static final KeyedEndec<Integer> WIDGET_TYPE_KEY = Endec.INT.keyed("widget_type", 2);
    private static final KeyedEndec<Boolean> MAIN_WIDGET_POSITION = Endec.BOOLEAN.keyed("main_widget_position", true);
    private static final KeyedEndec<Boolean> SIDE_WIDGET_POSITION = Endec.BOOLEAN.keyed("side_widget_position", false);

    private static final KeyedEndec<Boolean> SHOW_GROUP_FILTER = Endec.BOOLEAN.keyed("show_group_filter", false);
    private static final KeyedEndec<Boolean> IS_GROUP_FILTERS_OPEN_KEY = Endec.BOOLEAN.keyed("is_group_filter_open", false);
    private static final KeyedEndec<Set<String>> FILTERED_GROUPS_KEY = Endec.STRING.setOf().keyed("filtered_groups", HashSet::new);

    private static final KeyedEndec<Boolean> SHOW_CRAFTING_GRID = Endec.BOOLEAN.keyed("cosmetics_shown", false);

    @Override
    public void write(MapCarrier carrier, SerializationContext ctx) {
        if(slotContainers.isEmpty()) return;

        carrier.put(ctx, CONTAINERS_KEY, this.slotContainers);

        carrier.put(ctx, EQUIP_CONTROL_KEY, this.equipControl);

        carrier.put(ctx, COLUMN_AMOUNT_KEY, this.columnAmount);
        carrier.put(ctx, WIDGET_TYPE_KEY, this.widgetType);
        carrier.put(ctx, MAIN_WIDGET_POSITION, this.mainWidgetPosition);
        carrier.put(ctx, SIDE_WIDGET_POSITION, this.sideWidgetPosition);

        carrier.put(ctx, SHOW_COSMETICS_KEY, this.showCosmetics);
        carrier.put(ctx, SHOW_UNUSED_SLOTS_KEY, this.showUnusedSlots);

        carrier.put(ctx, SHOW_GROUP_FILTER, this.showGroupFilter);
        carrier.put(ctx, IS_GROUP_FILTERS_OPEN_KEY, this.isGroupFiltersOpen);
        carrier.put(ctx, FILTERED_GROUPS_KEY, this.filteredGroups);

        carrier.put(ctx, SHOW_CRAFTING_GRID, this.showCraftingGrid);
    }

    public void read(LivingEntity entity, MapCarrier carrier, SerializationContext ctx) {
        read(entity.accessoriesCapability(), entity, carrier, ctx);
    }

    public void read(AccessoriesCapability capability, LivingEntity entity, MapCarrier carrier, SerializationContext ctx) {
        this.loadedFromTag = false;

        EndecUtils.dfuKeysCarrier(
                carrier,
                Map.of(
                        "AccessoriesContainers", "accessories_containers",
                        "CosmeticsShown", "cosmetics_shown",
                        "LinesShown", "lines_shown",
                        "EquipControl", "equip_control"
                ));

        carrier.getWithErrors(ctx.withAttributes(new ContainersAttribute(this.slotContainers), new InvalidStacksAttribute(this.invalidStacks)), CONTAINERS_KEY);

        this.equipControl = carrier.get(ctx, EQUIP_CONTROL_KEY);

        this.columnAmount = carrier.get(ctx, COLUMN_AMOUNT_KEY);
        this.widgetType = carrier.get(ctx, WIDGET_TYPE_KEY);
        this.mainWidgetPosition = carrier.get(ctx, MAIN_WIDGET_POSITION);
        this.sideWidgetPosition = carrier.get(ctx, SIDE_WIDGET_POSITION);

        this.showCosmetics = carrier.get(ctx, SHOW_COSMETICS_KEY);
        this.showUnusedSlots = carrier.get(ctx, SHOW_UNUSED_SLOTS_KEY);

        this.showGroupFilter = carrier.get(ctx, SHOW_GROUP_FILTER);
        this.isGroupFiltersOpen = carrier.get(ctx, IS_GROUP_FILTERS_OPEN_KEY);
        this.filteredGroups = carrier.get(ctx, FILTERED_GROUPS_KEY);

        this.showCraftingGrid = carrier.get(ctx, SHOW_CRAFTING_GRID);

        capability.clearCachedSlotModifiers();

        this.carrier = EMPTY;

        var cache = this.getLookupCache();

        if (cache != null) cache.clearCache();
    }

    @Override
    public void read(MapCarrier carrier, SerializationContext context) {
        this.loadedFromTag = true;

        this.carrier = carrier;
    }

    @Deprecated(forRemoval = true)
    public boolean showUniqueSlots() {
        return false;
    }

    @Deprecated(forRemoval = true)
    public AccessoriesHolderImpl showUniqueSlots(boolean value) {
        return this;
    }

    @Deprecated(forRemoval = true)
    public boolean linesShown() {
        return false;
    }

    @Deprecated(forRemoval = true)
    public AccessoriesHolderImpl linesShown(boolean value) {
        return this;
    }

    private record ContainersAttribute(Map<String, AccessoriesContainer> slotContainers) implements SerializationAttribute.Instance {
        public static final SerializationAttribute.WithValue<ContainersAttribute> CONTAINERS = SerializationAttribute.withValue(Accessories.translationKey("containers"));

        @Override public SerializationAttribute attribute() { return CONTAINERS; }
        @Override public Object value() { return this; }
    }

    private record InvalidStacksAttribute(List<ItemStack> invalidStacks) implements SerializationAttribute.Instance {
        public static final SerializationAttribute.WithValue<InvalidStacksAttribute> INVALID_STACKS = SerializationAttribute.withValue(Accessories.translationKey("invalidStacks"));

        @Override public SerializationAttribute attribute() { return INVALID_STACKS; }
        @Override public Object value() { return this; }
    }

    private record EntityAttribute(LivingEntity livingEntity) implements SerializationAttribute.Instance{
        public static final SerializationAttribute.WithValue<EntityAttribute> ENTITY = SerializationAttribute.withValue("entity");

        @Override public SerializationAttribute attribute() { return ENTITY; }
        @Override public Object value() { return this;}
    }
}