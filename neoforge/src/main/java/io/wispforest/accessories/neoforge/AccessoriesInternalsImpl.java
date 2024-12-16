package io.wispforest.accessories.neoforge;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import io.wispforest.accessories.impl.AccessoriesPlayerOptions;
import io.wispforest.accessories.menu.AccessoriesMenuData;
import io.wispforest.accessories.menu.AccessoriesMenuVariant;
import io.wispforest.accessories.neoforge.mixin.ContextAwareReloadListenerAccessor;
import io.wispforest.endec.Endec;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.format.bytebuf.ByteBufDeserializer;
import io.wispforest.endec.format.bytebuf.ByteBufSerializer;
import io.wispforest.owo.serialization.RegistriesAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.common.util.AttributeUtil;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class AccessoriesInternalsImpl {

    public static AccessoriesHolderImpl getHolder(LivingEntity livingEntity){
        return livingEntity.getData(AccessoriesForge.HOLDER_ATTACHMENT_TYPE);
    }

    public static void modifyHolder(LivingEntity livingEntity, UnaryOperator<AccessoriesHolderImpl> modifier){
        var holder = getHolder(livingEntity);

        holder = modifier.apply(holder);

        livingEntity.setData(AccessoriesForge.HOLDER_ATTACHMENT_TYPE, holder);
    }

    public static AccessoriesPlayerOptions getPlayerOptions(Player player) {
        return player.getData(AccessoriesForge.PLAYER_OPTIONS_ATTACHMENT_TYPE);
    }

    public static void modifyPlayerOptions(Player player, UnaryOperator<AccessoriesPlayerOptions> modifier) {
        var options = getPlayerOptions(player);

        options = modifier.apply(options);

        player.setData(AccessoriesForge.PLAYER_OPTIONS_ATTACHMENT_TYPE, options);
    }

    //--

    public static void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    public static boolean isValidOnConditions(JsonObject object, String dataType, ResourceLocation key, SimplePreparableReloadListener listener, @Nullable RegistryOps.RegistryInfoLookup registryInfo) {
        return ICondition.conditionsMatched(((ContextAwareReloadListenerAccessor) listener).accessories$makeConditionalOps(), object);
    }

    public static <T extends AbstractContainerMenu, D> MenuType<T> registerMenuType(ResourceLocation location, Endec<D> endec, TriFunction<Integer, Inventory, D, T> func) {
        return Registry.register(BuiltInRegistries.MENU, location, IMenuTypeExtension.create((i, arg, arg2) -> {
            return func.apply(i, arg, endec.decodeFully(SerializationContext.attributes(RegistriesAttribute.of(arg2.registryAccess())), ByteBufDeserializer::of, arg2));
        }));
    }

    public static void openAccessoriesMenu(Player player, AccessoriesMenuVariant variant, @Nullable LivingEntity targetEntity, @Nullable ItemStack carriedStack) {
        player.openMenu(
                new SimpleMenuProvider((i, inventory, arg2) -> {
                    return AccessoriesMenuVariant.openMenu(i, inventory, variant, targetEntity, carriedStack);
                }, Component.empty()),
                buf -> {
                    AccessoriesMenuData.ENDEC.encode(SerializationContext.attributes(RegistriesAttribute.of(buf.registryAccess())), ByteBufSerializer.of(buf), AccessoriesMenuData.of(targetEntity));
                });
    }

    public static void addAttributeTooltips(@Nullable Player player, ItemStack stack, Multimap<Holder<Attribute>, AttributeModifier> multimap, Consumer<Component> tooltipAddCallback, Item.TooltipContext context, TooltipFlag flag) {
        var neoTooltipCtx = AttributeTooltipContext.of(player, context, flag);

        var event = NeoForge.EVENT_BUS.post(new GatherSkippedAttributeTooltipsEvent(stack, neoTooltipCtx));

        if (event.isSkippingAll()) return;

        var modifiers = HashMultimap.create(multimap);

        modifiers.values().removeIf(m -> event.isSkipped(m.id()));

        if (modifiers.isEmpty()) return;

        AttributeUtil.applyTextFor(stack, tooltipAddCallback, modifiers, neoTooltipCtx);
    }
}
