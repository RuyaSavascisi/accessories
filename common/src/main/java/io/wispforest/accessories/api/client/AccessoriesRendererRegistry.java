package io.wispforest.accessories.api.client;

import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.AccessoryRegistry;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.components.AccessoryRenderOverrideComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Main class used to register and hold {@link AccessoryRenderer}'s. This contains a method to
 * reload all renders when a data reload occurs for the client combined with method to retrieve renders.
 */
public class AccessoriesRendererRegistry {

    private static final Map<Item, Supplier<AccessoryRenderer>> RENDERERS = new HashMap<>();

    private static final Map<Item, AccessoryRenderer> CACHED_RENDERERS = new HashMap<>();

    /**
     * Main method used to register an {@link Item} with a given {@link AccessoryRenderer}
     */
    public static void registerRenderer(Item item, Supplier<AccessoryRenderer> renderer){
        RENDERERS.put(item, renderer);
    }

    /**
     * Method used to prevent default rendering for the given {@link Item}
     * <br/>
     * This should ONLY be used if ABSOLUTELY necessary
     */
    public static void registerNoRenderer(Item item){
        RENDERERS.put(item, () -> null);
    }

    /**
     * Registers the given item as if it should render like armor piece equipped within the targeted slot
     * as dictated by {@link Equippable#slot()}
     */
    public static void registerArmorRendering(Item item) {
        if (!AccessoriesRendererRegistry.hasRenderer(item)) {
            AccessoriesRendererRegistry.registerRenderer(item, () -> BuiltinAccessoryRenderers.ARMOR_RENDERER);
        }
    }

    public static boolean hasRenderer(Item item) {
        return RENDERERS.containsKey(item);
    }

    //--

    /**
     * @return Either the {@link AccessoryRenderer} bound to the item or the instance of the {@link DefaultAccessoryRenderer}
     */
    @Nullable
    public static AccessoryRenderer getRender(ItemStack stack){
        var renderOverrides = stack.getOrDefault(AccessoriesDataComponents.RENDER_OVERRIDE, AccessoryRenderOverrideComponent.DEFAULT);

        var defaultRenderOverride = renderOverrides.defaultRenderOverride();

        if(defaultRenderOverride != null) {
            if(defaultRenderOverride) {
                return DefaultAccessoryRenderer.INSTANCE;
            } else if(AccessoryRegistry.isDefaultAccessory(stack)) {
                return null;
            }
        }

        var armorRenderOverride = renderOverrides.useArmorRenderer();

        if(armorRenderOverride) return BuiltinAccessoryRenderers.ARMOR_RENDERER;

        return getRender(stack.getItem());
    }

    /**
     * @return Either the {@link AccessoryRenderer} bound to the item or the instance of the {@link DefaultAccessoryRenderer}
     */
    @Nullable
    public static AccessoryRenderer getRender(Item item){
        var renderer = CACHED_RENDERERS.getOrDefault(item, DefaultAccessoryRenderer.INSTANCE);

        if(renderer == null && Accessories.config().clientOptions.forceNullRenderReplacement()) {
            renderer = DefaultAccessoryRenderer.INSTANCE;
        }

        return renderer;
    }

    public static void onReload() {
        CACHED_RENDERERS.clear();

        RENDERERS.forEach((item, supplier) -> CACHED_RENDERERS.put(item, supplier.get()));
    }
}