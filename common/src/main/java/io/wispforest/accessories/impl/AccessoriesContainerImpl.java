package io.wispforest.accessories.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.ExtraSlotTypeProperties;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.utils.InstanceEndec;
import io.wispforest.owo.serialization.RegistriesAttribute;
import io.wispforest.accessories.utils.AttributeUtils;
import io.wispforest.accessories.utils.EndecUtils;
import io.wispforest.endec.Endec;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.impl.KeyedEndec;
import io.wispforest.endec.util.MapCarrier;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public class AccessoriesContainerImpl implements AccessoriesContainer, InstanceEndec, ContainerListener {

    protected AccessoriesCapability capability;
    private String slotName;

    protected final Map<ResourceLocation, AttributeModifier> modifiers = new HashMap<>();
    protected final Set<AttributeModifier> persistentModifiers = new HashSet<>();
    protected final Set<AttributeModifier> cachedModifiers = new HashSet<>();

    private final Multimap<AttributeModifier.Operation, AttributeModifier> modifiersByOperation = HashMultimap.create();

    @Nullable
    private Integer baseSize;

    private List<Boolean> renderOptions;

    private ExpandedSimpleContainer accessories;
    private ExpandedSimpleContainer cosmeticAccessories;

    private boolean update = false;
    private boolean resizingUpdate = false;

    public AccessoriesContainerImpl(AccessoriesCapability capability, SlotType slotType){
        this.capability = capability;

        this.slotName = slotType.name();
        this.baseSize = slotType.amount();

        this.accessories = new ExpandedSimpleContainer(this, this.baseSize, "accessories", false);
        this.cosmeticAccessories = new ExpandedSimpleContainer(this, this.baseSize, "cosmetic_accessories", false);

        this.renderOptions = getWithSize(baseSize, new ArrayList<>(), true);
    }

    protected boolean containerListenerLock = false;

    @Override
    public void containerChanged(Container container) {
        if(containerListenerLock) return;

        if (!this.capability.entity().level().isClientSide()) {
            var cache = AccessoriesHolderImpl.getHolder(this.capability()).getLookupCache();

            if (cache != null) cache.clearContainerCache(this.slotName);
        }

        if(((ExpandedSimpleContainer) container).name().contains("cosmetic")) return;

        this.markChanged();
        this.update();
    }

    @Nullable
    public Integer getBaseSize(){
        return this.baseSize;
    }

    @Override
    public void markChanged(boolean resizingUpdate){
        this.update = true;
        this.resizingUpdate = resizingUpdate;

        if(this.capability.entity().level().isClientSide) return;

        var inv = AccessoriesHolderImpl.getHolder(this.capability).containersRequiringUpdates;

        inv.remove(this);
        inv.put(this, resizingUpdate);
    }

    @Override
    public boolean hasChanged() {
        return this.update;
    }

    public void update(){
        var hasChangeOccurred = !this.resizingUpdate;

        if(!update) return;

        this.update = false;

        if(this.capability.entity().level().isClientSide) return;

        var slotType = this.slotType();

        if(this.baseSize == null) this.baseSize = 0;

        if (slotType != null && this.baseSize != slotType.amount()) {
            this.baseSize = slotType.amount();

            hasChangeOccurred = true;
        }

        double baseSize = this.baseSize;

        double size;

        if(ExtraSlotTypeProperties.getProperty(this.slotName, false).allowResizing()) {
            for (AttributeModifier modifier : this.getModifiersForOperation(AttributeModifier.Operation.ADD_VALUE)) {
                baseSize += modifier.amount();
            }

            size = baseSize;

            for (AttributeModifier modifier : this.getModifiersForOperation(AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
                size += (this.baseSize * modifier.amount());
            }

            for (AttributeModifier modifier : this.getModifiersForOperation(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
                size *= modifier.amount();
            }
        } else {
            size = baseSize;
        }

        //--

        var holder = AccessoriesHolderImpl.getHolder(this.capability());

        var currentSize = (int) Math.round(size);

        if(currentSize != this.accessories.getContainerSize()) {
            hasChangeOccurred = true;

            var invalidAccessories = new ArrayList<Pair<Integer, ItemStack>>();

            var invalidStacks = new ArrayList<ItemStack>();

            this.containerListenerLock = true;

            var newAccessories = new ExpandedSimpleContainer(this, currentSize, "accessories");
            var newCosmetics = new ExpandedSimpleContainer(this, currentSize, "cosmetic_accessories");

            for (int i = 0; i < this.accessories.getContainerSize(); i++) {
                if (i < newAccessories.getContainerSize()) {
                    newAccessories.setItem(i, this.accessories.getItem(i));
                    newCosmetics.setItem(i, this.cosmeticAccessories.getItem(i));
                } else {
                    invalidAccessories.add(Pair.of(i, this.accessories.getItem(i)));
                    invalidStacks.add(this.cosmeticAccessories.getItem(i));
                }
            }

            this.containerListenerLock = false;

            newAccessories.copyPrev(this.accessories);
            newCosmetics.copyPrev(this.cosmeticAccessories);

            this.accessories = newAccessories;
            this.cosmeticAccessories = newCosmetics;

            this.renderOptions = getWithSize(currentSize, this.renderOptions, true);

            var livingEntity = this.capability.entity();

            //TODO: Confirm if this is needed
            for (var invalidAccessory : invalidAccessories) {
                var index = invalidAccessory.getFirst();

                var invalidStack = invalidAccessory.getSecond();

                if (invalidStack.isEmpty()) continue;

                var slotReference = SlotReference.of(livingEntity, this.slotName, index);

                AttributeUtils.removeTransientAttributeModifiers(livingEntity, AccessoriesAPI.getAttributeModifiers(invalidStack, slotReference));

                var accessory = AccessoriesAPI.getOrDefaultAccessory(invalidStack);

                if (accessory != null) accessory.onUnequip(invalidStack, slotReference);

                invalidStacks.add(invalidStack);
            }

            holder.invalidStacks.addAll(invalidStacks);

            if (this.update) this.capability.updateContainers();
        }

        if(!hasChangeOccurred) {
            var inv = holder.containersRequiringUpdates;

            inv.remove(this);
        } else {
            var cache = holder.getLookupCache();

            if (cache != null) cache.clearContainerCache(this.slotName);
        }
    }

    @Override
    public int getSize() {
        this.update();
        return this.accessories.getContainerSize();
    }

    @Override
    public String getSlotName(){
        return this.slotName;
    }

    @Override
    public AccessoriesCapability capability() {
        return this.capability;
    }

    @Override
    public List<Boolean> renderOptions() {
        this.update();
        return this.renderOptions;
    }

    @Override
    public ExpandedSimpleContainer getAccessories() {
        this.update();
        return accessories;
    }

    @Override
    public ExpandedSimpleContainer getCosmeticAccessories() {
        this.update();
        return cosmeticAccessories;
    }

    @Override
    public Map<ResourceLocation, AttributeModifier> getModifiers() {
        return this.modifiers;
    }

    public Set<AttributeModifier> getCachedModifiers(){
        return this.cachedModifiers;
    }

    @Override
    public Collection<AttributeModifier> getModifiersForOperation(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.get(operation);
    }

    @Override
    public void addTransientModifier(AttributeModifier modifier) {
        this.modifiers.put(modifier.id(), modifier);
        this.getModifiersForOperation(modifier.operation()).add(modifier);
        this.markChanged();
    }

    @Override
    public void addPersistentModifier(AttributeModifier modifier) {
        this.addTransientModifier(modifier);
        this.persistentModifiers.add(modifier);
    }

    @Override
    public boolean hasModifier(ResourceLocation location) {
        return this.modifiers.containsKey(location);
    }

    @Override
    public void removeModifier(ResourceLocation location) {
        var modifier = this.modifiers.remove(location);

        if(modifier == null) return;

        this.persistentModifiers.remove(modifier);
        this.getModifiersForOperation(modifier.operation()).remove(modifier);
        this.markChanged();
    }

    @Override
    public void clearModifiers() {
        this.getModifiers().keySet().iterator().forEachRemaining(this::removeModifier);
    }

    @Override
    public void removeCachedModifiers(AttributeModifier modifier) {
        this.cachedModifiers.remove(modifier);
    }

    @Override
    public void clearCachedModifiers() {
        this.cachedModifiers.forEach(cachedModifier -> this.removeModifier(cachedModifier.id()));
        this.cachedModifiers.clear();
    }

    //--

    public void copyFrom(AccessoriesContainerImpl other){
        this.modifiers.clear();
        this.modifiersByOperation.clear();
        this.persistentModifiers.clear();
        other.modifiers.values().forEach(this::addTransientModifier);
        other.persistentModifiers.forEach(this::addPersistentModifier);
        this.update();
    }

    //TODO: Confirm Cross Dimension stuff works!
//    public static void copyFrom(LivingEntity oldEntity, LivingEntity newEntity){
//        var api = AccessoriesAccess.getAPI();
//
//        var oldCapability = api.getCapability(oldEntity);
//        var newCapability = api.getCapability(newEntity);
//
//        if(oldCapability.isEmpty() || newCapability.isEmpty()) return;
//
//        var newContainers = newCapability.get().getContainers();
//        for (var containerEntries : oldCapability.get().getContainers().entrySet()) {
//            if(!newContainers.containsKey(containerEntries.getKey())) continue;
//        }
//    }

    //--

    public static final KeyedEndec<String> SLOT_NAME_KEY = Endec.STRING.keyed("slot_name", "UNKNOWN");

    public static final KeyedEndec<Integer> BASE_SIZE_KEY = Endec.INT.keyed("base_size", () -> null);

    public static final KeyedEndec<Integer> CURRENT_SIZE_KEY = Endec.INT.keyed("current_size", 0);

    public static final KeyedEndec<List<Boolean>> RENDER_OPTIONS_KEY = Endec.BOOLEAN.listOf().keyed("render_options", ArrayList::new);

    public static final KeyedEndec<List<CompoundTag>> MODIFIERS_KEY = NbtEndec.COMPOUND.listOf().keyed("modifiers", ArrayList::new);
    public static final KeyedEndec<List<CompoundTag>> PERSISTENT_MODIFIERS_KEY = NbtEndec.COMPOUND.listOf().keyed("persistent_modifiers", ArrayList::new);
    public static final KeyedEndec<List<CompoundTag>> CACHED_MODIFIERS_KEY = NbtEndec.COMPOUND.listOf().keyed("cached_modifiers", ArrayList::new);

    public static final KeyedEndec<ListTag> ITEMS_KEY = EndecUtils.NBT_LIST.keyed("items", ListTag::new);
    public static final KeyedEndec<ListTag> COSMETICS_KEY = EndecUtils.NBT_LIST.keyed("cosmetics", ListTag::new);

    @Override
    public void write(MapCarrier carrier, SerializationContext ctx) {
        write(carrier, ctx, false);
    }

    public void write(MapCarrier carrier, SerializationContext ctx, boolean sync){
        var registryAccess = ctx.requireAttributeValue(RegistriesAttribute.REGISTRIES).registryManager();

        carrier.put(SLOT_NAME_KEY, this.slotName);

        carrier.putIfNotNull(ctx, BASE_SIZE_KEY, this.baseSize);

        carrier.put(RENDER_OPTIONS_KEY, this.renderOptions);

        if(!sync || this.accessories.wasNewlyConstructed()) {
            carrier.put(CURRENT_SIZE_KEY, accessories.getContainerSize());

            carrier.put(ITEMS_KEY, accessories.createTag(registryAccess));
            carrier.put(COSMETICS_KEY, cosmeticAccessories.createTag(registryAccess));
        }

        if(sync){
            if(!this.modifiers.isEmpty()){
                var modifiersTag = new ArrayList<CompoundTag>();

                this.modifiers.values().forEach(modifier -> modifiersTag.add(modifier.save()));

                carrier.put(MODIFIERS_KEY, modifiersTag);
            }
        } else {
            if(!this.persistentModifiers.isEmpty()){
                var persistentTag = new ArrayList<CompoundTag>();

                this.persistentModifiers.forEach(modifier -> persistentTag.add(modifier.save()));

                carrier.put(PERSISTENT_MODIFIERS_KEY, persistentTag);
            }

            if(!this.modifiers.isEmpty()){
                var cachedTag = new ArrayList<CompoundTag>();

                this.modifiers.values().forEach(modifier -> {
                    if(this.persistentModifiers.contains(modifier)) return;

                    cachedTag.add(modifier.save());
                });

                carrier.put(CACHED_MODIFIERS_KEY, cachedTag);
            }
        }
    }

    @Override
    public void read(MapCarrier carrier, SerializationContext ctx) {
        read(carrier, ctx, false);
    }

    public void read(MapCarrier carrier, SerializationContext ctx, boolean sync){
        var registryAccess = ctx.requireAttributeValue(RegistriesAttribute.REGISTRIES).registryManager();

        EndecUtils.dfuKeysCarrier(
                carrier,
                Map.of(
                        "SlotName", "slot_name",
                        "BaseSize", "base_size",
                        "CurrentSize", "current_size",
                        "RenderOptions", "render_options",
                        "Modifiers", "modifiers",
                        "PersistentModifiers", "persistent_modifiers",
                        "CachedModifiers", "cached_modifiers",
                        "Items", "items",
                        "Cosmetics", "cosmetics"
                ));

        this.slotName = carrier.get(SLOT_NAME_KEY);

        this.baseSize = carrier.get(BASE_SIZE_KEY);

        if(sync) {
            this.modifiers.clear();
            this.persistentModifiers.clear();
            this.modifiersByOperation.clear();

            if (carrier.has(MODIFIERS_KEY)) {
                var persistentTag = carrier.get(MODIFIERS_KEY);

                for (var compoundTag : persistentTag) {
                    var modifier = AttributeModifier.load(compoundTag);

                    if (modifier != null) this.addTransientModifier(modifier);
                }
            }
        } else {
            if (carrier.has(PERSISTENT_MODIFIERS_KEY)) {
                var persistentTag = carrier.get(PERSISTENT_MODIFIERS_KEY);

                for (var compoundTag : persistentTag) {
                    var modifier = AttributeModifier.load(compoundTag);

                    if (modifier != null) this.addPersistentModifier(modifier);
                }
            }

            if (carrier.has(CACHED_MODIFIERS_KEY)) {
                var cachedTag = carrier.get(CACHED_MODIFIERS_KEY);

                for (CompoundTag compoundTag : cachedTag) {
                    var modifier = AttributeModifier.load(compoundTag);

                    if (modifier != null) {
                        this.cachedModifiers.add(modifier);
                        this.addTransientModifier(modifier);
                    }
                }

                this.update();
            }
        }

        if(carrier.has(CURRENT_SIZE_KEY)) {
            var currentSize = carrier.get(CURRENT_SIZE_KEY);

            var sentOptions = carrier.get(RENDER_OPTIONS_KEY);

            this.renderOptions = getWithSize(currentSize, sentOptions, true);

            if(this.accessories.getContainerSize() != currentSize) {
                this.accessories = new ExpandedSimpleContainer(this, currentSize, "accessories");
                this.cosmeticAccessories = new ExpandedSimpleContainer(this, currentSize, "cosmetic_accessories");
            }

            this.accessories.fromTag(carrier.get(ITEMS_KEY), registryAccess);
            this.cosmeticAccessories.fromTag(carrier.get(COSMETICS_KEY), registryAccess);
        } else {
            this.renderOptions = carrier.get(RENDER_OPTIONS_KEY);
        }

        if(carrier.has(CURRENT_SIZE_KEY)) {
            var currentSize = carrier.get(CURRENT_SIZE_KEY);

            var sentOptions = carrier.get(RENDER_OPTIONS_KEY);

            this.renderOptions = getWithSize(currentSize, sentOptions, true);

            if(this.accessories.getContainerSize() != currentSize) {
                this.accessories = new ExpandedSimpleContainer(this, currentSize, "accessories");
                this.cosmeticAccessories = new ExpandedSimpleContainer(this, currentSize, "cosmetic_accessories");
            }

            this.accessories.fromTag(carrier.get(ITEMS_KEY), registryAccess);
            this.cosmeticAccessories.fromTag(carrier.get(COSMETICS_KEY), registryAccess);
        } else {
            this.renderOptions = carrier.get(RENDER_OPTIONS_KEY);
        }
    }

    private <T> List<T> getWithSize(int size, List<T> list, T defaultValue) {
        var sizedList = new ArrayList<T>(size);

        for (int i = 0; i < size; i++) {
            var value = (i < list.size()) ? list.get(i) : defaultValue;

            sizedList.add(value);
        }

        return sizedList;
    }

    public static SimpleContainer readContainer(MapCarrier carrier, SerializationContext ctx, KeyedEndec<ListTag> key){
        return readContainers(carrier, ctx, key).get(0);
    }

    @SafeVarargs
    public static List<SimpleContainer> readContainers(MapCarrier carrier, SerializationContext ctx, KeyedEndec<ListTag> ...keys){
        var containers = new ArrayList<SimpleContainer>();

        var registryAccess = ctx.requireAttributeValue(RegistriesAttribute.REGISTRIES).registryManager();

        for (var key : keys) {
            var stacks = new SimpleContainer();

            if(carrier.has(key)) stacks.fromTag(carrier.get(key), registryAccess);

            containers.add(stacks);
        }

        return containers;
    }

    public static SimpleContainer copyContainerList(SimpleContainer container){
        return new SimpleContainer(container.getItems().toArray(ItemStack[]::new));
    }
}
