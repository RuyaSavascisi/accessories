package io.wispforest.accessories.client.gui.components;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.slot.SlotGroup;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.client.GuiGraphicsUtils;
import io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen;
import io.wispforest.accessories.menu.SlotTypeAccessible;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.accessories.networking.server.SyncCosmeticToggle;
import io.wispforest.accessories.pond.owo.ComponentExtension;
import io.wispforest.accessories.pond.owo.MutableBoundingArea;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.NinePatchTexture;
import io.wispforest.owo.ui.util.ScissorStack;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.TriState;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.commands.arguments.SlotArgument.slot;

public class ComponentUtils {

    public static final ResourceLocation ENABLED_TEXTURE = Accessories.of("button/enabled");
    public static final ResourceLocation ENABLED_HOVERED_TEXTURE = Accessories.of("button/enabled_hovered");
    public static final ResourceLocation DISABLED_TEXTURE = Accessories.of("button/disabled");
    public static final ResourceLocation DISABLED_HOVERED_TEXTURE = Accessories.of("button/disabled_hovered");

    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("textures/gui/sprites/container/slot.png");
    private static final ResourceLocation DARK_SLOT = Accessories.of("textures/gui/dark_slot.png");

    public static final Surface BACKGROUND_SLOT_RENDERING_SURFACE = (context, component) -> {
        var slotComponents = new ArrayList<AccessoriesExperimentalScreen.ExtendedSlotComponent>();

        recursiveSearch(component, AccessoriesExperimentalScreen.ExtendedSlotComponent.class, slotComponents::add);

        context.push();
        context.translate(component.x(), component.y(), 0);

        for (var slotComponent : slotComponents) {
            context.blit(RenderType::guiTextured, getSlotTexture(), slotComponent.x() - component.x() - 1, slotComponent.y() - component.y() - 1, 0, 0, 18, 18, 18, 18);
        }

        for (var slotComponent : slotComponents) {
            var slot = slotComponent.slot();

            if (!(slot instanceof SlotTypeAccessible slotTypeAccessible) || !slotTypeAccessible.isCosmeticSlot()) continue;

            GuiGraphicsUtils.drawRectOutlineWithSpectrum(context, slotComponent.x() - component.x(), slotComponent.y() - component.y(), 0, 16, 16, 0.35f, true);
        }

        context.pop();
    };

    private static final ResourceLocation VERTICAL_VANILLA_SCROLLBAR_TEXTURE = Accessories.of("scrollbar_dark/vanilla_vertical");
    private static final ResourceLocation DISABLED_VERTICAL_VANILLA_SCROLLBAR_TEXTURE = Accessories.of("scrollbar_dark/vanilla_vertical_disabled");
    private static final ResourceLocation HORIZONTAL_VANILLA_SCROLLBAR_TEXTURE = Accessories.of("scrollbar_dark/vanilla_horizontal_disabled");
    private static final ResourceLocation DISABLED_HORIZONTAL_VANILLA_SCROLLBAR_TEXTURE = Accessories.of("scrollbar_dark/vanilla_horizontal_disabled");
    private static final ResourceLocation VANILLA_SCROLLBAR_TRACK_TEXTURE = Accessories.of("scrollbar_dark/track");

    public static final ScrollContainer.Scrollbar DARK_VANILLA = (context, x, y, width, height, trackX, trackY, trackWidth, trackHeight, lastInteractTime, direction, active) -> {
        NinePatchTexture.draw(VANILLA_SCROLLBAR_TRACK_TEXTURE, context, trackX, trackY, trackWidth, trackHeight);

        var texture = direction == ScrollContainer.ScrollDirection.VERTICAL
                ? active ? VERTICAL_VANILLA_SCROLLBAR_TEXTURE : DISABLED_VERTICAL_VANILLA_SCROLLBAR_TEXTURE
                : active ? HORIZONTAL_VANILLA_SCROLLBAR_TEXTURE : DISABLED_HORIZONTAL_VANILLA_SCROLLBAR_TEXTURE;

        NinePatchTexture.draw(texture, context, x + 1, y + 1, width - 2, height - 2);
    };

    public static final ResourceLocation DARK_PANEL_INSET_NINE_PATCH_TEXTURE = Accessories.of("panel/dark_inset");

    public static final Surface DARK_PANEL_INSET = (context, component) -> {
        NinePatchTexture.draw(DARK_PANEL_INSET_NINE_PATCH_TEXTURE, context, component);
    };

    public static final ResourceLocation DARK_ACTIVE_TEXTURE = Accessories.of("button_dark/active");
    public static final ResourceLocation DARK_HOVERED_TEXTURE = Accessories.of("button_dark/hovered");
    public static final ResourceLocation DARK_DISABLED_TEXTURE = Accessories.of("button_dark/disabled");

    private static final ButtonComponent.Renderer DARK_BUTTON_RENDERER = (context, button, delta) -> {
        RenderSystem.enableDepthTest();

        var texture = button.active ? (button.isHoveredOrFocused() ? DARK_HOVERED_TEXTURE : DARK_ACTIVE_TEXTURE) : DARK_DISABLED_TEXTURE;

        //System.out.println("Is Button Hovererd:" + button.isHovered());

        NinePatchTexture.draw(texture, context, button.getX(), button.getY(), button.width(), button.height());
    };

    public static ResourceLocation getSlotTexture() {
        return Accessories.config().screenOptions.isDarkMode() ? DARK_SLOT : SLOT;
    }

    public static Surface getPanelSurface() {
        return (context, component) -> {
            (Accessories.config().screenOptions.isDarkMode() ? Surface.DARK_PANEL : Surface.PANEL)
                    .draw(context, component);
        };
    }

    public static Surface getInsetPanelSurface() {
        return (context, component) -> {
            (Accessories.config().screenOptions.isDarkMode() ? DARK_PANEL_INSET : Surface.PANEL_INSET)
                    .draw(context, component);
        };
    }

    public static Surface getPanelWithInset(int insetWidth) {
        return (context, component) -> {
            var location = (Accessories.config().screenOptions.isDarkMode()
                    ? DARK_PANEL_INSET_NINE_PATCH_TEXTURE
                    : OwoUIDrawContext.PANEL_INSET_NINE_PATCH_TEXTURE);

            NinePatchTexture.draw(location, context, component.x() + insetWidth, component.y() + insetWidth, component.width() - insetWidth * 2, component.height() - insetWidth * 2);
        };
    }

    public static ButtonComponent.Renderer getButtonRenderer() {
        return (context, button, delta) -> {
            (Accessories.config().screenOptions.isDarkMode() ? DARK_BUTTON_RENDERER : ButtonComponent.Renderer.VANILLA)
                    .draw(context, button, delta);
        };
    }

    public static ScrollContainer.Scrollbar getScrollbarRenderer() {
        return (context, x, y, width, height, trackX, trackY, trackWidth, trackHeight, lastInteractTime, direction, active) -> {
            (Accessories.config().screenOptions.isDarkMode() ? DARK_VANILLA : ScrollContainer.Scrollbar.vanilla())
                    .draw(context, x, y, width, height, trackX, trackY, trackWidth, trackHeight, lastInteractTime, direction, active);
        };
    }

    public static <C extends io.wispforest.owo.ui.core.Component> void recursiveSearch(ParentComponent parentComponent, Class<C> target, Consumer<C> action) {
        if(parentComponent == null) return;

        for (var child : parentComponent.children()) {
            if(target.isInstance(child)) action.accept((C) child);
            if(child instanceof ParentComponent childParent) recursiveSearch(childParent, target, action);
        }
    }

    public static <S extends Slot & SlotTypeAccessible> Pair<io.wispforest.owo.ui.core.Component, PositionedRectangle> slotAndToggle(S slot, Function<Integer, AccessoriesExperimentalScreen.ExtendedSlotComponent> slotBuilder) {
        return slotAndToggle(slot, true, slotBuilder);
    }

    public static <S extends Slot & SlotTypeAccessible> Pair<io.wispforest.owo.ui.core.Component, PositionedRectangle> slotAndToggle(S slot, boolean isBatched, Function<Integer, AccessoriesExperimentalScreen.ExtendedSlotComponent> slotBuilder) {
        var btnPosition = Positioning.absolute(14, -1); //15, -1

        var toggleBtn = ComponentUtils.slotToggleBtn(slot)
                .configure(component -> {
                    component.zIndex(600) //900
                            .sizing(Sizing.fixed(5))
                            .positioning(btnPosition);
                });

        ((ComponentExtension)(toggleBtn)).allowIndividualOverdraw(true);

        var combinedLayout = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18))
                .child(
                        slotBuilder.apply(slot.index)
                                .margins(Insets.of(1))
                )
                .child(toggleBtn);

        var combinedArea = ((MutableBoundingArea) combinedLayout);

        //combinedArea.addInclusionZone(toggleBtn);
        //combinedArea.deepRecursiveChecking(true);

        return Pair.of(
                combinedLayout,
                toggleBtn
        );
    }

    public static <S extends Slot & SlotTypeAccessible> ButtonComponent slotToggleBtn(S slot) {
        return toggleBtn(Component.literal(""),
                () -> slot.getContainer().shouldRender(slot.getContainerSlot()),
                (btn) -> {
                    var entity = slot.getContainer().capability().entity();

                    AccessoriesNetworking
                            .sendToServer(SyncCosmeticToggle.of(entity.equals(Minecraft.getInstance().player) ? null : entity, slot.slotType(), slot.getContainerSlot()));
                });
    }

    public static final BiFunction<Color, ResourceLocation, RenderType> COLORED_GUI_TEXTURED = Util.memoize(
            (color, resourceLocation) -> {
                return RenderType.create(
                        "colored_gui_textured",
                        DefaultVertexFormat.POSITION_TEX_COLOR,
                        VertexFormat.Mode.QUADS,
                        786432,
                        RenderType.CompositeState.builder()
                                .setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, TriState.FALSE, false))
                                .setShaderState(RenderType.POSITION_TEXTURE_COLOR_SHADER)
                                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                                .setTransparencyState(new RenderStateShard.TransparencyStateShard("custom_blend",
                                        () -> {
                                            RenderSystem.setShaderColor(color.red(), color.green(), color.blue(), 1f);
                                            RenderSystem.enableBlend();
                                            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                                        }, () -> {
                                            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                                            RenderSystem.disableBlend();
                                            RenderSystem.defaultBlendFunc();
                                        })
                                )
                                .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                                .createCompositeState(false));
            }
    );

    public static ButtonComponent groupToggleBtn(AccessoriesExperimentalScreen screen, SlotGroup group) {
        var btn = toggleBtn(
                Component.empty(),
                () -> {
                    var menu = screen.getMenu();

                    return menu.isGroupSelected(group);
                },
                buttonComponent -> {
                    var menu = screen.getMenu();

                    if(menu.isGroupSelected(group)) {
                        menu.removeSelectedGroup(group);
                    } else {
                        menu.addSelectedGroup(group);
                    }

                    screen.rebuildAccessoriesComponent();
                },
                (context, button, delta) -> {
                    var textureAtlasSprite = Minecraft.getInstance()
                            .getTextureAtlas(ResourceLocation.withDefaultNamespace("textures/atlas/gui.png"))
                            .apply(group.icon());

                    var color = Color.WHITE;

                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderColor(color.red(), color.green(), color.blue(), 1f);
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

                    context.blitSprite(location -> COLORED_GUI_TEXTURED.apply(color, location), textureAtlasSprite, button.x() + 3, button.y() + 3, 8, 8, color.argb());

                    RenderSystem.depthMask(true);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        var tooltipData = new ArrayList<Component>();

        tooltipData.add(Component.translatable(group.translation()));
        if (UniqueSlotHandling.isUniqueGroup(group.name(), true)) tooltipData.add(Component.literal(group.name()).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));

        btn.sizing(Sizing.fixed(14))
                .tooltip(tooltipData);

        return btn;
    }

    public static ButtonComponent toggleBtn(net.minecraft.network.chat.Component message, Supplier<Boolean> stateSupplier, Consumer<ButtonComponent> onToggle) {
        return toggleBtn(message, stateSupplier, onToggle, (context, button, delta) -> {});
    }

    public static ButtonComponent toggleBtn(net.minecraft.network.chat.Component message, Supplier<Boolean> stateSupplier, Consumer<ButtonComponent> onToggle, ButtonComponent.Renderer extraRendering) {
        ButtonComponent.Renderer texturedRenderer = (context, btn, delta) -> {
            RenderSystem.enableDepthTest();
            var state = stateSupplier.get();

            ResourceLocation texture;

            if(btn.isHovered()) {
                texture = (state) ? ENABLED_HOVERED_TEXTURE : DISABLED_HOVERED_TEXTURE;
            } else {
                texture = (state) ? ENABLED_TEXTURE : DISABLED_TEXTURE;
            }

            context.push();

            Runnable drawCall = () -> {
                NinePatchTexture.draw(texture, context, btn.getX(), btn.getY(), btn.width(), btn.height());
                extraRendering.draw(context, btn, delta);
                context.flush();
            };

            if(btn instanceof ComponentExtension<?> extension && extension.allowIndividualOverdraw()) {
                ScissorStack.popFramesAndDraw(7, drawCall);
            } else {
                drawCall.run();
            }

            context.pop();
        };

        return Components.button(message, onToggle)
                .renderer(texturedRenderer);
    }

    public static <C extends BaseOwoHandledScreen.SlotComponent> io.wispforest.owo.ui.core.Component createCraftingComponent(int start, int end, Function<Integer, C> componentFactory, Consumer<Integer> slotEnabler, boolean isVertical) {
        var craftingLayout = isVertical ? Containers.verticalFlow(Sizing.fixed(18 * 2), Sizing.content()) : Containers.horizontalFlow(Sizing.content(), Sizing.fixed(18 * 2));

        slotEnabler.accept(0);
        slotEnabler.accept(1);
        slotEnabler.accept(2);
        slotEnabler.accept(3);
        slotEnabler.accept(4);

        craftingLayout.configure((FlowLayout layout) -> {
            layout.surface(BACKGROUND_SLOT_RENDERING_SURFACE)
                    .allowOverflow(true);
        });

        var childrenList = new ArrayList<io.wispforest.owo.ui.core.Component>();

        childrenList.add(
                (!isVertical ? Containers.verticalFlow(Sizing.content(), Sizing.content()) : Containers.horizontalFlow(Sizing.content(), Sizing.content()))
                        .child(componentFactory.apply(start + 1).margins(Insets.of(1)))
                        .child(componentFactory.apply(start + 2).margins(Insets.of(1)))
        );
        childrenList.add(
                (!isVertical ? Containers.verticalFlow(Sizing.content(), Sizing.content()) : Containers.horizontalFlow(Sizing.content(), Sizing.content()))
                        .child(componentFactory.apply(start + 3).margins(Insets.of(1)))
                        .child(componentFactory.apply(start + 4).margins(Insets.of(1)))
        );
        childrenList.add(
                new ArrowComponent((isVertical) ? ArrowComponent.Direction.DOWN : ArrowComponent.Direction.RIGHT)
                        .centered(true)
                        .margins(Insets.of(3, 3, 1, 1))
                        .id("crafting_arrow")
                //Components.spacer().sizing(Sizing.fixed(4))
        );
        childrenList.add(
                (!isVertical ? Containers.verticalFlow(Sizing.content(), Sizing.expand()) : Containers.horizontalFlow(Sizing.expand(), Sizing.content()))
                        .child(componentFactory.apply(start).margins(Insets.of(1)))
                        .horizontalAlignment(HorizontalAlignment.CENTER)
                        .verticalAlignment(VerticalAlignment.CENTER)
        );

        craftingLayout.children(childrenList).horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        return craftingLayout;
    }

    public static <C extends BaseOwoHandledScreen.SlotComponent> io.wispforest.owo.ui.core.Component createPlayerInv(int start, int end, Function<Integer, C> componentFactory, Consumer<Integer> slotEnabler) {
        var playerLayout = Containers.verticalFlow(Sizing.content(), Sizing.content());

        int row = 0;

        var rowLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .configure((FlowLayout layout) -> {
                    layout.surface(BACKGROUND_SLOT_RENDERING_SURFACE)
                            .allowOverflow(true);
                });

        int rowCount = 0;

        for (int i = start; i < end; i++) {
            var slotComponent = componentFactory.apply(i);

            slotEnabler.accept(i);

            rowLayout.child(slotComponent.margins(Insets.of(1)));

            if(row >= 8) {
                playerLayout.child(rowLayout);

                rowLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                        .configure((FlowLayout layout) -> {
                            layout.surface(BACKGROUND_SLOT_RENDERING_SURFACE)
                                    .allowOverflow(true);
                        });

                rowCount++;

                if(rowCount == 3) rowLayout.margins(Insets.top(4));

                row = 0;
            } else {
                row++;
            }
        }

        return playerLayout.allowOverflow(true);
    }

    public interface CreativeScreenExtension {
        Event<OnCreativeTabChange> getEvent();

        CreativeModeTab getTab();
    }

    public interface OnCreativeTabChange {
        void onTabChange(CreativeModeTab tab);
    }
}
