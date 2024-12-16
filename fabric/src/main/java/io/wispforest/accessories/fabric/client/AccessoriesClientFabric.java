package io.wispforest.accessories.fabric.client;

import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.client.BuiltinAccessoryRenderers;
import io.wispforest.accessories.client.AccessoriesClient;
import io.wispforest.accessories.client.AccessoriesRenderLayer;
import io.wispforest.accessories.client.gui.AccessoriesScreenBase;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.fabric.AccessoriesFabric;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import io.wispforest.accessories.menu.AccessoriesMenuTypes;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

import static io.wispforest.accessories.Accessories.MODID;

public class AccessoriesClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AccessoriesClient.initConfigStuff();
        AccessoriesClient.init();

        AccessoriesNetworking.initClient();

        AccessoriesMenuTypes.registerClientMenuConstructors(MenuScreens::register);

        {
            var afterOthers = Accessories.of("accessories_after_others");

            ItemTooltipCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, afterOthers);

            ItemTooltipCallback.EVENT.register(afterOthers, (stack, tooltipContext, tooltipType, lines) -> {
                var tooltipData = new ArrayList<Component>();

                AccessoriesEventHandler.getTooltipData(Minecraft.getInstance().player, stack, tooltipData, tooltipContext, tooltipType);

                if(!tooltipData.isEmpty()) lines.addAll(1, tooltipData);
            });
        }

        BuiltInRegistries.ITEM.forEach(BuiltinAccessoryRenderers::onAddCallback);

//        RegistryEntryAddedCallback.event(BuiltInRegistries.ITEM).register((i, resourceLocation, item) -> {
//            BuiltinAccessoryRenderers.onAddCallback(item);
//        });

        AccessoriesClient.OPEN_SCREEN = KeyBindingHelper.registerKeyBinding(new KeyMapping(MODID + ".key.open_accessories_screen", GLFW.GLFW_KEY_H, MODID + ".key.category.accessories"));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (AccessoriesClient.OPEN_SCREEN.consumeClick()){
                if (client.screen instanceof AccessoriesScreenBase) {
                    client.setScreen(null);
                } else {
                    AccessoriesClient.attemptToOpenScreen(client.player.isShiftKeyDown());
                }
            }
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if(!(entityRenderer.getModel() instanceof HumanoidModel)) return;

            // TODO: CONFIRM THIS IS CORRECT!
            var rendererCasted = (RenderLayerParent<LivingEntityRenderState, EntityModel<LivingEntityRenderState>>) entityRenderer;

            registrationHelper.register(new AccessoriesRenderLayer<>(rendererCasted));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
                var lookup = AccessoriesFabric.CAPABILITY;

                if(lookup.getProvider(entityType) != null) continue;

                lookup.registerForType((entity, unused) -> {
                    if(!(entity instanceof LivingEntity livingEntity)) return null;

                    var slots = EntitySlotLoader.getEntitySlots(livingEntity);

                    if(slots.isEmpty()) return null;

                    return new AccessoriesCapabilityImpl(livingEntity);
                }, entityType);
            }

            AccessoriesClient.initalConfigDataSync();
        });
    }
}
