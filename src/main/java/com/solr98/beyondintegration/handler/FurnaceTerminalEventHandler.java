package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 熔炉烧终端事件（BD 修改，配置 {@code bd_tweaks.furnace_terminal_smelt_all}，默认关闭）。
 *
 * <p>熔炉通过定制配方 {@code beyond_integration:terminal_smelt} 烧炼网络终端：
 * 产物为输入终端副本（继承数据组件，保留网络绑定，终端不消耗）。
 * 玩家从熔炉取出该终端产物时触发本事件：把该终端绑定网络内所有可烧炼物品
 * 按熔炉配方（SMELTING）一次性转换，配方经验按 1 XP = 20 mB 存入网络经验流体，
 * 并以 action bar 反馈实际转换数量（便于确认功能是否触发）。</p>
 */
public final class FurnaceTerminalEventHandler {

    private FurnaceTerminalEventHandler() {}

    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!CommandConfig.furnaceTerminalSmeltAllEnabled()) return;

        ItemStack smelted = event.getSmelting();
        if (!FurnaceTerminalSmelter.isTerminal(smelted)) return;

        DimensionsNet net = NetedItem.getNet(smelted);
        if (net == null) return;

        Level level = event.getEntity() != null ? event.getEntity().level() : null;
        if (level == null || level.isClientSide()) return;

        int converted = FurnaceTerminalSmelter.smeltAll(net, level, RecipeType.SMELTING);
        if (event.getEntity() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(converted > 0
                    ? "message.beyond_integration.furnace_terminal.smelted"
                    : "message.beyond_integration.furnace_terminal.nothing",
                    net.getId(), converted), true);
        }
    }
}
