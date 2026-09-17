package com.solr98.beyondintegration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 网络方块客户端提示组件（Jade）：从服务端数据渲染自定义网络名与附魔分离开关状态。
 */
public enum BlockClientProvider implements IBlockComponentProvider {
    INSTANCE;

    // 服务端数据键：自定义网络名 / 附魔分离开关
    private static final String CUSTOM_NAME_KEY = "bce_custom_name";
    private static final String ENCHANT_SEP_KEY = "bce_enchant_sep";

    // 提示入口：有服务端数据时依次追加网络名与附魔分离状态
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) return;

        appendCustomName(tooltip, data);
        appendEnchantSep(tooltip, data);
    }

    // 追加自定义网络名行
    private static void appendCustomName(ITooltip tooltip, CompoundTag data) {
        if (data.contains(CUSTOM_NAME_KEY)) {
            String customName = data.getString(CUSTOM_NAME_KEY);
            tooltip.add(Component.translatable("jade.beyond_integration.network_custom_name", customName));
        }
    }

    // 追加附魔分离开关状态行（开/关）
    private static void appendEnchantSep(ITooltip tooltip, CompoundTag data) {
        if (!data.contains(ENCHANT_SEP_KEY)) return;
        boolean sepEnabled = data.getBoolean(ENCHANT_SEP_KEY);
        Component sepText = Component.translatable("jade.beyond_integration.enchant_sep",
                Component.translatable(sepEnabled ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"));
        tooltip.add(sepText);
    }

    // 插件唯一 ID
    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:bd_client");
    }
}

