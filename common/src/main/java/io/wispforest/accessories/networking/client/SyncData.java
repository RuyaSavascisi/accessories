package io.wispforest.accessories.networking.client;

import io.wispforest.accessories.impl.slot.ExtraSlotTypeProperties;
import io.wispforest.accessories.api.slot.SlotGroup;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.data.SlotGroupLoader;
import io.wispforest.accessories.data.SlotTypeLoader;
import io.wispforest.accessories.impl.slot.SlotGroupImpl;
import io.wispforest.accessories.impl.slot.SlotTypeImpl;
import io.wispforest.endec.Endec;
import io.wispforest.endec.StructEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.stream.Collectors;

public record SyncData(List<SlotType> slotTypes, Map<EntityType<?>, Set<String>> entitySlots, Set<SlotGroup> slotGroups, Set<String> uniqueGroups, Map<String, ExtraSlotTypeProperties> uniqueExtraProperties) {

    public static StructEndec<SyncData> ENDEC = StructEndecBuilder.of(
            SlotTypeImpl.ENDEC.listOf().fieldOf("slotTypes", SyncData::slotTypes),
            Endec.map(MinecraftEndecs.ofRegistry(BuiltInRegistries.ENTITY_TYPE), Endec.STRING.setOf()).fieldOf("entitySlots", SyncData::entitySlots),
            SlotGroupImpl.ENDEC.setOf().fieldOf("slotGroups", SyncData::slotGroups),
            Endec.STRING.setOf().fieldOf("uniqueGroups", SyncData::uniqueGroups),
            ExtraSlotTypeProperties.ENDEC.mapOf().fieldOf("uniqueExtraProperties", SyncData::uniqueExtraProperties),
            SyncData::new
    );

    public static SyncData create(){
        var allSlotTypes = SlotTypeLoader.INSTANCE.getSlotTypes(false);

        var entitySlotData = EntitySlotLoader.INSTANCE.getEntitySlotData(false);

        var entitySlots = new HashMap<EntityType<?>, Set<String>>();

        for (var entry : entitySlotData.entrySet()) {
            entitySlots.put(entry.getKey(), entry.getValue().keySet());
        }

        var slotGroups = new HashSet<SlotGroup>();

        slotGroups.addAll(SlotGroupLoader.INSTANCE.getGroups(false, false));

        return new SyncData(List.copyOf(allSlotTypes.values()), entitySlots, slotGroups, UniqueSlotHandling.getGroups(false), ExtraSlotTypeProperties.getProperties(false));
    }

    @Environment(EnvType.CLIENT)
    public static void handlePacket(SyncData packet, Player player) {
        Map<String, SlotType> slotTypes = new HashMap<>();

        for (SlotType slotType : packet.slotTypes()) {
            slotTypes.put(slotType.name(), slotType);
        }

        SlotTypeLoader.INSTANCE.setSlotType(slotTypes);

        UniqueSlotHandling.buildClientSlotReferences();

        Map<EntityType<?>, Map<String, SlotType>> entitySlotTypes = new HashMap<>();

        for (var entry : packet.entitySlots().entrySet()) {
            var map = entry.getValue().stream()
                    .map(slotTypes::get)
                    .collect(Collectors.toUnmodifiableMap(SlotType::name, slotType -> slotType));

            entitySlotTypes.put(entry.getKey(), map);
        }

        EntitySlotLoader.INSTANCE.setEntitySlotData(entitySlotTypes);

        var slotGroups = packet.slotGroups().stream()
                .collect(Collectors.toUnmodifiableMap(SlotGroup::name, group -> group));

        SlotGroupLoader.INSTANCE.setGroups(slotGroups);

        UniqueSlotHandling.setClientGroups(packet.uniqueGroups());
        ExtraSlotTypeProperties.setClientPropertyMap(packet.uniqueExtraProperties());
    }
}
