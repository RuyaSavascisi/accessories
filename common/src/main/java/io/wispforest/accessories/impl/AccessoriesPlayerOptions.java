package io.wispforest.accessories.impl;

import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.utils.InstanceEndec;
import io.wispforest.endec.Endec;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.impl.KeyedEndec;
import io.wispforest.endec.util.MapCarrier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class AccessoriesPlayerOptions implements InstanceEndec {

    //-- Logical Stuff

    private PlayerEquipControl equipControl = PlayerEquipControl.MUST_NOT_CROUCH;

    //--

    //-- Rendering Stuff

    private boolean showAdvancedOptions = false;

    private boolean showUnusedSlots = false;

    private boolean showCosmetics = false;

    private int columnAmount = 1;
    private int widgetType = 2;
    private boolean showGroupFilter = false;
    private boolean mainWidgetPosition = true;
    private boolean sideWidgetPosition = false;

    private boolean showCraftingGrid = false;

    //--

    public AccessoriesPlayerOptions() {}

    public static AccessoriesPlayerOptions getOptions(Player player) {
        return AccessoriesInternals.getPlayerOptions(player);
    }

    public PlayerEquipControl equipControl() {
        return equipControl;
    }

    public AccessoriesPlayerOptions equipControl(PlayerEquipControl value) {
        this.equipControl = value;

        return this;
    }

    //--

    public boolean showUnusedSlots() {
        return this.showUnusedSlots;
    }

    public AccessoriesPlayerOptions showUnusedSlots(boolean value) {
        this.showUnusedSlots = value;

        return this;
    }

    public boolean showCosmetics() {
        return this.showCosmetics;
    }

    public AccessoriesPlayerOptions showCosmetics(boolean value) {
        this.showCosmetics = value;

        return this;
    }

    public int columnAmount() {
        return Math.max(columnAmount, 1);
    }

    public AccessoriesPlayerOptions columnAmount(int value) {
        this.columnAmount = value;

        return this;
    }

    public int widgetType() {
        return Math.max(widgetType, 1);
    }

    public AccessoriesPlayerOptions widgetType(int value) {
        this.widgetType = value;

        return this;
    }

    public boolean mainWidgetPosition() {
        return this.mainWidgetPosition;
    }

    public AccessoriesPlayerOptions mainWidgetPosition(boolean value) {
        this.mainWidgetPosition = value;

        return this;
    }

    public boolean showAdvancedOptions() {
        return this.showAdvancedOptions;
    }

    public AccessoriesPlayerOptions showAdvancedOptions(boolean value) {
        this.showAdvancedOptions = value;

        return this;
    }

    public boolean showGroupFilter() {
        return this.showGroupFilter;
    }

    public AccessoriesPlayerOptions showGroupFilter(boolean value) {
        this.showGroupFilter = value;

        return this;
    }

    private boolean isGroupFiltersOpen = true;

    public boolean isGroupFiltersOpen() {
        return isGroupFiltersOpen;
    }

    public AccessoriesPlayerOptions isGroupFiltersOpen(boolean value) {
        this.isGroupFiltersOpen = value;

        return this;
    }

    private Set<String> filteredGroups = Set.of();

    public Set<String> filteredGroups() {
        return filteredGroups;
    }

    public AccessoriesPlayerOptions filteredGroups(Set<String> value) {
        this.filteredGroups = value;

        return this;
    }

    public boolean sideWidgetPosition() {
        return this.sideWidgetPosition;
    }

    public AccessoriesPlayerOptions sideWidgetPosition(boolean value) {
        this.sideWidgetPosition = value;

        return this;
    }

    public boolean showCraftingGrid() {
        return this.showCraftingGrid;
    }

    public AccessoriesPlayerOptions showCraftingGrid(boolean value) {
        this.showCraftingGrid = value;

        return this;
    }

    //--

    public static final KeyedEndec<PlayerEquipControl> EQUIP_CONTROL_KEY = Endec.forEnum(PlayerEquipControl.class).keyed("equip_control", PlayerEquipControl.MUST_CROUCH);

    public static final KeyedEndec<Boolean> SHOW_UNUSED_SLOTS_KEY = Endec.BOOLEAN.keyed("show_unused_slots", false);
    public static final KeyedEndec<Boolean> SHOW_COSMETICS_KEY = Endec.BOOLEAN.keyed("show_cosmetics", false);

    public static final KeyedEndec<Integer> COLUMN_AMOUNT_KEY = Endec.INT.keyed("column_amount", 1);
    public static final KeyedEndec<Integer> WIDGET_TYPE_KEY = Endec.INT.keyed("widget_type", 2);
    public static final KeyedEndec<Boolean> MAIN_WIDGET_POSITION = Endec.BOOLEAN.keyed("main_widget_position", true);
    public static final KeyedEndec<Boolean> SIDE_WIDGET_POSITION = Endec.BOOLEAN.keyed("side_widget_position", false);

    public static final KeyedEndec<Boolean> SHOW_GROUP_FILTER = Endec.BOOLEAN.keyed("show_group_filter", false);
    public static final KeyedEndec<Boolean> IS_GROUP_FILTERS_OPEN_KEY = Endec.BOOLEAN.keyed("is_group_filter_open", false);
    public static final KeyedEndec<Set<String>> FILTERED_GROUPS_KEY = Endec.STRING.setOf().keyed("filtered_groups", HashSet::new);

    public static final KeyedEndec<Boolean> SHOW_CRAFTING_GRID = Endec.BOOLEAN.keyed("cosmetics_shown", false);

    @Override
    public void write(MapCarrier carrier, SerializationContext ctx) {
        carrier.put(ctx, EQUIP_CONTROL_KEY, this.equipControl);

        carrier.put(ctx, COLUMN_AMOUNT_KEY, this.columnAmount);
        carrier.put(ctx, WIDGET_TYPE_KEY, this.widgetType);
        carrier.put(ctx, MAIN_WIDGET_POSITION, this.mainWidgetPosition);
        carrier.put(ctx, SIDE_WIDGET_POSITION, this.sideWidgetPosition);

        carrier.put(ctx, SHOW_COSMETICS_KEY, this.showCosmetics);
        carrier.put(ctx, SHOW_UNUSED_SLOTS_KEY, this.showUnusedSlots);

        carrier.put(ctx, SHOW_GROUP_FILTER, this.showGroupFilter);
        carrier.put(ctx, IS_GROUP_FILTERS_OPEN_KEY, this.isGroupFiltersOpen);
        carrier.put(ctx, FILTERED_GROUPS_KEY, this.filteredGroups);

        carrier.put(ctx, SHOW_CRAFTING_GRID, this.showCraftingGrid);
    }

    @Override
    public void read(MapCarrier carrier, SerializationContext ctx) {
        this.equipControl = carrier.get(ctx, EQUIP_CONTROL_KEY);

        this.columnAmount = carrier.get(ctx, COLUMN_AMOUNT_KEY);
        this.widgetType = carrier.get(ctx, WIDGET_TYPE_KEY);
        this.mainWidgetPosition = carrier.get(ctx, MAIN_WIDGET_POSITION);
        this.sideWidgetPosition = carrier.get(ctx, SIDE_WIDGET_POSITION);

        this.showCosmetics = carrier.get(ctx, SHOW_COSMETICS_KEY);
        this.showUnusedSlots = carrier.get(ctx, SHOW_UNUSED_SLOTS_KEY);

        this.showGroupFilter = carrier.get(ctx, SHOW_GROUP_FILTER);
        this.isGroupFiltersOpen = carrier.get(ctx, IS_GROUP_FILTERS_OPEN_KEY);
        this.filteredGroups = carrier.get(ctx, FILTERED_GROUPS_KEY);

        this.showCraftingGrid = carrier.get(ctx, SHOW_CRAFTING_GRID);
    }
}
