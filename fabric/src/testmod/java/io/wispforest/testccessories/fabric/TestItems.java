package io.wispforest.testccessories.fabric;

import io.wispforest.testccessories.fabric.accessories.SlotIncreaserTest;
import io.wispforest.testccessories.fabric.accessories.WaterBreathingAccessory;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class TestItems {

    public static final Item testItem1 = register("test_item_1", new Item.Properties().stacksTo(1).durability(64));
    public static final Item testItem2 = register("test_item_2", new Item.Properties().stacksTo(1).durability(64));

    private static Item register(String path, Item.Properties properties) {
        var key = ResourceKey.create(BuiltInRegistries.ITEM.key(), Testccessories.of(path));

        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    }

    public static void init(){
        WaterBreathingAccessory.init();
        SlotIncreaserTest.init();

        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            var groupKey = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(group).get();

            if(groupKey.equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
                entries.accept(testItem1);
                entries.accept(testItem2);
            }
        });
    }
}
