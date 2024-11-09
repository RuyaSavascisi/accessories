package io.wispforest.accessories.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class PixelPerfectTextureComponent extends BaseComponent {

    private final ResourceLocation texture;

    private final int textureWidth;
    private final int textureHeight;

    public PixelPerfectTextureComponent(ResourceLocation texture, int textureWidth, int textureHeight, Sizing horizontalSizing, Sizing verticalSizing) {
        super();

        this.texture = texture;

        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;

        if(horizontalSizing.isContent()) throw new IllegalStateException("HorizontalSizing of PixelPerfectTextureComponent was found to be Content Sizing, which is not allowed!");
        if(verticalSizing.isContent()) throw new IllegalStateException("VerticalSizing of PixelPerfectTextureComponent was found to be Content Sizing, which is not allowed!");

        this.horizontalSizing(horizontalSizing);
        this.verticalSizing(verticalSizing);
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        drawPixelPerfectTextureQuad(context, texture, textureWidth, textureHeight, this.x(), this.y(), 0, this.width(), this.height());
    }

    public static void drawPixelPerfectTextureQuad(OwoUIDrawContext context, ResourceLocation texture, int textureWidth, int textureHeight, int x1, int y1, float z, int width, int height) {
        int x2 = x1 + width;
        int y2 = y1 + height;

        var vertexConsumer = context.vertexConsumers().getBuffer(RenderType.guiTextured(texture));
        var matrix4f = context.pose().last().pose();

        vertexConsumer.addVertex(matrix4f, x1, y1, z)
                .setColor(0xFFFFFFFF)
                .setUv(0, 0);

        vertexConsumer.addVertex(matrix4f, x1, y2, z)
                .setColor(0xFFFFFFFF)
                .setUv(0, 1);

        vertexConsumer.addVertex(matrix4f, x2, y2, z)
                .setColor(0xFFFFFFFF)
                .setUv(1, 1);

        vertexConsumer.addVertex(matrix4f, x2, y1, z)
                .setColor(0xFFFFFFFF)
                .setUv(1, 0);
    }
}
