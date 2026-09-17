package com.solr98.beyondintegration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * SuperbWarfare 容器方块服务端数据提供器（Jade）：反射读取方块实体 entityTag 中的
 * 网络 ID 与自定义名称并写入服务端数据。
 */
public enum ContainerServerProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    // 服务端数据键：网络 ID / 网络名
    private static final String NET_ID_KEY = "Net_id";
    private static final String NET_NAME_KEY = "bce_net_name";

    // 反射读取 ContainerBlockEntity 的 entityTag 字段提取网络信息（失败静默）
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        try {
            Class<?> containerClass = Class.forName("com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity");
            if (!containerClass.isInstance(accessor.getBlockEntity())) return;

            Object blockEntity = accessor.getBlockEntity();
            java.lang.reflect.Field entityTagField = containerClass.getDeclaredField("entityTag");
            entityTagField.setAccessible(true);
            CompoundTag entityTag = (CompoundTag) entityTagField.get(blockEntity);
            if (entityTag == null || !entityTag.contains(NET_ID_KEY)) return;

            data.putInt(NET_ID_KEY, entityTag.getInt(NET_ID_KEY));
            if (entityTag.contains("customName")) {
                data.putString(NET_NAME_KEY, entityTag.getString("customName"));
            }
        } catch (Exception ignored) {}
    }

    // 插件唯一 ID
    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:container_server");
    }
}

