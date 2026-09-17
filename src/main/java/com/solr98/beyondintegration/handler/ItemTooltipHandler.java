package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import com.wintercogs.beyonddimensions.common.item.MatterCompressionBall;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * 物品提示信息处理器：为携带网络 ID 数据的物品/方块实体
 * 在 Tooltip 中追加网络 ID 显示行。
 */
public class ItemTooltipHandler {
    /** 方块实体 NBT 中存储网络 ID 的键名（兼容旧版 BeyondChest） */
    private static final String BCE_NET_ID_KEY = "Net_id";

    /** 工具提示事件回调 */
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();

        // 附魔分离保护标记（beyond_integration:protect_sep，GUI Ctrl+右键切换）：绿色提示行
        var protectData = stack.get(DataComponents.CUSTOM_DATA);
        if (protectData != null && protectData.copyTag().getBoolean("beyond_integration:protect_sep")) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.protected")
                    .withStyle(ChatFormatting.GREEN));
        }

        // 数据组件中的网络 ID
        Integer netId = stack.get(BDDataComponents.NET_ID_DATA.get());
        if (netId != null && netId >= 0) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.network_id", netId).withStyle(ChatFormatting.AQUA));
        }

        // 悬停物品可左键快速存入网络时提示（正向：悬停网络物品+光标持物；反向：光标网络物品+悬停物品）
        if (canQuickStoreToNetwork(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.hover_insert")
                    .withStyle(ChatFormatting.YELLOW));
        }

        // 方块物品：读取其方块实体 NBT 中的 Entity 网络 ID（兼容旧数据）
        if (stack.getItem() instanceof BlockItem) {
            var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (beData != null) {
                CompoundTag rootTag = beData.copyTag();
                if (rootTag.contains("Entity", CompoundTag.TAG_COMPOUND)) {
                    CompoundTag entityTag = rootTag.getCompound("Entity");
                    if (entityTag.contains(BCE_NET_ID_KEY)) {
                        netId = entityTag.getInt(BCE_NET_ID_KEY);
                        event.getToolTip().add(Component.translatable("tooltip.beyond_integration.network_id", netId).withStyle(ChatFormatting.AQUA));
                    }
                }
            }
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
            contents = stack.get(BDDataComponents.ISTACK_SLOTS.get());
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

    /** 解析存储键显示名：物品用悬停名、流体用悬停名，其余类型回退为类型路径 */
    private static Component matterBallName(IStackKey<?> key) {
        try {
            if (key instanceof ItemStackKey ik) return ik.getReadOnlyStack().getHoverName();
            if (key instanceof FluidStackKey fk) return fk.getReadOnlyStack().getHoverName();
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
        return (!carried.isEmpty() && netIdOf(stack) >= 0)
                || (!stack.isEmpty() && netIdOf(carried) >= 0);
    }

    /** 读取网络 ID（BD 数据组件），未绑定返回 -1 */
    private static int netIdOf(ItemStack stack) {
        Integer id = stack.get(BDDataComponents.NET_ID_DATA.get());
        return id != null ? id : -1;
    }
}
