package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.MatterCompressionBall;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 客户端物品 Tooltip 处理器。
 * 为带有 BD 网络 ID 的物品（兼容 TaCZ/NetedItem 及自定义 NBT 两种来源）
 * 添加"网络 ID"灰色提示行，并为带保护标记的物品添加绿色保护提示。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class NetworkIdTooltipHandler {

    /** 物品 Tooltip 事件：追加网络 ID 与保护状态提示。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int netId = getNetId(stack);
        if (netId >= 0) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.net_id", netId)
                    .withStyle(ChatFormatting.GRAY));
        }

        // 悬停物品可左键快速存入网络时提示（正向：悬停网络物品+光标持物；反向：光标网络物品+悬停物品）
        if (canQuickStoreToNetwork(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.hover_insert")
                    .withStyle(ChatFormatting.YELLOW));
        }

        if (stack.hasTag() && stack.getTag().getBoolean("beyond_integration:protect_sep")) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.protected")
                    .withStyle(ChatFormatting.GREEN));
        }

        // 物质压缩球：有内部存储时显示内容列表
        addMatterBallContents(event);
    }

    /** 物质压缩球内部存储最多显示的条目数 */
    private static final int MATTER_BALL_MAX_LINES = 8;

    /** 为物质压缩球追加内部存储内容（含标题、条目、超量省略提示） */
    private static void addMatterBallContents(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof MatterCompressionBall)) return;

        List<KeyAmount> contents;
        try {
            if (!MatterCompressionBall.hasIStackList(stack)) return;
            contents = MatterCompressionBall.getIStackList(stack);
        } catch (Throwable ignored) {
            return;
        }
        if (contents == null || contents.isEmpty()) return;

        int total = 0;
        for (KeyAmount ka : contents) {
            if (ka != null && !ka.isEmpty()) total++;
        }
        if (total <= 0) return;

        // 按住 Shift 显示全部内容，否则仅显示前 MATTER_BALL_MAX_LINES 条
        boolean showAll = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        int maxLines = showAll ? Integer.MAX_VALUE : MATTER_BALL_MAX_LINES;

        event.getToolTip().add(Component.translatable("tooltip.beyond_integration.matter_ball.contents")
                .withStyle(ChatFormatting.GOLD));
        int shown = 0;
        for (KeyAmount ka : contents) {
            if (ka == null || ka.isEmpty()) continue;
            if (shown >= maxLines) break;
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.matter_ball.entry",
                    matterBallName(ka.key()), ka.amount()).withStyle(ChatFormatting.GRAY));
            shown++;
        }
        if (total > shown) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.matter_ball.more", total - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 解析存储键显示名：物品用悬停名、流体用显示名，其余类型回退为类型路径 */
    private static Component matterBallName(IStackKey<?> key) {
        try {
            if (key instanceof ItemStackKey ik) return ik.getReadOnlyStack().getHoverName();
            if (key instanceof FluidStackKey fk) return fk.getReadOnlyStack().getDisplayName();
        } catch (Throwable ignored) {
        }
        return Component.literal(key.getTypeId().getPath());
    }

    /**
     * 判断悬停中的该物品是否可左键快速存入网络（用于显示提示）：
     * 正向 = 悬停网络物品且光标持物；反向 = 光标网络物品且悬停物品非空。
     */
    private static boolean canQuickStoreToNetwork(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return false;
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || hovered.getItem() != stack) return false;
        ItemStack carried = screen.getMenu().getCarried();
        return (!carried.isEmpty() && getNetId(stack) >= 0)
                || (!stack.isEmpty() && getNetId(carried) >= 0);
    }

    /** 尝试从物品栈中解析网络 ID：优先用 NetedItem，缺失时回退读取 NBT 的 "NetId"，失败返回 -1。 */
    private static int getNetId(ItemStack stack) {
        try {
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) return netId;
        } catch (NoClassDefFoundError ignored) {}
        if (stack.hasTag() && stack.getTag().contains("NetId")) {
            return stack.getTag().getInt("NetId");
        }
        return -1;
    }
}
