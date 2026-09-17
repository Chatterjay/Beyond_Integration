package com.solr98.beyondintegration.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 合成成功提示 Toast（客户端）。
 * 展示合成出的物品图标、名称与数量，显示 2.5 秒后自动消失。
 */
public class CraftToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement"); // 背景贴图（沿用进度 Toast 样式）
    private static final long DISPLAY_TIME_MS = 2500L; // 显示时长（毫秒）
    private final ItemStack result; // 合成结果物品
    private final int count; // 合成数量

    public CraftToast(ItemStack result, int count) {
        this.result = result;
        this.count = count;
    }

    @Override
    /** 每帧渲染 Toast 内容，超时后隐藏 */
    public Visibility render(GuiGraphics g, ToastComponent comp, long timer) {
        g.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        g.renderFakeItem(result, 8, 8);
        g.drawString(comp.getMinecraft().font, Component.translatable("toast.beyond_integration.craft_success"), 30, 7, 0xFFFF5500);
        Component desc = result.getHoverName();
        if (count > 1) desc = Component.literal("").append(desc).append(Component.literal(" \u00D7" + count));
        g.drawString(comp.getMinecraft().font, desc, 30, 18, 0xFFFFFF);
        return timer >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    /** 弹出合成成功 Toast */
    public static void show(ItemStack result, int count) {
        Minecraft.getInstance().getToasts().addToast(new CraftToast(result, count));
    }
}

