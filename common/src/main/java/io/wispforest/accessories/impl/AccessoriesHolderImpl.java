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
import io.wispforest.endec.SerializationAttribute;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.impl.KeyedEndec;
import io.wispforest.endec.util.MapCarrier;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import static io.wispforest.accessories.impl.AccessoriesPlayerOptions.*;

@ApiStatus.Internal
public class AccessoriesHolderImpl implements InstanceEndec {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final MapCarrier EMPTY = new NbtMapCarrier(new CompoundTag());

    private final Map<String, AccessoriesContainer> slotContainers = new LinkedHashMap<>();

    public final List<ItemStack> invalidStacks = new ArrayList<>();

    protected final Map<AccessoriesContainer, Boolean> containersRequiringUpdates = new HashMap<>();

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

    @Override
    public void write(MapCarrier carrier, SerializationContext ctx) {
        if(slotContainers.isEmpty()) return;

        carrier.put(ctx, CONTAINERS_KEY, this.slotContainers);
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

        // TODO: REMOVE WITHIN THE FUTURE WHEN A GOOD AMOUNT OF TIME TO TRANSITION HAS OCCURRED
        if (entity instanceof ServerPlayer player) {
            var equipControl = carrier.get(ctx, EQUIP_CONTROL_KEY);

            var columnAmount = carrier.get(ctx, COLUMN_AMOUNT_KEY);
            var widgetType = carrier.get(ctx, WIDGET_TYPE_KEY);
            var mainWidgetPosition = carrier.get(ctx, MAIN_WIDGET_POSITION);
            var sideWidgetPosition = carrier.get(ctx, SIDE_WIDGET_POSITION);

            var showCosmetics = carrier.get(ctx, SHOW_COSMETICS_KEY);
            var showUnusedSlots = carrier.get(ctx, SHOW_UNUSED_SLOTS_KEY);

            var showGroupFilter = carrier.get(ctx, SHOW_GROUP_FILTER);
            var isGroupFiltersOpen = carrier.get(ctx, IS_GROUP_FILTERS_OPEN_KEY);
            var filteredGroups = carrier.get(ctx, FILTERED_GROUPS_KEY);

            var showCraftingGrid = carrier.get(ctx, SHOW_CRAFTING_GRID);

            AccessoriesPlayerOptions.getOptions(player)
                    .equipControl(equipControl)
                    .columnAmount(columnAmount)
                    .widgetType(widgetType)
                    .mainWidgetPosition(mainWidgetPosition)
                    .sideWidgetPosition(sideWidgetPosition)
                    .showCosmetics(showCosmetics)
                    .showUnusedSlots(showUnusedSlots)
                    .showGroupFilter(showGroupFilter)
                    .isGroupFiltersOpen(isGroupFiltersOpen)
                    .filteredGroups(filteredGroups)
                    .showCraftingGrid(showCraftingGrid);
        }

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