package io.wispforest.accessories.data;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.impl.slot.ExtraSlotTypeProperties;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resource Reload in which handles the loading of {@link SlotType}'s bindings
 * to the targeted {@link EntityType} though a {@link TagKey} or {@link ResourceLocation}
 */
public class EntitySlotLoader extends ReplaceableJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();

    public static final EntitySlotLoader INSTANCE = new EntitySlotLoader();

    private Map<TagKey<EntityType<?>>, Map<String, SlotType>> tagToBoundSlots = new HashMap<>();
    private Map<EntityType<?>, Map<String, SlotType>> entityToBoundSlots = new HashMap<>();

    private Map<EntityType<?>, Map<String, SlotType>> server = new HashMap<>();
    private Map<EntityType<?>, Map<String, SlotType>> client = new HashMap<>();

    protected EntitySlotLoader() {
        super(GSON, LOGGER, "accessories/entity");
    }

    //--

    /**
     * @return The valid {@link SlotType}'s for given {@link LivingEntity} based on its {@link EntityType}
     */
    public static Map<String, SlotType> getEntitySlots(LivingEntity livingEntity){
        return getEntitySlots(livingEntity.level(), livingEntity.getType());
    }

    /**
     * @return The valid {@link SlotType}'s for given {@link EntityType}
     */
    public static Map<String, SlotType> getEntitySlots(Level level, EntityType<?> entityType){
        var map = EntitySlotLoader.INSTANCE.getSlotTypes(level.isClientSide, entityType);

        return map != null ? map : Map.of();
    }

    //--

    @Nullable
    public final Map<String, SlotType> getSlotTypes(boolean isClientSide, EntityType<?> entityType){
        return this.getEntitySlotData(isClientSide).get(entityType);
    }

    @ApiStatus.Internal
    public final Map<EntityType<?>, Map<String, SlotType>> getEntitySlotData(boolean isClientSide){
        return isClientSide ? this.client : this.server;
    }

    @ApiStatus.Internal
    public final void setEntitySlotData(Map<EntityType<?>, Map<String, SlotType>> data){
        this.client = ImmutableMap.copyOf(data);
    }

    public void buildEntryMap() {
        var tempMap = new HashMap<EntityType<?>, ImmutableMap.Builder<String, SlotType>>();

        this.tagToBoundSlots.forEach((entityTag, slots) -> {
            var entityTypes = BuiltInRegistries.ENTITY_TYPE.get(entityTag)
                    .map(holders -> holders.stream().map(Holder::value).collect(Collectors.toSet()))
                    .orElseGet(() -> {
                        LOGGER.warn("[EntitySlotLoader]: Unable to locate the given EntityType Tag used within a slot entry: [Location: {}]", entityTag.location());
                        return Set.of();
                    });

            entityTypes.forEach(entityType -> {
                tempMap.computeIfAbsent(entityType, entityType1 -> ImmutableMap.builder())
                        .putAll(slots);
            });
        });

        this.entityToBoundSlots.forEach((entityType, slots) -> {
            tempMap.computeIfAbsent(entityType, entityType1 -> ImmutableMap.builder())
                    .putAll(slots);
        });

        var finishMap = new ImmutableMap.Builder<EntityType<?>, Map<String, SlotType>>();

        tempMap.forEach((entityType, slotsBuilder) -> finishMap.put(entityType, slotsBuilder.build()));

        this.server = finishMap.build();

        this.tagToBoundSlots.clear();
        this.entityToBoundSlots.clear();
    }

    //--

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> data, ResourceManager resourceManager, ProfilerFiller profiler) {
        var allSlotTypes = SlotTypeLoader.INSTANCE.getSlotTypes(false);

        this.tagToBoundSlots.clear();
        this.entityToBoundSlots.clear();

        for (var resourceEntry : data.entrySet()) {
            var location = resourceEntry.getKey();
            var jsonObject = resourceEntry.getValue();

            if(!AccessoriesInternals.isValidOnConditions(jsonObject, this.directory, location, this, null)) continue;

            var slots = new HashMap<String, SlotType>();

            var slotElements = this.safeHelper(GsonHelper::getAsJsonArray, jsonObject, "slots", new JsonArray(), location);

            this.decodeJsonArray(slotElements, "slot", location, element -> {
                var slotName = element.getAsString();

                return Pair.of(slotName, allSlotTypes.get(slotName));
            }, slotInfo -> {
                var slotType = slotInfo.right();

                if(slotType != null) {
                    if(!ExtraSlotTypeProperties.getProperty(slotInfo.left(), false).strictMode()) {
                        slots.put(slotType.name(), slotType);
                    } else {
                        LOGGER.warn("Unable to add the given slot to the given group due to it being in strict mode! [Slot: {}]", slotInfo.left());
                    }
                } else if (slotType == null) {
                    LOGGER.warn("Unable to locate a given slot to add to a given entity('s) as it was not registered: [Slot: {}]", slotInfo.first());
                }
            });

            //--

            var entityElements = this.safeHelper(GsonHelper::getAsJsonArray, jsonObject, "entities", new JsonArray(), location);

            this.<Void>decodeJsonArray(entityElements, "entity", location, element -> {
                var string = element.getAsString();

                if(string.contains("#")){
                    var entityTypeTagLocation = ResourceLocation.tryParse(string.replace("#", ""));

                    var entityTypeTag = TagKey.create(Registries.ENTITY_TYPE, entityTypeTagLocation);

                    tagToBoundSlots.computeIfAbsent(entityTypeTag, entityTag -> new HashMap<>())
                            .putAll(slots);
                } else {
                    Optional.ofNullable(ResourceLocation.tryParse(string))
                            .flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
                            .ifPresentOrElse(entityType -> {
                                entityToBoundSlots.computeIfAbsent(entityType, entityType1 -> new HashMap<>())
                                        .putAll(slots);
                            }, () -> {
                                LOGGER.warn("[EntitySlotLoader]: Unable to locate the given EntityType within the registries for a slot entry: [Location: {}]", string);
                            });
                }

                return null;
            }, unused -> {});
        }

        for (var entry : UniqueSlotHandling.getSlotToEntities().entrySet()) {
            var slotType = SlotTypeLoader.INSTANCE.getSlotTypes(false).get(entry.getKey());

            for (var entityType : entry.getValue()) {
                entityToBoundSlots.computeIfAbsent(entityType, entityType1 -> new HashMap<>())
                        .put(slotType.name(), slotType);
            }
        }
    }
}
