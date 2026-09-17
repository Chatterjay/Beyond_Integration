package com.solr98.beyondintegration.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * SuperbWarfare 容器方块客户端提示组件（Jade）：渲染 [网络名 (#网络ID)] 信息行。
 */
public enum ContainerClientProvider implements IBlockComponentProvider {
    INSTANCE;

    // 服务端数据键：网络 ID / 网络名
    private static final String NET_ID_KEY = "Net_id";
    private static final String NET_NAME_KEY = "bce_net_name";

    // 从服务端数据读取网络信息并以青色文本追加到提示
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(NET_ID_KEY)) return;
        int netId = data.getInt(NET_ID_KEY);
        String netName = data.getString(NET_NAME_KEY);
        String text = netName.isEmpty()
                ? "Net #" + netId
                : netName + " (#" + netId + ")";
        tooltip.add(Component.literal("[" + text + "]").withStyle(ChatFormatting.AQUA));
    }

    // 插件唯一 ID
    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:container_client");
    }
}

