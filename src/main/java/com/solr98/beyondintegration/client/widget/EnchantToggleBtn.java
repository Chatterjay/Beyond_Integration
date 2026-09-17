package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 附魔分离开关按钮（客户端 GUI 组件）：16x16 槽位样式按钮，
 * 通过 SuperbAmmoCache 缓存控制附魔分离功能开关，并渲染附魔书图标，
 * 悬停时显示对应的开关状态提示。
 */
public class EnchantToggleBtn extends Button {
    /** 开关打开时的按钮纹理。 */
    private static final ResourceLocation SLOT = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    /** 开关关闭时的按钮纹理。 */
    private static final ResourceLocation SLOT_DISABLED = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_disabled.png");
    /** 悬停时的按钮纹理。 */
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    /** 构造一个位于 (x, y) 的 16x16 附魔分离开关按钮。 */
    public EnchantToggleBtn(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    /** 渲染按钮：按开关状态选择纹理，并在按钮上绘制附魔书图标。 */
    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        // 按钮纹理表示模式：开=正常，关=禁用（暗）纹理
        boolean on = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
        var tex = !on ? SLOT_DISABLED : (isHovered ? SLOT_HOVERED : SLOT);
        g.blit(tex, getX(), getY(), 0, 0, 16, 16, 16, 16);

        var pose = g.pose();
        pose.pushPose();
        // 缩小后居中绘制附魔书图标
        pose.translate(getX() + 1, getY() + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        g.renderFakeItem(new ItemStack(Items.ENCHANTED_BOOK), 0, 0);
        pose.popPose();
    }

    /** 根据当前开关状态刷新按钮悬停提示文本。 */
    public void updateTooltip() {
        boolean on = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.beyond_integration.enchant_sep",
                        Component.translatable(on ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"))));
    }
}
