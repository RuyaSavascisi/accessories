package io.wispforest.accessories.utils;

import io.wispforest.accessories.endec.NbtMapCarrier;
import io.wispforest.endec.impl.BuiltInEndecs;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import io.wispforest.endec.*;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.endec.util.MapCarrier;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.StringRepresentable;
import org.joml.*;

import java.lang.Math;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EndecUtils {

    public static final Endec<ListTag> NBT_LIST = NbtEndec.ELEMENT.xmap(ListTag.class::cast, listTag -> listTag);

    public static final Endec<TriState> TRI_STATE_ENDEC = Endec.BOOLEAN.nullableOf().xmap(TriState::of, TriState::getBoxed);

    public static final Endec<Vector2i> VECTOR_2_I_ENDEC = StructEndecBuilder.of(
            Endec.LONG.fieldOf("x", vec2i -> (long) vec2i.x),
            Endec.LONG.fieldOf("y", vec2i -> (long) vec2i.y),
            (x, y) -> new Vector2i((int) (long) x, (int) (long) y)
    );

    public static final Endec<Vector3f> VECTOR_3_F_ENDEC = BuiltInEndecs.vectorEndec("Vector3f", Endec.FLOAT, Vector3f::new, Vector3f::x, Vector3f::y, Vector3f::z);

    public static final Endec<Quaternionf> QUATERNIONF_COMPONENTS = BuiltInEndecs.vectorEndec("QuaternionfComponents", Endec.FLOAT, Quaternionf::new, Quaternionf::x, Quaternionf::y, Quaternionf::z, Quaternionf::w);

    public static final StructEndec<AxisAngle4f> AXISANGLE4F = StructEndecBuilder.of(
            Endec.FLOAT.xmap(degrees -> (float) Math.toRadians(degrees), (radians) -> (float) Math.toDegrees(radians)).fieldOf("angle", axisAngle4f -> axisAngle4f.angle),
            VECTOR_3_F_ENDEC.fieldOf("axis", axisAngle4f -> new Vector3f(axisAngle4f.x, axisAngle4f.y, axisAngle4f.z)),
            AxisAngle4f::new
    );

    public static final Endec<Matrix4f> MATRIX4F = Endec.FLOAT.listOf()
            .validate(floats -> {
                if (floats.size() != 16) throw new IllegalStateException("Matrix entries must have 16 elements");
            }).xmap(floats -> {
                var matrix4f = new Matrix4f();

                for (int i = 0; i < floats.size(); i++) {
                    matrix4f.setRowColumn(i >> 2, i & 3, floats.get(i));
                }

                return matrix4f.determineProperties();
            }, matrix4f -> {
                var floats = new FloatArrayList(16);

                for (int i = 0; i < 16; i++) {
                    floats.add(matrix4f.getRowColumn(i >> 2, i & 3));
                }

                return floats;
            });

    public static void dfuKeysCarrier(MapCarrier carrier, Map<String, String> changedKeys) {
        CompoundTag compoundTag;

        if (carrier instanceof NbtMapCarrier nbtMapCarrier) {
            compoundTag = nbtMapCarrier.compoundTag();
        } else if (carrier instanceof CompoundTag carrierTag) {
            compoundTag = carrierTag;
        } else {
            compoundTag = null;
        }

        if(compoundTag != null) {
            changedKeys.forEach((prevKey, newKey) -> {
                if (compoundTag.contains(prevKey)) compoundTag.put(newKey, compoundTag.get(prevKey));
            });
        }
    }

    public static <E extends Enum<E> & StringRepresentable> Endec<E> forEnumStringRepresentable(Class<E> enumClass) {
        return Endec.ifAttr(
                SerializationAttributes.HUMAN_READABLE,
                Endec.STRING.xmap(name -> Arrays.stream(enumClass.getEnumConstants()).filter(e -> e.getSerializedName().equals(name)).findFirst().get(), StringRepresentable::getSerializedName)
        ).orElse(
                Endec.VAR_INT.xmap(ordinal -> enumClass.getEnumConstants()[ordinal], Enum::ordinal)
        );
    }
}
