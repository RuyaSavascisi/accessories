package io.wispforest.accessories.networking.holder;

import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.impl.AccessoriesPlayerOptions;
import io.wispforest.accessories.impl.PlayerEquipControl;
import io.wispforest.endec.Endec;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public record PlayerOption<T>(String name, Endec<T> endec, BiConsumer<AccessoriesPlayerOptions, T> setter, Function<AccessoriesPlayerOptions, T> getter) {

    public static final Endec<PlayerOption<?>> ENDEC = Endec.STRING.xmap(PlayerOption::getProperty, PlayerOption::name);

    private static final Map<String, PlayerOption<?>> ALL_PROPERTIES = new HashMap<>();

    public static PlayerOption<PlayerEquipControl> EQUIP_CONTROL;

    public static PlayerOption<Boolean> UNUSED_PROP;

    public static PlayerOption<Boolean> COSMETIC_PROP;

    public static PlayerOption<Integer> COLUMN_AMOUNT_PROP;
    public static PlayerOption<Integer> WIDGET_TYPE_PROP;

    public static PlayerOption<Boolean> GROUP_FILTER_PROP;
    public static PlayerOption<Boolean> GROUP_FILTER_OPEN_PROP;
    public static PlayerOption<Set<String>> FILTERED_GROUPS;

    public static PlayerOption<Boolean> MAIN_WIDGET_POSITION_PROP;
    public static PlayerOption<Boolean> SIDE_WIDGET_POSITION_PROP;

    public static PlayerOption<Boolean> CRAFTING_GRID_PROP;

    static { init(); }

    public static PlayerOption<?> getProperty(String name) {
        if(ALL_PROPERTIES.isEmpty()) init();

        var prop = ALL_PROPERTIES.get(name);

        if(prop == null) {
            throw new IllegalStateException("Unable to locate the given HolderProperty! [Name: " + name + "]");
        }

        return prop;
    }

    public PlayerOption {
        ALL_PROPERTIES.put(name, this);
    }

    public void setData(Player player, Object data) {
        AccessoriesInternals.modifyPlayerOptions(player, holder -> {
            setter.accept(holder, (T) data);

            return holder;
        });
    }

    public <V> V consumeData(Player player, BiFunction<PlayerOption<T>, T, V> biFunction) {
        var data = this.getter().apply(AccessoriesPlayerOptions.getOptions(player));
        return biFunction.apply(this, data);
    }

    public static void init() {
        if(!ALL_PROPERTIES.isEmpty()) return;

        EQUIP_CONTROL = new PlayerOption<>("equip_control", Endec.forEnum(PlayerEquipControl.class), AccessoriesPlayerOptions::equipControl, AccessoriesPlayerOptions::equipControl);

        COLUMN_AMOUNT_PROP = new PlayerOption<>("column_amount", Endec.VAR_INT, AccessoriesPlayerOptions::columnAmount, AccessoriesPlayerOptions::columnAmount);
        WIDGET_TYPE_PROP = new PlayerOption<>("widget_type", Endec.VAR_INT, AccessoriesPlayerOptions::widgetType, AccessoriesPlayerOptions::widgetType);

        MAIN_WIDGET_POSITION_PROP = new PlayerOption<>("main_widget_position", Endec.BOOLEAN, AccessoriesPlayerOptions::mainWidgetPosition, AccessoriesPlayerOptions::mainWidgetPosition);
        SIDE_WIDGET_POSITION_PROP = new PlayerOption<>("side_widget_position", Endec.BOOLEAN, AccessoriesPlayerOptions::sideWidgetPosition, AccessoriesPlayerOptions::sideWidgetPosition);

        UNUSED_PROP = new PlayerOption<>("unused_slots", Endec.BOOLEAN, AccessoriesPlayerOptions::showUnusedSlots, AccessoriesPlayerOptions::showUnusedSlots);

        COSMETIC_PROP = new PlayerOption<>("cosmetic", Endec.BOOLEAN, AccessoriesPlayerOptions::showCosmetics, AccessoriesPlayerOptions::showCosmetics);

        GROUP_FILTER_PROP = new PlayerOption<>("group_filter", Endec.BOOLEAN, AccessoriesPlayerOptions::showGroupFilter, AccessoriesPlayerOptions::showGroupFilter);
        GROUP_FILTER_OPEN_PROP = new PlayerOption<>("group_filter_open", Endec.BOOLEAN, AccessoriesPlayerOptions::isGroupFiltersOpen, AccessoriesPlayerOptions::isGroupFiltersOpen);
        FILTERED_GROUPS = new PlayerOption<>("filtered_groups", Endec.STRING.setOf(), AccessoriesPlayerOptions::filteredGroups, AccessoriesPlayerOptions::filteredGroups);

        CRAFTING_GRID_PROP = new PlayerOption<>("crafting_grid", Endec.BOOLEAN, AccessoriesPlayerOptions::showCraftingGrid, AccessoriesPlayerOptions::showCraftingGrid);
    }
}
