package com.solr98.beyondintegration.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * 附魔台侧栏图标按钮（16x16 槽位样式，挂 BD 左侧按钮栏）。
 * 图标与提示文本由外部实时提供（渲染时读取最新状态）；点击动作由 onPress 决定。
 */
public class EnchantIconBtn extends Button {
    private static final ResourceLocation SLOT = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    private final Supplier<ItemStack> iconSupplier;

    public EnchantIconBtn(int x, int y, Supplier<ItemStack> iconSupplier, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
        this.iconSupplier = iconSupplier;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        g.blit(isHovered ? SLOT_HOVERED : SLOT, getX(), getY(), 0, 0, 16, 16, 16, 16);
        ItemStack icon = iconSupplier.get();
        if (!icon.isEmpty()) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(getX() + 1, getY() + 1, 1);
            pose.scale(0.85f, 0.85f, 1);
            g.renderFakeItem(icon, 0, 0);
            pose.popPose();
        }
    }
}