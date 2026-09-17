package com.solr98.beyondintegration.client;

import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 悬停可存入网络的槽位左上角 "+" 提示角标（客户端渲染）。
 *
 * <p>光标持有物品/网络物品，且悬停到已绑定网络的物品（含网络终端）上时，
 * 在该槽位左上角绘制绿色 "+"：提示可左键快速存入
 * （对应 {@code NetedItemClickMixin} 的双向快速存入）。</p>
 *
 * <p>显示条件与 {@code NetworkIdTooltipHandler#canQuickStoreToNetwork} 一致：
 * 正向（悬停网络物品 + 光标持物）或反向（光标网络物品 + 悬停物品非空）。</p>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class HoverStoreOverlay {

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        ItemStack carried = screen.getMenu().getCarried();
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || !hovered.hasItem()) return;
        ItemStack stack = hovered.getItem();

        boolean canStore = (!carried.isEmpty() && netIdOf(stack) >= 0)
                || (!stack.isEmpty() && netIdOf(carried) >= 0);
        if (!canStore) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int x = screen.getGuiLeft() + hovered.x - 2; // 槽位左上角
        int y = screen.getGuiTop() + hovered.y - 2;
        g.drawString(font, "+", x + 1, y, 0xFF55FF55, true);
    }

    /** 读取物品绑定的网络 ID（NetedItem/NBT），未绑定返回 -1 */
    private static int netIdOf(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        try {
            return NetedItem.getNetId(stack);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private HoverStoreOverlay() {}
}
