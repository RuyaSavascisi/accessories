package io.wispforest.accessories.api.caching;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.Objects;

public class ItemLikeClassPredicate extends ItemStackBasedPredicate {

    private final boolean isBlockClass;
    private final Class<? extends ItemLike> clazz;

    public ItemLikeClassPredicate(String name, Class<? extends ItemLike> clazz){
        super(name);
        this.clazz = clazz;

        this.isBlockClass = clazz.isAssignableFrom(Block.class);
    }

    @Override
    public String extraStringData() {
        return "Class: " + clazz.getSimpleName();
    }

    @Override
    public boolean test(ItemStack stack) {
        var item = stack.getItem();

        if (this.isBlockClass) {
            return item instanceof BlockItem blockItem && clazz.isInstance(blockItem.getBlock());
        } else {
            return clazz.isInstance(item);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.clazz);
    }

    @Override
    protected boolean isEqual(Object other) {
        var itemLikeClassPredicate = (ItemLikeClassPredicate) other;

        return this.clazz.equals(itemLikeClassPredicate.clazz);
    }
}
