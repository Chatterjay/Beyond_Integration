package com.solr98.beyondintegration.jade;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 载具实体服务端数据提供器（Jade）：从载具的网络缓存（INetCachedVehicle）读取
 * 所属网络 ID 与自定义名称写入服务端数据。
 */
public enum VehicleServerProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    // 服务端数据键：网络 ID / 网络名
    private static final String NET_ID_KEY = "Net_id";
    private static final String NET_NAME_KEY = "bce_net_name";

    // 读取载具缓存的网络信息并写入服务端数据（非目标载具直接跳过）
    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity)) return;
        DimensionsNet net = ((INetCachedVehicle) accessor.getEntity()).getNetCache().getNet();
        if (net == null) return;
        data.putInt(NET_ID_KEY, net.getId());
        if (net instanceof NetworkNameProvider nnp) {
            String name = nnp.getCustomName();
            if (name != null && !name.isEmpty()) data.putString(NET_NAME_KEY, name);
        }
    }

    // 插件唯一 ID
    @Override
    public ResourceLocation getUid() { return ResourceLocation.parse("beyond_integration:vehicle_server"); }
}

