package io.wispforest.accessories.networking.client;

import io.wispforest.accessories.impl.AccessoriesPlayerOptions;
import io.wispforest.accessories.utils.InstanceEndec;
import io.wispforest.endec.StructEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Player;

public record SyncPlayerOptions(AccessoriesPlayerOptions options) {
    public static final StructEndec<SyncPlayerOptions> ENDEC = StructEndecBuilder.of(
            InstanceEndec.constructed(AccessoriesPlayerOptions::new).fieldOf("options", SyncPlayerOptions::options),
            SyncPlayerOptions::new
    );

    @Environment(EnvType.CLIENT)
    public static void handlePacket(SyncPlayerOptions packet, Player player) {
        AccessoriesPlayerOptions.getOptions(player).readFrom(packet.options());
    }
}
