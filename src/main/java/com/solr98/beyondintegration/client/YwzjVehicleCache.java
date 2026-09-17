package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 云之载具网络缓存（客户端单例）。
 * 缓存载具绑定网络的 ID、能量、网络名与弹药数量快照，
 * 供 HUD 直接读取，数据超时（TTL 2 分钟）自动失效。
 */
public enum YwzjVehicleCache {
    INSTANCE;

    private int netId = -1; // 网络 ID（-1 表示未绑定）
    private long energy = -1; // 网络能量
    private String networkName = ""; // 网络名称
    private boolean hasData = false; // 是否已有缓存数据
    private long lastUpdateTime = 0; // 上次更新时间戳
    private final Map<String, Long> ammoMap = new HashMap<>(); // 弹药名称 → 数量
    private static final long TTL = 120000; // 缓存有效期（毫秒）

    /** 更新整份缓存快照（由服务端同步包调用） */
    public void update(int netId, long energy, String networkName, Map<String, Long> ammoMap) {
        this.netId = netId;
        this.energy = energy;
        this.networkName = networkName != null ? networkName : "";
        this.ammoMap.clear();
        if (ammoMap != null) this.ammoMap.putAll(ammoMap);
        this.hasData = true;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public int getNetId() { return netId; } // 获取网络 ID
    public long getEnergy() { return energy; } // 获取网络能量
    public String getNetworkName() { return networkName; } // 获取网络名称
    /** 数据是否在有效期内 */
    public boolean hasData() { return hasData && System.currentTimeMillis() - lastUpdateTime < TTL; }

    public long getAmmoCount(String key) { return ammoMap.getOrDefault(key, 0L); } // 获取指定弹药数量（无则 0）
    public Map<String, Long> getAmmoMap() { return new HashMap<>(ammoMap); } // 获取弹药副本
    public boolean hasAnyAmmo() { return ammoMap.values().stream().anyMatch(v -> v > 0); } // 是否存有任何弹药

    /** 清空缓存（下车/断线时调用） */
    public void reset() {
        netId = -1;
        energy = -1;
        networkName = "";
        ammoMap.clear();
        hasData = false;
    }
}

