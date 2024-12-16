package io.wispforest.accessories.utils;

import com.google.gson.JsonObject;
import io.wispforest.accessories.endec.NbtMapCarrier;
import io.wispforest.endec.Endec;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.format.gson.GsonMapCarrier;
import io.wispforest.endec.util.MapCarrier;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import net.minecraft.Util;

import java.util.function.Supplier;

public interface InstanceEndec {

    void write(MapCarrier carrier, SerializationContext ctx);

    void read(MapCarrier carrier, SerializationContext ctx);

    static <T extends InstanceEndec> Endec<T> constructed(Supplier<T> supplier) {
        return NbtEndec.COMPOUND.xmapWithContext(
                (ctx, compound) -> Util.make(supplier.get(), t -> t.read(new NbtMapCarrier(compound), ctx)),
                (ctx, t) -> Util.make(NbtMapCarrier.of(), map -> t.write(map, ctx)).compoundTag());
    }

    default <I extends InstanceEndec> void readFrom(I from) {
        var carrier = new GsonMapCarrier(new JsonObject());

        from.write(carrier, SerializationContext.empty());
        this.read(carrier, SerializationContext.empty());
    }
}
