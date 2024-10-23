package io.wispforest.accessories.data;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.api.slot.SlotGroup;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.impl.slot.SlotGroupImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SlotGroupLoader extends ReplaceableJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setLenient().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SlotGroupLoader INSTANCE = new SlotGroupLoader();

    private Map<String, SlotGroup> server = new HashMap<>();
    private Map<String, SlotGroup> client = new HashMap<>();

    protected SlotGroupLoader() {
        super(GSON, LOGGER, "accessories/group");
    }

    //--

    public static List<SlotGroup> getGroups(Level level){
        return INSTANCE.getGroups(level.isClientSide(), true);
    }

    public static List<SlotGroup> getGroups(Level level, boolean filterUniqueGroups){
        return INSTANCE.getGroups(level.isClientSide(), filterUniqueGroups);
    }

    public static Map<SlotGroup, List<SlotType>> getValidGroups(LivingEntity living) {
        var entitySpecificSlots = EntitySlotLoader.getEntitySlots(living);

        var groups = SlotGroupLoader.getGroups(living.level(), false);

        return groups.stream()
                .sorted(Comparator.comparingInt(SlotGroup::order).reversed())
                .map(slotGroup -> {
                    if(UniqueSlotHandling.isUniqueGroup(slotGroup.name(), living.level().isClientSide())) return null;

                    var slots = slotGroup.slots()
                            .stream()
                            .filter(entitySpecificSlots::containsKey)
                            .map(slot -> SlotTypeLoader.getSlotType(living.level(), slot))
                            .sorted(Comparator.comparingInt(SlotType::order).reversed())
                            .toList();

                    return slots.isEmpty() ? null : Map.entry(slotGroup, slots);
                })
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (slotTypes, slotTypes2) -> Stream.concat(slotTypes.stream(), slotTypes2.stream()).toList(),
                                LinkedHashMap::new));
    }

    public static Optional<SlotGroup> getGroup(Level level, String group){
        return Optional.ofNullable(INSTANCE.getGroup(level.isClientSide(), group));
    }

    //--

    @ApiStatus.Internal
    public final Map<String, SlotGroup> getGroupMap(boolean isClientSide) {
        return (isClientSide ? this.client : this.server);
    }

    public final List<SlotGroup> getGroups(boolean isClientSide, boolean filterUniqueGroups){
        var groups = getGroupMap(isClientSide).values();

        if(filterUniqueGroups) groups = groups.stream().filter(group -> !UniqueSlotHandling.isUniqueGroup(group.name(), isClientSide)).toList();

        return List.copyOf(groups);
    }

    public final SlotGroup getGroup(boolean isClientSide, String group){
        return getGroupMap(isClientSide).get(group);
    }

    public final Optional<SlotGroup> findGroup(boolean isClientSide, String slot){
        for (var entry : getGroups(isClientSide, false)) {
            if(entry.slots().contains(slot)) return Optional.of(entry);
        }

        return Optional.empty();
    }

    public final SlotGroup getOrDefaultGroup(boolean isClientSide, String slot){
        var groups = getGroupMap(isClientSide);

        for (var entry : groups.values()) {
            if(entry.slots().contains(slot)) return entry;
        }

        return groups.get("any");
    }

    @ApiStatus.Internal
    public final void setGroups(Map<String, SlotGroup> groups){
        this.client = ImmutableMap.copyOf(groups);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> data, ResourceManager resourceManager, ProfilerFiller profiler) {
        var slotGroups = new HashMap<String, SlotGroupBuilder>();

        slotGroups.put("unsorted", new SlotGroupBuilder("unsorted").order(30));

        var allSlots = new HashMap<>(SlotTypeLoader.INSTANCE.getSlotTypes(false));

        for (var resourceEntry : data.entrySet()) {
            var location = resourceEntry.getKey();
            var jsonObject = resourceEntry.getValue();

            if(!AccessoriesInternals.isValidOnConditions(jsonObject, this.directory, location, null)) continue;

            var pathParts = location.getPath().split("/");

            String groupName = pathParts[pathParts.length - 1];
            String namespace = pathParts.length > 1 ? pathParts[0] + ":" : "";

            var isShared = namespace.isBlank();

            if(!isShared) groupName = namespace + ":" + groupName;

            var group = slotGroups.computeIfAbsent(groupName, SlotGroupBuilder::new);

            if(isShared) {
                var slotElements = safeHelper(GsonHelper::getAsJsonArray, jsonObject, "slots", new JsonArray(), location);

                decodeJsonArray(slotElements, "slot", location, JsonElement::getAsString, s -> {
                    for (var builderEntry : slotGroups.entrySet()) {
                        if (builderEntry.getValue().slots.contains(s)) {
                            LOGGER.error("Unable to assign a give slot [{}] to the group [{}] as it already exists within the group [{}]", s, group, builderEntry.getKey());
                            return;
                        }
                    }

                    var slotType = allSlots.remove(s);

                    if (slotType == null) {
                        LOGGER.warn("SlotType added to a given group without being in the main map for slots! [Name: {}]", slotType.name());
                    } else {
                        group.addSlot(s);
                    }
                });

                group.order(safeHelper(GsonHelper::getAsInt, jsonObject, "order", 100, location));
            }

            var icon = safeHelper(GsonHelper::getAsString, jsonObject, "icon", location);

            if(icon != null){
                var iconLocation = ResourceLocation.tryParse(icon);

                if(iconLocation != null){
                    group.icon(iconLocation);
                } else {
                    LOGGER.warn("A given SlotGroup was found to have a invalid Icon Location. [Location: {}]", location);
                }
            }
        }

        var remainSlots = new HashSet<String>();

        for (var value : allSlots.values()) {
            var slotName = value.name();

            if(!UniqueSlotHandling.isUniqueSlot(slotName)) {
                remainSlots.add(slotName);

                continue;
            }

            var group = slotName.split(":")[0];

            slotGroups.computeIfAbsent(group, SlotGroupBuilder::new)
                    .order(5)
                    .addSlot(slotName);

            UniqueSlotHandling.addGroup(group);
        }

        slotGroups.get("unsorted").addSlots(remainSlots);

        var tempMap = ImmutableMap.<String, SlotGroup>builder();

        slotGroups.forEach((s, builder) -> tempMap.put(s, builder.build()));

        this.server = tempMap.build();
    }

    public static class SlotGroupBuilder {
        private final String name;

        private Integer order = null;
        private final Set<String> slots = new LinkedHashSet<>();

        private ResourceLocation iconLocation = SlotGroup.UNKNOWN;

        public SlotGroupBuilder(String name){
            this.name = name;
        }

        public SlotGroupBuilder order(Integer value){
            this.order = value;

            return this;
        }

        public SlotGroupBuilder addSlot(String value){
            this.slots.add(value);

            return this;
        }

        public SlotGroupBuilder addSlots(Collection<String> values){
            this.slots.addAll(values);

            return this;
        }

        public SlotGroupBuilder icon(ResourceLocation location) {
            this.iconLocation = location;

            return this;
        }

        public SlotGroup build(){
            return new SlotGroupImpl(
                    name,
                    Optional.ofNullable(order).orElse(0),
                    slots,
                    iconLocation
            );
        }
    }
}
