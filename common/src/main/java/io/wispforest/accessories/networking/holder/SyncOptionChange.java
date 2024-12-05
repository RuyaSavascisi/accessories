package io.wispforest.accessories.networking.holder;

import io.wispforest.accessories.client.gui.AccessoriesScreenBase;
import io.wispforest.accessories.impl.AccessoriesPlayerOptions;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.endec.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

public record SyncOptionChange(PlayerOption<?> property, Object data) {

    public static final StructEndec<SyncOptionChange> ENDEC = new StructEndec<>() {
        @Override
        public void encodeStruct(SerializationContext ctx, Serializer<?> serializer, Serializer.Struct struct, SyncOptionChange value) {
            struct.field("property", ctx, PlayerOption.ENDEC, value.property());
            struct.field("value", ctx, (Endec) value.property().endec(), value.data());
        }

        @Override
        public SyncOptionChange decodeStruct(SerializationContext ctx, Deserializer<?> deserializer, Deserializer.Struct struct) {
            var prop = struct.field("property", ctx, PlayerOption.ENDEC);

            return new SyncOptionChange(prop, struct.field("value", ctx, prop.endec()));
        }
    };

    public static <T> SyncOptionChange of(PlayerOption<T> property, T data) {
        return new SyncOptionChange(property, data);
    }

    public static <T> SyncOptionChange of(PlayerOption<T> property, Player player, Function<T, T> operation) {
        return new SyncOptionChange(property, operation.apply(property.getter().apply(AccessoriesPlayerOptions.getOptions(player))));
    }

    public static void handlePacket(SyncOptionChange packet, Player player) {
        packet.property().setData(player, packet.data());

        if(player.level().isClientSide()) {
            handleClient(packet, player);
        } else {
            AccessoriesNetworking.sendToPlayer((ServerPlayer) player, SyncOptionChange.of((PlayerOption<Object>) packet.property(), (Object) packet.property().getter().apply(AccessoriesPlayerOptions.getOptions(player))));
        }
    }

    @Environment(EnvType.CLIENT)
    public static void handleClient(SyncOptionChange packet, Player player) {
        if(Minecraft.getInstance().screen instanceof AccessoriesScreenBase accessoriesScreen) {
            accessoriesScreen.onHolderChange(packet.property().name());
        }
    }
}