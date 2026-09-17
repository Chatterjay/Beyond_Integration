package com.solr98.beyondintegration.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * 附魔台工作站模式切换按钮（客户端 GUI 组件）：16x16 槽位样式按钮，
 * 挂载于 BD 左侧按钮栏；图标随模式切换（原版=附魔台，神化=经验瓶），
 * 悬停时显示当前模式与切换说明 tooltip。
 */
public class EnchantModeBtn extends Button {
    private static final ResourceLocation SLOT = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    /** 模式读取器：true=神化(Apothic)，false=原版（由外部提供，实时反映菜单状态） */
    private final Supplier<Boolean> apothGetter;

    public EnchantModeBtn(int x, int y, Supplier<Boolean> apothGetter, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
        this.apothGetter = apothGetter;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        g.blit(isHovered ? SLOT_HOVERED : SLOT, getX(), getY(), 0, 0, 16, 16, 16, 16);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(getX() + 1, getY() + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        ItemStack icon = new ItemStack(apothGetter.get() ? Items.EXPERIENCE_BOTTLE : Items.ENCHANTING_TABLE);
        g.renderFakeItem(icon, 0, 0);
        pose.popPose();
    }

    /** 按当前模式刷新悬停提示文本 */
    public void updateTooltip() {
        boolean apoth = apothGetter.get();
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                "gui.beyond_integration.enchant.mode", Component.translatable(
                        apoth ? "gui.beyond_integration.enchant.mode.apoth" : "gui.beyond_integration.enchant.mode.vanilla"),
                Component.translatable(apoth ? "gui.beyond_integration.enchant.mode.hint.apoth" : "gui.beyond_integration.enchant.mode.hint.vanilla"))));
    }
}