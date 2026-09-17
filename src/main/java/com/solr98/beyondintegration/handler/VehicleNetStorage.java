package com.solr98.beyondintegration.handler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 载具-网络绑定存储（非 SW 载具通用）：维护载具 UUID → 网络 ID 的映射，
 * 供 ywzj 等载具查询其绑定的维度网络。
 */
public class VehicleNetStorage {
    /** 载具 UUID → 网络 ID 映射（线程安全）。 */
    private static final Map<UUID, Integer> map = new ConcurrentHashMap<>();

    /** 将载具与网络绑定。 */
    public static void bindVehicle(UUID vehicleUuid, int netId) { map.put(vehicleUuid, netId); }
    /** 解绑载具与网络。 */
    public static void unbindVehicle(UUID vehicleUuid) { map.remove(vehicleUuid); }
    /** 查询载具绑定的网络 ID（未绑定返回 -1）。 */
    public static int getBoundNetId(UUID vehicleUuid) { return map.getOrDefault(vehicleUuid, -1); }
    /** 查询载具绑定的维度网络（未绑定或不存在时返回 null）。 */
    public static DimensionsNet getNetworkForVehicle(UUID vehicleUuid) {
        Integer netId = map.get(vehicleUuid);
        return netId == null ? null : DimensionsNet.getNetFromId(netId);
    }
}

