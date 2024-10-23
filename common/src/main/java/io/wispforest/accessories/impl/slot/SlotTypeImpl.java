package io.wispforest.accessories.impl.slot;

import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.DropRule;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.endec.Endec;
import io.wispforest.endec.StructEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

public record SlotTypeImpl(String name, Optional<String> alternativeTranslation, ResourceLocation icon, int order, int amount, Set<ResourceLocation> validators, DropRule dropRule) implements SlotType  {
    public SlotTypeImpl(String name, ResourceLocation icon, int order, int amount, Set<ResourceLocation> validators, DropRule dropRule) {
        this(name, Optional.empty(), icon, order, amount, validators, dropRule);
    }

    @Override
    public String translation() {
        return alternativeTranslation().orElseGet(SlotType.super::translation);
    }

    public static final StructEndec<SlotType> ENDEC = StructEndecBuilder.of(
            Endec.STRING.fieldOf("name", SlotType::name),
            Endec.STRING.optionalOf().fieldOf("alternativeTranslation", slotType -> {
                var translation = slotType.translation();

                return Optional.ofNullable(slotType.translation().contains(Accessories.translationKey("")) ? null : translation);
            }),
            MinecraftEndecs.IDENTIFIER.fieldOf("icon", SlotType::icon),
            Endec.INT.fieldOf("order", SlotType::order),
            Endec.INT.fieldOf("amount", SlotType::amount),
            MinecraftEndecs.IDENTIFIER.setOf().fieldOf("validators", SlotType::validators),
            Endec.STRING.xmap(DropRule::valueOf, DropRule::name).fieldOf("dropRule", SlotType::dropRule),
            SlotTypeImpl::new
    );
}
