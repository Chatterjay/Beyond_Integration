package com.solr98.beyondintegration.client;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestItemAmmoPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SW 网络缓存（按网络一份快照）。
 * 快照表 netId → NetSnapshot（固定项）；currentNetId/vehicleNetId 为当前指针。
 * ITEM 型弹药计数独立短 TTL 缓存（现查现用，5s 过期）。
 */
public enum SuperbAmmoCache {
    INSTANCE;

    /** 单网络快照：弹药、能量、网络名、附魔分离开关、更新时间与弹药列表 */
    public static final class NetSnapshot {
        public final Map<String, Long> ammo;
        public final long energy;
        public final String netName;
        public final boolean enchantSeparation;
        public final long lastUpdate;
        public final List<String> ammoList;

        NetSnapshot(Map<String, Long> ammo, long energy, String netName, boolean enchantSeparation,
                    long lastUpdate, List<String> ammoList) {
            this.ammo = ammo;
            this.energy = energy;
            this.netName = netName;
            this.enchantSeparation = enchantSeparation;
            this.lastUpdate = lastUpdate;
            this.ammoList = ammoList;
        }
    }

    private final Map<Integer, NetSnapshot> snapshots = new HashMap<>(); // 网络 ID → 快照表

    private int currentNetId = -1; // 当前玩家绑定网络指针
    private int vehicleNetId = -1; // 当前载具绑定网络指针

    private static final long TTL = Long.MAX_VALUE; // 玩家网络快照有效期为永久
    private static final long VEHICLE_TTL = 120000; // 载具快照有效期 2 分钟

    // ── ITEM 现查缓存（TTL 5s，区分玩家/载具来源）──
    private final Map<String, ItemEntry> itemCache = new HashMap<>(); // ITEM 条目键 → 缓存项
    private static final long ITEM_TTL = 5000; // ITEM 缓存有效期 5 秒
    private boolean itemRequestPending = false; // 是否有进行中的请求（节流用）
    private long itemLastRequest = 0; // 上次请求时间戳
    /** 请求中的 netId → 来源标记（玩家/载具），响应按 netId 匹配，避免交错请求标错来源 */
    private final Map<Integer, Boolean> itemVehicleByNet = new HashMap<>();
    private static final long ITEM_REQUEST_TIMEOUT = 2000; // 请求最小间隔 2 秒

    /** ITEM 缓存条目：数量、时间戳、来源（玩家/载具） */
    private record ItemEntry(long count, long ts, boolean vehicle) {}

    // ═══════════ S2C 写入 ═══════════

    /** 全量快照（SuperbAmmoStatusResponsePacket）。netId < 0 视为 reset（仅载具解绑场景，清载具指针）。 */
    public void update(int netId, boolean isVehicle, String netName, Map<String, Long> ammo,
                       long energy, boolean enchantSeparation, List<String> ammoList) {
        synchronized (LOCK) {
            if (netId < 0) {
                vehicleNetId = -1;
                clearItems(true);
                return;
            }
            NetSnapshot snap = new NetSnapshot(new HashMap<>(ammo), energy,
                    netName != null ? netName : "", enchantSeparation,
                    System.currentTimeMillis(), ammoList != null ? new ArrayList<>(ammoList) : null);
            snapshots.put(netId, snap);
            // 服务端推送的附魔分离状态同步到独立缓存（与 SW 快照解耦）
            EnchantSeparationState.INSTANCE.set(netId, enchantSeparation);
            if (isVehicle) {
                vehicleNetId = netId;
            } else {
                currentNetId = netId;
            }
        }
    }

    /** 增量/全量替换（SuperbAmmoDeltaS2CPacket）。full=true 时 ammo 为全量、energy 为绝对值。 */
    public void applyDelta(int netId, boolean isVehicle, boolean full, Map<String, Long> ammo,
                           long energy, String netName, boolean enchantSeparation) {
        synchronized (LOCK) {
            if (netId < 0) {
                update(-1, isVehicle, null, null, -1, true, null);
                return;
            }
            NetSnapshot snap = snapshots.get(netId);
            if (snap == null) {
                if (!isVehicle) {
                    PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket(netId));
                }
                return;
            }

            Map<String, Long> newAmmo;
            if (full) {
                newAmmo = new HashMap<>(ammo);
            } else {
                newAmmo = new HashMap<>(snap.ammo);
                for (var entry : ammo.entrySet()) {
                    long v = newAmmo.getOrDefault(entry.getKey(), 0L) + entry.getValue();
                    if (v <= 0) newAmmo.remove(entry.getKey());
                    else newAmmo.put(entry.getKey(), v);
                }
            }
            long newEnergy = full ? energy : snap.energy + energy;
            NetSnapshot ns = new NetSnapshot(newAmmo, newEnergy,
                    netName != null && !netName.isEmpty() ? netName : snap.netName,
                    enchantSeparation, System.currentTimeMillis(), snap.ammoList);
            snapshots.put(netId, ns);
            // 服务端推送的附魔分离状态同步到独立缓存（与 SW 快照解耦）
            EnchantSeparationState.INSTANCE.set(netId, enchantSeparation);
            if (isVehicle) {
                vehicleNetId = netId;
            } else {
                currentNetId = netId;
            }
        }
    }

    /** ITEM 现查响应写入（ItemAmmoResponsePacket）。来源标记按 netId 匹配请求时记录值。 */
    public void updateItemCounts(int netId, Map<String, Long> counts) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            Boolean vehicle = itemVehicleByNet.remove(netId);
            boolean isVehicle = vehicle != null && vehicle;
            for (var entry : counts.entrySet()) {
                itemCache.put(entry.getKey(), new ItemEntry(entry.getValue(), now, isVehicle));
            }
            itemRequestPending = false;
        }
    }

    // ═══════════ 玩家侧 API ═══════════

    /** 玩家侧：获取指定弹药类型的数量（无数据返回 0） */
    public long getCount(String t) {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? snap.ammo.getOrDefault(t, 0L) : 0L;
        }
    }

    /** 是否拥有无限弹药标记（__infinite__） */
    public boolean hasInfinite() {
        return getCount("__infinite__") > 0;
    }

    /** 玩家网络快照是否有效 */
    public boolean hasData() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null && System.currentTimeMillis() - snap.lastUpdate < TTL;
        }
    }

    /** 玩家侧：获取全部弹药映射副本 */
    public Map<String, Long> getAll() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? new HashMap<>(snap.ammo) : new HashMap<>();
        }
    }

    /** 获取当前玩家网络 ID */
    public int getNetId() {
        synchronized (LOCK) {
            return currentNetId;
        }
    }

    /** 获取当前网络能量（无数据返回 -1） */
    public long getNetworkEnergy() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? snap.energy : -1;
        }
    }

    /** 获取当前网络名称 */
    public String getNetworkName() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(currentNetId);
            return snap != null ? snap.netName : "";
        }
    }

    /** 当前网络是否启用附魔分离（默认启用；读取独立状态，不依赖 SW 快照） */
    public boolean getEnchantSeparation() {
        synchronized (LOCK) {
            return EnchantSeparationState.INSTANCE.get(currentNetId);
        }
    }

    /** 本地切换附魔分离状态（仅改独立状态，不通知服务端） */
    public void setEnchantSeparation(boolean v) {
        synchronized (LOCK) {
            EnchantSeparationState.INSTANCE.set(currentNetId, v);
        }
    }

    /** 当前网络是否启用自动充电（默认启用；读取独立状态，不依赖 SW 快照） */
    public boolean getEnergyCharge() {
        synchronized (LOCK) {
            return EnergyChargeState.INSTANCE.get(currentNetId);
        }
    }

    /** 本地切换自动充电状态（仅改独立状态，不通知服务端） */
    public void setEnergyCharge(boolean v) {
        synchronized (LOCK) {
            EnergyChargeState.INSTANCE.set(currentNetId, v);
        }
    }

    // ═══════════ 载具侧 API ═══════════

    /** 载具侧：获取指定弹药数量 */
    public long getVehicleCount(String t) {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null ? snap.ammo.getOrDefault(t, 0L) : 0L;
        }
    }

    /** 载具快照是否在有效期内 */
    public boolean vehicleHasData() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null && System.currentTimeMillis() - snap.lastUpdate < VEHICLE_TTL;
        }
    }

    /** 获取当前载具网络 ID */
    public int getVehicleNetId() {
        synchronized (LOCK) {
            return vehicleNetId;
        }
    }

    /** 获取载具网络能量 */
    public long getVehicleEnergy() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null ? snap.energy : -1;
        }
    }

    /** 获取载具网络名称 */
    public String getVehicleNetName() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null ? snap.netName : "";
        }
    }

    /** 获取载具弹药类型列表 */
    public List<String> getVehicleAmmoList() {
        synchronized (LOCK) {
            NetSnapshot snap = snapshots.get(vehicleNetId);
            return snap != null && snap.ammoList != null ? new ArrayList<>(snap.ammoList) : new ArrayList<>();
        }
    }

    // ═══════════ ITEM 现查（玩家/载具通用）═══════════

    /** 读取 ITEM 计数（TTL 内有效，过期返回 -1 表示需重新现查） */
    public long getItemCount(String itemKey, boolean vehicle) {
        synchronized (LOCK) {
            ItemEntry entry = itemCache.get(itemKey);
            if (entry == null || entry.vehicle() != vehicle) return -1;
            if (System.currentTimeMillis() - entry.ts() > ITEM_TTL) {
                itemCache.remove(itemKey);
                return -1;
            }
            return entry.count();
        }
    }

    /** 收集列表中缺失/过期的条目（用于批量现查请求） */
    public List<String> getMissingItemKeys(List<String> itemKeys, boolean vehicle) {
        synchronized (LOCK) {
            List<String> missing = new ArrayList<>();
            for (String key : itemKeys) {
                if (getItemCount(key, vehicle) < 0) missing.add(key);
            }
            return missing;
        }
    }

    /** 发起 ITEM 现查（2s 节流去重；按 netId 记录来源标记，响应时匹配） */
    public void requestItems(int netId, List<String> itemKeys, boolean vehicle) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (itemRequestPending) {
                if (now - itemLastRequest <= ITEM_REQUEST_TIMEOUT) return;
                itemRequestPending = false;
            }
            itemRequestPending = true;
            itemLastRequest = now;
            itemVehicleByNet.put(netId, vehicle);
        }
        PacketHandler.sendToServer(new RequestItemAmmoPacket(netId, itemKeys));
    }

    // ═══════════ 清理 ═══════════

    /** 下车清除：清载具指针与载具来源 ITEM 条目（共享快照保留） */
    public void clearVehicleData() {
        synchronized (LOCK) {
            vehicleNetId = -1;
            clearItems(true);
            itemVehicleByNet.values().removeIf(v -> v);
        }
    }

    /** 仅清除指定来源（载具/全部）的 ITEM 缓存条目 */
    private void clearItems(boolean vehicleOnly) {
        itemCache.entrySet().removeIf(e -> !vehicleOnly || e.getValue().vehicle());
    }

    /** 清空全部快照与缓存状态 */
    public void clear() {
        synchronized (LOCK) {
            snapshots.clear();
            currentNetId = -1;
            vehicleNetId = -1;
            itemCache.clear();
            itemRequestPending = false;
            itemVehicleByNet.clear();
        }
    }

    /** 快照访问互斥锁 */
    private static final Object LOCK = new Object();
}
